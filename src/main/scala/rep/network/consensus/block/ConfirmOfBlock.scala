package rep.network.consensus.block

import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import akka.pattern.AskTimeoutException
import scala.concurrent._

import akka.actor.{ ActorRef, Props, Address }
import rep.crypto.Sha256
import rep.network.base.ModuleBase
import rep.network.Topic
import rep.network.util.NodeHelp
import rep.protos.peer.{ Event, Transaction }
import rep.utils.GlobalUtils.{ ActorType, BlockEvent, EventType, NodeStatus }
import com.sun.beans.decoder.FalseElementHandler
import scala.util.control.Breaks._
import scala.util.control.Exception.Finally
import java.util.concurrent.ConcurrentHashMap
import rep.log.trace.LogType
import rep.network.consensus.block.Blocker.{ ConfirmedBlock }
import rep.network.persistence.Storager.{ BlockRestore, SourceOfBlock }
import rep.network.consensus.util.{ BlockVerify, BlockHelp }

object ConfirmOfBlock {
  def props(name: String): Props = Props(classOf[ConfirmOfBlock], name)
}

class ConfirmOfBlock(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher

  override def preStart(): Unit = {
    logMsg(LogType.INFO, "confirm Block module start")
    SubscribeTopic(mediator, self, selfAddr, Topic.Block, false)
    //scheduler.scheduleOnce(TimePolicy.getStableTimeDur millis, context.parent, BlockModuleInitFinished)
  }
  import scala.concurrent.duration._
  import rep.protos.peer._

  private def asyncVerifyEndorse(e: Signature, byteOfBlock: Array[Byte]): Future[Boolean] = {
    val result = Promise[Boolean]

    val tmp = BlockVerify.VerifyOneEndorseOfBlock(e, byteOfBlock, pe.getSysTag)
    if (tmp._1) {
      result.success(true)
    } else {
      result.success(false)
    }
    result.future
  }

  private def asyncVerifyEndorses(block: Block): Boolean = {
    val b = block.clearEndorsements.toByteArray
    val listOfFuture: Seq[Future[Boolean]] = block.endorsements.map(x => {
      asyncVerifyEndorse(x, b)
    })
    val futureOfList: Future[List[Boolean]] = Future.sequence(listOfFuture.toList)
    var result = true
    //breakable(
    futureOfList.map(x => {
      x.foreach(f => {
        if (!f) {
          result = false
          logMsg(LogType.INFO, s"comfirmOfBlock verify endorse is error, break,block height${block.height},local height=${pe.getCurrentHeight}")
          //break
        }
      })
    })
   // )
    result
  }

  private def handler(block: Block, actRefOfBlock: ActorRef) = {
    logMsg(LogType.INFO, "confirm verify endorsement start")
    if (asyncVerifyEndorses(block)) {
      logMsg(LogType.INFO, "confirm verify endorsement end")
      //背书人的签名一致
      if (BlockVerify.VerifyEndorserSorted(block.endorsements.toArray[Signature]) == 1 || (block.height==1 && pe.getCurrentBlockHash == "" && block.previousBlockHash.isEmpty())) {
        //背书信息排序正确
        logMsg(LogType.INFO, "confirm verify endorsement sort")
        sendEvent(EventType.RECEIVE_INFO, mediator, selfAddr, Topic.Block, Event.Action.BLOCK_NEW)
        pe.getActorRef(ActorType.storager) ! BlockRestore(block, SourceOfBlock.CONFIRMED_BLOCK, actRefOfBlock)
      } else {
        ////背书信息排序错误
      }
    } else {
      //背书验证有错误
    }
  }

  private def checkedOfConfirmBlock(block: Block, actRefOfBlock: ActorRef) = {
    if (pe.getCurrentBlockHash == "" && block.previousBlockHash.isEmpty()) {
      logMsg(LogType.INFO, "confirm verify blockhash")
      handler(block, actRefOfBlock)
    } else if (block.previousBlockHash.toStringUtf8 == pe.getCurrentBlockHash) {
      //与上一个块一致
      logMsg(LogType.INFO, "confirm verify blockhash")
      if (NodeHelp.ConsensusConditionChecked(block.endorsements.size, pe.getNodeMgr.getStableNodes.size)) {
        //符合大多数人背书要求
        handler(block, actRefOfBlock)
      } else {
        //错误，没有符合大多人背书要求。

      }
    } else {
      //错误，上一个块不一致
    }
  }

  override def receive = {
    //Endorsement block
    case ConfirmedBlock(block, actRefOfBlock) =>
      checkedOfConfirmBlock(block, actRefOfBlock)
    case _ => //ignore
  }

}