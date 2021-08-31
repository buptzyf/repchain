package rep.network.consensus.cfrd.vote

import akka.actor.Props
import rep.app.conf.{SystemCertList, TimePolicy}
import rep.log.RepLogger
import rep.network.consensus.common.vote.IVoter
import rep.network.module.cfrd.CFRDActorType
import rep.network.consensus.cfrd.MsgOfCFRD.{CreateBlock, ForceVoteInfo, SpecifyVoteHeight, VoteOfBlocker, VoteOfForce}
import rep.network.util.NodeHelp
import rep.network.consensus.common.algorithm.IRandomAlgorithmOfVote

/**
 * Created by jiangbuyun on 2020/03/17.
 * 实现CFRD抽签actor
 */

object VoterOfCFRD {
  def props(name: String): Props = Props(classOf[VoterOfCFRD], name)
}

class VoterOfCFRD(moduleName: String) extends IVoter(moduleName: String) {

  import scala.concurrent.duration._
  import context.dispatcher

  this.algorithmInVoted = new IRandomAlgorithmOfVote

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Vote_Logger, this.getLogMsgPrefix("VoterOfCFRD  module start"))
  }

  override protected def NoticeBlockerMsg: Unit = {
    if (this.Blocker.blocker.equals(pe.getSysTag)) {
      //发送建立新块的消息
      pe.getActorRef(CFRDActorType.ActorType.blocker) ! CreateBlock
    }
  }

  override protected def DelayVote: Unit = {
    this.voteCount += 1
    var time = this.voteCount * TimePolicy.getVoteRetryDelay
    schedulerLink = clearSched()
    schedulerLink = scheduler.scheduleOnce(TimePolicy.getVoteRetryDelay.millis, self, VoteOfBlocker)
  }

  override protected def vote(isForce: Boolean, forceInfo: ForceVoteInfo): Unit = {
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
            if (isForce && forceInfo != null) {
              if (this.Blocker.voteBlockHash.equals(forceInfo.blockHash) && this.Blocker.VoteIndex < forceInfo.voteIndex) {
                this.voteCount = 0
                this.resetBlocker(forceInfo.voteIndex, currentblockhash, currentheight)
                RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},SpecifyVoteHeight,reset voter,height=${currentheight},blocker=${this.Blocker.blocker}," +
                  s"voteidx=${this.Blocker.VoteIndex}" + "~" + selfAddr))
              } else {
                RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},SpecifyVoteHeight failed,reset voter,height=${currentheight},blocker=${this.Blocker.blocker}," +
                  s"SpecifyVoteHeight=${forceInfo.voteIndex}" + "~" + selfAddr))
              }
            } else {
              if ((System.currentTimeMillis() - this.Blocker.voteTime) / 1000 > TimePolicy.getTimeOutBlock) {
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
      }

    } else {
      RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},transaction is not enough,waiting transaction,height=${pe.getCurrentHeight}" + "~" + selfAddr))
    }
  }

  override def receive: Receive = {
    case VoteOfBlocker =>
      if (NodeHelp.isCandidateNow(pe.getSysTag, SystemCertList.getSystemCertList)) {
        voteMsgHandler(false, null)
      }
    case VoteOfForce =>
      voteMsgHandler(true, null)
    case SpecifyVoteHeight(voteinfo) =>
      voteMsgHandler(true, voteinfo)
  }
}
