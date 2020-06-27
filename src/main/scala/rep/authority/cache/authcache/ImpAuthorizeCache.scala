package rep.authority.cache.authcache

import java.util.concurrent.ConcurrentHashMap
import rep.storage.{ImpDataAccess, ImpDataPreload}
import scala.collection.JavaConverters._

/**
 * Created by jiangbuyun on 2020/06/24.
 * 实现权限缓存
 */
class ImpAuthorizeCache(sysTag:String) extends IAuthorizeCache(sysTag) {
  import rep.authority.cache.authcache.IAuthorizeCache.authData

  private var sr = ImpDataAccess.GetDataAccess(sysTag)

  override protected def getDataFromStorage(key: String): Array[Byte] = {
    this.sr.Get(key)
  }

  def getAuthorizeData(authid:String,pd:ImpDataPreload):authData={
    var pad = pd.getAuthCache
    var ad = pad.FindAuthorize(authid)
    if(ad == null)
      ad = this.FindAuthorize(authid)
    ad
  }

}

object ImpAuthorizeCache {
  private implicit var singleobjs = new ConcurrentHashMap[String, ImpAuthorizeCache]() asScala
  /**
   * @author jiangbuyun
   * @version	1.1
   * @since	2020-06-24
   * @category	根据系统名称获取权限缓存实例
   * @param	SystemName String 系统名称
   * @return	如果成功返回ImpAuthorizeCache实例，否则为null
   */
  def GetAuthorizeCache(SystemName: String): ImpAuthorizeCache = {
    var singleobj: ImpAuthorizeCache = null
    synchronized {
      if (singleobjs.contains(SystemName)) {
        singleobj = singleobjs.get(SystemName).getOrElse(null)
      } else {
        singleobj = new ImpAuthorizeCache(SystemName)
        singleobjs.put(SystemName, singleobj)
      }
      singleobj
    }
  }
}
