package rep.network.consensus.cfrdinstream.endorse

import akka.actor.Props
import akka.util.Timeout
import rep.app.conf.{SystemCertList, SystemProfile, TimePolicy}
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.autotransaction.Topic
import rep.network.base.ModuleBase
import rep.network.consensus.cfrd.MsgOfCFRD.{EndorsementInfo, EndorsementInfoInStream, ResultFlagOfEndorse, ResultOfEndorsed}
import rep.network.consensus.cfrd.endorse.RecvEndorsInfo
import rep.network.consensus.common.MsgOfConsensus.{PreTransBlock, PreTransBlockResult}
import rep.network.consensus.util.{BlockHelp, BlockVerify}
import rep.network.module.ModuleActorType
import akka.pattern.{AskTimeoutException, ask}
import com.google.protobuf.ByteString
import rep.network.module.cfrd.CFRDActorType
import rep.network.sync.SyncMsg.StartSync
import rep.network.util.NodeHelp
import rep.storage.ImpDataPreloadMgr
import rep.utils.GlobalUtils.{BlockerInfo, EventType}

object Endorser4FutureInStream{
  def props(name: String): Props = Props(classOf[Endorser4FutureInStream], name)
}

class Endorser4FutureInStream(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.concurrent.duration._
  import rep.proto.rc2._
  import rep.storage.ImpDataAccess
  import scala.concurrent._

  implicit val timeout = Timeout((TimePolicy.getTimeoutPreload * 3).seconds)

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("Endorser4FutureInStream Start"))
  }

  private var voteinfo:BlockerInfo = null
  private var blockOfEndorement : Block = null
  private var resultOfEndorement : ResultOfEndorsed = null
  private val dbIditifier_prefix = "endors_dbidentifier_"
  private var dbtag = null


  private def AskPreloadTransactionOfBlock(block: Block): Boolean = {
    var b = false
    RepTimeTracer.setStartTime(pe.getSysTag, s"recvendorsement-${moduleName}-AskPreloadTransactionOfBlock", System.currentTimeMillis(),block.header.get.height,block.transactions.size)
    RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry 14,height=${block.header.get.height}"))
    try {
      var tmpblock = block.withHeader(block.header.get.withHashPresent(ByteString.EMPTY))
      val future1 = pe.getActorRef(ModuleActorType.ActorType.dispatchofpreloadinstream).ask(PreTransBlock(tmpblock, this.dbtag))
      val result = Await.result(future1, timeout.duration).asInstanceOf[PreTransBlockResult]
      //var tmpblock = result.blc.withHashOfBlock(block.hashOfBlock)  //
      //if (BlockVerify.VerifyHashOfBlock(tmpblock)) {
        if(block.header.get.hashPresent.toStringUtf8 == result.blc.header.get.hashPresent.toStringUtf8){
        b = true
      }else{
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry 14.5,eb-height=${block.header.get.height}," +
            s"lb-height=${pe.getCurrentHeight},cb-height=${pe.getConfirmHeight},blockhash=${block.header.get.hashPresent.toStringUtf8}," +
            s"tmpblockhash=${result.blc.header.get.hashPresent.toStringUtf8},voteheight=${voteinfo.VoteHeight},voteindex=${voteinfo.VoteIndex}"))
          ImpDataPreloadMgr.Free(pe.getSysTag, "endors_dbidentifier_"+this.voteinfo.VoteHeight+"_"+this.voteinfo.VoteIndex)
        }
    } catch {
      case e: AskTimeoutException =>
        RepLogger.error(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry AskPreloadTransactionOfBlock error=AskTimeoutException"))
      case te:TimeoutException =>
        RepLogger.error(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry AskPreloadTransactionOfBlock error=TimeoutException"))
    }finally {
      //ImpDataPreloadMgr.Free(pe.getSysTag,"endors_"+block.transactions(0).id)
    }
    RepTimeTracer.setEndTime(pe.getSysTag, s"recvendorsement-${moduleName}-AskPreloadTransactionOfBlock", System.currentTimeMillis(),block.header.get.height,block.transactions.size)
    b
  }

  private def checkEndorseSign(block: Block): Boolean = {
    //println(s"${pe.getSysTag}:entry checkEndorseSign")
    RepTimeTracer.setStartTime(pe.getSysTag, s"recvendorsement-${moduleName}-checkEndorseSign", System.currentTimeMillis(),block.header.get.height,block.transactions.size)
    var result = false
    val r = BlockVerify.VerifyAllEndorseOfBlock(block, pe.getSysTag)
    result = r._1
    //println(s"${pe.getSysTag}:entry checkEndorseSign after,checkEndorseSign=${result}")
    RepTimeTracer.setEndTime(pe.getSysTag, s"recvendorsement-${moduleName}-checkEndorseSign", System.currentTimeMillis(),block.header.get.height,block.transactions.size)
    result
  }

  private def turnVote(vi: BlockerInfo, blc: Block):Int={
    var r = -1
    if(blc.header.get.hashPrevious.toStringUtf8 == pe.getCurrentBlockHash){
      //allow
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry 20,height=${blc.header.get.height}"))
      this.voteinfo = vi
      r = 0
    }else{
      if(blc.header.get.height > (pe.getCreateHeight+1)){
        //do not allow,entry synch
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry 21,height=${blc.header.get.height}"))
        r = 2
        ImpDataPreloadMgr.Free(pe.getSysTag, "endors_dbidentifier_"+this.voteinfo.VoteHeight+"_"+this.voteinfo.VoteIndex)
        pe.getActorRef(CFRDActorType.ActorType.synchrequester) ! StartSync(false)
      }else{
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry 22,height=${blc.header.get.height}"))
        6
      }
    }
    r
  }


  private def isAcceptEndorseRequest(vi: BlockerInfo, info: EndorsementInfoInStream): Int = {
    var r = -1
    if(this.voteinfo == null){
      this.voteinfo = pe.getBlocker
      this.blockOfEndorement = null
      if(this.dbtag != null){
        ImpDataPreloadMgr.Free(pe.getSysTag, this.dbtag)
      }
    }


    if(info.blc.header.get.height >= (vi.VoteHeight + SystemProfile.getBlockNumberOfRaft)){
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry 24,height=${info.blc.header.get.height}"))
      r = 2
      pe.getActorRef(CFRDActorType.ActorType.synchrequester) ! StartSync(false)
    }else if (this.voteinfo == null) {
      r = turnVote(vi,info.blc)
    } else {
      if (!NodeHelp.IsSameVote(vi, pe.getBlocker)) {
        //已经切换出块人，初始化信息
        ImpDataPreloadMgr.Free(pe.getSysTag, "endors_dbidentifier_"+this.voteinfo.VoteHeight+"_"+this.voteinfo.VoteIndex)
        r = turnVote(vi,info.blc)
      } else {
        if (this.blockOfEndorement == null) {
          r = turnVote(vi,info.blc)
        } else {
          if(info.blc.header.get.hashPrevious.toStringUtf8 == this.blockOfEndorement.header.get.hashPresent.toStringUtf8){
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry 25,height=${info.blc.header.get.height}"))
            r = 0
          }else  if (info.blc.header.get.height == pe.getConfirmHeight+1) {
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry 26,height=${info.blc.header.get.height}"))
            r = 0
          }else{
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry 27,height=${info.blc.header.get.height}"))
            r = 6
          }
        }
      }
    }
    r
  }

  private def isAllowEndorse(info: EndorsementInfoInStream): Int = {
    if (info.blocker == pe.getSysTag) {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"endorser is itself,do not endorse,recv endorse request,endorse height=${info.blc.header.get.height},local height=${pe.getCurrentHeight}"))
      1
    } else {
      if (NodeHelp.isCandidateNow(pe.getSysTag, SystemCertList.getSystemCertList)) {
        isAcceptEndorseRequest(pe.getBlocker,info)
      } else {
        //不是候选节点，不能够参与背书
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "it is not candidator node,recv endorse request,endorse height=${info.blc.height},local height=${pe.getCurrentHeight}"))
        3
      }
    }
  }

  /*private def isAllowEndorse(info: EndorsementInfo): Int = {
    if (info.blocker == pe.getSysTag) {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"endorser is itself,do not endorse,recv endorse request,endorse height=${info.blc.height},local height=${pe.getCurrentHeight}"))
      1
    } else {
      if (NodeHelp.isCandidateNow(pe.getSysTag, SystemCertList.getSystemCertList)) {
        //是候选节点，可以背书
        //if (info.blc.previousBlockHash.toStringUtf8 == pe.getCurrentBlockHash && NodeHelp.isBlocker(info.blocker, pe.getBlocker.blocker)) {
        if (isAcceptEndorseRequest(pe.getBlocker, info.blc) && NodeHelp.isBlocker(info.blocker, pe.getBlocker.blocker)) {
          //可以进入背书
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"vote result equal，allow entry endorse,recv endorse request,endorse height=${info.blc.height},local height=${pe.getCurrentHeight}"))
          0
        } else  {
          //todo 需要判断区块缓存，再决定是否需要启动同步,并且当前没有同步才启动同步，如果已经同步，则不需要发送消息。
          if(info.blc.height > pe.getCurrentHeight+1){
            if(!pe.isSynching){
              pe.getActorRef(CFRDActorType.ActorType.synchrequester) ! StartSync(false)
            }else{
              RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"node is synchoronizing,recv endorse request,endorse height=${info.blc.height},local height=${pe.getCurrentHeight}"))
            }
            //本地正在同步
            2
          }else if(info.blc.hashOfBlock.toStringUtf8 == pe.getCurrentBlockHash){
            if(pe.getBlocker.blocker == ""){
              RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"local not vote,start vote,recv endorse request,endorse height=${info.blc.height},local height=${pe.getCurrentHeight}"))
              //pe.getActorRef(CFRDActorType.ActorType.voter) ! VoteOfForce
              //本地开始抽签
              4
            }else if(info.voteindex != pe.getBlocker.VoteIndex){
              //pe.getActorRef(CFRDActorType.ActorType.voter) ! VoteOfReset
              RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"do not endorsed,vote index not equal,reset vote,recv endorse request,endorse height=${info.blc.height},local height=${pe.getCurrentHeight}"))
              //重置抽签
              5
            }else{
              RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"do not endorsed1,unknow of reason,recv endorse request,endorse height=${info.blc.height},local height=${pe.getCurrentHeight}"))
              6
            }
          }else{
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"do not endorsed2,unknow of reason,recv endorse request,endorse height=${info.blc.height},local height=${pe.getCurrentHeight}"))
            6
          }
        }
      } else {
        //不是候选节点，不能够参与背书
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "it is not candidator node,recv endorse request,endorse height=${info.blc.height},local height=${pe.getCurrentHeight}"))
        3
      }
    }
  }*/

  private def VerifyInfo(blc: Block) = {
    RepTimeTracer.setStartTime(pe.getSysTag, s"recvendorsement-${moduleName}-VerifyInfo", System.currentTimeMillis(),blc.header.get.height,blc.transactions.size)
    var r = false
    RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry 11,height=${blc.header.get.height}"))
    if(checkEndorseSign(blc)){
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry 12,height=${blc.header.get.height}"))
      if(AskPreloadTransactionOfBlock(blc)){
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry 13,height=${blc.header.get.height}"))
        r = true
      }
    }
    RepTimeTracer.setEndTime(pe.getSysTag, s"recvendorsement-${moduleName}-VerifyInfo", System.currentTimeMillis(),blc.header.get.height,blc.transactions.size)

    r
  }



  private def SendVerifyEndorsementInfo(blc: Block,result1:Boolean) = {
    if (result1) {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry 7,height=${blc.header.get.height}"))
      sendEvent(EventType.RECEIVE_INFO, mediator, pe.getSysTag, Topic.Endorsement,Event.Action.ENDORSEMENT)
      this.resultOfEndorement =  ResultOfEndorsed(ResultFlagOfEndorse.success, BlockHelp.SignBlock(blc, pe.getSysTag),
        blc.header.get.hashPresent.toStringUtf8(),pe.getSystemCurrentChainStatus,pe.getBlocker)
      this.blockOfEndorement = blc
      sender ! this.resultOfEndorement
    } else {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry 8,eb-height=${blc.header.get.height}," +
        s"lb-height=${pe.getCurrentHeight},cb-height=${pe.getConfirmHeight},voteheight=${voteinfo.VoteHeight},voteindex=${voteinfo.VoteIndex}"))
      this.resultOfEndorement = ResultOfEndorsed(ResultFlagOfEndorse.VerifyError, null, blc.header.get.hashPresent.toStringUtf8(),
        pe.getSystemCurrentChainStatus,pe.getBlocker)
      this.blockOfEndorement = null
      sender ! this.resultOfEndorement
    }
  }



  private def isRepeatEndoresment(info: EndorsementInfo):Boolean={
    var r = false

    if(this.blockOfEndorement != null && this.resultOfEndorement != null) {
      if( info.blc.header.get.hashPresent.toStringUtf8 == this.blockOfEndorement.header.get.hashPresent.toStringUtf8){
        r = true
      }
    }
    r
  }

  private def EndorseHandler(info: EndorsementInfoInStream) = {
    val r = isAllowEndorse(info)
    RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry 9,height=${info.blc.header.get.height}"))
    /*r match {
      case 0 =>

        var result1 = true
        if (SystemProfile.getIsVerifyOfEndorsement) {
          if(this.isRepeatEndoresment(info)){
            sender ! this.resultOfEndorement
          }else{
            result1 = VerifyInfo(info.blc)
            SendVerifyEndorsementInfo(info.blc, result1)
          }
        }else{
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry 10,height=${info.blc.height}"))
          sender ! ResultOfEndorsed(ResultFlagOfEndorse.VerifyError, null, info.blc.hashOfBlock.toStringUtf8(),
            pe.getSystemCurrentChainStatus,pe.getBlocker)
        }
      case 2 =>
        //cache endorse,waiting revote
        sender ! ResultOfEndorsed(ResultFlagOfEndorse.BlockHeightError, null, info.blc.hashOfBlock.toStringUtf8(), pe.getSystemCurrentChainStatus, pe.getBlocker)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"endorse node not equal height,synching,self height=${pe.getCurrentHeight},block height=${info.blc.height}"))
      case 4=>
        sender ! ResultOfEndorsed(ResultFlagOfEndorse.EndorseNodeNotVote, null, info.blc.hashOfBlock.toStringUtf8(), pe.getSystemCurrentChainStatus, pe.getBlocker)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"do not vote,force vote,self height=${pe.getCurrentHeight},block height=${info.blc.height}"))
      case 5=>
        sender ! ResultOfEndorsed(ResultFlagOfEndorse.VoteIndexError, null, info.blc.hashOfBlock.toStringUtf8(), pe.getSystemCurrentChainStatus, pe.getBlocker)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"vote index error,reset vote,self height=${pe.getCurrentHeight},block height=${info.blc.height}"))
      case 6=>
        sender ! ResultOfEndorsed(ResultFlagOfEndorse.EndorseNodeUnkonwReason, null, info.blc.hashOfBlock.toStringUtf8(), pe.getSystemCurrentChainStatus, pe.getBlocker)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"endorsement Unknow reason,self height=${pe.getCurrentHeight},block height=${info.blc.height}"))
      case 1 =>
        //do not endorse
        sender ! ResultOfEndorsed(ResultFlagOfEndorse.BlockerSelfError, null, info.blc.hashOfBlock.toStringUtf8(), pe.getSystemCurrentChainStatus, pe.getBlocker)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"itself,recv endorse request,endorse height=${info.blc.height},local height=${pe.getCurrentHeight}"))
      case 3 =>
        //do not endorse
        sender ! ResultOfEndorsed(ResultFlagOfEndorse.CandidatorError, null, info.blc.hashOfBlock.toStringUtf8(), pe.getSystemCurrentChainStatus, pe.getBlocker)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"it is not candator,do not endorse,recv endorse request,endorse height=${info.blc.height},local height=${pe.getCurrentHeight}"))
    }*/
  }



  private def EndorseIsFinish(re:RecvEndorsInfo,info: EndorsementInfo)={
    if(re.preload.get() && re.verifyBlockSign.get() && re.checkRepeatTrans.get()==1 && re.verifyTran.get()){
      SendVerifyEndorsementInfo(info.blc, true)
    }
  }


  override def receive = {
    //Endorsement block
    case EndorsementInfoInStream(block, blocker,voteIndex,voteHeight) =>
      if(!pe.isSynching){
        RepTimeTracer.setStartTime(pe.getSysTag, s"recvendorsement-${moduleName}", System.currentTimeMillis(),block.header.get.height,block.transactions.size)
        EndorseHandler(EndorsementInfoInStream(block, blocker,voteIndex,voteHeight))
        RepTimeTracer.setEndTime(pe.getSysTag, s"recvendorsement-${moduleName}", System.currentTimeMillis(),block.header.get.height,block.transactions.size)
      }else{
        sender ! ResultOfEndorsed(ResultFlagOfEndorse.EnodrseNodeIsSynching, null, block.header.get.hashPresent.toStringUtf8(),pe.getSystemCurrentChainStatus,pe.getBlocker)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"do not endorse,it is synching,recv endorse request,endorse height=${block.header.get.height},local height=${pe.getCurrentHeight}"))
      }

    case _ => //ignore
  }

}