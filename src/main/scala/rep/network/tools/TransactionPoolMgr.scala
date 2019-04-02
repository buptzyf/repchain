package rep.network.tools

import scala.collection.mutable.{ArrayBuffer,LinkedHashMap}
import rep.protos.peer.Transaction
import java.util.concurrent.locks._
import rep.utils.GlobalUtils.{TranscationPoolPackage}

class TransactionPoolMgr {
  private val  transLock : Lock = new ReentrantLock();
  private val transactions = LinkedHashMap.empty[ String, TranscationPoolPackage ]
  
  def getTransListClone(num: Int): Seq[ TranscationPoolPackage ] = {
    val result = ArrayBuffer.empty[ TranscationPoolPackage ]
    transLock.lock()
    try{
      val len = if (transactions.size < num) transactions.size else num
      transactions.take(len).foreach(pair => pair._2 +=: result)
    }finally{
      transLock.unlock()
    }
    result.reverse
  }

  def putTran(tran: Transaction): Unit = {
    transLock.lock()
    try{
      if (transactions.contains(tran.id)) {
        println(s"${tran.id} exists in cache")
      }
      else transactions.put(tran.id, new TranscationPoolPackage(tran,System.currentTimeMillis()/1000))
    }finally {
      transLock.unlock()
    }
  }
  
  def findTrans(txid:String):Boolean = {
    var b :Boolean = false
    if(transactions.contains(txid)){
        b = true
    }
    b
  }

  def removeTrans(trans: Seq[ Transaction ]): Unit = {
    transLock.lock()
    try{
      for (curT <- trans) {
          if (transactions.contains(curT.id)) transactions.remove(curT.id)
      }
    }finally{
        transLock.unlock()
    }
  }
  
  def removeTranscation(tran:Transaction):Unit={
    transLock.lock()
    try{
       if (transactions.contains(tran.id)) transactions.remove(tran.id)
    }finally{
        transLock.unlock()
    }
  }

  def getTransLength() : Int = {
    var len = 0
    transLock.lock()
    try{
      len = transactions.size
    }finally{
      transLock.unlock()
    }
    len
  }
}