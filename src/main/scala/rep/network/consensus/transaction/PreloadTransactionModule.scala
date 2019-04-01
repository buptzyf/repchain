/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
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

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import rep.app.conf.{SystemProfile, TimePolicy}
import rep.network.base.ModuleBase
import rep.network.consensus.block.BlockModule.{PreTransBlock, PreTransBlockResult, PreTransBlockResultGensis}
import rep.network.tools.PeerExtension
import rep.network.consensus.transaction.PreloadTransactionModule.{PreLoadTransTimeout, PreTransFromType}
import rep.network.Topic
import rep.network.consensus.CRFD.CRFD_STEP
import rep.protos.peer.{Block, OperLog, Transaction}
import rep.sc.TransProcessor.DoTransaction
import rep.sc.{Sandbox, TransProcessor}
import rep.storage.{ImpDataAccess, ImpDataPreload, ImpDataPreloadMgr}
import rep.utils.GlobalUtils.ActorType
import rep.utils._
import scala.collection.mutable
import rep.log.trace.LogType

/**
  * Transaction handler
  * Created by kami on 2017/6/6.
  * @update 2018-05 jiangbuyun
  */
object PreloadTransactionModule {
  def props(name: String,transProcessor:ActorRef): Props = Props(classOf[ PreloadTransactionModule ], name,transProcessor)

  case object PreTransFromType {
    val BLOCK_GENSIS = 0
    val BLOCK_CREATOR = 1
    val ENDORSER = 2
  }

  //交易回滚通知
  case object RollBackTrans

  //预执行超时通知
  case object PreLoadTransTimeout

}

class PreloadTransactionModule(moduleName: String, transProcessor:ActorRef) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.collection.breakOut
  import scala.concurrent.duration._

  val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)

  var preLoadTrans = mutable.HashMap.empty[ String, Transaction ]
  var transPreloadTime = mutable.HashMap.empty[ String, Long ]
  var transResult = Seq.empty[ rep.protos.peer.TransactionResult ]
  var errorCount = 0
  var blk = rep.protos.peer.Block()

  var preloadFrom = 0
  var isPreload = false
  
  var blkIdentifier_src = ""

  def clearCache(): Unit = {
    preloadFrom = 0
    errorCount = 0
    preLoadTrans = mutable.HashMap.empty[ String, Transaction ]
    transResult = Seq.empty[ rep.protos.peer.TransactionResult ]
    blk = rep.protos.peer.Block()
  }

  def preLoadFeedBackInfo(resultFlag: Boolean, block: rep.protos.peer.Block, from: Int, merk: String): Unit = {
    logTime("create block preload inner time", System.currentTimeMillis(),false)
    preloadFrom match {
      case PreTransFromType.BLOCK_CREATOR =>
        getActorRef(ActorType.BLOCK_MODULE) ! PreTransBlockResult(block,
          resultFlag, merk, 0, "",blkIdentifier_src)

      case PreTransFromType.ENDORSER =>
        getActorRef(ActorType.ENDORSE_MODULE) ! PreTransBlockResult(block,
          resultFlag, merk, 0, "",blkIdentifier_src)

      case PreTransFromType.BLOCK_GENSIS =>
        getActorRef(ActorType.BLOCK_MODULE) ! PreTransBlockResultGensis(block,
          resultFlag, merk, 0, "")
    }
  }

  def getBlkFromByte(array: Array[Byte]):Block = {
    Block.parseFrom(array)
  }

  override def preStart(): Unit = {
    logMsg(LogType.INFO,"PreloadTransaction Module Start")
  }

  def AssembleTransResult(merkle:Option[String])={
      var newTranList = mutable.Seq.empty[ Transaction ]
      for (tran <- blk.transactions) {
        if (preLoadTrans.getOrElse(tran.id, None) != None)
          newTranList = newTranList :+ preLoadTrans(tran.id)
      }
      if(newTranList.size > 0){
        blk = blk.withTransactions(newTranList)
        val millis = TimeUtils.getCurrentTime()
        
        blk = blk.withTransactionResults(transResult)
        
        //println(s"Merk ${pe.getSysTag} : ~ preload after " + merkle.getOrElse(""))
        preLoadFeedBackInfo(true, blk, preloadFrom, merkle.getOrElse(""))
      }else{
        preLoadFeedBackInfo(false, blk, preloadFrom, pe.getMerk)
      }
      blkIdentifier_src = ""
      //logTime(s"Trans Preload End, Trans size ${transResult.size - errorCount}", CRFD_STEP._6_PRELOAD_END,
      //  getActorRef(ActorType.STATISTIC_COLLECTION))
      isPreload = false
      freeSource
      schedulerLink = clearSched()
  }
  
  def freeSource={
    if(blk != null){
      if(blk.transactions.size > 0){
        ImpDataPreloadMgr.Free(pe.getDBTag,"preload_"+blk.transactions.head.id)
      }
    }
    clearCache() //是否是多余的，确保一定执行了 
  }
  
  override def receive = {

    case PreTransBlock(blc, from,blkIdentifier) =>
      logTime("create block preload inner time", System.currentTimeMillis(),true)
      val preBlk = dataaccess.getBlockByHash(blk.previousBlockHash.toStringUtf8)
      freeSource
      
      if((preBlk!=null && dataaccess.getBlockChainInfo().currentWorldStateHash == getBlkFromByte(preBlk).operHash.toStringUtf8)
      || blk.previousBlockHash == ByteString.EMPTY){
        blkIdentifier_src = blkIdentifier
        //先清空缓存
        clearCache() //是否是多余的，确保一定执行了
        schedulerLink = clearSched()
        //开始预执行
        //logTime("TransPreloadStart",CRFD_STEP._5_PRELOAD_START,getActorRef(ActorType.STATISTIC_COLLECTION))
        //预执行并收集结果
        logMsg(LogType.INFO, s"Get a preload req, form ${sender()}")
        preLoadTrans = blc.transactions.map(trans => (trans.id, trans))(breakOut): mutable.HashMap[ String, Transaction ]
        //modify by jby 2019-01-16 这个实例的获取没有任何意义
        //val preload :ImpDataPreload = ImpDataPreloadMgr.GetImpDataPreload(pe.getDBTag,blc.transactions.head.txid)
        //确保提交的时候顺序执行
        blc.transactions.map(t => {
          transProcessor ! new DoTransaction(t,self,"preload_"+blc.transactions.head.id)
        })
        blk = blc
        preloadFrom = from
        isPreload = true
        //TODO kami 超时在这里，如果超时算是错误类型中的一种
        schedulerLink = scheduler.scheduleOnce(TimePolicy.getTimeoutPreload seconds, self, PreLoadTransTimeout)
      }
      else logMsg(LogType.WARN, "Preload Transcations input consensus failed")

    case Sandbox.DoTransactionResult(txId,from, r, merkle, ol, err) =>
      //是否在当前交易列表中
      preLoadTrans.getOrElse(txId, None) match {
        case None => logMsg(LogType.WARN, s"${txId} does exist in the block this time, size is ${preLoadTrans.size}")
        case _ =>
          err match {
            case None =>
              //c4w  7.31
              var olist = new scala.collection.mutable.ArrayBuffer[ OperLog ]()

              for (l <- ol) {
                val bso = l.oldValue match {
                  case null => _root_.com.google.protobuf.ByteString.EMPTY
                  case _ => ByteString.copyFrom(l.oldValue.get)
                }
                val bsn = l.newValue match {
                  case null => _root_.com.google.protobuf.ByteString.EMPTY
                  case _ => ByteString.copyFrom(l.newValue.get)
                }
                olist += new OperLog(l.key, bso, bsn)
              }

              val result = new rep.protos.peer.TransactionResult(txId,
                olist, None)
                
              
              //val tmpsr = ImpDataPreloadMgr.GetImpDataPreload(pe.getDBTag,"preload_"+blk.transactions.head.id)
              //todo与执行之后需要记录当前交易执行之后的merkle值，目前结构中没有产生
              preLoadTrans(txId) = t//t.withMetadata(ByteString.copyFrom(SerializeUtils.serialise(mb)))


              transResult = (transResult :+ result)
              //          println(s"T:${transResult.size} + $errorCount ? ${blk.transactions.size}")
              //块内所有交易预执行全部成功
              if ((transResult.size + errorCount) == blk.transactions.size) {
                //Preload totally successfully
                AssembleTransResult(merkle)
              }
            case _ =>
              logMsg(LogType.WARN, s"${txId} preload error, error: ${err.get}")
              //TODO kami 删除出错的交易，如果全部出错，则返回false
              preLoadTrans.remove(txId)
              errorCount += 1
              //println("ErrCount:" + errorCount)
              if ((transResult.size + errorCount) == blk.transactions.size) {
                AssembleTransResult(merkle)
              }
          }
      }

    case PreLoadTransTimeout =>
      isPreload match {
        case true =>
          logMsg(LogType.WARN, "Preload trans timeout checked, unfinished")
          preLoadFeedBackInfo(false, blk, preloadFrom, pe.getMerk)
          blkIdentifier_src = ""
          isPreload = false
          freeSource
          schedulerLink = clearSched()
        case false => {
          freeSource
          logMsg(LogType.INFO, "Preload trans timeout checked, successfully")
        }

      }

    case _ => //ignore
  }
}
