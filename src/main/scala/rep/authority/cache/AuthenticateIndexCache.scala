package rep.authority.cache

import rep.authority.cache.PermissionCacheManager.CommonDataOfCache
import rep.log.RepLogger
import rep.sc.tpl.did.DidTplPrefix
import rep.storage.chain.preload.BlockPreload
import rep.utils.IdTool

class AuthenticateIndexCache(cd:CommonDataOfCache,mgr:PermissionCacheManager) extends ICache(cd,mgr) {
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

  override protected def getCacheType: String = {
    DidTplPrefix.authIdxPrefix
  }

  override protected def readData(key: String, blockPreload: BlockPreload): Option[Any] = {
    this.readDataFromMultiChain(key,blockPreload)
  }

  def get(key:String,blockPreload: BlockPreload):Option[Array[String]]={
    val d = this.getData(key,blockPreload)
    if(d == None)
      None
    else
      Some(d.get.asInstanceOf[Array[String]])
  }
}
