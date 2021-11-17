package rep.network.cache

import java.util.concurrent.ConcurrentLinkedQueue

import rep.app.conf.SystemProfile
import rep.crypto.cert.SignTool
import rep.log.RepLogger
import rep.network.cache.ITransactionPool.CheckedTransactionResult
import rep.network.tools.transpool.TransactionPoolMgr
import rep.protos.peer.Transaction
import rep.storage.ImpDataAccess

class TransactionPoolWorker1(pools:TransactionPoolMgr, dataAccess: ImpDataAccess) extends Runnable{
  private var qs = new ConcurrentLinkedQueue[Transaction]()

  def checkTransaction(t: Transaction, dataAccess: ImpDataAccess): CheckedTransactionResult = {
    var resultMsg = ""
    var result = false

    if(SystemProfile.getHasPreloadTransOfApi){
    val sig = t.getSignature
    val tOutSig = t.clearSignature
    val cert = sig.getCertId

    try {
      val siginfo = sig.signature.toByteArray()
      val bs = tOutSig.toByteArray
      if (SignTool.verify(siginfo, bs , cert, dataAccess.getSystemName)) {
        if (pools.findTrans(t.id) || dataAccess.isExistTrans4Txid(t.id)) {
          resultMsg = s"The transaction(${t.id}) is duplicated with txid"
        } else {
          result = true
        }
      } else {
        resultMsg = s"The transaction(${t.id}) is not completed"
      }
    } catch {
      case e: RuntimeException => throw e
    }
    }else{
      result = true
    }

    CheckedTransactionResult(result, resultMsg)
  }

  private def DoWork(t:Transaction) = {
    var result = CheckedTransactionResult(false,"")
    try {
      result = checkTransaction(t, dataAccess)
      if((result.result) && (SystemProfile.getMaxCacheTransNum == 0 || pools.getTransLength() < SystemProfile.getMaxCacheTransNum) ){
        pools.putTran(t, dataAccess.getSystemName)
        RepLogger.trace(RepLogger.System_Logger,s"${dataAccess.getSystemName} trans pool recv,txid=${t.id}")
      }
    }catch {
      case e:Exception=>
        RepLogger.trace(RepLogger.System_Logger,s"${dataAccess.getSystemName} trans pool recv,txid=${t.id},msg:${e.getMessage}")
    }
  }

  def addTransaction(t:Transaction):Unit={
    this.qs.offer(t)
  }

  override def run(): Unit = {
    var count = 0
    while(true){
      try{
        val t = this.qs.poll()
        if(t != null){
          DoWork(t)
        }else{
          if(count < 1000){
            count = count + 1
          }
          Thread.sleep(count)
        }
      }catch {
        case e:Exception=>
          e.printStackTrace()
      }
    }
  }
}
