package rep.authority.cache.certcache

import java.util.concurrent.ConcurrentHashMap

import rep.storage.{ImpDataAccess, ImpDataPreload}

import scala.collection.JavaConverters._

/**
 * Created by jiangbuyun on 2020/07/5.
 * 实现证书缓存
 */

class ImpCertCache(sysTag:String) extends ICertCache(sysTag) {
  import ICertCache.certData

  private var sr = ImpDataAccess.GetDataAccess(sysTag)

  override protected def getDataFromStorage(key: String): Array[Byte] = {
    this.sr.Get(key)
  }

  def getCertificateData(certid:String,pd:ImpDataPreload):certData={
    var cd : certData = null
    if(pd == null){
      cd = this.FindCertificate(certid)
    }else{
      val pcd = pd.getCertCache
      cd = pcd.FindCertificate(certid)
      if(cd == null)
        cd = this.FindCertificate(certid)
    }

    cd
  }

  def getCertId4CertHash(certHash:String,pd:ImpDataPreload):String={
    var cd : String = null
    if(pd == null){
      cd = this.FindCertId4CertHash(certHash)
    }else{
      val pcd = pd.getCertCache
      cd = pcd.FindCertId4CertHash(certHash)
      if(cd == null)
        cd = this.FindCertId4CertHash(certHash)
    }

    cd
  }


}

object ImpCertCache {
  private implicit var singleObjs = new ConcurrentHashMap[String, ImpCertCache]() asScala
  /**
   * @author jiangbuyun
   * @version	1.1
   * @since	2020-06-24
   * @category	根据系统名称获取证书缓存实例
   * @param	SystemName String 系统名称
   * @return	如果成功返回ImpCertCache实例，否则为null
   */
  def GetCertCache(SystemName: String): ImpCertCache = {
    var singleObj: ImpCertCache = null
    synchronized {
      if (singleObjs.contains(SystemName)) {
        singleObj = singleObjs.get(SystemName).getOrElse(null)
      } else {
        singleObj = new ImpCertCache(SystemName)
        singleObjs.put(SystemName, singleObj)
      }
      singleObj
    }
  }
}

