package rep.app

import java.util.concurrent._
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.{ActorSystem, Address, Terminated}
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType
import rep.network.consensus.util.BlockVerify
import akka.cluster.{Cluster, MemberStatus}
import akka.util.Timeout

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.Breaks.{break, breakable}


object RepChainMgr {
  private var clusterAddr: Address = null  //集群种子节点地址
  private var instanceOfCluster = new scala.collection.mutable.HashMap[String, (ClusterSystem,Int)]()
  private var isSingle = false
  private var nodelist : ArrayBuffer[String] = new ArrayBuffer[String]()
  private var isStarting = new AtomicBoolean(false)

  def isJDK8OfRunEnv:Boolean={
    var defaultvalue = false//默认未13
    val javaVersion = System.getProperty("java.version").split("[+.\\-]+", 3)
    if(javaVersion != null && javaVersion.length >= 2){
      if(javaVersion(1) == "8"){
        defaultvalue = true
      }
    }
    defaultvalue
  }

  def Startups(param:Array[(String,Int)])={
    param.foreach(f=>{
      Startup4Multi(f._1,f._2)
      Thread.sleep(2000)
    })
  }

  def Startup4Single(SystemName:String)={
    this.isSingle = true
    val sys1 = new ClusterSystem(SystemName, InitType.SINGLE_INIT,true)
    sys1.init
    //val joinAddress = sys1.getClusterAddr
    //sys1.joinCluster(joinAddress)
    if(!this.instanceOfCluster.contains(SystemName)){
      this.nodelist += SystemName
    }
    this.instanceOfCluster += SystemName -> (sys1,0)
    sys1.start
  }

  def Startup4Multi(SystemName:String,port:Int)={
    val sys1 = new ClusterSystem(SystemName,InitType.MULTI_INIT,true)
    if(this.isJDK8OfRunEnv){
      sys1.init3(port)//
    }else{
      sys1.init2(port)//初始化（参数和配置信息）
    }



    if(this.clusterAddr == null){
      this.clusterAddr = sys1.getClusterAddr//获取组网地址
      sys1.enableWS()//开启API接口
    }else{
      sys1.disableWS()
    }

    //val clusterAddr = sys1.getClusterAddr
    //sys1.joinCluster(clusterAddr)

    /*if(this.isJDK8OfRunEnv){
      sys1.joinCluster(this.clusterAddr)//加入网络
    }*/
    if(!this.instanceOfCluster.contains(SystemName)){
      this.nodelist += SystemName
    }
    this.instanceOfCluster += SystemName -> (sys1,port)
    sys1.start//启动系统
  }

  var isStopped = false



  def Stop(SystemName:String)={
    val  sys1 = this.instanceOfCluster(SystemName)
    if(sys1 != null){
      val sys = sys1._1
      if(sys != null){
        sys.shutdown
        Thread.sleep(10000)
      }
    }
  }

  def shutdown(SystemName:String)={
    val  sys1 = this.instanceOfCluster(SystemName)
    if(sys1 != null){
      val sys = sys1._1
      if(sys != null){
        var r = killActorSystem(sys)
        if(!r){
          Thread.sleep(10000)
          System.err.println(s"shutdown happen error,again shutdown,systemname=${SystemName}")
          killActorSystem(sys)
        }
      }
    }
  }

  def killActorSystem(sys:ClusterSystem):Boolean={
    var r :Boolean = false
    try{
      r = sys.terminateOfSystem
    }catch{
      case e1:Exception => e1.printStackTrace()
    }
    r
  }

  import scala.concurrent._
  private def isUpOfClusterForChecked(cluster: Cluster): Boolean =  {
    implicit val timeout = Timeout(120.seconds)
    val result = Future.successful(cluster.selfMember.status == MemberStatus.Up)
    val result1 = Await.result(result, timeout.duration).asInstanceOf[Boolean]
    result1
  }

  private def isFinishOfStartupForChecked(clusterSystem: ClusterSystem): Boolean =  {
    var r = false
    val cluster = clusterSystem.getClusterInstance
    if(cluster != null ){
      breakable {
        //持续6min，检查自己是否UP，每次检查的超时时间为120s，检查3次
        for(i <-1 to 3){
          if(isUpOfClusterForChecked(cluster)){
            r = true
            break
          }
        }
      }
    }
    r
  }

  private def  processOfRestart(systemName:String):Boolean={
    var r = false
    try{
      System.err.println(s"shutdown start time=${System.currentTimeMillis()}")
      var r = shutdown(systemName)
      System.err.println(s"shutdown end time=${System.currentTimeMillis()}")
      Thread.sleep(5000)
      System.err.println(s"terminateOfSystem finished,systemName=${systemName}")
      if(isSingle){
        Startup4Single(systemName)
      }else{
        val  sys1 = instanceOfCluster(systemName)
        if(sys1 != null){
          val port = sys1._2
          Startup4Multi(systemName,port)
        }
      }
      Thread.sleep(5000)
      r = isFinishOfStartupForChecked(instanceOfCluster(systemName)._1)
    }catch{
      case e:Exception=>e.printStackTrace()
    }
    r
  }

  //重启节点的策略是：没有启动成功一直启动，直到启动成功
  private def RestartCulsterUtilToSuccesss(systemName:String): Unit ={
    //持续6min，检查自己是否UP，每次检查的超时时间为120s，检查3次
    var r = false
    isStarting.set(true)
    var i = 1
    while(!r){
      try{
        r = processOfRestart(systemName)
        if(!r){
          if(i > 10){
            Thread.sleep(180*1000)
          }else if(i > 100){
            Thread.sleep(300*1000)
          }else{
            Thread.sleep(120*1000)
          }

        }
      }catch{
        case e:Exception => e.printStackTrace()
      }
      i += 1
    }
    isStarting.set(false)
  }

  val threadPool:ExecutorService=Executors.newFixedThreadPool(1)

  def ReStart(SystemName:String)={
    /*var scheduledExecutorService1 = Executors.newSingleThreadScheduledExecutor

    try{
      scheduledExecutorService1.schedule(new RestartThread(SystemName),3,TimeUnit.SECONDS)
    }finally {
        try{
          scheduledExecutorService1.shutdown()
        }catch{
          case e:Exception => e.printStackTrace()
        }
    }*/

    if(!isStarting.get()){
      try {
        threadPool.execute(new RestartThread(SystemName))
      }catch{
        case e:Exception => e.printStackTrace()
      }
    }
  }

  var scheduledExecutorService = Executors.newSingleThreadScheduledExecutor

  def StartClusterStub={
    this.scheduledExecutorService.scheduleWithFixedDelay(//).scheduleAtFixedRate(
      new ClusterTestStub,100,60, TimeUnit.SECONDS
    )
  }

  def StopNodeService={
    this.scheduledExecutorService.schedule(new ClusterTestStub,120,TimeUnit.SECONDS)
  }

  class RestartThread(systemName:String) extends Runnable{
    override def run(){
      try{
        RestartCulsterUtilToSuccesss(systemName)
        /*System.err.println(s"shutdown start time=${System.currentTimeMillis()}")
        var r = shutdown(systemName)
        System.err.println(s"shutdown end time=${System.currentTimeMillis()}")
        Thread.sleep(5000)
        System.err.println(s"terminateOfSystem finished,systemName=${systemName}")
        if(isSingle){
          Startup4Single(systemName)
        }else{
          val  sys1 = instanceOfCluster(systemName)
          if(sys1 != null){
            val port = sys1._2
            Startup4Multi(systemName,port)
          }
        }*/
      }catch{
        case e:Exception=>e.printStackTrace()
      }
    }
  }

  class ClusterTestStub extends Runnable{
    override def run(){
      try{
        //sleep 90 s
        //Thread.sleep(90000)
        System.err.println(s"entry terminate systemName")
        if(!isStarting.get()){
          if(!isSingle){
            //单机模拟多节点时，采用随机down某个节点
            System.err.println(s"start terminate systemName")
            var rd = scala.util.Random.nextInt(100)
            rd = rd % 5
            if(rd == 0) rd =  rd + 1
            var systemname = nodelist(rd)
            systemname = "921000006e0012v696.node5"
            RepChainMgr.Stop(systemname)
            System.err.println(s"stop system,systemName=${systemname}")
          }else{
            //单机启动时，需要做测试时启动该节点的动态停止，模拟断网
            System.err.println(s"start terminate systemName")
            val systemname = nodelist(0)
            //如果想down某个节点，就在条件中注明down的节点名称，例子里面down节点5
            if(systemname == "921000006e0012v696.node5"){
              RepChainMgr.Stop(systemname)
              System.err.println(s"stop system,systemName=${systemname}")
            }
          }
        }
        //临时使用
        //scheduledExecutorService.shutdown()
      }catch{
        case e:Exception=>e.printStackTrace()
      }
    }
  }

}
