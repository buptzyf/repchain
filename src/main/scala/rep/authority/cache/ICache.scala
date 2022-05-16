package rep.authority.cache

import java.util.concurrent.ConcurrentHashMap

import com.googlecode.concurrentlinkedhashmap.{ConcurrentLinkedHashMap, Weighers}
import rep.app.system.RepChainSystemContext
import scala.concurrent.ExecutionContext.Implicits.global
import rep.log.RepLogger
import rep.proto.rc2.ChaincodeId
import rep.storage.chain.KeyPrefixManager
import rep.storage.chain.preload.BlockPreload
import rep.storage.db.factory.DBFactory
import rep.utils.IdTool
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

abstract class ICache(ctx : RepChainSystemContext) {
  final protected val splitSign = "_"
  final private val config = ctx.getConfig
  final private val cid = ChaincodeId(config.getAccountContractName,config.getAccountContractVersion)
  final val common_prefix : String = KeyPrefixManager.getWorldStateKeyPrefix(
                                config,cid.chaincodeName)
  final private val db  = DBFactory.getDBAccess(config)
  final protected val cacheMaxSize = config.getAccountCacheSize
  final protected implicit val cache = new ConcurrentLinkedHashMap.Builder[String, Option[Any]]()
                                          .maximumWeightedCapacity(cacheMaxSize)
                                          .weigher(Weighers.singleton[Option[Any]]).build
  final protected implicit val reader = new ConcurrentHashMap[String,Future[Option[Any]]]()

  //负责数据格式转换
  protected def dataTypeConvert(any:Option[Any],blockPreload: BlockPreload):Option[Any]
  protected def getPrefix:String

  private def readData(key:String,blockPreload: BlockPreload):Option[Any]={
    var r = this.cache.getOrDefault(key,None)
    if(r == None){
      RepLogger.Permission_Logger.trace(s"ICache.readData asynchronous read,key=${key}")
      asynchronousReadData(key,blockPreload)
    }else{
      RepLogger.Permission_Logger.trace(s"ICache.readData cache read,key=${key},data=${r}")
      r
    }
  }

  private def asynchronousReadData(key:String,blockPreload: BlockPreload): Option[Any] ={
    var r : Option[Any] = None
    try{
      var dr : Future[Option[Any]] = this.reader.get(key)
      if(dr == null){
        //对于相同的key对应值的获取，保证系统只有一个线程在读，不会生成多个线程，防止缓存被众多线程击穿
        dr = asynchronousHandleData(key,blockPreload)
        this.reader.putIfAbsent(key,dr)
      }
      //线程等待获取数据
      r = Await.result(dr, 5.seconds).asInstanceOf[Option[Any]]
      RepLogger.Permission_Logger.trace(s"ICache.asynchronousReadData,key=${key},data=${r}")
    }finally {
      this.reader.remove(key)
    }
    r
  }

  private def asynchronousHandleData(key:String,blockPreload: BlockPreload):Future[Option[Any]]=Future{
    val r = this.dataTypeConvert(this.db.getObject(this.getPrefix+key),blockPreload)
    if(r != None){
      //将读取的数据写入缓存
      this.cache.put(key,r)
    }
    RepLogger.Permission_Logger.trace(s"ICache.asynchronousHandleData,key=${key},data=${r}")
    r
  }

  def updateCache(key:String): Unit={
    val idx = key.lastIndexOf("-")
    if(idx > 0){
      this.cache.remove(key.substring(idx+1))
      RepLogger.Permission_Logger.trace(s"ICache.updateCache update cache data,key=${key}")
    }
  }

  protected def getData(key:String,blockPreload: BlockPreload):Option[Any]={
    if(blockPreload != null){
      //在预执行中获取，如果预执行中没有找到，再到缓存中获取
      val pd = dataTypeConvert(blockPreload.getObjectFromCache(this.getPrefix+key),blockPreload)
      if(pd == None){
        readData(key,blockPreload)
      }else{
        RepLogger.Permission_Logger.trace(s"ICache.getData preload read,key=${key},data=${pd}")
        pd
      }
    }else{
      //直接在缓存中获取
      readData(key,blockPreload)
    }
  }
}
