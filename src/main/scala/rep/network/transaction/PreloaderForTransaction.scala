/*
 * Copyright  2019 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BA SIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package rep.network.transaction

import akka.actor.Props
import akka.pattern.{AskTimeoutException, ask}
import akka.util.Timeout
import com.google.protobuf.ByteString
import rep.api.rest.ResultCode
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.base.ModuleBase
import rep.network.consensus.common.MsgOfConsensus.{PreTransBlock, PreTransBlockOfCache, PreTransBlockResult, preTransBlockResultOfCache}
import rep.network.consensus.util.BlockHelp
import rep.sc.SandboxDispatcher.{DoTransaction, DoTransactionOfCache}
import rep.sc.TypeOfSender
import rep.network.module.ModuleActorType
import rep.utils._

import scala.util.control.Breaks.{break, breakable}
import scala.collection.mutable
import scala.concurrent._
import scala.util.Random
import rep.proto.rc2._

/**
 * Created by jiangbuyun on 2018/03/19.
 * 执行预执行actor
 */


object PreloaderForTransaction {
  def props(name: String): Props = Props(classOf[PreloaderForTransaction], name)
}

class PreloaderForTransaction(moduleName: String) extends ModuleBase(moduleName) {
  import scala.concurrent.duration._

  implicit val timeout = Timeout((pe.getRepChainContext.getTimePolicy.getTimeoutPreload * 2).seconds)

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("PreloaderForTransaction Start"))
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
      case te: TimeoutException =>
        val es = createErrorData(ts, Option(akka.actor.Status.Failure(te)))
        (1, es.toSeq)
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
      case te: TimeoutException =>
        val es = createErrorData(ts, Option(akka.actor.Status.Failure(te)))
        (1, es.toSeq)
    } finally {
      pe.removeTrans(cacheIdentifier)
    }
  }

  private def createErrorData(ts: scala.collection.Seq[Transaction], err: Option[akka.actor.Status.Failure]): Array[TransactionResult] = {
    var rs = scala.collection.mutable.ArrayBuffer[TransactionResult]()
    ts.foreach(t => {
      rs += new TransactionResult(t.id, Map.empty,Map.empty,Map.empty, Option(ActionResult(ResultCode.Transaction_Exception_In_Preload, err.get.cause.getMessage)))
    })
    rs.toArray
  }

  private def AssembleTransResult(block: Block, transResult: Seq[TransactionResult], db_indentifier: String): Option[Block] = {
    try {
      var rBlock = block.withTransactionResults(transResult)
      //val stateHashStr = Sha256.hashstr(Array.concat(pe.getSystemCurrentChainStatus.currentStateHash.toByteArray(), SerializeUtils.serialise(transResult)))
      //rblock = rblock.withStateHash(ByteString.copyFromUtf8(statehashstr))
      //RepLogger.error(RepLogger.Business_Logger, this.getLogMsgPrefix( s" current block height=${block.height},trans create serial: ${outputTransSerialOfBlock(block,rblock)}"))
      if (rBlock.getHeader.hashPresent == _root_.com.google.protobuf.ByteString.EMPTY) {
        //如果没有当前块的hash在这里生成，如果是背书已经有了hash不再进行计算
        rBlock = BlockHelp.AddBlockHeaderHash(rBlock,pe.getRepChainContext.getHashTool)
      }
      Some(rBlock)
    } catch {
      case e: RuntimeException =>
        RepLogger.error(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s" AssembleTransResult error, error: ${e.getMessage}"))
        None
    } finally {
      pe.getRepChainContext.freeBlockPreloadInstance(db_indentifier)
    }
  }

  def Handler(ts: Seq[Transaction], preLoadTrans: mutable.HashMap[String, Transaction], db_indentifier: String): Seq[TransactionResult] = {
    var transResult1 = Seq.empty[TransactionResult]
    try {
      val result = ExecuteTransactions(ts, db_indentifier)
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
    var rts = Seq.empty[Transaction]
    if (startIndex < ts.length) {
      val len = ts.length - 1
      val ft = ts(startIndex)
      val fCid = IdTool.getTXCId(ft)
      rts = rts :+ ft
      var tmpIdx = startIndex + 1
      breakable(
        for (i <- tmpIdx to len) {
          val t = ts(i)
          if (isSameContractInvoke(t, fCid)) {
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
      if ((block.getHeader.hashPrevious.toStringUtf8() == pe.getCurrentBlockHash || block.getHeader.hashPrevious == ByteString.EMPTY) &&
        block.getHeader.height == (pe.getCurrentHeight + 1)) {
        var preLoadTrans = mutable.HashMap.empty[String, Transaction]
        var transResult : Seq[TransactionResult] = Seq.empty[TransactionResult]
        val dbtag = prefixOfDbTag
        var curBlockHash = "temp_" + Random.nextInt(100)
        if (block.getHeader.hashPresent != ByteString.EMPTY) {
          curBlockHash = block.getHeader.hashPresent.toStringUtf8
        }
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s" preload db instance name, name: ${dbtag},height:${block.getHeader.height}"))
        //确保提交的时候顺序执行
        RepTimeTracer.setStartTime(pe.getSysTag, "PreloadTrans-exe", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)

        var preloadResult = true
        var loop = 0
        val limitlen = block.transactions.length
        breakable(
          while (loop < limitlen) {
            val data = getSameCid(block.transactions, loop)
            if (!data._2.isEmpty) {
              var ts = Handler(data._2, preLoadTrans, dbtag)
              if (!ts.isEmpty) {
                transResult = (transResult ++ ts)
              } else {
                preloadResult = false
                break
              }
              loop = data._1
            } else {
              loop = data._1 + 1
            }
          })

        RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans-exe", System.currentTimeMillis(), block.getHeader.height, block.transactions.size)
        if (preloadResult) {
          RepTimeTracer.setStartTime(pe.getSysTag, "PreloadTrans-assemble", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
          var newblock = AssembleTransResult(block, transResult, dbtag)
          RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans-assemble", System.currentTimeMillis(), block.getHeader.height, block.transactions.size)
          //全部交易执行完成
          sender ! PreTransBlockResult(newblock.get, true)
        } else {
          RepLogger.error(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s" All Transaction failed, error: ${block.getHeader.height}"))
          sender ! PreTransBlockResult(null, false)
        }
        RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans-inner", System.currentTimeMillis(), block.getHeader.height, block.transactions.size)
      }

    case PreTransBlockOfCache(blockIdentifierInCache, prefixOfDbTag) =>
      RepTimeTracer.setStartTime(pe.getSysTag, "PreloadTrans-inner", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
      var block = pe.getBlock(blockIdentifierInCache)
      if (block == null) {
        sender ! preTransBlockResultOfCache(false)
      } else {
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("entry preload"))
        if ((block.getHeader.hashPrevious.toStringUtf8() == pe.getCurrentBlockHash || block.getHeader.hashPrevious == ByteString.EMPTY) &&
          block.getHeader.height == (pe.getCurrentHeight + 1)) {
          var preLoadTrans = mutable.HashMap.empty[String, Transaction]
          var transResult : Seq[TransactionResult] = Seq.empty[TransactionResult]
          val dbtag = prefixOfDbTag //prefixOfDbTag+"_"+moduleName+"_"+block.transactions.head.id
          var curBlockHash = "temp_" + Random.nextInt(100)
          if (block.getHeader.hashPresent != _root_.com.google.protobuf.ByteString.EMPTY) {
            curBlockHash = block.getHeader.hashPresent.toStringUtf8
          }
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s" preload db instance name, name: ${dbtag},height:${block.getHeader.height}"))
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
          RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans-exe", System.currentTimeMillis(), block.getHeader.height, block.transactions.size)
          if (preloadReuslt) {
            RepTimeTracer.setStartTime(pe.getSysTag, "PreloadTrans-assemble", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
            var newblock = AssembleTransResult(block, transResult, dbtag)
            RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans-assemble", System.currentTimeMillis(), block.getHeader.height, block.transactions.size)
            //全部交易执行完成
            pe.addBlock(blockIdentifierInCache,newblock.get)
            sender ! preTransBlockResultOfCache(true)
          } else {
            RepLogger.error(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s" All Transaction failed, error: ${block.getHeader.height}"))
            sender ! preTransBlockResultOfCache(false)
          }
          RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans-inner", System.currentTimeMillis(), block.getHeader.height, block.transactions.size)
        }
      }

    case _ => //ignore
  }
}