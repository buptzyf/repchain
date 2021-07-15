package rep.log.httplog

import java.util.concurrent.{LinkedBlockingQueue, ThreadPoolExecutor, TimeUnit}


/**
 * @author jiangbuyun
 * @version 1.1
 * @since 2021-07-09
 * @category http日志器，采用线程池写入prisma
 **/
class HttpLogger(coreSize: Int, maxSize: Int, aliveTime: Int, isOutputAlert: Boolean, url: String) {
  private var threadpool: ThreadPoolExecutor = null

  initThreadPool

  private def initThreadPool: Unit = {
    if (this.isOutputAlert) {
      this.threadpool = new ThreadPoolExecutor(
        coreSize,
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

}

object HttpLogger {
  def getHttpLogger(coreSize: Int, maxSize: Int, aliveTime: Int, isOutputAlert: Boolean, url: String): HttpLogger = {
    var singleobj: HttpLogger = null
    synchronized {
      if (singleobj == null) {
        singleobj = new HttpLogger(coreSize, maxSize, aliveTime, isOutputAlert, url)
      }
      singleobj
    }
  }
}
