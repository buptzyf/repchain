package rep.network.consensus.raft.block

import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.autotransaction.Topic
import rep.network.module.cfrd.CFRDActorType
import rep.network.util.NodeHelp
import rep.utils.GlobalUtils.EventType
import rep.network.consensus.cfrd.MsgOfCFRD.{CreateBlock, VoteOfBlocker}
import rep.network.consensus.common.block.IBlocker
import rep.network.consensus.common.MsgOfConsensus.{ConfirmedBlock, PreTransBlock, PreTransBlockOfCache, PreTransBlockResult, preTransBlockResultOfCache}
import rep.network.consensus.util.BlockHelp
import rep.network.module.ModuleActorType
import akka.pattern.{AskTimeoutException, ask}
import rep.proto.rc2.{Block, Event}
import scala.concurrent.Await
import scala.util.Random


/**
 *Created by jiangbuyun on 2020/03/17.
 * RAFT共识协议的出块人actor
 */
object  BlockerOfRAFT {
  def props(name: String): Props = Props(classOf[BlockerOfRAFT], name)
}

class BlockerOfRAFT (moduleName: String) extends IBlocker(moduleName){
  var preblock: Block = null
  var dbIdentifier : String = null

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("BlockerOfRAFT module start"))
    //pe.register(CFRDActorType.ActorType.blocker,self)
    super.preStart()
  }

  override protected def ExecuteTransactionOfBlock(block: Block): Block = {
    try {
      if(this.dbIdentifier == null){
        this.dbIdentifier = pe.getBlocker.voteBlockHash
      } else if(this.dbIdentifier != pe.getBlocker.voteBlockHash){
        pe.getRepChainContext.freeBlockPreloadInstance("preload-"+this.dbIdentifier)
        this.dbIdentifier = pe.getBlocker.voteBlockHash
     }
      val future = pe.getActorRef(ModuleActorType.ActorType.dispatchofpreload) ? PreTransBlock(block, "preload-"+this.dbIdentifier)
      val result = Await.result(future, timeout.duration).asInstanceOf[PreTransBlockResult]
      if (result.result) {
        result.blc
      } else {
        null
      }
    } catch {
      case e: AskTimeoutException => null
    }
  }

  protected def ExecuteTransactionOfBlockOfCache(block: Block): Block = {
    val cacheIdentifier = "blockCache_" + Random.nextInt(10000)
    try {
      if(this.dbIdentifier == null){
        this.dbIdentifier = pe.getBlocker.voteBlockHash
      } else if(this.dbIdentifier != pe.getBlocker.voteBlockHash){
        pe.getRepChainContext.freeBlockPreloadInstance("preload-"+this.dbIdentifier)
        this.dbIdentifier = pe.getBlocker.voteBlockHash
      }

      pe.addBlock(cacheIdentifier,block)
      val future = pe.getActorRef(ModuleActorType.ActorType.dispatchofpreload) ? PreTransBlockOfCache(cacheIdentifier,"preload-"+this.dbIdentifier)
      val result = Await.result(future, timeout.duration).asInstanceOf[preTransBlockResultOfCache]
      if (result.result) {
        pe.getBlock(cacheIdentifier)
      } else {
        null
      }
    } catch {
      case e: AskTimeoutException => null
    }finally {
      pe.removeBlock(cacheIdentifier)
    }
  }

  private val config = pe.getRepChainContext.getConfig

  override protected def PackedBlock(start: Int = 0): Block = {
    val newHeight = pe.getCurrentHeight + 1
    RepTimeTracer.setStartTime(pe.getSysTag, "Block", System.currentTimeMillis(), newHeight, 0)
    RepTimeTracer.setStartTime(pe.getSysTag, "createBlock", System.currentTimeMillis(), newHeight, 0)
    RepTimeTracer.setStartTime(pe.getSysTag, "collectTransToBlock", System.currentTimeMillis(),  newHeight, 0)
    val trans = pe.getRepChainContext.getTransactionPool.packageTransactionToBlock//CollectedTransOfBlock(start, SystemProfile.getLimitBlockTransNum, SystemProfile.getBlockLength).reverse.toSeq
    //todo 交易排序
    if (trans.size >= config.getMinTransactionNumberOfBlock) {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,CollectedTransOfBlock success,height=${newHeight },local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
      RepTimeTracer.setEndTime(pe.getSysTag, "collectTransToBlock", System.currentTimeMillis(), newHeight, trans.size)
      //此处建立新块必须采用抽签模块的抽签结果来进行出块，否则出现刚抽完签，马上有新块的存储完成，就会出现错误
      var blc = BlockHelp.buildBlock(pe.getCurrentBlockHash, newHeight, trans)
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,height=${blc.getHeader.height},local height=${pe.getCurrentHeight}" + "~" + selfAddr))
      RepTimeTracer.setStartTime(pe.getSysTag, "PreloadTrans", System.currentTimeMillis(), blc.getHeader.height, blc.transactions.size)
      blc = ExecuteTransactionOfBlock(blc)
      //blc = ExecuteTransactionOfBlockOfCache(blc)
      if (blc != null) {
        RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans", System.currentTimeMillis(), blc.getHeader.height, blc.transactions.size)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,prelaod success,height=${blc.getHeader.height},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
        //blc = BlockHelp.AddBlockHash(blc)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,AddBlockHash success,height=${blc.getHeader.height},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
        blc.withHeader(BlockHelp.AddHeaderSignToBlock(blc.getHeader, pe.getSysTag,pe.getRepChainContext.getSignTool))
      } else {
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("create new block error,preload error" + "~" + selfAddr))
        null
      }
    } else {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("create new block error,trans count error" + "~" + selfAddr))
      null
    }
  }

  private def CreateBlockHandler = {
    var blc : Block = null

    blc = PackedBlock(0)
    if (blc != null && !blc.getHeader.hashPresent.isEmpty && blc.transactions.length > 0 ) {
      RepTimeTracer.setEndTime(pe.getSysTag, "createBlock", System.currentTimeMillis(), blc.getHeader.height, blc.transactions.size)
      this.preblock = blc
      schedulerLink = clearSched()
      pe.setCreateHeight(preblock.getHeader.height)
      if(!pe.getZeroOfTransNumFlag)
        //mediator ! Publish(Topic.Block, ConfirmedBlock(preblock, self))
      pe.getRepChainContext.getCustomBroadcastHandler.BroadcastConfirmBlock(context,mediator,ConfirmedBlock(preblock, self))
    } else {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("create new block error,CreateBlock is null" + "~" + selfAddr))
      if(pe.getRepChainContext.getTransactionPool.getCachePoolSize > config.getMinTransactionNumberOfBlock)
        pe.getActorRef(CFRDActorType.ActorType.voter) ! VoteOfBlocker
    }
  }

  override def receive = {
    //创建块请求（给出块人）
    case CreateBlock =>
      if (!pe.isSynching) {
        if (NodeHelp.isBlocker(pe.getBlocker.blocker, pe.getSysTag) && !pe.getZeroOfTransNumFlag){
          sendEvent(EventType.PUBLISH_INFO, mediator, pe.getSysTag, Topic.Block, Event.Action.CANDIDATOR)
          if (preblock == null || (preblock.getHeader.hashPrevious.toStringUtf8() != pe.getCurrentBlockHash)) {
            //是出块节点
            if((pe.getMaxHeight4SimpleRaft - pe.getBlocker.VoteHeight ) <= config.getBlockNumberOfRaft && !pe.getZeroOfTransNumFlag) {
              CreateBlockHandler
            }
          }
        }
      } else {
        //节点状态不对
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,node status error,status is synching,height=${pe.getCurrentHeight}" + "~" + selfAddr))
      }

    case _ => //ignore
  }

}
