package rep.api.rest

import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import rep.network.autotransaction.Topic
import rep.network.base.ModuleBase
import rep.proto.rc2.{Event, Transaction}
import rep.utils.GlobalUtils.EventType

object AcceptTransactionActor {
  def props(name: String): Props = Props(classOf[AcceptTransactionActor], name)
}

class AcceptTransactionActor(moduleName: String) extends ModuleBase(moduleName) {
  val config = pe.getRepChainContext.getConfig
  val contractOperationMode = config.getContractRunMode

  def preTransaction(t: Transaction): Unit = {

      try {
        //if (pe.getTransPoolMgr.getTransLength() < SystemProfile.getMaxCacheTransNum) {

        pe.getRepChainContext.getTransactionPool.addTransactionToCache(t)

        if(config.isBroadcastTransaction)
          //mediator ! Publish(Topic.Transaction, t)
          pe.getRepChainContext.getCustomBroadcastHandler.PublishOfCustom(context,mediator,Topic.Transaction,t)
        sendEvent(EventType.PUBLISH_INFO, mediator, pe.getSysTag, Topic.Transaction, Event.Action.TRANSACTION)
        //sender ! PostResult(t.id, None, None)
        /*} else {
          // 交易缓存池已满，不可继续提交交易
          sender ! PostResult(t.id, None, Option(s"交易缓存池已满，容量为${pe.getTransPoolMgr.getTransLength()}，不可继续提交交易"))
        }*/
      } catch {
        case e: Exception =>
          //sender ! PostResult(t.id, None, Option(e.getMessage))
      }

  }

  def receive: Receive = {
    case t : Transaction =>
      preTransaction(t)
  }
}
