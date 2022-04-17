package rep.network.sync.request

import akka.actor.{ActorRef, ActorSelection, Address, Props}
import akka.pattern.{AskTimeoutException, ask}
import akka.util.Timeout
import rep.app.conf.{SystemProfile, TimePolicy}
import rep.log.RepLogger
import rep.network.base.ModuleBase
import rep.network.consensus.byzantium.ConsensusCondition
import rep.network.module.ModuleActorType
import rep.network.persistence.IStorager.SourceOfBlock
import rep.network.sync.SyncMsg.{BlockDataOfRequest, BlockDataOfResponse, ChainInfoOfRequest, MaxBlockInfo, ResponseInfo}
import rep.network.util.NodeHelp
import rep.protos.peer.Event
import rep.utils.GlobalUtils.{BlockEvent, EventType}
import rep.network.consensus.common.MsgOfConsensus.BlockRestore

import scala.collection.{Seq, Set}
import scala.concurrent.{Await, Future, TimeoutException}
import rep.network.sync.parser.ISynchAnalyzer
import rep.storage.chain.block.{BlockSearcher, BlockStorager}

/**
 * Created by jiangbuyun on 2020/03/18.
 * 基于同步请求actor的抽象类
 */

object  ISynchRequester{
  def props(name: String): Props = Props(classOf[ISynchRequester], name)
}

abstract class ISynchRequester(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.concurrent.duration._

  implicit val timeout = Timeout(TimePolicy.getTimeoutSync.seconds)
  protected val responseActorName = "/user/modulemanager/synchresponser"


  protected def toAkkaUrl(addr: String, actorName: String): String = {
    return addr + "/" + actorName;
  }

  protected def AsyncGetNodeOfChainInfo(addr: Address, lh: Long): Future[ResponseInfo] = Future {
    var result: ResponseInfo = null

    try {
      val selection: ActorSelection = context.actorSelection(toAkkaUrl(addr.toString, responseActorName));
      val future1 = selection ? ChainInfoOfRequest(lh)
      result = Await.result(future1, timeout.duration).asInstanceOf[ResponseInfo]
    } catch {
      case e: AskTimeoutException =>
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------AsyncGetNodeOfChainInfo timeout,${addr.toString}"))
        null
      case te: TimeoutException =>
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------AsyncGetNodeOfChainInfo java timeout,${addr.toString}"))
        null
    }

    RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"entry AsyncGetNodeOfChainInfo after,chaininfo=${result}"))
    result
  }

  protected def AsyncGetNodeOfChainInfos(stablenodes: Set[Address], lh: Long): List[ResponseInfo] = {
    val listOfFuture: Seq[Future[ResponseInfo]] = stablenodes.toSeq.map(addr => {
      AsyncGetNodeOfChainInfo(addr, lh)
    })

    val futureOfList: Future[List[ResponseInfo]] = Future.sequence(listOfFuture.toList).recover({
      case e: Exception =>
        null
    })
    RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry AsyncGetNodeOfChainInfos 1"))
    try {
      val result1 = Await.result(futureOfList, timeout.duration*stablenodes.size).asInstanceOf[List[ResponseInfo]]
      RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry AsyncGetNodeOfChainInfos 2"))
      if (result1 == null) {
        List.empty
      } else {
        result1.filter(_ != null)
      }
    } catch {
      case te: TimeoutException =>
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------AsyncGetNodeOfChainInfos java timeout"))
        null
    }
  }

  protected def getBlockData(height: Long, ref: ActorRef): Boolean = {
    try {
      sendEvent(EventType.PUBLISH_INFO, mediator, pe.getSysTag, pe.getNodeMgr.getNodeName4AddrString(NodeHelp.getNodeAddress(ref)), Event.Action.BLOCK_SYNC_DATA)
      val selection: ActorSelection = context.actorSelection(toAkkaUrl(NodeHelp.getNodeAddress(ref), responseActorName));
      val future1 = selection ? BlockDataOfRequest(height)
      //logMsg(LogType.INFO, "--------AsyncGetNodeOfChainInfo success")
      var result = Await.result(future1, timeout.duration).asInstanceOf[BlockDataOfResponse]
      RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"height=${height}--------getBlockData success"))
      pe.getActorRef(ModuleActorType.ActorType.storager) ! BlockRestore(result.data, SourceOfBlock.SYNC_BLOCK, ref)
      true
    } catch {
      case e: AskTimeoutException =>
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------getBlockData timeout,${akka.serialization.Serialization.serializedActorPath(ref).toString}"))
        false
      case te: TimeoutException =>
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------getBlockData java timeout,${akka.serialization.Serialization.serializedActorPath(ref).toString}"))
        false
    }
  }

  protected def getBlockDatas(lh: Long, rh: Long, actorref: ActorRef) = {
    if (rh > lh) {
      var height = lh + 1
      while (height <= rh) {
        if (!pe.getBlockCacheMgr.exist(height) && !getBlockData(height, actorref)) {
          getBlockData(height, actorref)
        }
        height += 1
      }
    }
  }

  protected def checkHashAgreement(h: Long, ls: List[ResponseInfo], ns: Int, checkType: Int): (Boolean, String) = {
    val hls = ls.filter(_.response.height == h)
    var gls: List[(String, Int)] = null
    checkType match {
      case 1 =>
        //检查远端的最后一个块的hash的一致性
        gls = hls.groupBy(x => x.response.currentBlockHash.toStringUtf8()).map(x => (x._1, x._2.length)).toList.sortBy(x => -x._2)
      case 2 =>
        //检查远端指定高度块的一致性
        gls = hls.groupBy(x => x.ChainInfoOfSpecifiedHeight.currentBlockHash.toStringUtf8()).map(x => (x._1, x._2.length)).toList.sortBy(x => -x._2)
    }
    val tmpgHash = gls.head._1
    val tmpgCount = gls.head._2
    if (ConsensusCondition.ConsensusConditionChecked(tmpgCount)) {
      (true, tmpgHash)
    } else {
      (false, "")
    }
  }

  protected def getAnalyzerInSynch : ISynchAnalyzer
  protected def setStartVoteInfo(maxblockinfo:MaxBlockInfo):Unit

  protected def Handler(isStartupSynch: Boolean): Boolean = {
    var rb = true
    val lh = pe.getCurrentHeight
    val lhash = pe.getCurrentBlockHash
    val lprehash = pe.getSystemCurrentChainStatus.previousBlockHash.toStringUtf8()
    val nodes = pe.getNodeMgr.getStableNodes
    RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"current stableNode，node content=${nodes.mkString("|")}"))
    RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"current stableNode，node name content=${pe.getNodeMgr.getStableNodeNames.mkString("|")}"))
    sendEvent(EventType.PUBLISH_INFO, mediator, pe.getSysTag, BlockEvent.CHAIN_INFO_SYNC, Event.Action.BLOCK_SYNC)
    val res = AsyncGetNodeOfChainInfos(nodes, lh)

    var analyzerInSynch = getAnalyzerInSynch
    analyzerInSynch.Parser(res,isStartupSynch)

    val result = analyzerInSynch.getResult
    val rresult = analyzerInSynch.getRollbackAction
    val sresult = analyzerInSynch.getSynchActiob

    if (result.ar == 1) {

      setStartVoteInfo(analyzerInSynch.getMaxBlockInfo)

      if (rresult != null) {
        val da = BlockStorager.getBlockStorager(pe.getSysTag)
        if (da.rollbackToHeight(rresult.destHeight)) {
          if (sresult != null) {
            getBlockDatas(sresult.start, sresult.end, sresult.server)
          } else {
            pe.resetSystemCurrentChainStatus(new BlockSearcher(pe.getSysTag).getChainInfo)
          }
        } else {
          RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"回滚块失败，failed height=${rresult.destHeight}"))
        }
      } else {
        if (sresult != null) {
          getBlockDatas(sresult.start, sresult.end, sresult.server)
        }
      }
    } else if (result.ar == 2) {
      rb = false
    } else {
      RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(result.error))
    }
    rb
  }

  protected def initSystemChainInfo = {
    if (pe.getCurrentHeight == 0) {
      pe.resetSystemCurrentChainStatus(new BlockSearcher(pe.getSysTag).getChainInfo)
    }
  }

}
