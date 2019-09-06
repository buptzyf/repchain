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

package rep.network.consensus.block

import akka.actor.{ Actor, ActorRef, Props, Address, ActorSelection }
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.routing._;
import rep.app.conf.{ SystemProfile, TimePolicy }
import rep.network.base.ModuleBase
import rep.network.consensus.endorse.EndorseMsg.{ RequesterOfEndorsement, ResultOfEndorseRequester, CollectEndorsement,ResendEndorseInfo }
import rep.network.consensus.block.Blocker.ConfirmedBlock
import rep.network.tools.PeerExtension
import rep.network.Topic
import rep.protos.peer._
import rep.utils.GlobalUtils.{ EventType }
import rep.utils._
import scala.collection.mutable._
import rep.network.consensus.util.BlockVerify
import scala.util.control.Breaks
import rep.network.util.NodeHelp
import rep.network.consensus.util.BlockHelp
import rep.network.consensus.util.BlockVerify
import rep.log.RepLogger
import rep.log.RepTimeTracer

object EndorseCollector {
  def props(name: String): Props = Props(classOf[EndorseCollector], name)
}

class EndorseCollector(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.concurrent.duration._
  import scala.collection.immutable._

  private var router: Router = null
  private var block: Block = null
  private var blocker: String = null
  private var recvedEndorse = new HashMap[String, Signature]()

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

  private def resetEndorseInfo(block: Block, blocker: String) = {
    this.block = block
    this.blocker = blocker
    this.recvedEndorse = this.recvedEndorse.empty
  }

  private def clearEndorseInfo = {
    this.block = null
    this.blocker = null
    this.recvedEndorse = this.recvedEndorse.empty
  }

 

  private def CheckAndFinishHandler {
    sendEvent(EventType.PUBLISH_INFO, mediator, pe.getSysTag, Topic.Endorsement, Event.Action.ENDORSEMENT)
    RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("entry collectioner check  "))
    if (NodeHelp.ConsensusConditionChecked(this.recvedEndorse.size + 1, pe.getNodeMgr.getStableNodes.size)) {
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
    case CollectEndorsement(block, blocker) =>
      if(!pe.isSynching){
        createRouter
        if (this.block != null && this.block.hashOfBlock.toStringUtf8() == block.hashOfBlock.toStringUtf8()) {
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"collectioner is waiting endorse result,height=${block.height},local height=${pe.getCurrentHeight}"))
        } else {
          if(block.previousBlockHash.toStringUtf8() == pe.getCurrentBlockHash){
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"collectioner recv endorsement,height=${block.height},local height=${pe.getCurrentHeight}"))
            resetEndorseInfo(block, blocker)
            pe.getNodeMgr.getStableNodes.foreach(f => {
              RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"collectioner send endorsement to requester,height=${block.height},local height=${pe.getCurrentHeight}"))
              router.route(RequesterOfEndorsement(block, blocker, f), self)
            })
          }else{
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"collectioner back out endorsement request,height=${block.height},local height=${pe.getCurrentHeight}"))
          }
        }
      }
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
      if(!pe.isSynching){
        if (this.block != null && this.block.previousBlockHash.toStringUtf8() == pe.getCurrentBlockHash ) {
          if(this.router != null){
            router.route(RequesterOfEndorsement(this.block, this.blocker, endorer), self)
          }else{
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"collectioner's router is null,height=${block.height},local height=${pe.getCurrentHeight}"))
          }
        }else{
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"collectioner back out resend endorsement request,local height=${pe.getCurrentHeight}"))
        }
      }
    case _ => //ignore
  }
}