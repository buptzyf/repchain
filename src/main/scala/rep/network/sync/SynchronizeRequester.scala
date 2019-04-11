package rep.network.sync

import akka.actor.{ ActorRef, Props }
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import rep.network.base.ModuleBase
import rep.app.conf.TimePolicy
import rep.storage.ImpDataAccess
import rep.protos.peer._
import rep.network.persistence.Storager.{ BlockRestore, SourceOfBlock }
import scala.collection._
import rep.utils.GlobalUtils.{ ActorType, BlockEvent, EventType ,NodeStatus}
import rep.log.trace.LogType
import rep.app.conf.SystemProfile
import rep.network.util.NodeHelp

object SynchronizeRequester {
  def props(name: String): Props = Props(classOf[SynchronizeRequester], name)
  case class ResponseInfo(response: BlockchainInfo, responser: ActorRef)
  case object SyncStatus {
    val Sync_ChainInfo = 1
    val Sync_BlockData = 2
    val Sync_None = 3
  }

  case class SyncInfo(dp: ActorRef, minBlockHeight: Long, maxBlockHeight: Long)
  case class GreatMajority(addr: String, height: Long, lastHash: String, count: Int)

  def SyncStatusToString(s: Int): String = {
    s match {
      case 1 => "Sync_ChainInfo"
      case 2 => "Sync_BlockData"
      case 3 => "Sync_None"
      case _ => "Undefine"
    }
  }
}

class SynchronizeRequester(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.concurrent.duration._
  import scala.util.control.Breaks._

  private var CurrentStatus = SynchronizeRequester.SyncStatus.Sync_None

  private var chaininfo4local: BlockchainInfo = null
  private var ResultList = new immutable.TreeMap[String, SynchronizeRequester.ResponseInfo]()
  private var CurrentSyncInfo: SynchronizeRequester.SyncInfo = null
  private var CurrentSyncBlockNumber: Long = 0
  private var repeatTimes = 0

  private def reset: Unit = {
    chaininfo4local = null
    ResultList = new immutable.TreeMap[String, SynchronizeRequester.ResponseInfo]()
    this.CurrentSyncInfo = new SynchronizeRequester.SyncInfo(null, 0, 0)
    CurrentSyncBlockNumber = 0
    repeatTimes = 0
  }

  private def ChainInfoHandler = {
    val len = this.ResultList.size
    val nodelen = pe.getNodeMgr.getStableNodes.size
    if (NodeHelp.ConsensusConditionChecked(len, nodelen - 1)) {
      val majority = SyncHelp.GetGreatMajorityHeight(ResultList, this.chaininfo4local.height, nodelen)
      Decide(majority)
    } else {
      logMsg(LogType.INFO, moduleName + "~" + s"do not handler,recv number=${len},node number=${nodelen}")
    }
  }

  private def Decide(majority: SynchronizeRequester.GreatMajority) = {
    if (majority == null) {
      if (this.ResultList.size >= (pe.getNodeMgr.getStableNodes.size - 1)) {
        //自动停止同步
        FinishHandler
        logMsg(LogType.INFO, moduleName + "~" + s"sync chaininfo error,stoped sync,recv number=${this.ResultList.size},node number=${pe.getNodeMgr.getStableNodes.size}")
      } else {
        logMsg(LogType.INFO, moduleName + "~" + s"go on wait response,recv number=${this.ResultList.size},node number=${pe.getNodeMgr.getStableNodes.size}")
      }
    } else {
      //启动块同步
      if (majority.height > this.chaininfo4local.height) {
        logMsg(LogType.INFO, moduleName + "~" + s"chaininfo sync finish,start block data sync.")
        initBlockDataSync(this.ResultList(majority.addr).responser, this.chaininfo4local.height + 1, majority.height)
        SendBlockDataSync(false)
      }else{
        //自动停止同步
        FinishHandler
      }
    }
  }

  private def initBlockDataSync(responser: ActorRef, start: Long, end: Long) = {
    this.CurrentSyncInfo = SynchronizeRequester.SyncInfo(responser, start, end)
    this.CurrentStatus = SynchronizeRequester.SyncStatus.Sync_BlockData
    this.CurrentSyncBlockNumber = this.CurrentSyncInfo.minBlockHeight
  }
  
  private def SendChainInfoSync(isRepeat: Boolean) = {
    schedulerLink = clearSched()
    if (isRepeat) {
      repeatTimes += 1
    } else {
      repeatTimes = 1
    }
    reset
    val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
    chaininfo4local = dataaccess.getBlockChainInfo()
    mediator ! Publish(BlockEvent.CHAIN_INFO_SYNC, chaininfo4local)
    schedulerLink = scheduler.scheduleOnce(TimePolicy.getTimeoutSync seconds, self, SyncMsg.SyncTimeout)
  }

  private def SendBlockDataSync(isRepeat: Boolean) = {
    schedulerLink = clearSched()
    if (isRepeat) {
      repeatTimes += 1
    } else {
      repeatTimes = 1
    }
    if(pe.getBlockCacheMgr.exist(this.CurrentSyncBlockNumber)){
      this.CurrentSyncBlockNumber += 1
    }
    this.CurrentSyncInfo.dp ! SyncMsg.BlockDataOfRequest(this.CurrentSyncBlockNumber)
    schedulerLink = scheduler.scheduleOnce(TimePolicy.getTimeoutSync seconds, self, SyncMsg.SyncTimeout)
  }

  private def FinishHandler = {
    schedulerLink = clearSched()
    pe.setSystemStatus(NodeStatus.Ready)
    this.CurrentStatus = SynchronizeRequester.SyncStatus.Sync_None
    reset
  }

  override def receive: Receive = {
    case SyncMsg.StartSync =>
      if (pe.getNodeMgr.getStableNodes.size >= SystemProfile.getVoteNoteMin) {
        if (this.CurrentStatus == SynchronizeRequester.SyncStatus.Sync_None) {
          pe.setSystemStatus(NodeStatus.Synching)
          logMsg(LogType.INFO, moduleName + "~" + s"start sync chaininfo  from actorAddr" + "～" + NodeHelp.getNodePath(sender()))
          this.CurrentStatus = SynchronizeRequester.SyncStatus.Sync_ChainInfo
          SendChainInfoSync(false)
        } else {
          logMsg(LogType.INFO, moduleName + "~" + s"it is synchronizing,currentstatus=${SynchronizeRequester.SyncStatusToString(this.CurrentStatus)}, from actorAddr" + "～" + NodeHelp.getNodePath(sender()))
        }
      } else {
        pe.setSystemStatus(NodeStatus.Ready)
        logMsg(LogType.INFO, moduleName + "~" + s"too few node,min=${SystemProfile.getVoteNoteMin}  from actorAddr" + "～" + NodeHelp.getNodePath(sender()))
      }

    case SyncMsg.ChainInfoOfResponse(response) =>
      this.CurrentStatus match {
        case SynchronizeRequester.SyncStatus.Sync_ChainInfo =>
          if (NodeHelp.isSameNodeForRef(sender(), self)) {
            logMsg(LogType.INFO, moduleName + "~" + s"recv  chaininfo response,it is self,do not handler, from actorAddr" + "～" + NodeHelp.getNodePath(sender()))
          } else {
            logMsg(LogType.INFO, moduleName + "~" + s"recv chaininfo response,current status is ${SynchronizeRequester.SyncStatusToString(this.CurrentStatus)},  from actorAddr" + "～" + NodeHelp.getNodePath(sender()))
            this.ResultList += NodeHelp.getNodePath(sender()) -> SynchronizeRequester.ResponseInfo(response, sender())
            ChainInfoHandler
          }

        case _ => logMsg(LogType.INFO, moduleName + "~" + s"do not recv chaininfo response,current status is ${SynchronizeRequester.SyncStatusToString(this.CurrentStatus)},  from actorAddr" + "～" + NodeHelp.getNodePath(sender()))
      }

    case SyncMsg.BlockDataOfResponse(data) =>
      this.CurrentStatus match {
        case SynchronizeRequester.SyncStatus.Sync_BlockData =>
          repeatTimes = 0
          sendEvent(EventType.RECEIVE_INFO, mediator, sender.path.toString(), selfAddr, Event.Action.BLOCK_SYNC)
          sendEventSync(EventType.RECEIVE_INFO, mediator, sender.path.toString(), selfAddr, Event.Action.BLOCK_SYNC)
          logMsg(LogType.INFO, moduleName + "~" + s"Get a data from $sender" + "～" + selfAddr)
          pe.getActorRef(ActorType.storager) ! BlockRestore(data,  SourceOfBlock.SYNC_BLOCK, sender)
          if (this.CurrentSyncBlockNumber < this.CurrentSyncInfo.maxBlockHeight) {
            this.CurrentSyncBlockNumber +=  1
            SendBlockDataSync(false)
          } else {
            FinishHandler
          }
        case _ => logMsg(LogType.INFO, moduleName + "~" + s"do not recv blockdata response,current status is ${SynchronizeRequester.SyncStatusToString(this.CurrentStatus)},  from actorAddr" + "～" + NodeHelp.getNodePath(sender()))
      }

    case SyncMsg.SyncRequestOfStorager(responser, start, end) =>
      this.CurrentStatus match {
        case SynchronizeRequester.SyncStatus.Sync_None =>
          reset
          logMsg(LogType.INFO, moduleName + "~" + s"block data sync from storager,start=${start},end=${end}start block data sync.")
          //pe.setSystemStatus(NodeStatus.Synching)
          initBlockDataSync(responser, start, end)
          SendBlockDataSync(false)
        case SynchronizeRequester.SyncStatus.Sync_BlockData =>
          if(this.CurrentSyncInfo.maxBlockHeight < end){
            this.CurrentSyncInfo = SynchronizeRequester.SyncInfo(responser, this.CurrentSyncInfo.minBlockHeight, end)
          }
        case _ => logMsg(LogType.INFO, moduleName + "~" + s"do not recv Storager request,current status is ${SynchronizeRequester.SyncStatusToString(this.CurrentStatus)},  from actorAddr" + "～" + NodeHelp.getNodePath(sender()))
      }

    case SyncMsg.SyncTimeout =>
      this.CurrentStatus match {
        case SynchronizeRequester.SyncStatus.Sync_ChainInfo =>
          if (repeatTimes <= 3) {
            SendChainInfoSync(true)
          } else {
            FinishHandler
          }
        case SynchronizeRequester.SyncStatus.Sync_BlockData =>
          if (repeatTimes < 3) {
            SendBlockDataSync(true)
          } else {
            FinishHandler
          }
        case _ =>
          FinishHandler
      }
  }
}