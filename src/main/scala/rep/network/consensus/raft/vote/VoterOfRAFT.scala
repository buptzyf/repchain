package rep.network.consensus.raft.vote

import akka.actor.Props
import rep.app.conf.{SystemCertList, SystemProfile, TimePolicy}
import rep.log.RepLogger
import rep.network.consensus.cfrd.MsgOfCFRD.{CreateBlock, ForceVoteInfo, VoteOfBlocker, VoteOfForce}
import rep.network.consensus.common.algorithm.IRandomAlgorithmOfVote
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

  this.algorithmInVoted = new IRandomAlgorithmOfVote
  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Vote_Logger, this.getLogMsgPrefix("VoterOfCFRD  module start"))
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

  override protected def vote(isForce: Boolean,forceInfo:ForceVoteInfo): Unit = {
    if(this.Blocker.blocker == ""){
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
        this.resetBlocker(0, currentblockhash, currentheight)
      }
      RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},first voter,currentHeight=${currentheight},currentHash=${currentblockhash}" + "~" + selfAddr))
    }else if((this.Blocker.VoteHeight +SystemProfile.getBlockNumberOfRaft) <= pe.getMaxHeight4SimpleRaft){
      RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},second voter,currentHeight=${pe.getMaxHeight4SimpleRaft}" + "~" + selfAddr))
      val block = dataaccess.getBlock4ObjectByHeight(this.Blocker.VoteHeight +SystemProfile.getBlockNumberOfRaft)
      if(block != null){
        val currentblockhash = block.hashOfBlock.toStringUtf8()
        val currentheight = block.height
        this.cleanVoteInfo
        this.resetCandidator(currentblockhash)
        this.resetBlocker(0, currentblockhash, currentheight)
        RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},read block voter,currentHeight=${this.Blocker.VoteHeight +SystemProfile.getBlockNumberOfRaft},currentHash=${currentblockhash}" + "~" + selfAddr))
      }else{
        RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},second voter in synch,currentHeight=${this.Blocker.VoteHeight +SystemProfile.getBlockNumberOfRaft}" + "~" + selfAddr))
        pe.getActorRef(CFRDActorType.ActorType.synchrequester) ! StartSync(false)
      }
    }else{
      NoticeBlockerMsg
    }
  }

  override def receive: Receive = {
    case VoteOfBlocker =>
      if (NodeHelp.isCandidateNow(pe.getSysTag, SystemCertList.getSystemCertList)) {
        voteMsgHandler(false,null)
      }
    case VoteOfForce=>
      voteMsgHandler(true,null)
  }

}
