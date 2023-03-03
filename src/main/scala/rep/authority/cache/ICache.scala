package rep.authority.cache

import java.util.concurrent.ConcurrentHashMap
import com.googlecode.concurrentlinkedhashmap.{ConcurrentLinkedHashMap, Weighers}
import rep.authority.cache.PermissionCacheManager.CommonDataOfCache
import scala.util.control.Breaks._
import scala.concurrent.ExecutionContext.Implicits.global
import rep.log.RepLogger
import rep.sc.tpl.did.DidTplPrefix
import rep.storage.chain.KeyPrefixManager
import rep.storage.chain.preload.BlockPreload
import rep.utils.IdTool
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

abstract class ICache(cd:CommonDataOfCache,mgr:PermissionCacheManager) {
  final protected val cid = cd.cid
  final protected val db = cd.db
  final protected val cacheMaxSize = cd.cacheSize
  final protected val baseNetName = cd.baseNetName
  final protected val baseNetPrefix = KeyPrefixManager.getCustomNetKeyPrefix(
                                      this.baseNetName,cid.chaincodeName)
  final protected implicit val cache = new ConcurrentLinkedHashMap.Builder[String, Option[Any]]()
    .maximumWeightedCapacity(cacheMaxSize)
    .weigher(Weighers.singleton[Option[Any]]).build
  final protected implicit val reader = new ConcurrentHashMap[String, Future[Option[Any]]]()

  final protected val businessNets = new ArrayBuffer[String]()

  def registerBusinessNet(bn:String):Unit={
    synchronized(
      if(!baseNetName.equalsIgnoreCase(bn)){
        if(!businessNets.contains(bn)){
          businessNets += bn
        }
    })
  }



  //负责数据格式转换
  protected def dataTypeConvert(any: Option[Any], blockPreload: BlockPreload): Option[Any]

  protected def getCacheType: String

  protected def readDataFromMultiChain(key:String, blockPreload: BlockPreload):Option[Any] ={
    var r = this.cache.getOrDefault(key, None)
    if (r == None) {
      RepLogger.Permission_Logger.trace(s"OperateIndexCache.readData asynchronous read,from IdentityNet,key=${key}")
      //获取数据方式，0：从基础链获取；1：从业务链获取；
      var base = this.dataTypeConvert(this.db.getObject(baseNetPrefix + IdTool.WorldStateKeySeparator + getCacheType + key), blockPreload)

      this.businessNets.foreach(netName=>{
        val business = this.dataTypeConvert(this.db.getObject(KeyPrefixManager.getCustomNetKeyPrefix(
          netName,cid.chaincodeName) + IdTool.WorldStateKeySeparator + getCacheType + key), blockPreload)
        if(business != None){
          if(base == None){
            base = business
          }else{
            val base1 = base.get.asInstanceOf[Array[String]]
            val business1 = business.get.asInstanceOf[Array[String]]
            base = Some(Array.concat(base1, business1))
          }
        }
      })
      r = base
      if (r != None) {
        this.cache.put(key, r)
        RepLogger.Permission_Logger.trace(s"OperateIndexCache.readData ,key=${key}，value=${r.get.asInstanceOf[Array[String]]}")
      }
    }
    r
  }

  protected def readData(key: String, blockPreload: BlockPreload): Option[Any] = {
    val r = this.cache.getOrDefault(key, None)
    if (r == None) {
      RepLogger.Permission_Logger.trace(s"ICache.readData asynchronous read,from IdentityNet,key=${key}")
      //获取数据方式，0：从基础链获取；1：从业务链获取；
      var data = asynchronousReadData(key, blockPreload,baseNetPrefix + IdTool.WorldStateKeySeparator + getCacheType)
      if(data == None){
        breakable({
          this.businessNets.foreach(netName=>{
            val prefix = KeyPrefixManager.getCustomNetKeyPrefix(
                          netName,cid.chaincodeName) + IdTool.WorldStateKeySeparator + getCacheType
            data = asynchronousReadData(key, blockPreload,prefix)
            RepLogger.Permission_Logger.trace(s"ICache.readData asynchronous read,from Business,key=${key}")
            if(data != None) break
          })
        })
      }
      data
    } else {
      RepLogger.Permission_Logger.trace(s"ICache.readData cache read,key=${key},data=${r}")
      r
    }
  }

  private def asynchronousReadData(key: String, blockPreload: BlockPreload,prefix:String): Option[Any] = {
    var r: Option[Any] = None
    try {
      var dr: Future[Option[Any]] = this.reader.get(key)
      if (dr == null) {
        //对于相同的key对应值的获取，保证系统只有一个线程在读，不会生成多个线程，防止缓存被众多线程击穿
        dr = asynchronousHandleData(key, blockPreload, prefix)
        val old = this.reader.putIfAbsent(key, dr)
        if (old != null) {
          dr = old
        }
      }
      //线程等待获取数据
      r = Await.result(dr, 5.seconds).asInstanceOf[Option[Any]]
      RepLogger.Permission_Logger.trace(s"ICache.asynchronousReadData,key=${key},data=${r}")
    } finally {
      this.reader.remove(key)
    }
    r
  }

  private def asynchronousHandleData(key: String, blockPreload: BlockPreload,prefix :String): Future[Option[Any]] = Future {
    val r = this.dataTypeConvert(this.db.getObject(prefix + key), blockPreload)
    if (r != None) {
      //将读取的数据写入缓存
      this.cache.put(key, r)
    }
    RepLogger.Permission_Logger.trace(s"ICache.asynchronousHandleData,key=${key},data=${r}")
    r
  }

  def updateCache(key: String): Unit = {
    var pk = key
      val splitString =
                        IdTool.WorldStateKeySeparator + this.getCacheType
      val idx = key.lastIndexOf(splitString)
      if (idx > 0)
        pk = key.substring(idx + splitString.length)
      if(this.getCacheType.equalsIgnoreCase(DidTplPrefix.authIdxPrefix)){
        val signer = mgr.getCache(DidTplPrefix.signerPrefix)
        signer.updateCache(key.substring(0,idx+1)+DidTplPrefix.signerPrefix+
          pk.substring(0,pk.indexOf(DidTplPrefix.authIdxSuffix)))
        RepLogger.Permission_Logger.trace(s"ICache.updateCache update cache data,key=${key},pk=${pk},signer=${DidTplPrefix.signerPrefix+pk.substring(0,pk.indexOf(DidTplPrefix.authIdxSuffix))}")
      }

    this.cache.remove(pk)
    RepLogger.Permission_Logger.trace(s"ICache.updateCache update cache data,key=${key},pk=${pk}")
  }

  /*protected def readDataOfRealtime(key: String, blockPreload: BlockPreload): Option[Any] = {
    synchronized {
      val r = this.dataTypeConvert(this.db.getObject(this.getPrefix + key), blockPreload)
      if (r != None) {
        //将读取的数据写入缓存
        this.cache.put(key, r)
      }
      RepLogger.Permission_Logger.trace(s"ICache.read data in realtime,key=${key},data=${r}")
      r
    }
  }*/

  protected def getData(key: String, blockPreload: BlockPreload): Option[Any] = {
    if (blockPreload != null) {
      //在预执行中获取，如果预执行中没有找到，再到缓存中获取
      val pd = dataTypeConvert(blockPreload.getObjectFromCache(KeyPrefixManager.getCustomNetKeyPrefix(
                                  blockPreload.getCurrentChainNetName,cid.chaincodeName)
                                  + IdTool.WorldStateKeySeparator + getCacheType + key), blockPreload)
      if (pd == None) {
        readData(key, blockPreload)
        //readDataOfRealtime(key,blockPreload)
      } else {
        RepLogger.Permission_Logger.trace(s"ICache.getData preload read,key=${key},data=${pd}")
        pd
      }
    } else {
      //直接在缓存中获取
      readData(key, blockPreload)
      //readDataOfRealtime(key,blockPreload)
    }
  }
}
