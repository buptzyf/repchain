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
import rep.app.conf.{ TimePolicy}
import rep.log.RepTimeTracer
import rep.network.base.ModuleBase
import rep.network.consensus.common.MsgOfConsensus.{PreTransBlock, PreTransBlockResult}
import rep.network.consensus.pbft.MsgOfPBFT.{MPbftCommit, MPbftPrepare, MsgPbftCommit, MsgPbftPrePrepare, MsgPbftPrePrepareResend, MsgPbftPrepare, ResultFlagOfEndorse, ResultOfEndorsed}
import rep.network.module.ModuleActorType
import rep.network.module.pbft.PBFTActorType
import rep.network.util.NodeHelp
import rep.utils.SerializeUtils
import rep.log.RepLogger
import rep.network.confirmblock.pbft.ConfirmOfBlockOfPBFT
import rep.network.consensus.util.BlockVerify
import rep.network.sync.SyncMsg.StartSync
import rep.proto.rc2.{Block, Transaction}
import rep.storage.chain.block.BlockSearcher

import scala.util.control.Breaks._

object Endorser4Future {
  def props(name: String): Props = Props(classOf[Endorser4Future], name)
}

class Endorser4Future(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
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
      var tmpblock = f.blc.withHeader(f.blc.getHeader.withHashPresent(block.getHeader.hashPresent) )
      if (BlockVerify.VerifyHashOfBlock(tmpblock,pe.getRepChainContext.getHashTool)) {
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
      val sr = pe.getRepChainContext.getBlockSearch
      breakable(
        trans.foreach(f => {
          if (sr.isExistTransactionByTxId(f.id)) {
            isRepeat = true
            break
          }
        }))
    }
    isRepeat
  }

  private def asyncVerifyTransaction(t: Transaction): Future[Boolean] = Future {
    var result = false

    if (pe.getRepChainContext.getTransactionPool.isExist(t.id)) {
      result = true
    } else {
      val tmp = BlockVerify.VerifyOneSignOfTrans(t, pe.getRepChainContext.getSignTool)
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
    val r = BlockVerify.VerifyAllEndorseOfBlock(block, pe.getRepChainContext.getSignTool)
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
    RepLogger.debug(RepLogger.zLogger, "R: " + ConfirmOfBlockOfPBFT.nn(sender) + "->" + ConfirmOfBlockOfPBFT.nn(pe.getSysTag) +
      ", PrePrepare 3a: " + ConfirmOfBlockOfPBFT.nn(blocker) + ", " + ConfirmOfBlockOfPBFT.h4(block.getHeader.hashPresent.toStringUtf8))
    if (block.getHeader.height > pe.getCurrentHeight + 1) {
      pe.getActorRef(PBFTActorType.ActorType.synchrequester) ! StartSync(false)
      RepLogger.debug(RepLogger.zLogger, ConfirmOfBlockOfPBFT.nn(pe.getSysTag) +
       ", BlockHeightError: " + ConfirmOfBlockOfPBFT.nn(block.getHeader.hashPresent.toStringUtf8) + ", " + block.getHeader.height + "," +
        ConfirmOfBlockOfPBFT.h4(pe.getCurrentBlockHash) + "," + pe.getCurrentHeight)
      r = ResultFlagOfEndorse.BlockHeightError
    } else {
      if (NodeHelp.isCandidateNow(pe.getSysTag,  pe.getRepChainContext.getSystemCertList.getSystemCertList)
        //是候选节点，可以背书
        && (!pe.isSynching)
        && (block.getHeader.hashPrevious.toStringUtf8 == pe.getBlocker.voteBlockHash)
        && NodeHelp.isBlocker(blocker, pe.getBlocker.blocker)) {
        r = ResultFlagOfEndorse.success
      }else{
        RepLogger.debug(RepLogger.zLogger, "R: " + ConfirmOfBlockOfPBFT.nn(sender) + "->" + ConfirmOfBlockOfPBFT.nn(pe.getSysTag) +
          ",  MsgPbftPrePrepare 6: " + pe.isSynching + "," +
          ConfirmOfBlockOfPBFT.h4(block.getHeader.hashPrevious.toStringUtf8) + "," + ConfirmOfBlockOfPBFT.h4(pe.getBlocker.voteBlockHash) +"," +
          ConfirmOfBlockOfPBFT.nn(blocker) + ", " + ConfirmOfBlockOfPBFT.nn(pe.getBlocker.blocker))
        r = ResultFlagOfEndorse.CandidatorError
      }
    }
    r
  }

  private def CheckMessage2(block : Block, blocker: String):Boolean = {
    val r : Boolean = (block.getHeader.hashPrevious.toStringUtf8 == pe.getBlocker.voteBlockHash) &&
            NodeHelp.isBlocker(blocker, pe.getBlocker.blocker)
    r
  }

  private def ProcessMsgPbftPrePrepare(prePrepare: MsgPbftPrePrepare): Int = {
    var r = CheckMessage(prePrepare.block,prePrepare.blocker)
    if (r==ResultFlagOfEndorse.success) {
      RepLogger.debug(RepLogger.zLogger, "R: " + ConfirmOfBlockOfPBFT.nn(sender) + "->" + ConfirmOfBlockOfPBFT.nn(pe.getSysTag) + ", PrePrepare 3: " + ConfirmOfBlockOfPBFT.nn(prePrepare.blocker) + ", " + ConfirmOfBlockOfPBFT.h4(prePrepare.block.getHeader.hashPresent.toStringUtf8))
      if (prePrepare.blocker != pe.getSysTag) {
        var b = true;
        if (pe.getRepChainContext.getConfig.isVerifyOfEndorsement)
          b = VerifyInfo(prePrepare)
        RepLogger.debug(RepLogger.zLogger, "R: " + ConfirmOfBlockOfPBFT.nn(sender) + "->" + ConfirmOfBlockOfPBFT.nn(pe.getSysTag) + ", PrePrepare 4: " + ConfirmOfBlockOfPBFT.nn(prePrepare.blocker) + ", " + ConfirmOfBlockOfPBFT.h4(prePrepare.block.getHeader.hashPresent.toStringUtf8))
        if (b) {
          RepLogger.debug(RepLogger.zLogger, "R: " + ConfirmOfBlockOfPBFT.nn(sender) + "->" + ConfirmOfBlockOfPBFT.nn(pe.getSysTag) + ", PrePrepare 5: " + ConfirmOfBlockOfPBFT.nn(prePrepare.blocker) + ", " + ConfirmOfBlockOfPBFT.h4(prePrepare.block.getHeader.hashPresent.toStringUtf8))
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
    val bb = block.getHeader.clearEndorsements.toByteArray
    val signature = prepare.signature.get//todo get?
    val ev = BlockVerify.VerifyOneEndorseOfBlock(signature, bb, pe.getRepChainContext.getSignTool)
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
    val bb = SerializeUtils.serialise(MPbftCommit(commit.prepares,None))
    val signature = commit.signature.get//todo get?
    val ev = BlockVerify.VerifyOneEndorseOfBlock(signature, bb, pe.getRepChainContext.getSignTool)
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
      RepLogger.debug(RepLogger.zLogger, "R: " + ConfirmOfBlockOfPBFT.nn(sender) + "->" + ConfirmOfBlockOfPBFT.nn(pe.getSysTag) + ", PrePrepare 1: " + ConfirmOfBlockOfPBFT.nn(blocker) + ", " + ConfirmOfBlockOfPBFT.h4(block.getHeader.hashPresent.toStringUtf8))
      var r = ResultFlagOfEndorse.success
      if(!pe.isSynching){
        RepTimeTracer.setStartTime(pe.getSysTag, s"recvendorsement-${moduleName}", System.currentTimeMillis(),block.getHeader.height,block.transactions.size)
        RepLogger.debug(RepLogger.zLogger, "R: " + ConfirmOfBlockOfPBFT.nn(sender) + "->" + ConfirmOfBlockOfPBFT.nn(pe.getSysTag) + ", PrePrepare 2: " + ConfirmOfBlockOfPBFT.nn(blocker) + ", " + ConfirmOfBlockOfPBFT.h4(block.getHeader.hashPresent.toStringUtf8))
        r = ProcessMsgPbftPrePrepare(MsgPbftPrePrepare(sender.path.toString, block, blocker))
        RepTimeTracer.setEndTime(pe.getSysTag, s"recvendorsement-${moduleName}", System.currentTimeMillis(),block.getHeader.height,block.transactions.size)
      } else
        r = ResultFlagOfEndorse.EnodrseNodeIsSynching

        if (r != ResultFlagOfEndorse.success) {
          if (block.getHeader.height >= pe.getCurrentHeight) {//zhj0623
            sender ! MsgPbftPrePrepareResend(senderPath,block, blocker)
            RepLogger.debug(RepLogger.zLogger, "R: " + ConfirmOfBlockOfPBFT.nn(sender) + "->" + ConfirmOfBlockOfPBFT.nn(pe.getSysTag) + ", PrePrepare resend: " + r + ", " + ConfirmOfBlockOfPBFT.nn(blocker) + ", " + ConfirmOfBlockOfPBFT.h4(block.getHeader.hashPresent.toStringUtf8) +","+ConfirmOfBlockOfPBFT.h4(pe.getBlocker.voteBlockHash))
          } //else
            //RepLogger.debug(RepLogger.zLogger, "R: " + Repchain.nn(sender) + "->" + Repchain.nn(pe.getSysTag) + ", PrePrepare not resend: " + r + ", " + Repchain.nn(blocker) + ", " + Repchain.h4(block.hashOfBlock.toStringUtf8) +","+Repchain.h4(pe.getBlocker.voteBlockHash))
        }

    case MsgPbftPrepare(senderPath,result, block, blocker, prepare, chainInfo) =>
      RepLogger.debug(RepLogger.zLogger,"R: " + ConfirmOfBlockOfPBFT.nn(sender) + "->" + ConfirmOfBlockOfPBFT.nn(pe.getSysTag) + ", Prepare: " + ConfirmOfBlockOfPBFT.nn(blocker) + ", " + ConfirmOfBlockOfPBFT.h4(block.getHeader.hashPresent.toStringUtf8))
      ProcessMsgPbftPepare(MsgPbftPrepare(senderPath,result, block, blocker, prepare, chainInfo))

    case MsgPbftCommit(senderPath,block,blocker,commit,chainInfo) =>
      RepLogger.debug(RepLogger.zLogger,"R: " + ConfirmOfBlockOfPBFT.nn(sender) + "->" + ConfirmOfBlockOfPBFT.nn(pe.getSysTag) + ", Commit: " + ConfirmOfBlockOfPBFT.nn(blocker) + ", " + ConfirmOfBlockOfPBFT.h4(block.getHeader.hashPresent.toStringUtf8))
      ProcessMsgPbftCommit(MsgPbftCommit(senderPath,block,blocker,commit,chainInfo))

    case _ => //ignore
  }

}