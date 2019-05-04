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

package rep.network.consensus.transaction

import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import akka.pattern.AskTimeoutException
import scala.concurrent._

import akka.actor.{ Actor, ActorRef, Props }
import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import rep.app.conf.{ SystemProfile, TimePolicy }
import rep.network.base.ModuleBase
import rep.network.consensus.block.Blocker.{ PreTransBlock, PreTransBlockResult}
import rep.network.tools.PeerExtension
import rep.network.Topic
import rep.protos.peer._
import rep.sc.SandboxDispatcher.DoTransaction
import rep.sc.Sandbox.DoTransactionResult
import rep.storage.{ ImpDataPreloadMgr }
import rep.utils.GlobalUtils.ActorType
import rep.utils._
import scala.collection.mutable
import akka.pattern.AskTimeoutException
import rep.crypto.Sha256
import rep.log.RepLogger
import rep.sc.TypeOfSender

object PreloaderForTransaction {
  def props(name: String): Props = Props(classOf[PreloaderForTransaction], name)
}

class PreloaderForTransaction(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.collection.breakOut
  import scala.concurrent.duration._

  implicit val timeout = Timeout(TimePolicy.getTimeoutPreload*10 seconds)

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "PreloaderForTransaction Start"))
  }

  private def ExecuteTransaction(t: Transaction, db_identifier: String): (Int, DoTransactionResult) = {
    try {
      val future1 = pe.getActorRef(ActorType.transactiondispatcher) ? new DoTransaction(t,  db_identifier,TypeOfSender.FromPreloader)
      //val future1 = pe.getActorRef(ActorType.preloadtransrouter) ? new DoTransaction(t,  db_identifier)
      val result = Await.result(future1, timeout.duration).asInstanceOf[DoTransactionResult]
      (0, result)
    } catch {
      case e: AskTimeoutException => (1, null)
      case te:TimeoutException =>
        (1, null)
    }
  }

  private def AssembleTransResult(block:Block,preLoadTrans:mutable.HashMap[String,Transaction],transResult:Seq[TransactionResult], db_indentifier: String):Option[Block]={
    try{
      var newTranList = mutable.Seq.empty[ Transaction ]
      for (tran <- transResult) {
        if (preLoadTrans.getOrElse(tran.txId, None) != None)
          newTranList = newTranList :+ preLoadTrans(tran.txId)
      }
      if(newTranList.size > 0){
        val tmpblk = block.withTransactions(newTranList)
        var rblock = tmpblk.withTransactionResults(transResult)
        val statehashstr = Sha256.hashstr(Array.concat(pe.getSystemCurrentChainStatus.currentStateHash.toByteArray() , SerializeUtils.serialise(transResult)))
        rblock = rblock.withStateHash(ByteString.copyFromUtf8(statehashstr))
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
  }

  def Handler(t: Transaction, preLoadTrans: mutable.HashMap[String, Transaction], db_indentifier: String): Option[TransactionResult] = {
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
  }

  override def receive = {
    case PreTransBlock(block,prefixOfDbTag) =>
      
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(  "entry preload"))
      logTime("block preload inner time", System.currentTimeMillis(), true)
      if ((block.previousBlockHash.toStringUtf8().equals(pe.getCurrentBlockHash) || block.previousBlockHash == ByteString.EMPTY) &&
        block.height == (pe.getCurrentHeight + 1)) {
        var preLoadTrans = mutable.HashMap.empty[String, Transaction]
        preLoadTrans = block.transactions.map(trans => (trans.id, trans))(breakOut): mutable.HashMap[String, Transaction]
        var transResult = Seq.empty[rep.protos.peer.TransactionResult]
        val dbtag = prefixOfDbTag+"_"+block.transactions.head.id
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
        logTime("block preload inner time", System.currentTimeMillis(), false)
      }
    case _ => //ignore
  }
}