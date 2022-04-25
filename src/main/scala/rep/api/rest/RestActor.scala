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
import akka.pattern.{AskTimeoutException, ask}
import scala.concurrent._
import rep.crypto._
import rep.app.TestMain
import org.json4s._
import org.json4s.jackson.JsonMethods
import rep.network.module.ModuleActorType
import akka.actor.Props
import rep.log.RepLogger
import rep.network.base.ModuleBase
import rep.network.consensus.byzantium.ConsensusCondition
import rep.network.consensus.common.MsgOfConsensus.{PreTransBlock, PreTransBlockResult}
import rep.network.consensus.util.BlockHelp
import rep.proto.rc2.{ActionResult, Block, ChaincodeId, Event, Transaction, TransactionResult}
import rep.sc.TypeOfSender
import rep.sc.SandboxDispatcher.DoTransaction
import rep.sc.Sandbox.DoTransactionResult
import rep.storage.chain.block.BlockSearcher
import rep.utils.GlobalUtils.EventType
import scala.collection.mutable.ArrayBuffer
import scala.util.Random
import rep.utils.{MessageToJson, SerializeUtils}
/**
 * RestActor伴生object，包含可接受的传入消息定义，以及处理的返回结果定义。
 * 以及用于建立Tranaction，检索Tranaction的静态方法
 * @author c4w created
 *
 */

object RestActor {
  def props(name: String): Props = Props(classOf[RestActor], name)


  case object ChainInfo
  case object NodeNumber
  case object TransNumber
  case object AcceptedTransNumber

  case class SystemStart(cout: Int)
  case class SystemStop(from: Int, to: Int)

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
  case object LoadBlockInfo
  case object IsLoadBlockInfo

  case class PostResult(txid: String, result: Option[ActionResult], err: Option[String])
  case class QueryResult(result: Option[JValue])

  case class resultMsg(result: String)

  case class CSpec(methodType: Int, chainCodeName: String, chainCodeVersion: Int,
                   iptFunc: String, iptArgs: Seq[String], timeout: Int, legal_prose: String,
                   code: String, codeType: Int, state: Boolean,gasLimited:Int,oid:String,runType:Int,stateType:Int,contractLevel:Int)
  case class tranSign(tran: String)



}

/**
 * RestActor负责处理rest api请求
 *
 */
class RestActor(moduleName: String) extends ModuleBase(moduleName) {

  import RestActor._
  import spray.json._
  import akka.http.scaladsl.model.{ HttpResponse, MediaTypes, HttpEntity }
  import rep.network.autotransaction.Topic
  import akka.cluster.pubsub.DistributedPubSubMediator.Publish
  //import rep.utils.JsonFormat.AnyJsonFormat
  val config = pe.getRepChainContext.getConfig
  val contractOperationMode = config.getContractRunMode

  implicit val timeout = Timeout(1000.seconds)
  val sr: BlockSearcher = new BlockSearcher(pe.getRepChainContext)
  private val consensusCondition = new ConsensusCondition(pe.getRepChainContext.getConfig)

  /**
   * 根据节点名称和chainCode定义建立交易实例
   * @param nodeName 节点名称
   * @param c chainCode定义
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
    val run_type = c.runType match{
      case 1 =>
        rep.proto.rc2.ChaincodeDeploy.RunType.RUN_SERIAL
      case 2 =>
        rep.proto.rc2.ChaincodeDeploy.RunType.RUN_PARALLEL
      case 3 =>
        rep.proto.rc2.ChaincodeDeploy.RunType.RUN_OPTIONAL
      case _ =>
        rep.proto.rc2.ChaincodeDeploy.RunType.RUN_SERIAL
    }
    val state_type = c.stateType match{
      case 1 =>
        rep.proto.rc2.ChaincodeDeploy.StateType.STATE_BLOCK
      case 2 =>
        rep.proto.rc2.ChaincodeDeploy.StateType.STATE_GLOBAL
      case _ =>
        rep.proto.rc2.ChaincodeDeploy.StateType.STATE_BLOCK
    }
    val contract_Level = c.contractLevel match{
      case 1 =>
        rep.proto.rc2.ChaincodeDeploy.ContractClassification.CONTRACT_SYSTEM
      case 2 =>
        rep.proto.rc2.ChaincodeDeploy.ContractClassification.CONTRACT_CUSTOM
      case _ =>
        rep.proto.rc2.ChaincodeDeploy.ContractClassification.CONTRACT_CUSTOM
    }
    val chaincodeId = new ChaincodeId(c.chainCodeName, c.chainCodeVersion)
    val gas_limited = if(c.gasLimited < 0) 0 else c.gasLimited
    val oid = if(c.oid == null || c.oid.equalsIgnoreCase("null"))"" else c.oid
    if (method_type == Transaction.Type.CHAINCODE_DEPLOY) {
      pe.getRepChainContext.getTransactionBuilder.createTransaction4Deploy(nodeName, chaincodeId, c.code,
        c.legal_prose, c.timeout, code_type,run_type,
        state_type,contract_Level,gas_limited)
    } else if (method_type == Transaction.Type.CHAINCODE_INVOKE) {
      pe.getRepChainContext.getTransactionBuilder.createTransaction4Invoke(nodeName, chaincodeId, c.iptFunc, c.iptArgs,gas_limited,oid)
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
    } else if (!this.consensusCondition.CheckWorkConditionOfSystem(pe.getNodeMgr.getStableNodes.size)) {
      sender ! PostResult(t.id, None, Option("共识节点数目太少，暂时无法处理交易"))
    } else {
      try {
        //if (pe.getTransPoolMgr.getTransLength() < SystemProfile.getMaxCacheTransNum) {

        //pe.getTransPoolMgr.putTran(t,pe.getSysTag)

        if(config.isBroadcastTransaction== 1) {
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

    case SystemStart(cout) =>
      val rs = TestMain.startSystem(cout)
      val r = rs match {
        case null => QueryResult(None)
        case _ =>
          QueryResult(Option(JsonMethods.parse(string2JsonInput(rs))))
      }
      sender ! r

    case SystemStop(from, to) =>
      val rs = TestMain.stopSystem(from, to)
      val r = rs match {
        case null => QueryResult(None)
        case _ =>
          QueryResult(Option(JsonMethods.parse(string2JsonInput(rs))))
      }
      sender ! r

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
        case  None => QueryResult(None)
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
      val stablenode = pe.getNodeMgr.getStableNodes.size
      val snode = pe.getNodeMgr.getNodes.size
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

    case LoadBlockInfo =>
      sr.loadBlockInfoToCache
      val rs = "{\"startup\":\"" + "true" + "\"}"
      sender ! QueryResult(Option(JsonMethods.parse(string2JsonInput(rs))))
    case IsLoadBlockInfo =>
      val num = sr.isFinish
      val rs = "{\"isfinish\":\"" + num + "\"}"
      sender ! QueryResult(Option(JsonMethods.parse(string2JsonInput(rs))))
  }

  //test contract speed
  private def  testContractSpeed(h:Long):Long={
    val si2 = scala.io.Source.fromFile("api_req/json/transfer_" + pe.getSysTag + ".json","UTF-8")
    val li2 = try si2.mkString finally si2.close()
    val chaincode:ChaincodeId = new ChaincodeId("ContractAssetsTPL",1)
    var txs = new ArrayBuffer[Transaction]()
    var start = System.currentTimeMillis()
    val len = h.toInt
    for( i <- 1 to len){
      val t3 = pe.getRepChainContext.getTransactionBuilder.createTransaction4Invoke(pe.getSysTag, chaincode,"transfer", Seq(li2))
      txs += t3
    }
    var end = System.currentTimeMillis()
    println(s"create transaction,trans number=${len},spent time =${(end - start)}ms")

    val dbtag = "test_db_spend"
    var txresults = new ArrayBuffer[Option[TransactionResult]]()
    start = System.currentTimeMillis()

    val tr = ExecuteTransaction(txs,dbtag)//+"_"+h+"_"+Random.nextInt(10000))
    if(tr._2.length > 0){
      tr._2.foreach(trs=>{
        //todo in struct change 2022-04-07
        //var ts = TransactionResult(trs.txId, trs.ol.toSeq, trs.Option(trs.r))
        //txresults += Some(ts)
      })
    }

    /*txs.foreach(t=>{
      val tr = ExecuteTransaction(t,dbtag)//+"_"+h+"_"+Random.nextInt(10000))

      var ts = TransactionResult(t.id, tr._2.ol.toSeq, Option(tr._2.r))
      txresults += Some(ts)
    })*/
    end = System.currentTimeMillis()
    println(s"execute transaction,trans number=${len},spent time =${(end - start)}ms")
    end - start
  }

  private def ExecuteTransaction(ts: Seq[Transaction], db_identifier: String): (Int, Seq[DoTransactionResult]) = {
    try {
      val future1 = pe.getActorRef(ModuleActorType.ActorType.transactiondispatcher) ? new DoTransaction(ts,  db_identifier,TypeOfSender.FromPreloader)
      val result = Await.result(future1, timeout.duration).asInstanceOf[Seq[DoTransactionResult]]
      (0, result)
    } catch {
      case e: AskTimeoutException => (1, null)
      case te:TimeoutException =>
        (1, null)
    }
  }


  private var testHash=""
  private var testheight : Long= 0

  protected def ExecuteTransactionOfBlock(block: Block): Block = {
    try {
      val future = pe.getActorRef(ModuleActorType.ActorType.dispatchofpreload) ? PreTransBlock(block, "preload")
      val result = Await.result(future, timeout.duration).asInstanceOf[PreTransBlockResult]
      if (result.result) {
        result.blc
      } else {
        null
      }
    } catch {
      case e: AskTimeoutException => null
    }
  }

  private def dyncCreateTrans(h:Long):Long={
    val nodename = Array("121000005l35120456.node1", "12110107bi45jh675g.node2",
    "122000002n00123567.node3", "921000005k36123789.node4", "921000006e0012v696.node5")
    val txinfo = new Array[String](5)
    pe.getRepChainContext.getSignTool.loadNodeCertList("changeme", s"${pe.getRepChainContext.getCryptoMgr.getKeyFileSuffix.substring(1)}/mytruststore${pe.getRepChainContext.getCryptoMgr.getKeyFileSuffix}")
    for( i <- 1 to 5){
      pe.getRepChainContext.getSignTool.loadPrivateKey(nodename(i-1), "123", s"${pe.getRepChainContext.getCryptoMgr.getKeyFileSuffix.substring(1)}/"+nodename(i-1)+"${CryptoMgr.getKeyFileSuffix}")
      val si2 = scala.io.Source.fromFile("api_req/json/transfer_" + nodename(i-1) + ".json","UTF-8")
      txinfo(i-1) = try si2.mkString finally si2.close()
    }

    if(this.testHash == ""){
      //val sr: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
      val cinfo = sr.getChainInfo
      this.testHash = cinfo.currentBlockHash.toStringUtf8
      this.testheight = cinfo.height
      pe.resetSystemCurrentChainStatus(cinfo)
    }

    var chaincode:ChaincodeId = new ChaincodeId("ContractAssetsTPL",1)
    val len = h.toInt
    var txs = new ArrayBuffer[Transaction]()
    var start = System.currentTimeMillis()
    for( i <- 1 to len){
      val j = Random.nextInt(1000) % 5
      val t3 = pe.getRepChainContext.getTransactionBuilder.createTransaction4Invoke(nodename(j), chaincode,"transfer", Seq(txinfo(j)))
      txs += t3
    }
    var end = System.currentTimeMillis()
    println(s"create transaction,trans number=${len},spent time =${(end - start)}ms")


    val dbtag = "test_db_spend"
    var txresults = new ArrayBuffer[Option[TransactionResult]]()
    start = System.currentTimeMillis()

    var blc = BlockHelp.buildBlock(this.testHash, this.testheight + 1, txs.toSeq)
    blc = ExecuteTransactionOfBlock(blc)
    //this.testHash = blc.hashOfBlock.toStringUtf8

    /*val tr = ExecuteTransaction(txs,dbtag+"_"+h+"_"+Random.nextInt(10000))
    //val tr = ExecuteTransaction(txs,dbtag)//+"_"+h+"_"+Random.nextInt(10000))
    if(tr._2.length > 0){
      tr._2.foreach(trs=>{
        var ts = TransactionResult(trs.txId, trs.ol.toSeq, Option(trs.r))
        txresults += Some(ts)
      })
    }*/

    /*txs.foreach(t=>{
      val tr = ExecuteTransaction(t,dbtag)//+"_"+h+"_"+Random.nextInt(10000))
      var ts = TransactionResult(t.id, tr._2.ol.toSeq, Option(tr._2.r))
      txresults += Some(ts)
    })*/
    end = System.currentTimeMillis()
    println(s"execute transaction,trans number=${len},spent time =${(end - start)}ms")
    end - start
  }

  private def checkTransRepeat(h:Long):Long={
    val nodename = Array("121000005l35120456.node1", "12110107bi45jh675g.node2",
      "122000002n00123567.node3", "921000005k36123789.node4", "921000006e0012v696.node5")
    val txinfo = new Array[String](5)
    pe.getRepChainContext.getSignTool.loadNodeCertList("changeme", s"${pe.getRepChainContext.getCryptoMgr.getKeyFileSuffix.substring(1)}/mytruststore${pe.getRepChainContext.getCryptoMgr.getKeyFileSuffix}")
    for( i <- 1 to 5){
      pe.getRepChainContext.getSignTool.loadPrivateKey(nodename(i-1), "123", s"${pe.getRepChainContext.getCryptoMgr.getKeyFileSuffix.substring(1)}/"+nodename(i-1)+"${CryptoMgr.getKeyFileSuffix}")
      val si2 = scala.io.Source.fromFile("api_req/json/transfer_" + nodename(i-1) + ".json","UTF-8")
      txinfo(i-1) = try si2.mkString finally si2.close()
    }

    var chaincode:ChaincodeId = new ChaincodeId("ContractAssetsTPL",1)
    val len = h.toInt
    var txs = new ArrayBuffer[Transaction]()
    var start = System.currentTimeMillis()
    for( i <- 1 to len){
      val j = Random.nextInt(1000) % 5
      val t3 = pe.getRepChainContext.getTransactionBuilder.createTransaction4Invoke(nodename(j), chaincode,"transfer", Seq(txinfo(j)))
      txs += t3
    }
    var end = System.currentTimeMillis()
    println(s"create transaction,trans number=${len},spent time =${(end - start)}ms")

    val dbtag = "test_db_spend"
    var txresults = new ArrayBuffer[Boolean]()
    start = System.currentTimeMillis()

    //val sr: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
    txs.foreach(t=>{
      val rc = sr.isExistTransactionByTxId(t.id)
      txresults += rc
    })
    end = System.currentTimeMillis()
    println(s"execute transaction,trans number=${len},spent time =${(end - start)}ms")
    end - start
  }

}