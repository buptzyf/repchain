package rep.network.consensus.endorse

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
import rep.app.conf.{ SystemProfile, TimePolicy }
import rep.storage.{ ImpDataPreload, ImpDataPreloadMgr }
import rep.utils.GlobalUtils.{ ActorType, BlockEvent, EventType, NodeStatus }
import com.sun.beans.decoder.FalseElementHandler
import rep.network.consensus.vote.Voter.VoteOfBlocker
import sun.font.TrueTypeFont
import scala.util.control.Breaks._
import scala.util.control.Exception.Finally
import java.util.concurrent.ConcurrentHashMap
import rep.log.trace.LogType
import rep.network.consensus.block.Blocker.{ EndorsementInfo, ResultOfEndorsed, PreTransBlock, PreTransBlockResult }
import rep.network.consensus.util.{ BlockVerify, BlockHelp }

object Endorser {
  def props(name: String): Props = Props(classOf[Endorser], name)
}

class Endorser(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.concurrent.duration._
  import rep.protos.peer._
  import rep.storage.ImpDataAccess

  implicit val timeout = Timeout(TimePolicy.getTimeoutPreload seconds)

  private def hasRepeatOfTrans(trans: Seq[Transaction]): Boolean = {
    var isRepeat: Boolean = false
    val aliaslist = trans.distinct
    if (aliaslist.size != trans.size) {
      isRepeat = true
    } else {
      val sr: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
      breakable(
        trans.foreach(f => {
          if (sr.getBlockByTxId(f.id) != null) {
            isRepeat = true
            break
          }
        }))
    }
    isRepeat
  }

  private def ExecuteTransactionOfBlock(block: Block): Boolean = {
    try {
      val future = pe.getActorRef(ActorType.preloaderoftransaction) ? PreTransBlock(block,"endorser")
      val result = Await.result(future, timeout.duration).asInstanceOf[PreTransBlockResult]
      if (result.result) {
        var tmpblock = result.blc.withHashOfBlock(block.hashOfBlock)
        BlockVerify.VerifyHashOfBlock(tmpblock)
      } else {
        false
      }
    } catch {
      case e: AskTimeoutException => false
    }
  }

  private def asyncVerifyTransaction(t: Transaction): Future[Boolean] = {
    val result = Promise[Boolean]
    if (pe.getTransPoolMgr.findTrans(t.id)) {
      result.success(true)
    } else {
      val tmp = BlockVerify.VerifyOneSignOfTrans(t, pe.getSysTag)
      if (tmp._1) {
        result.success(true)
      }
    }
    result.future
  }

  private def asyncVerifyTransactions(block: Block): Boolean = {
    val listOfFuture: Seq[Future[Boolean]] = block.transactions.map(x => {
      asyncVerifyTransaction(x)
    })
    val futureOfList: Future[List[Boolean]] = Future.sequence(listOfFuture.toList)
    var result = true
    futureOfList.map(x => {
      x.foreach(f => {
        if (f) {
          result = false
          break
        }
      })
    })
    result
  }

  private def checkedOfEndorseCondition(block: Block, blocker: String) = {
    if (pe.getSystemStatus == NodeStatus.Endorsing) {
      if (NodeHelp.isCandidateNow(pe.getSysTag, pe.getNodeMgr.getCandidator)) {
        if (block.previousBlockHash.toStringUtf8 == pe.getCurrentBlockHash) {
          if (NodeHelp.isBlocker(blocker, pe.getBlocker.blocker)) {
            //符合要求，可以进行交易以及交易结果进行校验
            val bv = BlockVerify.VerifyAllEndorseOfBlock(block, pe.getSysTag)
            if (bv._1) {
              //出块人的签名验证正确
              if (!hasRepeatOfTrans(block.transactions)) {
                //  没有重复交易
                if (asyncVerifyTransactions(block)) {
                  //交易签名验证正确
                  if (ExecuteTransactionOfBlock(block)) {
                    //交易执行结果一致
                    sendEvent(EventType.PUBLISH_INFO, mediator, selfAddr, Topic.Block, Event.Action.ENDORSEMENT)
                    sender ! ResultOfEndorsed(true, BlockHelp.SignBlock(block, pe.getSysTag), block.hashOfBlock.toStringUtf8())
                  } else {
                    //交易执行结果不一致
                    sender ! ResultOfEndorsed(true, null, block.hashOfBlock.toStringUtf8())
                  }
                } else {
                  //交易签名验证错误
                  sender ! ResultOfEndorsed(true, null, block.hashOfBlock.toStringUtf8())
                }
              } else {
                //存在重复交易
                sender ! ResultOfEndorsed(true, null, block.hashOfBlock.toStringUtf8())
              }
            } else {
              //出块人的签名验证错误
              sender ! ResultOfEndorsed(true, null, block.hashOfBlock.toStringUtf8())
            }
          } else {
            //产生待背书的节点不是出块节点
            sender ! ResultOfEndorsed(true, null, block.hashOfBlock.toStringUtf8())
          }
        } else {
          //背书节点不是当前节点的后续块
          sender ! ResultOfEndorsed(true, null, block.hashOfBlock.toStringUtf8())
        }
      } else {
        //节点不是共识节点
        sender ! ResultOfEndorsed(true, null, block.hashOfBlock.toStringUtf8())
      }
    } else {
      //节点不是背书节点，如果是自己接到自己的背书请求也会走这个分支
      sender ! ResultOfEndorsed(true, null, block.hashOfBlock.toStringUtf8())
    }
  }

  override def receive = {
    //Endorsement block
    case EndorsementInfo(block, blocker) =>
      checkedOfEndorseCondition(block, blocker)
    case _ => //ignore
  }

}