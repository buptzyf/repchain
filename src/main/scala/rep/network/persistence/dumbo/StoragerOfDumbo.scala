package rep.network.persistence.dumbo

import akka.actor.Props
import rep.log.RepLogger
import rep.network.consensus.asyncconsensus.AsyncBlocker.startup
import rep.network.module.cfrd.CFRDActorType
import rep.network.persistence.IStorager
import rep.network.persistence.raft.StoragerOfRAFT

object StoragerOfDumbo{
  def props(name: String): Props = Props(classOf[StoragerOfDumbo], name)
}

class StoragerOfDumbo  (moduleName: String) extends IStorager (moduleName: String){
  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Storager_Logger, this.getLogMsgPrefix( "StoragerOfDumbo Start"))
  }

  override protected def sendVoteMessage: Unit = {
    pe.getActorRef( CFRDActorType.ActorType.blocker) ! startup
  }
}