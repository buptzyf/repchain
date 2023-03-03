package rep.storage.chain.preload


import java.util.concurrent.ConcurrentHashMap

import rep.app.system.RepChainSystemContext
import rep.log.RepLogger
import rep.storage.chain.block.BlockSearcher
import rep.utils.SerializeUtils

import scala.collection.mutable
import scala.reflect.ClassTag

/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-13
 * @category	区块预执行。
 * */
class BlockPreload(preloadId:String,ctx:RepChainSystemContext,isEncrypt:Boolean=false)
                                      extends BlockSearcher(ctx,isEncrypt){
  private val update :ConcurrentHashMap[String,Array[Byte]] = new ConcurrentHashMap[String,Array[Byte]]
  private val delete :ConcurrentHashMap[String,Array[Byte]] = new ConcurrentHashMap[String,Array[Byte]]
  private val readCache  :ConcurrentHashMap[String,Array[Byte]] = new ConcurrentHashMap[String,Array[Byte]]
  private val transactionPreloads:ConcurrentHashMap[String,TransactionPreload] = new ConcurrentHashMap[String,TransactionPreload]

  def getCurrentChainNetName:String={
    this.ctx.getConfig.getChainNetworkId
  }


  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	获取交易预执行
   * @param tid:String 交易Id
   * @return 返回TransactionPreload交易预执行实例
   * */
  def getTransactionPreload(tid:String):TransactionPreload={
    synchronized{
      if(this.transactionPreloads.containsKey(tid)){
        this.transactionPreloads.get(tid)
      }else{
        val tp = new TransactionPreload(tid,this)
        this.transactionPreloads.put(tid,tp)
        tp
      }
    }
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-12
   * @category	只从缓存中获取获取指定的键值，缓存不存在，不会从数据库中获取数据
   * @param	key String 指定的键
   * @return	返回对应键的值 Array[Byte]
   * */
  def getFromCache(key : String):Array[Byte]={
    var ro : Array[Byte] = null
    try{
      if(this.delete.containsKey(key)){
        ro = null
      }else if(this.update.containsKey(key)){
       ro = this.update.get(key)
      }
    }catch{
      case e:Exception =>{
        RepLogger.error(RepLogger.Storager_Logger,
          s"BlockPreload get data from update Except, systemName=${this.ctx.getSystemName},msg=${e.getCause}")
      }
    }
    ro
  }


  def getObjectFromCache[T](key : String):Option[T]={
    var ro : Option[T] = None
    try{
      val ob = this.getFromCache(key)
      if(ob != null){
        val tmp = SerializeUtils.deserialise(ob)
        if(tmp != null){
          ro = Some(tmp.asInstanceOf[T])
        }
      }
    }catch{
      case e:Exception =>{
        RepLogger.error(RepLogger.Storager_Logger,
          s"BlockPreload get data from update Except, systemName=${this.ctx.getSystemName},msg=${e.getCause}")
      }
    }
    ro
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	从数据库中直接获取指定的键值
   * @param	key String 指定的键
   * @return	返回对应键的值 Option[T]
   * */
  def getFromDB(key : String):Array[Byte]={
    var ro : Array[Byte] = null
    try{
      ro = this.getBytes(key)
    }catch{
      case e:Exception =>{
        RepLogger.error(RepLogger.Storager_Logger,
          s"BlockPreload get data from db Except, systemName=${this.ctx.getSystemName},msg=${e.getCause}")
      }
    }
    ro
  }

  def getObjectFromDB[T](key : String):Option[T]={
    this.getObjectForClass[T](key)
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	获取指定的键值,优先从缓存中获取；如果缓存不存在，再从数据库中获取
   * @param	key String 指定的键
   * @return	返回对应键的值 Option[Any]
   * */
  def get(key : String):Array[Byte]={
    var ro : Array[Byte] = null
    try{
      if(this.delete.containsKey(key)){
        ro = null
      }else if(this.update.containsKey(key)){
        ro = this.update.get(key)
      }else if(this.readCache.containsKey(key)){
        ro = this.readCache.get(key)
      }else {
        ro = this.getBytes(key)
        if(ro != null) {
          this.readCache.put(key,ro)
        }
      }
    }catch{
      case e:Exception =>{
        RepLogger.error(RepLogger.Storager_Logger,
          s"BlockPreload get data Except, systemName=${this.ctx},msg=${e.getCause}")
      }
    }
    ro
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	存储指定的键和值到数据库
   * @param	key String 指定的键，bb Array[Byte] 要存储的值
   * @return	返回成功或者失败 Boolean
   * */
  def put (key : String,value:Array[Byte]):Boolean={
    var b : Boolean = false
    try{
      key match{
        case null =>
          RepLogger.error(RepLogger.Storager_Logger,
            s"BlockPreload put data Except, systemName=${this.ctx},msg=key is null")
        case _ =>
          if(value == null) throw new Exception("value is null")
          this.update.put(key,value)
          if(this.delete.containsKey(key)) this.delete.remove(key)
          b = true
      }
    }catch{
      case e:Exception =>{
        RepLogger.error(RepLogger.Storager_Logger,
          s"BlockPreload put data Except, systemName=${this.ctx},msg=${e.getCause}")
      }
    }
    b
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	删除指定的键和值到数据库
   * @param	key String 指定的键，bb Array[Byte] 要存储的值
   * @return	返回成功或者失败 Boolean
   * */
  def del (key : String,value:Array[Byte]):Boolean={
    var b : Boolean = false
    try{
      key match{
        case null =>
          RepLogger.error(RepLogger.Storager_Logger,
            s"BlockPreload del data Except, systemName=${this.ctx},msg=key is null")
        case _ =>
          if(value == null) throw new Exception("value is null")
          this.delete.put(key,value)
          if(this.update.containsKey(key)) this.update.remove(key)
          b = true
      }
    }catch{
      case e:Exception =>{
        RepLogger.error(RepLogger.Storager_Logger,
          s"BlockPreload del data Except, systemName=${this.ctx},msg=${e.getCause}")
      }
    }
    b
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	清除缓存
   * @param
   * @return
   * */
  def free:Unit={
    this.transactionPreloads.clear()
    this.update.clear()
    this.delete.clear()
    this.readCache.clear()
  }
}

/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-13
 * @category	区块预执行实例管理
 * */
/*
object BlockPreload{
  private var blockPreloadInstances = new mutable.HashMap[String, BlockPreload]()

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据实例id获取交易预执行实例
   * @param instanceId:String 实例id,systemName:String 系统名称
   * @return 返回BlockPreload预执行实例
   * */
  def getBlockPreload(instanceId:String,systemName:String):BlockPreload={
    var instance: BlockPreload = null
    val key = systemName + "-" + instanceId
    synchronized {
      if (blockPreloadInstances.contains(key)) {
        instance = blockPreloadInstances(key)
      } else {
        instance = new BlockPreload(instanceId,systemName)
        blockPreloadInstances.put(key, instance)
      }
      instance
    }
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据实例id释放交易预执行实例
   * @param instanceId:String 实例id,systemName:String 系统名称
   * @return
   * */
  def freeInstance(instanceId:String,systemName:String):Unit={
    try{
      val key = systemName + "-" + instanceId
      val instance = this.blockPreloadInstances(key)
      if(instance != null){
        instance.free
      }
      this.blockPreloadInstances.remove(key)
    }catch {
      case e:Exception =>
        RepLogger.info(RepLogger.Storager_Logger,s"free preload instance failed,instanceId=${instanceId}," +
          s"systemName=${systemName},msg=${e.getCause}")
    }
  }
}

 */
