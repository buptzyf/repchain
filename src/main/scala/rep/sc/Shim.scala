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


import akka.actor.ActorSystem
import rep.network.tools.PeerExtension
import rep.protos.peer.OperLog
import rep.storage.TransactionOfDataPreload
import rep.utils.IdTool
import rep.utils.SerializeUtils.deserialise
import rep.utils.SerializeUtils.serialise
import rep.crypto.cert.SignTool
import _root_.com.google.protobuf.ByteString
import rep.log.RepLogger
import org.slf4j.Logger
import rep.app.conf.SystemProfile
import rep.proto.rc2.TransactionResult

/** Shim伴生对象
 *  @author c4w
 * 
 */
object Shim {
  
  type Key = String  
  type Value = Array[Byte]


  import rep.storage.IdxPrefix._
  val ERR_CERT_EXIST = "证书已存在"
  val PRE_CERT_INFO = "CERT_INFO_"
  val PRE_CERT = "CERT_"
  val NOT_PERR_CERT = "非节点证书"
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

  //记录状态修改日志
  //var ol = scala.collection.mutable.ListBuffer.empty[OperLog]

  //记录读写集合
  var tr = TransactionResult.defaultInstance

  def setVal(key: Key, value: Any):Unit ={
    setState(key, serialise(value))
  }
   def getVal(key: Key):Any ={
    deserialise(getState(key))
  }
 
  def setState(key: Key, value: Array[Byte]): Unit = {
    val pKey = pre_key + key
    val oldValue = get(pKey)
    //sr.Put(pkey, value)
    this.srOfTransaction.Put(pKey,value)
    //val ov = if(oldValue == null) ByteString.EMPTY else ByteString.copyFrom(oldValue)
    val nv = if(value == null) ByteString.EMPTY else ByteString.copyFrom(value)
    //记录操作日志
    //getLogger.trace(s"nodename=${sr.getSystemName},dbname=${sr.getInstanceName},txid=${txid},key=${key},old=${deserialise(oldValue)},new=${deserialise(value)}")
    //最后写入，加到写入集合
    tr.addStatesSet((pKey,nv))
  }

  private def get(key: Key): Array[Byte] = {
    //sr.Get(key)
    this.srOfTransaction.Get(key)
  }

  def getState(key: Key): Array[Byte] = {
    val pKey = pre_key + key
    val pVal = get(pKey)
    //第一次读取,加到读取集合
    if(!tr.statesGet.contains(pKey)) {
      if(pVal ==null)
        tr.addStatesGet((pKey,null))
      else
        tr.addStatesGet((pKey, ByteString.copyFrom(pVal)))
    }
    pVal
  }

  def getStateEx(cName:String, key: Key): Array[Byte] = {
    get(WorldStateKeyPreFix + cName + PRE_SPLIT + key)
  }
  
  //判断账号是否节点账号 TODO
  def bNodeCreditCode(credit_code: String) : Boolean ={
    SignTool.isNode4Credit(credit_code)
  }

  /**
    * 判断是否为超级管理员
    *
    * @param credit_code
    * @return
    */
  def isAdminCert(credit_code: String): Boolean = {
    var r = true
    val certId = IdTool.getCertIdFromName(SystemProfile.getChainCertName)
    if (!certId.creditCode.equals(credit_code)) {
      r = false
    }
    r
  }
  
  //通过该接口获取日志器，合约使用此日志器输出业务日志。
  def getLogger:Logger={
    RepLogger.Business_Logger
  }
}
