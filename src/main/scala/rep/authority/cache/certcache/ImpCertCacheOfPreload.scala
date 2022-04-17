package rep.authority.cache.certcache


/**
 * Created by jiangbuyun on 2020/07/5.
 * 实现证书缓存
 */

class ImpCertCacheOfPreload (sysTag:String,pd:ImpDataPreload) extends ICertCache(sysTag) {

  override protected def getDataFromStorage(key: String): Array[Byte] = {
    this.pd.GetFormCache(key)
  }

}