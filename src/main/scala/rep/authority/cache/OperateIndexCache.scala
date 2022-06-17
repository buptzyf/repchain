package rep.authority.cache

import rep.app.system.RepChainSystemContext
import rep.log.RepLogger
import rep.sc.tpl.did.DidTplPrefix
import rep.storage.chain.preload.BlockPreload
import rep.utils.IdTool

class OperateIndexCache(ctx : RepChainSystemContext) extends ICache(ctx){
  override protected def dataTypeConvert(any: Option[Any], blockPreload: BlockPreload): Option[Any] = {
    if(any == None){
      None
    }else{
      val tmp = any.getOrElse(null)
      if(tmp == null){
        None
      }else{
        if(tmp.isInstanceOf[Seq[String]]){
          val seq = tmp.asInstanceOf[Seq[String]]
          Some(seq.toArray)
        }else{
          None
        }
      }
    }
  }

  override protected def getBaseNetworkPrefix: String = {
    this.common_prefix + IdTool.WorldStateKeySeparator + DidTplPrefix.operIdxPrefix
  }

  override protected def getBusinessNetworkPrefix: String = {
    this.business_prefix + IdTool.WorldStateKeySeparator + DidTplPrefix.operIdxPrefix
  }

  override protected def getCacheType: String = {
    IdTool.WorldStateKeySeparator + DidTplPrefix.operIdxPrefix
  }

  override protected def readData(key: String, blockPreload: BlockPreload): Option[Any] = {
    var r = this.cache.getOrDefault(key, None)
    if (r == None) {
      RepLogger.Permission_Logger.trace(s"OperateIndexCache.readData asynchronous read,from IdentityNet,key=${key}")
      //获取数据方式，0：从基础链获取；1：从业务链获取；
      val base = this.dataTypeConvert(this.db.getObject(this.getBaseNetworkPrefix + key), blockPreload)
      val business = this.dataTypeConvert(this.db.getObject(this.getBusinessNetworkPrefix + key), blockPreload)
      if (base != None) {
        if (business != None) {
          val base1 = base.get.asInstanceOf[Array[String]]
          val business1 = business.get.asInstanceOf[Array[String]]
          r = Some(Array.concat(base1, business1))
        } else {
          r = base
        }
      } else {
        if (business != None) {
          r = business
        } else {
          r = None
        }
      }
      if (r != None) {
        this.cache.put(key, r)
        RepLogger.Permission_Logger.trace(s"OperateIndexCache.readData ,key=${key}，value=${r.get.asInstanceOf[Array[String]]}")
      }
    }
    r
  }

  def get(key:String,blockPreload: BlockPreload):Option[Array[String]]={
    val d = this.getData(key,blockPreload)
    if(d == None)
      None
    else
      Some(d.get.asInstanceOf[Array[String]])
  }
}
