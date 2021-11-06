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

package rep.network.tools.transpool

import java.util.concurrent.ConcurrentLinkedQueue

import rep.protos.peer.Transaction
import java.util.concurrent.locks._

import rep.app.conf.SystemProfile
import rep.log.RepLogger
import rep.utils.SerializeUtils

import scala.collection.mutable.ArrayBuffer
//import scala.jdk.CollectionConverters._
import scala.collection.JavaConverters._
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import rep.app.conf.{  TimePolicy }
import rep.storage.ImpDataAccess
import scala.util.control.Breaks._

class TransactionPoolMgrForCFRD extends ITransctionPoolMgr {
  private val  transLock : Lock = new ReentrantLock();
  
  private implicit var transactions = new ConcurrentSkipListMap[Long,Transaction]() asScala
  private implicit var transQueue = new ConcurrentLinkedQueue[Transaction]()
  private implicit var transKeys = new ConcurrentHashMap[String,Long]() asScala
  private implicit var transNumber = new AtomicInteger(0)

   def getTransListClone(start:Int,num: Int,sysName:String): Seq[Transaction] = {
    var translist = scala.collection.mutable.ArrayBuffer[Transaction]()
    transLock.lock()
    val starttime = System.currentTimeMillis()
    try{
        var deltrans4id = scala.collection.mutable.ArrayBuffer[String]()
        val sr: ImpDataAccess = ImpDataAccess.GetDataAccess(sysName)
        val currenttime = System.nanoTime() / 1000000000
        var count = 0
        var pos = 0
        breakable(
        transactions.foreach(f=>{
          if(count <= num){
            val txid = f._2.id
            if ((currenttime - f._1/1000000000) > TimePolicy.getTranscationWaiting || sr.isExistTrans4Txid(txid) ){
              deltrans4id += txid
            }else{
              if(pos < start){
                pos += 1
              }else{
                translist += f._2
                count += 1
              }
            }
          }else{
            break
          }
        })
        )
       
        if(deltrans4id.length > 0){
          deltrans4id.foreach(f=>{
            RepLogger.info(RepLogger.TransLifeCycle_Logger,  s"systemname=${sysName},remove trans from pool,trans timeout or exist in block,${f}")
            removeTranscation4Txid(f,sysName)
          })
        }
        deltrans4id.clear()
    }finally{
      transLock.unlock()
    }
    val end = System.currentTimeMillis()
    RepLogger.trace(RepLogger.OutputTime_Logger, s"systemname=${sysName},transNumber=${transNumber},getTransListClone spent time=${end-starttime}")
    
    translist.toSeq
  }


  def putTran(tran: Transaction,sysName:String): Unit = {
    transLock.lock()
    val start = System.currentTimeMillis()
    try{
      val time = System.nanoTime()
      val txid = tran.id
      if(transKeys.contains(txid)){
        RepLogger.info(RepLogger.TransLifeCycle_Logger,  s"systemname=${sysName},trans entry pool,${tran.id} exists in cache")
      }else{
        transactions.put(time, tran)
        transKeys.put(txid, time)
        transNumber.incrementAndGet()
        RepLogger.info(RepLogger.TransLifeCycle_Logger,  s"systemname=${sysName},transNumber=${transNumber},trans entry pool,${tran.id},entry time = ${time}")
      }
    }finally {
      transLock.unlock()
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
    transactions.getOrElse(txid,null)
  }

  def removeTrans(trans: Seq[ Transaction ],sysName:String): Unit = {
    transLock.lock()
    try{
      trans.foreach(f=>{
        removeTranscation(f,sysName)
      })
    }finally{
      transLock.unlock()
    }
  }

  def removeTranscation(tran:Transaction,sysName:String):Unit={
    transLock.lock()
    try{
      RepLogger.info(RepLogger.TransLifeCycle_Logger,  s"systemname=${sysName},remove trans from pool,trans entry block,${tran.id}")
      removeTranscation4Txid(tran.id,sysName)
    }finally{
      transLock.unlock()
    }
  }

  def removeTranscation4Txid(txid:String,sysName:String):Unit={
    transLock.lock()
    val start = System.currentTimeMillis()
    try{
      if(transKeys.contains(txid)){
        transactions.remove(transKeys(txid))
        transKeys.remove(txid)
        transNumber.decrementAndGet()
      }
      RepLogger.info(RepLogger.TransLifeCycle_Logger,  s"systemname=${sysName},remove trans from pool,${txid}")
    }finally{
      transLock.unlock()
    }
    val end = System.currentTimeMillis()
    RepLogger.trace(RepLogger.OutputTime_Logger, s"systemname=${sysName},removeTranscation4Txid spent time=${end-start}")
  }

  def getTransLength() : Int = {
    this.transNumber.get
  }

  def isEmpty:Boolean={
    transactions.isEmpty
  }

  override def packageTransaction(blockIdentifier: String, num: Int, sysName: String): Seq[Transaction] = {Seq.empty}

  override def rollbackTransaction(blockIdentifier: String): Unit = {}

  override def cleanPreloadCache(blockIdentifier: String): Unit = {}

  override def getTransListClone(num: Int, sysName: String): Seq[Transaction] = {Seq.empty}

  override def startupSchedule(sysName: String): Unit = {}

  override def saveTransaction(sysName: String): Unit = {
    if (SystemProfile.getIsPersistenceTxToDB == 1) {
      var r = new ArrayBuffer[Array[Byte]]()
      this.transactions.values.foreach(t => {
        r += t.toByteArray
      })
      SerializeUtils.serialise(r)
      var da = ImpDataAccess.GetDataAccess(sysName)
      da.Put(sysName + "-" + txPrefix, SerializeUtils.serialise(r))
    }
  }

}