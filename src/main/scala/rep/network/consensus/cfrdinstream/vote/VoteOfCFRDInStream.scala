package rep.network.consensus.cfrdinstream.vote

import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import rep.app.conf.{SystemCertList, SystemProfile, TimePolicy}
import rep.log.RepLogger
import rep.network.autotransaction.Topic
import rep.network.consensus.cfrd.MsgOfCFRD.{CreateBlock, TransformBlocker, VoteOfBlocker, VoteOfForce}
import rep.network.consensus.common.algorithm.ISequencialAlgorithmOfVote
import rep.network.consensus.common.vote.IVoter
import rep.network.module.cfrd.CFRDActorType
import rep.network.sync.SyncMsg.StartSync
import rep.network.util.NodeHelp
import rep.utils.GlobalUtils.BlockerInfo

object VoteOfCFRDInStream{
  def props(name: String): Props = Props(classOf[VoteOfCFRDInStream],name)
}

class VoteOfCFRDInStream (moduleName: String) extends IVoter(moduleName: String) {
  import context.dispatcher

  import scala.concurrent.duration._

  private var voteIndex : Int = -1
  private var zeroOfTransNumTimeout : Long = -1
  private var transformInfo:TransformBlocker = null
  private var blockTimeout : Boolean = false
  this.algorithmInVoted = new ISequencialAlgorithmOfVote

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Vote_Logger, this.getLogMsgPrefix("VoteOfCFRDInStream  module start"))
    if(SystemProfile.getVoteNodeList.contains(pe.getSysTag)){
      //共识节点可以订阅交易的广播事件
      SubscribeTopic(mediator, self, selfAddr, Topic.VoteTransform, true)
    }
  }

  override protected def NoticeBlockerMsg: Unit = {
    RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},notice"))
    if (this.Blocker.blocker.equals(pe.getSysTag)) {
      if(pe.getCreateHeight <= this.Blocker.VoteHeight+SystemProfile.getBlockNumberOfRaft){
        //发送建立新块的消息
        RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},send create block"))
        pe.getActorRef(CFRDActorType.ActorType.blocker) ! CreateBlock
      }
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
        s",transpoolcount=${pe.getTransPoolMgr.getTransLength()}" + "~" + selfAddr))
      this.zeroOfTransNumTimeout = -1
      transform
    }else if(pe.getTransPoolMgr.getTransLength() <= 0){
      if(NodeHelp.isBlocker(this.Blocker.blocker, pe.getSysTag)){
        if(this.zeroOfTransNumTimeout == -1){
          RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},set zero trans time,lblocker=${this.Blocker.blocker}," +
            s"lvoteindex=${this.voteIndex},lblockheight=${pe.getCurrentHeight}" +
            s",transpoolcount=${pe.getTransPoolMgr.getTransLength()}" + "~" + selfAddr))
          this.zeroOfTransNumTimeout = System.currentTimeMillis()
        }else{
          if((System.currentTimeMillis() - this.zeroOfTransNumTimeout) > TimePolicy.getVoteWaitingDelay * 10){
            //已经超时
            //发出迁移出块人消息
            RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},set zero trans timeout,lblocker=${this.Blocker.blocker}," +
              s"lvoteindex=${this.voteIndex},lblockheight=${pe.getCurrentHeight}" +
              s",transpoolcount=${pe.getTransPoolMgr.getTransLength()}" + "~" + selfAddr))
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
      //if(NodeHelp.isBlocker(this.Blocker.blocker, pe.getSysTag)){
        this.zeroOfTransNumTimeout = -1
      //}
      if((this.Blocker.VoteHeight +SystemProfile.getBlockNumberOfRaft) <= pe.getMaxHeight4SimpleRaft){
        RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},second voter,prevheight=${this.Blocker.VoteHeight},prevvoteindex=${this.voteIndex},lh=${pe.getCurrentHeight},currentHeight=${pe.getMaxHeight4SimpleRaft}" + "~" + selfAddr))
        val block = dataaccess.getBlock4ObjectByHeight(this.Blocker.VoteHeight +SystemProfile.getBlockNumberOfRaft)
        if(block != null){
          val currentblockhash = block.hashOfBlock.toStringUtf8()
          val currentheight = block.height
          pe.resetTimeoutOfRaft
          this.blockTimeout = false
          this.cleanVoteInfo
          this.resetCandidator(currentblockhash)
          this.resetBlocker(getVoteIndex, currentblockhash, currentheight)
          RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},read block voter,currentHeight=${this.Blocker.VoteHeight +SystemProfile.getBlockNumberOfRaft},lh=${pe.getCurrentHeight},currentHash=${currentblockhash}" + "~" + selfAddr))
        }else{
          RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},second voter in synch,lh=${pe.getCurrentHeight},currentHeight=${this.Blocker.VoteHeight +SystemProfile.getBlockNumberOfRaft}" + "~" + selfAddr))
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
      pe.resetTimeoutOfRaft
      this.blockTimeout = false
      this.cleanVoteInfo
      this.resetCandidator(currentblockhash)
      this.resetBlocker(getVoteIndex, currentblockhash, currentheight)
      RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},transform read block voter,currentHeight=${this.Blocker.VoteHeight +SystemProfile.getBlockNumberOfRaft},currentHash=${currentblockhash}" + "~" + selfAddr))
    }else{
      RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},transform second voter in synch,currentHeight=${this.Blocker.VoteHeight +SystemProfile.getBlockNumberOfRaft}" + "~" + selfAddr))
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
    RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},read block voter,currentHeight=${this.Blocker.VoteHeight +SystemProfile.getBlockNumberOfRaft},currentHash=${currentblockhash}" + "~" + selfAddr))
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