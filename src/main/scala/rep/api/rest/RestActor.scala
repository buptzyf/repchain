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

import akka.util.{ByteString, Timeout}
import akka.util.Timeout
import org.json4s.jackson.JsonMethods.{pretty, render}
import org.json4s.DefaultFormats

import scala.concurrent.duration._
import rep.crypto._
import org.json4s._
import org.json4s.jackson.JsonMethods
import akka.actor.Props
import rep.log.RepLogger
import rep.network.base.ModuleBase
import rep.network.consensus.byzantium.ConsensusCondition
import rep.network.module.ModuleActorType
import rep.proto.rc2.{ActionResult, ChaincodeId, Event, Transaction, TransactionResult}
import scalapb.json4s.JsonFormat
import rep.sc.TypeOfSender
import rep.storage.chain.block.BlockSearcher
import rep.storage.db.factory.DBFactory
import rep.utils.GlobalUtils.EventType
import rep.utils.{IdTool, MessageToJson, SerializeUtils}

import scala.concurrent.Await
import akka.pattern.ask
import com.fasterxml.jackson.databind.ObjectMapper
import rep.accumulator.verkle.VerkleTreeType
import rep.accumulator.verkle.util.ProofSerialize
import rep.accumulator.{Accumulator, PrimeTool, verkle}
import rep.api.rest.ResultCode._
import rep.censor.DataFilter
import rep.sc.SandboxDispatcher.DoTransaction

/**
 * RestActor伴生object，包含可接受的传入消息定义，以及处理的返回结果定义。
 * 以及用于建立Tranaction，检索Tranaction的静态方法
 *
 * @author c4w created
 * @author jayTsang modified
 */

object RestActor {
  def props(name: String): Props = Props(classOf[RestActor], name)

  case class ErrMessage(code: Int, msg: String)

  case object ChainInfo

  case object NodeInfo

  case object NodeNumber

  case object TransNumber

  case object AcceptedTransNumber

  case class BlockId(id: String,ip:String)
  case class BlockHeaderId(id: String,ip:String)

  case class BlockHeight(height: Long,ip:String)
  case class BlockHeaderHeight(height: Long,ip:String)

  case class BlockTime(createTime: String, createTimeUtc: String)

  case class BlockTimeForHeight(height: Long)

  case class BlockTimeForTxid(txid: String)

  case class BlockHeightStream(height: Int,ip:String)

  case class TransactionId(txid: String)

  case class ProofOfTransaction(txid:String)
  case class ProofOfOutput(proof:String,txid:String)

  case class TransactionStreamId(txid: String)

  case class TranInfoAndHeightId(txid: String)

  case class TranInfoHeight(tranInfo: JValue, height: Long)

  case class TransNumberOfBlock(height: Long)

  case class QueryDB(netId: String, chainCodeName: String, oid: String, key: String)

  case class PostResult(txid: String, err: Option[ErrMessage])

  case class QueryResult(result: Option[JValue])

  case class CSpec(methodType: Int, chainCodeName: String, chainCodeVersion: Int,
                   iptFunc: String, iptArgs: Seq[String], timeout: Int, legal_prose: String,
                   code: String, codeType: Int, state: Boolean, gasLimited: Int, oid: String, runType: Int, stateType: Int, contractLevel: Int)

  case class signedTran(tran: String,ip:String)
  case class streamTran(tran:Transaction,ip:String)

  case class CSpecTran(spec:CSpec,ip:String)

  case class DidDocumentReq(id: String)
  case class DidPubKey(
                     id: String,
                     `type`: String,
                     controller: String,
                     publicKeyPEM: String
                   )
  case class SerEnd(id: String, `type`: String, serviceEndpoint: String)
  case class DidDocument(
                        `@context`: String = "https://www.w3.org/ns/did/v1",
                        id: String,
                        controller: Seq[String],
                        created: String,
                        updated: String,
                        verificationMethod: Seq[DidPubKey],
                        authentication: Seq[Any],
                        assertionMethod: Seq[Any],
                        capabilityInvocation: Seq[Any],
                        service: Seq[SerEnd]
                        )
  case class DidPubKeyReq(didPubKeyId: String)
  val DELIMITER = "#"
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
  private val df = new DataFilter(pe.getRepChainContext)

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
      pe.getRepChainContext.getTransactionBuilder.createTransaction4Deploy(nodeName, chaincodeId, c.code, c.legal_prose, c.timeout,
        code_type, run_type, state_type, contract_Level, gas_limited)
    } else if (method_type == Transaction.Type.CHAINCODE_INVOKE) {
      pe.getRepChainContext.getTransactionBuilder.createTransaction4Invoke(nodeName, chaincodeId, c.iptFunc, c.iptArgs, gas_limited, oid)
    } else if (method_type == Transaction.Type.CHAINCODE_SET_STATE) {
      pe.getRepChainContext.getTransactionBuilder.createTransaction4State(nodeName, chaincodeId, c.state)
    } else {
      null
    }
  }

  private def sendTransaction(t: Transaction): Unit = {
    val pool = pe.getRepChainContext.getTransactionPool
    if (!pool.hasOverflowed) {
      //优先加入本地交易池
      pool.addTransactionToCache(t)
      if (config.isBroadcastTransaction) {
        //mediator ! Publish(Topic.Transaction, t)
        pe.getRepChainContext.getCustomBroadcastHandler.PublishOfCustom(context,mediator,Topic.Transaction,t)
      }
      sendEvent(EventType.PUBLISH_INFO, mediator, pe.getSysTag, Topic.Transaction, Event.Action.TRANSACTION)
      sender ! PostResult(t.id, None)
    } else {
      sender ! PostResult(t.id, Option(ErrMessage(TranPoolFulled, "交易池已经满了")))
    }
  }

  // 先检查交易大小，然后再检查交易是否已存在，再去验证签名，如果没有问题，则广播
  private def doTransaction(t: Transaction,ip:String): Unit = {
    val tranLimitSize = config.getBlockMaxLength(pe.getRepChainContext) / 3
    val pool = pe.getRepChainContext.getTransactionPool
    if(!pool.transactionChecked(t)){
      pe.getRepChainContext.getProblemAnalysis.AddProblemTrading(ip)
      sender ! PostResult(t.id, Option(ErrMessage(TransactionCheckedError, s"交易检查错误，可能的问题：交易ID为空，链码为空，链码版本号不对，交易类型不对。")))
    }else if (t.toByteArray.length > tranLimitSize) {
      pe.getRepChainContext.getProblemAnalysis.AddProblemTrading(ip)
      sender ! PostResult(t.id, Option(ErrMessage(TranSizeExceed, s"交易大小超出限制： ${tranLimitSize}，请重新检查")))
    } else if (!this.consensusCondition.CheckWorkConditionOfSystem(pe.getRepChainContext.getNodeMgr.getStableNodes.size)) {
      sender ! PostResult(t.id, Option(ErrMessage(ConsensusNodesNotEnough, "共识节点数目太少，暂时无法处理交易")))
    } else if (pe.getRepChainContext.getTransactionPool.isExistInCache(t.id) || sr.isExistTransactionByTxId(t.id)) {
      pe.getRepChainContext.getProblemAnalysis.AddProblemTrading(ip)
      sender ! PostResult(t.id, Option(ErrMessage(TranIdDuplicate, s"交易ID重复, ID为：${t.id}")))
    } else {
      try {
        val sig = t.signature.get.signature.toByteArray
        val tOutSig = t.clearSignature
        val certId = t.signature.get.certId.get
        if (pe.getRepChainContext.getSignTool.verify(sig, tOutSig.toByteArray, certId)) {
          RepLogger.info(RepLogger.Business_Logger, s"验证签名成功，txid: ${t.id},creditCode: ${t.signature.get.getCertId.creditCode}, certName: ${t.signature.get.getCertId.certName}")
          if (pe.getRepChainContext.getConfig.hasPreloadOfApi) {
            val future = pe.getActorRef(ModuleActorType.ActorType.transactiondispatcher) ? DoTransaction(Seq(t), "api_" + t.id, TypeOfSender.FromAPI)
            val result = Await.result(future, timeout.duration).asInstanceOf[Seq[TransactionResult]]
            val rv = result.head.err
            rv.get.code match {
              case 0 =>
                sendTransaction(t)
              case _ =>
                //预执行异常,废弃交易，向api调用者发送异常
                sender ! PostResult(t.id, Option(ErrMessage(rv.get.code, rv.get.reason)))
            }
          } else {
            sendTransaction(t)
          }
        } else {
          pe.getRepChainContext.getProblemAnalysis.AddProblemTrading(ip)
          RepLogger.info(RepLogger.Business_Logger, s"验证签名出错，txid: ${t.id},creditCode: ${t.signature.get.getCertId.creditCode}, certName: ${t.signature.get.getCertId.certName}")
          sender ! PostResult(t.id, Option(ErrMessage(SignatureVerifyFailed, "验证签名出错")))
        }
      } catch {
        case e: Exception =>
          sender ! PostResult(t.id, Option(ErrMessage(UnkonwFailure, e.getMessage)))
      }
    }
  }


  def receive: Receive = {

    case signedTran(tranHexString: String,ip:String) =>
      val tmpstart = System.currentTimeMillis()
      val tr1 = BytesHex.hex2bytes(tranHexString) // 解析交易编码后的16进制字符串,进行解码16进制反解码decode
      var txr = Transaction.defaultInstance
      try {
        txr = Transaction.parseFrom(tr1)
        doTransaction(txr,ip)
      } catch {
        case e: Exception =>
          sender ! PostResult(txr.id, Option(ErrMessage(TranParseError, s"transaction parser error! + ${e.getMessage}")))
      }
      val tmpend = System.currentTimeMillis()
      RepLogger.trace(RepLogger.OutputTime_Logger, this.getLogMsgPrefix(s"API recv trans time,thread-id=${Thread.currentThread().getName + "-" + Thread.currentThread().getId},spent time=${(tmpend - tmpstart)}" + "~" + selfAddr))

    //处理post CSpec构造交易的请求
    case cspec: CSpecTran =>
      var txr = Transaction.defaultInstance
      //debug状态才动用节点密钥签名
      if (contractOperationMode == 0) {
        //构建transaction并通过peer预执行广播
        txr = buildTranaction(pe.getRepChainContext.getConfig.getChainNetworkId+IdTool.DIDPrefixSeparator + pe.getSysTag, cspec.spec)
        doTransaction(txr,cspec.ip)
      } else {
        sender ! PostResult(txr.id, Option(ErrMessage(NotValidInDebug, "非Debug状态下此调用无效")))
      }

    // 流式提交交易
    case tran: streamTran => doTransaction(tran.tran,tran.ip)

    // 根据高度检索块
    case BlockHeight(height,ip) =>
      val bb = sr.getBlockByHeight(height)
      pe.getRepChainContext.getProblemAnalysis.AddMaliciousDownloadOfBlocks(ip)
      val r = bb match {
        case None => QueryResult(None)
        case _ =>
          val bl = df.filterBlock( bb.get)
          QueryResult(Option(MessageToJson.toJson(bl)))
      }
      sender ! r
    // 根据高度检索区块头
    case BlockHeaderHeight(height, ip) =>
      val bb = sr.getBlockByHeight(height)
      pe.getRepChainContext.getProblemAnalysis.AddMaliciousDownloadOfBlocks(ip)
      val r = bb match {
        case None => QueryResult(None)
        case _ =>
          val bl = df.filterBlock(bb.get)
          QueryResult(Option(MessageToJson.toJson(bl.getHeader)))
      }
      sender ! r


    case BlockTimeForHeight(height) =>
      val bb = sr.getBlockCreateTimeByHeight(height)
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
    case BlockHeightStream(height,ip) =>
      val bb = sr.getBlockByHeight(height)
      pe.getRepChainContext.getProblemAnalysis.AddMaliciousDownloadOfBlocks(ip)
      if (bb.isEmpty) {
        sender ! HttpResponse(entity = HttpEntity.Strict(MediaTypes.`application/octet-stream`, akka.util.ByteString.empty))
      } else {
        val block = df.filterBlock(bb.get)
        val body = akka.util.ByteString(block.toByteArray)
        val entity = HttpEntity.Strict(MediaTypes.`application/octet-stream`, body)
        val httpResponse = HttpResponse(entity = entity)
        sender ! httpResponse
      }

    //根据block hash检索
    case BlockId(id,ip) =>
      val bb = sr.getBlockByBase64Hash(id)
      pe.getRepChainContext.getProblemAnalysis.AddMaliciousDownloadOfBlocks(ip)
      val r = bb match {
        case None => QueryResult(None)
        case _ =>
          val blk = df.filterBlock(bb.get)
          QueryResult(Option(MessageToJson.toJson(blk)))
      }
      sender ! r

    //根据block hash检索区块头
    case BlockHeaderId(id, ip) =>
      val bb = sr.getBlockByBase64Hash(id)
      pe.getRepChainContext.getProblemAnalysis.AddMaliciousDownloadOfBlocks(ip)
      val r = bb match {
        case None => QueryResult(None)
        case _ =>
          val blk = df.filterBlock(bb.get)
          QueryResult(Option(MessageToJson.toJson(blk.getHeader)))
      }
      sender ! r

    // 根据txid检索交易
    case TransactionId(txId) =>
      val r = sr.getTransactionByTxId(txId) match {
        case None =>
          QueryResult(None)
        case t: Some[Transaction] =>
          QueryResult(Option(MessageToJson.toJson(df.filterTransaction(t.get))))
      }
      sender ! r

    case ProofOfTransaction(txId) =>
      val r = sr.getTransactionByTxId(txId) match {
        case None =>
          QueryResult(None)
        case t: Some[Transaction] =>
          val tg = t.get
          val tgb = tg.toByteArray
          val idx = verkle.util.verkleTool.getIndex(pe.getRepChainContext.getHashTool.hash(tgb))
          val prime = PrimeTool.hash2Prime(tgb, Accumulator.bitLength, pe.getRepChainContext.getHashTool)
          val root = pe.getRepChainContext.getVerkleTreeNodeBuffer(VerkleTreeType.TransactionTree).readMiddleNode(null)
          val proofs = root.getProofs(idx, tgb, prime)
          //val objectMapper = new ObjectMapper()
          //val json = objectMapper.writeValueAsString(proofs)
          //val hJson = new String(org.apache.commons.codec.binary.Hex.encodeHex(json.getBytes))
          val hJson = ProofSerialize.SerialProof(proofs)
          val rs = s"""{"proof": "${hJson}", "txid": "${txId}"}"""
          QueryResult(Option(JsonMethods.parse(rs)))
      }
      sender ! r

    case ProofOfOutput(proof,txId) =>
      /*val json = new String(org.apache.commons.codec.binary.Hex.decodeHex(proof))
      val objectMapper = new ObjectMapper()
      val proofs = objectMapper.readValue(json, classOf[Array[Any]])*/
      val proofs = ProofSerialize.DeserialProof(proof)
      val r = sr.getTransactionByTxId(txId) match {
        case None =>
          QueryResult(Option(JsonMethods.parse(s"""{"result": "验证失败,没有找到交易", "txid": "${txId}"}""")))
        case t: Some[Transaction] =>
          val tg = t.get
          val tgb = tg.toByteArray
          val idx = verkle.util.verkleTool.getIndex(pe.getRepChainContext.getHashTool.hash(tgb))
          val prime = PrimeTool.hash2Prime(tgb, Accumulator.bitLength, pe.getRepChainContext.getHashTool)
          val root = pe.getRepChainContext.getVerkleTreeNodeBuffer(VerkleTreeType.TransactionTree).readMiddleNode(null)
          val b = root.verifyProofs(prime, proofs)
          var rs = if(b){
            s"""{"result": "验证成功", "txid": "${txId}"}"""
          }else{
            s"""{"result": "验证失败", "txid": "${txId}"}"""
          }
          QueryResult(Option(JsonMethods.parse(rs)))
      }
      sender ! r

    // 根据txid检索交易字节流
    case TransactionStreamId(txId) =>
      val r = sr.getTransactionByTxId(txId)
      if (r.isEmpty) {
        sender ! HttpResponse(entity = HttpEntity.Strict(MediaTypes.`application/octet-stream`, akka.util.ByteString.empty))
      } else {
        val t = df.filterTransaction(r.get)
        val body = akka.util.ByteString(t.toByteArray)
        val entity = HttpEntity.Strict(MediaTypes.`application/octet-stream`, body)
        val httpResponse = HttpResponse(entity = entity)
        sender ! httpResponse
      }

    case TranInfoAndHeightId(txId) =>
      implicit val fomats = DefaultFormats
      val r = sr.getTransactionByTxId(txId) match {
        case None =>
          QueryResult(None)
        case t: Some[Transaction] =>
          val txr = df.filterTransaction(t.get)
          val tranInfoHeight = TranInfoHeight(MessageToJson.toJson(txr), sr.getBlockIndexByTxId(txr.id).get.getHeight)
          QueryResult(Option(Extraction.decompose(tranInfoHeight)))
      }
      sender ! r

    // 获取链信息
    case ChainInfo =>
      val cij = MessageToJson.toJson(sr.getChainInfo)
      sender ! QueryResult(Option(cij))

    // 获取节点信息
    case NodeInfo =>
      val netWorkId = pe.getRepChainContext.getConfig.getChainNetworkId
      val nodeName = pe.getSysTag
      val rs = s"""{"networkid": "$netWorkId", "nodename": "$nodeName"}"""
      sender ! QueryResult(Option(JsonMethods.parse(rs)))

    case NodeNumber =>
      val stablenode = pe.getRepChainContext.getNodeMgr.getStableNodes.size
      val snode = pe.getRepChainContext.getNodeMgr.getNodes.size
      val rs = s"""{"consensusnodes": "$stablenode", "nodes": "$snode"}"""
      sender ! QueryResult(Option(JsonMethods.parse(rs)))

    case TransNumber =>
      val num = pe.getRepChainContext.getTransactionPool.getCachePoolSize
      val rs = s"""{"numberofcache": "$num"}"""
      sender ! QueryResult(Option(JsonMethods.parse(rs)))

    case AcceptedTransNumber =>
      val num = sr.getChainInfo.totalTransactions + pe.getRepChainContext.getTransactionPool.getCachePoolSize
      val rs = s"""{"acceptedNumber": "$num"}"""
      sender ! QueryResult(Option(JsonMethods.parse(rs)))

    case TransNumberOfBlock(h) =>
      val num = sr.getNumberOfTransInBlockByHeight(h)
      val rs = s"""{"transnumberofblock": "$num"}"""
      sender ! QueryResult(Option(JsonMethods.parse(rs)))

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

    // resolve the did to get the did document
    case DidDocumentReq(did) =>
      import rep.sc.tpl.did.DidTplPrefix.{ signerPrefix, certPrefix }
      import org.joda.time.DateTime
      import org.joda.time.format.ISODateTimeFormat
      import rep.proto.rc2.Certificate
      import rep.proto.rc2.Certificate.CertType
      import rep.proto.rc2.Signer
      import akka.http.scaladsl.model.StatusCodes
      import org.json4s.jackson.Serialization.write

      implicit val fomats = DefaultFormats

      val didOrigin = did.replaceAll("\"", "")

      // check the did format
      if (!didOrigin.startsWith("did:rep:")) {
        sender ! HttpResponse(
          StatusCodes.BadRequest,
          entity = "Bad did format, which should start with \"did:rep:\""
        )
      }

      // To retrieve the signer for the did document
      // The did is like "did:rep:<network>:<oid>:<specific_user_id>" or "did:rep:<network>:<specific_user_id>"
      val info = didOrigin.split(":");
      var oid = "_";
      if (info.length == 5) {
        oid = info(3) match {
          case ""  => "_"
          case _ => info(3)
        }
      }
      val signerStateKey = s"""${info(2)}_${config.getAccountContractName}_${oid}_${signerPrefix}${info(2)}:${info(4)}"""
      val signerBytes = DBFactory.getDBAccess(config).getBytes(signerStateKey)
      if (signerBytes == null) {
          sender ! HttpResponse(
            StatusCodes.NotFound,
            entity = s"""Not fount the did document for the did: "$didOrigin""""
          )
      }
      val signer = SerializeUtils.deserialise(signerBytes).asInstanceOf[Signer]

      // to get the created time string (ISO8601 UTC format) for the did document
      val createdTime = new DateTime(signer.createTime.get.seconds * 1000 +
        signer.createTime.get.nanos / 1000000)
      val createdTimeStr = ISODateTimeFormat.dateTime()
        .withZoneUTC().print(createdTime)

      //  to get the verificationMethod, authentication,
      //  assertMethod and capabilityInvocation for the did document
      var verficationMethod: Seq[DidPubKey] = Seq()
      var authentication: Seq[Any] = Seq()
      var assertionMethod: Seq[Any] = Seq()
      var capabilityInvocation: Seq[Any] = Seq()
      signer.certNames.foreach( certName => {
        // to retrieve a certificate for the signer
        val certStateKey = s"""${info(2)}_${config.getAccountContractName}_${oid}_${certPrefix}${certName}"""
        val cert = SerializeUtils.deserialise(
          DBFactory.getDBAccess(config).getBytes(certStateKey)
        ).asInstanceOf[Certificate]

        val pubKeyId = didOrigin + DELIMITER + cert.id.get.certName

        verficationMethod = verficationMethod :+
          constructDidPubKey(cert.certificate, pubKeyId, didOrigin)

        cert.certType match {
          case CertType.CERT_AUTHENTICATION => {
            authentication = authentication :+ pubKeyId
            assertionMethod = assertionMethod :+ pubKeyId
          }
          case CertType.CERT_CUSTOM => assertionMethod = assertionMethod :+ pubKeyId
        }
      } )

      val didDocument = DidDocument(
        id = didOrigin,
        controller = Seq(didOrigin),
        created = createdTimeStr,
        // TODO: use the real updated time
        updated = createdTimeStr,
        verificationMethod = verficationMethod,
        authentication = authentication,
        assertionMethod = assertionMethod,
        // TODO: use the real capabilityInvocation info
        capabilityInvocation = capabilityInvocation,
        // TODO: use the real service info
        service = Seq()
      )
      sender ! HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity.Strict(
          MediaTypes.`application/json`,
          ByteString(write(didDocument))
        )
      )

    // resolve the didPubKeyId to get the did PubKey
    case DidPubKeyReq(didPubKeyId) =>
      import rep.sc.tpl.did.DidTplPrefix.{ signerPrefix, certPrefix }
      import rep.proto.rc2.Certificate
      import akka.http.scaladsl.model.StatusCodes
      import org.json4s.jackson.Serialization.write

      implicit val fomats = DefaultFormats

      val pubKeyId = didPubKeyId.replaceAll("\"", "")

      // check the did pubKeyId format
      if (!pubKeyId.startsWith("did:rep:") ||
        !pubKeyId.contains("#")
      ) {
        sender ! HttpResponse(
          StatusCodes.BadRequest,
          entity = "Bad did PubKeyId format, which should be like: \"did:rep:<str1>#<str2>\""
        )
      }

      // to retrieve the certificate for the did pubKeyId
      // The pubKeyId is like "did:rep:<network>:<oid>:<specific_user_id>#<cert_name>"
      // or "did:rep:<network>:<specific_user_id>#<cert_name>"
      val info = pubKeyId.split(":");
      var oid = "_";
      if (info.length == 5) {
        oid = info(3) match {
          case "" => "_"
          case _ => info(3)
        }
      }
      val certName = s"""${info(2)}:${info.last.replace(DELIMITER, ".")}"""
      val certStateKey = s"""${info(2)}_${config.getAccountContractName}_${oid}_${certPrefix}${certName}"""
      val certBytes = DBFactory.getDBAccess(config).getBytes(certStateKey)
      if (certBytes == null) {
          sender ! HttpResponse(
            StatusCodes.NotFound,
            entity = s"""Not fount the did PubKey for the did pubKeyId: "$pubKeyId""""
          )
      }
      val cert = SerializeUtils.deserialise(certBytes).asInstanceOf[Certificate]

      val didPubKey = constructDidPubKey(cert.certificate, pubKeyId, pubKeyId.split(DELIMITER)(0))
      sender ! HttpResponse(
        StatusCodes.OK,
        entity = HttpEntity.Strict(
          MediaTypes.`application/json`,
          ByteString(write(didPubKey))
        )
      )
  }

  private def constructDidPubKey(
                               certPemStr: String,
                               pubKeyId: String,
                               did: String
                             ): DidPubKey = {
    import org.bouncycastle.util.io.pem.{ PemReader, PemWriter, PemObject }
    import java.io.{ ByteArrayInputStream, StringReader, StringWriter }
    import org.bouncycastle.asn1.x509
    import java.security.cert.CertificateFactory

    // to get the public key type
    val pemReader = new PemReader(new StringReader(certPemStr))
    val certBytes = pemReader.readPemObject().getContent
    val pubKeyInfo = x509.Certificate.getInstance(certBytes).getSubjectPublicKeyInfo
    val oid2MyDidPubKeyTypeName = Map(
      "1.2.840.10045.2.1" -> "Ecdsa",
      "1.2.840.10045.3.1.7" -> "Prime256v1",
      "1.3.132.0.10" -> "Secp256k1",
      "1.2.840.113549.1.1.1" -> "Rsa"
    )
    var pubKeyType = oid2MyDidPubKeyTypeName.getOrElse(
      pubKeyInfo.getAlgorithm.getAlgorithm.toASN1Primitive.toString,
      "Unknown"
    )
    pubKeyType += oid2MyDidPubKeyTypeName.getOrElse(
      pubKeyInfo.getAlgorithm.getParameters.toASN1Primitive.toString,
      ""
    )
    pubKeyType += "VerificationKey"
    pubKeyType = pubKeyType match {
      case "EcdsaSecp256k1VerificationKey" => pubKeyType + "2019"
      case "RsaVerificationKey" => pubKeyType + "2018"
      case _ => pubKeyType
    }

    // to get the public key pem format string
    val pubKeyPemStrWriter = new StringWriter()
    val pubKeyPemWriter = new PemWriter(pubKeyPemStrWriter)
    val cf = CertificateFactory.getInstance("X.509")
    val certificate = cf.generateCertificate(new ByteArrayInputStream(certBytes))
    pubKeyPemWriter.writeObject(new PemObject("PUBLIC KEY", certificate.getPublicKey.getEncoded))
    pubKeyPemWriter.flush()
    val pubKeyPem = pubKeyPemStrWriter.toString
    //        val pemReader2 = new PemReader(new StringReader(pubKeyPem))
    //        val pubKeyBytes = pemReader2.readPemObject().getContent
    //        val hh = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(pubKeyBytes)).getAlgorithm

    DidPubKey(pubKeyId, pubKeyType, did, pubKeyPem)
  }
}