package rep.app

import java.util.concurrent._
import java.util.concurrent.atomic.AtomicBoolean
import rep.app.system.ClusterSystem
import akka.cluster.{Cluster, MemberStatus}
import akka.util.Timeout
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.util.control.Breaks.{break, breakable}

object RepChainMgr {
  private var instanceOfCluster = new scala.collection.mutable.HashMap[String, ClusterSystem]()
  private var nodeList: ArrayBuffer[String] = new ArrayBuffer[String]()
  private var shutdownOfManual: ArrayBuffer[String] = new ArrayBuffer[String]()
  private val isStarting = new AtomicBoolean(false)

  def Startup4Single(SystemName: String) = {
    if (!this.instanceOfCluster.contains(SystemName)) {
      val sys1 = new ClusterSystem(SystemName, true)
      sys1.createClusterSystem
      if (!nodeList.contains(SystemName)) {
        nodeList += SystemName
      }
      this.instanceOfCluster += SystemName -> (sys1)
      if (this.shutdownOfManual.contains(SystemName)) {
        this.shutdownOfManual -= SystemName
      }
      sys1.startupRepChain
    }
  }

  def isStartupFinish(SystemName: String): Boolean = {
    var r = false
    if (this.instanceOfCluster.contains(SystemName)) {
      val sys = this.instanceOfCluster(SystemName)
      if (sys != null) {
        r = this.isFinishOfStartupForChecked(sys)
      }
    }
    r
  }

  def shutdown(SystemName: String): Boolean = {
    var r = false
    val sys = this.instanceOfCluster(SystemName)
    if (sys != null) {
      if (!this.shutdownOfManual.contains(SystemName)) {
        this.shutdownOfManual += SystemName
      }
      r = killActorSystem(sys)
      if (!r) {
        Thread.sleep(5000)
        System.err.println(s"shutdown happen error,again shutdown,systemname=${SystemName}")
        r = killActorSystem(sys)
      }
    }
    if (r) {
      this.instanceOfCluster -= SystemName
    } else {
      if (this.shutdownOfManual.contains(SystemName)) {
        this.shutdownOfManual -= SystemName
      }
    }
    r
  }

  def killActorSystem(sys: ClusterSystem): Boolean = {
    var r: Boolean = false
    try {
      r = sys.terminateOfSystem
    } catch {
      case e1: Exception => e1.printStackTrace()
    }
    r
  }

  import scala.concurrent._

  private def isUpOfClusterForChecked(cluster: Cluster): Boolean = {
    implicit val timeout = Timeout(30.seconds)
    val result = Future.successful(cluster.selfMember.status == MemberStatus.Up)
    val result1 = Await.result(result, timeout.duration).asInstanceOf[Boolean]
    result1
  }

  private def isFinishOfStartupForChecked(clusterSystem: ClusterSystem): Boolean = {
    var r = false
    val cluster = clusterSystem.getClusterInstance
    if (cluster != null) {
      breakable {
        //持续120s，检查自己是否UP，每次检查的超时时间为35s，检查3次
        for (i <- 1 to 3) {
          Thread.sleep(5000)
          if (isUpOfClusterForChecked(cluster)) {
            r = true
            break
          }
        }
      }
    }
    r
  }

  private def processOfRestart(systemName: String): Boolean = {
    var r = false
    try {
      System.err.println(s"shutdown start time=${System.currentTimeMillis()}")
      shutdown(systemName)
      System.err.println(s"shutdown end time=${System.currentTimeMillis()}")
      Thread.sleep(5000)
      System.err.println(s"terminateOfSystem finished,systemName=${systemName}")
      Startup4Single(systemName)
      Thread.sleep(5000)
      r = isFinishOfStartupForChecked(instanceOfCluster(systemName))
    } catch {
      case e: Exception => e.printStackTrace()
    }
    r
  }

  //重启节点的策略是：没有启动成功一直启动，直到启动成功
  private def RestartCulsterUtilToSuccesss(systemName: String): Unit = {
    //持续6min，检查自己是否UP，每次检查的超时时间为120s，检查3次
    var r = false
    isStarting.set(true)
    var i = 1
    while (!r) {
      try {
        r = processOfRestart(systemName)
        if (!r) {
          if (i > 10) {
            Thread.sleep(180 * 1000)
          } else if (i > 100) {
            Thread.sleep(300 * 1000)
          } else {
            Thread.sleep(120 * 1000)
          }

        }
      } catch {
        case e: Exception => e.printStackTrace()
      }
      i += 1
    }
    isStarting.set(false)
  }

  val threadPool: ExecutorService = Executors.newFixedThreadPool(1)

  def ReStart(SystemName: String) = {
    if (!this.shutdownOfManual.contains(SystemName)) {
      if (!isStarting.get()) {
        try {
          threadPool.execute(new RestartThread(SystemName))
        } catch {
          case e: Exception => e.printStackTrace()
        }
      }
    }
  }

  var scheduledExecutorService = Executors.newSingleThreadScheduledExecutor

  def StartClusterStub = {
    this.scheduledExecutorService.scheduleWithFixedDelay( //).scheduleAtFixedRate(
      new ClusterTestStub, 100, 60, TimeUnit.SECONDS
    )
  }

  class RestartThread(systemName: String) extends Runnable {
    override def run() {
      try {
        RestartCulsterUtilToSuccesss(systemName)
      } catch {
        case e: Exception => e.printStackTrace()
      }
    }
  }

  class ClusterTestStub extends Runnable {
    override def run() {
      try {
        //sleep 90 s
        //Thread.sleep(90000)
        System.err.println(s"entry terminate systemName")
        if (!isStarting.get()) {
          //单机模拟多节点时，采用随机down某个节点
          System.err.println(s"start terminate systemName")
          var rd = scala.util.Random.nextInt(100)
          rd = rd % nodeList.length
          if (rd == 0) rd = rd + 1
          var systemName = nodeList(rd)
          systemName = "921000006e0012v696.node5"
          RepChainMgr.shutdown(systemName)
          System.err.println(s"stop system,systemName=${systemName}")
        }
      } catch {
        case e: Exception => e.printStackTrace()
      }
    }
  }

}
