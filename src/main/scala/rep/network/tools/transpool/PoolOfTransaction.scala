package rep.network.tools.transpool

import java.util
import java.util.concurrent.atomic.{AtomicLong, LongAdder}
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import rep.app.system.RepChainSystemContext
import rep.proto.rc2.Transaction
import rep.storage.chain.block.BlockSearcher
import rep.storage.db.common.ITransactionCallback
import rep.storage.db.factory.DBFactory
import rep.utils.SerializeUtils
import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks.{break, breakable}

/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-15
 * @category	交易缓存池。
 * */
class PoolOfTransaction(ctx:RepChainSystemContext) {
  final private val cache_transaction_serial = ctx.getConfig.getSystemName+"-pl-serial"
  final private val cache_transaction_tx_serial_prefix = ctx.getConfig.getSystemName+"-pl-tx-serial-"
  final private val cache_transaction_serial_tx_prefix = ctx.getConfig.getSystemName+"-pl-serial-tx-"
  //交易缓存池可以缓存的最大交易数
  final private val cacheMaxSize : Int = ctx.getConfig.getMaxCacheNumberOfTransaction
  //单个区块最大的交易数
  final private val maxNumberOfBlock : Int = ctx.getConfig.getLimitTransactionNumberOfBlock
  //是否持久化交易缓存池的交易到数据库
  final private val isPersistenceTxToDB : Boolean = ctx.getConfig.isPersistenceTransactionToDB
  final private val txPrefix = "tx-buffer-on-shutdown"
  //交易缓存池的交易计数器
  final private val transactionCount : LongAdder = new LongAdder()
  transactionCount.reset()
  //区块数据查询器，主要在数据存储中根据交易id查找交易是否已经入块
  final private val searcher : BlockSearcher = ctx.getBlockSearch
  //交易排序队列，存放内容为交易的id，保证交易进入缓存池的顺序，出块是按照这个顺序打包到预出块中
  final private val transactionOrder : ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String]()
  //被预出块打包的交易排序队列，存放内容为交易的id，在预出块打包失败之后，重新打包预出块是优先从该队列中获取要打包的交易
  final private val packagedTransactionOrder : ConcurrentLinkedQueue[String] = new ConcurrentLinkedQueue[String]()
  //交易缓存池，该缓存池是线程安全
  final private implicit val transactionCaches = new ConcurrentHashMap[String,Transaction]()

  final private val serialOfTransaction : AtomicLong = new AtomicLong(0l)
  final val db = DBFactory.getDBAccess(ctx.getConfig)

  loadTransactionFromLevelDB

  final private def loadTransactionFromLevelDB:Unit={
    val last = this.db.getObject[Long](this.cache_transaction_serial)
    if(last == None){
      this.serialOfTransaction.set(0l)
    }else{
      this.serialOfTransaction.set(last.get)
      val trs = new ArrayBuffer[Transaction]()
      var limit = last.get
      var tb = this.db.getBytes(this.cache_transaction_serial_tx_prefix+last.get)
      while (tb != null){
        val t = Transaction.parseFrom(tb)
        if(this.isExist(t.id)){
          this.deleteLocalStore(t)
        }else{
          trs += t
        }
        limit -= 1
        if(limit >= 0)
          tb = this.db.getBytes(this.cache_transaction_serial_tx_prefix+limit)
        else
          tb = null
      }
      if(trs.length > 0){
        this.addTransactionToCaches(trs,false)
      }
    }
  }

  final private def toLocalStore(t:Transaction):Unit={
    this.db.transactionOperate(new ITransactionCallback {
      override def callback: Boolean = {
        var rb = false
        try {
          val serial = serialOfTransaction.incrementAndGet()
          db.putBytes(cache_transaction_serial, SerializeUtils.serialise(serial))
          db.putBytes(cache_transaction_serial_tx_prefix+serial,t.toByteArray)
          db.putBytes(cache_transaction_tx_serial_prefix+t.id,SerializeUtils.serialise(serial))
          rb = true
        } catch {
          case e: Exception =>
            rb = false
        }
        rb
      }
    })
  }

  final private def deleteLocalStore(t:Transaction):Unit={
    this.db.transactionOperate(new ITransactionCallback {
      override def callback: Boolean = {
        var rb = false
        try {
          val serial = db.getObject[Long](cache_transaction_tx_serial_prefix+t.id)
          if(serial != None){
            db.delete(cache_transaction_tx_serial_prefix+t.id)
            db.delete(cache_transaction_serial_tx_prefix+serial.get)
          }
          rb = true
        } catch {
          case e: Exception =>
            rb = false
        }
        rb
      }
    })
  }
  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-15
   * @category	添加单条交易到缓存池
   * @param	t:Transaction 交易
   * @return
  * */
  def addTransactionToCache(t:Transaction,isStore:Boolean=true):Unit={
    val v = this.transactionCaches.putIfAbsent(t.id,t)
    if(v == null){
      this.transactionCount.increment()
      this.transactionOrder.offer(t.id)
      if(isStore)
        this.toLocalStore(t)
    }
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-15
   * @category	添加多条交易到缓存池
   * @param	ts:Seq[Transaction] 交易序列
   * @return
   * */
  def addTransactionToCaches(ts:Seq[Transaction],isStore:Boolean=true):Unit={
    ts.foreach(t=>{
      this.addTransactionToCache(t,isStore)
    })
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-15
   * @category	打包交易到预出块
   * @param
   * @return 返回Seq[Transaction]交易序列
   * */
  def packageTransactionToBlock:Seq[Transaction]={
    val rts = new  ArrayBuffer[Transaction]()
    if(!this.packagedTransactionOrder.isEmpty){
      rts ++= getTransactionFromQueue(this.packagedTransactionOrder,this.maxNumberOfBlock)
    }
    if((this.maxNumberOfBlock - rts.length)  > 0 ){
      rts ++=  getTransactionFromQueue(this.transactionOrder,this.maxNumberOfBlock - rts.length)
    }
    rts
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-15
   * @category	从交易排序器中获取交易
   * @param ts:ConcurrentLinkedQueue[String] 交易排序器,limited:Int 限定获取交易的数目
   * @return 返回ArrayBuffer[Transaction]交易数组
   * */
  private def getTransactionFromQueue(ts:ConcurrentLinkedQueue[String],limited:Int):ArrayBuffer[Transaction]={
    val rts = new ArrayBuffer[Transaction]()
    val rts_ids = new util.ArrayList[String]()
    var count = 0
    breakable(while(!ts.isEmpty){
      if(count < limited){
        val tid = ts.poll()
        if(tid != null){
          val t = this.transactionCaches.get(tid)
          if(t != null){
            //不考虑交易在缓存池超时的情况，超时也可以入块
            if(isExist(t.id)){
              this.removeTransactionFromCache(t)
            }else{
              rts += t
              rts_ids.add(tid)
            }
            count += 1
          }
        }
      }else{
        break
      }
    })

    if(rts_ids.size() > 0 ) this.packagedTransactionOrder.addAll(rts_ids)
    rts
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-15
   * @category	根据交易Id检查交易是否已经出块
   * @param tid:String 交易Id
   * @return 交易已经出块返回true，否则false
   * */
  def isExist(tid:String):Boolean={
    var r = false
    if(this.searcher.isExistTransactionByTxId(tid)){
      r = true
    }
    r
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-15
   * @category	根据交易Id检查交易是否已经出块
   * @param tid:String 交易Id
   * @return 交易已经出块返回true，否则false
   * */
  def isExistInCache(tid:String):Boolean={
    var r = false
    if(this.transactionCaches.containsKey(tid)){
      r = true
    }
    r
  }
  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-15
   * @category	从交易缓存池中获取交易
   * @param
   * @return 返回交易，缓存池不存在返回null
   * */
  private def getTransaction(tid:String):Transaction={
    this.transactionCaches.get(tid)
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-15
   * @category	检查交易缓存池是否满
   * @param
   * @return 交易缓存池满返回true，否则false
   * */
  def hasOverflowed:Boolean={
    this.transactionCount.intValue() >= this.cacheMaxSize
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-15
   * @category	获取当前交易缓存池中缓存交易的数量
   * @param
   * @return 返回Int的交易数量
   * */
  def getCachePoolSize:Int={
    this.transactionCount.intValue()
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-15
   * @category	批量删除已经入块的交易
   * @param trs:Seq[Transaction] 待删除的交易序列
   * @return
   * */
  def removeTransactionsFromCache(trs:Seq[Transaction]):Unit = {
    trs.foreach(t=>{
      this.removeTransactionFromCache(t)
    })
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-15
   * @category	删除已经入块的交易
   * @param t:Transaction 待删除的交易
   * @return
   * */
  def removeTransactionFromCache(t:Transaction):Unit = {
    val v = this.transactionCaches.remove(t.id)
    this.deleteLocalStore(t)
    if(v != null){
      this.transactionCount.decrement()
    }
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-15
   * @category	系统停止时，保存交易缓存池的交易到数据库
   * @param
   * @return
   * */
  /*def saveCachePoolToDB:Unit={
    if(this.isPersistenceTxToDB){
      val db = DBFactory.getDBAccess(ctx.getConfig)
      var r = new ArrayBuffer[Array[Byte]]()
      this.transactionCaches.values().forEach(t=>{
        r += t.toByteArray
      })
      db.putBytes(ctx.getSystemName+"_"+txPrefix,SerializeUtils.serialise(r))
    }
    RepLogger.info(RepLogger.TransLifeCycle_Logger, s"systemname=${ctx.getSystemName},save trans to db")
  }*/

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-15
   * @category	系统启动时，从数据库中恢复交易到交易缓存池
   * @param
   * @return
   * */
  /*def restoreCachePoolFromDB:Unit={
    if (this.isPersistenceTxToDB) {
      val db = DBFactory.getDBAccess(ctx.getConfig)
      try {
        val obj = db.getBytes(ctx.getSystemName+"_"+txPrefix)
        obj match {
          case null =>
            RepLogger.info(RepLogger.TransLifeCycle_Logger, s"systemname=${ctx.getSystemName},load transaction failed from db,get data is None")
          case _ =>
            val ls = SerializeUtils.deserialise(obj).asInstanceOf[ArrayBuffer[Array[Byte]]]
            ls.foreach(tb=>{
              val tx = Transaction.parseFrom(tb)
              this.addTransactionToCache(tx)
            })
            RepLogger.info(RepLogger.TransLifeCycle_Logger, s"systemname=${ctx.getSystemName},load trans success from db")
        }
      } catch {
        case e: Exception =>
          RepLogger.info(RepLogger.TransLifeCycle_Logger, s"systemname=${ctx.getSystemName},load trans except from db,msg=${e.getCause}")
      }
    }
  }*/
}
