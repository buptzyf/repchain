/*
 * Copyright  2019 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BA SIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package rep.network.tools

import scala.collection.mutable.{ArrayBuffer,LinkedHashMap}
import rep.protos.peer.Transaction
import java.util.concurrent.locks._
import rep.utils.GlobalUtils.{TranscationPoolPackage}
import rep.log.RepLogger

class TransactionPoolMgr {
  private val  transLock : Lock = new ReentrantLock();
  private val transactions = LinkedHashMap.empty[ String, TranscationPoolPackage ]
  
  def getTransListClone(num: Int,start:Int=0): Seq[ TranscationPoolPackage ] = {
    val result = ArrayBuffer.empty[ TranscationPoolPackage ]
    transLock.lock()
    try{
      val data = transactions.slice(start, start+num)
      data.foreach(pair => pair._2 +=: result)
      //transactions.take(len).foreach(pair => pair._2 +=: result)
    }finally{
      transLock.unlock()
    }
    result.reverse
  }

  def putTran(tran: Transaction): Unit = {
    transLock.lock()
    try{
      if (transactions.contains(tran.id)) {
        RepLogger.trace(RepLogger.System_Logger, s"${tran.id} exists in cache")
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