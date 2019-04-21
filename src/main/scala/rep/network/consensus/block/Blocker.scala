package rep.network.consensus.block

import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import akka.pattern.AskTimeoutException
import scala.concurrent._

import akka.actor.{ ActorRef, Address, Props }
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import com.google.protobuf.ByteString
import rep.app.conf.{ SystemProfile, TimePolicy }
import rep.crypto.Sha256
import rep.network.consensus.vote.Voter.VoteOfBlocker
import rep.network.base.ModuleBase
import rep.network.consensus.block.Blocker.{ConfirmedBlock,PreTransBlock,PreTransBlockResult}
import rep.protos.peer._
import rep.storage.ImpDataAccess
import rep.utils.GlobalUtils.{ ActorType, BlockEvent, EventType, NodeStatus}
import scala.collection.mutable
import com.sun.beans.decoder.FalseElementHandler
import scala.util.control.Breaks
import rep.utils.IdTool
import scala.util.control.Breaks._
import rep.network.consensus.util.{ BlockHelp, BlockVerify }
import rep.network.util.NodeHelp
import rep.network.Topic
import rep.network.consensus.endorse.EndorseMsg
import rep.log.RepLogger

object Blocker {
  def props(name: String): Props = Props(classOf[Blocker], name)

  case class PreTransBlock(block: Block, prefixOfDbTag: String)
  //块预执行结果
  case class PreTransBlockResult(blc: Block, result: Boolean)

  

  //正式块
  case class ConfirmedBlock(blc: Block, actRef: ActorRef)

  case object CreateBlock

  case object EndorseOfBlockTimeOut

}

/**
 * 出块模块
 *
 * @author shidianyue
 * @version 1.0
 * @since 1.0
 * @param moduleName 模块名称
 */
class Blocker(moduleName: String) extends ModuleBase(moduleName) {

  import context.dispatcher
  import scala.concurrent.duration._
  import akka.actor.ActorSelection
  import scala.collection.mutable.ArrayBuffer
  import rep.protos.peer.{ Transaction }

  val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
  implicit val timeout = Timeout(TimePolicy.getTimeoutPreload seconds)

  var preblock: Block = null

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("Block module start"))
    //SubscribeTopic(mediator, self, selfAddr, Topic.Block, true)
    //scheduler.scheduleOnce(TimePolicy.getStableTimeDur millis, context.parent, BlockModuleInitFinished)
  }

  private def CollectedTransOfBlock(num: Int, limitsize: Int): Seq[Transaction] = {
    val result = ArrayBuffer.empty[Transaction]
    try {
      val tmplist = pe.getTransPoolMgr.getTransListClone(num)
      if (tmplist.size > 0) {
        val currenttime = System.currentTimeMillis() / 1000
        val sr: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
        var transsize = 0
        breakable(
          tmplist.foreach(f => {
            //判断交易是否超时，把超时的交易删除
            if ((currenttime - f.createTime) > TimePolicy.getTranscationWaiting) {
              pe.getTransPoolMgr.removeTranscation(f.t)
            } else {
              //判断交易是否已经被打包入块，如果已经打包入块需要删除
              if (sr.getBlockByTxId(f.t.id) != null) {
                pe.getTransPoolMgr.removeTranscation(f.t)
              } else {
                transsize += f.t.toByteArray.size
                if (transsize * 3 > limitsize) {
                  //区块的长度限制
                  break
                } else {
                  f.t +=: result
                }
              }
            }
          }))
      }
    } finally {
    }
    result.reverse
  }

  private def ExecuteTransactionOfBlock(block: Block): Block = {
    try {
      val future = pe.getActorRef(ActorType.preloaderoftransaction) ? Blocker.PreTransBlock(block, "preload")
      val result = Await.result(future, timeout.duration).asInstanceOf[PreTransBlockResult]
      if (result.result) {
        result.blc
      } else {
        null
      }
    } catch {
      case e: AskTimeoutException => null
    }
  }

  private def CreateBlock: Block = {
    val trans = CollectedTransOfBlock(SystemProfile.getLimitBlockTransNum, SystemProfile.getBlockLength)
    //todo 交易排序
    if (trans.size > SystemProfile.getMinBlockTransNum) {
      var blc = BlockHelp.WaitingForExecutionOfBlock(pe.getCurrentBlockHash, pe.getCurrentHeight + 1, trans)
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,height=${blc.height},local height=${pe.getCurrentHeight}" + "~" + selfAddr))
      blc = ExecuteTransactionOfBlock(blc)
      if (blc != null) {
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"create new block,prelaod success,height=${blc.height},local height=${pe.getCurrentHeight}" + "~" + selfAddr))
        blc = BlockHelp.AddBlockHash(blc)
        BlockHelp.AddSignToBlock(blc, pe.getSysTag)
      } else {
        null
      }
    } else {
      null
    }
  }

  private def CreateBlockHandler = {
    //if (preblock == null) {
      val blc = CreateBlock
      if (blc != null) {
        this.preblock = blc
        schedulerLink = clearSched()
        pe.getActorRef(ActorType.endorsementcollectioner) ! EndorseMsg.CollectEndorsement(this.preblock, pe.getBlocker.blocker)
        //schedulerLink = scheduler.scheduleOnce(TimePolicy.getTimeoutEndorse seconds, self, Blocker.EndorseOfBlockTimeOut)
      }
    //}
  }

  override def receive = {
    //创建块请求（给出块人）
    case Blocker.CreateBlock =>
      if (NodeHelp.isBlocker(pe.getBlocker.blocker, pe.getSysTag)) {
        sendEvent(EventType.PUBLISH_INFO, mediator, selfAddr, Topic.Block, Event.Action.CANDIDATOR)
        if (!pe.isSynching) {
          //是出块节点
          if (preblock == null) {
            CreateBlockHandler
          } else {
            if (preblock.previousBlockHash.toStringUtf8() == pe.getCurrentBlockHash) {
              //预出块已经建立，不需要重新创建，可以请求再次背书
              /*schedulerLink = clearSched()
              logMsg(LogType.INFO, moduleName + "~" + s"create new block,send endorse collector,height=${this.preblock.height},local height=${pe.getCurrentHeight}" + "~" + selfAddr)
              pe.getActorRef(ActorType.endorsementcollectioner) ! EndorseMsg.CollectEndorsement(this.preblock, pe.getBlocker.blocker)
              sendEvent(EventType.PUBLISH_INFO, mediator, selfAddr, Topic.Endorsement, Event.Action.BLOCK_ENDORSEMENT)
              schedulerLink = scheduler.scheduleOnce(TimePolicy.getTimeoutEndorse seconds, self, Blocker.EndorseOfBlockTimeOut)*/
              
            } else {
              //上一个块已经变化，需要重新出块
              CreateBlockHandler
            }
          }
        } else {
          //节点状态不对
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"create new block,node status error,height=${this.preblock.height}" + "~" + selfAddr))
        }
      } else {
        //出块标识错误,暂时不用做任何处理
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,do not blocker,height=${this.preblock.height}" + "~" + selfAddr))
      }

    //出块超时
    case Blocker.EndorseOfBlockTimeOut =>
      schedulerLink = clearSched()
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,send endorse collector,height=${this.preblock.height},local height=${pe.getCurrentHeight}" + "~" + selfAddr))
      pe.getActorRef(ActorType.endorsementcollectioner) ! EndorseMsg.CollectEndorsement(this.preblock, pe.getBlocker.blocker)
      schedulerLink = scheduler.scheduleOnce(TimePolicy.getTimeoutEndorse seconds, self, Blocker.EndorseOfBlockTimeOut)
    case _ => //ignore
  }

}