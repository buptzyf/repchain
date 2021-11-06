package rep.network.tools.transpool

import java.util.Collections
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue, ConcurrentSkipListMap, Executors, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, LongAdder}

import scala.collection.JavaConverters._
import rep.app.conf.{SystemProfile, TimePolicy}
import rep.log.RepLogger
import rep.network.cache.ITransactionPool
import rep.protos.peer.Transaction
import rep.storage.ImpDataAccess
import rep.utils.SerializeUtils

import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks.{break, breakable}

class TransactionPoolMgr extends ITransctionPoolMgr {
  case class TransactionInfo(transaction: Transaction,entryTime:Long)

  private implicit var transQueueOfTx = new ConcurrentLinkedQueue[Transaction]()
  private implicit var transKeys = new ConcurrentHashMap[String,TransactionInfo]() asScala
  //private implicit var transNumber = new AtomicInteger(0)
  private implicit var transNumber = new LongAdder()
  transNumber.reset()
  private implicit var preloadBlocks = new ConcurrentHashMap[String,Seq[Transaction]]() asScala

  private var scheduledExecutorService = Executors.newSingleThreadScheduledExecutor
  private var isStarup = new AtomicBoolean(false)

  def startupSchedule(sysName:String)={
    if(this.isStarup.get() == false){
      this.isStarup.set((true))
      this.scheduledExecutorService.scheduleWithFixedDelay(
        new cleanCache(sysName),100,300, TimeUnit.SECONDS
      )
    }
  }

  class cleanCache(sysName:String) extends Runnable{
    override def run(){
      var translist = scala.collection.mutable.ArrayBuffer[String]()
      val currenttime = System.currentTimeMillis()
      val sr: ImpDataAccess = ImpDataAccess.GetDataAccess(sysName)
      try{
        System.err.println(s"entry Clean Cache,system=${sysName}")
        transKeys.values.foreach(ti=>{
          if((currenttime - ti.entryTime)/1000 > TimePolicy.getTranscationWaiting || sr.isExistTrans4Txid(ti.transaction.id) ){
            translist += ti.transaction.id
          }
        })

        System.err.println(s"waiting delete trans,,system=${sysName},list:"+translist.mkString(","))
        translist.foreach(txid=>{
          transKeys.remove(txid)
        })
        System.err.println(s"entry Clean Cache finish,system=${sysName}")
      }catch{
        case e:Exception=>e.printStackTrace()
      }
    }
  }

  def packageTransaction(blockIdentifier:String,num: Int,sysName:String):Seq[Transaction]={
    val transList = getTransListClone(num,sysName)
    if(transList.length > 0){
      this.preloadBlocks.put(blockIdentifier,transList)
    }
    transList
  }

  def rollbackTransaction(blockIdentifier:String)={
    if(this.preloadBlocks.contains(blockIdentifier)){
      val txIdList = this.preloadBlocks.getOrElse(blockIdentifier,null)
      if(txIdList != null){
        addTxIdToQueue(txIdList)
      }
    }
  }

  private def addTxIdToQueue(txIdList:Seq[Transaction])={
    txIdList.foreach(txId=>{
        this.transQueueOfTx.add(txId)
        this.transNumber.increment()
    })
  }

  def cleanPreloadCache(blockIdentifier:String)={
    this.preloadBlocks.remove(blockIdentifier)
  }

  def getTransListClone(num: Int,sysName:String): Seq[Transaction] = {
    var translist = scala.collection.mutable.ArrayBuffer[Transaction]()
    val currenttime = System.currentTimeMillis()
    try{
      val sr: ImpDataAccess = ImpDataAccess.GetDataAccess(sysName)

      breakable(
        for(i<-0 to num-1){
          val tx = this.transQueueOfTx.poll()
          if(tx != null){
            this.transNumber.decrement()
            val l = this.transKeys.get(tx.id)
            if(l != None){
              if((currenttime - l.get.entryTime)/1000 > TimePolicy.getTranscationWaiting){// || sr.isExistTrans4Txid(txid) ){
                //超时或者重复 删除
                this.transKeys.remove(tx.id)
              }else{
                translist += l.get.transaction
              }
            }
          }else{
            //队列为空，打包结束
            break
          }
        })
    }catch{
      case e:Exception =>
        RepLogger.error(RepLogger.OutputTime_Logger, s"systemname=${sysName},transNumber=${transNumber},getTransListClone error, info=${e.getMessage}")
    }
    val end = System.currentTimeMillis()
    RepLogger.trace(RepLogger.OutputTime_Logger, s"systemname=${sysName},transNumber=${transNumber},getTransListClone spent time=${end-currenttime}")

    translist.toSeq
  }

  def putTran(tran: Transaction,sysName:String): Unit = {

    val start = System.currentTimeMillis()
    try{
      val time = System.currentTimeMillis()
      val txid = tran.id
      if(transKeys.contains(txid)){
        RepLogger.info(RepLogger.TransLifeCycle_Logger,  s"systemname=${sysName},trans entry pool,${tran.id} exists in cache")
      }else{
        transKeys.put(txid, TransactionInfo(tran,time))
        this.transQueueOfTx.add(tran)
        transNumber.increment()
        RepLogger.info(RepLogger.TransLifeCycle_Logger,  s"systemname=${sysName},transNumber=${transNumber},trans entry pool,${tran.id},entry time = ${time}")
      }
    }finally {
    }
    val end = System.currentTimeMillis()
    RepLogger.trace(RepLogger.OutputTime_Logger, s"systemname=${sysName},putTran spent time=${end-start}")
  }

  def findTrans(txid:String):Boolean = {
    var b :Boolean = false
    val start = System.currentTimeMillis()
    if(transKeys.contains(txid)){
      b = true
    }
    val end = System.currentTimeMillis()
    RepLogger.trace(RepLogger.OutputTime_Logger, s"findTrans spent time=${end-start}")
    b
  }

  def getTransaction(txid:String):Transaction={
    var t : Transaction = null
    val d = this.transKeys.getOrElse(txid,null)
    if(d != None){
      t = d.transaction
    }
    t
  }

  def removeTrans(trans: Seq[ Transaction ],sysName:String): Unit = {
    try{
      trans.foreach(f=>{
        removeTranscation(f,sysName)
      })
    }finally{
    }
  }

  def removeTranscation(tran:Transaction,sysName:String):Unit={
    try{
      RepLogger.info(RepLogger.TransLifeCycle_Logger,  s"systemname=${sysName},remove trans from pool,trans entry block,${tran.id}")
      this.transKeys.remove(tran.id)
    }finally{

    }
  }

  def getTransLength() : Int = {
    this.transNumber.intValue()
  }

  def isEmpty:Boolean={
    this.transQueueOfTx.isEmpty
  }

  override def getTransListClone(start: Int, num: Int, sysName: String): Seq[Transaction] = {Seq.empty}

  override def saveTransaction(sysName: String): Unit = {
    if (SystemProfile.getIsPersistenceTxToDB == 1) {
      var r = new ArrayBuffer[Array[Byte]]()
      this.transQueueOfTx.forEach(t=>{
        r += t.toByteArray
    })
      SerializeUtils.serialise(r)
      var da = ImpDataAccess.GetDataAccess(sysName)
      da.Put(sysName + "-" + txPrefix, SerializeUtils.serialise(r))
    }
  }



}