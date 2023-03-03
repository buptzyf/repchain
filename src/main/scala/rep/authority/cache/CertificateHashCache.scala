package rep.authority.cache


import rep.authority.cache.PermissionCacheManager.CommonDataOfCache
import rep.sc.tpl.did.DidTplPrefix
import rep.storage.chain.preload.BlockPreload

class CertificateHashCache(cd:CommonDataOfCache,mgr:PermissionCacheManager) extends ICache(cd,mgr) {
  override protected def dataTypeConvert(any: Option[Any],blockPreload: BlockPreload): Option[Any] = {
    if(any == None){
      None
    }else{
      Some(any.get.asInstanceOf[String])
    }
  }

  def get(key:String,blockPreload: BlockPreload):Option[String]={
    val d = this.getData(key,blockPreload)
    if(d == None)
      None
    else
      Some(d.get.asInstanceOf[String])
  }

  override protected def getCacheType: String = {
    DidTplPrefix.hashPrefix
  }
}
