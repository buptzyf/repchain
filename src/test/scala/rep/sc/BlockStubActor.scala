package rep.sc

import akka.util.Timeout

import scala.concurrent.duration._
import akka.pattern.ask
import akka.pattern.AskTimeoutException
import rep.network.module.ModuleActorType
import scala.concurrent._
import akka.actor.{Actor, ActorRef, Props}
import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import rep.app.conf.{SystemProfile, TimePolicy}
import rep.network.base.ModuleBase
import rep.network.consensus.common.MsgOfConsensus.{PreTransBlock, PreTransBlockResult}
import rep.network.tools.PeerExtension
import rep.proto.rc2._
import rep.network.consensus.common.MsgOfConsensus.BlockRestore
import rep.sc.SandboxDispatcher.DoTransaction
import rep.sc.Sandbox.DoTransactionResult
import rep.storage.ImpDataPreloadMgr

import rep.utils._

import scala.collection.mutable
import akka.pattern.AskTimeoutException
import rep.crypto.Sha256
import rep.log.RepLogger
import akka.routing._
import rep.network.autotransaction.Topic
import rep.network.transaction.PreloaderForTransaction;


object BlockStubActor {

  def props(name: String): Props = Props(classOf[BlockStubActor], name)

  case class WriteBlockStub(trans: Seq[Transaction])

}

class BlockStubActor(moduleName: String) extends ModuleBase(moduleName) {

  import context.dispatcher
  import scala.concurrent.duration._
  import rep.utils.IdTool
  import rep.sc.BlockStubActor._
  import rep.network.consensus.cfrd.block.BlockerOfCFRD
  import rep.network.consensus.util.BlockHelp
  import rep.network.persistence.IStorager.{SourceOfBlock}

  implicit val timeout = Timeout(6.seconds)

  private def ExecuteTransactionOfBlock(block: Block): Block = {
    try {
      //val ref = pe.getActorRef(ActorType.preloaderoftransaction)
      val ref = pe.getActorRef(ModuleActorType.ActorType.dispatchofpreload)
      //val ref1 = this.transpreload
      val future = ref ? PreTransBlock(block, "preload")
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

  private def CreateBlock(trans: Seq[Transaction]): Block = {
    //todo 交易排序
    if (trans.size > SystemProfile.getMinBlockTransNum) {
      var blc = BlockHelp.WaitingForExecutionOfBlock(pe.getCurrentBlockHash, pe.getCurrentHeight + 1, trans)
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,height=${blc.header.get.height},local height=${pe.getCurrentHeight}" + "~" + selfAddr))
      blc = ExecuteTransactionOfBlock(blc)
      if (blc != null) {
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,prelaod success,height=${blc.header.get.height},local height=${pe.getCurrentHeight}" + "~" + selfAddr))
        blc = BlockHelp.AddBlockHash(blc)
        BlockHelp.AddSignToBlock(blc, pe.getSysTag)
      } else {
        null
      }
    } else {
      null
    }
  }

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("BlockStubActor Start"))
  }


  override def receive = {
    case wb: WriteBlockStub =>
      val newblock = CreateBlock(wb.trans)
      if (newblock != null) {
        pe.getActorRef(ModuleActorType.ActorType.storager).forward(BlockRestore(newblock, SourceOfBlock.TEST_PROBE, self))
      }
    case _ => //ignore
  }
}