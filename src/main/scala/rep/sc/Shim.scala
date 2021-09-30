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

package rep.sc

import com.fasterxml.jackson.core.Base64Variants
import akka.actor.ActorSystem
import rep.network.tools.PeerExtension
import rep.protos.peer.{OperLog, Transaction}
import rep.storage.{ImpDataAccess, ImpDataPreload, TransactionOfDataPreload}
import rep.utils.SerializeUtils
import rep.utils.SerializeUtils.deserialise
import rep.utils.SerializeUtils.serialise
import java.security.cert.CertificateFactory
import java.io.FileInputStream
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.security.cert.X509Certificate

import rep.crypto.cert.SignTool
import _root_.com.google.protobuf.ByteString
import rep.log.RepLogger
import org.slf4j.Logger
import scala.collection.mutable;

/** Shim伴生对象
 *  @author c4w
 * 
 */
object Shim {
  
  type Key = String  
  type Value = Array[Byte]
  var kmap_read = mutable.Map.empty[Key,mutable.Set[Transaction]]
  var kmap_write = mutable.Map.empty[Key,mutable.Set[Transaction]]
  var tmap_read = mutable.Map.empty[Transaction,mutable.Set[Key]]
  var tmap_write = mutable.Map.empty[Transaction, mutable.Set[Key]]


  import rep.storage.IdxPrefix._
  val ERR_CERT_EXIST = "证书已存在"
  val PRE_CERT_INFO = "CERT_INFO_"
  val PRE_CERT = "CERT_"
  val NOT_PERR_CERT = "非节点证书"

  def loadTx(s: Shim, tid:String):(Option[Transaction], Long) ={
    val db = ImpDataAccess.GetDataAccess(s.pe.getSysTag)
    (db.getTransDataByTxId(tid),db.getBlock4ObjectByTxId(tid).height)
  }

  //获得接收链上的出块签名交易及交易出块证明
  def loadRemoteTx(s: Shim, targetChain:String, tid:String):Option[Transaction] ={
    val db = ImpDataAccess.GetDataAccess(targetChain)
    db.getTransDataByTxId(tid)
  }

  // 获得交易读取和写入的相关键值集合
  def getKeySet(t:Transaction):(Set[Key],Set[Key]) ={
    (tmap_read.apply(t).toSet,tmap_write.apply(t).toSet)
  }

  // 获得当前区块高度
  def getBlockHeight(s:Shim):Long ={
    val db = ImpDataAccess.GetDataAccess(s.pe.getSysTag)
    db.getBlock4ObjectByTxId(s.tx.t.id).height
  }

  // 获得读取指定键值的相关条件执行签名交易集合
  def getLockedTxRead(key:Key):Option[mutable.Set[Transaction]] ={
    kmap_read.get(key)
  }

  // 获得写入指定键值的相关条件执行签名交易集合
  def getLockedTxWrite(key:Key):Option[mutable.Set[Transaction]] ={
    kmap_write.get(key)
  }

  // 获得相关条件执行交易集合
  def getRelatedTx(t:Transaction):Set[Transaction] ={
    val (ks_read, ks_write) = getKeySet(t)
    var stx = mutable.Set[Transaction]()
    if(t.commit!=null) {
      val ki1 = ks_write.intersect(kmap_read.keySet)
      ki1.foreach(x=>stx.++(getLockedTxRead(x)))
      val ki2 = ks_read.intersect(kmap_write.keySet)
      ki2.foreach(x=>stx.++(getLockedTxWrite(x)))
    } else {
      val ki = ks_write.intersect(kmap_read.keySet)
      ki.foreach(x=>stx.++(getLockedTxRead(x)))
    }
    stx.toSet
  }

  // 锁定条件交易
  def lockTx(s:Shim, tid:String) ={
    val t = loadTx(s, tid)._1.getOrElse(Transaction())
    val (ks_read,ks_write) = getKeySet(t)
    ks_read.foreach{ x =>
      val ts = getLockedTxRead(x).getOrElse(mutable.Set[Transaction]())
      ts.add(t)
    }
    ks_write.foreach{ x =>
      val ts = getLockedTxWrite(x).getOrElse(mutable.Set[Transaction]())
      ts.add(t)
    }
  }

  // 解锁条件交易
  def unlockTx(s:Shim, tid:String) = {
    val t = loadTx(s, tid)._1.getOrElse(Transaction())
    val (ks_read,ks_write) = getKeySet(t)
    val ki_read = ks_read.intersect(kmap_read.keySet)
    ki_read.foreach{ x =>
      val tr = getLockedTxRead(x)
      if(!tr.isEmpty) tr.get.remove(t)
    }
    val ki_write = ks_write.intersect(kmap_write.keySet)
    ki_write.foreach{ x =>
      val tw = getLockedTxRead(x)
      if(!tw.isEmpty) tw.get.remove(t)
    }
  }
}

/** 为合约容器提供底层API的类
 *  @author c4w
 * @constructor 根据actor System和合约的链码id建立shim实例
 * @param system 所属的actorSystem
 * @param cName 合约的链码id
 */
class Shim(system: ActorSystem, cName: String) {

  import Shim._
  import rep.storage.IdxPrefix._

  val PRE_SPLIT = "_"
  //本chaincode的 key前缀
  val pre_key = WorldStateKeyPreFix + cName + PRE_SPLIT
  //存储模块提供的system单例
  val pe = PeerExtension(system)
  //从交易传入, 内存中的worldState快照
  //不再直接使用区块预执行对象，后面采用交易预执行对象，可以更细粒度到控制交易事务
  //var sr:ImpDataPreload = null
  var srOfTransaction : TransactionOfDataPreload = null
  //交易
  var tx:SandboxDispatcher.DoTransactionOfSandbox = null
  //记录状态修改日志
  var ol = scala.collection.mutable.ListBuffer.empty[OperLog]

  //记录读集
  var kset_read = mutable.Set.empty[Key]
  //记录写集
  var kset_write = mutable.Set.empty[Key]
    
  def setVal(key: Key, value: Any):Unit ={
    kset_write += key
    Shim.tmap_write += (tx.t -> kset_write)
    setState(key, serialise(value))
  }
  def getVal(key: Key):Any ={
    kset_read += key
    Shim.tmap_read += (tx.t -> kset_read)
    deserialise(getState(key))
  }
 
  def setState(key: Key, value: Array[Byte]): Unit = {
    val pkey = pre_key + key
    val oldValue = get(pkey)
    //sr.Put(pkey, value)
    this.srOfTransaction.Put(pkey,value)
    val ov = if(oldValue == null) ByteString.EMPTY else ByteString.copyFrom(oldValue)
    val nv = if(value == null) ByteString.EMPTY else ByteString.copyFrom(value)
    //记录操作日志
    //getLogger.trace(s"nodename=${sr.getSystemName},dbname=${sr.getInstanceName},txid=${txid},key=${key},old=${deserialise(oldValue)},new=${deserialise(value)}")
    ol += new OperLog(key,ov, nv)
  }

  private def get(key: Key): Array[Byte] = {
    //sr.Get(key)
    this.srOfTransaction.Get(key)
  }

  def getState(key: Key): Array[Byte] = {
    get(pre_key + key)
  }

  def getStateEx(cName:String, key: Key): Array[Byte] = {
    get(WorldStateKeyPreFix + cName + PRE_SPLIT + key)
  }
  
  //判断账号是否节点账号 TODO
  def bNodeCreditCode(credit_code: String) : Boolean ={
    SignTool.isNode4Credit(credit_code)
  }
  
  //通过该接口获取日志器，合约使用此日志器输出业务日志。
  def getLogger:Logger={
    RepLogger.Business_Logger
  }



}