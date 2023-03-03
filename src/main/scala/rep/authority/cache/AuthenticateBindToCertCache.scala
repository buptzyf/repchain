package rep.authority.cache

import rep.authority.cache.PermissionCacheManager.CommonDataOfCache
import rep.sc.tpl.did.DidTplPrefix
import rep.storage.chain.preload.BlockPreload
import rep.utils.IdTool

class AuthenticateBindToCertCache(cd:CommonDataOfCache,mgr:PermissionCacheManager) extends ICache(cd,mgr) {
  override protected def dataTypeConvert(any: Option[Any],blockPreload: BlockPreload): Option[Any] = {
    if(any == None){
      None
    }else{
      Some(any.get.asInstanceOf[Boolean])
    }
  }

  def get(authid:String,certid:String,blockPreload: BlockPreload):Option[Boolean]={
    get(authid+"-"+certid,blockPreload)
  }

  def get(key:String,blockPreload: BlockPreload):Option[Boolean]={
    val d = this.getData(key,blockPreload)
    if(d == None)
      None
    else
      Some(d.get.asInstanceOf[Boolean])
  }

  override protected def getCacheType: String = {
    DidTplPrefix.bindPrefix
  }

}
