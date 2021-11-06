package rep.log

import rep.log.httplog.{AlertInfo, HttpLogger}


object HttpLoggerTest extends App {
  val log = HttpLogger.getHttpLogger(2,4,10,true,"http://192.168.2.112:4467")
  log.SendAlert(new AlertInfo("SYSTEM",4,"test1"))

}
