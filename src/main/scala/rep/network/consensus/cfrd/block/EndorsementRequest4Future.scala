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
import rep.app.conf.TimePolicy
import rep.network.base.ModuleBase
import rep.protos.peer._
import akka.pattern.AskTimeoutException
import rep.network.consensus.util.BlockVerify
import rep.network.consensus.cfrd.MsgOfCFRD.{EndorsementInfo, RequesterOfEndorsement, ResendEndorseInfo, ResultFlagOfEndorse, ResultOfEndorseRequester, ResultOfEndorsed}
import rep.network.sync.SyncMsg.StartSync
import rep.log.RepLogger
import rep.log.RepTimeTracer
import rep.network.module.cfrd.CFRDActorType
import rep.utils.GlobalUtils.BlockerInfo

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

  implicit val timeout = Timeout(TimePolicy.getTimeoutEndorse.seconds)
  //private val endorsementActorName = "/user/modulemanager/endorser"
  private val endorsementActorName = "/user/modulemanager/dispatchofRecvendorsement"

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "EndorsementRequest4Future Start"))
  }

  private def ExecuteOfEndorsement(addr: Address, data: EndorsementInfo): ResultOfEndorsed = {
    try {
      val selection: ActorSelection = context.actorSelection(toAkkaUrl(addr, endorsementActorName));
      val future1 = selection ? data
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"--------ExecuteOfEndorsement waiting resultheight=${data.blc.height},local height=${pe.getCurrentHeight}"))
      Await.result(future1, timeout.duration).asInstanceOf[ResultOfEndorsed]
    } catch {
      case e: AskTimeoutException =>
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"--------ExecuteOfEndorsement timeout,height=${data.blc.height},local height=${pe.getCurrentHeight}"))
        null
      case te: TimeoutException =>
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"--------ExecuteOfEndorsement java timeout,height=${data.blc.height},local height=${pe.getCurrentHeight}"))
        null
    }
  }

  private def toAkkaUrl(addr: Address, actorName: String): String = {
    return addr.toString + "/" + actorName;
  }

  private def EndorsementVerify(block: Block, result: ResultOfEndorsed): Boolean = {
    val bb = block.clearEndorsements.toByteArray
    val ev = BlockVerify.VerifyOneEndorseOfBlock(result.endor, bb, pe.getSysTag)
    ev._1
  }

  private def handler(reqinfo: RequesterOfEndorsement) = {
    schedulerLink = clearSched()
    
      RepTimeTracer.setStartTime(pe.getSysTag, s"Endorsement-request-${moduleName}", System.currentTimeMillis(),reqinfo.blc.height,reqinfo.blc.transactions.size)
      val result = this.ExecuteOfEndorsement(reqinfo.endorer, EndorsementInfo(reqinfo.blc, reqinfo.blocker))
      if (result != null) {
        if (result.result == ResultFlagOfEndorse.success) {
          if (EndorsementVerify(reqinfo.blc, result)) {
            val re = ResultOfEndorseRequester(true, result.endor, result.BlockHash, reqinfo.endorer)
            context.parent ! re
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"--------endorsementRequest4Future, send endorsement, height=${reqinfo.blc.height},local height=${pe.getCurrentHeight} "))
          } else {
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"--------endorsementRequest4Future recv endorsement result is error, result=${result.result},height=${reqinfo.blc.height},local height=${pe.getCurrentHeight}"))
            context.parent ! ResultOfEndorseRequester(false, null, reqinfo.blc.hashOfBlock.toStringUtf8(), reqinfo.endorer)
          }
        } else {
          if (result.result == ResultFlagOfEndorse.BlockHeightError) {
            if (result.endorserOfChainInfo.height > pe.getCurrentHeight + 1) {
              //todo 需要从块缓冲判断是否启动块同步
              pe.getActorRef(CFRDActorType.ActorType.synchrequester) ! StartSync(false)
              context.parent ! ResultOfEndorseRequester(false, null, reqinfo.blc.hashOfBlock.toStringUtf8(), reqinfo.endorer)
              RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"--------endorsementRequest4Future recv endorsement result must synch,height=${reqinfo.blc.height},local height=${pe.getCurrentHeight} "))
            } else {
              /*if(isSameVoteInfo(result.endorserOfVote) && result.endorserOfVote.VoteIndex > pe.getBlocker.VoteIndex) {
                //背书时候发现背书节点的抽签的索引大于自己，更新抽签索引，发出强制抽签消息给抽签模块

              }else{*/
                context.parent ! ResendEndorseInfo(reqinfo.endorer)
                RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"--------endorsement node's height low,must resend endorsement ,height=${reqinfo.blc.height},local height=${pe.getCurrentHeight} "))
             /* }*/
            }
          }else if(result.result == ResultFlagOfEndorse.EnodrseNodeIsSynching){
            context.parent ! ResendEndorseInfo(reqinfo.endorer)
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"--------endorsement node is synching, must resend endorsement,height=${reqinfo.blc.height},local height=${pe.getCurrentHeight} "))
          }
        }
      } else {
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"--------endorsementRequest4Future recv endorsement result is null,height=${reqinfo.blc.height},local height=${pe.getCurrentHeight} "))
        //context.parent ! ResultOfEndorseRequester(false, null, reqinfo.blc.hashOfBlock.toStringUtf8(), reqinfo.endorer)
         context.parent ! ResendEndorseInfo(reqinfo.endorer)
      }
    
    RepTimeTracer.setEndTime(pe.getSysTag, s"Endorsement-request-${moduleName}", System.currentTimeMillis(),reqinfo.blc.height,reqinfo.blc.transactions.size)
  }

  private def isSameVoteInfo(voteInfoOfEndorser:BlockerInfo):Boolean={
    if(voteInfoOfEndorser.voteBlockHash == pe.getBlocker.voteBlockHash && voteInfoOfEndorser.VoteHeight == pe.getBlocker.VoteHeight) true else false
  }

  override def receive = {
    case RequesterOfEndorsement(block, blocker, addr) =>
      //待请求背书的块的上一个块的hash不等于系统最新的上一个块的hash，停止发送背书
      if(block.previousBlockHash.toStringUtf8() == pe.getCurrentBlockHash){
        handler(RequesterOfEndorsement(block, blocker, addr))
      }else{
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"--------endorsementRequest4Future back out  endorsement,prehash not equal pe.currenthash ,height=${block.height},local height=${pe.getCurrentHeight} "))
    }
    case _ => //ignore
  }
}