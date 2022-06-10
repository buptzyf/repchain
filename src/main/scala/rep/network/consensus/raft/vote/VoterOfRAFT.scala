package rep.network.consensus.raft.vote

import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import rep.log.RepLogger
import rep.network.autotransaction.Topic
import rep.network.consensus.cfrd.MsgOfCFRD.{CreateBlock, ForceVoteInfo, TransformBlocker, VoteOfBlocker, VoteOfForce}
import rep.network.consensus.common.algorithm.{IRandomAlgorithmOfVote, ISequencialAlgorithmOfVote}
import rep.network.consensus.common.vote.IVoter
import rep.network.module.cfrd.CFRDActorType
import rep.network.sync.SyncMsg.{StartSync, SyncPreblocker}
import rep.network.util.NodeHelp
import rep.utils.GlobalUtils.BlockerInfo

/**
 * Created by jiangbuyun on 2020/03/19.
 * RAFT共识实现的抽签actor
 */

object  VoterOfRAFT{
    def props(name: String): Props = Props(classOf[VoterOfRAFT],name)
}

class VoterOfRAFT (moduleName: String) extends IVoter(moduleName: String) {
  import scala.concurrent.duration._
  import context.dispatcher

  private val config = pe.getRepChainContext.getConfig
  private var voteIndex : Int = -1
  private var zeroOfTransNumTimeout : Long = -1
  //private var zeroOfTransNumFlag:Boolean = false
  private var transformInfo:TransformBlocker = null
  private var blockTimeout : Boolean = false
  this.algorithmInVoted = new ISequencialAlgorithmOfVote

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Vote_Logger, this.getLogMsgPrefix("VoterOfRAFT  module start"))
    if(config.getVoteNodeList.contains(pe.getSysTag)){
      //共识节点可以订阅交易的广播事件
      SubscribeTopic(mediator, self, selfAddr, Topic.VoteTransform, true)
    }
  }

  override protected def NoticeBlockerMsg: Unit = {
    RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},notice"))
    if (this.Blocker.blocker.equals(pe.getSysTag)) {
      //发送建立新块的消息
      RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},send create block"))
      pe.getActorRef(CFRDActorType.ActorType.blocker) ! CreateBlock
    }
  }

  override protected def resetBlocker(idx: Int, currentblockhash: String, currentheight: Long) = {
    pe.setConfirmHeight(0)
    pe.setCreateHeight(0)

    RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},votelist=${candidator.toArray[String].mkString("|")},idx=${idx}"))
    this.Blocker = BlockerInfo(algorithmInVoted.blocker(candidator.toArray[String], idx), idx, System.currentTimeMillis(), currentblockhash, currentheight)
    pe.resetBlocker(this.Blocker)
    if (this.Blocker.blocker.equals(pe.getSysTag)) {
      //发送建立新块的消息
      pe.setZeroOfTransNumFlag(false)
    }
    this.zeroOfTransNumTimeout = -1
    NoticeBlockerMsg
    RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},blocker=${this.Blocker.blocker},currentHeight=${pe.getMaxHeight4SimpleRaft}" + "~" + selfAddr))
  }

  override protected def DelayVote: Unit = {
    this.voteCount += 1
    var time = this.voteCount * pe.getRepChainContext.getTimePolicy.getVoteRetryDelay
    schedulerLink = clearSched()
    schedulerLink = scheduler.scheduleOnce(pe.getRepChainContext.getTimePolicy.getVoteRetryDelay.millis, self, VoteOfBlocker)
    }

  private def getVoteIndex:Int={
    if(this.voteIndex == Int.MaxValue) this.voteIndex = -1
    this.voteIndex += 1
    this.voteIndex
  }

  private def blockerIsNull={
    RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},first voter,currentHeight=${pe.getCurrentHeight},currentHash=${pe.getCurrentBlockHash}" + "~" + selfAddr))
    val maxinfo = pe.getStartVoteInfo
    var currentblockhash:String = ""
    var currentheight : Long = 0
    if(maxinfo.height > 0){
      currentblockhash = maxinfo.hash
      currentheight = maxinfo.height
    }else{
      currentblockhash = pe.getCurrentBlockHash
      currentheight = pe.getCurrentHeight
    }
    if(currentheight > 0){
      pe.resetTimeoutOfRaft
      this.blockTimeout = false
      this.cleanVoteInfo
      this.resetCandidator(currentblockhash)
      this.resetBlocker(getVoteIndex, currentblockhash, currentheight)
    }
    RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},first voter,currentHeight=${currentheight},currentHash=${currentblockhash}" + "~" + selfAddr))
  }

  private def blockerIsNotNull={
    if(this.transformInfo != null){
      //检查是否存在迁移出块人消息
      RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},transform,preblocker=${this.transformInfo.preBlocker}," +
        s"prevoteindex=${this.transformInfo.voteIndexOfBlocker},preblockheight=${this.transformInfo.heightOfBlocker},lvoteindex=${this.voteIndex},lheight=${pe.getCurrentHeight}" +
        s",transpoolcount=${pe.getRepChainContext.getTransactionPool.getCachePoolSize}" + "~" + selfAddr))
      transform
    }else if(pe.getRepChainContext.getTransactionPool.getCachePoolSize <= 0){
      if(NodeHelp.isBlocker(this.Blocker.blocker, pe.getSysTag)){
        if(this.zeroOfTransNumTimeout == -1){
          RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},set zero trans time,lblocker=${this.Blocker.blocker}," +
            s"lvoteindex=${this.voteIndex},lblockheight=${pe.getCurrentHeight}" +
            s",transpoolcount=${pe.getRepChainContext.getTransactionPool.getCachePoolSize}" + "~" + selfAddr))
          this.zeroOfTransNumTimeout = System.currentTimeMillis()
        }else{
          if((System.currentTimeMillis() - this.zeroOfTransNumTimeout) > pe.getRepChainContext.getTimePolicy.getVoteWaitingDelay * 10){
            //已经超时
            //发出迁移出块人消息
            RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},set zero trans timeout,lblocker=${this.Blocker.blocker}," +
              s"lvoteindex=${this.voteIndex},lblockheight=${pe.getCurrentHeight}" +
              s",transpoolcount=${pe.getRepChainContext.getTransactionPool.getCachePoolSize}" + "~" + selfAddr))
            pe.setZeroOfTransNumFlag(true)
            this.zeroOfTransNumTimeout = System.currentTimeMillis()
            this.zeroOfTransNumTimeout = -1
            pe.resetTimeoutOfRaft
            this.blockTimeout = false
            mediator ! Publish(Topic.VoteTransform,  TransformBlocker(pe.getSysTag,pe.getCurrentHeight,pe.getCurrentBlockHash,this.voteIndex))
            this.cleanVoteInfo
            this.resetCandidator(pe.getCurrentBlockHash)
            this.resetBlocker(getVoteIndex, pe.getCurrentBlockHash, pe.getCurrentHeight)
          }else{
            //没有超时
          }
        }
      }else{
        //不是出块人
      }
    }else{
      if(NodeHelp.isBlocker(this.Blocker.blocker, pe.getSysTag)){
        this.zeroOfTransNumTimeout = -1
      }
      if((this.Blocker.VoteHeight +config.getBlockNumberOfRaft) <= pe.getMaxHeight4SimpleRaft){
        RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},second voter,prevheight=${this.Blocker.VoteHeight},prevvoteindex=${this.voteIndex},lh=${pe.getCurrentHeight},currentHeight=${pe.getMaxHeight4SimpleRaft}" + "~" + selfAddr))
        //val block = dataaccess.getBlock4ObjectByHeight(this.Blocker.VoteHeight +SystemProfile.getBlockNumberOfRaft)
        val blockHash = searcher.getBlockHashByHeight(this.Blocker.VoteHeight +config.getBlockNumberOfRaft)
        //if(block != null){
        if(blockHash != ""){
          //val currentblockhash = block.hashOfBlock.toStringUtf8()
          //val currentheight = block.height
          val currentblockhash = blockHash
          val currentheight = this.Blocker.VoteHeight +config.getBlockNumberOfRaft
          pe.resetTimeoutOfRaft
          this.blockTimeout = false
          this.cleanVoteInfo
          this.resetCandidator(currentblockhash)
          this.resetBlocker(getVoteIndex, currentblockhash, currentheight)
          RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},read block voter,currentHeight=${this.Blocker.VoteHeight +config.getBlockNumberOfRaft},lh=${pe.getCurrentHeight},currentHash=${currentblockhash}" + "~" + selfAddr))
        }else{
          RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},second voter in synch,lh=${pe.getCurrentHeight},currentHeight=${this.Blocker.VoteHeight +config.getBlockNumberOfRaft}" + "~" + selfAddr))
          pe.getActorRef(CFRDActorType.ActorType.synchrequester) ! StartSync(false)
        }
      }else{
        NoticeBlockerMsg
      }
    }
  }

  private def transform={
    val h = this.transformInfo.heightOfBlocker
    val hash = this.transformInfo.lastHashOfBlocker
    val block = searcher.getBlockByHeight(h)
    if(block != None){
      val currentblockhash = block.get.getHeader.hashPresent.toStringUtf8()
      val currentheight = block.get.getHeader.height
      this.voteIndex = this.transformInfo.voteIndexOfBlocker
      this.transformInfo = null
      pe.resetTimeoutOfRaft
      this.blockTimeout = false
      this.cleanVoteInfo
      this.resetCandidator(currentblockhash)
      this.resetBlocker(getVoteIndex, currentblockhash, currentheight)
      RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},transform read block voter,currentHeight=${this.Blocker.VoteHeight + config.getBlockNumberOfRaft},currentHash=${currentblockhash}" + "~" + selfAddr))
    }else{
      RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},transform second voter in synch,currentHeight=${this.Blocker.VoteHeight +config.getBlockNumberOfRaft}" + "~" + selfAddr))
      //pe.getActorRef(CFRDActorType.ActorType.synchrequester) ! SyncPreblocker(this.Blocker.blocker)
      pe.getActorRef(CFRDActorType.ActorType.synchrequester) ! StartSync(false)
    }
  }

  private def blockTimeoutOftransform={
      val currentblockhash = pe.getCurrentBlockHash
      val currentheight = pe.getCurrentHeight
      this.transformInfo = null
      pe.resetTimeoutOfRaft
      this.blockTimeout = false
      this.cleanVoteInfo
      this.resetCandidator(currentblockhash)
      this.resetBlocker(getVoteIndex, currentblockhash, currentheight)
      RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},read block voter,currentHeight=${this.Blocker.VoteHeight +config.getBlockNumberOfRaft},currentHash=${currentblockhash}" + "~" + selfAddr))
  }

  override protected def vote(isForce: Boolean,forceInfo:ForceVoteInfo): Unit = {
    if(this.Blocker.blocker == ""){
      blockerIsNull
    }else{
      blockerIsNotNull
    }
  }

  override def receive: Receive = {
    case VoteOfBlocker =>
      if (NodeHelp.isCandidateNow(pe.getSysTag, pe.getRepChainContext.getSystemCertList.getVoteList)) {
        voteMsgHandler(false,null)
      }
    case VoteOfForce=>
      voteMsgHandler(true,null)
    case TransformBlocker(preBlocker,heightOfBlocker,lastHashOfBlocker,voteIndexOfBlocker)=>
      if(!NodeHelp.isBlocker(preBlocker,pe.getSysTag)){
        this.transformInfo = TransformBlocker(preBlocker,heightOfBlocker,lastHashOfBlocker,voteIndexOfBlocker)
      }
  }

}
