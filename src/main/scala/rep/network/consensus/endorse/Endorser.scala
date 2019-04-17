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

object Endorser {
  def props(name: String): Props = Props(classOf[Endorser], name)
  case class CacheOfEndorsement(endorseinfo: EndorsementInfo, requester: ActorRef,
                                repeatOfChecked: Int, transSignOfChecked: Int, EndorseSignOfChecked: Int,
                                transExeOfChecked: Int, result: ResultOfEndorsed)
}

class Endorser(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.concurrent.duration._
  import rep.protos.peer._
  import rep.storage.ImpDataAccess
  import rep.network.consensus.endorse.Endorser.CacheOfEndorsement

  implicit val timeout = Timeout(TimePolicy.getTimeoutPreload seconds)

  //缓存当前的背书请求
  var cache: CacheOfEndorsement = null

  
   private def hasRepeatOfTrans(trans: Seq[Transaction]): Boolean = {
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
        }))
    }
    isRepeat
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

  private def EndorseHandler = {
    val r = isAllowEndorse(this.cache.endorseinfo)
    r match {
      case 0 =>
        //entry endorse
        if (this.cache.result == null) {
          if (this.cache.transSignOfChecked == 0) pe.getActorRef(ActorType.verifytransofsigner) ! verifyTransSignOfEndorsement(this.cache.endorseinfo.blc, this.cache.endorseinfo.blocker)
          if (this.cache.transExeOfChecked == 0) pe.getActorRef(ActorType.verifytransofexecutor) ! verifyTransExeOfEndorsement(this.cache.endorseinfo.blc, this.cache.endorseinfo.blocker)
          if (this.cache.EndorseSignOfChecked == 0) pe.getActorRef(ActorType.verifyblockendorser) ! VerfiyBlockEndorseOfEndorsement(this.cache.endorseinfo.blc, this.cache.endorseinfo.blocker)
          if (this.cache.repeatOfChecked == 0) {
            if (!this.hasRepeatOfTrans(this.cache.endorseinfo.blc.transactions)) {
              this.cache = CacheOfEndorsement(this.cache.endorseinfo, this.cache.requester, 1, this.cache.transSignOfChecked, this.cache.EndorseSignOfChecked, this.cache.transExeOfChecked, null)
            } else {
              this.cache = CacheOfEndorsement(this.cache.endorseinfo, this.cache.requester, 2, this.cache.transSignOfChecked, this.cache.EndorseSignOfChecked, this.cache.transExeOfChecked, null)
            }
          }
        } else {
          //send result to endorse requester
          if (this.cache.result.result) {
            logMsg(LogType.INFO, "endorser is success")
            sendEvent(EventType.PUBLISH_INFO, mediator, selfAddr, Topic.Block, Event.Action.ENDORSEMENT)
          } else {
            logMsg(LogType.INFO, s"endorser is failed,cause={isrepeat=${this.cache.repeatOfChecked},endorsesign=${this.cache.EndorseSignOfChecked},transsign=${this.cache.transSignOfChecked},transexe=${this.cache.transExeOfChecked}}")
          }
          this.cache.requester ! this.cache.result
        }
      case 2 =>
        //cache endorse,waiting revote
        logMsg(LogType.INFO, s"endorsement entry cache,self height=${pe.getCurrentHeight},block height=${this.cache.endorseinfo.blc.height}")
      case 1 =>
        //do not endorse
        logMsg(LogType.INFO, "it is blocker")
      case 3 =>
        //do not endorse
        logMsg(LogType.INFO, "it is not candator,do not endorse")
    }
  }

  private def CheckEndorseInfo(info: EndorsementInfo) = {
    if (this.cache == null) {
      //直接接收背书并进行处理
      this.cache = CacheOfEndorsement(info, sender, 0, 0, 0, 0, null)
      logMsg(LogType.INFO, "new endorsment")
    } else {
      //已经缓存背书了，检查是否是同一个背书请求
      if (info.blc.hashOfBlock.toStringUtf8() == this.cache.endorseinfo.blc.hashOfBlock.toStringUtf8() && info.blocker == this.cache.endorseinfo.blocker) {
        //检查为同一个背书
        logMsg(LogType.INFO, "endorsement is same")
      } else {
        //不是同一个背书
        this.cache = CacheOfEndorsement(info, sender, 0, 0, 0, 0, null)
        logMsg(LogType.INFO, "new endorsment,refresh cache")
      }
    }
    EndorseHandler
  }

  private def VerifyOfHandler(vr: VerifyResultOfEndorsement) = {
    if (this.cache != null && this.cache.endorseinfo.blc != null && this.cache.endorseinfo.blc.hashOfBlock.toStringUtf8() == vr.blockhash && this.cache.endorseinfo.blocker == vr.blocker) {
      vr.verifyType match {
        case VerifyTypeOfEndorsement.transSignVerify =>
          if (vr.result) {
            this.cache = CacheOfEndorsement(this.cache.endorseinfo, this.cache.requester, this.cache.repeatOfChecked, 1, this.cache.EndorseSignOfChecked, this.cache.transExeOfChecked, null)
          } else {
            this.cache = CacheOfEndorsement(this.cache.endorseinfo, this.cache.requester, this.cache.repeatOfChecked, 2, this.cache.EndorseSignOfChecked, this.cache.transExeOfChecked, null)
          }
        case VerifyTypeOfEndorsement.transExeVerify =>
          if (vr.result) {
            this.cache = CacheOfEndorsement(this.cache.endorseinfo, this.cache.requester, this.cache.repeatOfChecked, this.cache.transSignOfChecked, this.cache.EndorseSignOfChecked, 1, null)
          } else {
            this.cache = CacheOfEndorsement(this.cache.endorseinfo, this.cache.requester, this.cache.repeatOfChecked, this.cache.transSignOfChecked, this.cache.EndorseSignOfChecked, 2, null)
          }
        case VerifyTypeOfEndorsement.endorsementVerify =>
          if (vr.result) {
            this.cache = CacheOfEndorsement(this.cache.endorseinfo, this.cache.requester, this.cache.repeatOfChecked, this.cache.transSignOfChecked, 1, this.cache.transExeOfChecked, null)
          } else {
            this.cache = CacheOfEndorsement(this.cache.endorseinfo, this.cache.requester, this.cache.repeatOfChecked, this.cache.transSignOfChecked, 2, this.cache.transExeOfChecked, null)
          }
      }

      if (this.cache.repeatOfChecked == 1 && this.cache.transSignOfChecked == 1 && this.cache.transExeOfChecked == 1 && this.cache.EndorseSignOfChecked == 1) {
        this.cache = CacheOfEndorsement(this.cache.endorseinfo, this.cache.requester, this.cache.repeatOfChecked, this.cache.transSignOfChecked, this.cache.EndorseSignOfChecked, this.cache.transExeOfChecked,
          ResultOfEndorsed(true, BlockHelp.SignBlock(this.cache.endorseinfo.blc, pe.getSysTag), this.cache.endorseinfo.blc.hashOfBlock.toStringUtf8()))
      } else if (this.cache.repeatOfChecked == 2 || this.cache.transSignOfChecked == 2 || this.cache.transExeOfChecked == 2 || this.cache.EndorseSignOfChecked == 2) {
        this.cache = CacheOfEndorsement(this.cache.endorseinfo, this.cache.requester, this.cache.repeatOfChecked, this.cache.transSignOfChecked, this.cache.EndorseSignOfChecked, this.cache.transExeOfChecked,
          ResultOfEndorsed(false, BlockHelp.SignBlock(this.cache.endorseinfo.blc, pe.getSysTag), this.cache.endorseinfo.blc.hashOfBlock.toStringUtf8()))
      }
      EndorseHandler
    }
  }

  override def receive = {
    //Endorsement block
    case EndorsementInfo(block, blocker) =>
      CheckEndorseInfo(EndorsementInfo(block, blocker))
    case VerifyResultOfEndorsement(blockhash: String, blocker: String, verifyType: Int, result: Boolean) =>
      VerifyOfHandler(VerifyResultOfEndorsement(blockhash, blocker, verifyType, result))
    case ConsensusOfVote =>
      if (this.cache != null) {
        if(this.cache.endorseinfo.blc.height <= pe.getCurrentHeight){
          cache = null
        }else{
          EndorseHandler
        }
      }
    case _ => //ignore
  }

}