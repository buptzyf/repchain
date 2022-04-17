package rep.authority.cache.authcache



/**
 * Created by jiangbuyun on 2020/06/24.
 * 实现权限缓存
 */

class ImpAuthorizeCacheOfPreload(sysTag:String,pd:ImpDataPreload) extends IAuthorizeCache(sysTag) {

  override protected def getDataFromStorage(key: String): Array[Byte] = {
    this.pd.GetFormCache(key)
  }
}
