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

package rep.network.consensus.cfrd.endorse

import akka.util.Timeout
import akka.pattern.ask
import akka.actor.Props
import com.google.protobuf.ByteString
import rep.network.base.ModuleBase
import rep.network.util.NodeHelp
import rep.app.conf.{SystemCertList, SystemProfile, TimePolicy}
import rep.crypto.Sha256
import rep.utils.GlobalUtils.EventType
import rep.network.autotransaction.Topic
import scala.util.control.Breaks._
import rep.network.module.ModuleActorType
import rep.network.module.cfrd.CFRDActorType
import rep.network.consensus.common.MsgOfConsensus.{PreTransBlock, PreTransBlockResult}
import rep.network.consensus.cfrd.MsgOfCFRD.{/*CreateBlockTPS, */EndorsementInfo, ResultFlagOfEndorse, ResultOfEndorsed, VoteOfForce}
import rep.network.consensus.util.{BlockHelp, BlockVerify}
import rep.network.sync.SyncMsg.StartSync
import rep.log.RepLogger
import rep.log.RepTimeTracer
import rep.network.consensus.common.algorithm.{IAlgorithmOfVote, IRandomAlgorithmOfVote}
import scala.collection.mutable.ArrayBuffer

/**
 * Created by jiangbuyun on 2020/03/19.
 * 背书actor
 */

object Endorser4Future {
  def props(name: String): Props = Props(classOf[Endorser4Future], name)
}

class Endorser4Future(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.concurrent.duration._
  import rep.protos.peer._
  import rep.storage.ImpDataAccess
  import scala.concurrent._

  implicit val timeout = Timeout(TimePolicy.getTimeoutPreload.seconds)

  //zhjtps
  /*protected var algorithmInVoted:IAlgorithmOfVote = new IRandomAlgorithmOfVote*/

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("Endorser4Future Start"))
  }

  //背书块的交易预执行,然后验证block
  //zhjtps
  /*private def AskPreloadTransactionOfBlock(block: Block): Future[Boolean] = {

    var blc2: Block = null
    val isMe = isNextBlocker(block)
    var nextTrans: Seq[Transaction] = null
    if (isMe) {
      nextTrans = CollectedTransOfBlockTPS(0, block.transactions, SystemProfile.getLimitBlockTransNum, SystemProfile.getBlockLength).reverse
      blc2 = block.withTransactions(block.transactions ++ nextTrans.toSeq)
    } else {
      blc2 = block
    }

    pe.getActorRef(ModuleActorType.ActorType.dispatchofpreload).ask(PreTransBlock(blc2, "endors"))(timeout)
      .mapTo[PreTransBlockResult].flatMap(f => {
      val result = Promise[Boolean]

      var tr = ArrayBuffer[TransactionResult]()
      var nextTr = ArrayBuffer[TransactionResult]()
      if (isMe) {
        val txId1 : Seq[String] = block.transactions.map(_.id)
        val txId2 : Seq[String] = nextTrans.map(_.id)
        f.blc.transactionResults.foreach(x=>{
          if (txId1.contains(x.txId))
            tr.append(x)
          if (txId2.contains(x.txId))
            nextTr.append(x)
        })
      }
      var tmpblock : Block = null
      if (isMe) {
        tmpblock = f.blc.withTransactions(block.transactions).withTransactionResults(tr.toSeq)
        //val statehashstr = Sha256.hashstr(Array.concat(pe.getSystemCurrentChainStatus.currentStateHash.toByteArray() ,
        //  SerializeUtils.serialise(tr)))
        tmpblock = tmpblock.withStateHash(ByteString.copyFromUtf8(block.stateHash.toStringUtf8))//zhj todo
        tmpblock = tmpblock.withHashOfBlock(block.hashOfBlock)
        tmpblock = BlockHelp.AddSignToBlock(tmpblock, pe.getSysTag)
      } else
        tmpblock = f.blc.withHashOfBlock(block.hashOfBlock)

      /*val b1 = block.clearEndorsements.withHashOfBlock(ByteString.EMPTY).toByteArray
      val b2 = tmpblock.clearEndorsements.withHashOfBlock(ByteString.EMPTY).toByteArray
      for (i<-0 to b1.size-1){
        if (b1(i)!=b2(i))
          1
      }*/

      if (BlockVerify.VerifyHashOfBlock(tmpblock)) {
        if (isMe) {
          pe.getActorRef(CFRDActorType.ActorType.blocker) ! CreateBlockTPS(nextTrans,nextTr)
        }
        result.success(true)
      } else {
        result.success(false)
      }
      result.future
    }).recover({
      case e: Throwable =>
        RepLogger.error(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry AskPreloadTransactionOfBlock error"))
        false
    })
  }*/
  private def AskPreloadTransactionOfBlock(block: Block): Future[Boolean] =
  //pe.getActorRef(ActorType.preloaderoftransaction).ask(PreTransBlock(block, "endors"))(timeout).mapTo[PreTransBlockResult].flatMap(f => {
    pe.getActorRef(ModuleActorType.ActorType.dispatchofpreload).ask(PreTransBlock(block, "endors"))(timeout).mapTo[PreTransBlockResult].flatMap(f => {
      //println(s"${pe.getSysTag}:entry AskPreloadTransactionOfBlock")
      val result = Promise[Boolean]
      var tmpblock = f.blc.withHashOfBlock(block.hashOfBlock)
      if (BlockVerify.VerifyHashOfBlock(tmpblock)) {
        result.success(true)
      } else {
        result.success(false)
      }
      result.future
    }).recover({
      case e: Throwable =>
        RepLogger.error(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry AskPreloadTransactionOfBlock error"))
        false
    })

  private def checkRepeatOfTrans(trans: Seq[Transaction]): Future[Boolean] = Future {
    //println("entry checkRepeatOfTrans")
    var isRepeat: Boolean = false
    val aliaslist = trans.distinct
    if (aliaslist.size != trans.size) {
      isRepeat = true
    } else {
      val sr: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
      breakable(
        trans.foreach(f => {
          if (sr.isExistTrans4Txid(f.id)) {
            isRepeat = true
            break
          }
          //println(s"${pe.getSysTag}:entry checkRepeatOfTrans loop")
        }))
    }
    //println(s"${pe.getSysTag}:entry checkRepeatOfTrans after,isrepeat=${isRepeat}")
    isRepeat
  }

  private def asyncVerifyTransaction(t: Transaction): Future[Boolean] = Future {
    //println(s"${pe.getSysTag}:entry asyncVerifyTransaction")
    var result = false

    if (pe.getTransPoolMgr.findTrans(t.id)) {
      result = true
    } else {
      val tmp = BlockVerify.VerifyOneSignOfTrans(t, pe.getSysTag)
      if (tmp._1) {
        result = true
      }
      //println(s"${pe.getSysTag}:entry asyncVerifyTransaction loop")
    }
    //println(s"${pe.getSysTag}:entry asyncVerifyTransaction after,asyncVerifyTransaction=${result}")
    result
  }

  private def asyncVerifyTransactions(block: Block): Future[Boolean] = Future {
    //println(s"${pe.getSysTag}:entry asyncVerifyTransactions")
    var result = true
    val listOfFuture: Seq[Future[Boolean]] = block.transactions.map(x => {
      asyncVerifyTransaction(x)
    })

    val futureOfList: Future[List[Boolean]] = Future.sequence(listOfFuture.toList)

    //val result1 = Await.result(futureOfList, timeout4Sign.duration).asInstanceOf[Int]

    futureOfList.map(x => {
      x.foreach(f => {
        if (f) {
          result = false
        }
        //println(s"${pe.getSysTag}:entry asyncVerifyTransactions loop result")
      })
    })
    //println(s"${pe.getSysTag}:entry asyncVerifyTransactions after,asyncVerifyTransactions=${result}")
    result
  }

  private def checkEndorseSign(block: Block): Future[Boolean] = Future {
    //println(s"${pe.getSysTag}:entry checkEndorseSign")
    var result = false
    val r = BlockVerify.VerifyAllEndorseOfBlock(block, pe.getSysTag)
    result = r._1
    //println(s"${pe.getSysTag}:entry checkEndorseSign after,checkEndorseSign=${result}")
    result
  }

  private def checkAllEndorseSign(block: Block):Boolean =  {
    var result = false
    val r = BlockVerify.VerifyAllEndorseOfBlock(block, pe.getSysTag)
    result = r._1
    result
  }

  private def isAllowEndorse(info: EndorsementInfo): Int = {
    if (info.blocker == pe.getSysTag) {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"endorser is itself,do not endorse,recv endorse request,endorse height=${info.blc.height},local height=${pe.getCurrentHeight}"))
      1
    } else {
      if (NodeHelp.isCandidateNow(pe.getSysTag, SystemCertList.getSystemCertList)) {
        //是候选节点，可以背书
        //if (info.blc.previousBlockHash.toStringUtf8 == pe.getCurrentBlockHash && NodeHelp.isBlocker(info.blocker, pe.getBlocker.blocker)) {
        if (info.blc.previousBlockHash.toStringUtf8 == pe.getBlocker.voteBlockHash && NodeHelp.isBlocker(info.blocker, pe.getBlocker.blocker)) {
          //可以进入背书
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"vote result equal，allow entry endorse,recv endorse request,endorse height=${info.blc.height},local height=${pe.getCurrentHeight}"))
          0
        } else  {
          //todo 需要判断区块缓存，再决定是否需要启动同步,并且当前没有同步才启动同步，如果已经同步，则不需要发送消息。
          if(info.blc.height > pe.getCurrentHeight+1 && !pe.isSynching){
            pe.getActorRef(CFRDActorType.ActorType.synchrequester) ! StartSync(false)
          }
          /*else if(info.blc.height > pe.getCurrentHeight+1){
            pe.getActorRef(CFRDActorType.ActorType.voter) ! VoteOfForce
          }*/
          //当前块hash和抽签的出块人都不一致，暂时不能够进行背书，可以进行缓存
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"block hash is not equal or blocker is not equal,recv endorse request,endorse height=${info.blc.height},local height=${pe.getCurrentHeight}"))
          2
        }
      } else {
        //不是候选节点，不能够参与背书
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "it is not candidator node,recv endorse request,endorse height=${info.blc.height},local height=${pe.getCurrentHeight}"))
        3
      }
    }
  }

  private def VerifyInfo(blc: Block) = {
    val transSign = asyncVerifyTransactions(blc)
    val transRepeat = checkRepeatOfTrans(blc.transactions)
    val endorseSign = checkEndorseSign(blc)
    val transExe = AskPreloadTransactionOfBlock(blc)
    val result = for {
      v1 <- transSign
      v2 <- transRepeat
      v3 <- endorseSign
      v4 <- transExe
    } yield (v1 && !v2 && v3 && v4)

    val result1 = Await.result(result, timeout.duration*2).asInstanceOf[Boolean]
    //SendVerifyEndorsementInfo(blc,result1)
    result1
  }

  private def SendVerifyEndorsementInfo(blc: Block,result1:Boolean) = {
    if (result1) {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry 7"))
      sendEvent(EventType.RECEIVE_INFO, mediator, pe.getSysTag, Topic.Endorsement,Event.Action.ENDORSEMENT)
      sender ! ResultOfEndorsed(ResultFlagOfEndorse.success, BlockHelp.SignBlock(blc, pe.getSysTag),
        blc.hashOfBlock.toStringUtf8(),pe.getSystemCurrentChainStatus,pe.getBlocker)

    } else {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"${pe.getSysTag}:entry 8"))
      sender ! ResultOfEndorsed(ResultFlagOfEndorse.VerifyError, null, blc.hashOfBlock.toStringUtf8(),
        pe.getSystemCurrentChainStatus,pe.getBlocker)
    }
  }


  //zhjtps
  /*protected def isNextBlocker(blc:Block) : Boolean =
  {
    var candidator: Array[String] = Array.empty[String]
    candidator = algorithmInVoted.candidators(pe.getSysTag, blc.hashOfBlock.toStringUtf8,
      SystemCertList.getSystemCertList, Sha256.hash(blc.hashOfBlock.toStringUtf8))
    val nextBlocker = algorithmInVoted.blocker(candidator.toArray[String], 0)
    nextBlocker.equals(pe.getSysTag)

  }*/

  //zhjtps
  /*protected def CollectedTransOfBlockTPS(start: Int, bypassTrans : Seq[Transaction], num: Int, limitsize: Int): ArrayBuffer[Transaction] = {
    var result = ArrayBuffer.empty[Transaction]
    try {
      val tmplist = pe.getTransPoolMgr.getTransListClone(start, num + bypassTrans.size, pe.getSysTag)
      if (tmplist.size > 0) {
        var transsize = 0
        breakable(
          tmplist.foreach(f => {
            transsize += f.toByteArray.size
            if (transsize * 3 > limitsize) {
              break
            } else {
              if (!bypassTrans.contains(f))
                f +=: result
              if (result.size >= num)
                break
            }
          }))
      }
    } finally {
    }
    result
  }*/

  private def EndorseHandler(info: EndorsementInfo) = {
    val r = isAllowEndorse(info)
    r match {
      case 0 =>

        var result1 = true
        if (SystemProfile.getIsVerifyOfEndorsement) {
          result1 = VerifyInfo(info.blc)
        }

        SendVerifyEndorsementInfo(info.blc, true)

      case 2 =>
        //cache endorse,waiting revote
        sender ! ResultOfEndorsed(ResultFlagOfEndorse.BlockHeightError, null, info.blc.hashOfBlock.toStringUtf8(), pe.getSystemCurrentChainStatus, pe.getBlocker)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"endorsement entry cache,self height=${pe.getCurrentHeight},block height=${info.blc.height}"))
      case 1 =>
        //do not endorse
        sender ! ResultOfEndorsed(ResultFlagOfEndorse.BlockerSelfError, null, info.blc.hashOfBlock.toStringUtf8(), pe.getSystemCurrentChainStatus, pe.getBlocker)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"itself,recv endorse request,endorse height=${info.blc.height},local height=${pe.getCurrentHeight}"))
      case 3 =>
        //do not endorse
        sender ! ResultOfEndorsed(ResultFlagOfEndorse.CandidatorError, null, info.blc.hashOfBlock.toStringUtf8(), pe.getSystemCurrentChainStatus, pe.getBlocker)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"it is not candator,do not endorse,recv endorse request,endorse height=${info.blc.height},local height=${pe.getCurrentHeight}"))
    }
  }

  override def receive = {
    //Endorsement block
    case EndorsementInfo(block, blocker) =>
      if(!pe.isSynching){
        RepTimeTracer.setStartTime(pe.getSysTag, s"recvendorsement-${moduleName}", System.currentTimeMillis(),block.height,block.transactions.size)
        EndorseHandler(EndorsementInfo(block, blocker))
        RepTimeTracer.setEndTime(pe.getSysTag, s"recvendorsement-${moduleName}", System.currentTimeMillis(),block.height,block.transactions.size)
      }else{
        sender ! ResultOfEndorsed(ResultFlagOfEndorse.EnodrseNodeIsSynching, null, block.hashOfBlock.toStringUtf8(),pe.getSystemCurrentChainStatus,pe.getBlocker)
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"do not endorse,it is synching,recv endorse request,endorse height=${block.height},local height=${pe.getCurrentHeight}"))
      }

    case _ => //ignore
  }

}