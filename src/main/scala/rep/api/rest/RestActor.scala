/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Fintech Research Center of ISCAS.
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
 */

package rep.api.rest

import akka.actor.{ Actor, ActorLogging }
import akka.util.Timeout
import rep.protos.peer._
import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import com.trueaccord.scalapb.json.JsonFormat
import rep.utils.{ GlobalUtils,  TimeUtils }
import rep.network._
import org.json4s._
import rep.network._

import scala.concurrent.duration._
import akka.pattern.{ ask, pipe }

import scala.concurrent._
import rep.sc.TransProcessor
import rep.sc.TransProcessor._
import rep.sc.Sandbox._
import rep.protos.peer._
import rep.protos.peer.Transaction._
import rep.protos.peer.ChaincodeID._
import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import rep.crypto._
import rep.sc.Shim._
import rep.network.PeerHelper._
import rep.storage._
import rep.storage.FakeStorage._
import spray.json._
import com.trueaccord.scalapb.json.JsonFormat
import rep.app.TestMain
import org.json4s._
import org.json4s.jackson.JsonMethods
import rep.network.base.ModuleHelper
import rep.network.tools.PeerExtension
import rep.network.base.ModuleBase
import rep.utils.GlobalUtils.ActorType
import akka.actor.Props
import akka.actor.Status._
import rep.network.tools.PeerExtension
import java.security.cert.Certificate
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import rep.sc.Sandbox.SandboxException
import rep.utils.SerializeUtils._
import rep.sc.Shim
import rep.utils.SerializeUtils
import IdxPrefix._
import java.util.Base64
import rep.log.trace.LogTraceOption
import rep.log.trace.RepTimeTrace


/**
 * RestActor伴生object，包含可接受的传入消息定义，以及处理的返回结果定义。
 * 以及用于建立Tranaction，检索Tranaction的静态方法
 * @author c4w created
 *
 */

object RestActor {
  def props(name: String): Props = Props(classOf[RestActor], name)
  case object ChainInfo
  
  case class SystemStart(cout: Int)
  case class SystemStop(from: Int, to: Int)

  case class BlockId(bid: String)
  case class BlockHeight(h: Int)
  case class BlockHeightStream(h: Int)
  case class TransactionId(txid: String)
  case class TransactionStreamId(txid: String)

  case class PostResult(txid: String, result: Option[JValue], ol: List[Oper], err: Option[String])
  case class PostCert(cert: String, cid: String)
  case class PostAddr(addr: String, cid: String)
  case class PostHash(hash: String, cid: String)
  case class QueryHash(result: String)
  case class QueryAddr(addr: String, err: String)
  case class QueryCert(addr: String, cert: String, cid: String, err: String)
  case class QueryResult(result: Option[JValue])
  case class QueryCertAddr(addr: String)
  case class CSpec(stype: Int, idPath: String, idName: Option[String], 
          iptFunc: String, iptArgs: Seq[String], timeout: Int,
          secureContext: String, code: String, ctype: Int)      
  case class tranSign(tran: String)
  
  case class closeOrOpen4Node(nodename:String,status:String)
  case class closeOrOpen4Package(packagename:String,status:String)
  case class ColseOrOpenAllLogger(status:String)
  case class ColseOrOpenTimeTrace(status:String)

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
      case _ =>
        Transaction.Type.UNDEFINED
    }
    val ctype = c.ctype match{
      case 1 =>
         rep.protos.peer.ChaincodeSpec.CodeType.CODE_SCALA
      case _ =>
        rep.protos.peer.ChaincodeSpec.CodeType.CODE_JAVASCRIPT
    }
    transactionCreator(nodeName,stype,c.idPath, c.iptFunc, c.iptArgs, c.code, c.idName, ctype)
  }  
  
  /** 根据存储实例和交易id检索并返回交易Transaction
   *  @param sr 存储实例
   *  @param txId 交易id
   *  @return 如果存在该交易，返回该交易；否则返回null
   *
   */
  def loadTransaction(sr: ImpDataAccess, txId: String): Option[Transaction] = {
    val bb = sr.getBlockByTxId(txId)
    bb match {
      case null => None
      case _ =>
        //交易所在的块
        val bl = Block.parseFrom(bb)
        //bl.transactions.foreach(f=>println(s"---${f.txid}"))
        bl.transactions.find(_.txid == txId)
    }
  }
}

/**
 * RestActor负责处理rest api请求
 *
 */
class RestActor extends Actor with ModuleHelper {

  import RestActor._
  import spray.json._
  import akka.http.scaladsl.model.{HttpResponse, MediaTypes,HttpEntity}

  //import rep.utils.JsonFormat.AnyJsonFormat

  implicit val timeout = Timeout(1000.seconds)
  //  val atp = getActorRef(ActorType.TRANSACTION_POOL)
  //  println(s"atp:${atp}")
  val sr: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
//  val sTag = PeerExtension(context.system).getSysTag
//  val preload :ImpDataPreload = ImpDataPreloadMgr.GetImpDataPreload(sTag,"preload")
  val sandbox = context.actorOf(TransProcessor.props("sandbox", "", self), "sandboxPost")
  val LINE_SEPARATOR = System.getProperty("line.separator");
  val cert_begin = "-----BEGIN CERTIFICATE-----";
  val end_cert = "-----END CERTIFICATE-----";
  
  def preTransaction(t:Transaction) : Unit ={
      val future = sandbox ? PreTransaction(t)
      val result = Await.result(future, timeout.duration).asInstanceOf[DoTransactionResult]
      val rv = result

      ImpDataPreloadMgr.Free(pe.getDBTag,t.txid)

      rv.err match {
        case None =>
          //预执行正常,提交并广播交易
          getActorRef(ActorType.TRANSACTION_POOL) ! t // 给交易池发送消息 ！=》告知（getActorRef）
          sender ! PostResult(t.txid, Option(rv.r.asInstanceOf[JValue]), rv.ol, None) // 发送消息给调用者（sender）
        case Some(e) =>
          //预执行异常,废弃交易，向api调用者发送异常
          sender ! e
      }
  }
  
  def receive: Receive = {
    // TODO test_zyf 处理post 带签名交易请求 测试用，添加API
    case tranSign(tr: String) =>
      val tr1 = BytesHex.hex2bytes(tr) // 解析交易编码后的16进制字符串,进行解码16进制反解码decode
      try {
         val txr = Transaction.parseFrom(tr1)
      } catch {
        case e:Exception =>
          println (e.getMessage)
          throw e
      }
      val txr = Transaction.parseFrom(tr1)
     //TODO 做交易的签名验证，是否能在信任列表或kv中找到证书，如果找到验证一下，如果没有找到，则返回错误
      val sig = txr.signature.toByteArray
      val tOutSig1 = txr.withSignature(ByteString.EMPTY)
      val tOutSig  = tOutSig1.withMetadata(ByteString.EMPTY)
      val cid = ChaincodeID.fromAscii(txr.chaincodeID.toStringUtf8).name
      val certKey = WorldStateKeyPreFix + cid + "_" + PRE_CERT + txr.cert.toStringUtf8
      try{
        var cert = ECDSASign.getCertWithCheck(txr.cert.toStringUtf8,certKey,pe.getSysTag)
        if(cert != None){
          ECDSASign.verify(sig, PeerHelper.getTxHash(tOutSig), cert.get.getPublicKey) match {
            case true => 
            case false => throw new RuntimeException("验证签名出错")
          }
        }else{
          throw new RuntimeException("没有证书")
        }
      }catch{
        case e : RuntimeException =>
          sender ! PostResult(txr.txid, None, null, Option(e.getMessage))
      }
      
      try {
        val future = sandbox ? PreTransaction(txr)
        val result = Await.result(future, timeout.duration).asInstanceOf[DoTransactionResult]
        val rv = result
        rv.err match {
          case None =>
            //预执行正常,提交并广播交易
            getActorRef(ActorType.TRANSACTION_POOL) ! txr
            sender ! PostResult(txr.txid, Option(rv.r.asInstanceOf[JValue]), rv.ol, None) // 发送消息给调用者（sender）
          case Some(e) =>
            //预执行异常,废弃交易，向api调用者发送异常
            //c4w 4.11
            //sender ! e
            sender ! PostResult(txr.txid,None, null, Option(e.cause.toString()))
        }
     } catch {
       case esig:java.security.SignatureException =>
         sender ! PostResult(txr.txid, None, null, Option(esig.getMessage+"：无效的签名"))
       case e:RuntimeException =>
//         val fail = Failure(new SandboxException("hahahahahahaha")) // 在evserver中有个拦截一样的，貌似只能接受SandboxException
//         sender ! fail
         sender ! PostResult(txr.txid, None, null, Option(e.getMessage))
       case _:Exception  =>
         sender ! PostResult(txr.txid, None, null, Option("未知错误"))
     }

    //处理post CSpec构造交易的请求
    case c: CSpec =>
      //构建transaction并通过peer广播
      println(pe.getSysTag) // test_zyf
      val t = buildTranaction(pe.getSysTag, c)
      preTransaction(t)

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
      
      
      
      case ColseOrOpenTimeTrace(status) =>
        var remsg = "";
        var  rtt = RepTimeTrace.getRepTimeTrace()
        if(status.equalsIgnoreCase("on")){
          rtt.openTimeTrace()
          remsg = "已经打开运行时间跟踪"
        }else if(status.equalsIgnoreCase("off")){
          rtt.closeTimeTrace()
          remsg = "已经关闭运行时间跟踪"
        }else{
          remsg = "状态只能输入on/off"
        }
        sender ! QueryHash(remsg)
      
      case ColseOrOpenAllLogger(status) =>
        var remsg = "";
        var  lto = LogTraceOption.getLogTraceOption()
        if(status.equalsIgnoreCase("on")){
          lto.OpenAll()
          remsg = "已经打开日志输出"
        }else if(status.equalsIgnoreCase("off")){
          lto.CloseAll()
          remsg = "已经关闭日志输出"
        }else{
          remsg = "状态只能输入on/off"
        }
        sender ! QueryHash(remsg)
        
        case closeOrOpen4Node(nodename,status) =>
        var remsg = "";
        var  lto = LogTraceOption.getLogTraceOption()
        if(status.equalsIgnoreCase("on")){
          lto.addNodeLogOption(nodename,true)
          remsg = "节点="+nodename+",已经打开日志输出"
        }else if(status.equalsIgnoreCase("off")){
          lto.addNodeLogOption(nodename,false)
          remsg = "节点="+nodename+",已经关闭日志输出"
        }else{
          remsg = "状态只能输入on/off"
        }
        sender ! QueryHash(remsg)
        
        case closeOrOpen4Package(packagename,status) =>
        var remsg = "";
        var  lto = LogTraceOption.getLogTraceOption()
        if(status.equalsIgnoreCase("on")){
          lto.addLogOption(packagename,true)
          remsg = "包名="+packagename+",已经打开日志输出"
        }else if(status.equalsIgnoreCase("off")){
          lto.addLogOption(packagename,false)
          remsg = "包名="+packagename+",已经关闭日志输出"
        }else{
          remsg = "状态只能输入on/off"
        }
        sender ! QueryHash(remsg)
        
        
    case BlockHeight(h) =>
      val bb = sr.getBlockByHeight(h)
      val r = bb match {
        case null => QueryResult(None)
        case _ =>
          val bl = Block.parseFrom(bb)
          QueryResult(Option(JsonFormat.toJson(bl)))
      }
      sender ! r
      
    case BlockHeightStream(h) =>
      val bb = sr.getBlockByHeight(h)
      val body = akka.util.ByteString(bb)
      val entity = HttpEntity.Strict(MediaTypes.`application/octet-stream`, body)        
      val httpResponse = HttpResponse(entity = entity)              
      sender ! httpResponse

    case TransactionId(txId) =>
      var r = loadTransaction(sr, txId) match {
        case None =>
          QueryResult(None)
        case t: Some[Transaction] =>
          QueryResult(Option(JsonFormat.toJson(t.get)))
      }
      sender ! r

    case TransactionStreamId(txId) =>
      val r = loadTransaction(sr, txId)
      val t = r.get
      val t1  = t.withMetadata(ByteString.EMPTY)
      val body = akka.util.ByteString(t1.toByteArray)
      val entity = HttpEntity.Strict(MediaTypes.`application/octet-stream`, body)        
      val httpResponse = HttpResponse(entity = entity)              
      sender ! httpResponse
    case ChainInfo =>
      val cij = JsonFormat.toJson(sr.getBlockChainInfo)
      sender ! QueryResult(Option(cij))

    case PostCert(pemcert,cid) =>
      val cf = CertificateFactory.getInstance("X.509");
      try {
        val cert = cf.generateCertificate(
          new ByteArrayInputStream(
            Base64.getDecoder.decode(pemcert)
          )
        )
        println(cert.toString())
        val certByte = SerializeUtils.serialise(cert)
        val certaddr = ECDSASign.getBitcoinAddrByCert(certByte)
        sender ! QueryAddr(certaddr, "")
      } catch {
        case e:Exception =>
          sender ! QueryAddr("", "证书字符串错误")
      }



    case pa: PostAddr =>
      //TODO 从短地址到证书得有信任列表里的，还有就是ws中存储的，两个都得做，如果证书在，返回证书字符串
      try{
          var peercert : Option[Certificate]  = None
          try{
            peercert = ECDSASign.getCertByBitcoinAddr(pa.addr)    
          }catch{
            case el : Exception => 
          }
          val certKey = WorldStateKeyPreFix + pa.cid + "_" + PRE_CERT + pa.addr  
          val kvcer = Option(sr.Get(certKey))
          if(peercert != None) {
            val pemCertPre = new String(Base64.getEncoder.encode(peercert.get.getEncoded))
            val pemcertstr = cert_begin + LINE_SEPARATOR + pemCertPre + LINE_SEPARATOR + end_cert
            sender ! QueryCert(pa.addr, pemcertstr, pa.cid, "")
          } else if (kvcer != None){
              if (new String(kvcer.get) == "null") {
                throw new RuntimeException("该用户证书已注销")
              }  
              val kvcert = SerializeUtils.deserialise(kvcer.get).asInstanceOf[Certificate]
              val pemCertPre = new String(Base64.getEncoder.encode(kvcert.getEncoded))
              val pemcertstr = cert_begin + LINE_SEPARATOR + pemCertPre + LINE_SEPARATOR + end_cert
              sender ! QueryCert(pa.addr, pemcertstr, pa.cid, "")
          } else {
            sender ! QueryCert(pa.addr, "", pa.cid, "不存在证书")
          }
      }catch{
            case e : Exception => sender ! QueryCert(pa.addr, "", pa.cid, e.getMessage)
        }
      
    // TODO 主要是查询hash是否存在
    case ph: PostHash =>
      val pre_key = WorldStateKeyPreFix + ph.cid + "_"
      val res = deserialiseJson(sr.Get(pre_key + ph.hash))
      if(res != null) {
        sender ! QueryHash(ph.hash+"已存在")
      } else {
        sender ! QueryHash("当前"+ph.hash+"未上链")
      }
  }
}