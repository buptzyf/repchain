package rep.network.consensus.transaction

import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import akka.pattern.AskTimeoutException
import scala.concurrent._

import akka.actor.{ Actor, ActorRef, Props }
import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import rep.app.conf.{ SystemProfile, TimePolicy }
import rep.network.base.ModuleBase
import rep.network.consensus.block.Blocker.{ PreTransBlock, PreTransBlockResult}
import rep.network.tools.PeerExtension
import rep.network.Topic
import rep.protos.peer._
import rep.sc.TransProcessor.DoTransaction
import rep.sc.{ Sandbox, TransProcessor }
import rep.sc.Sandbox.DoTransactionResult
import rep.storage.{ ImpDataPreloadMgr }
import rep.utils.GlobalUtils.ActorType
import rep.utils._
import scala.collection.mutable
import akka.pattern.AskTimeoutException
import rep.crypto.Sha256
import rep.log.RepLogger
import akka.routing._;

object PreloadTransRouter {
  def props(name: String): Props = Props(classOf[PreloadTransRouter], name)
}

class PreloadTransRouter(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.collection.breakOut
  import scala.concurrent.duration._
  import scala.collection.immutable._

  private var router: Router = null

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "PreloadTransRouter Start"))
    createRouter
  }

  private def createRouter = {
    if (router == null) {
      var list: Array[Routee] = new Array[Routee](SystemProfile.getNumberOfTransProcessor)
      for (i <- 0 to SystemProfile.getVoteNodeList.size() - 1) {
        var ca = context.actorOf( TransProcessor.props("sandbox_for_Preload_of_router" + i), "sandbox_for_Preload_of_router" + i)
        context.watch(ca)
        list(i) = new ActorRefRoutee(ca)
      }
      val rlist: IndexedSeq[Routee] = list.toIndexedSeq
      router = Router(SmallestMailboxRoutingLogic(), rlist)
    }
  }
  
  override def receive = {
    case tr:DoTransaction =>
      router.route(tr, sender)
    case _ => //ignore
  }
}
