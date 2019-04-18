package rep.network.consensus.endorse

import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import akka.pattern.AskTimeoutException
import scala.concurrent._

import akka.actor.{ ActorRef, Props, Address, ActorSystemImpl }
import rep.crypto.Sha256
import rep.network.base.ModuleBase
import rep.network.Topic
import rep.network.util.NodeHelp
import rep.protos.peer.{ Event, Transaction }
import rep.app.conf.{ SystemProfile, TimePolicy, SystemCertList }
import rep.storage.{ ImpDataPreload, ImpDataPreloadMgr }
import rep.utils.GlobalUtils.{ ActorType, BlockEvent, EventType, NodeStatus }
import com.sun.beans.decoder.FalseElementHandler
import rep.network.consensus.vote.Voter.VoteOfBlocker
import sun.font.TrueTypeFont
import scala.util.control.Breaks._
import scala.util.control.Exception.Finally
import java.util.concurrent.ConcurrentHashMap
import rep.log.trace.LogType
import rep.network.consensus.endorse.EndorseMsg.{ EndorsementInfo, ResultOfEndorsed, verifyTransSignOfEndorsement, verifyTransExeOfEndorsement, VerfiyBlockEndorseOfEndorsement, VerifyResultOfEndorsement, VerifyTypeOfEndorsement, ConsensusOfVote }
import rep.network.consensus.block.Blocker.{ PreTransBlock, PreTransBlockResult }
import rep.network.consensus.util.{ BlockVerify, BlockHelp }

object Endorser4Future {
  def props(name: String): Props = Props(classOf[Endorser4Future], name)
  case class CacheOfEndorsement(endorseinfo: EndorsementInfo, requester: ActorRef,
                                repeatOfChecked: Int, transSignOfChecked: Int, EndorseSignOfChecked: Int,
                                transExeOfChecked: Int, result: ResultOfEndorsed)
}

class Endorser4Future(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.concurrent.duration._
  import rep.protos.peer._
  import rep.storage.ImpDataAccess
  import rep.network.consensus.endorse.Endorser.CacheOfEndorsement

  implicit val timeout = Timeout(TimePolicy.getTimeoutPreload seconds)
  
  override def preStart(): Unit = {
    logMsg(LogType.INFO, "Endorser4Future Start")
  }

  //背书块的交易预执行,然后验证block
  private def AskPreloadTransactionOfBlock(block: Block): Future[Boolean] =
    pe.getActorRef(ActorType.preloaderoftransaction).ask(PreTransBlock(block, "endors"))(timeout).mapTo[PreTransBlockResult].flatMap(f => {
      println("entry AskPreloadTransactionOfBlock")
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
        println("entry AskPreloadTransactionOfBlock error")
        false
    })

  private def checkRepeatOfTrans(trans: Seq[Transaction]): Future[Boolean] = Future {
    println("entry checkRepeatOfTrans")
    var isRepeat: Boolean = false
    val aliaslist = trans.distinct
    if (aliaslist.size != trans.size) {
      isRepeat = true
    } else {
      val sr: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
      breakable(
        trans.foreach(f => {
          if (sr.getBlockByTxId(f.id) != null) {
            isRepeat = true
            break
          }
          println("entry checkRepeatOfTrans loop")
        }))
    }
    println(s"entry checkRepeatOfTrans after,isrepeat=${isRepeat}")
    isRepeat
  }

  private def asyncVerifyTransaction(t: Transaction): Future[Boolean] = Future {
    println("entry asyncVerifyTransaction")
    var result = false

    if (pe.getTransPoolMgr.findTrans(t.id)) {
      result = true
    } else {
      val tmp = BlockVerify.VerifyOneSignOfTrans(t, pe.getSysTag)
      if (tmp._1) {
        result = true
      }
      println("entry asyncVerifyTransaction loop")
    }
    println(s"entry asyncVerifyTransaction after,asyncVerifyTransaction=${result}")
    result
  }

  private def asyncVerifyTransactions(block: Block): Future[Boolean] = Future {
    println("entry asyncVerifyTransactions")
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
        println("entry asyncVerifyTransactions loop result")
      })
    })
    println(s"entry asyncVerifyTransactions after,asyncVerifyTransactions=${result}")
    result
  }

  private def checkEndorseSign(block: Block): Future[Boolean] = Future {
    println("entry checkEndorseSign")
    var result = false
    val r = BlockVerify.VerifyAllEndorseOfBlock(block, pe.getSysTag)
    result = r._1
    println(s"entry checkEndorseSign after,checkEndorseSign=${result}")
    result
  }

  private def isAllowEndorse(info: EndorsementInfo): Int = {
    if (info.blocker == pe.getSysTag) {
      logMsg(LogType.INFO, "endorser is itself,do not endorse")
      1
    } else {
      if (NodeHelp.isCandidateNow(pe.getSysTag, SystemCertList.getSystemCertList)) {
        //是候选节点，可以背书
        if (info.blc.previousBlockHash.toStringUtf8 == pe.getCurrentBlockHash && NodeHelp.isBlocker(info.blocker, pe.getBlocker.blocker)) {
          //可以进入背书
          logMsg(LogType.INFO, "vote result equal，allow entry endorse")
          0
        } else {
          //当前块hash和抽签的出块人都不一致，暂时不能够进行背书，可以进行缓存
          logMsg(LogType.INFO, "block hash is not equal or blocker is not equal")
          2
        }
      } else {
        //不是候选节点，不能够参与背书
        logMsg(LogType.INFO, "it is not candidator node")
        3
      }
    }
  }

  private def VerifyInfo(info: EndorsementInfo) = {
    println("entry 0")
    val transSign = asyncVerifyTransactions(info.blc)
    println("entry 1")
    val transRepeat = checkRepeatOfTrans(info.blc.transactions)
    println("entry 2")
    val endorseSign = checkEndorseSign(info.blc)
    println("entry 3")
    val transExe = AskPreloadTransactionOfBlock(info.blc)
    println("entry 4")
    val result = for {
      v1 <- transSign
      v2 <- transRepeat
      v3 <- endorseSign
      v4 <- transExe
    } yield (v1 && !v2 && v3 && v4)

    println(s"entry 5 ")
    val result1 = Await.result(result, timeout.duration).asInstanceOf[Boolean]
    println("entry 6")
    if (result1) {
      println("entry 7")
      //if(AskPreloadTransactionOfBlock(info.blc)){
      //println("entry 9")
      sendEvent(EventType.PUBLISH_INFO, mediator, selfAddr, Topic.Block, Event.Action.ENDORSEMENT)
      sender ! ResultOfEndorsed(true, BlockHelp.SignBlock(info.blc, pe.getSysTag), info.blc.hashOfBlock.toStringUtf8())
      //}else{
      //  println("entry 10")
      //  sender ! ResultOfEndorsed(false, null, info.blc.hashOfBlock.toStringUtf8())
      //}
    } else {
      println("entry 8")
      sender ! ResultOfEndorsed(false, null, info.blc.hashOfBlock.toStringUtf8())
    }
  }

  private def EndorseHandler(info: EndorsementInfo) = {
    val r = isAllowEndorse(info)
    r match {
      case 0 =>
        //entry endorse
        VerifyInfo(info: EndorsementInfo)
      case 2 =>
        //cache endorse,waiting revote
        logMsg(LogType.INFO, s"endorsement entry cache,self height=${pe.getCurrentHeight},block height=${info.blc.height}")
      case 1 =>
        //do not endorse
        logMsg(LogType.INFO, "it is blocker")
      case 3 =>
        //do not endorse
        logMsg(LogType.INFO, "it is not candator,do not endorse")
    }
  }

  override def receive = {
    //Endorsement block
    case EndorsementInfo(block, blocker) =>
      EndorseHandler(EndorsementInfo(block, blocker))
    //CheckEndorseInfo(EndorsementInfo(block, blocker))
    //case VerifyResultOfEndorsement(blockhash: String, blocker: String, verifyType: Int, result: Boolean) =>
    //  VerifyOfHandler(VerifyResultOfEndorsement(blockhash, blocker, verifyType, result))
    //case ConsensusOfVote =>

    case _ => //ignore
  }

}