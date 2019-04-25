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
import rep.network.consensus.block.Blocker.{ ConfirmedBlock }
import rep.network.persistence.Storager.{ BlockRestore, SourceOfBlock }
import rep.network.consensus.util.{ BlockVerify, BlockHelp }
import rep.log.RepLogger

object ConfirmOfBlock {
  def props(name: String): Props = Props(classOf[ConfirmOfBlock], name)
}

class ConfirmOfBlock(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("confirm Block module start"))
    SubscribeTopic(mediator, self, selfAddr, Topic.Block, false)
  }
  import scala.concurrent.duration._
  import rep.protos.peer._
  
  implicit val timeout = Timeout(3 seconds)

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
    val futureOfList: Future[List[Boolean]] = Future.sequence(listOfFuture.toList).recover({
      case e:Exception =>
        null
    })
    
    val result1 = Await.result(futureOfList, timeout.duration).asInstanceOf[List[Boolean]]
    
    var result = true
    if(result1 == null){
      false
    }else{
      result1.toList.foreach(f=>{
        if(!f){
          result = false
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"comfirmOfBlock verify endorse is error, break,block height=${block.height},local height=${pe.getCurrentHeight}"))
        }
      })
    }
    
    result
  }

  private def handler(block: Block, actRefOfBlock: ActorRef) = {
    RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("confirm verify endorsement start"))
    if (asyncVerifyEndorses(block)) {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("confirm verify endorsement end"))
      //背书人的签名一致
      if (BlockVerify.VerifyEndorserSorted(block.endorsements.toArray[Signature]) == 1 || (block.height==1 && pe.getCurrentBlockHash == "" && block.previousBlockHash.isEmpty())) {
        //背书信息排序正确
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "confirm verify endorsement sort"))
        sendEvent(EventType.RECEIVE_INFO, mediator, pe.getSysTag, Topic.Block, Event.Action.BLOCK_NEW)
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
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("confirm verify blockhash"))
      handler(block, actRefOfBlock)
    } else  {
      //与上一个块一致
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("confirm verify blockhash"))
      if (NodeHelp.ConsensusConditionChecked(block.endorsements.size, pe.getNodeMgr.getStableNodes.size)) {
        //符合大多数人背书要求
        handler(block, actRefOfBlock)
      } else {
        //错误，没有符合大多人背书要求。

      }
    } 
  }

  override def receive = {
    //Endorsement block
    case ConfirmedBlock(block, actRefOfBlock) =>
      checkedOfConfirmBlock(block, actRefOfBlock)
    case _ => //ignore
  }

}