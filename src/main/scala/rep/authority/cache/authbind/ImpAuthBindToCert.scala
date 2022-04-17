package rep.authority.cache.authbind

import java.util.concurrent.ConcurrentHashMap

import scala.collection.JavaConverters._

/**
 * Created by jiangbuyun on 2020/07/06.
 * 实现权限与证书绑定缓存
 */
class ImpAuthBindToCert (sysTag:String) extends IAuthBindToCert(sysTag) {

  private var sr = ImpDataAccess.GetDataAccess(sysTag)

  override protected def getDataFromStorage(key: String): Array[Byte] = {
    this.sr.Get(key)
  }

  def hasAuthBindToCert(authid:String,certid:String,pd:ImpDataPreload):Boolean={
    var r = false
    if(pd == null){
      r = this.FindBindCertToAuthorize(authid,certid)
    }else{
      var pad = pd.getAuthBindCertCache
      r = pad.FindBindCertToAuthorize(authid,certid)
      if(r == false)
        r = this.FindBindCertToAuthorize(authid,certid)
    }

    r
  }

}

object ImpAuthBindToCert {
  private implicit var singleObjs = new ConcurrentHashMap[String, ImpAuthBindToCert]() asScala
  /**
   * @author jiangbuyun
   * @version	1.1
   * @since	2020-06-24
   * @category	根据系统名称获取权限缓存实例
   * @param	SystemName String 系统名称
   * @return	如果成功返回ImpAuthorizeCache实例，否则为null
   */
  def GetAuthBindToCertCache(SystemName: String): ImpAuthBindToCert = {
    var singleObj: ImpAuthBindToCert = null
    synchronized {
      if (singleObjs.contains(SystemName)) {
        singleObj = singleObjs.get(SystemName).getOrElse(null)
      } else {
        singleObj = new ImpAuthBindToCert(SystemName)
        singleObjs.put(SystemName, singleObj)
      }
      singleObj
    }
  }
}

