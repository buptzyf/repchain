package rep.network.persistence.cfrd

import akka.actor.Props
import rep.log.RepLogger
import rep.network.module.cfrd.CFRDActorType
import rep.network.persistence.IStorager
import rep.network.consensus.cfrd.MsgOfCFRD.VoteOfBlocker

/**
 * Created by jiangbuyun on 2020/03/19.
 * 基于CFRD共识实现的存储actor
 */

object StoragerOfCFRD{
  def props(name: String): Props = Props(classOf[StoragerOfCFRD], name)
}

class StoragerOfCFRD  (moduleName: String) extends IStorager (moduleName: String){
  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Storager_Logger, this.getLogMsgPrefix( "StoragerOfCFRD Start"))
  }

  override protected def sendVoteMessage: Unit = {
    pe.getActorRef( CFRDActorType.ActorType.voter) ! VoteOfBlocker
  }
}
