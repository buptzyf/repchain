package rep.authority.cache.signercache

import java.util.concurrent.ConcurrentHashMap


import scala.collection.JavaConverters._

/**
 * Created by jiangbuyun on 2020/06/24.
 * 实现实体账户缓存
 */


class ImpSignerCache(sysTag:String) extends ISignerCache(sysTag) {
  import ISignerCache.signerData

  private var sr = ImpDataAccess.GetDataAccess(sysTag)

  override protected def getDataFromStorage(key: String): Array[Byte] = {
    this.sr.Get(key)
  }

  def getSignerData(did:String,pd:ImpDataPreload):signerData={
    var sd : signerData = null
    if(pd == null ){
      sd = this.FindSigner(did,pd)
    }else{
      var sad = pd.getSignerCache
      sd = sad.FindSigner(did,pd)
      if(sd == null)
        sd = this.FindSigner(did,pd)
    }

    sd
  }
}

object ImpSignerCache {
  private implicit var singleObjs = new ConcurrentHashMap[String, ImpSignerCache]() asScala
  /**
   * @author jiangbuyun
   * @version	1.1
   * @since	2020-06-24
   * @category	根据系统名称获取实体账户缓存
   * @param	SystemName String 系统名称
   * @return	如果成功返回ImpSignerCache实例，否则为null
   */
  def GetSignerCache(SystemName: String): ImpSignerCache = {
    var singleObj: ImpSignerCache = null
    synchronized {
      if (singleObjs.contains(SystemName)) {
        singleObj = singleObjs.get(SystemName).getOrElse(null)
      } else {
        singleObj = new ImpSignerCache(SystemName)
        singleObjs.put(SystemName, singleObj)
      }
      singleObj
    }
  }
}