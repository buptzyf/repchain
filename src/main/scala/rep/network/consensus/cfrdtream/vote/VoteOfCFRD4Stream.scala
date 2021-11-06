package rep.network.consensus.cfrdtream.vote

import java.util.concurrent.ConcurrentHashMap

import akka.actor.Props
import rep.app.conf.{SystemCertList, SystemProfile, TimePolicy}
import rep.log.RepLogger
import rep.network.autotransaction.Topic
import rep.network.consensus.byzantium.ConsensusCondition
import rep.network.consensus.cfrd.MsgOfCFRD.{CreateBlock, ForceVoteInfo, VoteOfBlocker, VoteOfForce, VoteOfReset}

import scala.collection.JavaConverters._
import rep.network.consensus.cfrdtream.util.MsgOfCFRD4Stream.{JoinVoteSyncMsg, ReponseVoteSyncMsg}
import rep.network.consensus.common.algorithm.IRandomAlgorithmOfVote
import rep.network.consensus.common.vote.IVoter
import rep.network.module.cfrd.CFRDActorType
import rep.network.sync.SyncMsg.StartSync
import rep.network.util.NodeHelp
import rep.utils.GlobalUtils.BlockerInfo

object VoteOfCFRD4Stream{
  def props(name: String): Props = Props(classOf[VoteOfCFRD4Stream],name)
}

class VoteOfCFRD4Stream(moduleName: String) extends IVoter(moduleName: String) {
  import scala.concurrent.duration._
  import context.dispatcher

  this.algorithmInVoted = new IRandomAlgorithmOfVote

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Vote_Logger, this.getLogMsgPrefix("VoteOfCFRD4Stream  module start"))
    if(SystemProfile.getVoteNodeList.contains(pe.getSysTag)){
      //共识节点可以订阅交易的广播事件
      SubscribeTopic(mediator, self, selfAddr, Topic.VoteSynchronized, true)
    }
  }

  override protected def NoticeBlockerMsg: Unit = {
    if (this.Blocker.blocker.equals(pe.getSysTag)) {
      //发送建立新块的消息
      pe.getActorRef(CFRDActorType.ActorType.blocker) ! CreateBlock
    }
  }

  override protected def DelayVote: Unit = {
    if(voteCount >= 50)
      this.voteCount = 1
    else
      this.voteCount += 1
    val time = this.voteCount * TimePolicy.getVoteRetryDelay
    schedulerLink = clearSched()
    schedulerLink = scheduler.scheduleOnce(time.millis, self, VoteOfBlocker)
  }

  private def getVoteIdx(idx:Int):Int={
    var r = idx
    if(idx == Int.MaxValue){
      r = 0
    }else{
      r += 1
    }
    r
  }

  private def FirstVote={
    //第一次抽签
    this.resetCandidator(pe.getCurrentBlockHash)
    this.resetBlocker(getVoteIdx(this.Blocker.VoteIndex), pe.getCurrentBlockHash, pe.getCurrentHeight)
    RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"the first voter,height=${pe.getCurrentHeight}" + "~" + selfAddr))
  }

  override protected def vote(isForce: Boolean,forceInfo:ForceVoteInfo): Unit = {
    if(this.Blocker.blocker == ""){
      this.FirstVote
    }else{

    }
  }

  /*override protected def vote(isForce: Boolean): Unit = {
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
            if ((System.currentTimeMillis() - this.Blocker.voteTime) / 1000 > TimePolicy.getTimeOutBlock) {
              //说明出块超时
              /*if(NodeHelp.isBlocker(this.Blocker.blocker, pe.getSysTag)){
                pe.getTransPoolMgr.rollbackTransaction("identifier-"+(pe.getCurrentHeight+1))
              }*/
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
  }*/

 private def VoterOfBlocker(voteHeight:Long)={
   /*RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"Voter:prevheight=${this.Blocker.VoteHeight},prevvoteindex=${this.Blocker.VoteIndex}," +
                    s"lh=${pe.getCurrentHeight},currentHeight=${pe.getMaxHeight4SimpleRaft}" + "~" + selfAddr))
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
   }*/
 }

  private def VoteSyncHandler:ReponseVoteSyncMsg={
    if(this.Blocker.blocker != ""){
      ReponseVoteSyncMsg(pe.getSysTag,this.Blocker)
    }else{
      ReponseVoteSyncMsg(pe.getSysTag,BlockerInfo("", -1, Long.MaxValue, pe.getCurrentBlockHash, pe.getCurrentHeight))
    }
  }

  private var voteSyncInfo = new ConcurrentHashMap[String, BlockerInfo]() asScala
  private var initVoteHeight : Long = -1
  private def ResponseVoteSyncHandler(tmp:ReponseVoteSyncMsg)={
    this.voteSyncInfo.put(tmp.nodeName,tmp.voteInfo)
    if(ConsensusCondition.ConsensusConditionChecked(this.voteSyncInfo.size)){
      var infos = this.voteSyncInfo.values.toList
      val heightStatis = infos.groupBy(x => x.VoteHeight).map(x => (x._1, x._2.length)).toList.sortBy(x => -x._2)
      val maxHeight = heightStatis.head._1
      if(pe.getCurrentHeight >= maxHeight){
        //同步完成证书抽签
      }else{
        //开启同步
      }
      this.initVoteHeight = maxHeight
    }
  }

  override def receive: Receive = {
    case VoteOfBlocker =>
      if (NodeHelp.isCandidateNow(pe.getSysTag, SystemCertList.getSystemCertList)) {
        voteMsgHandler(false,null)
      }
    case JoinVoteSyncMsg =>
      if(!NodeHelp.isSameNodeForString(this.selfAddr,NodeHelp.getNodePath(sender()))) {
        val tmprvs = VoteSyncHandler
        sender() ! tmprvs
      }
    case rvs:ReponseVoteSyncMsg=>
      if(this.Blocker.blocker == "" && pe.getBlocker.blocker == "" && this.initVoteHeight < 0){
        ResponseVoteSyncHandler(rvs)
      }
    case VoteOfForce=>
      voteMsgHandler(true,null)
    case VoteOfReset=>
      cleanVoteInfo
      voteMsgHandler(true,null)


  }
}