package rep.app


import scala.collection.JavaConverters._
import java.util.concurrent.ConcurrentHashMap

import rep.network.tools.transpool.PoolOfTransaction


object TxPools {
  private implicit var mgrs = new ConcurrentHashMap[String,PoolOfTransaction]() asScala

  def registerTransactionPool(sysName:String,pm:PoolOfTransaction):Unit={
    this.mgrs.put(sysName,pm)
  }

  def getTransactionPool(sysName:String):PoolOfTransaction={
    var r : PoolOfTransaction = null
    if(this.mgrs.contains(sysName)){
      r = this.mgrs.getOrElse(sysName,null)
    }
    r
  }

}
