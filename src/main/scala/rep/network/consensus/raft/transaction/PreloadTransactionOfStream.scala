package rep.network.consensus.raft.transaction

import akka.actor.Props
import com.google.protobuf.ByteString
import rep.app.conf.TimePolicy
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.base.ModuleBase
import rep.network.consensus.common.MsgOfConsensus.{PreTransBlockOfStream, preTransBlockResultOfStream}
import rep.network.consensus.util.BlockHelp
import rep.network.module.ModuleActorType
import rep.network.module.cfrd.CFRDActorType
import rep.proto.rc2.{ActionResult, Block, Transaction, TransactionResult}
import rep.sc.SandboxDispatcher.DoTransactionOfCache
import rep.sc.TypeOfSender
import rep.utils.{IdTool}
import scala.util.Random
import scala.util.control.Breaks.{break, breakable}

object PreloadTransactionOfStream {
  def props(name: String): Props = Props(classOf[PreloadTransactionOfStream], name)
}

class PreloadTransactionOfStream(moduleName: String) extends ModuleBase(moduleName) {

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("PreloadTransactionOfStream Start"))
  }

  private var preBlockHash: String = null
  private var curBlock: Block = null
  private var dbIdentifier: String = null
  private var blockIdentifier: String = null
  private var transactionCacheIdentifier: String = null
  private var preloadStartTime: Long = Long.MaxValue
  private val timeout = TimePolicy.getTimeoutPreload //单位为秒

  private def createErrorData(ts: scala.collection.Seq[Transaction], err: Option[akka.actor.Status.Failure]): Array[TransactionResult] = {
    var rs = scala.collection.mutable.ArrayBuffer[TransactionResult]()
    ts.foreach(t => {
      rs += new TransactionResult(t.id, Map.empty,Map.empty,Map.empty, Option(ActionResult(103, err.get.cause.getMessage))) //new TransactionResult(t.id, null, null, err)
    })
    rs.toArray
  }

  private def AssembleTransResult(block: Block, transResult: Seq[TransactionResult], db_indentifier: String): Option[Block] = {
    try {
      var rBlock = block.withTransactionResults(transResult)
      //val statehashstr = Sha256.hashstr(Array.concat(pe.getSystemCurrentChainStatus.currentStateHash.toByteArray(), SerializeUtils.serialise(transResult)))
      //rblock = rblock.withStateHash(ByteString.copyFromUtf8(statehashstr))
      if (rBlock.getHeader.hashPresent == _root_.com.google.protobuf.ByteString.EMPTY) {
        //如果没有当前块的hash在这里生成，如果是背书已经有了hash不再进行计算
        rBlock = BlockHelp.AddBlockHeaderHash(rBlock,pe.getRepChainContext.getHashTool)
        //this.DbInstance = new DB_Instance_Type(this.DbInstance.tagName,rblock.hashOfBlock.toStringUtf8)
      }
      Some(rBlock)
    } catch {
      case e: RuntimeException =>
        RepLogger.error(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s" AssembleTransResult error, error: ${e.getMessage}"))
        None
    } finally {
      //ImpDataPreloadMgr.Free(pe.getSysTag,db_indentifier)
    }
  }

  private def isSameContractInvoke(t: Transaction, cid: String): Boolean = {
    t.`type`.isChaincodeInvoke && (cid == IdTool.getTXCId(t))
  }

  private def getSameCid(ts: Seq[Transaction], startIndex: Int): (Int, Seq[Transaction]) = {
    var rts = Seq.empty[Transaction]
    if (startIndex < ts.length) {
      val len = ts.length - 1
      val ft = ts(startIndex)
      val fcid = IdTool.getTXCId(ft)
      rts = rts :+ ft
      var tmpIdx = startIndex + 1
      breakable(
        for (i <- tmpIdx to len) {
          val t = ts(i)
          if (isSameContractInvoke(t, fcid)) {
            rts = rts :+ t
            tmpIdx = tmpIdx + 1
          } else {
            tmpIdx = i
            break
          }
        }
      )
      (tmpIdx, rts)
    } else {
      (startIndex, rts)
    }
  }

  private def checkedStatus: Boolean = {
    var r = false
    if (this.curBlock == null) {
      r = true
    } else {
      //当前存在预执行的区块
      if ((System.currentTimeMillis() - this.preloadStartTime) / 1000 > this.timeout) {
        //超时，重置当前Actor状态
        pe.getActorRef(CFRDActorType.ActorType.blocker) ! preTransBlockResultOfStream(this.blockIdentifier, false)
        resetStatus
        r = true
      }
    }
    r
  }

  private def IsAcceptBlock(block: Block): Boolean = {
    var r = false
    if (checkedStatus) {
      if (block.getHeader.hashPrevious == ByteString.EMPTY) {
        //创世块直接进入
        r = true
      } else {
        if (this.preBlockHash != null) {
          if (block.getHeader.hashPrevious.toStringUtf8 == this.preBlockHash) {
            r = true
          } else {
            if (block.getHeader.hashPrevious.toStringUtf8 == pe.getCurrentBlockHash) {
              r = true
            }
          }
        } else {
          if (block.getHeader.hashPrevious.toStringUtf8 == pe.getCurrentBlockHash) {
            r = true
          }
        }
      }
    }
    r
  }

  private def resetStatus = {
    pe.removeTrans(this.transactionCacheIdentifier)
    curBlock = null
    dbIdentifier = null
    blockIdentifier = null
    transactionCacheIdentifier = null
    preloadStartTime = Long.MaxValue
  }


  override def receive = {
    case ts: Seq[TransactionResult] =>
      pe.removeTrans(this.transactionCacheIdentifier)
      RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans-exe", System.currentTimeMillis(), this.curBlock.getHeader.height, this.curBlock.transactions.size)
      if (ts.size > 0) {
        RepTimeTracer.setStartTime(pe.getSysTag, "PreloadTrans-assemble", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
        var newblock = AssembleTransResult(this.curBlock, ts, this.dbIdentifier)
        //全部交易执行完成
        pe.addBlock(this.blockIdentifier, newblock.get)
        pe.getActorRef(CFRDActorType.ActorType.blocker) ! preTransBlockResultOfStream(this.blockIdentifier, true)
        this.preBlockHash = newblock.get.getHeader.hashPresent.toStringUtf8
        RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans-assemble", System.currentTimeMillis(), this.curBlock.getHeader.height, this.curBlock.transactions.size)
        this.resetStatus
      } else {
        pe.getActorRef(CFRDActorType.ActorType.blocker) ! preTransBlockResultOfStream(this.blockIdentifier, false)
        this.resetStatus
      }
    case PreTransBlockOfStream(blockIdentifier, prefixOfDbTag) =>
      RepTimeTracer.setStartTime(pe.getSysTag, "PreloadTrans-exe", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
      var block = pe.getBlock(blockIdentifier)
      if (block == null) {
        sender ! preTransBlockResultOfStream(blockIdentifier, false)
      } else {
        if (this.IsAcceptBlock(block)) {
          //发出交易给合约容器
          this.dbIdentifier = prefixOfDbTag
          this.blockIdentifier = blockIdentifier
          this.curBlock = block
          this.preloadStartTime = System.currentTimeMillis()
          this.transactionCacheIdentifier = "preloadCache_" + Random.nextInt(10000)
          pe.addTrans(this.transactionCacheIdentifier, this.curBlock.transactions)
          pe.getActorRef(ModuleActorType.ActorType.transactiondispatcher) ! new DoTransactionOfCache(this.transactionCacheIdentifier, this.dbIdentifier, TypeOfSender.FromPreloader)
        }
      }
    case _ => //ignore
  }
}