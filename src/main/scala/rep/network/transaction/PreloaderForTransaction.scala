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
import rep.app.conf.TimePolicy
import rep.crypto.Sha256
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.base.ModuleBase
import rep.network.consensus.common.MsgOfConsensus.{PreTransBlock, PreTransBlockResult}
import rep.network.consensus.util.BlockHelp
import rep.protos.peer._
import rep.sc.Sandbox.DoTransactionResult
import rep.sc.SandboxDispatcher.{DoTransaction, DoTransactions}
import rep.sc.TypeOfSender
import rep.storage.ImpDataPreloadMgr
import rep.network.module.ModuleActorType
import rep.utils._

import scala.collection.mutable
import scala.concurrent._
import scala.util.Random
import scala.util.control.Breaks.{break, breakable}

/**
 * Created by jiangbuyun on 2018/03/19.
 * 执行预执行actor
 */


object PreloaderForTransaction {
  def props(name: String): Props = Props(classOf[PreloaderForTransaction], name)
}

class PreloaderForTransaction(moduleName: String) extends ModuleBase(moduleName) {
  import scala.collection.breakOut
  import scala.concurrent.duration._

  implicit val timeout = Timeout(TimePolicy.getTimeoutPreload*10.seconds)

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "PreloaderForTransaction Start"))
  }

  /*private def ExecuteTransaction(t: Transaction, db_identifier: String): (Int, DoTransactionResult) = {
    try {
      val future1 = pe.getActorRef(ModuleActorType.ActorType.transactiondispatcher) ? new DoTransaction(t,  db_identifier,TypeOfSender.FromPreloader)
      //val future1 = pe.getActorRef(ActorType.preloadtransrouter) ? new DoTransaction(t,  db_identifier)
      val result = Await.result(future1, timeout.duration).asInstanceOf[DoTransactionResult]
      (0, result)
    } catch {
      case e: AskTimeoutException => (1, null)
      case te:TimeoutException =>
        (1, null)
    }
  }*/

  /*private def outputTransSerialOfBlock(oldBlock:Block,newBlock:Block):String={
    var rstr = "old_txserial="
    oldBlock.transactions.foreach(f=>{
      rstr = rstr + f.id+","
    })
    rstr = rstr + "\r\n"+"new_txserial="
    
    newBlock.transactions.foreach(f=>{
      rstr = rstr + f.id+","
    })
    
    rstr = rstr + "\r\n"+"transresult_txserial="
    newBlock.transactionResults.foreach(f=>{
      rstr = rstr + f.txId+","
    })
    
    rstr
  }*/

  /*private def AssembleTransResult(block:Block,preLoadTrans:mutable.HashMap[String,Transaction],transResult:Seq[TransactionResult], db_indentifier: String):Option[Block]={
    try{
      var newTranList = mutable.Seq.empty[ Transaction ]
      for (tran <- transResult) {
        if (preLoadTrans.getOrElse(tran.txId, None) != None)
          newTranList = newTranList :+ preLoadTrans(tran.txId)
      }
      if(newTranList.size > 0){
        val tmpblk = block.withTransactions(newTranList.toSeq)
        var rblock = tmpblk.withTransactionResults(transResult)
        val statehashstr = Sha256.hashstr(Array.concat(pe.getSystemCurrentChainStatus.currentStateHash.toByteArray() , SerializeUtils.serialise(transResult)))
        rblock = rblock.withStateHash(ByteString.copyFromUtf8(statehashstr))
        //RepLogger.error(RepLogger.Business_Logger, this.getLogMsgPrefix( s" current block height=${block.height},trans create serial: ${outputTransSerialOfBlock(block,rblock)}"))
        Some(rblock)
      }else{
        None
      }
    }catch{
      case e:RuntimeException=>
        RepLogger.error(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s" AssembleTransResult error, error: ${e.getMessage}"))
        None
    }finally{
      ImpDataPreloadMgr.Free(pe.getSysTag,db_indentifier)
    }
  }*/

  /*def Handler(t: Transaction, preLoadTrans: mutable.HashMap[String, Transaction], db_indentifier: String): Option[TransactionResult] = {
    try {
      val result = ExecuteTransaction(t, db_indentifier)
      result._1 match {
        case 0 =>
          //finish
          val r = result._2
          r.err match {
            case None =>
              //success
              var ts = TransactionResult(t.id, r.ol.toSeq, Option(r.r))
              Some(ts)
            case _ =>
              //error
              preLoadTrans.remove(t.id)
              RepLogger.error(RepLogger.Business_Logger, this.getLogMsgPrefix( s"${t.id} preload error, error: ${r.err}"))
              None
          }
        case _ =>
          // timeout failed
          preLoadTrans.remove(t.id)
          RepLogger.error(RepLogger.Business_Logger, this.getLogMsgPrefix( s"${t.id} preload error, error: timeout"))
          None
      }
    } catch {
      case e: RuntimeException =>
        preLoadTrans.remove(t.id)
        RepLogger.error(RepLogger.Business_Logger, this.getLogMsgPrefix( s"${t.id} preload error, error: ${e.getMessage}"))
        None
    }
  }*/

  private def AssembleTransResult(block: Block, transResult: Seq[TransactionResult], db_indentifier: String): Option[Block] = {
    try {
      var rblock = block.withTransactionResults(transResult)
      val statehashstr = Sha256.hashstr(Array.concat(pe.getSystemCurrentChainStatus.currentStateHash.toByteArray(), SerializeUtils.serialise(transResult)))
      rblock = rblock.withStateHash(ByteString.copyFromUtf8(statehashstr))
      //RepLogger.error(RepLogger.Business_Logger, this.getLogMsgPrefix( s" current block height=${block.height},trans create serial: ${outputTransSerialOfBlock(block,rblock)}"))
      if (rblock.hashOfBlock == _root_.com.google.protobuf.ByteString.EMPTY) {
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
      ImpDataPreloadMgr.Free(pe.getSysTag,db_indentifier)
    }
  }


  private def createErrorData(ts: scala.collection.Seq[Transaction], err: Option[akka.actor.Status.Failure]): Array[TransactionResult] = {
    var rs = scala.collection.mutable.ArrayBuffer[TransactionResult]()
    ts.foreach(t => {
      rs += new TransactionResult(t.id, _root_.scala.Seq.empty, Option(ActionResult(103, err.get.cause.getMessage))) //new TransactionResult(t.id, null, null, err)
    })
    rs.toArray
  }

  private def ExecuteTransactions(ts: Seq[Transaction], db_identifier: String): (Int, Seq[TransactionResult]) = {
    try {
      val future1 = pe.getActorRef(ModuleActorType.ActorType.transactiondispatcher) ? new DoTransactions(ts, db_identifier, TypeOfSender.FromPreloader)
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
    var rts = Seq.empty[rep.protos.peer.Transaction]
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
      if ((block.previousBlockHash.toStringUtf8() == pe.getCurrentBlockHash || block.previousBlockHash == ByteString.EMPTY) &&
        block.height == (pe.getCurrentHeight + 1)) {
        var preLoadTrans = mutable.HashMap.empty[String, Transaction]
        //preLoadTrans = block.transactions.map(trans => (trans.id, trans))(breakOut): mutable.HashMap[String, Transaction]
        //preLoadTrans = block.transactions.map(trans => (trans.id, trans)).to(mutable.HashMap)  //.HashMap[String, Transaction]
        //preLoadTrans = block.transactions.map(trans => (trans.id, trans))(breakOut): mutable.HashMap[String, Transaction]
        var transResult = Seq.empty[rep.protos.peer.TransactionResult]
        val dbtag = prefixOfDbTag //prefixOfDbTag+"_"+moduleName+"_"+block.transactions.head.id
        var curBlockHash = "temp_" + Random.nextInt(100)
        if (block.hashOfBlock != _root_.com.google.protobuf.ByteString.EMPTY) {
          curBlockHash = block.hashOfBlock.toStringUtf8
        }
        //setDbInstanceName(block.previousBlockHash.toStringUtf8,curBlockHash)
        //val dbtag = this.DbInstance.tagName
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s" preload db instance name, name: ${dbtag},height:${block.height}"))
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
        /*block.transactions.foreach(t => {
          var ts = Handler(t, preLoadTrans, dbtag)
          if(ts != null){
            //transResult = (transResult :+ ts)
            transResult = (transResult ++ ts)
          }
        })*/
        RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans-exe", System.currentTimeMillis(), block.height, block.transactions.size)
        if (preloadReuslt) {
          RepTimeTracer.setStartTime(pe.getSysTag, "PreloadTrans-assemble", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
          var newblock = AssembleTransResult(block, transResult, dbtag)
          RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans-assemble", System.currentTimeMillis(), block.height, block.transactions.size)
          //全部交易执行完成
          sender ! PreTransBlockResult(newblock.get, true)
        } else {
          RepLogger.error(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s" All Transaction failed, error: ${block.height}"))
          sender ! PreTransBlockResult(null, false)
        }
        RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans-inner", System.currentTimeMillis(), block.height, block.transactions.size)
      }


    /*case PreTransBlock(block,prefixOfDbTag) =>
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(  "entry preload"))
      if ((block.previousBlockHash.toStringUtf8() == pe.getCurrentBlockHash || block.previousBlockHash == ByteString.EMPTY) &&
        block.height == (pe.getCurrentHeight + 1)) {
        var preLoadTrans = mutable.HashMap.empty[String, Transaction]
        //preLoadTrans = block.transactions.map(trans => (trans.id, trans))(breakOut): mutable.HashMap[String, Transaction]
        //preLoadTrans = block.transactions.map(trans => (trans.id, trans)).to(mutable.HashMap)  //.HashMap[String, Transaction]
        preLoadTrans = block.transactions.map(trans => (trans.id, trans))(breakOut): mutable.HashMap[String, Transaction]
        var transResult = Seq.empty[rep.protos.peer.TransactionResult]
        val dbtag = prefixOfDbTag+"_"+moduleName+"_"+block.transactions.head.id
        //确保提交的时候顺序执行
        block.transactions.map(t => {
          var ts = Handler(t, preLoadTrans, dbtag)
          if(ts != None){
            transResult = (transResult :+ ts.get)
          }
        })

        var newblock = AssembleTransResult(block,preLoadTrans,transResult,dbtag)

        if(newblock == None){
          //所有交易执行失败
          RepLogger.error(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s" All Transaction failed, error: ${block.height}"))
          sender ! PreTransBlockResult(null,false)
        }else{
          //全部或者部分交易成功
          sender ! PreTransBlockResult(newblock.get,true)
        }
      }*/
    case _ => //ignore
  }
}