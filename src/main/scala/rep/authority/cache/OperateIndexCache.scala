package rep.authority.cache

import rep.app.system.RepChainSystemContext
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
    this.common_prefix + IdTool.WorldStateKeySeparator + DidTplPrefix.authPrefix
  }

  override protected def getBusinessNetworkPrefix: String = {
    this.business_prefix + IdTool.WorldStateKeySeparator + DidTplPrefix.authPrefix
  }

  override protected def getCacheType: String = {
    IdTool.WorldStateKeySeparator + DidTplPrefix.operIdxPrefix
  }

  def get(key:String,blockPreload: BlockPreload):Option[Array[String]]={
    val d = this.getData(key,blockPreload)
    if(d == None)
      None
    else
      Some(d.get.asInstanceOf[Array[String]])
  }
}
