package rep.network.consensus.raft.block

import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.autotransaction.Topic
import rep.network.module.cfrd.CFRDActorType
import rep.network.util.NodeHelp
import rep.protos.peer.{Block, Event}
import rep.utils.GlobalUtils.EventType
import rep.network.consensus.cfrd.MsgOfCFRD.{CreateBlock, VoteOfBlocker}
import rep.network.consensus.common.block.IBlocker
import rep.network.consensus.common.MsgOfConsensus.ConfirmedBlock
import rep.app.conf.SystemProfile
import rep.network.confirmblock.BoardcastComfirmBlock
import rep.network.consensus.util.BlockHelp


/**
 *Created by jiangbuyun on 2020/03/17.
 * RAFT共识协议的出块人actor
 */
object  BlockerOfRAFT {
  def props(name: String): Props = Props(classOf[BlockerOfRAFT], name)
}

class BlockerOfRAFT (moduleName: String) extends IBlocker(moduleName){
  var preblock: Block = null

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("BlockerOfRAFT module start"))
    //pe.register(CFRDActorType.ActorType.blocker,self)
    super.preStart()
  }

  override protected def PackedBlock(start: Int = 0): Block = {
    RepTimeTracer.setStartTime(pe.getSysTag, "Block", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
    RepTimeTracer.setStartTime(pe.getSysTag, "createBlock", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
    RepTimeTracer.setStartTime(pe.getSysTag, "collectTransToBlock", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
    val trans = CollectedTransOfBlock(start, SystemProfile.getLimitBlockTransNum, SystemProfile.getBlockLength).reverse.toSeq
    //todo 交易排序
    if (trans.size >= SystemProfile.getMinBlockTransNum) {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,CollectedTransOfBlock success,height=${pe.getBlocker.VoteHeight },local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
      RepTimeTracer.setEndTime(pe.getSysTag, "collectTransToBlock", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, trans.size)
      //此处建立新块必须采用抽签模块的抽签结果来进行出块，否则出现刚抽完签，马上有新块的存储完成，就会出现错误
      var blc = BlockHelp.WaitingForExecutionOfBlock(pe.getCurrentBlockHash, pe.getCurrentHeight + 1, trans)
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,height=${blc.height},local height=${pe.getCurrentHeight}" + "~" + selfAddr))
      RepTimeTracer.setStartTime(pe.getSysTag, "PreloadTrans", System.currentTimeMillis(), blc.height, blc.transactions.size)
      blc = ExecuteTransactionOfBlock(blc)
      if (blc != null) {
        RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans", System.currentTimeMillis(), blc.height, blc.transactions.size)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,prelaod success,height=${blc.height},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
        if (blc.hashOfBlock == _root_.com.google.protobuf.ByteString.EMPTY) {
          blc = BlockHelp.AddBlockHash(blc)
        }
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,AddBlockHash success,height=${blc.height},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
        BlockHelp.AddSignToBlock(blc, pe.getSysTag)
      } else {
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("create new block error,preload error" + "~" + selfAddr))
        PackedBlock(start + trans.size)
      }
    } else {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("create new block error,trans count error" + "~" + selfAddr))
      null
    }
  }

  private def CreateBlockHandler = {
    var blc : Block = null

    blc = PackedBlock(0)
    if (blc != null) {
      RepTimeTracer.setEndTime(pe.getSysTag, "createBlock", System.currentTimeMillis(), blc.height, blc.transactions.size)
      this.preblock = blc
      schedulerLink = clearSched()
      pe.setCreateHeight(preblock.height)
      mediator ! Publish(Topic.Block, ConfirmedBlock(preblock, self))
      var confirms = new BoardcastComfirmBlock(context,ConfirmedBlock(preblock, self),pe.getNodeMgr.getNodes,pe.getNodeMgr.getStableNodes)
      this.works.execute(confirms)
    } else {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("create new block error,CreateBlock is null" + "~" + selfAddr))
      pe.getActorRef(CFRDActorType.ActorType.voter) ! VoteOfBlocker
    }
  }

  override def receive = {
    //创建块请求（给出块人）
    case CreateBlock =>
      if (!pe.isSynching) {
        if (NodeHelp.isBlocker(pe.getBlocker.blocker, pe.getSysTag)){
          sendEvent(EventType.PUBLISH_INFO, mediator, pe.getSysTag, Topic.Block, Event.Action.CANDIDATOR)
          if (preblock == null || (preblock.previousBlockHash.toStringUtf8() != pe.getCurrentBlockHash)) {
            //是出块节点
            CreateBlockHandler
          }
        }
      } else {
        //节点状态不对
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,node status error,status is synching,height=${pe.getCurrentHeight}" + "~" + selfAddr))
      }

    case _ => //ignore
  }

}
