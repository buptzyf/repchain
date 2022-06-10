package rep.storage.test

import rep.app.system.RepChainSystemContext
import rep.log.RepLogger
import rep.log.httplog.AlertInfo
import rep.storage.verify.verify4Storage

object storageCheck extends App {
  val ctx = new RepChainSystemContext("330597659476689954.node6",null)

  checkSystemStorage

  def checkSystemStorage: Boolean = {
    var r = true
    try {
      if (!new verify4Storage(ctx).verify(ctx.getSystemName)) {
        RepLogger.sendAlertToDB(ctx.getHttpLogger(),new AlertInfo("SYSTEM",1,s"Node Name=${ctx.getSystemName},BlockChain file error."))
        r = false
      }
    } catch {
      case e: Exception => {
        r = false
        //throw new Exception("Storager Verify Error,info:" + e.getMessage)
      }
    }
    r
  }
}
