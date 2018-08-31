package rep.api.rest

import akka.actor.{Actor, Props}
import akka.pattern.ask
import akka.util.Timeout
import rep.network.base.ModuleHelper
import rep.protos.peer.Transaction
import rep.sc.Sandbox.DoTransactionResult
import rep.sc.TransProcessor
import rep.sc.TransProcessor.PreTransaction
import rep.utils.GlobalUtils.ActorType
import rep.utils.RepLogging

import scala.concurrent.Await
import scala.concurrent.duration._

object RestIot {
  def props(name: String): Props = Props(classOf[RestIot], name)
  case class tranSign(tran: Array[Byte])


}

/**
  * @author zyf
  */
class RestIot extends Actor with ModuleHelper with RepLogging{

  val sandbox = context.actorOf(TransProcessor.props("sandbox", "", self), "sandboxPost")
  implicit val timeout = Timeout(1000.seconds)

  def preTransaction(t:Transaction) : Unit ={
    val future = sandbox ? PreTransaction(t)
    val result = Await.result(future, timeout.duration).asInstanceOf[DoTransactionResult]
    result.err match {
      case None =>
        //预执行正常,提交并广播交易
        getActorRef(ActorType.TRANSACTION_POOL) ! t // 给交易池发送消息 ！=》告知（getActorRef）
      case Some(e) => println(e.cause)
    }
  }

  def receive = {

    /**
      * 这个目前用来接受带签名的交易
      */
    case trs : RestIot.tranSign => {
      if (trs.tran != null) {
        val txr = Transaction.parseFrom(trs.tran)
        preTransaction(txr)
        println(txr)
      }
    }
  }
}
