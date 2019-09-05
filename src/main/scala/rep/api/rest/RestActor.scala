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

import akka.actor.Actor

import akka.util.Timeout
import rep.network._

import scala.concurrent.duration._
import akka.pattern.ask

import scala.concurrent._
import rep.protos.peer._
import rep.crypto._
import rep.sc.Shim._
import rep.network.PeerHelper._
import rep.storage._
import spray.json._
import scalapb.json4s.JsonFormat
import rep.app.TestMain
import org.json4s._
import org.json4s.jackson.JsonMethods
import rep.network.tools.PeerExtension
import rep.network.base.ModuleBase
import rep.utils.GlobalUtils.ActorType
import akka.actor.Props
import rep.crypto.cert.SignTool
import rep.protos.peer.ActionResult
import rep.app.conf.SystemProfile
import rep.network.base.ModuleBase
import rep.sc.TypeOfSender
import rep.sc.SandboxDispatcher.DoTransaction
import rep.sc.Sandbox.DoTransactionResult
/**
  * RestActor伴生object，包含可接受的传入消息定义，以及处理的返回结果定义。
  * 以及用于建立Tranaction，检索Tranaction的静态方法
  * @author c4w created
  *
  */

object RestActor {
  def props(name: String): Props = Props(classOf[RestActor], name)

  val contractOperationMode = SystemProfile.getContractOperationMode
  case object ChainInfo

  case class SystemStart(cout: Int)
  case class SystemStop(from: Int, to: Int)

  case class BlockId(bid: String)
  case class BlockHeight(h: Int)
  case class BlockHeightStream(h: Int)
  case class TransactionId(txid: String)
  case class TransactionStreamId(txid: String)

  case class PostResult(txid: String, result: Option[ActionResult], err: Option[String])
  case class QueryResult(result: Option[JValue])

  case class resultMsg(result: String)

  /*case class CSpec(stype: Int, idPath: String, idName: Option[String],
          iptFunc: String, iptArgs: Seq[String], timeout: Int,
          secureContext: String, code: String, ctype: Int)    */
  case class CSpec(stype: Int, chaincodename: String, chaincodeversion: Int,
                   iptFunc: String, iptArgs: Seq[String], timeout: Int,legal_prose:String,
                   code: String, ctype: Int, state: Boolean)
  case class tranSign(tran: String)

  /**
    * 根据节点名称和chainCode定义建立交易实例
    * @param nodeName 节点名称
    * @param c chainCode定义
    */
  def buildTranaction(nodeName: String, c: CSpec): Transaction = {
    val stype = c.stype match {
      case 1 =>
        Transaction.Type.CHAINCODE_DEPLOY
      case 2 =>
        Transaction.Type.CHAINCODE_INVOKE
      case 3=>
        Transaction.Type.CHAINCODE_SET_STATE
      case _ =>
        Transaction.Type.UNDEFINED
    }
    val ctype = c.ctype match{
      case 2 =>
        rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA
      case 3 =>  
        rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA_PARALLEL
      case _ =>
        rep.protos.peer.ChaincodeDeploy.CodeType.CODE_JAVASCRIPT
    }

    val chaincodeId = new ChaincodeId(c.chaincodename,c.chaincodeversion)
    if(stype==Transaction.Type.CHAINCODE_DEPLOY){
      PeerHelper.createTransaction4Deploy(nodeName, chaincodeId, c.code, c.legal_prose, c.timeout, ctype)
    }else if(stype==Transaction.Type.CHAINCODE_INVOKE){
      PeerHelper.createTransaction4Invoke(nodeName, chaincodeId, c.iptFunc, c.iptArgs)
    }else if (stype == Transaction.Type.CHAINCODE_SET_STATE){
      PeerHelper.createTransaction4State(nodeName, chaincodeId, c.state)
    } else {
      null
    }
  }

}

/**
  * RestActor负责处理rest api请求
  *
  */
class RestActor(moduleName: String) extends  ModuleBase(moduleName) {

  import RestActor._
  import spray.json._
  import akka.http.scaladsl.model.{HttpResponse, MediaTypes,HttpEntity}

  //import rep.utils.JsonFormat.AnyJsonFormat

  implicit val timeout = Timeout(1000.seconds)
  val sr: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)

  // 先检查交易大小，然后再检查交易是否已存在，再去验证签名，如果没有问题，则广播
  def preTransaction(t:Transaction) : Unit ={
    val tranLimitSize = SystemProfile.getBlockLength/3
    if (t.toByteArray.length > tranLimitSize) {
      sender ! PostResult(t.id, None, Option(s"交易大小超出限制： ${tranLimitSize}，请重新检查"))
    }
    
    if (pe.getNodeMgr.getStableNodes.size < SystemProfile.getVoteNoteMin)  {
      sender ! PostResult(t.id, None, Option("系统共识节点数目太少，暂时无法处理交易"))
    }
    
    val sig = t.signature.get.signature.toByteArray
    val tOutSig = t.clearSignature
    val certId = t.signature.get.certId.get
    try{
      sr.getTransDataByTxId(t.id) match {
        case st: Some[Transaction] =>
          sender ! PostResult(t.id, None, Option(s"transactionId is exists, the transaction is \n ${JsonFormat.toJson(st.get)}"))
        case None =>
          if (SignTool.verify(sig, tOutSig.toByteArray, certId,pe.getSysTag)) {
              val future = pe.getActorRef(ActorType.transactiondispatcher) ? DoTransaction(t, "api_" + t.id,TypeOfSender.FromAPI)
              val result = Await.result(future, timeout.duration).asInstanceOf[DoTransactionResult]
              val rv = result
              // 释放存储实例
              ImpDataPreloadMgr.Free(pe.getSysTag,t.id)
              rv.err match {
                case None =>
                  //预执行正常,提交并广播交易
                  pe.getActorRef(ActorType.transactionpool) ! t // 给交易池发送消息 ！=》告知（getActorRef）
                  if (rv.r == null)
                    sender ! PostResult(t.id, None, None)
                  else
                    sender ! PostResult(t.id, Some(rv.r), None)  // legal_prose need
                case Some(err) =>
                  //预执行异常,废弃交易，向api调用者发送异常
                  sender ! PostResult(t.id, None, Option(err.cause.getMessage))
              }
          } else {
            sender ! PostResult(t.id, None, Option("验证签名出错"))
          }
      }
    }catch{
      case e : RuntimeException =>
        sender ! PostResult(t.id, None, Option(e.getMessage))
    }
  }

  def receive: Receive = {

    case tranSign(tr: String) =>
      val tr1 = BytesHex.hex2bytes(tr) // 解析交易编码后的16进制字符串,进行解码16进制反解码decode
      var txr = Transaction.defaultInstance
      try {
        txr = Transaction.parseFrom(tr1)
        preTransaction(txr)
      } catch {
        case e:Exception =>
          sender ! PostResult(txr.id, None, Option(s"transaction parser error! + ${e.getMessage}"))
      }

    //处理post CSpec构造交易的请求
    case c: CSpec =>
      var txr = Transaction.defaultInstance
      //debug状态才动用节点密钥签名
      if(contractOperationMode==0){
        //构建transaction并通过peer预执行广播
        txr = buildTranaction(pe.getSysTag, c)
        preTransaction(txr)
      }else
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
        case null => QueryResult(None)
        case _ =>
          val bl = Block.parseFrom(bb)
          QueryResult(Option(JsonFormat.toJson(bl)))
      }
      sender ! r

    // 根据高度检索块的子节流
    case BlockHeightStream(h) =>
      val bb = sr.getBlockByHeight(h)
      if (bb == null) {
        sender ! HttpResponse(entity = HttpEntity.Strict(MediaTypes.`application/octet-stream`,akka.util.ByteString.empty))
      } else {
        val body = akka.util.ByteString(bb)
        val entity = HttpEntity.Strict(MediaTypes.`application/octet-stream`, body)
        val httpResponse = HttpResponse(entity = entity)
        sender ! httpResponse
      }

    //根据block hash检索
    case BlockId(bid) =>
      val bb = sr.getBlockByBase64Hash(bid)
      val r = bb match {
        case null => QueryResult(None)
        case _ =>
          val bl = Block.parseFrom(bb)
          QueryResult(Option(JsonFormat.toJson(bl)))
      }
      sender ! r

    // 根据txid检索交易
    case TransactionId(txId) =>
      var r = sr.getTransDataByTxId( txId) match {
        case None =>
          QueryResult(None)
        case t: Some[Transaction] =>
          QueryResult(Option(JsonFormat.toJson(t.get)))
      }
      sender ! r


    // 根据txid检索交易字节流
    case TransactionStreamId(txId) =>
      val r = sr.getTransDataByTxId( txId)
      if (r.isEmpty) {
        sender ! HttpResponse(entity = HttpEntity.Strict(MediaTypes.`application/octet-stream`,akka.util.ByteString.empty))
      } else {
        val t = r.get
        val body = akka.util.ByteString(t.toByteArray)
        val entity = HttpEntity.Strict(MediaTypes.`application/octet-stream`, body)
        val httpResponse = HttpResponse(entity = entity)
        sender ! httpResponse
      }

    // 获取链信息
    case ChainInfo =>
      val cij = JsonFormat.toJson(sr.getBlockChainInfo)
      sender ! QueryResult(Option(cij))

  }
}