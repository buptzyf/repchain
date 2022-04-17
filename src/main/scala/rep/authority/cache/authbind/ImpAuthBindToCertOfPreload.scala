package rep.authority.cache.authbind



/**
 * Created by jiangbuyun on 2020/07/06.
 * 实现权限与证书绑定缓存
 */

class ImpAuthBindToCertOfPreload(sysTag:String,pd:ImpDataPreload) extends IAuthBindToCert(sysTag) {

  override protected def getDataFromStorage(key: String): Array[Byte] = {
    this.pd.GetFormCache(key)
  }
}
