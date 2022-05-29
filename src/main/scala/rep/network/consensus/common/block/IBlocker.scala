package rep.network.consensus.common.block

import akka.actor.Props
import akka.pattern.{AskTimeoutException, ask}
import akka.util.Timeout
import rep.app.conf.{ TimePolicy}
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.base.ModuleBase
import rep.network.consensus.common.MsgOfConsensus.{PreTransBlock, PreTransBlockResult}
import rep.network.consensus.util.BlockHelp
import rep.network.module.ModuleActorType
import rep.storage.chain.block.BlockSearcher
import scala.concurrent.Await
import rep.proto.rc2.Block

/**
 * Created by jiangbuyun on 2020/03/17.
 * CFRD管理的actor
 */

object IBlocker {
  def props(name: String): Props = Props(classOf[IBlocker], name)
}

abstract class IBlocker(moduleName: String) extends ModuleBase(moduleName) {
  import scala.concurrent.duration._

  protected val blockSearch: BlockSearcher = pe.getRepChainContext.getBlockSearch
  implicit val timeout = Timeout((TimePolicy.getTimeoutPreload * 6).seconds)


  protected def ExecuteTransactionOfBlock(block: Block): Block = {
    try {
      val future = pe.getActorRef(ModuleActorType.ActorType.dispatchofpreload) ? PreTransBlock(block, "preload-"+block.transactions(0).id)
      val result = Await.result(future, timeout.duration).asInstanceOf[PreTransBlockResult]
      if (result.result) {
        result.blc
      } else {
        null
      }
    } catch {
      case e: AskTimeoutException => null
    }finally {
      pe.getRepChainContext.freeBlockPreloadInstance("preload-"+block.transactions(0).id)
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

  private val config = pe.getRepChainContext.getConfig

  protected def PackedBlock(start: Int = 0): Block = {
    RepTimeTracer.setStartTime(pe.getSysTag, "Block", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
    RepTimeTracer.setStartTime(pe.getSysTag, "createBlock", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
    RepTimeTracer.setStartTime(pe.getSysTag, "collectTransToBlock", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
    val trans = pe.getRepChainContext.getTransactionPool.packageTransactionToBlock
    //todo 交易排序
    if (trans.size >= config.getMinTransactionNumberOfBlock) {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,CollectedTransOfBlock success,height=${pe.getBlocker.VoteHeight + 1},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
      RepTimeTracer.setEndTime(pe.getSysTag, "collectTransToBlock", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, trans.size)
      //此处建立新块必须采用抽签模块的抽签结果来进行出块，否则出现刚抽完签，马上有新块的存储完成，就会出现错误
      var blc = BlockHelp.buildBlock(pe.getBlocker.voteBlockHash, pe.getBlocker.VoteHeight + 1, trans)
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,height=${blc.getHeader.height},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
      RepTimeTracer.setStartTime(pe.getSysTag, "PreloadTrans", System.currentTimeMillis(), blc.getHeader.height, blc.transactions.size)
      blc = ExecuteTransactionOfBlock(blc)
      if (blc != null) {
        RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans", System.currentTimeMillis(), blc.getHeader.height, blc.transactions.size)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,prelaod success,height=${blc.getHeader.height},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
        //块hash在预执行中生成
        //blc = BlockHelp.AddBlockHash(blc)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,AddBlockHash success,height=${blc.getHeader.height},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
        blc.withHeader(BlockHelp.AddHeaderSignToBlock(blc.getHeader, pe.getSysTag,pe.getRepChainContext.getSignTool))
      } else {
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("create new block error,preload error" + "~" + selfAddr))
        //PackedBlock(start + trans.size)
        null
      }
    } else {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("create new block error,transaction number too less" + "~" + selfAddr))
      null
    }
  }

}
