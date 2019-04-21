package rep.network.sync

import akka.actor.{ ActorRef, Props }
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import rep.network.base.ModuleBase
import rep.utils.GlobalUtils.{ ActorType, BlockEvent, EventType }
import rep.storage.ImpDataAccess
import rep.protos.peer._
import rep.network.util.NodeHelp
import rep.network.sync.SyncMsg.{ResponseInfo,BlockDataOfResponse}
import rep.log.RepLogger

object SynchronizeResponser {
  def props(name: String): Props = Props(classOf[SynchronizeResponser], name)
}

class SynchronizeResponser(moduleName: String) extends ModuleBase(moduleName) {

  import context.dispatcher
  import scala.concurrent.duration._
  import scala.util.control.Breaks._

  override def preStart(): Unit = {
    SubscribeTopic(mediator, self, selfAddr, BlockEvent.CHAIN_INFO_SYNC, true)
    RepLogger.info(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix( "SynchronizeResponse start"))
  }

  val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)

  override def receive: Receive = {
    case SyncMsg.ChainInfoOfRequest =>
      if (NodeHelp.isSameNodeForRef(sender(), self)) {
        RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(  s"recv sync chaininfo request,it is self,do not response, from actorAddr" + "～" + NodeHelp.getNodePath(sender())))
      } else {
        RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix( s"recv sync chaininfo request from actorAddr" + "～" + NodeHelp.getNodePath(sender())))
        val responseInfo = dataaccess.getBlockChainInfo()
        sender ! ResponseInfo(responseInfo,self)
      }

    case SyncMsg.BlockDataOfRequest(startHeight) =>
      sendEvent(EventType.PUBLISH_INFO, mediator,sender.path.toString(), selfAddr,  Event.Action.BLOCK_SYNC)
      sendEventSync(EventType.PUBLISH_INFO, mediator,sender.path.toString(), selfAddr,  Event.Action.BLOCK_SYNC)
      RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(  s"node number:${pe.getSysTag},start block number:${startHeight},Get a data request from  $sender" + "～" + selfAddr))
      val local = dataaccess.getBlockChainInfo()
      var data = Block()
      if (local.height >= startHeight) {
        data = dataaccess.getBlock4ObjectByHeight(startHeight)
        sender  ! SyncMsg.BlockDataOfResponse(data)
      }

  }

}