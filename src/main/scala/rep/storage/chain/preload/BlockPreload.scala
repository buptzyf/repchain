package rep.storage.chain.preload


import java.util.concurrent.ConcurrentHashMap
import rep.app.system.RepChainSystemContext
import rep.log.RepLogger
import rep.storage.chain.block.BlockSearcher
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
  private val update :ConcurrentHashMap[String,Option[Any]] = new ConcurrentHashMap[String,Option[Any]]
  private val readCache  :ConcurrentHashMap[String,Option[Any]] = new ConcurrentHashMap[String,Option[Any]]
  private val transactionPreloads:ConcurrentHashMap[String,TransactionPreload] = new ConcurrentHashMap[String,TransactionPreload]

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
  def getFromCache(key : String):Option[Any]={
    var ro : Option[Any] = None
    try{
      if(this.update.containsKey(key)){
       ro = this.update.get(key)
      }
    }catch{
      case e:Exception =>{
        RepLogger.error(RepLogger.Storager_Logger,
          s"BlockPreload get data from update Except, systemName=${this.ctx.getSystemName},msg=${e.getCause}")
        throw e
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
  def getFromDB[T:ClassTag](key : String):Option[T]={
    var ro : Option[T] = None
    try{
      val tmp = this.getObject(key)
      if(tmp != None){
        if(tmp.get.isInstanceOf[T]){
          ro = Some(ro.get.asInstanceOf[T])
        }
      }
    }catch{
      case e:Exception =>{
        RepLogger.error(RepLogger.Storager_Logger,
          s"BlockPreload get data from db Except, systemName=${this.ctx.getSystemName},msg=${e.getCause}")
        throw e
      }
    }
    ro
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	获取指定的键值,优先从缓存中获取；如果缓存不存在，再从数据库中获取
   * @param	key String 指定的键
   * @return	返回对应键的值 Option[Any]
   * */
  def get(key : String):Option[Any]={
    var ro : Option[Any] = None
    try{
      if(this.update.containsKey(key)){
        ro = this.update.get(key)
      }else if(this.readCache.containsKey(key)){
        ro = this.readCache.get(key)
      }else {
        ro = this.getObject(key)
        if(ro != None) {
          this.readCache.put(key,ro)
        }else{
          None
        }
      }
    }catch{
      case e:Exception =>{
        RepLogger.error(RepLogger.Storager_Logger,
          s"BlockPreload get data Except, systemName=${this.ctx},msg=${e.getCause}")
        throw e
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
  def put (key : String,any:Any):Boolean={
    var b : Boolean = false
    try{
      key match{
        case null =>
          RepLogger.error(RepLogger.Storager_Logger,
            s"BlockPreload put data Except, systemName=${this.ctx},msg=key is null")
        case _ =>
          val o = if(any == null) None else Some(any)
          this.update.put(key,o)
          b = true
      }
    }catch{
      case e:Exception =>{
        RepLogger.error(RepLogger.Storager_Logger,
          s"BlockPreload put data Except, systemName=${this.ctx},msg=${e.getCause}")
        throw e
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
