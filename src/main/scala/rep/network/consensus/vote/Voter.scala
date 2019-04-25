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

package rep.network.consensus.vote

import akka.actor.{ Actor, Address, Props }
import rep.app.conf.{ SystemProfile, TimePolicy, SystemCertList }
import rep.crypto.Sha256
import rep.network.base.ModuleBase
import rep.protos.peer.BlockchainInfo
import rep.storage.ImpDataAccess
import rep.utils.GlobalUtils.{ ActorType, BlockEvent, BlockerInfo, NodeStatus }
import com.sun.beans.decoder.FalseElementHandler
import rep.network.util.NodeHelp
import rep.network.consensus.block.Blocker.{ CreateBlock }
import rep.network.sync.SyncMsg.StartSync
import rep.network.consensus.block.GenesisBlocker.GenesisBlock
import rep.log.RepLogger

object Voter {

  def props(name: String): Props = Props(classOf[Voter], name)

  case object VoteOfBlocker

}

class Voter(moduleName: String) extends ModuleBase(moduleName) with CRFDVoter {

  import context.dispatcher
  import scala.concurrent.duration._
  import scala.concurrent.forkjoin.ThreadLocalRandom

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Vote_Logger, this.getLogMsgPrefix(  "Vote module start"))
  }

  val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)

  private var BlockHashOfVote: String = null
  private var Blocker: BlockerInfo = BlockerInfo("", -1, 0l)
  private var voteCount = 0

  def checkTranNum: Boolean = {
    if (pe.getTransPoolMgr.getTransLength() > SystemProfile.getMinBlockTransNum) true else false
  }

  private def cleanVoteInfo = {
    this.voteCount = 0
    this.BlockHashOfVote = null
    this.Blocker = BlockerInfo("", -1, 0l)
    pe.resetBlocker(this.Blocker)
  }

  private def getSystemBlockHash: String = {
    if (pe.getCurrentBlockHash == "") {
      pe.resetSystemCurrentChainStatus(dataaccess.getBlockChainInfo())
    }
    pe.getCurrentBlockHash
  }

  private def resetCandidator = {
    this.BlockHashOfVote = pe.getCurrentBlockHash
    val candidatorCur = candidators(pe.getSysTag,this.BlockHashOfVote,SystemCertList.getSystemCertList, Sha256.hash(BlockHashOfVote))
    pe.getNodeMgr.resetCandidator(candidatorCur)
  }

  private def resetBlocker(idx: Int) = {
    RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(  s"sysname=${pe.getSysTag},votelist=${pe.getNodeMgr.getCandidator.toArray[String].mkString("|")},idx=${idx}"))
    this.Blocker = BlockerInfo(blocker(pe.getNodeMgr.getCandidator.toArray[String], idx), idx, System.currentTimeMillis())
    pe.resetBlocker(this.Blocker)
    NoticeBlockerMsg
  }

  private def NoticeBlockerMsg = {
    if (this.Blocker.blocker.equals(pe.getSysTag)) {
      //发送建立新块的消息
      pe.getActorRef(ActorType.blocker) ! CreateBlock
    } 
  }

  private def DelayVote = {
    this.voteCount += 1
    var time = this.voteCount * TimePolicy.getVoteRetryDelay
    schedulerLink = clearSched()
    schedulerLink = scheduler.scheduleOnce(TimePolicy.getVoteRetryDelay millis, self, Voter.VoteOfBlocker)
  }

  private def vote = {
    if (checkTranNum) {
      if (this.BlockHashOfVote == null) {
        this.cleanVoteInfo
        this.resetCandidator
        this.resetBlocker(0)
        RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(  s"sysname=${pe.getSysTag},first voter,blocker=${this.Blocker.blocker},voteidx=${this.Blocker.VoteIndex}" + "~" + selfAddr))
      } else {
        if (!this.BlockHashOfVote.equals(pe.getCurrentBlockHash)) {
          //抽签的基础块已经变化，需要重续选择候选人
          this.cleanVoteInfo
          this.resetCandidator
          this.resetBlocker(0)
          RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(  s"sysname=${pe.getSysTag},hash change,reset voter,height=${pe.getCurrentHeight},blocker=${this.Blocker.blocker},voteidx=${this.Blocker.VoteIndex}" + "~" + selfAddr))
        } else {
          if (this.Blocker.blocker == "") {
            this.cleanVoteInfo
            this.resetCandidator
            this.resetBlocker(0)
            RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(  s"sysname=${pe.getSysTag},blocker=null,reset voter,height=${pe.getCurrentHeight},blocker=${this.Blocker.blocker},voteidx=${this.Blocker.VoteIndex}" + "~" + selfAddr))
          } else {
            if ((System.currentTimeMillis() - this.Blocker.voteTime) / 1000 > TimePolicy.getTimeOutBlock) {
              //说明出块超时
              this.voteCount = 0
              this.resetBlocker(this.Blocker.VoteIndex + 1)
              RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix( s"sysname=${pe.getSysTag},block timeout,reset voter,height=${pe.getCurrentHeight},blocker=${this.Blocker.blocker},voteidx=${this.Blocker.VoteIndex}" + "~" + selfAddr))
            } else {
              NoticeBlockerMsg
            }
          }
        }
      }
    }
    //logMsg(LogType.INFO, s"current blocker=${this.Blocker.blocker},${this.Blocker.VoteIndex}")
  }

  private def voteMsgHandler = {
    if(pe.getNodeMgr.getStableNodes.size >= SystemProfile.getVoteNoteMin ){
    //只有共识节点符合要求之后开始工作
    if (getSystemBlockHash == "") {
      //系统属于初始化状态
      if (NodeHelp.isSeedNode(pe.getSysTag)) {
        // 建立创世块消息
        pe.getActorRef(ActorType.gensisblock) ! GenesisBlock
      } else {
        // 发出同步消息
        //pe.setSystemStatus(NodeStatus.Synching)
        //pe.getActorRef(ActorType.synchrequester) ! StartSync
      }
    } else {
      if (!pe.isSynching) {
        vote
      }
    }
    }
    DelayVote
  }

  override def receive = {
    case Voter.VoteOfBlocker =>
      voteMsgHandler
  }
}