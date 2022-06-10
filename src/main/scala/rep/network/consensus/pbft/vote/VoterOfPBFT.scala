//zhj

package rep.network.consensus.pbft.vote

import akka.actor.Props
import rep.log.RepLogger
import rep.network.confirmblock.pbft.ConfirmOfBlockOfPBFT
import rep.network.consensus.cfrd.MsgOfCFRD.ForceVoteInfo
import rep.network.consensus.pbft.MsgOfPBFT.{CreateBlock, VoteOfBlocker, VoteOfForce}
import rep.network.consensus.common.algorithm.{ ISequencialAlgorithmOfVote}
import rep.network.consensus.common.vote.IVoter
import rep.network.module.pbft.PBFTActorType
import rep.network.util.NodeHelp

object VoterOfPBFT{
  def props(name: String): Props = Props(classOf[VoterOfPBFT],name)
}

class VoterOfPBFT(moduleName: String) extends IVoter(moduleName: String) {
  import context.dispatcher

  import scala.concurrent.duration._

  this.algorithmInVoted = new ISequencialAlgorithmOfVote

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Vote_Logger, this.getLogMsgPrefix("VoterOfPBFT  module start"))
  }

  override protected def NoticeBlockerMsg: Unit = {
    if (this.Blocker.blocker.equals(pe.getSysTag)) {
      //发送建立新块的消息
      pe.getActorRef(PBFTActorType.ActorType.blocker) ! CreateBlock
    }
  }

  override protected def DelayVote: Unit = {
    this.voteCount += 1
    var time = this.voteCount * pe.getRepChainContext.getTimePolicy.getVoteRetryDelay
    schedulerLink = clearSched()
    schedulerLink = scheduler.scheduleOnce(pe.getRepChainContext.getTimePolicy.getVoteRetryDelay.millis, self, VoteOfBlocker("voter"))
  }

  override protected def vote(isForce: Boolean,forceInfo:ForceVoteInfo): Unit = {
    if (checkTranNum || isForce) {
      val currentblockhash = pe.getCurrentBlockHash
      val currentheight = pe.getCurrentHeight
      if (this.Blocker.voteBlockHash == "") {
        this.cleanVoteInfo
        this.resetCandidator(currentblockhash)
        this.resetBlocker(0, currentblockhash, currentheight)
        RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},first voter,blocker=${this.Blocker.blocker},voteidx=${this.Blocker.VoteIndex}" + "~" + selfAddr))
      } else {
        if (!this.Blocker.voteBlockHash.equals(currentblockhash)) {
          //抽签的基础块已经变化，需要重续选择候选人
          this.cleanVoteInfo
          this.resetCandidator(currentblockhash)
          this.resetBlocker(0, currentblockhash, currentheight)
          RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},hash change,reset voter,height=${currentheight},hash=${currentblockhash},blocker=${this.Blocker.blocker},voteidx=${this.Blocker.VoteIndex}" + "~" + selfAddr))
        } else {
          if (this.Blocker.blocker == "") {
            this.cleanVoteInfo
            this.resetCandidator(currentblockhash)
            this.resetBlocker(0, currentblockhash, currentheight)
            RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},blocker=null,reset voter,height=${currentheight},blocker=${this.Blocker.blocker},voteidx=${this.Blocker.VoteIndex}" + "~" + selfAddr))
          } else {
            if (((System.currentTimeMillis() - this.Blocker.voteTime) / 1000 > pe.getRepChainContext.getTimePolicy.getTimeOutBlock)
              ||(!pe.getNodeMgr.getStableNodeNames.contains(this.Blocker.blocker))) { //zhj
              //说明出块超时
              this.voteCount = 0
              this.resetBlocker(this.Blocker.VoteIndex + 1, currentblockhash, currentheight)
              RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},block timeout,reset voter,height=${currentheight},blocker=${this.Blocker.blocker},voteidx=${this.Blocker.VoteIndex}" + "~" + selfAddr))
            } else {
              NoticeBlockerMsg
            }
          }
        }
      }
    } else {
      RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},transaction is not enough,waiting transaction,height=${pe.getCurrentHeight}" + "~" + selfAddr))
    }
  }

  override def receive: Receive = {
    case VoteOfBlocker(flag:String) =>
      RepLogger.debug(RepLogger.zLogger,"R: " + ConfirmOfBlockOfPBFT.nn(sender) + "->" + ConfirmOfBlockOfPBFT.nn(pe.getSysTag) + ", VoteOfBlocker: " + flag)
      if (NodeHelp.isCandidateNow(pe.getSysTag, pe.getRepChainContext.getSystemCertList.getVoteList)) {
        voteMsgHandler(false,null)
      }
    case VoteOfForce=>
      RepLogger.debug(RepLogger.zLogger,"R: " + ConfirmOfBlockOfPBFT.nn(sender) + "->" + ConfirmOfBlockOfPBFT.nn(pe.getSysTag) + ", VoteOfForce: ")
      voteMsgHandler(true,null)
  }
}
