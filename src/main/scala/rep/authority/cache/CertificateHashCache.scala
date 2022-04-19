package rep.authority.cache

import rep.sc.tpl.did.DidTplPrefix
import rep.storage.chain.preload.BlockPreload

class CertificateHashCache(systemName:String) extends ICache(systemName) {
  override protected def dataTypeConvert(any: Option[Any],blockPreload: BlockPreload): Option[Any] = {
    if(any == None){
      None
    }else{
      Some(any.get.asInstanceOf[String])
    }
  }

  override protected def getPrefix: String = {
    this.common_prefix + this.splitSign + DidTplPrefix.hashPrefix
  }

  def get(key:String,blockPreload: BlockPreload):Option[String]={
    val d = this.getData(key,blockPreload)
    if(d == None)
      None
    else
      Some(d.get.asInstanceOf[String])
  }

}
