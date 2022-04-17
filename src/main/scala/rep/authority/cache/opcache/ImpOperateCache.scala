package rep.authority.cache.opcache

import java.util.concurrent.ConcurrentHashMap
import rep.crypto.Sha256
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
    var od : opData = null
    val opid = Sha256.hashstr(opname)
    if(pd == null){
      od = this.FindOp4OpName(opid)
    }else{
      var ppc = pd.getOpCache
      od = ppc.FindOp4OpName(opid)
      if(od == null)
        od = this.FindOp4OpName(opid)
    }

    od
  }
}

object ImpOperateCache {
  private implicit var singleObjs = new ConcurrentHashMap[String, ImpOperateCache]() asScala
  /**
   * @author jiangbuyun
   * @version	1.1
   * @since	2020-06-24
   * @category	根据系统名称获取操作缓存
   * @param	SystemName String 系统名称
   * @return	如果成功返回ImpOperateCache实例，否则为null
   */
  def GetOperateCache(SystemName: String): ImpOperateCache = {
    var singleObj: ImpOperateCache = null
    synchronized {
      if (singleObjs.contains(SystemName)) {
        singleObj = singleObjs.get(SystemName).getOrElse(null)
      } else {
        singleObj = new ImpOperateCache(SystemName)
        singleObjs.put(SystemName, singleObj)
      }
      singleObj
    }
  }
}
