package rep.authority.cache

import rep.sc.tpl.did.DidTplPrefix
import rep.storage.chain.preload.BlockPreload

class AuthenticateBindToCertCache(systemName:String) extends ICache(systemName) {
  override protected def dataTypeConvert(any: Option[Any],blockPreload: BlockPreload): Option[Any] = {
    if(any == None){
      None
    }else{
      Some(any.get.asInstanceOf[Boolean])
    }
  }

  override protected def getPrefix: String = {
    this.common_prefix + this.splitSign + DidTplPrefix.bindPrefix
  }

  def get(authid:String,certid:String,blockPreload: BlockPreload):Option[Boolean]={
    get(authid+"_"+certid,blockPreload)
  }

  def get(key:String,blockPreload: BlockPreload):Option[Boolean]={
    val d = this.getData(key,blockPreload)
    if(d == None)
      None
    else
      Some(d.get.asInstanceOf[Boolean])
  }
}
