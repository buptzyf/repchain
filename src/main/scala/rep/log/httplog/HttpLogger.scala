package rep.log.httplog

import java.util.concurrent.{ LinkedBlockingQueue, ThreadPoolExecutor, TimeUnit}


/**
 * @author jiangbuyun
 * @version	1.1
 * @since	2021-07-09
 * @category	http日志器，采用线程池写入prisma
 * */
class HttpLogger(coreSize: Int, maxSize: Int, aliveTime: Int, isOutputAlert: Boolean, url: String) {
  private var threadpool: ThreadPoolExecutor = null


  initThreadPool

  private def initThreadPool: Unit = {
    if (this.isOutputAlert) {
      this.threadpool = new ThreadPoolExecutor(coreSize,
        maxSize,
        aliveTime,
        TimeUnit.SECONDS,
        new LinkedBlockingQueue[Runnable]())
    }
  }



  def SendAlert(info: AlertInfo): Unit = {
    if (this.isOutputAlert) {
      val task = new HttpThread(url, info)
      this.threadpool.execute(task)
    }
  }

  def hasOutputAlert:Boolean={
    this.isOutputAlert
  }
}


/*
object HttpLogger {
  var instances: ConcurrentHashMap[String,HttpLogger] = new ConcurrentHashMap[String,HttpLogger]()
  def getHttpLogger(systemName:String): HttpLogger = {
    synchronized {
      if(instances.containsKey(systemName)){
        instances.get(systemName)
      }else{
        val config = RepChainConfig.getSystemConfig(systemName)
        var instance = new HttpLogger(config.getOuputAlertThreads,config.getOutputMaxThreads,config.getOutputAlertAliveTime,
          config.isOutputAlert,config.getOutputAlertPrismaUrl)
        val old = instances.putIfAbsent(systemName,instance)
        if(old != null){
          instance = old
        }
        instance
      }
    }
  }
}*/
