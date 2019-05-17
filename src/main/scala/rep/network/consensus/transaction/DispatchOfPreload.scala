package rep.network.consensus.transaction

import akka.actor.{ Actor, ActorRef, Props, Address, ActorSelection }
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.routing._;
import rep.app.conf.{ SystemProfile, TimePolicy }
import rep.network.base.ModuleBase
import rep.network.tools.PeerExtension
import rep.network.Topic
import rep.protos.peer._
import rep.utils.GlobalUtils.{ EventType }
import rep.utils._
import scala.collection.mutable._
import rep.network.consensus.util.BlockVerify
import scala.util.control.Breaks
import rep.network.util.NodeHelp
import rep.network.consensus.util.BlockHelp
import rep.network.consensus.util.BlockVerify
import rep.log.RepLogger
import rep.log.RepTimeTracer
import rep.network.consensus.block.Blocker.{ PreTransBlock}


object DispatchOfPreload  {
  def props(name: String): Props = Props(classOf[DispatchOfPreload], name)
}


class DispatchOfPreload(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.concurrent.duration._
  import scala.collection.immutable._

  private var router: Router = null

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "DispatchOfPreload Start"))
  }

  private def createRouter = {
    if (router == null) {
      var list: Array[Routee] = new Array[Routee](SystemProfile.getVoteNodeList.size())
      for (i <- 0 to SystemProfile.getVoteNodeList.size() - 1) {
        var ca = context.actorOf(PreloaderForTransaction.props("preloaderoftransaction" + i), "preloaderoftransaction" + i)
        context.watch(ca)
        list(i) = new ActorRefRoutee(ca)
      }
      val rlist: IndexedSeq[Routee] = list.toIndexedSeq
      router = Router(SmallestMailboxRoutingLogic(), rlist)
    }
  }

  

  override def receive = {
    case PreTransBlock(block,prefixOfDbTag) =>
      createRouter
      router.route(PreTransBlock(block,prefixOfDbTag) , sender)  
    case _ => //ignore
  }
}