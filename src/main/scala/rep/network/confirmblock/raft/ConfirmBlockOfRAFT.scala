package rep.network.confirmblock.raft

import akka.actor.{ActorRef, Props}
import rep.log.RepLogger
import rep.network.autotransaction.Topic
import rep.network.confirmblock.IConfirmOfBlock
import rep.network.consensus.common.MsgOfConsensus.BlockRestore
import rep.network.module.ModuleActorType
import rep.network.persistence.IStorager.SourceOfBlock
import rep.utils.GlobalUtils.EventType
import rep.network.consensus.common.MsgOfConsensus.BatchStore
import rep.proto.rc2.{Block, Event}

/**
 * Created by jiangbuyun on 2020/03/19.
 * RAFT共识的确认块actor
 */

object ConfirmBlockOfRAFT{
  def props(name: String): Props = Props(classOf[ConfirmBlockOfRAFT], name)
}

class ConfirmBlockOfRAFT(moduleName: String) extends IConfirmOfBlock(moduleName: String) {
  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("ConfirmBlockOfRAFT module start"))
    if (pe.getRepChainContext.getConfig.useCustomBroadcast) {
      pe.getRepChainContext.getCustomBroadcastHandler.SubscribeTopic(Topic.Block, "/user/modulemanager/confirmerofblock")
      RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix("Subscribe custom broadcast,/user/modulemanager/confirmerofblock"))
    }
    //else {
      SubscribeTopic(mediator, self, selfAddr, Topic.Block, false)
      RepLogger.info(RepLogger.System_Logger,this.getLogMsgPrefix("Subscribe system broadcast,/user/modulemanager/confirmerofblock"))
    //}
  }

  override protected def handler(block: Block, actRefOfBlock: ActorRef) = {
    RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"confirm verify endorsement start,height=${block.getHeader.height}"))
    if (pe.getRepChainContext.getConfig.isVerifyOfEndorsement) {
      if (asyncVerifyEndorses(block)) {
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"confirm verify endorsement end,height=${block.getHeader.height}"))
        //背书人的签名一致
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"confirm verify endorsement sort,height=${block.getHeader.height}"))
        pe.getBlockCacheMgr.addToCache(BlockRestore(block, SourceOfBlock.CONFIRMED_BLOCK, actRefOfBlock))
        pe.getActorRef(ModuleActorType.ActorType.storager) ! BatchStore
        sendEvent(EventType.RECEIVE_INFO, mediator, pe.getSysTag, Topic.Block, Event.Action.BLOCK_NEW)
      } else {
        //背书验证有错误
        RepLogger.error(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"confirm verify endorsement error,height=${block.getHeader.height}"))
      }
    } else {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"confirm verify endorsement sort,height=${block.getHeader.height}"))
      pe.getBlockCacheMgr.addToCache(BlockRestore(block, SourceOfBlock.CONFIRMED_BLOCK, actRefOfBlock))
      pe.getActorRef(ModuleActorType.ActorType.storager) ! BatchStore
      sendEvent(EventType.RECEIVE_INFO, mediator, pe.getSysTag, Topic.Block, Event.Action.BLOCK_NEW)
    }
  }

  override protected def checkedOfConfirmBlock(block: Block, actRefOfBlock: ActorRef) = {
    if (pe.getCurrentBlockHash == "" && block.getHeader.hashPrevious.isEmpty()) {
      //if (NodeHelp.isSeedNode(pe.getNodeMgr.getStableNodeName4Addr(actRefOfBlock.path.address))) {
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"confirm verify blockhash,height=${block.getHeader.height}"))
        handler(block, actRefOfBlock)
      //}else{
      //  RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"not confirm genesis block,blocker is not seed node,height=${block.height}"))
      //}
    } else {
      //与上一个块一致
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"confirm verify blockhash,height=${block.getHeader.height}"))
      handler(block, actRefOfBlock)
      pe.setConfirmHeight(block.getHeader.height)
      pe.resetTimeoutOfRaft
    }
  }
}
