package rep.network.persistence.raft

import akka.actor.Props
import rep.log.RepLogger
import rep.network.module.cfrd.CFRDActorType
import rep.network.persistence.IStorager
import rep.network.consensus.cfrd.MsgOfCFRD.VoteOfBlocker

/**
 * Created by jiangbuyun on 2020/03/19.
 * 基于RAFT共识实现的存储actor
 */

object  StoragerOfRAFT{
  def props(name: String): Props = Props(classOf[StoragerOfRAFT], name)
}

class StoragerOfRAFT  (moduleName: String) extends IStorager (moduleName: String){
  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Storager_Logger, this.getLogMsgPrefix( "StoragerOfCFRD Start"))
  }

  override protected def sendVoteMessage: Unit = {
    pe.getActorRef( CFRDActorType.ActorType.voter) ! VoteOfBlocker
  }
}
