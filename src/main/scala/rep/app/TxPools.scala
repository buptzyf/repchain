package rep.app


import scala.collection.JavaConverters._
import java.util.concurrent.ConcurrentHashMap

import rep.network.tools.transpool.{ITransctionPoolMgr}


object TxPools {
  private implicit var mgrs = new ConcurrentHashMap[String,ITransctionPoolMgr]() asScala

  def registPoolMgr(sysName:String,pm:ITransctionPoolMgr):Unit={
    this.mgrs.put(sysName,pm)
  }

  def getPoolMgr(sysName:String):ITransctionPoolMgr={
    var r : ITransctionPoolMgr = null
    if(this.mgrs.contains(sysName)){
      r = this.mgrs.getOrElse(sysName,null)
    }
    r
  }

}
