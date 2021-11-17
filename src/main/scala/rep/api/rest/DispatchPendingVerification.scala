package rep.api.rest

import akka.actor.Props
import akka.routing.{ActorRefRoutee, Routee, Router, SmallestMailboxRoutingLogic}
import rep.log.RepLogger
import rep.network.base.ModuleBase
import rep.protos.peer.Transaction
import scala.collection.immutable.IndexedSeq

object DispatchPendingVerification{
  def props(name: String): Props = Props(classOf[DispatchPendingVerification],name)
}

class DispatchPendingVerification(moduleName: String) extends ModuleBase(moduleName) {
  private var router: Router = null

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "DispatchPendingVerification Start"))
  }

  private def createRouter = {
    if (router == null) {
      var len = 20
      var list: Array[Routee] = new Array[Routee](len)
      for (i <- 0 to len-1 ) {
        var ca = context.actorOf(ReceiveTransactionsOfPendingVerification.props("transactionverification" + i), "transactionverification" + i)
        context.watch(ca)
        list(i) = new ActorRefRoutee(ca)
      }
      val rlist: IndexedSeq[Routee] = list.toIndexedSeq
      router = Router(SmallestMailboxRoutingLogic(), rlist)
    }
  }



  override def receive = {
    case t:Transaction =>
      //System.out.println(s"outputer : ${pe.getSysTag},recv pending verify transaction ,from:${this.sender().toString()}")
      createRouter
      router.route(t , sender)
    case _ => //ignore
  }

}
