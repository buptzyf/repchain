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

import akka.actor.{Actor, ActorRef, Props, actorRef2Scala}
import rep.utils._
import rep.protos.peer._
import rep.network.tools.PeerExtension
import rep.storage.IdxPrefix.WorldStateKeyPreFix
import rep.storage._
import rep.utils.SerializeUtils.deserialise
import rep.log.RepLogger
import rep.authority.check.PermissionVerify

/**
 * 合约容器的抽象类伴生对象,定义了交易执行结果的case类
 *
 * @author c4w
 *
 */
object Sandbox {
  val ERR_UNKNOWN_TRANSACTION_TYPE = "无效的交易类型"

  val SplitChainCodeId = "_"
  //日志前缀
  //t:中含txid可以找到原始交易; r:执行结果; merkle:执行完worldstate的hash; err:执行异常
  /**
   * 交易执行结果类
   * @param txId 传入交易实例
   * @param r 执行结果,任意类型
   * @param ol 合约执行中对worldState的写入操作
   * @param err 执行中抛出的异常信息
   */
  case class DoTransactionResult(txId: String, r: ActionResult,
                                 ol:  List[OperLog],
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
  import spray.json._
  //与存储交互的实例
  val pe = PeerExtension(context.system)
  val sTag = pe.getSysTag

  //与底层交互的api实例,不同版本的合约KV空间重叠
  val shim = new Shim(context.system, cid.chaincodeName)
  val addr_self = akka.serialization.Serialization.serializedActorPath(self)
  val permissioncheck = PermissionVerify.GetPermissionVerify(pe.getSysTag)

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
      val tr = onTransaction(dotrans)
      sender ! tr
  }

  def onTransaction(dotrans: DoTransactionOfSandbox): DoTransactionResult = {
    try {
      shim.sr = ImpDataPreloadMgr.GetImpDataPreload(sTag, dotrans.da)
      checkTransaction(dotrans)
      shim.ol = new scala.collection.mutable.ListBuffer[OperLog]
      doTransaction(dotrans)
    } catch {
      case e: Exception =>
        RepLogger.except(RepLogger.Sandbox_Logger, dotrans.t.id, e)
        new DoTransactionResult(dotrans.t.id, null, null,
          Option(akka.actor.Status.Failure(e)))
    }
  }

  /**
   * 交易处理抽象方法，接受待处理交易，返回处理结果
   *  @param dotrans 交易分派消息
   *  @return 交易执行结果
   */
  def doTransaction(dotrans: DoTransactionOfSandbox): DoTransactionResult

  private def getContractEnableValueFromLevelDB(txcid: String): Option[Boolean] = {
    val db = ImpDataAccess.GetDataAccess(this.sTag)
    val key_tx_state = WorldStateKeyPreFix + txcid + PRE_STATE
    val state_bytes = db.Get(key_tx_state)
    if (state_bytes == null) {
      None
    } else {
      val state = deserialise(state_bytes).asInstanceOf[Boolean]
      Some(state)
    }
  }



  private def IsCurrentSigner(dotrans: DoTransactionOfSandbox) {
    val cn = dotrans.t.cid.get.chaincodeName
    val key_coder = WorldStateKeyPreFix + cn
    val coder_bytes = shim.sr.Get(key_coder)
    if (coder_bytes != null) {
      val coder = Some(deserialise(coder_bytes).asInstanceOf[String])
      //合约已存在且部署,需要重新部署，但是当前提交者不是以前提交者
      if (!dotrans.t.signature.get.certId.get.creditCode.equals(coder.get))
        throw new SandboxException(ERR_CODER)
    }
  }

  private def ContraceIsExist(txcid: String) {
    val key_tx_state = WorldStateKeyPreFix + txcid + PRE_STATE
    val state_bytes = shim.sr.Get(key_tx_state)
    //合约不存在
    if (state_bytes == null) {
      throw new SandboxException(ERR_INVOKE_CHAINCODE_NOT_EXIST)
    }
  }

  private def checkTransaction(dotrans: DoTransactionOfSandbox) = {
    val txcid = IdTool.getTXCId(dotrans.t)
    dotrans.t.`type` match {
      case Transaction.Type.CHAINCODE_DEPLOY =>
        dotrans.contractStateType match {
          case ContractStateType.ContractInLevelDB =>
            throw new SandboxException(ERR_REPEATED_CID)
          case _ =>
            //检查合约部署者
            //IsCurrentSigner(dotrans)
            permissioncheck.CheckPermissionOfDeployContract(dotrans,shim)

        }

      case Transaction.Type.CHAINCODE_SET_STATE =>
        ContraceIsExist(txcid)
        //IsCurrentSigner(dotrans)
        //new Account4RDidByContract(shim).hasPermissionOfSetStateContract(dotrans)
        permissioncheck.CheckPermissionOfSetStateContract(dotrans,shim)

      case Transaction.Type.CHAINCODE_INVOKE =>
        ContraceIsExist(txcid)
        val cstateInLevelDB = getContractEnableValueFromLevelDB(txcid)
        cstateInLevelDB match {
          case None =>
            dotrans.contractStateType match {
              case ContractStateType.ContractInSnapshot =>
              //ignor
              case _ =>
                //except
                throw new SandboxException(ERR_DISABLE_CID)
            }
          case _ =>
            if (!cstateInLevelDB.get) {
              throw new SandboxException(ERR_DISABLE_CID)
            } else {
              //ignore
            }
        }
        //new Account4RDidByContract(shim).hasPermissionOfInvokeContract(dotrans)
        permissioncheck.CheckPermissionOfInvokeContract(dotrans,shim)
      case _ => throw SandboxException(ERR_UNKNOWN_TRANSACTION_TYPE)
    }
  }
}