package rep.network.cache

import akka.actor.Props
import akka.routing.{ActorRefRoutee, Routee, Router, SmallestMailboxRoutingLogic}
import rep.log.RepLogger
import rep.network.autotransaction.Topic
import rep.network.base.ModuleBase
import rep.proto.rc2.Transaction
import scala.collection.immutable.IndexedSeq


object TransactionOfCollectioner{
  def props(name: String): Props = Props(classOf[TransactionOfCollectioner], name)
}


class TransactionOfCollectioner  (moduleName: String) extends ModuleBase(moduleName){
  private var router: Router = null

  private val config = pe.getRepChainContext.getConfig
  override def preStart(): Unit = {
    //注册接收交易的广播
    if(config.getVoteNodeList.contains(pe.getSysTag)){
      //共识节点可以订阅交易的广播事件
      SubscribeTopic(mediator, self, selfAddr, Topic.Transaction, true)
    }
    createRouter
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("TransactionOfCollectioner module start"))
    super.preStart()
  }

  private def createRouter = {
    if (router == null) {
      var list: Array[Routee] = new Array[Routee](config.getVoteNodeList.length)
      for (i <- 0 to config.getVoteNodeList.length - 1) {
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
      if(router != null ) router.route(t,self) else RepLogger.info(RepLogger.System_Logger,  "交易检查Actor为null")
    case _ => //ignore
  }
}
