package rep.network.confirmblock

import akka.actor.{ActorContext,  ActorSelection, Address}
import rep.network.consensus.common.MsgOfConsensus.ConfirmedBlock

class BoardcastComfirmBlock(context:ActorContext,cb:ConfirmedBlock,nodeAddress:Set[Address],stableNodeAddress:Set[Address]) extends Runnable{
  protected val responseActorName = "/user/modulemanager/confirmerofblock"

  protected def toAkkaUrl(addr: String, actorName: String): String = {
    addr + "/" + actorName;
  }
  override def run(): Unit = {
    nodeAddress.foreach(addr=>{
      if(!stableNodeAddress.contains(addr)){
        val selection: ActorSelection = context.actorSelection(toAkkaUrl(addr.toString, responseActorName));
        val future1 = selection ! cb
      }
    })
  }
}
