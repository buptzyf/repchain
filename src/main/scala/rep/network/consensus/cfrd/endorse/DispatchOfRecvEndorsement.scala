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

import akka.actor.{Actor, ActorRef, ActorSelection, Address, Props}
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.routing._
import rep.app.conf.{SystemCertList, SystemProfile, TimePolicy}
import rep.network.base.ModuleBase
import rep.network.tools.PeerExtension
import rep.protos.peer._
import rep.utils.GlobalUtils.EventType
import rep.utils._

import scala.collection.mutable._
import rep.network.consensus.util.BlockVerify

import scala.util.control.Breaks
import rep.network.util.NodeHelp
import rep.network.consensus.util.BlockHelp
import rep.network.consensus.util.BlockVerify
import rep.log.RepLogger
import rep.log.RepTimeTracer
import rep.network.autotransaction.Topic
import rep.network.consensus.cfrd.MsgOfCFRD.{EndorsementInfo, ResultFlagOfEndorse, ResultOfEndorsed, verifyTransOfEndorsement, verifyTransPreloadOfEndorsement, verifyTransRepeatOfEndorsement}
import rep.network.module.cfrd.CFRDActorType
import rep.network.sync.SyncMsg.StartSync
import akka.util.ByteString

/**
 * Created by jiangbuyun on 2020/03/19.
 * 接收并分派背书请求actor
 */

object DispatchOfRecvEndorsement {
  def props(name: String): Props = Props(classOf[DispatchOfRecvEndorsement], name)
}


class DispatchOfRecvEndorsement(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.concurrent.duration._
  import scala.collection.immutable._

  private var router: Router = null

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "DispatchOfRecvEndorsement Start"))
  }

  private def createRouter = {
    if (router == null) {
      var list: Array[Routee] = new Array[Routee](SystemProfile.getVoteNodeList.size())
      for (i <- 0 to SystemProfile.getVoteNodeList.size() - 1) {
        var ca = context.actorOf(Endorser4Future.props("endorser" + i), "endorser" + i)
        context.watch(ca)
        list(i) = new ActorRefRoutee(ca)
      }
      val rlist: IndexedSeq[Routee] = list.toIndexedSeq
      router = Router(SmallestMailboxRoutingLogic(), rlist)
    }
  }

 /* private def isAllowEndorse(info: EndorsementInfo): Int = {
    if (info.blocker == pe.getSysTag) {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"DispatchOfRecvEndorsement is itself,do not endorse,recv endorse request,endorse height=${info.blc.height},local height=${pe.getCurrentHeight}"))
      1
    } else {
      if (NodeHelp.isCandidateNow(pe.getSysTag, SystemCertList.getSystemCertList)) {
        //是候选节点，可以背书
        //if (info.blc.previousBlockHash.toStringUtf8 == pe.getCurrentBlockHash && NodeHelp.isBlocker(info.blocker, pe.getBlocker.blocker)) {
        if (info.blc.previousBlockHash.toStringUtf8 == pe.getBlocker.voteBlockHash && NodeHelp.isBlocker(info.blocker, pe.getBlocker.blocker)) {
          //可以进入背书
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"DispatchOfRecvEndorsement，vote result equal，allow entry endorse,recv endorse request,endorse height=${info.blc.height},local height=${pe.getCurrentHeight}"))
          0
        } else  {
          //todo 需要判断区块缓存，再决定是否需要启动同步,并且当前没有同步才启动同步，如果已经同步，则不需要发送消息。
          if(info.blc.height > pe.getCurrentHeight+1 && !pe.isSynching){
            pe.getActorRef(CFRDActorType.ActorType.synchrequester) ! StartSync(false)
          }
          /*else if(info.blc.height > pe.getCurrentHeight+1){
            pe.getActorRef(CFRDActorType.ActorType.voter) ! VoteOfForce
          }*/
          //当前块hash和抽签的出块人都不一致，暂时不能够进行背书，可以进行缓存
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"DispatchOfRecvEndorsement，block hash is not equal or blocker is not equal,recv endorse request,endorse height=${info.blc.height},local height=${pe.getCurrentHeight}"))
          2
        }
      } else {
        //不是候选节点，不能够参与背书
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "DispatchOfRecvEndorsement，it is not candidator node,recv endorse request,endorse height=${info.blc.height},local height=${pe.getCurrentHeight}"))
        3
      }
    }
  }

  private def checkEndorseSign(block: Block): Boolean = {
    //println(s"${pe.getSysTag}:entry checkEndorseSign")
    RepTimeTracer.setStartTime(pe.getSysTag, s"recvendorsement-${moduleName}-checkEndorseSign", System.currentTimeMillis(),block.height,block.transactions.size)
    var result = false
    val r = BlockVerify.VerifyAllEndorseOfBlock(block, pe.getSysTag)
    result = r._1
    //println(s"${pe.getSysTag}:entry checkEndorseSign after,checkEndorseSign=${result}")
    RepTimeTracer.setEndTime(pe.getSysTag, s"recvendorsement-${moduleName}-checkEndorseSign", System.currentTimeMillis(),block.height,block.transactions.size)
    result
  }

  private def EndorseHandler(info: EndorsementInfo) = {
    val r = isAllowEndorse(info)
    r match {
      case 0 =>
        if(checkEndorseSign(info.blc)){
          var tmp = pe.getCurrentEndorseInfo
          tmp.clean
          tmp.block = info.blc
          tmp.verifyBlockSign.set(true)
          router.route(verifyTransOfEndorsement(info.blc, info.blocker), sender)
          router.route(verifyTransRepeatOfEndorsement(info.blc, info.blocker), sender)
          router.route(verifyTransPreloadOfEndorsement(info.blc, info.blocker), sender)
        }else{
          sender ! ResultOfEndorsed(ResultFlagOfEndorse.VerifyError, null, info.blc.hashOfBlock.toStringUtf8(), pe.getSystemCurrentChainStatus, pe.getBlocker)
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"DispatchOfRecvEndorsement，blocker sign verify error,self height=${pe.getCurrentHeight},block height=${info.blc.height}"))
        }
      case 2 =>
        //cache endorse,waiting revote
        sender ! ResultOfEndorsed(ResultFlagOfEndorse.BlockHeightError, null, info.blc.hashOfBlock.toStringUtf8(), pe.getSystemCurrentChainStatus, pe.getBlocker)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"DispatchOfRecvEndorsement，endorsement entry cache,self height=${pe.getCurrentHeight},block height=${info.blc.height}"))
      case 1 =>
        //do not endorse
        sender ! ResultOfEndorsed(ResultFlagOfEndorse.BlockerSelfError, null, info.blc.hashOfBlock.toStringUtf8(), pe.getSystemCurrentChainStatus, pe.getBlocker)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"DispatchOfRecvEndorsement，itself,recv endorse request,endorse height=${info.blc.height},local height=${pe.getCurrentHeight}"))
      case 3 =>
        //do not endorse
        sender ! ResultOfEndorsed(ResultFlagOfEndorse.CandidatorError, null, info.blc.hashOfBlock.toStringUtf8(), pe.getSystemCurrentChainStatus, pe.getBlocker)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"DispatchOfRecvEndorsement，it is not candator,do not endorse,recv endorse request,endorse height=${info.blc.height},local height=${pe.getCurrentHeight}"))
    }
  }*/


  override def receive = {
    case EndorsementInfo(block, blocker) =>
      createRouter
      /*if(!pe.isSynching){
        RepTimeTracer.setStartTime(pe.getSysTag, s"DispatchOfRecvEndorsement-recvendorsement-${moduleName}", System.currentTimeMillis(),block.height,block.transactions.size)
        EndorseHandler(EndorsementInfo(block, blocker))
        RepTimeTracer.setEndTime(pe.getSysTag, s"DispatchOfRecvEndorsement-recvendorsement-${moduleName}", System.currentTimeMillis(),block.height,block.transactions.size)
      }else{
        sender ! ResultOfEndorsed(ResultFlagOfEndorse.EnodrseNodeIsSynching, null, block.hashOfBlock.toStringUtf8(),pe.getSystemCurrentChainStatus,pe.getBlocker)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"DispatchOfRecvEndorsement，do not endorse,it is synching,recv endorse request,endorse height=${block.height},local height=${pe.getCurrentHeight}"))
      }*/

      router.route(EndorsementInfo(block, blocker), sender)
    case _ => //ignore
  }
}