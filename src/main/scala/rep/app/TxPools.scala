package rep.app


import scala.collection.JavaConverters._
import java.util.concurrent.ConcurrentHashMap
import rep.network.tools.transpool.TransactionPoolMgr


object TxPools {
  private implicit var mgrs = new ConcurrentHashMap[String,TransactionPoolMgr]() asScala

  def registPoolMgr(sysName:String,pm:TransactionPoolMgr):Unit={
    this.mgrs.put(sysName,pm)
  }

  def getPoolMgr(sysName:String):TransactionPoolMgr={
    var r : TransactionPoolMgr = null
    if(this.mgrs.contains(sysName)){
      r = this.mgrs.getOrElse(sysName,null)
    }
    r
  }

}
