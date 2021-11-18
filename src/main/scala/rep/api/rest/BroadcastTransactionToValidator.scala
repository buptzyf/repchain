package rep.api.rest

import akka.actor.{ActorContext, ActorSelection}
import rep.app.conf.SystemProfile
import rep.log.RepLogger
import rep.protos.peer.Transaction

class BroadcastTransactionToValidator(t:Transaction,context:ActorContext,validatorAddrIdx:Int) extends Runnable{
  protected val validatorActorName = "/user/modulemanager/dispatchtransactiontovalidator"

  protected def toAkkaUrl(addr: String, actorName: String): String = {
    addr + actorName;
  }

  override def run(): Unit = {
        val selection: ActorSelection = context.actorSelection(toAkkaUrl(SystemProfile.getValidatorAddr.get(validatorAddrIdx), validatorActorName));
        RepLogger.info(RepLogger.Consensus_Logger,s"^^^^^^^^${selection.anchorPath.toString}")
        val future1 = selection ! t
        //System.out.println(s"send transaction to validator,addr:${SystemProfile.getValidatorAddr.get(validatorAddrIdx)}")
  }

}
