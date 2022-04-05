package rep.network.consensus.cfrdinstream.transaction

import akka.actor.Props
import akka.pattern.AskTimeoutException
import akka.util.Timeout
import com.google.protobuf.ByteString
import rep.app.conf.TimePolicy
import rep.crypto.Sha256
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.base.ModuleBase
import rep.network.consensus.common.MsgOfConsensus.{PreTransBlock, PreTransBlockOfCache, PreTransBlockResult, preTransBlockResultOfCache}
import rep.network.consensus.util.BlockHelp
import rep.network.module.ModuleActorType
import rep.proto.rc2.{ActionResult, Block, Transaction, TransactionResult}
import rep.sc.SandboxDispatcher.{DoTransaction, DoTransactionOfCache}
import rep.sc.TypeOfSender
import rep.utils.{IdTool, SerializeUtils}
import akka.pattern.{AskTimeoutException, ask}
import scala.collection.mutable
import scala.concurrent.{Await, TimeoutException}
import scala.util.Random
import scala.util.control.Breaks.{break, breakable}

object PreloaderForTransactionInStream{
  def props(name: String): Props = Props(classOf[PreloaderForTransactionInStream], name)
}

class PreloaderForTransactionInStream (moduleName: String) extends ModuleBase(moduleName) {

  import scala.collection.breakOut
  import scala.concurrent.duration._

  implicit val timeout = Timeout((TimePolicy.getTimeoutPreload * 2).seconds)
  //case class DB_Instance_Type(tagName:String,BlockHash:String)

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("PreloaderForTransactionInStream Start"))
  }

  private def ExecuteTransactions(ts: Seq[Transaction], db_identifier: String): (Int, Seq[TransactionResult]) = {
    try {
      val future1 = pe.getActorRef(ModuleActorType.ActorType.transactiondispatcher) ? new DoTransaction(ts, db_identifier, TypeOfSender.FromPreloader)
      val result = Await.result(future1, timeout.duration).asInstanceOf[Seq[TransactionResult]]
      (0, result)
    } catch {
      case e: AskTimeoutException =>
        val es = createErrorData(ts, Option(akka.actor.Status.Failure(e)))
        (1, es.toSeq)
      /*(1, new DoTransactionResult(t.id, null,
        scala.collection.mutable.ListBuffer.empty[OperLog].toList,
        Option(akka.actor.Status.Failure(e))))*/
      case te: TimeoutException =>
        val es = createErrorData(ts, Option(akka.actor.Status.Failure(te)))
        (1, es.toSeq)
      /*(1, new DoTransactionResult(t.id, null,
        scala.collection.mutable.ListBuffer.empty[OperLog].toList,
        Option(akka.actor.Status.Failure(te))))*/
    }
  }

  private def ExecuteTransactionsFromCache(ts: Seq[Transaction], db_identifier: String): (Int, Seq[TransactionResult]) = {
    val cacheIdentifier = "preloadCache_" + Random.nextInt(10000)
    try {
      pe.addTrans(cacheIdentifier, ts)
      val future1 = pe.getActorRef(ModuleActorType.ActorType.transactiondispatcher) ? new DoTransactionOfCache(cacheIdentifier, db_identifier, TypeOfSender.FromPreloader)
      val result = Await.result(future1, timeout.duration).asInstanceOf[Seq[TransactionResult]]
      (0, result)
    } catch {
      case e: AskTimeoutException =>
        val es = createErrorData(ts, Option(akka.actor.Status.Failure(e)))
        (1, es.toSeq)
      /*(1, new DoTransactionResult(t.id, null,
        scala.collection.mutable.ListBuffer.empty[OperLog].toList,
        Option(akka.actor.Status.Failure(e))))*/
      case te: TimeoutException =>
        val es = createErrorData(ts, Option(akka.actor.Status.Failure(te)))
        (1, es.toSeq)
      /*(1, new DoTransactionResult(t.id, null,
        scala.collection.mutable.ListBuffer.empty[OperLog].toList,
        Option(akka.actor.Status.Failure(te))))*/
    } finally {
      pe.removeTrans(cacheIdentifier)
    }
  }

  private def createErrorData(ts: scala.collection.Seq[Transaction], err: Option[akka.actor.Status.Failure]): Array[TransactionResult] = {
    var rs = scala.collection.mutable.ArrayBuffer[TransactionResult]()
    ts.foreach(t => {
      rs += new TransactionResult(t.id, Map.empty, Map.empty, Option(ActionResult(103, err.get.cause.getMessage))) //new TransactionResult(t.id, null, null, err)
    })
    rs.toArray
  }



  private def AssembleTransResult(block: Block, transResult: Seq[TransactionResult], db_indentifier: String): Option[Block] = {
    try {
      var rblock = block
      //状态读写集合合并,TODO 剔除重复
      transResult.foreach(tr => {
        rblock = block.addAllStatesSet(tr.statesSet)
        rblock = block.addAllStatesGet(tr.statesGet)
      })
      val statehashstr = Sha256.hashstr(Array.concat(pe.getSystemCurrentChainStatus.currentStateHash.toByteArray(), SerializeUtils.serialise(transResult)))
      rblock = rblock.withHeader(rblock.header.get.withHashPresent(ByteString.copyFromUtf8(statehashstr)))
      //RepLogger.error(RepLogger.Business_Logger, this.getLogMsgPrefix( s" current block height=${block.height},trans create serial: ${outputTransSerialOfBlock(block,rblock)}"))
      if (rblock.header.get.hashPresent == _root_.com.google.protobuf.ByteString.EMPTY) {
        //如果没有当前块的hash在这里生成，如果是背书已经有了hash不再进行计算
        rblock = BlockHelp.AddBlockHash(rblock)
        //this.DbInstance = new DB_Instance_Type(this.DbInstance.tagName,rblock.hashOfBlock.toStringUtf8)
      }
      Some(rblock)
    } catch {
      case e: RuntimeException =>
        RepLogger.error(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s" AssembleTransResult error, error: ${e.getMessage}"))
        None
    } finally {
      //ImpDataPreloadMgr.Free(pe.getSysTag,db_indentifier)
    }
  }



  def Handler(ts: Seq[Transaction], preLoadTrans: mutable.HashMap[String, Transaction], db_indentifier: String): Seq[TransactionResult] = {
    var transResult1 = Seq.empty[rep.protos.peer.TransactionResult]
    try {
      val result = ExecuteTransactions(ts, db_indentifier)
      //val result = ExecuteTransactionsFromCache(ts, db_indentifier)
      result._2
    } catch {
      case e: RuntimeException =>
        Seq.empty
    }
  }

  private def isSameContractInvoke(t: Transaction, cid: String): Boolean = {
    t.`type`.isChaincodeInvoke && (cid == IdTool.getTXCId(t))
  }

  private def getSameCid(ts: Seq[Transaction], startIndex: Int): (Int, Seq[Transaction]) = {
    var rts = Seq.empty[rep.proto.rc2.Transaction]
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

  override def receive = {
    case PreTransBlock(block, prefixOfDbTag) =>
      RepTimeTracer.setStartTime(pe.getSysTag, "PreloadTrans-inner", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("entry preload"))
      //if ((block.previousBlockHash.toStringUtf8() == pe.getCurrentBlockHash || block.previousBlockHash == ByteString.EMPTY) &&
      //  block.height == (pe.getCurrentHeight + 1)) {
        var preLoadTrans = mutable.HashMap.empty[String, Transaction]

        var transResult = Seq.empty[rep.proto.rc2.TransactionResult]
        val dbtag = prefixOfDbTag //prefixOfDbTag+"_"+moduleName+"_"+block.transactions.head.id


        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s" preload db instance name, name: ${dbtag},height:${block.header.get.height}"))
        //确保提交的时候顺序执行
        RepTimeTracer.setStartTime(pe.getSysTag, "PreloadTrans-exe", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)

        var preloadReuslt = true
        var loop = 0
        val limitlen = block.transactions.length
        breakable(
          while (loop < limitlen) {
            val data = getSameCid(block.transactions, loop)
            if (!data._2.isEmpty) {
              var ts = Handler(data._2, preLoadTrans, dbtag)
              if (!ts.isEmpty) {
                //transResult = (transResult :+ ts)
                transResult = (transResult ++ ts)
              } else {
                preloadReuslt = false
                break
              }
              loop = data._1
            } else {
              loop = data._1 + 1
            }
          })

        RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans-exe", System.currentTimeMillis(), block.header.get.height, block.transactions.size)
        if (preloadReuslt) {
          RepTimeTracer.setStartTime(pe.getSysTag, "PreloadTrans-assemble", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
          var newblock = AssembleTransResult(block, transResult, dbtag)
          RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans-assemble", System.currentTimeMillis(), block.header.get.height, block.transactions.size)
          //全部交易执行完成
          sender ! PreTransBlockResult(newblock.get, true)
        } else {
          RepLogger.error(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s" All Transaction failed, error: ${block.header.get.height}"))
          sender ! PreTransBlockResult(null, false)
        }
        RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans-inner", System.currentTimeMillis(), block.header.get.height, block.transactions.size)
      //}

    case PreTransBlockOfCache(blockIdentifierInCache, prefixOfDbTag) =>
      RepTimeTracer.setStartTime(pe.getSysTag, "PreloadTrans-inner", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
      var block = pe.getBlock(blockIdentifierInCache)
      if (block == null) {
        sender ! preTransBlockResultOfCache(false)
      } else {
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("entry preload"))
        //if ((block.previousBlockHash.toStringUtf8() == pe.getCurrentBlockHash || block.previousBlockHash == ByteString.EMPTY) &&
        //  block.height == (pe.getCurrentHeight + 1)) {
          var preLoadTrans = mutable.HashMap.empty[String, Transaction]
          var transResult = Seq.empty[rep.proto.rc2.TransactionResult]
          val dbtag = prefixOfDbTag //prefixOfDbTag+"_"+moduleName+"_"+block.transactions.head.id

          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s" preload db instance name, name: ${dbtag},height:${block.header.get.height}"))
          //确保提交的时候顺序执行
          RepTimeTracer.setStartTime(pe.getSysTag, "PreloadTrans-exe", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)

          var preloadReuslt = true
          var loop = 0
          val limitlen = block.transactions.length
          breakable(
            while (loop < limitlen) {
              val data = getSameCid(block.transactions, loop)
              if (!data._2.isEmpty) {
                var ts = Handler(data._2, preLoadTrans, dbtag)
                if (!ts.isEmpty) {
                  //transResult = (transResult :+ ts)
                  transResult = (transResult ++ ts)
                } else {
                  preloadReuslt = false
                  break
                }
                loop = data._1
              } else {
                loop = data._1 + 1
              }
            })
          RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans-exe", System.currentTimeMillis(), block.header.get.height, block.transactions.size)
          if (preloadReuslt) {
            RepTimeTracer.setStartTime(pe.getSysTag, "PreloadTrans-assemble", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
            var newblock = AssembleTransResult(block, transResult, dbtag)
            RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans-assemble", System.currentTimeMillis(), block.header.get.height, block.transactions.size)
            //全部交易执行完成
            pe.addBlock(blockIdentifierInCache,newblock.get)
            sender ! preTransBlockResultOfCache(true)
          } else {
            RepLogger.error(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s" All Transaction failed, error: ${block.header.get.height}"))
            sender ! preTransBlockResultOfCache(false)
          }
          RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans-inner", System.currentTimeMillis(), block.header.get.height, block.transactions.size)
        //}
      }

    case _ => //ignore
  }
}