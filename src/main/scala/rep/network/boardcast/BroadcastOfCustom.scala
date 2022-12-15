package rep.network.boardcast

import akka.actor.{ActorContext, ActorRef, ActorSelection, Address}
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import rep.app.system.RepChainSystemContext
import rep.log.RepLogger
import rep.network.autotransaction.Topic
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class BroadcastOfCustom(ctx:RepChainSystemContext) {
  private val hm : mutable.HashMap[String,ArrayBuffer[String]] = new mutable.HashMap[String,ArrayBuffer[String]]()
  init
  private def init:Unit={
    hm += Topic.Transaction -> new ArrayBuffer[String]()
    hm += Topic.Block -> new ArrayBuffer[String]()
    hm += Topic.MessageWithZeroTransaction -> new ArrayBuffer[String]()
    hm += Topic.VoteTransform -> new ArrayBuffer[String]()
    hm += Topic.Event -> new ArrayBuffer[String]()
  }

  def SubscribeTopic(topic:String,path:String):Unit={
    val ls = hm(topic)
    if(ls != null && !ls.contains(path)){
       ls += path
    }
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
          path.foreach(p=>{
            Send(context, nodes, p, data)
          })
        case Topic.Block =>
          val path = hm(Topic.Block)
          val nodes = ctx.getNodeMgr.getNodes
          path.foreach(p=>{
            Send(context, nodes, p, data)
          })
        case Topic.MessageWithZeroTransaction=>
          val path = hm(Topic.MessageWithZeroTransaction)
          val nodes = ctx.getNodeMgr.getStableNodes
          path.foreach(p => {
            Send(context, nodes, p, data)
          })
        case Topic.VoteTransform=>
          val path = hm(Topic.VoteTransform)
          val nodes = ctx.getNodeMgr.getStableNodes
          path.foreach(p => {
            Send(context, nodes, p, data)
          })
        case Topic.Event =>
          val path = hm(Topic.Event)
          val nodes = ctx.getNodeMgr.getStableNodes
          path.foreach(p => {
            Send(context, nodes, p, data)
          })
      }
    }else{
      mediator ! Publish(topic, data)
    }
  }


}
