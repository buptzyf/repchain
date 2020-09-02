package rep.network.cache

import akka.actor.Props
import akka.routing.{ActorRefRoutee, Routee, Router, SmallestMailboxRoutingLogic}
import rep.app.conf.SystemProfile
import rep.log.RepLogger
import rep.network.autotransaction.Topic
import rep.network.base.ModuleBase
import rep.network.consensus.cfrd.endorse.Endorser4Future
import rep.protos.peer.{Event, Transaction}
import rep.utils.ActorUtils
import rep.utils.GlobalUtils.EventType

import scala.collection.immutable.IndexedSeq


object TransactionOfCollectioner{
  def props(name: String): Props = Props(classOf[TransactionOfCollectioner], name)
}


class TransactionOfCollectioner  (moduleName: String) extends ModuleBase(moduleName){
  private var router: Router = null

  override def preStart(): Unit = {
    //注册接收交易的广播
    SubscribeTopic(mediator, self, selfAddr, Topic.Transaction, true)
  }

  private def createRouter = {
    if (router == null) {
      var list: Array[Routee] = new Array[Routee](SystemProfile.getVoteNodeList.size())
      for (i <- 0 to SystemProfile.getVoteNodeList.size() - 1) {
        var ca = context.actorOf(TransactionChecker.props("transactionchecker" + i), "transactionchecker" + i)
        context.watch(ca)
        list(i) = new ActorRefRoutee(ca)
      }
      val rlist: IndexedSeq[Routee] = list.toIndexedSeq
      router = Router(SmallestMailboxRoutingLogic(), rlist)
    }
  }

  override def receive = {
    //处理接收的交易
    case t: Transaction =>
      createRouter
      router.route(t,self)
    case _ => //ignore
  }
}