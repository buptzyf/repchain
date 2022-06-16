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

package rep.api.rest

import akka.util.Timeout
import scala.concurrent.duration._
import rep.crypto._
import org.json4s._
import org.json4s.jackson.JsonMethods
import akka.actor.Props
import rep.log.RepLogger
import rep.network.base.ModuleBase
import rep.network.consensus.byzantium.ConsensusCondition
import rep.proto.rc2.{ActionResult, ChaincodeId, Event, Transaction}
import rep.storage.chain.block.BlockSearcher
import rep.storage.db.factory.DBFactory
import rep.utils.GlobalUtils.EventType
import rep.utils.{MessageToJson, SerializeUtils}

/**
 * RestActor伴生object，包含可接受的传入消息定义，以及处理的返回结果定义。
 * 以及用于建立Tranaction，检索Tranaction的静态方法
 *
 * @author c4w created
 *
 */

object RestActor {
  def props(name: String): Props = Props(classOf[RestActor], name)


  case object ChainInfo

  case object NodeNumber

  case object TransNumber

  case object AcceptedTransNumber

  case class BlockId(bid: String)

  case class BlockHeight(h: Int)

  case class BlockTime(createTime: String, createTimeUtc: String)

  case class BlockTimeForHeight(h: Long)

  case class BlockTimeForTxid(txid: String)

  case class BlockHeightStream(h: Int)

  case class TransactionId(txid: String)

  case class TransactionStreamId(txid: String)

  case class TranInfoAndHeightId(txid: String)

  case class TranInfoHeight(tranInfo: JValue, height: Long)

  case class TransNumberOfBlock(height: Long)

  case class QueryDB(netId: String, chainCodeName: String, oid: String, key: String)

  case class PostResult(txid: String, result: Option[ActionResult], err: Option[String])

  case class QueryResult(result: Option[JValue])

  case class resultMsg(result: String)

  case class CSpec(methodType: Int, chainCodeName: String, chainCodeVersion: Int,
                   iptFunc: String, iptArgs: Seq[String], timeout: Int, legal_prose: String,
                   code: String, codeType: Int, state: Boolean, gasLimited: Int, oid: String, runType: Int, stateType: Int, contractLevel: Int)

  case class tranSign(tran: String)


}

/**
 * RestActor负责处理rest api请求
 *
 */
class RestActor(moduleName: String) extends ModuleBase(moduleName) {

  import RestActor._
  import spray.json._
  import akka.http.scaladsl.model.{HttpResponse, MediaTypes, HttpEntity}
  import rep.network.autotransaction.Topic
  import akka.cluster.pubsub.DistributedPubSubMediator.Publish

  val config = pe.getRepChainContext.getConfig
  val contractOperationMode = config.getContractRunMode

  implicit val timeout = Timeout(1000.seconds)
  val sr: BlockSearcher = new BlockSearcher(pe.getRepChainContext)
  private val consensusCondition = new ConsensusCondition(pe.getRepChainContext)

  /**
   * 根据节点名称和chainCode定义建立交易实例
   *
   * @param nodeName 节点名称
   * @param c        chainCode定义
   */
  def buildTranaction(nodeName: String, c: CSpec): Transaction = {
    val method_type = c.methodType match {
      case 1 =>
        Transaction.Type.CHAINCODE_DEPLOY
      case 2 =>
        Transaction.Type.CHAINCODE_INVOKE
      case 3 =>
        Transaction.Type.CHAINCODE_SET_STATE
      case _ =>
        Transaction.Type.UNDEFINED
    }
    val code_type = c.codeType match {
      case 1 =>
        rep.proto.rc2.ChaincodeDeploy.CodeType.CODE_JAVASCRIPT
      case 2 =>
        rep.proto.rc2.ChaincodeDeploy.CodeType.CODE_SCALA
      case 3 =>
        rep.proto.rc2.ChaincodeDeploy.CodeType.CODE_VCL_DLL
      case 4 =>
        rep.proto.rc2.ChaincodeDeploy.CodeType.CODE_VCL_EXE
      case 5 =>
        rep.proto.rc2.ChaincodeDeploy.CodeType.CODE_VCL_WASM
      case 6 =>
        rep.proto.rc2.ChaincodeDeploy.CodeType.CODE_WASM
      case _ =>
        rep.proto.rc2.ChaincodeDeploy.CodeType.CODE_SCALA
    }
    val run_type = c.runType match {
      case 1 =>
        rep.proto.rc2.ChaincodeDeploy.RunType.RUN_SERIAL
      case 2 =>
        rep.proto.rc2.ChaincodeDeploy.RunType.RUN_PARALLEL
      case 3 =>
        rep.proto.rc2.ChaincodeDeploy.RunType.RUN_OPTIONAL
      case _ =>
        rep.proto.rc2.ChaincodeDeploy.RunType.RUN_SERIAL
    }
    val state_type = c.stateType match {
      case 1 =>
        rep.proto.rc2.ChaincodeDeploy.StateType.STATE_BLOCK
      case 2 =>
        rep.proto.rc2.ChaincodeDeploy.StateType.STATE_GLOBAL
      case _ =>
        rep.proto.rc2.ChaincodeDeploy.StateType.STATE_BLOCK
    }
    val contract_Level = c.contractLevel match {
      case 1 =>
        rep.proto.rc2.ChaincodeDeploy.ContractClassification.CONTRACT_SYSTEM
      case 2 =>
        rep.proto.rc2.ChaincodeDeploy.ContractClassification.CONTRACT_CUSTOM
      case _ =>
        rep.proto.rc2.ChaincodeDeploy.ContractClassification.CONTRACT_CUSTOM
    }
    val chaincodeId = new ChaincodeId(c.chainCodeName, c.chainCodeVersion)
    val gas_limited = if (c.gasLimited < 0) 0 else c.gasLimited
    val oid = if (c.oid == null || c.oid.equalsIgnoreCase("null")) "" else c.oid
    if (method_type == Transaction.Type.CHAINCODE_DEPLOY) {
      pe.getRepChainContext.getTransactionBuilder.createTransaction4Deploy(nodeName, chaincodeId, c.code,
        c.legal_prose, c.timeout, code_type, run_type,
        state_type, contract_Level, gas_limited)
    } else if (method_type == Transaction.Type.CHAINCODE_INVOKE) {
      pe.getRepChainContext.getTransactionBuilder.createTransaction4Invoke(nodeName, chaincodeId, c.iptFunc, c.iptArgs, gas_limited, oid)
    } else if (method_type == Transaction.Type.CHAINCODE_SET_STATE) {
      pe.getRepChainContext.getTransactionBuilder.createTransaction4State(nodeName, chaincodeId, c.state)
    } else {
      null
    }
  }

  // 先检查交易大小，然后再检查交易是否已存在，再去验证签名，如果没有问题，则广播
  def preTransaction(t: Transaction): Unit = {
    val tranLimitSize = config.getBlockMaxLength / 3
    if (t.toByteArray.length > tranLimitSize) {
      sender ! PostResult(t.id, None, Option(s"交易大小超出限制： ${tranLimitSize}，请重新检查"))
    } else if (!this.consensusCondition.CheckWorkConditionOfSystem(pe.getRepChainContext.getNodeMgr.getStableNodes.size)) {
      sender ! PostResult(t.id, None, Option("共识节点数目太少，暂时无法处理交易"))
    } else {
      try {
        //if (pe.getTransPoolMgr.getTransLength() < SystemProfile.getMaxCacheTransNum) {

        //pe.getTransPoolMgr.putTran(t,pe.getSysTag)

        if (config.isBroadcastTransaction) {
          mediator ! Publish(Topic.Transaction, t)
        }
        sendEvent(EventType.PUBLISH_INFO, mediator, pe.getSysTag, Topic.Transaction, Event.Action.TRANSACTION)


        sender ! PostResult(t.id, None, None)
        /*} else {
          // 交易缓存池已满，不可继续提交交易
          sender ! PostResult(t.id, None, Option(s"交易缓存池已满，容量为${pe.getTransPoolMgr.getTransLength()}，不可继续提交交易"))
        }*/
      } catch {
        case e: Exception =>
          sender ! PostResult(t.id, None, Option(e.getMessage))
      }
    }
  }


  def receive: Receive = {

    case tranSign(tr: String) =>
      val tmpstart = System.currentTimeMillis()
      val tr1 = BytesHex.hex2bytes(tr) // 解析交易编码后的16进制字符串,进行解码16进制反解码decode
      var txr = Transaction.defaultInstance
      try {
        txr = Transaction.parseFrom(tr1)
        preTransaction(txr)
      } catch {
        case e: Exception =>
          sender ! PostResult(txr.id, None, Option(s"transaction parser error! + ${e.getMessage}"))
      }
      val tmpend = System.currentTimeMillis()
      RepLogger.trace(RepLogger.OutputTime_Logger, this.getLogMsgPrefix(s"API recv trans time,thread-id=${Thread.currentThread().getName + "-" + Thread.currentThread().getId},spent time=${(tmpend - tmpstart)}" + "~" + selfAddr))

    //处理post CSpec构造交易的请求
    case c: CSpec =>
      var txr = Transaction.defaultInstance
      //debug状态才动用节点密钥签名
      if (contractOperationMode == 0) {
        //构建transaction并通过peer预执行广播
        txr = buildTranaction(pe.getSysTag, c)
        preTransaction(txr)
      } else
        sender ! PostResult(txr.id, None, Option("非Debug状态下此调用无效"))

    // 流式提交交易
    case t: Transaction =>
      preTransaction(t)

    // 根据高度检索块
    case BlockHeight(h) =>
      val bb = sr.getBlockByHeight(h)
      val r = bb match {
        case None => QueryResult(None)
        case _ =>
          val bl = bb.get
          QueryResult(Option(MessageToJson.toJson(bl)))
      }
      sender ! r

    case BlockTimeForHeight(h) =>
      val bb = sr.getBlockCreateTimeByHeight(h)
      val r = bb match {
        case None => QueryResult(None)
        case _ =>
          QueryResult(Option(JsonMethods.parse(SerializeUtils.toJson(bb.get))))
      }
      sender ! r

    case BlockTimeForTxid(txid) =>
      val bb = sr.getBlockCreateTimeByTxId(txid)
      val r = bb match {
        case None => QueryResult(None)
        case _ =>
          QueryResult(Option(JsonMethods.parse(SerializeUtils.toJson(bb.get))))
      }
      sender ! r

    // 根据高度检索块的子节流
    case BlockHeightStream(h) =>
      val bb = sr.getBlockByHeight(h)
      if (bb == None) {
        sender ! HttpResponse(entity = HttpEntity.Strict(MediaTypes.`application/octet-stream`, akka.util.ByteString.empty))
      } else {
        val body = akka.util.ByteString(bb.get.toByteArray)
        val entity = HttpEntity.Strict(MediaTypes.`application/octet-stream`, body)
        val httpResponse = HttpResponse(entity = entity)
        sender ! httpResponse
      }

    //根据block hash检索
    case BlockId(bid) =>
      val bb = sr.getBlockByBase64Hash(bid)
      val r = bb match {
        case None => QueryResult(None)
        case _ =>
          QueryResult(Option(MessageToJson.toJson(bb.get)))
      }
      sender ! r

    // 根据txid检索交易
    case TransactionId(txId) =>
      var r = sr.getTransactionByTxId(txId) match {
        case None =>
          QueryResult(None)
        case t: Some[Transaction] =>
          QueryResult(Option(MessageToJson.toJson(t.get)))
      }
      sender ! r

    // 根据txid检索交易字节流
    case TransactionStreamId(txId) =>
      val r = sr.getTransactionByTxId(txId)
      if (r.isEmpty) {
        sender ! HttpResponse(entity = HttpEntity.Strict(MediaTypes.`application/octet-stream`, akka.util.ByteString.empty))
      } else {
        val t = r.get
        val body = akka.util.ByteString(t.toByteArray)
        val entity = HttpEntity.Strict(MediaTypes.`application/octet-stream`, body)
        val httpResponse = HttpResponse(entity = entity)
        sender ! httpResponse
      }

    case TranInfoAndHeightId(txId) =>
      implicit val fomats = DefaultFormats
      var r = sr.getTransactionByTxId(txId) match {
        case None =>
          QueryResult(None)
        case t: Some[Transaction] =>
          val txr = t.get
          val tranInfoHeight = TranInfoHeight(MessageToJson.toJson(txr), sr.getBlockIndexByTxId(txr.id).get.getHeight)
          QueryResult(Option(Extraction.decompose(tranInfoHeight)))
      }
      sender ! r

    // 获取链信息
    case ChainInfo =>
      val cij = MessageToJson.toJson(sr.getChainInfo)
      sender ! QueryResult(Option(cij))

    case NodeNumber =>
      val stablenode = pe.getRepChainContext.getNodeMgr.getStableNodes.size
      val snode = pe.getRepChainContext.getNodeMgr.getNodes.size
      val rs = "{\"consensusnodes\":\"" + stablenode + "\",\"nodes\":\"" + snode + "\"}"
      sender ! QueryResult(Option(JsonMethods.parse(string2JsonInput(rs))))

    case TransNumber =>
      val num = pe.getRepChainContext.getTransactionPool.getCachePoolSize
      val rs = "{\"numberofcache\":\"" + num + "\"}"
      sender ! QueryResult(Option(JsonMethods.parse(string2JsonInput(rs))))

    case AcceptedTransNumber =>
      val num = sr.getChainInfo.totalTransactions + pe.getRepChainContext.getTransactionPool.getCachePoolSize
      val rs = "{\"acceptedNumber\":\"" + num + "\"}"
      sender ! QueryResult(Option(JsonMethods.parse(string2JsonInput(rs))))

    case TransNumberOfBlock(h) =>
      val num = sr.getNumberOfTransInBlockByHeight(h)
      val rs = "{\"transnumberofblock\":\"" + num + "\"}"
      sender ! QueryResult(Option(JsonMethods.parse(string2JsonInput(rs))))

    case QueryDB(netId, cName, oid, key) =>
      val dataAccess = DBFactory.getDBAccess(config)
      val tran_oid = oid match {
        case "" => "_"
        case _ => oid
      }
      val pkey = netId + "_" + cName + "_" + tran_oid + "_" + key
      val pvalue = dataAccess.getBytes(pkey)
      val jsonString = SerializeUtils.compactJson(SerializeUtils.deserialise(pvalue))
      sender ! QueryResult(Option(JsonMethods.parse(string2JsonInput(jsonString))))
  }

}