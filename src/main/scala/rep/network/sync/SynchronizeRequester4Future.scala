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

package rep.network.sync

import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import akka.pattern.AskTimeoutException
import scala.concurrent._

import akka.actor.{ ActorRef, Props, Address, ActorSelection }
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import rep.network.base.ModuleBase
import rep.app.conf.TimePolicy
import rep.network.module.ModuleManager
import rep.storage.ImpDataAccess
import rep.protos.peer._
import rep.network.persistence.Storager.{ BlockRestore, SourceOfBlock }
import scala.collection._
import rep.utils.GlobalUtils.{ ActorType, BlockEvent, EventType, NodeStatus }
import rep.app.conf.SystemProfile
import rep.network.util.NodeHelp
import rep.network.sync.SyncMsg.{ ResponseInfo, StartSync, GreatMajority, BlockDataOfRequest, BlockDataOfResponse, SyncRequestOfStorager }
import scala.util.control.Breaks._
import rep.log.RepLogger

object SynchronizeRequester4Future {
  def props(name: String): Props = Props(classOf[SynchronizeRequester4Future], name)

}

class SynchronizeRequester4Future(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.concurrent.duration._

  implicit val timeout = Timeout(TimePolicy.getTimeoutSync seconds)
  private val responseActorName = "/user/modulemanager/synchresponser"

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix( "SynchronizeRequester4Future Start"))
  }

  private def toAkkaUrl(addr: Address, actorName: String): String = {
    return addr.toString + "/" + actorName;
  }

  private def AsyncGetNodeOfChainInfo(addr: Address): Future[ResponseInfo] = Future {
    //println(s"${pe.getSysTag}:entry AsyncGetNodeOfChainInfo")
    var result: ResponseInfo = null

    try {
      val selection: ActorSelection = context.actorSelection(toAkkaUrl(addr, responseActorName));
      val future1 = selection ? SyncMsg.ChainInfoOfRequest
      //logMsg(LogType.INFO, "--------AsyncGetNodeOfChainInfo success")
      result = Await.result(future1, timeout.duration).asInstanceOf[ResponseInfo]
    } catch {
      case e: AskTimeoutException =>
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------AsyncGetNodeOfChainInfo timeout"))
        null
      case te: TimeoutException =>
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix( "--------AsyncGetNodeOfChainInfo java timeout"))
        null
    }

    RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"entry AsyncGetNodeOfChainInfo after,asyncVerifyTransaction=${result}"))
    result
  }

  private def AsyncGetNodeOfChainInfos(stablenodes: Set[Address]): List[ResponseInfo] = {
    //println(s"${pe.getSysTag}:entry AsyncGetNodeOfChainInfos")
    //var result = new immutable.TreeMap[String, ResponseInfo]()
    val listOfFuture: Seq[Future[ResponseInfo]] = stablenodes.toSeq.map(addr => {
      AsyncGetNodeOfChainInfo(addr)
    })

    val futureOfList: Future[List[ResponseInfo]] = Future.sequence(listOfFuture.toList).recover({
      case e: Exception =>
        null
    })
    RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry AsyncGetNodeOfChainInfos 1"))
    try {
      val result1 = Await.result(futureOfList, timeout.duration).asInstanceOf[List[ResponseInfo]]
      RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry AsyncGetNodeOfChainInfos 2"))
      if (result1 == null) {
        List.empty
      } else {
        result1.toList
      }
    } catch {
      case te: TimeoutException =>
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix( "--------AsyncGetNodeOfChainInfo java timeout"))
        null
    }
  }

  private def getSyncInfo(infos: List[ResponseInfo], nodelength: Int, localHeight: Long): GreatMajority = {
    var majority: GreatMajority = null
    if (infos != null) {
      var nodecount = 0
      var nodeheight = 0l
      var addr: ActorRef = null
      var oneAddr: ActorRef = null

      infos.foreach(f => {
        if (f != null) {
          if (f.response.height >= localHeight) {
            nodecount += 1
            if (nodeheight == 0) {
              nodeheight = f.response.height
              addr = f.responser
            } else {
              if (f.response.height <= nodeheight) {
                nodeheight = f.response.height
                addr = f.responser
              }
            }
          }
          if (f.response.height == 1) {
            oneAddr = f.responser
          }
        }
      })

      if (NodeHelp.ConsensusConditionChecked(nodecount, nodelength)) {
        if (nodeheight == 0 && localHeight == 0 && oneAddr != null) {
          majority = GreatMajority(oneAddr, 1)
        } else if (nodeheight > 0) {
          majority = GreatMajority(addr, nodeheight)
        }
      }
    }
    majority
  }

  private def getBlockData(height: Long, ref: ActorRef): Boolean = {
    try {
      val future1 = ref ? BlockDataOfRequest(height)
      //logMsg(LogType.INFO, "--------AsyncGetNodeOfChainInfo success")
      var result = Await.result(future1, timeout.duration).asInstanceOf[BlockDataOfResponse]
      RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix( s"height=${height}--------AsyncGetNodeOfChainInfo success"))
      pe.getActorRef(ActorType.storager) ! result
      true
    } catch {
      case e: AskTimeoutException =>
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(  "--------getBlockData timeout"))
        false
      case te: TimeoutException =>
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix( "--------getBlockData java timeout"))
        false
    }
  }

  private def getBlockDatas(majority: GreatMajority) = {
    if (majority != null && majority.height > pe.getCurrentHeight) {
      var height = pe.getCurrentHeight + 1
      while (height <= majority.height) {
        if (!pe.getBlockCacheMgr.exist(height)) {
          if (!getBlockData(height, majority.addr)) {
            getBlockData(height, majority.addr)
          }
        }
        height += 1
      }
    }
  }

  private def Handler = {
    val nodes = pe.getNodeMgr.getStableNodes
    val res = AsyncGetNodeOfChainInfos(nodes)
    val majority = getSyncInfo(res, nodes.size, pe.getCurrentHeight)
    getBlockDatas(majority)
  }

  private def initSystemChainInfo = {
    if (pe.getCurrentHeight == 0) {
      val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
      pe.resetSystemCurrentChainStatus(dataaccess.getBlockChainInfo())
    }
  }

  override def receive: Receive = {
    case SyncMsg.StartSync(isNoticeModuleMgr: Boolean) =>
      initSystemChainInfo
      if (pe.getNodeMgr.getStableNodes.size >= SystemProfile.getVoteNoteMin && !pe.isSynching) {
        pe.setSynching(true)
        Handler
        pe.setSynching(false)
        if (isNoticeModuleMgr)
          pe.getActorRef(ActorType.modulemanager) ! ModuleManager.startup_Consensus
      } else {
        RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(  s"too few node,min=${SystemProfile.getVoteNoteMin} or synching  from actorAddr" + "～" + NodeHelp.getNodePath(sender())))
      }

    case SyncRequestOfStorager(responser, maxHeight) =>
      if (!pe.isSynching) {
        pe.setSynching(true)
        getBlockDatas(GreatMajority(responser, maxHeight))
        pe.setSynching(false)
      }
  }
}
