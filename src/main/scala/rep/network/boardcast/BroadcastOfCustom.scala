package rep.network.boardcast

import akka.actor.{ActorContext, ActorRef, ActorSelection, Address}
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import rep.app.system.RepChainSystemContext
import rep.log.RepLogger
import rep.network.autotransaction.Topic
import rep.network.consensus.common.MsgOfConsensus.ConfirmedBlock
import rep.proto.rc2.{Block, Transaction}

class BroadcastOfCustom(ctx:RepChainSystemContext) {
  private val useCustomBroadcast = ctx.getConfig.useCustomBroadcast
  private val actorPathInTransaction = "/user/modulemanager/transactioncollectioner"
  private val actorPathInConfirmBlock = "/user/modulemanager/confirmerofblock"

  private def toAkkaUrl(addr: String, actorName: String): String = {
    addr + "/" + actorName;
  }

  private def getDestActorService(context:ActorContext,sn: Address,actorName:String):ActorSelection = {
    try {
      context.actorSelection(toAkkaUrl(sn.toString, actorName))
    } catch {
      case e: Exception =>
        RepLogger.error(RepLogger.System_Logger, s"BroadcastOfCustom transaction,dest=${sn.toString}")
        null
    }
  }

  private def BroadcastTransaction4Custom(context:ActorContext,nodes:Set[Address],t:Transaction):Unit={
    nodes.foreach(addr => {
      try {
        val dest = getDestActorService(context,addr,actorPathInTransaction)
        if(dest != null){
          dest ! t
        }else{
          RepLogger.error(RepLogger.System_Logger, s"BroadcastOfCustom transaction,dest is null," +
            s"dest=${addr.toString},t=${t.id}")
        }
      } catch {
        case e: Exception =>
          RepLogger.error(RepLogger.System_Logger, s"BroadcastOfCustom transaction," +
            s"dest is null,dest=${addr.toString},t=${t.id},msg=${e.getMessage}")
      }
    })
  }

  private def BroadcastConfirmBlock4Custom(context: ActorContext, nodes: Set[Address], cb: ConfirmedBlock): Unit = {
    nodes.foreach(addr => {
      try {
        val dest = getDestActorService(context, addr, actorPathInConfirmBlock)
        if (dest != null) {
          dest ! cb
        } else {
          RepLogger.error(RepLogger.System_Logger, s"BroadcastOfCustom confirm block,dest is null," +
            s"dest=${addr.toString},block height=${cb.blc.getHeader.height}")
        }
      } catch {
        case e: Exception =>
          RepLogger.error(RepLogger.System_Logger, s"BroadcastOfCustom confirm block," +
            s"dest is null,dest=${addr.toString},block height=${cb.blc.getHeader.height},msg=${e.getMessage}")
      }
    })
  }

  def BroadcastTransaction(context:ActorContext, mediator:ActorRef, t:Transaction):Unit={
    if(useCustomBroadcast){
      BroadcastTransaction4Custom(context,ctx.getNodeMgr.getStableNodes,t)
    }else{
      mediator ! Publish(Topic.Transaction, t)
    }
  }

  def BroadcastConfirmBlock(context: ActorContext, mediator: ActorRef, cb: ConfirmedBlock): Unit = {
    if (useCustomBroadcast) {
      BroadcastConfirmBlock4Custom(context, ctx.getNodeMgr.getStableNodes, cb)
    } else {
      mediator ! Publish(Topic.Block, cb)
    }
  }

}
