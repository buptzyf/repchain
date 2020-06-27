package rep.authority.cache.opcache

import java.util.concurrent.ConcurrentHashMap
import rep.crypto.Sha256
import rep.storage.{ImpDataAccess, ImpDataPreload}
import scala.collection.JavaConverters._

/**
 * Created by jiangbuyun on 2020/06/24.
 * 实现操作缓存
 */

class ImpOperateCache(sysTag:String)extends IOperateCache(sysTag) {
  import IOperateCache.opData
  private var sr = ImpDataAccess.GetDataAccess(sysTag)

  override protected def getDataFromStorage(key: String): Array[Byte] = {
    this.sr.Get(key)
  }

  def getOperateData(opname:String,pd:ImpDataPreload):opData={
    val opid = Sha256.hashstr(opname)
    var ppc = pd.getOpCache
    var od = ppc.FindOp4OpName(opid)
    if(od == null)
      od = this.FindOp4OpName(opid)
    od
  }
}

object ImpOperateCache {
  private implicit var singleobjs = new ConcurrentHashMap[String, ImpOperateCache]() asScala
  /**
   * @author jiangbuyun
   * @version	1.1
   * @since	2020-06-24
   * @category	根据系统名称获取操作缓存
   * @param	SystemName String 系统名称
   * @return	如果成功返回ImpOperateCache实例，否则为null
   */
  def GetOperateCache(SystemName: String): ImpOperateCache = {
    var singleobj: ImpOperateCache = null
    synchronized {
      if (singleobjs.contains(SystemName)) {
        singleobj = singleobjs.get(SystemName).getOrElse(null)
      } else {
        singleobj = new ImpOperateCache(SystemName)
        singleobjs.put(SystemName, singleobj)
      }
      singleobj
    }
  }
}
