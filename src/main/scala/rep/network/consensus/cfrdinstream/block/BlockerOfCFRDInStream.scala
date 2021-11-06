package rep.network.consensus.cfrdinstream.block

import akka.actor.Props
import akka.pattern.{AskTimeoutException, ask}
import rep.app.conf.SystemProfile
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.autotransaction.Topic
import rep.network.consensus.cfrd.MsgOfCFRD.{CollectEndorsement, CreateBlock, ForceVoteInfo}
import rep.network.consensus.common.MsgOfConsensus.{PreTransBlock, PreTransBlockOfCache, PreTransBlockResult, preTransBlockResultOfCache}
import rep.network.consensus.common.block.IBlocker
import rep.network.consensus.util.BlockHelp
import rep.network.module.ModuleActorType
import rep.network.module.cfrd.CFRDActorType
import rep.network.util.NodeHelp
import rep.protos.peer.{Block, Event}
import rep.storage.ImpDataPreloadMgr
import rep.utils.GlobalUtils.{BlockerInfo, EventType}

import scala.concurrent.Await


object BlockerOfCFRDInStream{
  def props(name: String): Props = Props(classOf[BlockerOfCFRDInStream], name)
}

class BlockerOfCFRDInStream(moduleName: String) extends IBlocker(moduleName){
  var voteinfo:BlockerInfo = null
  var lastPreloadBlock: Block = null
  val blockIdentifier_prefix : String = "blockidentifier_"
  val dbIdentifier_prefix: String = "dbidentifier_"


  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("BlockOfRaftInStram module start"))
    super.preStart()
  }

  override protected def ExecuteTransactionOfBlock(block: Block): Block = {
    try {
      val dbtag = this.dbIdentifier_prefix + this.voteinfo.VoteHeight+"_"+this.voteinfo.VoteIndex
      val future = pe.getActorRef(ModuleActorType.ActorType.dispatchofpreloadinstream) ? PreTransBlock(block, "preload-"+dbtag)
      val result = Await.result(future, timeout.duration).asInstanceOf[PreTransBlockResult]
      if (result.result) {
        result.blc
      } else {
        null
      }
    } catch {
      case e: AskTimeoutException =>
        pe.getTransPoolMgr.rollbackTransaction("blockidentifier_"+block.height)
        null
    }
  }

  protected def ExecuteTransactionOfBlockOfCache(block: Block): Block = {
    val cacheIdentifier = this.blockIdentifier_prefix + this.voteinfo.VoteHeight+"_"+this.voteinfo.VoteIndex
    try {
      val dbtag = this.dbIdentifier_prefix + this.voteinfo.VoteHeight+"_"+this.voteinfo.VoteIndex
      pe.addBlock(cacheIdentifier,block)
      val future = pe.getActorRef(ModuleActorType.ActorType.dispatchofpreloadinstream) ? PreTransBlockOfCache(cacheIdentifier,"preload-"+dbtag)
      val result = Await.result(future, timeout.duration).asInstanceOf[preTransBlockResultOfCache]
      if (result.result) {
        pe.getBlock(cacheIdentifier)
      } else {
        null
      }
    } catch {
      case e: AskTimeoutException =>
        pe.getTransPoolMgr.rollbackTransaction("blockidentifier_"+block.height+"_"+this.voteinfo.VoteIndex)
        null
    }finally {
      pe.removeBlock(cacheIdentifier)
    }
  }

  private def checkedStatus(vi:BlockerInfo): Boolean = {
    var r = false
    if(this.voteinfo == null){
      this.voteinfo = vi
      this.lastPreloadBlock = null
      r = true
    }else{
      if(!NodeHelp.IsSameVote(vi,pe.getBlocker)){
        //已经切换出块人，初始化信息
        ImpDataPreloadMgr.Free(pe.getSysTag, "preload-"+this.dbIdentifier_prefix + this.voteinfo.VoteHeight+"_"+this.voteinfo.VoteIndex)
        this.voteinfo = vi
        this.lastPreloadBlock = null
        r = true
      }else{
        if(this.lastPreloadBlock == null){
          r = true
        }else{
          if(this.lastPreloadBlock.height <= (this.voteinfo.VoteHeight + SystemProfile.getBlockNumberOfRaft) && !pe.getZeroOfTransNumFlag) {
            if(this.lastPreloadBlock.height <= pe.getConfirmHeight)
              r = true
          }
        }
      }
    }
    r
  }

  private  def getNewBlockHeight:Long={
    if(this.lastPreloadBlock == null){
      pe.getCurrentHeight + 1
    }else{
      this.lastPreloadBlock.height + 1
    }
  }

  private def getPrevHashOfNewBlock:String={
    if(this.lastPreloadBlock == null){
      pe.getCurrentBlockHash
    }else{
      this.lastPreloadBlock.hashOfBlock.toStringUtf8
    }
  }

  private def NewBlock={
    val newHeight = this.getNewBlockHeight
    val prevHash = this.getPrevHashOfNewBlock
    RepTimeTracer.setStartTime(pe.getSysTag, "Block", System.currentTimeMillis(), newHeight, 0)
    RepTimeTracer.setStartTime(pe.getSysTag, "createBlock", System.currentTimeMillis(), newHeight, 0)
    RepTimeTracer.setStartTime(pe.getSysTag, "collectTransToBlock", System.currentTimeMillis(), newHeight, 0)
    val trans = pe.getTransPoolMgr.packageTransaction(this.blockIdentifier_prefix + newHeight, SystemProfile.getLimitBlockTransNum, pe.getSysTag) //CollectedTransOfBlock(start, SystemProfile.getLimitBlockTransNum, SystemProfile.getBlockLength).reverse.toSeq
    //todo 交易排序
    if (trans.size >= SystemProfile.getMinBlockTransNum) {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,CollectedTransOfBlock success,height=${newHeight},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
      RepTimeTracer.setEndTime(pe.getSysTag, "collectTransToBlock", System.currentTimeMillis(), newHeight, trans.size)
      //此处建立新块必须采用抽签模块的抽签结果来进行出块，否则出现刚抽完签，马上有新块的存储完成，就会出现错误
      var blk = BlockHelp.WaitingForExecutionOfBlock(prevHash, newHeight, trans)
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,height=${newHeight},current height=${pe.getCurrentHeight},vote height=${this.voteinfo.VoteHeight}" + "~" + selfAddr))
      RepTimeTracer.setStartTime(pe.getSysTag, "PreloadTrans", System.currentTimeMillis(), newHeight, trans.size)
      blk = this.ExecuteTransactionOfBlock(blk)
      RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans", System.currentTimeMillis(), newHeight, trans.size)
      if(blk != null) {
        this.lastPreloadBlock = blk
        this.lastPreloadBlock = BlockHelp.AddSignToBlock(this.lastPreloadBlock, pe.getSysTag)
        RepTimeTracer.setStartTime(pe.getSysTag, "Endorsement", System.currentTimeMillis(), newHeight, this.lastPreloadBlock.transactions.size)
        //pe.getActorRef(CFRDActorType.ActorType.endorsementcollectionerinstream) ! CollectEndorsement(this.lastPreloadBlock, pe.getSysTag,pe.getBlocker.VoteIndex)
        pe.getActorRef(CFRDActorType.ActorType.endorsementcollectionerinstream) ! CollectEndorsement(this.lastPreloadBlock, ForceVoteInfo(this.voteinfo.voteBlockHash,this.voteinfo.VoteHeight,this.voteinfo.VoteIndex,pe.getSysTag))
      }else{
        pe.getTransPoolMgr.rollbackTransaction("blockidentifier_"+blk.height)
      }
    }else{
      pe.getTransPoolMgr.cleanPreloadCache(this.blockIdentifier_prefix+newHeight)
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("trans number < 0 error" + "~" + selfAddr))
    }
    RepTimeTracer.setEndTime(pe.getSysTag, "createBlock", System.currentTimeMillis(), newHeight, 0)
  }

  override def receive = {
    //创建块请求（给出块人）
    case CreateBlock =>
      if (!pe.isSynching) {
        if (NodeHelp.isBlocker(pe.getBlocker.blocker, pe.getSysTag) && !pe.getZeroOfTransNumFlag) {
          sendEvent(EventType.PUBLISH_INFO, mediator, pe.getSysTag, Topic.Block, Event.Action.CANDIDATOR)
          if(this.checkedStatus(pe.getBlocker) && pe.getTransPoolMgr.getTransLength() > 0){
            this.NewBlock
          }else{
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"do not create new block,status error,height=${pe.getCurrentHeight},voteheight-${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
          }
        }
      } else {
        //节点状态不对
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,node status error,status is synching,height=${pe.getCurrentHeight}" + "~" + selfAddr))
      }

    case _ => //ignore
  }
}
