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
//zhj

package rep.network.consensus.pbft.endorse


import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout
import rep.app.Repchain
import rep.app.conf.{SystemCertList, SystemProfile, TimePolicy}
import rep.log.RepTimeTracer
import rep.network.base.ModuleBase
import rep.network.consensus.common.MsgOfConsensus.{PreTransBlock, PreTransBlockResult}
import rep.network.consensus.pbft.MsgOfPBFT.{MsgPbftCommit, MsgPbftPrePrepare, MsgPbftPrePrepareResend, MsgPbftPrepare, ResultFlagOfEndorse, ResultOfEndorsed}
import rep.network.module.ModuleActorType
import rep.network.module.pbft.PBFTActorType
import rep.network.persistence.IStorager
import rep.network.util
import rep.network.util.NodeHelp
//import rep.network.consensus.vote.Voter.VoteOfBlocker
import rep.log.RepLogger
import rep.network.consensus.util.BlockVerify
import rep.network.sync.SyncMsg.StartSync

import scala.util.control.Breaks._

object Endorser4Future {
  def props(name: String): Props = Props(classOf[Endorser4Future], name)
}

class Endorser4Future(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import rep.proto.rc2._
  import rep.storage.ImpDataAccess

  import scala.concurrent._
  import scala.concurrent.duration._

  implicit val timeout = Timeout(TimePolicy.getTimeoutPreload.seconds)

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("Endorser4Future Start"))
  }
  //preprepare start-------------------------------------------
  private def AskPreloadTransactionOfBlock(block: Block): Future[Boolean] =
    pe.getActorRef(ModuleActorType.ActorType.dispatchofpreload).ask(PreTransBlock(block, "endors"))(timeout).
      mapTo[PreTransBlockResult].flatMap(f => {
      val result = Promise[Boolean]
      var tmpblock = f.blc.withHeader(f.blc.header.get.withHashPresent(block.header.get.hashPresent))
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
        }))
    }
    isRepeat
  }

  private def asyncVerifyTransaction(t: Transaction): Future[Boolean] = Future {
    var result = false

    if (pe.getTransPoolMgr.findTrans(t.id)) {
      result = true
    } else {
      val tmp = BlockVerify.VerifyOneSignOfTrans(t, pe.getSysTag)
      if (tmp._1) {
        result = true
      }
    }
    result
  }

  private def asyncVerifyTransactions(block: Block): Future[Boolean] = Future {
    var result = true
    val listOfFuture: Seq[Future[Boolean]] = block.transactions.map(x => {
      asyncVerifyTransaction(x)
    })

    val futureOfList: Future[List[Boolean]] = Future.sequence(listOfFuture.toList)

    futureOfList.map(x => {
      x.foreach(f => {
        if (f) {
          result = false
        }
      })
    })
    result
  }


  private def checkEndorseSign(block: Block): Future[Boolean] = Future {
    var result = false
    val r = BlockVerify.VerifyAllEndorseOfBlock(block, pe.getSysTag)
    result = r._1
    result
  }

  private def VerifyInfo(info: MsgPbftPrePrepare) = {
      val transSign = asyncVerifyTransactions(info.block)
      val transRepeat = checkRepeatOfTrans(info.block.transactions)
      val endorseSign = checkEndorseSign(info.block)
      val transExe = AskPreloadTransactionOfBlock(info.block)
      val result = for {
        v1 <- transSign
        v2 <- transRepeat
        v3 <- endorseSign
        v4 <- transExe
      } yield (v1 && !v2 && v3 && v4)

      Await.result(result, timeout.duration).asInstanceOf[Boolean]
  }

  private def CheckMessage(block : Block, blocker: String) = {
    var r = ResultFlagOfEndorse.success
    RepLogger.debug(RepLogger.zLogger, "R: " + Repchain.nn(sender) + "->" + Repchain.nn(pe.getSysTag) +
      ", PrePrepare 3a: " + Repchain.nn(blocker) + ", " + Repchain.h4(block.header.get.hashPresent.toStringUtf8))
    if (block.header.get.height > pe.getCurrentHeight + 1) {
      pe.getActorRef(PBFTActorType.ActorType.synchrequester) ! StartSync(false)
      RepLogger.debug(RepLogger.zLogger, Repchain.nn(pe.getSysTag) +
       ", BlockHeightError: " + Repchain.nn(block.header.get.hashPresent.toStringUtf8) + ", " + block.header.get.height + "," +
        Repchain.h4(pe.getCurrentBlockHash) + "," + pe.getCurrentHeight)
      r = ResultFlagOfEndorse.BlockHeightError
    } else {
      if (NodeHelp.isCandidateNow(pe.getSysTag, SystemCertList.getSystemCertList)
        //是候选节点，可以背书
        && (!pe.isSynching)
        && (block.header.get.hashPrevious.toStringUtf8 == pe.getBlocker.voteBlockHash)
        && NodeHelp.isBlocker(blocker, pe.getBlocker.blocker)) {
        r = ResultFlagOfEndorse.success
      }else{
        RepLogger.debug(RepLogger.zLogger, "R: " + Repchain.nn(sender) + "->" + Repchain.nn(pe.getSysTag) +
          ",  MsgPbftPrePrepare 6: " + pe.isSynching + "," +
          Repchain.h4(block.header.get.hashPrevious.toStringUtf8) + "," + Repchain.h4(pe.getBlocker.voteBlockHash) +"," +
          Repchain.nn(blocker) + ", " + Repchain.nn(pe.getBlocker.blocker))
        r = ResultFlagOfEndorse.CandidatorError
      }
    }
    r
  }

  private def CheckMessage2(block : Block, blocker: String):Boolean = {
    val r : Boolean = (block.header.get.hashPrevious.toStringUtf8 == pe.getBlocker.voteBlockHash) &&
            NodeHelp.isBlocker(blocker, pe.getBlocker.blocker)
    r
  }

  private def ProcessMsgPbftPrePrepare(prePrepare: MsgPbftPrePrepare): Int = {
    var r = CheckMessage(prePrepare.block,prePrepare.blocker)
    if (r==ResultFlagOfEndorse.success) {
      RepLogger.debug(RepLogger.zLogger, "R: " + Repchain.nn(sender) + "->" + Repchain.nn(pe.getSysTag) + ", PrePrepare 3: " + Repchain.nn(prePrepare.blocker) + ", " + Repchain.h4(prePrepare.block.header.get.hashPresent.toStringUtf8))
      if (prePrepare.blocker != pe.getSysTag) {
        var b = true;
        if (SystemProfile.getIsVerifyOfEndorsement)
          b = VerifyInfo(prePrepare)
        RepLogger.debug(RepLogger.zLogger, "R: " + Repchain.nn(sender) + "->" + Repchain.nn(pe.getSysTag) + ", PrePrepare 4: " + Repchain.nn(prePrepare.blocker) + ", " + Repchain.h4(prePrepare.block.header.get.hashPresent.toStringUtf8))
        if (b) {
          RepLogger.debug(RepLogger.zLogger, "R: " + Repchain.nn(sender) + "->" + Repchain.nn(pe.getSysTag) + ", PrePrepare 5: " + Repchain.nn(prePrepare.blocker) + ", " + Repchain.h4(prePrepare.block.header.get.hashPresent.toStringUtf8))
          pe.getActorRef(PBFTActorType.ActorType.pbftpreprepare) ! prePrepare

        } else
          r = ResultFlagOfEndorse.VerifyError
      } else {
      }
    }
    r
  }
  //preprepare end-------------------------------------------

  //prepare start-------------------------------------------
  private def VerifyPrepare(block: Block, prepare: MPbftPrepare): Boolean = {
    val bb = block.withHeader(block.header.get.clearEndorsements).toByteArray
    val signature = prepare.signature.get//todo get?
    val ev = BlockVerify.VerifyOneEndorseOfBlock(signature, bb, pe.getSysTag)
    ev._1
  }

  private def ProcessMsgPbftPepare(prepare:MsgPbftPrepare){
    if (CheckMessage2(prepare.block,prepare.blocker))
    if (prepare.result == ResultFlagOfEndorse.success) {
        if (VerifyPrepare(prepare.block, prepare.prepare)) {
          pe.getActorRef(PBFTActorType.ActorType.pbftprepare) ! prepare
        }
    }
  }
  //prepare end-------------------------------------------


  //commit start-------------------------------------------
  private def VerifyCommit(block: Block, commit: MPbftCommit): Boolean = {
    val bb = commit.clearSignature.toByteArray
    val signature = commit.signature.get//todo get?
    val ev = BlockVerify.VerifyOneEndorseOfBlock(signature, bb, pe.getSysTag)
    ev._1
  }

  private def ProcessMsgPbftCommit(commit: MsgPbftCommit){
    if (CheckMessage2(commit.block,commit.blocker))
        if (VerifyCommit(commit.block, commit.commit)) {
          pe.getActorRef(PBFTActorType.ActorType.pbftcommit) ! commit
        }
  }
  //commit end-------------------------------------------

  override def receive = {
    //Endorsement block
    case MsgPbftPrePrepare(senderPath,block, blocker) =>
      RepLogger.debug(RepLogger.zLogger, "R: " + Repchain.nn(sender) + "->" + Repchain.nn(pe.getSysTag) + ", PrePrepare 1: " + Repchain.nn(blocker) + ", " + Repchain.h4(block.header.get.hashPresent.toStringUtf8))
      var r = ResultFlagOfEndorse.success
      if(!pe.isSynching){
        RepTimeTracer.setStartTime(pe.getSysTag, s"recvendorsement-${moduleName}", System.currentTimeMillis(),block.header.get.height,block.transactions.size)
        RepLogger.debug(RepLogger.zLogger, "R: " + Repchain.nn(sender) + "->" + Repchain.nn(pe.getSysTag) + ", PrePrepare 2: " + Repchain.nn(blocker) + ", " + Repchain.h4(block.header.get.hashPresent.toStringUtf8))
        r = ProcessMsgPbftPrePrepare(MsgPbftPrePrepare(sender.path.toString, block, blocker))
        RepTimeTracer.setEndTime(pe.getSysTag, s"recvendorsement-${moduleName}", System.currentTimeMillis(),block.header.get.height,block.transactions.size)
      } else
        r = ResultFlagOfEndorse.EnodrseNodeIsSynching

        if (r != ResultFlagOfEndorse.success) {
          if (block.header.get.height >= pe.getCurrentHeight) {//zhj0623
            sender ! MsgPbftPrePrepareResend(senderPath,block, blocker)
            RepLogger.debug(RepLogger.zLogger, "R: " + Repchain.nn(sender) + "->" + Repchain.nn(pe.getSysTag) + ", PrePrepare resend: " + r + ", " + Repchain.nn(blocker) + ", " + Repchain.h4(block.header.get.hashPresent.toStringUtf8) +","+Repchain.h4(pe.getBlocker.voteBlockHash))
          } //else
            //RepLogger.debug(RepLogger.zLogger, "R: " + Repchain.nn(sender) + "->" + Repchain.nn(pe.getSysTag) + ", PrePrepare not resend: " + r + ", " + Repchain.nn(blocker) + ", " + Repchain.h4(block.hashOfBlock.toStringUtf8) +","+Repchain.h4(pe.getBlocker.voteBlockHash))
        }

    case MsgPbftPrepare(senderPath,result, block, blocker, prepare, chainInfo) =>
      RepLogger.debug(RepLogger.zLogger,"R: " + Repchain.nn(sender) + "->" + Repchain.nn(pe.getSysTag) + ", Prepare: " + Repchain.nn(blocker) + ", " + Repchain.h4(block.header.get.hashPresent.toStringUtf8))
      ProcessMsgPbftPepare(MsgPbftPrepare(senderPath,result, block, blocker, prepare, chainInfo))

    case MsgPbftCommit(senderPath,block,blocker,commit,chainInfo) =>
      RepLogger.debug(RepLogger.zLogger,"R: " + Repchain.nn(sender) + "->" + Repchain.nn(pe.getSysTag) + ", Commit: " + Repchain.nn(blocker) + ", " + Repchain.h4(block.header.get.hashPresent.toStringUtf8))
      ProcessMsgPbftCommit(MsgPbftCommit(senderPath,block,blocker,commit,chainInfo))

    case _ => //ignore
  }

}