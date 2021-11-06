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

import akka.actor.{Address, Props}
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.routing._
import rep.app.conf.{SystemProfile, TimePolicy}
import rep.network.base.ModuleBase
import rep.network.consensus.common.MsgOfConsensus.ConfirmedBlock
import rep.network.consensus.cfrd.MsgOfCFRD.{CollectEndorsement, DelayResendEndorseInfo, RequesterOfEndorsement, ResendEndorseInfo, ResultOfEndorseRequester}
import rep.protos.peer._
import rep.utils.GlobalUtils.{BlockerInfo, EventType}
import rep.network.util.NodeHelp
import rep.network.consensus.util.BlockHelp
import rep.network.consensus.util.BlockVerify
import rep.log.RepLogger
import rep.log.RepTimeTracer
import rep.network.autotransaction.Topic
import rep.network.consensus.byzantium.ConsensusCondition

import scala.collection.mutable.ArrayBuffer

/**
 * Created by jiangbuyun on 2020/03/19.
 * 背书的收集的actor
 */

object EndorseCollector {
  def props(name: String): Props = Props(classOf[EndorseCollector], name)
}

class EndorseCollector(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.concurrent.duration._
  import scala.collection.immutable._

  private var router: Router = null
  private var block: Block = null
  private var resendTimes:Int = 0
  private var blockerIndex : Int = 0
  private var blocker: String = null
  private var recvedEndorse = new HashMap[String, Signature]()
  private var resendEndorsements = new ArrayBuffer[Address]


  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "EndorseCollector Start"))
  }

  private def createRouter = {
    if (router == null) {
      var list: Array[Routee] = new Array[Routee](SystemProfile.getVoteNodeList.size()*2)
      for (i <- 0 to SystemProfile.getVoteNodeList.size()*2 - 1) {
        var ca = context.actorOf(EndorsementRequest4Future.props("endorsementrequester" + i), "endorsementrequester" + i)
        context.watch(ca)
        list(i) = new ActorRefRoutee(ca)
      }
      val rlist: IndexedSeq[Routee] = list.toIndexedSeq
      router = Router(SmallestMailboxRoutingLogic(), rlist)
    }
  }

  private def resetEndorseInfo(block: Block, blocker: String,blockerIndex: Int) = {
    schedulerLink = clearSched()
    this.block = block
    this.blocker = blocker
    this.blockerIndex = blockerIndex
    this.resendTimes = 0
    this.recvedEndorse = this.recvedEndorse.empty
    this.resendEndorsements.clear()
  }

  private def clearEndorseInfo = {
    schedulerLink = clearSched()
    this.block = null
    this.blocker = null
    this.resendTimes = 0
    this.recvedEndorse = this.recvedEndorse.empty
    this.resendEndorsements.clear()
  }

 

  private def CheckAndFinishHandler {
    sendEvent(EventType.PUBLISH_INFO, mediator, pe.getSysTag, Topic.Endorsement, Event.Action.ENDORSEMENT)
    RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("entry collectioner check  "))
    if (ConsensusCondition.ConsensusConditionChecked(this.recvedEndorse.size + 1)) {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("collectioner package endorsement to block"))
      this.recvedEndorse.foreach(f => {
        this.block = BlockHelp.AddEndorsementToBlock(this.block, f._2)
      })
      var consensus = this.block.endorsements.toArray[Signature]
      consensus=BlockVerify.sort(consensus)
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("collectioner endorsement sort"))
      this.block = this.block.withEndorsements(consensus)
      RepTimeTracer.setEndTime(pe.getSysTag, "Endorsement", System.currentTimeMillis(),this.block.height,this.block.transactions.size)
      mediator ! Publish(Topic.Block, new ConfirmedBlock(this.block, sender))
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "collectioner endorsementt finish"))
      clearEndorseInfo
    } else {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"collectioner check is error,get size=${this.recvedEndorse.size}"))
    }
  }

  override def receive = {
    case CollectEndorsement(block, blocker,index) =>
      if(!pe.isSynching && ConsensusCondition.CheckWorkConditionOfSystem(pe.getNodeMgr.getStableNodes.size)) {
        createRouter

        if (this.block != null && this.block.hashOfBlock.toStringUtf8() == block.hashOfBlock.toStringUtf8()) {
          //需要重启背书
          if (this.blockerIndex < index && blocker == this.blocker) {
            if (block.previousBlockHash.toStringUtf8() == pe.getCurrentBlockHash) {
              RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"collectioner recv endorsement in repeat endorsement,height=${block.height},local height=${pe.getCurrentHeight}"))
              resetEndorseInfo(block, blocker, index)
              pe.getNodeMgr.getStableNodes.foreach(f => {
                RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"collectioner send endorsement to requester in repeat endorsement,height=${block.height},local height=${pe.getCurrentHeight}"))
                router.route(RequesterOfEndorsement(block, blocker, f, pe.getBlocker.VoteIndex), self)
              })
            } else {
              RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"collectioner back out endorsement request in repeat endorsement,height=${block.height},local height=${pe.getCurrentHeight}"))
            }
          }
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"collectioner is waiting endorse result in repeat endorsement,height=${block.height},local height=${pe.getCurrentHeight}"))
        } else {
          //第一次背书
          if (block.previousBlockHash.toStringUtf8() == pe.getCurrentBlockHash) {
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"collectioner recv endorsement in first endorsement,height=${block.height},local height=${pe.getCurrentHeight}"))

            resetEndorseInfo(block, blocker, index)
            pe.getNodeMgr.getStableNodes.foreach(f => {
              RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"collectioner send endorsement to requester in first endorsement,height=${block.height},local height=${pe.getCurrentHeight}"))
              router.route(RequesterOfEndorsement(block, blocker, f, pe.getBlocker.VoteIndex), self)
            })
          } else {
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"collectioner back out endorsement request in first endorsement,height=${block.height},local height=${pe.getCurrentHeight}"))
          }
        }
      }else{
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"collectioner do not send endorsement request ,reason:synch?;nodes too little,height=${block.height},local height=${pe.getCurrentHeight}"))
      }
        /*//第一次背书和重启背书采用同一逻辑
        if( block.previousBlockHash.toStringUtf8() == pe.getCurrentBlockHash){
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"collectioner recv endorsement,height=${block.height},local height=${pe.getCurrentHeight}"))
          resetEndorseInfo(block, blocker)
          pe.getNodeMgr.getStableNodes.foreach(f => {
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"collectioner send endorsement to requester,height=${block.height},local height=${pe.getCurrentHeight}"))
            router.route(RequesterOfEndorsement(block, blocker, f,pe.getBlocker.VoteIndex), self)
          })
        }else{
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"collectioner back out endorsement request,height=${block.height},local height=${pe.getCurrentHeight}"))
        }*/

        /*if (this.block != null && this.block.hashOfBlock.toStringUtf8() == block.hashOfBlock.toStringUtf8()) {
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"collectioner is waiting endorse result,height=${block.height},local height=${pe.getCurrentHeight}"))
        } else {
          if( block.previousBlockHash.toStringUtf8() == pe.getCurrentBlockHash){
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"collectioner recv endorsement,height=${block.height},local height=${pe.getCurrentHeight}"))
            resetEndorseInfo(block, blocker)
            pe.getNodeMgr.getStableNodes.foreach(f => {
              if(NodeHelp.isBlocker(pe.getSysTag, pe.getBlocker.blocker)){
                RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"collectioner send endorsement to requester,height=${block.height},local height=${pe.getCurrentHeight}"))
                router.route(RequesterOfEndorsement(block, blocker, f,pe.getBlocker.VoteIndex), self)
              }
            })
          }else{
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"collectioner back out endorsement request,height=${block.height},local height=${pe.getCurrentHeight}"))
          }
        }*/

    case ResultOfEndorseRequester(result, endors, blockhash, endorser) =>
      if(!pe.isSynching){
        //block不空，该块的上一个块等于最后存储的hash，背书结果的块hash跟当前发出的块hash一致
        if (this.block != null && this.block.previousBlockHash.toStringUtf8() == pe.getCurrentBlockHash && this.block.hashOfBlock.toStringUtf8() == blockhash) {
            if (result) {
              RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"collectioner recv endorsement result,height=${block.height},local height=${pe.getCurrentHeight}"))
              recvedEndorse += endorser.toString -> endors
              CheckAndFinishHandler
            } else {
              RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"collectioner recv endorsement result,is error,height=${block.height},local height=${pe.getCurrentHeight}"))
            }
        }else{
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"collectioner back out endorsement result,local height=${pe.getCurrentHeight}"))
        }
      }
    case ResendEndorseInfo(endorer)=>
      if(!pe.isSynching && ConsensusCondition.CheckWorkConditionOfSystem(pe.getNodeMgr.getStableNodes.size)){
        if (this.block != null && this.block.previousBlockHash.toStringUtf8() == pe.getCurrentBlockHash ) {
          if(this.router != null){
            if(this.resendTimes <= SystemProfile.getEndorseResendTimes){
              if(this.resendEndorsements.isEmpty){
                this.schedulerLink = clearSched()
                schedulerLink = scheduler.scheduleOnce(( TimePolicy.getTimeoutEndorse * 2 ).second, self, DelayResendEndorseInfo(this.block.hashOfBlock.toStringUtf8))
              }
              this.resendEndorsements += endorer
            }else{
              RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"ResendEndorseInfo endorse info,resend times eq ${this.resendTimes} ,height=${block.height},local height=${pe.getCurrentHeight}"))
            }
          }else{
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"ResendEndorseInfo collectioner's router is null,height=${block.height},local height=${pe.getCurrentHeight}"))
          }
        }else{
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"ResendEndorseInfo collectioner back out resend endorsement request,local height=${pe.getCurrentHeight}"))
        }
      }

      /*if(!pe.isSynching && ConsensusCondition.CheckWorkConditionOfSystem(pe.getNodeMgr.getStableNodes.size)){
        if(NodeHelp.isBlocker(pe.getSysTag, pe.getBlocker.blocker)){
          if (this.block != null && this.block.previousBlockHash.toStringUtf8() == pe.getCurrentBlockHash ) {
            if(this.router != null){
              if(this.resendTimes <= SystemProfile.getEndorseResendTimes){
                this.resendTimes += 1
                router.route(RequesterOfEndorsement(this.block, this.blocker, endorer), self)
              }else{
                RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"resend endorse info,resend times eq ${this.resendTimes} ,height=${block.height},local height=${pe.getCurrentHeight}"))
              }

            }else{
              RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"collectioner's router is null,height=${block.height},local height=${pe.getCurrentHeight}"))
            }
          }else{
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"collectioner back out resend endorsement request,local height=${pe.getCurrentHeight}"))
          }
        }
      }*/
    case DelayResendEndorseInfo(bHash)=>
      this.schedulerLink = clearSched()
      if(!pe.isSynching && ConsensusCondition.CheckWorkConditionOfSystem(pe.getNodeMgr.getStableNodes.size)){
        if (this.block != null && bHash == this.block.hashOfBlock.toStringUtf8 && this.block.previousBlockHash.toStringUtf8() == pe.getCurrentBlockHash ) {
          if(this.router != null){
            if(this.resendTimes <= SystemProfile.getEndorseResendTimes){
              this.resendTimes += 1
              this.resendEndorsements.foreach(addr=>{
                router.route(RequesterOfEndorsement(this.block, this.blocker, addr,pe.getBlocker.VoteIndex), self)
              })
            }else{
              RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"DelayResendEndorseInfo endorse info,resend times eq ${this.resendTimes} ,height=${block.height},local height=${pe.getCurrentHeight}"))
            }

          }else{
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"DelayResendEndorseInfo collectioner's router is null,height=${block.height},local height=${pe.getCurrentHeight}"))
          }
        }else{
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"DelayResendEndorseInfo collectioner back out resend endorsement request,local height=${pe.getCurrentHeight}"))
        }
      }
      this.resendEndorsements.clear()
    case _ => //ignore
  }
}