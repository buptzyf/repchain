package rep.api.rest

import java.util.concurrent.Executors

import akka.actor.{ActorRef, Props}
import akka.routing.{ActorRefRoutee, Routee, Router, SmallestMailboxRoutingLogic}
import rep.log.RepLogger
import rep.network.base.ModuleBase
import rep.network.consensus.endorse.Endorser4Future
import rep.protos.peer.Transaction

import scala.concurrent.ExecutionContext


object VerifySignDispatcher {
  def props(name: String): Props = Props(classOf[VerifySignDispatcher], name)
  //分派验签的消息
  case class RequestVerifySign(tran: Transaction,httpresactor:ActorRef)
  case class ResponseVerifySign(result:Boolean,msg:String)
}

class VerifySignDispatcher(moduleName: String) extends ModuleBase(moduleName)  {
  import scala.collection.immutable._



  private var router: Router = null

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.APIAccess_Logger, this.getLogMsgPrefix("VerifySignDispatcher Start"))
  }

  private def createRouter = {
    val num = 50
    if (router == null) {
      var list: Array[Routee] = new Array[Routee](num)
      for (i <- 0 to num - 1) {
        var ca = context.actorOf(VerifySignActor.props("verifysignactor_" + i), "verifysignactor_" + i)
        context.watch(ca)
        list(i) = new ActorRefRoutee(ca)
      }
      val rlist: IndexedSeq[Routee] = list.toIndexedSeq
      router = Router(SmallestMailboxRoutingLogic(), rlist)
    }
  }


  override def receive: Receive = {
    case rvs:VerifySignDispatcher.RequestVerifySign =>
      createRouter
      router.route(VerifySignDispatcher.RequestVerifySign(rvs.tran,rvs.httpresactor), sender)
    case _ => //ignore
  }
}
