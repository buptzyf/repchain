package rep.network.sync.request.raft

import akka.actor.{ActorSelection, Address, Props}
import akka.pattern.AskTimeoutException
import rep.app.conf.SystemProfile
import rep.log.RepLogger
import rep.network.consensus.byzantium.ConsensusCondition
import rep.network.module.{IModuleManager, ModuleActorType}
import rep.network.sync.SyncMsg.{ChainInfoOfRequest, MaxBlockInfo, ResponseInfo, StartSync, SyncPreblocker, SyncRequestOfStorager}
import rep.network.sync.parser.ISynchAnalyzer
import rep.network.sync.parser.raft.IRAFTSynchAnalyzer
import rep.network.sync.request.ISynchRequester
import rep.network.util.NodeHelp
import rep.utils.GlobalUtils.{BlockEvent, EventType}
import akka.pattern.{AskTimeoutException, ask}
import rep.proto.rc2.Event
import rep.storage.chain.block.BlockStorager

import scala.concurrent.{Await, Future, TimeoutException}
import scala.concurrent.duration._

/**
 * Created by jiangbuyun on 2020/03/18.
 * 基于RAFT共识协议的同步actor的实现类
 */

object SynchRequesterOfRAFT{
  def props(name: String): Props = Props(classOf[SynchRequesterOfRAFT], name)
}

class SynchRequesterOfRAFT (moduleName: String) extends ISynchRequester(moduleName: String)  {
  import context.dispatcher

  override protected def getAnalyzerInSynch: ISynchAnalyzer = {
    new IRAFTSynchAnalyzer(pe.getSysTag, pe.getSystemCurrentChainStatus, pe.getNodeMgr)
  }

  protected def GetNodeOfChainInfo(addr: Address, lh: Long):ResponseInfo = {
    var result: ResponseInfo = null

    try {
      val selection: ActorSelection = context.actorSelection(toAkkaUrl(addr.toString, responseActorName));
      val future1 = selection ? ChainInfoOfRequest(lh)
      result = Await.result(future1, timeout.duration).asInstanceOf[ResponseInfo]
    } catch {
      case e: AskTimeoutException =>
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------GetNodeOfChainInfo timeout,${addr.toString}"))
        null
      case te: TimeoutException =>
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------GetNodeOfChainInfo java timeout,${addr.toString}"))
        null
    }

    RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"entry GetNodeOfChainInfo after,chaininfo=${result}"))
    result
  }

  protected def preblockerOfHandler(blockerName:String): Boolean = {
    var rb = true
    val lh = pe.getCurrentHeight
    val lhash = pe.getCurrentBlockHash
    val lprehash = pe.getSystemCurrentChainStatus.previousBlockHash.toStringUtf8()
    val addr = pe.getNodeMgr.getNodeAddr4NodeName(blockerName)
    RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"current stableNode，node content=${addr.toString}"))
    sendEvent(EventType.PUBLISH_INFO, mediator, pe.getSysTag, BlockEvent.CHAIN_INFO_SYNC, Event.Action.BLOCK_SYNC)
    val res = GetNodeOfChainInfo(addr, lh)

    if(res != null){
      if(res.response.height == lh ){
        //高度相同
        if(res.response.currentBlockHash.toStringUtf8 == lhash){
          //当前hash一致
          RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"height same，synch finish,localhost=${lh}"))
        }else{
          //当前hash不一致
          if(res.response.previousBlockHash.toStringUtf8 == lprehash){
            //前面一个块的hash一致，回滚一个块
            val da = BlockStorager.getBlockStorager(pe.getSysTag)
            if (da.rollbackToHeight(lh - 1)) {
              RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"entry rollback block,localhost=${lh}"))
              //同步区块
              getBlockDatas(lh-1, lh, res.responser)
            }
          }else{
            //前一块也不一致，需要手工处理
            rb = false
            RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"prehash error,localhost=${lh}"))
          }
        }
        RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"synch success,localhost=${lh},preblocker=${res.response.height}"))
      }else if(res.response.height < lh){
        RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"synch success,localhost=${lh},preblocker=${res.response.height}"))
      }else{
        if(res.ChainInfoOfSpecifiedHeight.currentBlockHash == lhash){
          RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"entry synch block,localhost=${lh},preblocker=${res.response.height}"))
          getBlockDatas(lh+1, res.response.height, res.responser)
        }else{
          if(res.ChainInfoOfSpecifiedHeight.previousBlockHash == lprehash){
            val da = BlockStorager.getBlockStorager(pe.getSysTag)
            if (da.rollbackToHeight(lh - 1)) {
              RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"entry rollback block,localhost=${lh}"))
              getBlockDatas(lh-1, res.response.height, res.responser)
            }
          }else{
            rb = false
          }
        }
      }
    }else{
      RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"preblockerOfHandler,response is null ,blockerName=${blockerName}"))
    }

    rb
  }

  override def receive: Receive = {
    case StartSync(isNoticeModuleMgr: Boolean) =>
      schedulerLink = clearSched()
      var rb = true
      initSystemChainInfo
      if (ConsensusCondition.CheckWorkConditionOfSystem(pe.getNodeMgr.getStableNodes.size) && !pe.isSynching) {
        pe.setSynching(true)
        try {
          val ssize = pe.getNodeMgr.getStableNodes.size
          if(SystemProfile.getVoteNodeList.size() == ssize){
            rb = Handler(isNoticeModuleMgr)
          }
        } catch {
          case e: Exception =>
            rb = false
            RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"request synch excep,msg=${e.getMessage}"))
        }
        pe.setSynching(false)
        if (rb) {
          if (isNoticeModuleMgr)
            pe.getActorRef(ModuleActorType.ActorType.modulemanager) ! IModuleManager.startup_Consensus
        } else {
          schedulerLink = scheduler.scheduleOnce(1.second, self, StartSync(isNoticeModuleMgr))
        }

      } else {
        RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"too few node,min=${SystemProfile.getVoteNodeMin} or synching  from actorAddr" + "～" + NodeHelp.getNodePath(sender())))
      }

    case SyncRequestOfStorager(responser, maxHeight) =>
      RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"entry blockdata synch,maxheight=${maxHeight},responser=${responser}"))
      if (!pe.isSynching) {
        pe.setSynching(true)
        RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"start blockdata synch,currentheight=${pe.getCurrentHeight},maxheight=${maxHeight}"))
        try {
          getBlockDatas(pe.getCurrentHeight, maxHeight, responser)
        } catch {
          case e: Exception =>
            pe.setSynching(false)
        }
        RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"stop blockdata synch,maxheight=${maxHeight}"))
        pe.setSynching(false)
      }
    case SyncPreblocker(blockerName)=>
      RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"entry preblocker synch,blockerName=${blockerName}"))
      if (!pe.isSynching) {
        pe.setSynching(true)
        RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"start preblocker synch,currentheight=${pe.getCurrentHeight}"))
        try {
          preblockerOfHandler(blockerName)
        } catch {
          case e: Exception =>
            pe.setSynching(false)
        }
        RepLogger.trace(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"stop preblocker synch,blockerName=${blockerName}"))
        pe.setSynching(false)
      }
  }

  override protected def setStartVoteInfo(maxblockinfo:MaxBlockInfo): Unit = {
    pe.setStartVoteInfo(maxblockinfo)
  }
}
