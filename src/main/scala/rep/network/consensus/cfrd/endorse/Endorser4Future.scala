/*
 * Copyright  2019 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BA SIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package rep.network.consensus.cfrd.endorse

import akka.util.Timeout
import akka.pattern.{AskTimeoutException, ask}
import akka.actor.Props
import rep.network.base.ModuleBase
import rep.network.util.NodeHelp
import rep.utils.GlobalUtils.{EventType}
import rep.network.autotransaction.Topic
import rep.network.module.ModuleActorType
import rep.network.module.cfrd.CFRDActorType
import rep.network.consensus.common.MsgOfConsensus.{PreTransBlock, PreTransBlockResult}
import rep.network.consensus.cfrd.MsgOfCFRD.{EndorsementInfo, ResultFlagOfEndorse, ResultOfEndorsed, SpecifyVoteHeight, VoteOfForce, VoteOfReset, verifyTransOfEndorsement, verifyTransPreloadOfEndorsement, verifyTransRepeatOfEndorsement}
import rep.network.consensus.util.{BlockHelp, BlockVerify}
import rep.network.sync.SyncMsg.StartSync
import rep.log.RepLogger
import rep.log.RepTimeTracer
import rep.proto.rc2.{Block, Event}

/**
 * Created by jiangbuyun on 2020/03/19.
 * 背书actor
 */

object Endorser4Future {
  def props(name: String): Props = Props(classOf[Endorser4Future], name)
}

class Endorser4Future(moduleName: String) extends ModuleBase(moduleName) {
  import scala.concurrent.duration._
  import scala.concurrent._

  implicit val timeout = Timeout((pe.getRepChainContext.getTimePolicy.getTimeoutPreload * 3).seconds)

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("Endorser4Future Start"))
  }

  private var blockOfEndorement : Block = null
  private var resultOfEndorement : ResultOfEndorsed = null


  private def AskPreloadTransactionOfBlock(block: Block): Boolean = {
    var b = false
    RepTimeTracer.setStartTime(pe.getSysTag, s"recvendorsement-${moduleName}-AskPreloadTransactionOfBlock", System.currentTimeMillis(),block.getHeader.height,block.transactions.size)
    try {
      val future1 = pe.getActorRef(ModuleActorType.ActorType.dispatchofpreload).ask(PreTransBlock(block, "endors_"+block.transactions(0).id))
      val result = Await.result(future1, timeout.duration).asInstanceOf[PreTransBlockResult]
      var tmpblock = result.blc.withHeader(result.blc.getHeader.withHashPresent(block.getHeader.hashPresent))
      if (BlockVerify.VerifyHashOfBlock(tmpblock,pe.getRepChainContext.getHashTool)) {
        b = true
      }
    } catch {
      case e: AskTimeoutException =>
        RepLogger.error(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry AskPreloadTransactionOfBlock error=AskTimeoutException"))
      case te:TimeoutException =>
        RepLogger.error(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry AskPreloadTransactionOfBlock error=TimeoutException"))
    }finally {
      pe.getRepChainContext.freeBlockPreloadInstance("endors_"+block.transactions(0).id)
    }
    RepTimeTracer.setEndTime(pe.getSysTag, s"recvendorsement-${moduleName}-AskPreloadTransactionOfBlock", System.currentTimeMillis(),block.getHeader.height,block.transactions.size)
    b
  }

  private def checkEndorseSign(block: Block): Boolean = {
    //println(s"${pe.getSysTag}:entry checkEndorseSign")
    RepTimeTracer.setStartTime(pe.getSysTag, s"recvendorsement-${moduleName}-checkEndorseSign", System.currentTimeMillis(),block.getHeader.height,block.transactions.size)
    var result = false
    val r = BlockVerify.VerifyAllEndorseOfBlock(block, pe.getRepChainContext.getSignTool)
    result = r._1
    //println(s"${pe.getSysTag}:entry checkEndorseSign after,checkEndorseSign=${result}")
    RepTimeTracer.setEndTime(pe.getSysTag, s"recvendorsement-${moduleName}-checkEndorseSign", System.currentTimeMillis(),block.getHeader.height,block.transactions.size)
    result
  }

  private def isAllowEndorse(info: EndorsementInfo): Int = {
    //if (info.blocker == pe.getSysTag) {
    if (info.blocker.blocker == pe.getSysTag) {
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"endorser is itself,do not endorse,recv endorse request,endorse height=${info.blc.getHeader.height},local height=${pe.getCurrentHeight}"))
        1
      } else {
        if (NodeHelp.isCandidateNow(pe.getSysTag, pe.getRepChainContext.getSystemCertList.getVoteList)) {
          //是候选节点，可以背书
          //if (info.blc.previousBlockHash.toStringUtf8 == pe.getCurrentBlockHash && NodeHelp.isBlocker(info.blocker, pe.getBlocker.blocker)) {
          //if (info.blc.previousBlockHash.toStringUtf8 == pe.getBlocker.voteBlockHash && NodeHelp.isBlocker(info.blocker, pe.getBlocker.blocker)) {
          if (info.blc.getHeader.hashPrevious.toStringUtf8 == pe.getBlocker.voteBlockHash && NodeHelp.isBlocker(info.blocker.blocker, pe.getBlocker.blocker)) {
            //可以进入背书
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"vote result equal，allow entry endorse,recv endorse request,endorse height=${info.blc.getHeader.height},local height=${pe.getCurrentHeight}"))
            0
          } else  {
            //todo 需要判断区块缓存，再决定是否需要启动同步,并且当前没有同步才启动同步，如果已经同步，则不需要发送消息。
            if(info.blc.getHeader.height > pe.getCurrentHeight+1){
              if(!pe.isSynching){
                pe.getActorRef(CFRDActorType.ActorType.synchrequester) ! StartSync(false)
              }else if(info.blocker.blockHeight == pe.getBlocker.VoteHeight && info.blocker.blockHash == pe.getBlocker.voteBlockHash && info.blocker.voteIndex > pe.getBlocker.VoteIndex){
                RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"vote index not equal，,endorse height=${info.blc.getHeader.height},local height=${pe.getCurrentHeight},blocker index=${info.blocker.voteIndex},local index=${pe.getBlocker.VoteIndex}"))
                pe.getActorRef(CFRDActorType.ActorType.voter) ! SpecifyVoteHeight(info.blocker)
              } else{
                RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"node is synchoronizing,recv endorse request,endorse height=${info.blc.getHeader.height},local height=${pe.getCurrentHeight}"))
              }
              //本地正在同步
              2
            }else if(info.blc.getHeader.hashPresent.toStringUtf8 == pe.getCurrentBlockHash){
              if(pe.getBlocker.blocker == ""){
                RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"local not vote,start vote,recv endorse request,endorse height=${info.blc.getHeader.height},local height=${pe.getCurrentHeight}"))
                pe.getActorRef(CFRDActorType.ActorType.voter) ! VoteOfForce
                //本地开始抽签
                4
              }else if(info.blocker.voteIndex != pe.getBlocker.VoteIndex){
                pe.getActorRef(CFRDActorType.ActorType.voter) ! VoteOfReset
                RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"do not endorsed,vote index not equal,reset vote,recv endorse request,endorse height=${info.blc.getHeader.height},local height=${pe.getCurrentHeight}"))
                //重置抽签
                5
              }else{
                RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"do not endorsed,unknow of reason,recv endorse request,endorse height=${info.blc.getHeader.height},local height=${pe.getCurrentHeight}"))
                6
              }
            }else{
              RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"do not endorsed,unknow of reason,recv endorse request,endorse height=${info.blc.getHeader.height},local height=${pe.getCurrentHeight}"))
              6
            }
          }
        } else {
          //不是候选节点，不能够参与背书
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "it is not candidator node,recv endorse request,endorse height=${info.blc.height},local height=${pe.getCurrentHeight}"))
          3
        }
      }
  }

  private def VerifyInfo(blc: Block) = {
    RepTimeTracer.setStartTime(pe.getSysTag, s"recvendorsement-${moduleName}-VerifyInfo", System.currentTimeMillis(),blc.getHeader.height,blc.transactions.size)
    var r = false
    if(checkEndorseSign(blc)){
      if(AskPreloadTransactionOfBlock(blc)){
        r = true
      }
    }
    RepTimeTracer.setEndTime(pe.getSysTag, s"recvendorsement-${moduleName}-VerifyInfo", System.currentTimeMillis(),blc.getHeader.height,blc.transactions.size)

    r
  }



  private def SendVerifyEndorsementInfo(blc: Block,result1:Boolean) = {
    if (result1) {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry 7"))
      sendEvent(EventType.RECEIVE_INFO, mediator, pe.getSysTag, Topic.Endorsement,Event.Action.ENDORSEMENT)
      this.resultOfEndorement =  ResultOfEndorsed(ResultFlagOfEndorse.success, BlockHelp.SignBlock(blc.getHeader,pe.getSysTag, pe.getRepChainContext.getSignTool),
        blc.getHeader.hashPresent.toStringUtf8(),pe.getSystemCurrentChainStatus,pe.getBlocker)
      this.blockOfEndorement = blc
      sender ! this.resultOfEndorement
    } else {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry 8"))
      this.resultOfEndorement = ResultOfEndorsed(ResultFlagOfEndorse.VerifyError, null, blc.getHeader.hashPresent.toStringUtf8(),
        pe.getSystemCurrentChainStatus,pe.getBlocker)
        this.blockOfEndorement = blc
        sender ! this.resultOfEndorement
    }
  }



  private def isRepeatEndorsement(info: EndorsementInfo):Boolean={
    var r = false
    if(this.blockOfEndorement != null && this.resultOfEndorement != null) {
      if( info.blc.getHeader.hashPresent.toStringUtf8 == this.blockOfEndorement.getHeader.hashPresent.toStringUtf8
        && info.blocker.blocker == this.resultOfEndorement.endorserOfVote.blocker){
        r = true
      }else{
        this.resultOfEndorement = null
        this.blockOfEndorement = null
      }
    }
    r
  }

  private def EndorseHandler(info: EndorsementInfo) = {
    val r = isAllowEndorse(info)
    r match {
      case 0 =>

        var result1 = true
        if (pe.getRepChainContext.getConfig.isVerifyOfEndorsement) {
          if(this.isRepeatEndorsement(info)){
            sender ! this.resultOfEndorement
          }else{
            result1 = VerifyInfo(info.blc)
            SendVerifyEndorsementInfo(info.blc, result1)
          }
        }else{
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry 8"))
          sender ! ResultOfEndorsed(ResultFlagOfEndorse.VerifyError, null, info.blc.getHeader.hashPresent.toStringUtf8(),
            pe.getSystemCurrentChainStatus,pe.getBlocker)
        }
      case 2 =>
        //cache endorse,waiting revote
        sender ! ResultOfEndorsed(ResultFlagOfEndorse.BlockHeightError, null, info.blc.getHeader.hashPresent.toStringUtf8(), pe.getSystemCurrentChainStatus, pe.getBlocker)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"endorse node not equal height,synching,self height=${pe.getCurrentHeight},block height=${info.blc.getHeader.height}"))
      case 4=>
        sender ! ResultOfEndorsed(ResultFlagOfEndorse.EndorseNodeNotVote, null, info.blc.getHeader.hashPresent.toStringUtf8(), pe.getSystemCurrentChainStatus, pe.getBlocker)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"do not vote,force vote,self height=${pe.getCurrentHeight},block height=${info.blc.getHeader.height}"))
      case 5=>
        sender ! ResultOfEndorsed(ResultFlagOfEndorse.VoteIndexError, null, info.blc.getHeader.hashPresent.toStringUtf8(), pe.getSystemCurrentChainStatus, pe.getBlocker)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"vote index error,reset vote,self height=${pe.getCurrentHeight},block height=${info.blc.getHeader.height}"))
      case 6=>
        sender ! ResultOfEndorsed(ResultFlagOfEndorse.EndorseNodeUnkonwReason, null, info.blc.getHeader.hashPresent.toStringUtf8(), pe.getSystemCurrentChainStatus, pe.getBlocker)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"endorsement Unknow reason,self height=${pe.getCurrentHeight},block height=${info.blc.getHeader.height}"))
      case 1 =>
        //do not endorse
        sender ! ResultOfEndorsed(ResultFlagOfEndorse.BlockerSelfError, null, info.blc.getHeader.hashPresent.toStringUtf8(), pe.getSystemCurrentChainStatus, pe.getBlocker)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"itself,recv endorse request,endorse height=${info.blc.getHeader.height},local height=${pe.getCurrentHeight}"))
      case 3 =>
        //do not endorse
        sender ! ResultOfEndorsed(ResultFlagOfEndorse.CandidatorError, null, info.blc.getHeader.hashPresent.toStringUtf8(), pe.getSystemCurrentChainStatus, pe.getBlocker)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"it is not candator,do not endorse,recv endorse request,endorse height=${info.blc.getHeader.height},local height=${pe.getCurrentHeight}"))
    }
  }



  private def EndorseIsFinish(re:RecvEndorsInfo,info: EndorsementInfo)={
    if(re.preload.get() && re.verifyBlockSign.get() && re.checkRepeatTrans.get()==1 && re.verifyTran.get()){
      SendVerifyEndorsementInfo(info.blc, true)
    }
  }


  override def receive = {
    //Endorsement block
    //case EndorsementInfo(block, blocker,voteindex) =>
    case EndorsementInfo(block, blocker) =>
      if(!pe.isSynching){
        RepTimeTracer.setStartTime(pe.getSysTag, s"recvendorsement-${moduleName}", System.currentTimeMillis(),block.getHeader.height,block.transactions.size)
        //EndorseHandler(EndorsementInfo(block, blocker,voteindex))
        EndorseHandler(EndorsementInfo(block, blocker))
        RepTimeTracer.setEndTime(pe.getSysTag, s"recvendorsement-${moduleName}", System.currentTimeMillis(),block.getHeader.height,block.transactions.size)
      }else{
        sender ! ResultOfEndorsed(ResultFlagOfEndorse.EnodrseNodeIsSynching, null, block.getHeader.hashPresent.toStringUtf8(),pe.getSystemCurrentChainStatus,pe.getBlocker)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"do not endorse,it is synching,recv endorse request,endorse height=${block.getHeader.height},local height=${pe.getCurrentHeight}"))
      }

     /* case verifyTransOfEndorsement(block, blocker) =>
        if(!pe.isSynching){
          RepTimeTracer.setStartTime(pe.getSysTag, s"recvendorsement-verifyTransOfEndorsementOfOp-${moduleName}", System.currentTimeMillis(),block.height,block.transactions.size)
          verifyTransOfEndorsementOfOp(EndorsementInfo(block, blocker))
          RepTimeTracer.setEndTime(pe.getSysTag, s"recvendorsement-verifyTransOfEndorsementOfOp-${moduleName}", System.currentTimeMillis(),block.height,block.transactions.size)
        }else{
          sender ! ResultOfEndorsed(ResultFlagOfEndorse.EnodrseNodeIsSynching, null, block.hashOfBlock.toStringUtf8(),pe.getSystemCurrentChainStatus,pe.getBlocker)
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"do not endorse,it is synching,recv endorse request,endorse height=${block.height},local height=${pe.getCurrentHeight}"))
        }

      case verifyTransRepeatOfEndorsement(block, blocker) =>
        if(!pe.isSynching){
          RepTimeTracer.setStartTime(pe.getSysTag, s"recvendorsement-verifyTransRepeatOfEndorsementOfOp-${moduleName}", System.currentTimeMillis(),block.height,block.transactions.size)
          verifyTransRepeatOfEndorsementOfOp(EndorsementInfo(block, blocker))
          RepTimeTracer.setEndTime(pe.getSysTag, s"recvendorsement-verifyTransRepeatOfEndorsementOfOp-${moduleName}", System.currentTimeMillis(),block.height,block.transactions.size)
        }else{
          sender ! ResultOfEndorsed(ResultFlagOfEndorse.EnodrseNodeIsSynching, null, block.hashOfBlock.toStringUtf8(),pe.getSystemCurrentChainStatus,pe.getBlocker)
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"do not endorse,it is synching,recv endorse request,endorse height=${block.height},local height=${pe.getCurrentHeight}"))
        }
      case verifyTransPreloadOfEndorsement(block, blocker) =>
        if(!pe.isSynching){
          RepTimeTracer.setStartTime(pe.getSysTag, s"recvendorsement-verifyTransPreloadOfEndorsementOfOp-${moduleName}", System.currentTimeMillis(),block.height,block.transactions.size)
          verifyTransPreloadOfEndorsementOfOp(EndorsementInfo(block, blocker))
          RepTimeTracer.setEndTime(pe.getSysTag, s"recvendorsement-verifyTransPreloadOfEndorsementOfOp-${moduleName}", System.currentTimeMillis(),block.height,block.transactions.size)
        }else{
          sender ! ResultOfEndorsed(ResultFlagOfEndorse.EnodrseNodeIsSynching, null, block.hashOfBlock.toStringUtf8(),pe.getSystemCurrentChainStatus,pe.getBlocker)
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"do not endorse,it is synching,recv endorse request,endorse height=${block.height},local height=${pe.getCurrentHeight}"))
        }*/

    case _ => //ignore
  }

}