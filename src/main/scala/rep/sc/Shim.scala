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


import akka.actor.{ActorContext, ActorRef, ActorSystem}
import rep.network.tools.PeerExtension
import rep.utils.IdTool
import rep.utils.SerializeUtils.deserialise
import rep.utils.SerializeUtils.serialise
import _root_.com.google.protobuf.ByteString
import akka.pattern.AskTimeoutException
import akka.util.Timeout
import org.json4s.native.JsonParser
import org.json4s.{DefaultFormats, jackson}
import rep.log.RepLogger
import org.slf4j.Logger
import rep.api.rest.ResultCode
import rep.app.conf.RepChainConfig
import rep.app.system.RepChainSystemContext
import rep.crypto.Sha256
import rep.network.autotransaction.Topic
import rep.network.module.ModuleActorType
import rep.proto.rc2.{ActionResult, Block, CertId, Certificate, ChaincodeId, ChaincodeInput, Event, Signer, Transaction, TransactionResult}
import rep.sc.SandboxDispatcher.{DoTransaction, DoTransactionOfCrossContract}
import rep.sc.scalax.ContractException
import rep.sc.tpl.did.DidTplPrefix.{certPrefix, signerPrefix}
import rep.sc.tpl.did.operation.SignerOperation.{signerNotExists, toJsonErrMsg}
import rep.storage.chain.KeyPrefixManager
import rep.storage.chain.preload.TransactionPreload
import rep.utils.GlobalUtils.EventType
import org.json4s.native.Serialization.write
import scalapb.json4s.JsonFormat

import scala.collection.immutable.HashMap
import scala.concurrent.{Await, TimeoutException}


/** Shim伴生对象
 *
 * @author c4w
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
 *
 * @author c4w
 */
class Shim {

  import Shim._

  private implicit val serialization = jackson.Serialization
  private implicit val formats = DefaultFormats

  private var context:ActorContext = null
  private var system: ActorSystem = null
  private var t: Transaction = null
  private var identifier: String = null
  private var ctx: RepChainSystemContext = null
  private var mediator:ActorRef = null

  private val PRE_SPLIT = "_"
  //本chaincode的 key前缀
  private var pre_key: String = ""
  //从交易传入, 内存中的worldState快照
  //不再直接使用区块预执行对象，后面采用交易预执行对象，可以更细粒度到控制交易事务
  private var srOfTransaction: TransactionPreload = null
  private var config: RepChainConfig = null
  private var msg : String = ""

  //记录状态修改日志
  private var stateGet: HashMap[String, ByteString] = new HashMap[String, ByteString]()
  private var stateSet: HashMap[String, ByteString] = new HashMap[String, ByteString]()
  private var stateDel: HashMap[String, ByteString] = new HashMap[String, ByteString]()

  /**
   * @constructor 根据actor System和合约的链码id建立shim实例
   * @param system     所属的actorSystem
   * @param t          合约执行的交易
   * @param identifier 合约执行的交易
   */
  def this(context:ActorContext,mediator:ActorRef, t: Transaction, identifier: String) {
    this()
    this.context = context
    this.mediator = mediator
    this.system = context.system
    this.t = t
    this.identifier = identifier
    this.ctx = PeerExtension(system).getRepChainContext
    this.pre_key = KeyPrefixManager.getWorldStateKeyPrefix(ctx.getConfig, t.getCid.chaincodeName, t.oid)
    this.srOfTransaction = ctx.getBlockPreload(identifier).getTransactionPreload(t.id)
    this.config = ctx.getConfig
    this.msg = ""
  }

  def getMessage:String={
    this.msg
  }

  def setMessage(m: String): Unit = {
    this.msg = m
  }

  def getStateGet: HashMap[String, ByteString] = {
    this.stateGet
  }

  def getStateSet: HashMap[String, ByteString] = {
    this.stateSet
  }

  def getStateDel: HashMap[String, ByteString] = {
    this.stateDel
  }

  private def checkKeyName(key: Key): Unit = {
    //删除worldstate的key字符串中包含"_"字符的限制，也就是key可以使用任意字符串
    /*if (key.indexOf("_") >= 0) {
      if (!key.equalsIgnoreCase(IdTool.getCid(t.getCid))
        && !key.equalsIgnoreCase(IdTool.getCid(t.getCid) + SandboxDispatcher.PRE_STATE)
        && key.lastIndexOf("super_admin") < 0) {
        println("key: " + key + ", " + SandboxDispatcher.ERR_WORLDSTATE_CANNOT_CONTAIN_UNDERSCORES)
        throw new Exception(SandboxDispatcher.ERR_WORLDSTATE_CANNOT_CONTAIN_UNDERSCORES)
      }
    }*/
  }

  def setVal(key: Key, value: Any): Unit = {
    checkKeyName(key)
    setState(key, serialise(value))
  }

  def delVal(key: Key): Unit = {
    checkKeyName(key)
    delState(key)
  }

  def getVal(key: Key): Any = {
    checkKeyName(key)
    val v = getState(key)
    if (v == null)
      null
    else
      deserialise(v)
  }

  def getValForBytes(key: Key): Array[Byte] = {
    checkKeyName(key)
    getState(key)
  }

  private def delState(key: Key): Unit = {
    val pkey = pre_key + PRE_SPLIT + key
    var bs = ByteString.EMPTY
    if (this.stateSet.contains(pkey)) {
      //该状态字被删除之前已经发生更新并且是最后的更新，需要删除这个更新
      this.stateSet -= pkey
    } else {
      checkStateGet(pkey)
    }
    bs = this.stateGet(pkey)
    this.stateDel += pkey -> bs
    //执行删除操作
    this.srOfTransaction.del(pkey, bs.toByteArray)
  }

  private def checkStateGet(pkey: Key): Unit = {
    if (!this.stateGet.contains(pkey)) {
      //如果该键从来没有read，从DB获取read，并写入到read日志
      val oldValue = get(pkey)
      if (oldValue == null) {
        //如果没有读到，写入None的字节数组
        this.stateGet += pkey -> ByteString.EMPTY
      } else {
        this.stateGet += pkey -> ByteString.copyFrom(oldValue)
      }
    }
  }

  private def setState(key: Key, value: Array[Byte]): Unit = {
    val pkey = pre_key + PRE_SPLIT + key
    checkStateGet(pkey)
    if (value != null) {
      this.srOfTransaction.put(pkey, value)
      this.stateSet += pkey -> ByteString.copyFrom(value)
    } else {
      //如果待写入的值是null，采用None替代，并转字节数组
      val nv = ByteString.EMPTY
      this.srOfTransaction.put(pkey, nv.toByteArray)
      this.stateSet += pkey -> nv
    }
    if (this.stateDel.contains(pkey)) this.stateDel -= pkey
  }

  private def get(key: Key): Array[Byte] = {
    val v = this.srOfTransaction.get(key)
    if (v == null) {
      this.stateGet += key -> ByteString.EMPTY
      null
    } else {
      if (v.length == 0) {
        this.stateGet += key -> ByteString.EMPTY
        null
      } else {
        this.stateGet += key -> ByteString.copyFrom(v)
        v
      }
    }
  }

  private def getState(key: Key): Array[Byte] = {
    get(pre_key + PRE_SPLIT + key)
  }

  def getStateEx(chainId: String, chainCodeName: String, contractInstanceId: String, key: Key): Any = {
    checkKeyName(key)
    val v = get(chainId + PRE_SPLIT + chainCodeName + PRE_SPLIT + contractInstanceId + PRE_SPLIT + key)
    if (v == null)
      null
    else
      deserialise(v)
  }

  def getStateEx(chainId: String, chainCodeName: String, key: Key): Any = {
    checkKeyName(key)
    val v = get(chainId + PRE_SPLIT + chainCodeName + PRE_SPLIT + PRE_SPLIT + PRE_SPLIT + key)
    if (v == null)
      null
    else
      deserialise(v)
  }

  def getCrossKey(chainId: String, chainCodeName: String, key: Key):String = {
    chainId + PRE_SPLIT + chainCodeName + PRE_SPLIT + PRE_SPLIT + PRE_SPLIT + key
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-07-22
   * @category 根据账户的DID标识获取完整账户信息
   * @param creditCode :String 账户的DID标识
   * @return Signer 账户完整信息
   * */
  def getDIDSigner(creditCode: String): Signer = {
    def specialGetVal(key: Key): Any = {
      checkKeyName(key)
      val somePreKey = KeyPrefixManager.getWorldStateKeyPrefix(ctx.getConfig, "RdidOperateAuthorizeTPL", "")
      val v = get(somePreKey + PRE_SPLIT + key)
      if (v == null)
        null
      else
        deserialise(v)
    }

    val tmpSigner = specialGetVal(signerPrefix + creditCode)
    // 判断是否有值
    if (tmpSigner != null && tmpSigner.isInstanceOf[Signer]) {
      tmpSigner.asInstanceOf[Signer]
    } else {
      null
    }
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-07-22
   * @category 根据账户的DID标识和证书名称获取证书信息
   * @param creditCode :String 账户的DID标识
   * @param certName   :String 证书名称
   * @return Certificate 证书信息
   * */
  def getSignerCert(creditCode: String, certName: String): Certificate = {
    val tmpCert = this.getVal(certPrefix + creditCode + "." + certName)
    // 判断是否有值
    if (tmpCert != null && tmpCert.isInstanceOf[Certificate]) {
      tmpCert.asInstanceOf[Certificate]
    } else {
      null
    }
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-07-22
   * @category 验证签名结果是否正确
   * @param originalContent  :Array[Byte] 签名的原始信息
   * @param signatureResults :Array[Byte] 签名结果
   * @param certInfo         :CertId  账户证书标识
   * @return Boolean 验证结果，true 验证通过；false 验证不通过
   * */
  def VerifySignature(originalContent: Array[Byte], signatureResults: Array[Byte], certInfo: CertId): Boolean = {
    this.ctx.getSignTool.verify(signatureResults, originalContent, certInfo)
  }

  //判断账号是否节点账号 TODO
  def bNodeCreditCode(credit_code: String): Boolean = {
    ctx.getSignTool.isNode4Credit(credit_code)
  }

  def getCurrentContractDeployer: String = {
    val coder = this.getVal(t.getCid.chaincodeName)
    try {
      if (coder != null) {
        coder.asInstanceOf[String]
      } else {
        ""
      }
    } catch {
      case e: Exception => ""
    }
  }

  def getChainNetId: String = {
    config.getChainNetworkId
  }

  def getIdentityNetName: String = {
    config.getIdentityNetName
  }

  def getAccountContractName: String = {
    config.getAccountContractName
  }

  def getAccountContractCodeName: String = {
    config.getAccountContractName
  }

  def getAccountContractVersion: Int = {
    config.getAccountContractVersion
  }

  def isDidContract: Boolean = {
    IdTool.isDidContract(config.getAccountContractName)
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

  def getSha256Tool: Sha256 = {
    ctx.getHashTool
  }

  //通过该接口获取日志器，合约使用此日志器输出业务日志。
  def getLogger: Logger = {
    RepLogger.Business_Logger
  }

  def getDIDURIPrefix: String = {
    if (isDidContract) {
//      s"did:rep:${this.getChainNetId}:"
      "did:rep:"
    } else {
      ""
    }
  }

  @Deprecated
  def isExistKey4DID(key: String): Boolean = {
    val baseKey = KeyPrefixManager.getCustomNetKeyPrefix(ctx.getConfig.getIdentityNetName, ctx.getConfig.getAccountContractName) + PRE_SPLIT + key
    val b = get(baseKey)
    if (b == null) {
      if (ctx.getConfig.getIdentityNetName.equalsIgnoreCase(ctx.getConfig.getChainNetworkId)) {
        false
      } else {
        val businessKey = KeyPrefixManager.getCustomNetKeyPrefix(ctx.getConfig.getChainNetworkId, ctx.getConfig.getAccountContractName) + PRE_SPLIT + key
        val bs = get(businessKey)
        if (bs == null) {
          false
        } else {
          true
        }
      }
    } else {
      true
    }
  }

  def permissionCheck(did: String, certName: String, op: String): Boolean = {
    this.ctx.getPermissionVerify.CheckPermission(did, certName, op, this.srOfTransaction.getBlockPreload)
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2023-04-22
   * @category 发送合约事件到Sandbox_logger日志文件，供合约开负者调用
   * @param content : 合约开发者需要输出的事件内容，json字符串
   * @return
   * */
  def sendContractEventToLog(content: String): Unit = {
    RepLogger.info(RepLogger.Sandbox_Logger, "CONTRACT_EVENT:"+getContractEvent(content))
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2023-04-22
   * @category 发送合约事件到消息订阅通道（websocket），供合约开负者调用
   * @param content : 合约开发者需要输出的事件内容，json字符串
   * @return
   * */
  def sendContractEventToSubscribe(content: String): Unit = {
    val pe = PeerExtension(system)
    val evt = new Event(pe.getSysTag, Topic.CONTRACT_EVENT,
      Event.Action.CONTRACT_EVENT,None,getContractEvent(content))
    pe.getRepChainContext.getCustomBroadcastHandler.PublishOfCustom(this.context,mediator,Topic.Event,evt)
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2023-04-22
   * @category 合约消息定义模板
   * @param content : 合约开发者需要输出的事件内容，json字符串
   * @return 返回String，为json字符串，增加了事件是哪个节点发起，执行的是哪条交易，哪个合约。
   * */
  private def getContractEvent(content:String):String={
    val jv = try{
      JsonParser.parse(content)
    }catch {
      case e:Exception=>content
    }
    var map:HashMap[String,Any] = new HashMap[String,Any]
    map += "from"->ctx.getSystemName
    map += "txID"->this.t.id
    map += "chaincode"->IdTool.getCid(this.t.getCid)
    map += "content"->jv //content
    write(map)
  }

    /**
     * @author jiangbuyun
     * @version 2.0
     * @since 2023-04-22
     * @category 跨合约调用方法，支持合于开发者在合约中通过调用该方法实现跨合约之间的调用
     * @param cid : ChaincodeId 跨链调用的合约id
     * @param chaincodeInputFunc : String 跨链调用的合约方法名称
     * @param params : Seq[String]跨链调用的参数
     * @return TransactionResult 返回跨合约方法调用的返回值，没有错误，err==None;否则包含错误信息
     * */
    def crossContractCall(cid: ChaincodeId,
                          chaincodeInputFunc: String,
                          params: Seq[String]): TransactionResult = {
      var result: TransactionResult = null
      if (cid == null ) {
        throw new Exception("The chaincodeId called across contracts is empty")
      } else if (chaincodeInputFunc == null || chaincodeInputFunc.isEmpty) {
        throw new Exception("The method called across contracts is empty")
      }  else if (cid.chaincodeName == this.t.getCid.chaincodeName) {
        throw new Exception("Equal contract names for cross contract calls")
      }
      val rs = ExecuteTransaction(Seq(createTransactionForCrossContractCalled(
        cid, chaincodeInputFunc, params)), this.identifier)
      if(rs.isEmpty){
        throw new Exception("No execution result returned")
      }else{
        result = rs(0)
      }
      if(result == null){
        throw new Exception("Unknown exception occurred during call")
      }
      if(result.err != None && result.err.get.code > 0){
        throw new Exception(result.err.get.reason)
      }
      result
    }

    private def  createTransactionForCrossContractCalled(cid: ChaincodeId,
                                                         chaincodeInputFunc: String,
                                                         params: Seq[String]):Transaction={
      var t1: Transaction = new Transaction()
      val txid1 = IdTool.getRandomUUID
      val cip = new ChaincodeInput(chaincodeInputFunc, params)
      t1 = t1.withId(txid1)
      t1 = t1.withCid(cid)
      t1 = t1.withIpt(cip)
      t1 = t1.withType(rep.proto.rc2.Transaction.Type.CHAINCODE_INVOKE)
      t1 = t1.clearSignature
      t1
    }

    private def ExecuteTransaction(ts: Seq[Transaction], db_identifier: String): Seq[TransactionResult] = {
      import akka.pattern.{AskTimeoutException, ask}
      import scala.concurrent.duration._
      val pe = PeerExtension(system)
      implicit val timeout = Timeout((pe.getRepChainContext.getTimePolicy.getTimeoutPreload).seconds)
      try {
        val future2 = pe.getActorRef(ModuleActorType.ActorType.transactiondispatcher) ? new DoTransactionOfCrossContract(ts, db_identifier, TypeOfSender.FromPreloader)
        val result = Await.result(future2, timeout.duration).asInstanceOf[Seq[TransactionResult]]
        result
      } catch {
        case e: Exception =>
          Seq(new TransactionResult(t.id, Map.empty, Map.empty, Map.empty, Option(ActionResult(ResultCode.Transaction_Exception_In_SandboxOfScala, e.getMessage))))
      }
    }

  }
