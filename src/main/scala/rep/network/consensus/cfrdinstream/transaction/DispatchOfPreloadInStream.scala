package rep.network.consensus.cfrdinstream.transaction

import akka.actor.Props
import akka.routing.{ActorRefRoutee, Routee, Router, SmallestMailboxRoutingLogic}
import rep.app.conf.SystemProfile
import rep.log.RepLogger
import rep.network.base.ModuleBase
import rep.network.consensus.common.MsgOfConsensus.{PreTransBlock, PreTransBlockOfCache}

object DispatchOfPreloadInStream{
  def props(name: String): Props = Props(classOf[DispatchOfPreloadInStream], name)
}

class DispatchOfPreloadInStream(moduleName: String) extends ModuleBase(moduleName) {
  import scala.collection.immutable._

  private var router: Router = null

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "DispatchOfPreloadInStream Start"))
  }

  private def createRouter = {
    if (router == null) {
      var len = SystemProfile.getVoteNodeList.size()
      if(len <= 0){
        len  = 1
      }
      var list: Array[Routee] = new Array[Routee](len)
      for (i <- 0 to len-1 ) {
        var ca = context.actorOf(PreloaderForTransactionInStream.props("preloaderoftransactioninstream" + i), "preloaderoftransactioninstream" + i)
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
    case PreTransBlockOfCache(blockIdentifierInCache, prefixOfDbTag)=>
      createRouter
      router.route(PreTransBlockOfCache(blockIdentifierInCache, prefixOfDbTag) , sender)
    case _ => //ignore
  }
}