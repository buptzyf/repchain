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

import akka.actor.{Actor, actorRef2Scala}
import com.google.protobuf.ByteString
import rep.api.rest.ResultCode
import rep.utils._
import rep.network.tools.PeerExtension
import rep.log.{RepLogger, RepTimeTracer}
import rep.proto.rc2.{ActionResult, ChaincodeId, Transaction, TransactionResult}
import rep.storage.chain.KeyPrefixManager
import scala.collection.immutable.HashMap
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * 合约容器的抽象类伴生对象,定义了交易执行结果的case类
 *
 * @author c4w
 *
 */
object Sandbox {
  val ERR_UNKNOWN_TRANSACTION_TYPE = "无效的交易类型"
  val ERR_UNKNOWN_CONTRACT_STATUS = "未知的合约状态"

  val SplitChainCodeId = "_"
  //日志前缀
  //t:中含txid可以找到原始交易; r:执行结果; merkle:执行完worldstate的hash; err:执行异常
  /**
   * 交易执行结果类
   * @param txId 传入交易实例
   * @param r 执行结果,任意类型
   * @param statesGet 合约执行中对worldState的读操作
   * @param statesSet 合约执行中对worldState的写操作
   * @param err 执行中抛出的异常信息
   */
  case class DoTransactionResult(txId: String, r: ActionResult,
                                 statesGet:  HashMap[String,ByteString],
                                 statesSet:  HashMap[String,ByteString],
                                 err: Option[akka.actor.Status.Failure])

  /**
   * 合约执行异常类
   *  @param message 异常信息的文本描述
   *  @param cause 导致异常的原因
   */
  case class SandboxException(
                               private val message: String    = "",
                               private val cause:   Throwable = None.orNull)
    extends Exception(message, cause)
}

/**
 * 合约容器的抽象类，提供与底层进行API交互的shim实例，用于与存储交互的实例pe
 * @author c4w
 * 目前已实现的合约容器包括SandboxJS—以javascript作为合约脚本语言,不支持debug,性能较低;
 * 另一个实现是以scala作为合约脚本语言的SandboxScalax，支持debug,性能较高
 *
 * @constructor 以合约在区块链上的链码id作为合约容器id建立实例
 * @param cid 链码id
 */
abstract class Sandbox(cid: ChaincodeId) extends Actor {
  import SandboxDispatcher._
  import Sandbox._
  import scala.concurrent.duration._
  protected val PRE_STATE = "_STATE"
  //与存储交互的实例
  val pe = PeerExtension(context.system)
  val sTag = pe.getSysTag
  protected val timeout = pe.getRepChainContext.getTimePolicy.getTimeoutPreload / 4 * 1000  //单位是毫秒

  //与底层交互的api实例,不同版本的合约KV空间重叠
  var shim : Shim = null //new Shim(context.system, cid.chaincodeName,)
  val addr_self = akka.serialization.Serialization.serializedActorPath(self)
  val permissioncheck = pe.getRepChainContext.getPermissionVerify

  def ExecutionInTimeoutManagement[T](timeoutWithMS: Long)(f: => T): T = {
    Await.result(Future(f), timeoutWithMS milliseconds)
  }

  def errAction(errCode: Int): ActionResult = {
    errCode match {
      case -101 =>
        ActionResult(errCode, "目标合约不存在")
    }
    ActionResult(errCode, "不明原因")
  }
  /**
   * 消息处理主流程,包括对交易处理请求、交易的预执行处理请求、从存储恢复合约的请求
   *
   */
  def receive = {
    //交易处理请求
    case dotrans: DoTransactionOfSandbox =>
      isStart = true
      RepTimeTracer.setStartTime(pe.getSysTag, "transaction-sandbox-do", System.currentTimeMillis(), pe.getCurrentHeight+1, dotrans.ts.length)
      val tr = onTransactions(dotrans)
      RepTimeTracer.setEndTime(pe.getSysTag, "transaction-sandbox-do", System.currentTimeMillis(), pe.getCurrentHeight+1, dotrans.ts.length)
      sender ! tr.toSeq
    case dotransOfCache: DoTransactionOfSandboxOfCache =>
      isStart = true
      if(pe.getTrans(dotransOfCache.cacheIdentifier) != null){
        val ts = pe.getTrans(dotransOfCache.cacheIdentifier)
        RepTimeTracer.setStartTime(pe.getSysTag, "transaction-sandbox-do", System.currentTimeMillis(), pe.getCurrentHeight+1, ts.length)
        val tr = onTransactionsOfCache(dotransOfCache,ts)
        RepTimeTracer.setEndTime(pe.getSysTag, "transaction-sandbox-do", System.currentTimeMillis(), pe.getCurrentHeight+1, ts.length)
        sender ! tr.toSeq
      }
  }

  private def onTransactions(dotrans: DoTransactionOfSandbox): Array[TransactionResult] = {
    var rs = scala.collection.mutable.ArrayBuffer[TransactionResult]()
    dotrans.ts.foreach(t=>{
      rs += onTransaction(DoTransactionOfSandboxInSingle(t,dotrans.da,dotrans.contractStateType))
    })
    RepTimeTracer.setEndTime(pe.getSysTag, "transaction-dispatcher", System.currentTimeMillis(), pe.getCurrentHeight+1, dotrans.ts.length)
    rs.toArray
  }

  private def onTransactionsOfCache(dotrans: DoTransactionOfSandboxOfCache,ts:Seq[Transaction]): Array[TransactionResult] = {
    var rs = scala.collection.mutable.ArrayBuffer[TransactionResult]()
    ts.foreach(t=>{
      rs += onTransaction(DoTransactionOfSandboxInSingle(t,dotrans.da,dotrans.contractStateType))
    })
    RepTimeTracer.setEndTime(pe.getSysTag, "transaction-dispatcher", System.currentTimeMillis(), pe.getCurrentHeight+1, ts.length)
    rs.toArray
  }

  private def onTransaction(dotrans: DoTransactionOfSandboxInSingle): TransactionResult = {
    try {
      /*if(dotrans.t.`type` == Transaction.Type.CHAINCODE_DEPLOY){
        System.out.println("sss")
      }*/
      shim = new Shim(context.system,dotrans.t,dotrans.da)
      checkTransaction(dotrans)
      doTransaction(dotrans)
    } catch {
      case e: Exception =>
        RepLogger.except4Throwable(RepLogger.Sandbox_Logger,e.getMessage,e)
        RepLogger.except(RepLogger.Sandbox_Logger, dotrans.t.id, e)
        new TransactionResult(dotrans.t.id, Map.empty,Map.empty,Map.empty,Option(ActionResult(ResultCode.Transaction_Exception_In_Sandbox,e.getMessage)))
    }
  }

  /**
   * 交易处理抽象方法，接受待处理交易，返回处理结果
   *
   *  @return 交易执行结果
   */
  def doTransaction(dotrans: DoTransactionOfSandboxInSingle): TransactionResult

  protected var ContractStatus : Option[Boolean] = None
  protected var ContractStatusSource : Option[Int] = None //1 来自持久化；2 来自预执行
  private var isStart : Boolean = true

  private def getContractStatus(txcid: String,contractStateType: ContractStateType.Value,oid:String,da:String,tid:String)= {
    var tmpStatus : Option[Boolean] = None
    if(this.ContractStatus == None){
      //说明合约容器处于初始化状态
      if(contractStateType == ContractStateType.ContractInSnapshot){
        //当前合约处于快照中，尚未出块,属于部署阶段
        tmpStatus = getStatusFromSnapshot(txcid,oid,da,tid)
        ContractStatusSource = Some(2)
        RepLogger.System_Logger.trace(s"systemName=${pe.getSysTag},######cname=${txcid},txid=${tid}当前合约处于快照中，尚未出块,属于部署阶段")
      }else if(contractStateType == ContractStateType.ContractInLevelDB){
        //当前合约部署已经出块，从持久化中装载该合约的状态
        tmpStatus = getStatusFromDB(txcid,oid,tid)
        ContractStatusSource = Some(1)
        RepLogger.System_Logger.trace(s"systemName=${pe.getSysTag},######cname=${txcid},txid=${tid},当前合约部署已经出块，从持久化中装载该合约的状态")
      }
      if(tmpStatus == None){
        RepLogger.System_Logger.trace(s"systemName=${pe.getSysTag},######cname=${txcid},txid=${tid},当前合约状态未知")
        throw SandboxException(ERR_UNKNOWN_CONTRACT_STATUS)
      }else{
        ContractStatus = tmpStatus
        isStart = false
        RepLogger.System_Logger.trace(s"systemName=${pe.getSysTag},######cname=${txcid},txid=${tid},合约状态=${tmpStatus}")
      }
    }else if(isStart){
        //当前交易序列第一次检查合约状态，需要判断是否需要重新装载合约状态
      RepLogger.System_Logger.trace(s"systemName=${pe.getSysTag},######cname=${txcid},txid=${tid}," +
        s"StateChanged=${pe.getRepChainContext.getStateChange(txcid)}," +
        s"当前交易序列第一次检查合约状态，需要判断是否需要重新装载合约状态")
        if(contractStateType == ContractStateType.ContractInLevelDB && ContractStatusSource.get == 2
        || contractStateType == ContractStateType.ContractInLevelDB && pe.getRepChainContext.getStateChange(txcid)) {
          //当前合约已经持久化，并且合约状态的值还是来自于预执行，需要重新装载
          tmpStatus = getStatusFromDB(txcid,oid,da)
          if (tmpStatus == None) {
            RepLogger.System_Logger.trace(s"systemName=${pe.getSysTag},######cname=${txcid},txid=${tid},合约状态=${tmpStatus}," +
              s"StateChanged=${pe.getRepChainContext.getStateChange(txcid)},当前合约已经持久化，并且合约状态的值还是来自于预执行，需要重新装载")
            throw SandboxException(ERR_UNKNOWN_CONTRACT_STATUS)
          } else {
            ContractStatus = tmpStatus
            ContractStatusSource = Some(1)
            RepLogger.System_Logger.trace(s"systemName=${pe.getSysTag},######cname=${txcid},txid=${tid},合约状态=${tmpStatus}," +
              s"StateChanged=${pe.getRepChainContext.getStateChange(txcid)},从持久化中获取合约状态")
          }
        }
      isStart = false
    }
    RepLogger.System_Logger.trace(s"systemName=${pe.getSysTag},######cname=${txcid},txid=${tid},合约状态=${this.ContractStatus},返回最后的合约状态")
    this.ContractStatus
  }

  private def getStatusFromDB(txCid: String,oid:String,da:String):Option[Boolean]={
    var r : Option[Boolean] = None

    val chainCodeName : String = if(txCid.lastIndexOf(SplitChainCodeId)>0){
      txCid.substring(0,txCid.lastIndexOf(SplitChainCodeId))
    } else txCid
    val blockPreload = pe.getRepChainContext.getBlockPreload(da)
    val state = blockPreload.getObjectFromDB(KeyPrefixManager.getWorldStateKey(pe.getRepChainContext.getConfig,txCid+PRE_STATE,chainCodeName,oid))
    //val state = shim.getVal(txCid+PRE_STATE)
    if(state != None){
      r = Some(state.get.asInstanceOf[Boolean])
    }
    r
  }

  private def getStatusFromSnapshot(txCid: String,oid:String,da:String,tid:String):Option[Boolean]={
    var r : Option[Boolean] = None
    val srOfTransaction = pe.getRepChainContext.getBlockPreload(da).getTransactionPreload(tid)
    //val state = srOfTransaction.get(KeyPrefixManager.getWorldStateKey(pe.getRepChainContext.getConfig,txCid+ PRE_STATE,txCid,oid))
    val state = shim.getVal(txCid+PRE_STATE)
    if(state != null){
      r = Some(state.asInstanceOf[Boolean])
    }
    r
  }

  private def IsCurrentSigner(dotrans: DoTransactionOfSandboxInSingle) {
    val cn = dotrans.t.cid.get.chaincodeName
    //val srOfTransaction = pe.getRepChainContext.getBlockPreload(dotrans.da)
    //val coder = srOfTransaction.get(KeyPrefixManager.getWorldStateKey(pe.getRepChainContext.getConfig,cn,IdTool.getCid(dotrans.t.getCid),dotrans.t.oid))
    val coder = shim.getVal(cn)
    if (coder != null) {
      //合约已存在且部署,需要重新部署，但是当前提交者不是以前提交者
      if (!dotrans.t.signature.get.certId.get.creditCode.equals(coder.asInstanceOf[String]))
        throw new SandboxException(ERR_CODER)
    }
  }

  private def checkTransaction(dotrans: DoTransactionOfSandboxInSingle) = {
    val txcid = IdTool.getTXCId(dotrans.t)
    dotrans.t.`type` match {
      case Transaction.Type.CHAINCODE_DEPLOY =>
        dotrans.contractStateType match {
          case ContractStateType.ContractInLevelDB =>
            throw SandboxException(ERR_REPEATED_CID)
          case _ =>
            //检查合约部署者以及权限
            if(IdTool.isDidContract(pe.getRepChainContext.getConfig.getAccountContractName)){
              permissioncheck.CheckPermissionOfDeployContract(dotrans)
            }else{
              throw SandboxException(ERR_NONDID_CONTRACT)
              //IsCurrentSigner(dotrans)
            }
        }
      case Transaction.Type.CHAINCODE_SET_STATE =>
        //检查合约部署者以及权限
        if(IdTool.isDidContract(pe.getRepChainContext.getConfig.getAccountContractName)){
          permissioncheck.CheckPermissionOfSetStateContract(dotrans)
        }else{
          throw SandboxException(ERR_NONDID_CONTRACT)
          //IsCurrentSigner(dotrans)
        }

      case Transaction.Type.CHAINCODE_INVOKE =>
        val status = getContractStatus(txcid,dotrans.contractStateType,dotrans.t.oid,dotrans.da,dotrans.t.id)
        if(status == None ){
          throw SandboxException(ERR_UNKNOWN_CONTRACT_STATUS)
        }else if( !status.get){
          throw new SandboxException(ERR_DISABLE_CID)
        }else{
          if(IdTool.isDidContract(pe.getRepChainContext.getConfig.getAccountContractName)) {
            permissioncheck.CheckPermissionOfInvokeContract(dotrans)
          }else{
            throw SandboxException(ERR_NONDID_CONTRACT)
          }
        }
      case _ => throw SandboxException(ERR_UNKNOWN_TRANSACTION_TYPE)
    }
  }

  protected def DoDeploy(tx_cid: String, t: Transaction) = {
    //deploy返回chancode.name
    //新部署合约利用kv记住cid对应的txid,并增加kv操作日志,以便恢复deploy时能根据cid找到当时deploy的tx及其代码内容
    //部署合法性在外围TransProcessor中检查
    val key_tx = tx_cid
    val cn = cid.chaincodeName
    val key_coder = cn
    val coder = t.signature.get.certId.get.creditCode
    shim.setVal(key_tx, t.id)

    //写入初始状态
    val key_tx_state = tx_cid + PRE_STATE //KeyPrefixManager.getWorldStateKey(pe.getRepChainContext.getConfig,tx_cid + PRE_STATE,tx_cid,t.oid)
    this.ContractStatus = Some(true)
    this.ContractStatusSource = Some(2)

    shim.setVal(key_tx_state, true)
    //利用kv记住合约的开发者
    shim.setVal(key_coder, coder)
  }
}