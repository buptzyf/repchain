package rep.network.consensus.raft.vote

import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import rep.app.conf.{SystemCertList, SystemProfile, TimePolicy}
import rep.log.RepLogger
import rep.network.autotransaction.Topic
import rep.network.consensus.cfrd.MsgOfCFRD.{CreateBlock, TransformBlocker, VoteOfBlocker, VoteOfForce}
import rep.network.consensus.common.algorithm.{IRandomAlgorithmOfVote, ISequencialAlgorithmOfVote}
import rep.network.consensus.common.vote.IVoter
import rep.network.module.cfrd.CFRDActorType
import rep.network.sync.SyncMsg.StartSync
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

  private var voteIndex : Int = -1
  private var zeroOfTransNumTimeout : Long = -1
  private var zeroOfTransNumFlag:Boolean = false
  private var transformInfo:TransformBlocker = null
  this.algorithmInVoted = new ISequencialAlgorithmOfVote

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Vote_Logger, this.getLogMsgPrefix("VoterOfRAFT  module start"))
    if(SystemProfile.getVoteNodeList.contains(pe.getSysTag)){
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
    NoticeBlockerMsg
    RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},blocker=${this.Blocker.blocker},currentHeight=${pe.getMaxHeight4SimpleRaft}" + "~" + selfAddr))
  }

  override protected def DelayVote: Unit = {
    this.voteCount += 1
    var time = this.voteCount * TimePolicy.getVoteRetryDelay
    schedulerLink = clearSched()
    schedulerLink = scheduler.scheduleOnce(TimePolicy.getVoteRetryDelay.millis, self, VoteOfBlocker)
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
      this.cleanVoteInfo
      this.resetCandidator(currentblockhash)
      this.resetBlocker(getVoteIndex, currentblockhash, currentheight)
    }
    RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},first voter,currentHeight=${currentheight},currentHash=${currentblockhash}" + "~" + selfAddr))
  }

  private def blockerIsNotNull={
    if(this.transformInfo != null){
      //检查是否存在迁移出块人消息
      transform
    }else if(this.zeroOfTransNumFlag){
      //当出现交易池交易为零的标志时，检查是否已经超时，如果超时发出迁移出块人的消息，如果没有超时继续等待。
      if((System.currentTimeMillis() - this.zeroOfTransNumTimeout) > TimePolicy.getVoteWaitingDelay * 10){
        //发出迁移出块人消息
        this.zeroOfTransNumTimeout = -1
        this.zeroOfTransNumFlag = false
        mediator ! Publish(Topic.VoteTransform,  TransformBlocker(pe.getSysTag,pe.getCurrentHeight,pe.getCurrentBlockHash,this.voteIndex))
        this.cleanVoteInfo
        this.resetCandidator(pe.getCurrentBlockHash)
        this.resetBlocker(getVoteIndex, pe.getCurrentBlockHash, pe.getCurrentHeight)
      }
    }else if(pe.getTransPoolMgr.getTransLength() <= 0 && NodeHelp.isBlocker(this.Blocker.blocker, pe.getSysTag)){
      //设置交易池信息为零的标志，等待超时
      this.zeroOfTransNumTimeout = System.currentTimeMillis()
      this.zeroOfTransNumFlag = true
    }else{
      if((this.Blocker.VoteHeight +SystemProfile.getBlockNumberOfRaft) <= pe.getMaxHeight4SimpleRaft){
        RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},second voter,currentHeight=${pe.getMaxHeight4SimpleRaft}" + "~" + selfAddr))
        val block = dataaccess.getBlock4ObjectByHeight(this.Blocker.VoteHeight +SystemProfile.getBlockNumberOfRaft)
        if(block != null){
          val currentblockhash = block.hashOfBlock.toStringUtf8()
          val currentheight = block.height
          this.cleanVoteInfo
          this.resetCandidator(currentblockhash)
          this.resetBlocker(getVoteIndex, currentblockhash, currentheight)
          RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},read block voter,currentHeight=${this.Blocker.VoteHeight +SystemProfile.getBlockNumberOfRaft},currentHash=${currentblockhash}" + "~" + selfAddr))
        }else{
          RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},second voter in synch,currentHeight=${this.Blocker.VoteHeight +SystemProfile.getBlockNumberOfRaft}" + "~" + selfAddr))
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
    val block = dataaccess.getBlock4ObjectByHeight(h)
    if(block != null){
      val currentblockhash = block.hashOfBlock.toStringUtf8()
      val currentheight = block.height
      this.voteIndex = this.transformInfo.voteIndexOfBlocker
      this.transformInfo = null
      this.cleanVoteInfo
      this.resetCandidator(currentblockhash)
      this.resetBlocker(getVoteIndex, currentblockhash, currentheight)
      RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},read block voter,currentHeight=${this.Blocker.VoteHeight +SystemProfile.getBlockNumberOfRaft},currentHash=${currentblockhash}" + "~" + selfAddr))
    }else{
      RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},second voter in synch,currentHeight=${this.Blocker.VoteHeight +SystemProfile.getBlockNumberOfRaft}" + "~" + selfAddr))
      pe.getActorRef(CFRDActorType.ActorType.synchrequester) ! StartSync(false)
    }
  }

  override protected def vote(isForce: Boolean): Unit = {
    if(this.Blocker.blocker == ""){
      blockerIsNull
    }else{
      blockerIsNotNull
    }
  }

  override def receive: Receive = {
    case VoteOfBlocker =>
      if (NodeHelp.isCandidateNow(pe.getSysTag, SystemCertList.getSystemCertList)) {
        voteMsgHandler(false)
      }
    case VoteOfForce=>
      voteMsgHandler(true)
    case TransformBlocker(preBlocker,heightOfBlocker,lastHashOfBlocker,voteIndexOfBlocker)=>
      if(!NodeHelp.isBlocker(preBlocker,pe.getSysTag)){
        this.transformInfo = TransformBlocker(preBlocker,heightOfBlocker,lastHashOfBlocker,voteIndexOfBlocker)
      }
  }

}
