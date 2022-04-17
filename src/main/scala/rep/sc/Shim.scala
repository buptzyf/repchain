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
import rep.utils.IdTool
import rep.utils.SerializeUtils.deserialise
import rep.utils.SerializeUtils.serialise
import rep.crypto.cert.SignTool
import _root_.com.google.protobuf.ByteString
import rep.log.RepLogger
import org.slf4j.Logger
import rep.app.conf.RepChainConfig
import rep.proto.rc2.Transaction
import rep.storage.chain.KeyPrefixManager
import rep.storage.chain.preload.{BlockPreload, TransactionPreload}
import scala.collection.immutable.HashMap


/** Shim伴生对象
 *  @author c4w
 * 
 */
object Shim {
  
  type Key = String  
  type Value = Array[Byte]

  val ERR_CERT_EXIST = "证书已存在"
  val PRE_CERT_INFO = "CERT_INFO_"
  val PRE_CERT = "CERT_"
  val NOT_PERR_CERT = "非节点证书"
}

/** 为合约容器提供底层API的类
 *  @author c4w
 * @constructor 根据actor System和合约的链码id建立shim实例
 * @param system 所属的actorSystem
 * @param t 合约执行的交易
 * @param identifier 合约执行的交易
 */
class Shim(system: ActorSystem,t:Transaction,identifier:String) {

  import Shim._

  private val PRE_SPLIT = "_"
  //本chaincode的 key前缀
  private var pre_key : String = KeyPrefixManager.getWorldStateKeyPrefix(pe.getSysTag,IdTool.getCid(t.getCid),t.oid)
  //存储模块提供的system单例
  private val pe = PeerExtension(system)
  //从交易传入, 内存中的worldState快照
  //不再直接使用区块预执行对象，后面采用交易预执行对象，可以更细粒度到控制交易事务
  private var srOfTransaction : TransactionPreload = BlockPreload.getBlockPreload(identifier,pe.getSysTag).getTransactionPreload(t.id)
  private var config  = RepChainConfig.getSystemConfig(pe.getSysTag)

  //记录状态修改日志
  private var stateGet : HashMap[String,ByteString] = new HashMap[String,ByteString]()
  private var stateSet : HashMap[String,ByteString] = new HashMap[String,ByteString]()

  def getStateGet:HashMap[String,ByteString]={
    this.stateGet
  }

  def getStateSet:HashMap[String,ByteString]={
    this.stateSet
  }

  def setVal(key: Key, value: Any):Unit ={
    setState(key, serialise(value))
  }
   def getVal(key: Key):Any ={
     val v = getState(key)
     if(v == null)
       null
     else
      deserialise(v)
  }
 
  private def setState(key: Key, value: Array[Byte]): Unit = {
    val pkey = pre_key + PRE_SPLIT + key
    if(!this.stateGet.contains(pkey)){
      //如果该键从来没有read，从DB获取read，并写入到read日志
      val oldValue = get(pkey)
      if(oldValue == null){
        //如果没有读到，写入None的字节数组
        this.stateGet += pkey->ByteString.EMPTY
      }else{
        this.stateGet += pkey->ByteString.copyFrom(oldValue)
      }
    }
    if(value != null){
      this.srOfTransaction.put(pkey,value)
      this.stateSet += pkey -> ByteString.copyFrom(value)
    }else{
      //如果待写入的值是null，采用None替代，并转字节数组
      val nv = ByteString.EMPTY
      this.srOfTransaction.put(pkey,nv.toByteArray)
      this.stateSet += pkey -> nv
    }
  }

  private def get(key: Key): Array[Byte] = {
    val v = this.srOfTransaction.get(key)
    if(v == None){
      this.stateGet += key -> ByteString.EMPTY
      null
    }else{
      val bv = v.get.asInstanceOf[Array[Byte]]
      if(bv.length == 0){
        this.stateGet += key -> ByteString.EMPTY
        null
      }else{
        this.stateGet += key -> ByteString.copyFrom(bv)
        bv
      }
    }
  }

  private def getState(key: Key): Array[Byte] = {
    get(pre_key + PRE_SPLIT + key)
  }

  def getStateEx(chainId:String,contractId:String,contractInstanceId:String, key: Key): Array[Byte] = {
    get(chainId + PRE_SPLIT + contractId + PRE_SPLIT + contractInstanceId + PRE_SPLIT + key)
  }

  def getStateEx(chainId:String,contractId:String, key: Key): Array[Byte] = {
    get(chainId + PRE_SPLIT + contractId + PRE_SPLIT + PRE_SPLIT + PRE_SPLIT + key)
  }
  
  //判断账号是否节点账号 TODO
  def bNodeCreditCode(credit_code: String) : Boolean ={
    SignTool.isNode4Credit(credit_code)
  }

  def getCurrentContractDeployer:String={
    val key_coder = KeyPrefixManager.getWorldStateKey(pe.getSysTag,t.getCid.chaincodeName,IdTool.getCid(t.getCid),t.oid)
    val coder = this.srOfTransaction.get(key_coder)
    if(coder == None){
      ""
    }else{
      coder.get.asInstanceOf[String]
    }
  }

  def isDidContract:Boolean = {
    IdTool.isDidContract(pe.getSysTag)
  }

  /**
    * 判断是否为超级管理员
    *
    * @param credit_code
    * @return
    */
  def isAdminCert(credit_code: String): Boolean = {
    var r = true
    val certId = IdTool.getCertIdFromName(config.getChainCertName)
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
