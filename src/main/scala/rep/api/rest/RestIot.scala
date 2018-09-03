package rep.api.rest

import akka.actor.{Actor, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.google.protobuf.ByteString
import org.json4s.JValue
import rep.api.rest.RestIot.tranResult
import rep.crypto.ECDSASign
import rep.network.PeerHelper
import rep.network.base.ModuleHelper
import rep.protos.peer.{ChaincodeID, Transaction}
import rep.sc.Sandbox.DoTransactionResult
import rep.sc.Shim.Oper
import rep.sc.TransProcessor
import rep.sc.TransProcessor.PreTransaction
import rep.storage.IdxPrefix.WorldStateKeyPreFix
import rep.utils.GlobalUtils.ActorType
import rep.utils.RepLogging
import rep.sc.Shim._

import scala.concurrent.Await
import scala.concurrent.duration._

object RestIot {
  def props(name: String): Props = Props(classOf[RestIot], name)

  case class tranSign(tran: Array[Byte])
  case class tranResult(txid: String, result: Option[JValue], ol: List[Oper], err: Option[String])

}

/**
  * @author zyf
  */
class RestIot extends Actor with ModuleHelper with RepLogging{

  val sandbox = context.actorOf(TransProcessor.props("sandbox", "", self), "sandboxPost")
  implicit val timeout = Timeout(1000.seconds)

  def preTransaction(t:Transaction) : Any ={
    val future = sandbox ? PreTransaction(t)
    val result = Await.result(future, timeout.duration).asInstanceOf[DoTransactionResult]
    result.err match {
      case None =>
        //预执行正常,提交并广播交易
        val tOutMetadata = t.withMetadata(ByteString.EMPTY)
        getActorRef(ActorType.TRANSACTION_POOL) ! tOutMetadata // 给交易池发送消息 ！=》告知（getActorRef）
        tranResult(t.txid, Option(result.r.asInstanceOf[JValue]), result.ol, None) // 返回预执行结果
      case Some(e) => tranResult(t.txid, None, null, Option(e.cause.getMessage))
    }
  }

  def receive = {

    /**
      * 这个目前用来接受带签名的交易
      */
    case trs : RestIot.tranSign => {
      if (trs.tran != null) {
        var txr: Transaction = null
        try {
          txr = Transaction.parseFrom(trs.tran)
        } catch {
          case e:Exception =>
            print(e.getCause)
        }
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
                val result = preTransaction(txr)
                print(result)
                sender ! result
              case false => throw new RuntimeException("验证签名出错")
            }
          }else{
            throw new RuntimeException("没有证书")
          }
        }catch{
          case e : RuntimeException =>
            sender ! tranResult(txr.txid, None, null, Option(e.getMessage))
        }
      }
    }
  }
}
