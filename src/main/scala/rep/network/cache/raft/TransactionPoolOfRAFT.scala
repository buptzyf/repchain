package rep.network.cache.raft

import akka.actor.Props
import rep.network.cache.ITransactionPool
import rep.network.module.cfrd.CFRDActorType
import rep.network.consensus.cfrd.MsgOfCFRD.VoteOfBlocker

/**
 * Created by jiangbuyun on 2020/03/19.
 * RAFT共识的确认块actor
 */

object TransactionPoolOfRAFT{
  def props(name: String): Props = Props(classOf[TransactionPoolOfRAFT], name)
}

class TransactionPoolOfRAFT (moduleName: String) extends ITransactionPool(moduleName){
  override protected def sendVoteMessage: Unit = {
    pe.getActorRef(CFRDActorType.ActorType.voter) ! VoteOfBlocker
  }
}

