package rep.network.consensus.cfrd.vote

import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import rep.log.RepLogger
import rep.log.httplog.AlertInfo
import rep.network.autotransaction.Topic
import rep.network.consensus.byzantium.ConsensusCondition
import rep.network.consensus.common.vote.IVoter
import rep.network.module.cfrd.CFRDActorType
import rep.network.consensus.cfrd.MsgOfCFRD.{CreateBlock, ForceVoteInfo, SpecifyVoteHeight, VoteOfBlocker, VoteOfForce, VoteOfReset}
import rep.network.consensus.cfrd.vote.VoterOfCFRD.{CheckZero, RequestWithZeroTransaction, ZeroTransactionRequests}
import rep.network.util.NodeHelp
import rep.network.consensus.common.algorithm.IRandomAlgorithmOfVote

import scala.collection.mutable.ArrayBuffer

/**
 * Created by jiangbuyun on 2020/03/17.
 * 实现CFRD抽签actor
 */

object VoterOfCFRD {
  def props(name: String): Props = Props(classOf[VoterOfCFRD], name)

  case object CheckZero

  case class RequestWithZeroTransaction(height: Long, systemName: String)

  case class ZeroTransactionRequests(height: Long, nodes: ArrayBuffer[String])

}

class VoterOfCFRD(moduleName: String) extends IVoter(moduleName: String) {

  import scala.concurrent.duration._
  import context.dispatcher

  override def preStart(): Unit = {
    //注册接收交易为空的广播
    if (pe.getRepChainContext.getConsensusNodeConfig.getVoteListOfConfig.contains(pe.getSysTag)) {
      //共识节点可以订阅交易为空的广播事件
      if (pe.getRepChainContext.getConfig.useCustomBroadcast) {
        pe.getRepChainContext.getCustomBroadcastHandler.SubscribeTopic(Topic.MessageWithZeroTransaction, "/user/modulemanager/voter")
        RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix("Subscribe custom broadcast,/user/modulemanager/voter"))
      } else {
        SubscribeTopic(mediator, self, selfAddr, Topic.MessageWithZeroTransaction, false)
        RepLogger.info(RepLogger.System_Logger,this.getLogMsgPrefix("Subscribe system broadcast,/user/modulemanager/voter"))
      }
    }
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("VoterOfCFRD module start"))
    super.preStart()
  }

  this.algorithmInVoted = new IRandomAlgorithmOfVote

  //////////////交易数为零导致无法进行抽签，广播交易数为零请求，接收到该请求的节点，
  // 获得了大于1/2的节点请求之后，如果本节点有未出块的交易，广播一条交易。
  // 如果当前节点与大于1/2节点的高度不一致，发送同步命令，进行同步
  private var schedulerOfZero: akka.actor.Cancellable = null


  private def checkZeroScheduler: Unit = {
    if (!checkTranNum) {
      if (schedulerOfZero == null) {
        this.schedulerOfZero = scheduler.scheduleWithFixedDelay(15.second,15.second, self, VoterOfCFRD.CheckZero)
        RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag}," +
          s"startup scheduler" + "~" + selfAddr))
      }
    } else {
      if (schedulerOfZero != null) {
        schedulerOfZero.cancel()
        this.schedulerOfZero = null
        RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag}," +
          s"delete scheduler" + "~" + selfAddr))
      }

    }
  }

  override protected def NoticeBlockerMsg: Unit = {
    if (this.Blocker.blocker.equals(pe.getSysTag)) {
      //发送建立新块的消息
      pe.getActorRef(CFRDActorType.ActorType.blocker) ! CreateBlock
    }
  }

  override protected def DelayVote: Unit = {
    if (voteCount >= 50)
      this.voteCount = 1
    else
      this.voteCount += 1
    val time = this.voteCount * pe.getRepChainContext.getTimePolicy.getVoteRetryDelay
    schedulerLink = clearSched()
    schedulerLink = scheduler.scheduleOnce(time.millis, self, VoteOfBlocker)
  }


  override protected def vote(isForce: Boolean, forceInfo: ForceVoteInfo): Unit = {
    checkZeroScheduler
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
            /*if ((System.currentTimeMillis() - this.Blocker.voteTime) / 1000 > TimePolicy.getTimeOutBlock) {
              //说明出块超时
              /*if(NodeHelp.isBlocker(this.Blocker.blocker, pe.getSysTag)){
                pe.getTransPoolMgr.rollbackTransaction("identifier-"+(pe.getCurrentHeight+1))
              }*/
              this.voteCount = 0
              this.resetBlocker(this.Blocker.VoteIndex + 1, currentblockhash, currentheight)
              RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},block timeout,reset voter,height=${currentheight},blocker=${this.Blocker.blocker},voteidx=${this.Blocker.VoteIndex}" + "~" + selfAddr))
            } else {
              NoticeBlockerMsg
            }*/
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
              if ((System.currentTimeMillis() - this.Blocker.voteTime) / 1000 > pe.getRepChainContext.getTimePolicy.getTimeOutBlock) {
                //说明出块超时
                RepLogger.sendAlertToDB(pe.getRepChainContext.getHttpLogger(), AlertInfo("CONSENSUS", 5, s"block timeout,reset voter,height=${currentheight},blocker=${this.Blocker.blocker},voteidx=${this.Blocker.VoteIndex}."))
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
      if (NodeHelp.isCandidateNow(pe.getSysTag, pe.getRepChainContext.getSystemCertList.getVoteList)) {
        voteMsgHandler(false, null)
      }
    case VoteOfForce =>
      voteMsgHandler(true, null)
    case VoteOfReset =>
      cleanVoteInfo
      voteMsgHandler(true, null)
    case SpecifyVoteHeight(voteinfo) =>
      voteMsgHandler(true, voteinfo)
    case CheckZero =>
      if (new ConsensusCondition(pe.getRepChainContext).CheckWorkConditionOfSystem(pe.getRepChainContext.getNodeMgr.getStableNodes.size)) {
        if (!pe.isSynching) {
          if (checkTranNum) {
            //如果产生交易了，删除定时器，什么也不做
            RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag}," +
              s"CheckZero stop" + "~" + selfAddr))
            if (schedulerOfZero != null) schedulerOfZero.cancel()
            this.schedulerOfZero = null
          } else {
            RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag}," +
              s"CheckZero ,broadcast RequestWithZeroTransaction " + "~" + selfAddr))
            pe.getRepChainContext.getCustomBroadcastHandler.PublishOfCustom(context,mediator,Topic.MessageWithZeroTransaction,RequestWithZeroTransaction(pe.getCurrentHeight, pe.getSysTag))
            //mediator ! Publish(Topic.MessageWithZeroTransaction, RequestWithZeroTransaction(pe.getCurrentHeight, pe.getSysTag))
          }
        }
      }
    case RequestWithZeroTransaction(h, sn) =>
      recvZeroTransactionHandle(VoterOfCFRD.RequestWithZeroTransaction(h, sn))
  }

  private def recvZeroTransactionHandle(zt: RequestWithZeroTransaction): Unit = {
    RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag}," +
      s"recv RequestWithZeroTransaction,height=${zt.height},requester=${zt.systemName}" + "~" + selfAddr))
    if (new ConsensusCondition(pe.getRepChainContext).CheckWorkConditionOfSystem(pe.getRepChainContext.getNodeMgr.getStableNodes.size)) {
      if (!pe.isSynching && checkTranNum) {
        RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag}," +
          s"recv RequestWithZeroTransaction,broadcast transaction,requester=${zt.systemName}" + "~" + selfAddr))
        val t = pe.getRepChainContext.getTransactionPool.getRandomTransaction
        if (t != null) {
          RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag}," +
            s"recv RequestWithZeroTransaction,get transaction ,broadcast transaction,tid=${t.id},requester=${zt.systemName}" + "~" + selfAddr))
          //mediator ! Publish(Topic.Transaction, t)
          pe.getRepChainContext.getCustomBroadcastHandler.PublishOfCustom(context,mediator,Topic.Transaction,t)
        }
      }
    }
  }
}
