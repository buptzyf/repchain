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

package rep.network.consensus.cfrd.block

import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent._
import akka.actor.{ActorSelection, Address, Props}
import rep.network.base.ModuleBase
import akka.pattern.AskTimeoutException
import rep.network.consensus.util.BlockVerify
import rep.network.consensus.cfrd.MsgOfCFRD.{EndorsementInfo, RequesterOfEndorsement, ResendEndorseInfo, ResultFlagOfEndorse, ResultOfEndorseRequester, ResultOfEndorsed, VoteOfReset}
import rep.network.sync.SyncMsg.StartSync
import rep.log.RepLogger
import rep.log.RepTimeTracer
import rep.network.module.cfrd.CFRDActorType
import rep.network.util.NodeHelp
import rep.proto.rc2.Block

/**
 * Created by jiangbuyun on 2020/03/19.
 * 背书请求的actor
 */

object EndorsementRequest4Future {
  def props(name: String): Props = Props(classOf[EndorsementRequest4Future], name)
}

class EndorsementRequest4Future(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.concurrent.duration._

  implicit val timeout = Timeout((pe.getRepChainContext.getTimePolicy.getTimeoutEndorse* 4).seconds)
  //private val endorsementActorName = "/user/modulemanager/endorser"
  private val endorsementActorName = "/user/modulemanager/dispatchofRecvendorsement"

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "EndorsementRequest4Future Start"))
  }

  private def ExecuteOfEndorsement(addr: Address, data: EndorsementInfo): ResultOfEndorsed = {
    try {
      val selection: ActorSelection = context.actorSelection(toAkkaUrl(addr, endorsementActorName));
      val future1 = selection ? data
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"--------ExecuteOfEndorsement waiting resultheight=${data.blc.getHeader.height},local height=${pe.getCurrentHeight}"))
      Await.result(future1, timeout.duration).asInstanceOf[ResultOfEndorsed]
    } catch {
      case e: AskTimeoutException =>
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"--------ExecuteOfEndorsement timeout,height=${data.blc.getHeader.height},local height=${pe.getCurrentHeight}"))
        null
      case te: TimeoutException =>
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"--------ExecuteOfEndorsement java timeout,height=${data.blc.getHeader.height},local height=${pe.getCurrentHeight}"))
        null
    }
  }

  private def toAkkaUrl(addr: Address, actorName: String): String = {
    return addr.toString + "/" + actorName;
  }

  private def EndorsementVerify(block: Block, result: ResultOfEndorsed): Boolean = {
    val bb = block.getHeader.clearEndorsements.toByteArray
    val ev = BlockVerify.VerifyOneEndorseOfBlock(result.endor, bb, pe.getRepChainContext.getSignTool)
    ev._1
  }

  private def handler(reqinfo: RequesterOfEndorsement) = {
    schedulerLink = clearSched()
    
      RepTimeTracer.setStartTime(pe.getSysTag, s"Endorsement-request-${moduleName}", System.currentTimeMillis(),reqinfo.blc.getHeader.height,reqinfo.blc.transactions.size)
      //val result = this.ExecuteOfEndorsement(reqinfo.endorer, EndorsementInfo(reqinfo.blc, reqinfo.blocker,reqinfo.voteindex))
      val result = this.ExecuteOfEndorsement(reqinfo.endorer, EndorsementInfo(reqinfo.blc, reqinfo.blocker))
      if (result != null) {
        if (result.result == ResultFlagOfEndorse.success) {
          if (EndorsementVerify(reqinfo.blc, result)) {
            val re = ResultOfEndorseRequester(true, result.endor, result.BlockHash, reqinfo.endorer)
            context.parent ! re
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"--------endorsementRequest4Future, send endorsement, height=${reqinfo.blc.getHeader.height},local height=${pe.getCurrentHeight} "))
          } else {
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"--------endorsementRequest4Future recv endorsement result is error, result=${result.result},height=${reqinfo.blc.getHeader.height},local height=${pe.getCurrentHeight}"))
            context.parent ! ResultOfEndorseRequester(false, null, reqinfo.blc.getHeader.hashPresent.toStringUtf8(), reqinfo.endorer)
          }
        } else {
          if (result.result == ResultFlagOfEndorse.BlockHeightError) {
            if (result.endorserOfChainInfo.height > pe.getCurrentHeight + 1) {
              //todo 需要从块缓冲判断是否启动块同步
              pe.getActorRef(CFRDActorType.ActorType.synchrequester) ! StartSync(false)
              context.parent ! ResultOfEndorseRequester(false, null, reqinfo.blc.getHeader.hashPresent.toStringUtf8(), reqinfo.endorer)
              RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"--------endorsementRequest4Future recv endorsement result must synch,height=${reqinfo.blc.getHeader.height},local height=${pe.getCurrentHeight} "))
            } else {
               context.parent ! ResendEndorseInfo(reqinfo.endorer)
               RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"--------endorsement node's height low,must resend endorsement ,height=${reqinfo.blc.getHeader.height},local height=${pe.getCurrentHeight} "))
            }
          }else if(result.result == ResultFlagOfEndorse.EnodrseNodeIsSynching || result.result == ResultFlagOfEndorse.EndorseNodeNotVote){
            context.parent ! ResendEndorseInfo(reqinfo.endorer)
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"--------endorsement node is synching, must resend endorsement,height=${reqinfo.blc.getHeader.height},local height=${pe.getCurrentHeight} "))
          }else if(result.result == ResultFlagOfEndorse.VoteIndexError){
            pe.getActorRef(CFRDActorType.ActorType.voter) ! VoteOfReset
          }else if(result.result == ResultFlagOfEndorse.EndorseNodeUnkonwReason){
            context.parent ! ResendEndorseInfo(reqinfo.endorer)
          }
        }
      } else {
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"--------endorsementRequest4Future recv endorsement result is null,height=${reqinfo.blc.getHeader.height},local height=${pe.getCurrentHeight} "))
        //context.parent ! ResultOfEndorseRequester(false, null, reqinfo.blc.hashOfBlock.toStringUtf8(), reqinfo.endorer)
         context.parent ! ResendEndorseInfo(reqinfo.endorer)
      }
    
    RepTimeTracer.setEndTime(pe.getSysTag, s"Endorsement-request-${moduleName}", System.currentTimeMillis(),reqinfo.blc.getHeader.height,reqinfo.blc.transactions.size)
  }


  override def receive = {
    //case RequesterOfEndorsement(block, blocker, addr,voteindex) =>
    case RequesterOfEndorsement(block, blocker, addr) =>
      //待请求背书的块的上一个块的hash不等于系统最新的上一个块的hash，停止发送背书
      if(NodeHelp.isBlocker(pe.getSysTag, pe.getBlocker.blocker)){
        if(block.getHeader.hashPrevious.toStringUtf8() == pe.getCurrentBlockHash){
          //handler(RequesterOfEndorsement(block, blocker, addr,voteindex))
          handler(RequesterOfEndorsement(block, blocker, addr))
        }else{
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"--------endorsementRequest4Future back out  endorsement,prehash not equal pe.currenthash ,height=${block.getHeader.height},local height=${pe.getCurrentHeight} "))
        }
      }
    case _ => //ignore
  }
}