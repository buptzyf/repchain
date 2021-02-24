package rep.authority.cache.opcache

import java.util.concurrent.ConcurrentHashMap

import rep.storage.ImpDataPreload

import scala.collection.JavaConverters._
import scala.concurrent.Future

class ImpOperateCacheOfPreload(sysTag:String,dp:ImpDataPreload) extends IOperateCache(sysTag) {
  import rep.authority.cache.opcache.IOperateCache.opData

  private implicit var op_read_cache = new ConcurrentHashMap[String, Future[Option[opData]]] asScala
  private implicit var opname_read_cache = new ConcurrentHashMap[String, String] asScala

  override protected def getDataFromStorage(key: String): Array[Byte] = {
    this.dp.GetFormCache(key)
  }

}
