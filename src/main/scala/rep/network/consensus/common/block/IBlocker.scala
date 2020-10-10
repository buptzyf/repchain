package rep.network.consensus.common.block

import akka.actor.Props
import akka.pattern.{AskTimeoutException, ask}
import akka.util.Timeout
import com.google.protobuf.ByteString
import rep.app.conf.{SystemProfile, TimePolicy}
import rep.crypto.Sha256
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.base.ModuleBase
import rep.network.consensus.common.MsgOfConsensus.{PreTransBlock, PreTransBlockResult}
import rep.network.consensus.util.BlockHelp
import rep.network.module.ModuleActorType
import rep.protos.peer.{Block, TransactionResult}
import rep.storage.ImpDataAccess
import rep.utils.SerializeUtils

import scala.concurrent.Await
import scala.util.control.Breaks.{break, breakable}



/**
 * Created by jiangbuyun on 2020/03/17.
 * CFRD管理的actor
 */

object IBlocker {
  def props(name: String): Props = Props(classOf[IBlocker], name)
}

abstract class IBlocker(moduleName: String) extends ModuleBase(moduleName) {
  import rep.protos.peer.Transaction

  import scala.collection.mutable.ArrayBuffer
  import scala.concurrent.duration._

  protected val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
  implicit val timeout = Timeout((TimePolicy.getTimeoutPreload * 6).seconds)

  protected def CollectedTransOfBlock(start: Int, num: Int, limitsize: Int): Seq[Transaction] = {
    //var result = ArrayBuffer.empty[Transaction]
    try {
      val tmplist = pe.getTransPoolMgr.getTransListClone(start, num, pe.getSysTag)
      //if (tmplist.size > 0) {
        val currenttime = System.currentTimeMillis() / 1000
        /*var transsize = 0
        breakable(
          tmplist.foreach(f => {
            transsize += f.toByteArray.size
            if (transsize * 3 > limitsize) {
              //区块的长度限制
              RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"block too length,txid=${f.id}" + "~" + selfAddr))
              break
            } else {
              f +=: result
            }
          }))
        if (result.isEmpty && tmplist.size >= SystemProfile.getMinBlockTransNum) {
          result = CollectedTransOfBlock(start + num, num, limitsize)
        }*/
        tmplist
      //}
      //else{
        //CollectedTransOfBlock(start + num, num, limitsize)
      //}
    } finally {
    }
  }



  protected def ExecuteTransactionOfBlock(block: Block): Block = {
    try {
      val future = pe.getActorRef(ModuleActorType.ActorType.dispatchofpreload) ? PreTransBlock(block, "preload")
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

  protected def ExecuteTransactionOfBlockInStream(block: Block,dbTag:String): Block = {
    try {
      val future = pe.getActorRef(ModuleActorType.ActorType.dispatchofpreload) ? PreTransBlock(block, "preload-"+dbTag)
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

  /*protected def PackedBlock(start:Int = 0, h:Long) : Block = {
    RepTimeTracer.setStartTime(pe.getSysTag, "Block", System.currentTimeMillis(), h, 0)
    RepTimeTracer.setStartTime(pe.getSysTag, "createBlock", System.currentTimeMillis(), h, 0)
    RepTimeTracer.setStartTime(pe.getSysTag, "collectTransToBlock", System.currentTimeMillis(), h, 0)
    val trans = CollectedTransOfBlock(start, SystemProfile.getLimitBlockTransNum, SystemProfile.getBlockLength).reverse.toSeq
    //todo 交易排序
    if (trans.size >= SystemProfile.getMinBlockTransNum) {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,CollectedTransOfBlock success,height=${h}" + "~" + selfAddr))
      RepTimeTracer.setEndTime(pe.getSysTag, "collectTransToBlock", System.currentTimeMillis(), h, trans.size)

      var blc = BlockHelp.WaitingForExecutionOfBlock(pe.getBlocker.voteBlockHash, h, trans.toSeq)
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,height=${blc.height},local height=${h}" + "~" + selfAddr))
      RepTimeTracer.setStartTime(pe.getSysTag, "PreloadTrans", System.currentTimeMillis(), blc.height, blc.transactions.size)
      blc = ExecuteTransactionOfBlock(blc)
      if (blc != null) {
        RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans", System.currentTimeMillis(), blc.height, blc.transactions.size)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,prelaod success,height=${blc.height}" + "~" + selfAddr))
        blc = BlockHelp.AddBlockHash(blc)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,AddBlockHash success,height=${blc.height}" + "~" + selfAddr))
        BlockHelp.AddSignToBlock(blc, pe.getSysTag)
      } else {
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("create new block error,preload error" + "~" + selfAddr))
        PackedBlock(start + trans.size, h)
      }
    } else {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("create new block error,trans count error" + "~" + selfAddr))
      null
    }
  }*/

  protected def PackedBlock(start: Int = 0): Block = {
    RepTimeTracer.setStartTime(pe.getSysTag, "Block", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
    RepTimeTracer.setStartTime(pe.getSysTag, "createBlock", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
    RepTimeTracer.setStartTime(pe.getSysTag, "collectTransToBlock", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
    val trans = CollectedTransOfBlock(start,SystemProfile.getLimitBlockTransNum, SystemProfile.getBlockLength).reverse
    //todo 交易排序
    if (trans.size >= SystemProfile.getMinBlockTransNum) {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,CollectedTransOfBlock success,height=${pe.getBlocker.VoteHeight + 1},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
      RepTimeTracer.setEndTime(pe.getSysTag, "collectTransToBlock", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, trans.size)
      //此处建立新块必须采用抽签模块的抽签结果来进行出块，否则出现刚抽完签，马上有新块的存储完成，就会出现错误
      var blc = BlockHelp.WaitingForExecutionOfBlock(pe.getBlocker.voteBlockHash, pe.getBlocker.VoteHeight + 1, trans)
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,height=${blc.height},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
      RepTimeTracer.setStartTime(pe.getSysTag, "PreloadTrans", System.currentTimeMillis(), blc.height, blc.transactions.size)
      blc = ExecuteTransactionOfBlock(blc)
      if (blc != null) {
        RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans", System.currentTimeMillis(), blc.height, blc.transactions.size)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,prelaod success,height=${blc.height},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
        //块hash在预执行中生成
        //blc = BlockHelp.AddBlockHash(blc)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,AddBlockHash success,height=${blc.height},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
        BlockHelp.AddSignToBlock(blc, pe.getSysTag)
      } else {
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("create new block error,preload error" + "~" + selfAddr))
        PackedBlock(start + trans.size)
      }
    } else {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("create new block error,trans count error" + "~" + selfAddr))
      null
    }
  }

  //zhjtps
  /*protected def PackedBlockTPS(ts : Seq[Transaction], trs : Seq[TransactionResult],start: Int = 0): Block = {
    var blc = BlockHelp.WaitingForExecutionOfBlock(pe.getBlocker.voteBlockHash, pe.getBlocker.VoteHeight + 1, ts.toSeq)
    if (blc != null) {
      blc = blc.withVersion(5).withTransactionResults(trs)
      val statehashstr = Sha256.hashstr(Array.concat(pe.getSystemCurrentChainStatus.currentStateHash.toByteArray() , SerializeUtils.serialise(trs)))
      blc = blc.withStateHash(ByteString.copyFromUtf8(statehashstr))
      blc = BlockHelp.AddBlockHash(blc)
      BlockHelp.AddSignToBlock(blc, pe.getSysTag)
    } else {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("create new block error,trans count error" + "~" + selfAddr))
      null
    }
  }*/

}
