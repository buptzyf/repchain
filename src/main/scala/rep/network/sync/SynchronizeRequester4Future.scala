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
import rep.network.sync.SyncMsg.{ ResponseInfo, StartSync,  BlockDataOfRequest, BlockDataOfResponse, SyncRequestOfStorager,ChainInfoOfRequest }
import scala.util.control.Breaks._
import rep.log.RepLogger
import scala.collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer

object SynchronizeRequester4Future {
  def props(name: String): Props = Props(classOf[SynchronizeRequester4Future], name)

}

class SynchronizeRequester4Future(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.concurrent.duration._

  implicit val timeout = Timeout(TimePolicy.getTimeoutSync seconds)
  private val responseActorName = "/user/modulemanager/synchresponser"

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("SynchronizeRequester4Future Start"))
  }

  private def toAkkaUrl(addr: Address, actorName: String): String = {
    return addr.toString + "/" + actorName;
  }

  private def AsyncGetNodeOfChainInfo(addr: Address,lh:Long): Future[ResponseInfo] = Future {
    //println(s"${pe.getSysTag}:entry AsyncGetNodeOfChainInfo")
    var result: ResponseInfo = null

    try {
      val selection: ActorSelection = context.actorSelection(toAkkaUrl(addr, responseActorName));
      val future1 = selection ? ChainInfoOfRequest(lh)
      //logMsg(LogType.INFO, "--------AsyncGetNodeOfChainInfo success")
      result = Await.result(future1, timeout.duration).asInstanceOf[ResponseInfo]
    } catch {
      case e: AskTimeoutException =>
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------AsyncGetNodeOfChainInfo timeout"))
        null
      case te: TimeoutException =>
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------AsyncGetNodeOfChainInfo java timeout"))
        null
    }

    RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"entry AsyncGetNodeOfChainInfo after,chaininfo=${result}"))
    result
  }

  private def AsyncGetNodeOfChainInfos(stablenodes: Set[Address],lh:Long): List[ResponseInfo] = {
    //println(s"${pe.getSysTag}:entry AsyncGetNodeOfChainInfos")
    //var result = new immutable.TreeMap[String, ResponseInfo]()
    val listOfFuture: Seq[Future[ResponseInfo]] = stablenodes.toSeq.map(addr => {
      AsyncGetNodeOfChainInfo(addr,lh)
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
        result1
      }
    } catch {
      case te: TimeoutException =>
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------AsyncGetNodeOfChainInfo java timeout"))
        null
    }
  }

  
  
  private def getBlockData(height: Long, ref: ActorRef): Boolean = {
    try {
      sendEvent(EventType.PUBLISH_INFO, mediator,pe.getNodeMgr.getStableNodeName4Addr(self.path.address), BlockEvent.CHAIN_INFO_SYNC,  Event.Action.BLOCK_SYNC)
      val future1 = ref ? BlockDataOfRequest(height)
      //logMsg(LogType.INFO, "--------AsyncGetNodeOfChainInfo success")
      var result = Await.result(future1, timeout.duration).asInstanceOf[BlockDataOfResponse]
      RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"height=${height}--------getBlockData success"))
      pe.getActorRef(ActorType.storager) ! BlockRestore(result.data, SourceOfBlock.SYNC_BLOCK, ref)
      true
    } catch {
      case e: AskTimeoutException =>
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------getBlockData timeout"))
        false
      case te: TimeoutException =>
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------getBlockData java timeout"))
        false
    }
  }

  private def getBlockDatas(lh:Long,rh:Long,actorref:ActorRef) = {
    if (rh > lh) {
      var height = lh + 1
      while (height <= rh) {
        if (!pe.getBlockCacheMgr.exist(height)) {
          if (!getBlockData(height, actorref)) {
            getBlockData(height, actorref)
          }
        }
        height += 1
      }
    }
  }

  
  private def checkHashAgreement(h:Long,ls:List[ResponseInfo],ns:Int,checkType:Int):(Boolean,String)={
    val hls = ls.filter(_.response.height == h)
    var gls : List[(String, Int)] = null
    checkType match{
      case 1 =>
        //检查远端的最后一个块的hash的一致性
          gls = hls.groupBy(x => x.response.currentBlockHash.toStringUtf8()).map(x => (x._1,x._2.length)).toList.sortBy(x => -x._2)
      case 2 =>
        //检查远端指定高度块的一致性
        gls = hls.groupBy(x => x.ChainInfoOfSpecifiedHeight.currentBlockHash.toStringUtf8()).map(x => (x._1,x._2.length)).toList.sortBy(x => -x._2)
    }
    val tmpgHash = gls.head._1
    val tmpgCount = gls.head._2
    if(NodeHelp.ConsensusConditionChecked(tmpgCount, ns)){
      (true,tmpgHash)
    }else{
      (false,"")
    }
  }
 
  
 
  private def Handler = {
    val lh = pe.getCurrentHeight
    val lhash = pe.getCurrentBlockHash
    val lprehash = pe.getSystemCurrentChainStatus.previousBlockHash.toStringUtf8()
    val nodes = pe.getNodeMgr.getStableNodes
    sendEvent(EventType.PUBLISH_INFO, mediator,pe.getNodeMgr.getStableNodeName4Addr(self.path.address), BlockEvent.CHAIN_INFO_SYNC,  Event.Action.BLOCK_SYNC)
    val res = AsyncGetNodeOfChainInfos(nodes,lh)
    
    val parser = new SynchResponseInfoAnalyzer(pe.getSysTag, pe.getSystemCurrentChainStatus, pe.getNodeMgr)
    parser.Parser(res)
    val result = parser.getResult
    val rresult = parser.getRollbackAction
    val sresult = parser.getSynchActiob
    
    
    if(result == null){
      println(pe.getSysTag)
    }
    
    if(result.ar){
      if(rresult != null){
        val da = ImpDataAccess.GetDataAccess(pe.getSysTag)
       if(da.rollbackToheight(rresult.destHeight)){
         if(sresult != null){
           getBlockDatas(sresult.start,sresult.end,sresult.server)
         }
       }else{
         RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"回滚块失败，failed height=${rresult.destHeight}"))
       }
      }else{
        if(sresult != null){
          getBlockDatas(sresult.start,sresult.end,sresult.server)
        }
      }
    }else{
      RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(result.error))
    }
  }

  private def initSystemChainInfo = {
    if (pe.getCurrentHeight == 0) {
      val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
      pe.resetSystemCurrentChainStatus(dataaccess.getBlockChainInfo())
    }
  }

  override def receive: Receive = {
    case StartSync(isNoticeModuleMgr: Boolean) =>
      initSystemChainInfo
      if (pe.getNodeMgr.getStableNodes.size >= SystemProfile.getVoteNoteMin && !pe.isSynching) {
        pe.setSynching(true)
        Handler
        pe.setSynching(false)
        if (isNoticeModuleMgr)
          pe.getActorRef(ActorType.modulemanager) ! ModuleManager.startup_Consensus
      } else {
        RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"too few node,min=${SystemProfile.getVoteNoteMin} or synching  from actorAddr" + "～" + NodeHelp.getNodePath(sender())))
      }

    case SyncRequestOfStorager(responser, maxHeight) =>
      if (!pe.isSynching) {
        pe.setSynching(true)
        getBlockDatas(pe.getCurrentHeight,maxHeight,responser)
        pe.setSynching(false)
      }
  }
}
