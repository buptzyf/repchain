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

package rep.network.consensus.pbft.block

import akka.actor.Props
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.routing._
import rep.app.Repchain
import rep.app.conf.SystemProfile
import rep.log.RepLogger
import rep.network.autotransaction.Topic
import rep.network.base.ModuleBase
import rep.network.consensus.pbft.MsgOfPBFT
import rep.network.consensus.pbft.MsgOfPBFT.{CollectEndorsement, MsgPbftReplyOk, RequesterOfEndorsement}
import rep.proto.rc2.{Block, Event, Signature}
import rep.utils.GlobalUtils.EventType

object EndorseCollector {
  def props(name: String): Props = Props(classOf[EndorseCollector], name)
}

class EndorseCollector(moduleName: String) extends ModuleBase(moduleName) {
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

  override def receive = {
    case CollectEndorsement(block, blocker) =>
      RepLogger.debug(RepLogger.zLogger,"R: " + Repchain.nn(sender) + "->" + Repchain.nn(pe.getSysTag) + ", CollectEndorsement: " + ", " + Repchain.h4(block.getHeader.hashPresent.toStringUtf8) +","+block.getHeader.height)
      if(!pe.isSynching){
        createRouter
        if (this.block != null && this.block.getHeader.hashPresent.toStringUtf8() == block.getHeader.hashPresent.toStringUtf8()) {
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"collectioner is waiting endorse result,height=${block.getHeader.height},local height=${pe.getCurrentHeight}"))
        } else {
          if(block.getHeader.hashPrevious.toStringUtf8() == pe.getCurrentBlockHash){
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"collectioner recv endorsement,height=${block.getHeader.height},local height=${pe.getCurrentHeight}"))
            resetEndorseInfo(block, blocker)
            pe.getNodeMgr.getStableNodes.foreach(f => {
              RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"collectioner send endorsement to requester,height=${block.getHeader.height},local height=${pe.getCurrentHeight}"))
              router.route(RequesterOfEndorsement(block, blocker, f), self)
            })
          }else{
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"collectioner back out endorsement request,height=${block.getHeader.height},local height=${pe.getCurrentHeight}"))
          }
        }
      }

    case MsgPbftReplyOk(block, replies) =>
      RepLogger.debug(RepLogger.zLogger,"R: " + Repchain.nn(sender) + "->" + Repchain.nn(pe.getSysTag) + ", MsgPbftReplyOk: " + ", " + Repchain.h4(block.getHeader.hashPresent.toStringUtf8))
      if(!pe.isSynching) {
        //block不空，该块的上一个块等于最后存储的hash，背书结果的块hash跟当前发出的块hash一致
        val hash = pe.getCurrentBlockHash
        if (this.block != null)
          if (this.block.getHeader.hashPrevious.toStringUtf8 == hash)
            if (this.block.getHeader.hashPresent.toStringUtf8 == block.getHeader.hashPresent.toStringUtf8) {
              sendEvent(EventType.PUBLISH_INFO, mediator, pe.getSysTag, Topic.Endorsement, Event.Action.ENDORSEMENT)
              //this.block = this.block.withReplies(block.replies)
              mediator ! Publish(Topic.Block, new MsgOfPBFT.ConfirmedBlock(this.block, sender, replies))
              clearEndorseInfo
        }
      } else{
        RepLogger.debug(RepLogger.zLogger,pe.getSysTag + ", MsgPbftReplyOk ignored")
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"collectioner back out endorsement result,local height=${pe.getCurrentHeight}"))
      }

    case _ =>
      RepLogger.debug(RepLogger.zLogger, "R: " + Repchain.nn(sender) + "->" + Repchain.nn(pe.getSysTag) + ", EndorseCollector recv other message")
  }
}