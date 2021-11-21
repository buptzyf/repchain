package rep.network.confirmblock

import akka.actor.{ActorContext, ActorSelection}
import rep.app.conf.SystemProfile
import rep.network.consensus.common.MsgOfConsensus.ConfirmedBlock

class BoardcastConfirmBlock1 (context:ActorContext, cb:ConfirmedBlock) extends Runnable{
  protected val responseActorName = "/user/modulemanager/confirmerofblock"

  protected def toAkkaUrl(addr: String, actorName: String): String = {
    addr + actorName;
  }
  override def run(): Unit = {
    if(SystemProfile.getIsForwarding){
      SystemProfile.getForwardingNodes.forEach(addr=>{
        val selection: ActorSelection = context.actorSelection(toAkkaUrl(addr.toString, responseActorName));
        val future1 = selection ! cb
      })
    }
  }
}