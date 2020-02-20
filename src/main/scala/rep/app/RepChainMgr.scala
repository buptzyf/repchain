package rep.app

import java.util.concurrent._

import akka.actor.Address
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType
import scala.collection.mutable.ArrayBuffer


object RepChainMgr {
  private var clusterAddr: Address = null  //集群种子节点地址
  private var instanceOfCluster = new scala.collection.mutable.HashMap[String, (ClusterSystem,Int)]()
  private var isSingle = false
  private var nodelist : ArrayBuffer[String] = new ArrayBuffer[String]()

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
    val joinAddress = sys1.getClusterAddr
    sys1.joinCluster(joinAddress)
    if(!this.instanceOfCluster.contains(SystemName)){
      this.nodelist += SystemName
    }
    this.instanceOfCluster += SystemName -> (sys1,0)
    sys1.start
  }

  def Startup4Multi(SystemName:String,port:Int)={
    val sys1 = new ClusterSystem(SystemName,InitType.MULTI_INIT,true)
    if(this.isJDK8OfRunEnv){
      sys1.init
    }else{
      sys1.init2(port)//初始化（参数和配置信息）
    }

    if(this.clusterAddr == null){
      this.clusterAddr = sys1.getClusterAddr//获取组网地址
      sys1.enableWS()//开启API接口
    }else{
      sys1.disableWS()
    }

    if(this.isJDK8OfRunEnv){
      sys1.joinCluster(this.clusterAddr)//加入网络
    }
    if(!this.instanceOfCluster.contains(SystemName)){
      this.nodelist += SystemName
    }
    this.instanceOfCluster += SystemName -> (sys1,port)
    sys1.start//启动系统
  }



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
        sys.terminateOfSystem
      }
    }
  }

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
    val threadPool:ExecutorService=Executors.newFixedThreadPool(1)
    try {
      threadPool.execute(new RestartThread(SystemName))
    }finally {
      threadPool.shutdown()
    }
  }

  var scheduledExecutorService = Executors.newSingleThreadScheduledExecutor

  def StartClusterStub={
    /*val threadPool:ExecutorService=Executors.newFixedThreadPool(1)
    try {
      threadPool.execute(new ClusterTestStub)
    }finally {
      threadPool.shutdown()
    }*/
    //try{
    this.scheduledExecutorService.scheduleWithFixedDelay(//).scheduleAtFixedRate(
      new ClusterTestStub,100,600, TimeUnit.SECONDS
    )
    /*}catch {
      case e:Exception =>
              try{
                scheduledExecutorService.shutdown()
              }catch{
                case e:Exception => e.printStackTrace()
              }
    }*/

  }

  class RestartThread(systemName:String) extends Runnable{
    override def run(){
      try{
        System.err.println(s"shutdown start time=${System.currentTimeMillis()}")
        shutdown(systemName)
        System.err.println(s"shutdown end time=${System.currentTimeMillis()}")
        //Thread.sleep(120000)
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
        if(!isSingle){
          //单机模拟多节点时，采用随机down某个节点
          System.err.println(s"start terminate systemName")
          var rd = scala.util.Random.nextInt(100)
          rd = rd % 5
          if(rd == 0) rd =  rd + 1
          val systemname = nodelist(rd)

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
      }catch{
        case e:Exception=>e.printStackTrace()
      }
    }
  }

}
