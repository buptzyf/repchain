package rep.app

import java.util.concurrent._
import java.util.concurrent.atomic.AtomicBoolean

import rep.app.system.ClusterSystem
import akka.cluster.{Cluster, MemberStatus}
import akka.util.Timeout
import rep.log.RepLogger
import rep.log.httplog.AlertInfo

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.control.Breaks.{break, breakable}

case object SystemStatus {
  val Starting = 1 //正在停止
  val Running = 2 //运行中
  val Stopping = 3 //正在停止
  val Stopped = 4 //已经停止
  val None = 5 //未知状态
}

case object ReasonOfStop {
  val Manual = 1 //手动停止节点
  val Unusual = 2 //系统发生异常停止节点，未知原因停止节点，系统会尝试自动重启
  val None = 3 //未知
}

case object ReasonOfStartup {
  val Manual = 1 //手动启动节点
  val Auto = 2 //系统自动启动
  val None = 3 //未知
}

class SystemInfo(nodeName: String) {
  private val lock: Object = new Object

  private var systemStatus: Int = SystemStatus.None
  private var reasonOfStop: Int = ReasonOfStop.None
  private var reasonOfStartup: Int = ReasonOfStartup.None

  private var clusterSystem: ClusterSystem = null


  def startup(mode: Int): String = {
    var resultInfo: String = ""

    lock.synchronized({
      if (mode == ReasonOfStartup.Manual) {
        if (this.systemStatus == SystemStatus.None || this.systemStatus == SystemStatus.Stopped) {
          resultInfo = systemStartup(mode)
        } else {
          //不能启动，返回原因
          this.systemStatus match {
            case SystemStatus.Starting => resultInfo = "系统正在启动..."
            case SystemStatus.Running => resultInfo = "系统已经启动..."
            case SystemStatus.Stopping => resultInfo = "系统正在停止，暂时不能启动..."
          }
        }
      } else {
        if (mode == ReasonOfStartup.Auto) {
          if (reasonOfStop == ReasonOfStop.Unusual && systemStatus == SystemStatus.Stopped) {
            resultInfo = systemStartup(mode)
          }
        } else {
          resultInfo = "未知的启动模式，不能执行启动..."
        }
      }

    })

    resultInfo
  }

  def getReasonOfStop:Int={
      reasonOfStop
  }

  def shutdown(mode: Int): String = {
    var resultInfo: String = ""
    lock.synchronized({
      if (this.systemStatus == SystemStatus.Running || this.systemStatus == SystemStatus.Stopping) {
        this.reasonOfStop = mode
        this.systemStatus = SystemStatus.Stopping
        if (killSystem) {
          this.clusterSystem = null
          this.systemStatus = SystemStatus.Stopped
          resultInfo = "系统已经停止"
        } else {
          resultInfo = "系统无法正常停止，请与管理员联系。"
        }
      } else {
        this.systemStatus match {
          case SystemStatus.Stopped => resultInfo = "系统已经停止"
          case SystemStatus.Starting => resultInfo = "系统正在启动，不能停止"
          case SystemStatus.None => resultInfo = "系统没有启动，不需要停止"
        }
      }
    })
    resultInfo
  }

  private def systemStartup(mode: Int): String = {
    var resultInfo = ""
    //可以启动
    clusterSystem = new ClusterSystem(nodeName, true)
    clusterSystem.createClusterSystem
    this.systemStatus = SystemStatus.Starting
    this.reasonOfStartup = mode
    this.reasonOfStop = ReasonOfStop.None

    try {
      val rt = new Thread(new SystemHandleThread)
      rt.start()
      resultInfo = "系统开始启动..."
    } catch {
      case e: Exception => resultInfo = e.getMessage
    }

    resultInfo
  }


  class SystemHandleThread extends Runnable {
    override def run() {
      try {
        if (checkStartupStatus) {
          clusterSystem.startupRepChain
          systemStatus = SystemStatus.Running
          RepLogger.info(RepLogger.System_Logger, s"装载Repchain系统成功")
        } else {
          RepLogger.info(RepLogger.System_Logger, s"系统加入组网异常")
          RepLogger.sendAlertToDB(clusterSystem.getRepChainContext.getHttpLogger(),
            new AlertInfo("SYSTEM", 1, s"Node Name=${nodeName},系统加入组网异常."))
          //失败处理
          shutdown(ReasonOfStop.Unusual)
        }
      } catch {
        case e: Exception =>
          RepLogger.info(RepLogger.System_Logger, s"装载Repchain系统异常，error=${e.getMessage}")
          RepLogger.sendAlertToDB(clusterSystem.getRepChainContext.getHttpLogger(),
            new AlertInfo("SYSTEM", 1, s"Node Name=${nodeName},${e.getMessage}."))
          //失败处理
          shutdown(ReasonOfStop.Unusual)
      }
    }
  }

  private def killSystem: Boolean = {
    var r = false
    if (clusterSystem != null) {
      breakable {
        for (i <- 1 to 3) {
          if (clusterSystem.terminateOfSystem) {
            r = true
            break
          }
        }
      }
    } else {
      r = true
    }
    r
  }

  private def checkStartupStatus: Boolean = {
    var r = false
    val cluster = clusterSystem.getClusterInstance
    if (cluster != null) {
      breakable {
        for (i <- 1 to 3) {
          Thread.sleep(5000)
          if (isUpOfClusterStatus(cluster)) {
            r = true
            break
          }
        }
      }
    }
    r
  }

  private def isUpOfClusterStatus(cluster: Cluster): Boolean = {
    try {
      implicit val timeout = Timeout(30.seconds)
      val result = Future.successful(cluster.selfMember.status == MemberStatus.Up)
      val result1 = Await.result(result, timeout.duration).asInstanceOf[Boolean]
      result1
    } catch {
      case e: Exception =>
        RepLogger.info(RepLogger.System_Logger, s"检查是否已经加入集群异常，error=${e.getMessage}")
        false
    }
  }

  def getSystemStatus: Int = {
    this.systemStatus
  }
}

object RepChainMgr {

  private var instanceOfCluster = new ConcurrentHashMap[String, SystemInfo]()

  def Startup4Single(SystemName: String, mode: Int): String = {
    this.synchronized({
      var info : SystemInfo = null
      if (this.instanceOfCluster.containsKey(SystemName))
        info = this.instanceOfCluster.get(SystemName)
      else {
        info = new SystemInfo(SystemName)
        this.instanceOfCluster.put(SystemName,info)
      }
      info.startup(mode)
    })
  }

  def shutdown(SystemName: String, mode: Int): String = {
    this.synchronized({
      var info : SystemInfo = null
        if (this.instanceOfCluster.containsKey(SystemName)){
          info = this.instanceOfCluster.get(SystemName)
          info.shutdown(mode)
        }else{
          s"没有找到${SystemName}系统"
        }
    })
  }

  def systemStatus(SystemName: String): String = {
    this.synchronized({
      val info = if (this.instanceOfCluster.containsKey(SystemName))
        this.instanceOfCluster.get(SystemName) else new SystemInfo(SystemName)
      info.getSystemStatus match {
        case SystemStatus.None => "系统处于初始化状态"
        case SystemStatus.Starting => "系统处于正在启动状态"
        case SystemStatus.Running => "系统处于运行状态"
        case SystemStatus.Stopping => "系统处于正在停止状态"
        case SystemStatus.Stopped => "系统处于已经停止状态"
      }
    })
  }

  private def processOfRestart(systemName: String): String = {
    try {
      System.err.println(s"shutdown start time=${System.currentTimeMillis()}")
      shutdown(systemName, ReasonOfStop.Unusual)
      System.err.println(s"shutdown end time=${System.currentTimeMillis()}")
      Thread.sleep(5000)
      System.err.println(s"terminateOfSystem finished,system status=${systemStatus(systemName)},systemName=${systemName}")
      Startup4Single(systemName, ReasonOfStartup.Auto)
      Thread.sleep(5000)
    } catch {
      case e: Exception => e.printStackTrace()
    }
    systemStatus(systemName)
  }

  private def RestartCluster(systemName: String): Unit = {
    //持续6min，检查自己是否UP，每次检查的超时时间为120s，检查3次
    var i = 1
    breakable {
      for (i <- 0 to 3) {
        val r = processOfRestart(systemName)
        if (r == "系统处于运行状态") break
        Thread.sleep(30 * 1000)
      }
    }
  }

  def ReStart(SystemName: String) = {
    var info : SystemInfo = null
    if(this.instanceOfCluster.containsKey(SystemName)){
      info = this.instanceOfCluster.get(SystemName)
    }
    if(info != null){
      if(!(info.getReasonOfStop==ReasonOfStop.Manual)){
        val t = new Thread(new Runnable {
          override def run(): Unit = {
            try {
              RestartCluster(SystemName)
            } catch {
              case e: Exception => e.printStackTrace()
            }
          }
        })
        t.start()
      }
    }

  }


  var scheduledExecutorService = Executors.newSingleThreadScheduledExecutor

  def StartClusterStub = {
    this.scheduledExecutorService.scheduleWithFixedDelay(
      new ClusterTestStub, 100, 60, TimeUnit.SECONDS
    )
  }

  class ClusterTestStub extends Runnable {
    override def run() {
      try {
        System.err.println(s"entry terminate systemName")
        val systemName = "921000006e0012v696.node5"
        RepChainMgr.shutdown(systemName, ReasonOfStop.Unusual)
        System.err.println(s"stop system,systemName=${systemName}")
      } catch {
        case e: Exception => e.printStackTrace()
      }
    }
  }

}
