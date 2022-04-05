package rep.network.consensus.raft.block

import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.autotransaction.Topic
import rep.network.module.cfrd.CFRDActorType
import rep.network.util.NodeHelp
import rep.proto.rc2.{Block, Event}
import rep.utils.GlobalUtils.EventType
import rep.network.consensus.cfrd.MsgOfCFRD.{CreateBlock, VoteOfBlocker}
import rep.network.consensus.common.block.IBlocker
import rep.network.consensus.common.MsgOfConsensus.{ConfirmedBlock, PreTransBlock, PreTransBlockOfCache, PreTransBlockOfStream, PreTransBlockResult, preTransBlockResultOfCache, preTransBlockResultOfStream}
import rep.app.conf.{SystemProfile, TimePolicy}
import rep.network.consensus.util.BlockHelp
import rep.network.module.ModuleActorType
import rep.storage.ImpDataPreloadMgr

import scala.util.Random

object BlockOfRaftInStram {
  def props(name: String): Props = Props(classOf[BlockOfRaftInStram], name)
}

class BlockOfRaftInStram(moduleName: String) extends IBlocker(moduleName) {
  var preBlockHash : String = null
  var preblock: Block = null
  var blockIdentifier : String = null
  var dbIdentifier: String = null
  var blockStartTime : Long = Long.MaxValue
  var isPublish : Boolean = false
  val timeoutOfRaft = TimePolicy.getTimeoutPreload + 2

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("BlockOfRaftInStram module start"))
    super.preStart()
  }

  private def checkedStatus: Boolean = {
    var r = false
    if (this.preblock == null) {
      r = true
    } else {
      //当前存在预执行的区块
      if(this.isPublish){
        if(this.preblock.header.get.hashPrevious.toStringUtf8 != pe.getCurrentBlockHash){
          r = true
        }
      }else{
        if ((System.currentTimeMillis() - this.blockStartTime) / 1000 > this.timeoutOfRaft) {
          //超时，重置当前Actor状态
          pe.getTransPoolMgr.rollbackTransaction("identifier-" + this.preblock.header.get.height)
          resetStatus
          r = true
        }
      }
    }
    r
  }

  private def resetStatus = {
    this.preblock = null
    pe.removeBlock(this.blockIdentifier)
    blockIdentifier = null
    this.blockStartTime = Long.MaxValue
    isPublish = false
  }

  private def NewBlock={
    val newHeight = pe.getCurrentHeight + 1
    RepTimeTracer.setStartTime(pe.getSysTag, "Block", System.currentTimeMillis(), newHeight, 0)
    RepTimeTracer.setStartTime(pe.getSysTag, "createBlock", System.currentTimeMillis(), newHeight, 0)
    RepTimeTracer.setStartTime(pe.getSysTag, "collectTransToBlock", System.currentTimeMillis(), newHeight, 0)
    val trans = pe.getTransPoolMgr.packageTransaction("identifier-" + newHeight, SystemProfile.getLimitBlockTransNum, pe.getSysTag) //CollectedTransOfBlock(start, SystemProfile.getLimitBlockTransNum, SystemProfile.getBlockLength).reverse.toSeq
    //todo 交易排序
    if (trans.size >= SystemProfile.getMinBlockTransNum) {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,CollectedTransOfBlock success,height=${newHeight},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
      RepTimeTracer.setEndTime(pe.getSysTag, "collectTransToBlock", System.currentTimeMillis(), newHeight, trans.size)
      //此处建立新块必须采用抽签模块的抽签结果来进行出块，否则出现刚抽完签，马上有新块的存储完成，就会出现错误
      this.preblock = BlockHelp.WaitingForExecutionOfBlock(pe.getCurrentBlockHash, newHeight, trans)
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,height=${this.preblock.header.get.height},local height=${pe.getCurrentHeight}" + "~" + selfAddr))
      RepTimeTracer.setStartTime(pe.getSysTag, "PreloadTrans", System.currentTimeMillis(), this.preblock.header.get.height, this.preblock.transactions.size)
      this.blockIdentifier = "blockCache_" + Random.nextInt(10000)
      if (this.dbIdentifier == null) {
        this.dbIdentifier = pe.getBlocker.voteBlockHash
      } else if (this.dbIdentifier != pe.getBlocker.voteBlockHash) {
        ImpDataPreloadMgr.Free(pe.getSysTag, "preload-" + this.dbIdentifier)
        this.dbIdentifier = pe.getBlocker.voteBlockHash
      }
      pe.addBlock(this.blockIdentifier, this.preblock)
      this.blockStartTime = System.currentTimeMillis()
      this.isPublish = false
      pe.getActorRef(ModuleActorType.ActorType.transactionPreloadInStream) ! PreTransBlockOfStream(this.blockIdentifier, "preload-" + this.dbIdentifier)
    }else{
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("trans number < 0 error" + "~" + selfAddr))
      //如果缓存还有交易，让抽签模块，立即抽签
      //if(pe.getTransPoolMgr.getTransLength() > SystemProfile.getMinBlockTransNum)
      //  pe.getActorRef(CFRDActorType.ActorType.voter) ! VoteOfBlocker
    }
  }

  private def preloadedHandler={
    RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans", System.currentTimeMillis(), this.preblock.header.get.height, this.preblock.transactions.size)
    RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,prelaod success,height=${this.preblock.header.get.height},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
    this.preblock = pe.getBlock(this.blockIdentifier)
    RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,AddBlockHash success,height=${this.preblock.header.get.height},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
    this.preblock = BlockHelp.AddSignToBlock(this.preblock, pe.getSysTag)
    schedulerLink = clearSched()
    pe.setCreateHeight(preblock.header.get.height)
    pe.getTransPoolMgr.cleanPreloadCache("identifier-" + this.preblock.header.get.height)
    RepTimeTracer.setEndTime(pe.getSysTag, "createBlock", System.currentTimeMillis(), this.preblock.header.get.height, this.preblock.transactions.size)
    this.isPublish = true
    if(!pe.getZeroOfTransNumFlag) {
      mediator ! Publish(Topic.Block, ConfirmedBlock(this.preblock, self))
    }else{
      this.resetStatus
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("blocker transform error" + "~" + selfAddr))
    }
  }

  override def receive = {
    //创建块请求（给出块人）
    case CreateBlock =>
      if (!pe.isSynching) {
        if (NodeHelp.isBlocker(pe.getBlocker.blocker, pe.getSysTag) && !pe.getZeroOfTransNumFlag) {
          sendEvent(EventType.PUBLISH_INFO, mediator, pe.getSysTag, Topic.Block, Event.Action.CANDIDATOR)
          if(this.checkedStatus){
            if((pe.getMaxHeight4SimpleRaft - pe.getBlocker.VoteHeight ) <= SystemProfile.getBlockNumberOfRaft  && !pe.getZeroOfTransNumFlag) {
              this.NewBlock
            }else{
              RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"do not create new block,height=${pe.getCurrentHeight},voteheight-${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
            }
          }else{
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"do not create new block,status error,height=${pe.getCurrentHeight},voteheight-${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
          }
        }
      } else {
        //节点状态不对
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,node status error,status is synching,height=${pe.getCurrentHeight}" + "~" + selfAddr))
      }
    case preTransBlockResultOfStream(blockIdentifier,result)=>
      if(this.blockIdentifier == blockIdentifier ){
        if(result){
          //预执行成功
            preloadedHandler
        } else{
          //执行失败，开始清理
          pe.getTransPoolMgr.rollbackTransaction("identifier-" + this.preblock.header.get.height)
          this.resetStatus
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("new block result error" + "~" + selfAddr))
          //如果缓存还有交易，让抽签模块，立即抽签
          if(pe.getTransPoolMgr.getTransLength() > SystemProfile.getMinBlockTransNum)
            pe.getActorRef(CFRDActorType.ActorType.voter) ! VoteOfBlocker
        }
      }
    case _ => //ignore
  }

}
