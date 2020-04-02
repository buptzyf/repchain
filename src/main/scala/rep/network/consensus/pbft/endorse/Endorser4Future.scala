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
import rep.app.conf.{SystemCertList, SystemProfile, TimePolicy}
import rep.network.base.ModuleBase
import rep.network.consensus.common.MsgOfConsensus.{PreTransBlock, PreTransBlockResult}
import rep.network.consensus.pbft.MsgOfPBFT.{MsgPbftCommit, MsgPbftPrePrepare, MsgPbftPrepare, ResultFlagOfEndorse}
import rep.network.module.ModuleActorType
import rep.network.module.pbft.PBFTActorType
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
  import rep.protos.peer._
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

  private def CheckMessage(block : Block, blocker: String):Boolean = {
    var r = false
    if (block.height > pe.getCurrentHeight + 1) {
      pe.getActorRef(PBFTActorType.ActorType.synchrequester) ! StartSync(false)
    } else {
      if (NodeHelp.isCandidateNow(pe.getSysTag, SystemCertList.getSystemCertList)
        //是候选节点，可以背书
        && (!pe.isSynching)
        && (block.previousBlockHash.toStringUtf8 == pe.getBlocker.voteBlockHash)
        && NodeHelp.isBlocker(blocker, pe.getBlocker.blocker)) {
        r = true
      }
    }
    r
  }

  private def ProcessMsgPbftPrePrepare(prePrepare: MsgPbftPrePrepare): Unit = {
    if (CheckMessage(prePrepare.block,prePrepare.blocker))
      if (prePrepare.blocker != pe.getSysTag) {
        var b = true;
        if (SystemProfile.getIsVerifyOfEndorsement)
          b = VerifyInfo(prePrepare)
        if (b)
          pe.getActorRef(PBFTActorType.ActorType.pbftpreprepare) ! prePrepare
        else
          sender ! MsgPbftPrepare("",ResultFlagOfEndorse.VerifyError, null, null,null, pe.getSystemCurrentChainStatus)
      }
  }
  //preprepare end-------------------------------------------

  //prepare start-------------------------------------------
  private def VerifyPrepare(block: Block, prepare: MPbftPrepare): Boolean = {
    val bb = block.clearEndorsements.toByteArray
    val signature = prepare.signature.get//todo get?
    val ev = BlockVerify.VerifyOneEndorseOfBlock(signature, bb, pe.getSysTag)
    ev._1
  }

  private def ProcessMsgPbftPepare(prepare:MsgPbftPrepare){
    if (CheckMessage(prepare.block,prepare.blocker))
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
    if (CheckMessage(commit.block,commit.blocker))
        if (VerifyCommit(commit.block, commit.commit)) {
          pe.getActorRef(PBFTActorType.ActorType.pbftcommit) ! commit
        }
  }
  //commit end-------------------------------------------

  override def receive = {
    //Endorsement block
    case MsgPbftPrePrepare(senderPath,block, blocker) =>
      //RepLogger.print(RepLogger.zLogger,pe.getSysTag + ", Endorser4Future recv preprepare: " + blocker + ", " + block.hashOfBlock)
      ProcessMsgPbftPrePrepare(MsgPbftPrePrepare(sender.path.toString, block, blocker))


    case MsgPbftPrepare(senderPath,result, block, blocker, prepare, chainInfo) =>
      //RepLogger.print(RepLogger.zLogger,pe.getSysTag + ", Endorser4Future recv prepare: " + blocker + ", " + block.hashOfBlock)
      ProcessMsgPbftPepare(MsgPbftPrepare(senderPath,result, block, blocker, prepare, chainInfo))


    case MsgPbftCommit(senderPath,block,blocker,commit,chainInfo) =>
      //RepLogger.print(RepLogger.zLogger,pe.getSysTag + ", Endorser4Future recv commit: " + blocker + ", " + block.hashOfBlock)
      ProcessMsgPbftCommit(MsgPbftCommit(senderPath,block,blocker,commit,chainInfo))

    case _ => //ignore
  }

}