package rep.network.tools.transpool

import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue, ConcurrentSkipListMap}
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.JavaConverters._
import rep.app.conf.TimePolicy
import rep.log.RepLogger
import rep.protos.peer.Transaction
import rep.storage.ImpDataAccess

import scala.util.control.Breaks.{break, breakable}

class TransactionPoolOfQueueMgr {

  private implicit var transQueue = new ConcurrentLinkedQueue[Transaction]()
  private implicit var transKeys = new ConcurrentHashMap[String,(Long,Transaction)]() asScala
  private implicit var transNumber = new AtomicInteger(0)

  def getTransListClone(number: Int,sysName:String): Seq[Transaction] = {
    var transList = scala.collection.mutable.ArrayBuffer[Transaction]()
    val currentTime = System.currentTimeMillis()
    val len = number - 1

    breakable(
      for(i<-0 to len){
        val t = this.transQueue.poll()
        if(t == null){
          break
        }else{
          var tk = this.transKeys.getOrElse(t.id,null)
          var time = 0l
          if(tk != null){
            time = tk._1
          }
          if((time - currentTime > TimePolicy.getTranscationWaiting)){//|| sr.isExistTrans4Txid(txid) ){
            this.transKeys.remove(t.id)
          }else{
            transList += t
            this.transKeys.remove(t.id)
            this.transNumber.decrementAndGet()
          }
        }
      }
    )
    val end = System.currentTimeMillis()
    RepLogger.trace(RepLogger.OutputTime_Logger, s"systemname=${sysName},transNumber=${transList.length},getTransListClone spent time=${end-currentTime}")

    transList.toSeq
  }


  def putTran(tran: Transaction,sysName:String): Unit = {
    val start = System.currentTimeMillis()

    if(this.transKeys.contains(tran.id)){
      RepLogger.info(RepLogger.TransLifeCycle_Logger,  s"systemname=${sysName},trans entry pool,${tran.id} exists in cache")
    }else{
      this.transQueue.add(tran)
      this.transKeys.put(tran.id,(System.currentTimeMillis(),tran))
      this.transNumber.incrementAndGet()
    }
    val end = System.currentTimeMillis()
    RepLogger.trace(RepLogger.OutputTime_Logger, s"systemname=${sysName},putTran spent time=${end-start}")
  }

  def putTrans(trans:Seq[Transaction],sysName:String): Unit ={
    val start = System.currentTimeMillis()
    trans.foreach(t=>{
      this.putTran(t,sysName)
    })
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
    val t = this.transKeys.getOrElse(txid,null)
    if(t != null){
      t._2
    }else null
  }

  def removeTrans(trans: Seq[ Transaction ],sysName:String): Unit = {
    trans.foreach(f=>{
      this.transKeys.remove(f.id)
    })
  }

  def getTransLength() : Int = {
    this.transNumber.get
  }

  def isEmpty:Boolean={
    this.transQueue.isEmpty
  }
}