package rep.authority.cache.signercache


import rep.storage.ImpDataPreload

/**
 * Created by jiangbuyun on 2020/06/24.
 * 实现实体账户缓存
 */

class ImpSignerCacheOfPreload(sysTag:String,pd:ImpDataPreload) extends ISignerCache(sysTag) {

  override protected def getDataFromStorage(key: String): Array[Byte] = {
    this.pd.GetFormCache(key)
  }
}
