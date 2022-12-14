package rep.network.boardcast

import akka.actor.{ActorContext, ActorRef, ActorSelection, Address}
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import rep.app.system.RepChainSystemContext
import rep.log.RepLogger
import rep.network.autotransaction.Topic
import rep.network.consensus.common.MsgOfConsensus.ConfirmedBlock
import rep.proto.rc2.{Block, Transaction}

import scala.collection.mutable

class BroadcastOfCustom(ctx:RepChainSystemContext) {
  case class SubscribeInfo(topicName:String,path:String)
  private val hm : mutable.HashMap[String,String] = new mutable.HashMap[String,String]()
  init
  private def init:Unit={
    hm += Topic.Transaction -> "/user/modulemanager/transactioncollectioner"
    hm += Topic.Block -> "/user/modulemanager/confirmerofblock"

    /*val Transaction = "Transaction"
    val Block = "Block"
    val Event = "Event"
    val Endorsement = "Endorsement"
    val SyncOfTransaction = "SyncOfTransaction"
    val SyncOfBlock = "SyncOfBlock"
    val VoteTransform = "VoteTransform"
    val VoteSynchronized = "VoteSynchronized"
    val MessageWithZeroTransaction = "MessageWithZeroTransaction"*/
  }

  private val useCustomBroadcast = ctx.getConfig.useCustomBroadcast

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

  private def Send(context:ActorContext,nodes:Set[Address],path:String,data:Any):Unit={
    nodes.foreach(addr => {
      val dest = getDestActorService(context, addr, path)
      if (dest != null) {
        dest ! data
      } else {
        RepLogger.error(RepLogger.System_Logger, s"Send,dest is null," +
          s"dest=${addr.toString},data=${data}")
      }
    })
  }
  def PublishOfCustom(context:ActorContext,mediator:ActorRef,topic:String,data:Any):Unit={
    if(this.useCustomBroadcast){
      topic match {
        case Topic.Transaction =>
          val path = hm(Topic.Transaction)
          val nodes = ctx.getNodeMgr.getStableNodes
          Send(context, nodes, path, data)
        case Topic.Block =>
          val path = hm(Topic.Block)
          val nodes = ctx.getNodeMgr.getNodes
          Send(context, nodes, path, data)
      }
    }else{
      mediator ! Publish(topic, data)
    }
  }


}
