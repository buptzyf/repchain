package rep.network.consensus.endorse

import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import akka.pattern.AskTimeoutException
import scala.concurrent._

import akka.actor.{ ActorRef, Props, Address ,ActorSystemImpl}
import rep.crypto.Sha256
import rep.network.base.ModuleBase
import rep.network.Topic
import rep.network.util.NodeHelp
import rep.protos.peer.{ Event, Transaction }
import rep.app.conf.{ SystemProfile, TimePolicy }
import rep.storage.{ ImpDataPreload, ImpDataPreloadMgr }
import rep.utils.GlobalUtils.{ ActorType, BlockEvent, EventType, NodeStatus }
import com.sun.beans.decoder.FalseElementHandler
import rep.network.consensus.vote.Voter.VoteOfBlocker
import sun.font.TrueTypeFont
import scala.util.control.Breaks._
import scala.util.control.Exception.Finally
import java.util.concurrent.ConcurrentHashMap
import rep.log.trace.LogType
import rep.network.consensus.endorse.EndorseMsg.{ EndorsementInfo, ResultOfEndorsed }
import rep.network.consensus.block.Blocker.{PreTransBlock, PreTransBlockResult}
import rep.network.consensus.util.{ BlockVerify, BlockHelp }

object Endorser {
  def props(name: String): Props = Props(classOf[Endorser], name)
  case class CacheOfEndorsement(endorseinfo:EndorsementInfo,requester:ActorRef,
      repeatOfChecked:Boolean,transSignOfChecked:Boolean,EndorseSignOfChecked:Boolean,
      transExeOfChecked:Boolean,result:ResultOfEndorsed)
}

class Endorser(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.concurrent.duration._
  import rep.protos.peer._
  import rep.storage.ImpDataAccess
  import rep.network.consensus.endorse.Endorser.CacheOfEndorsement

  implicit val timeout = Timeout(TimePolicy.getTimeoutPreload seconds)
  
  //缓存当前的背书请求
  var cache : CacheOfEndorsement = null
  
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

  private def ExecuteTransactionOfBlock(block: Block): Boolean = {
    try {
      val future = pe.getActorRef(ActorType.preloaderoftransaction) ? PreTransBlock(block,"endorser")
      val result = Await.result(future, timeout.duration).asInstanceOf[PreTransBlockResult]
      if (result.result) {
        var tmpblock = result.blc.withHashOfBlock(block.hashOfBlock)
        BlockVerify.VerifyHashOfBlock(tmpblock)
      } else {
        false
      }
    } catch {
      case e: AskTimeoutException => false
    }
  }

  private def asyncVerifyTransaction(t: Transaction): Future[Boolean] = {
    val result = Promise[Boolean]
    if (pe.getTransPoolMgr.findTrans(t.id)) {
      result.success(true)
    } else {
      val tmp = BlockVerify.VerifyOneSignOfTrans(t, pe.getSysTag)
      if (tmp._1) {
        result.success(true)
      }else{
        result.success(false)
      }
    }
    result.future
  }

  private def asyncVerifyTransactions(block: Block): Boolean = {
    val listOfFuture: Seq[Future[Boolean]] = block.transactions.map(x => {
      asyncVerifyTransaction(x)
    })
    val futureOfList: Future[List[Boolean]] = Future.sequence(listOfFuture.toList)
    var result = true
    //breakable(
    futureOfList.map(x => {
      x.foreach(f => {
        if (!f) {
          result = false
          logMsg(LogType.INFO, "endorser verify tran sign is error, break")
          //break
        }
      })
    })
    //)
    result
  }

  private def checkedOfEndorseCondition(block: Block, blocker: String) = {
    if (pe.getSystemStatus == NodeStatus.Endorsing) {
      logMsg(LogType.INFO, "endorser recv endorsement")
      if (NodeHelp.isCandidateNow(pe.getSysTag, pe.getNodeMgr.getCandidator)) {
        logMsg(LogType.INFO, "endorser is candidator")
        if (block.previousBlockHash.toStringUtf8 == pe.getCurrentBlockHash) {
          logMsg(LogType.INFO, "endorser is curent blockhash")
          if (NodeHelp.isBlocker(blocker, pe.getBlocker.blocker)) {
            logMsg(LogType.INFO, "endorser verify blocker")
            //符合要求，可以进行交易以及交易结果进行校验
            val bv = BlockVerify.VerifyAllEndorseOfBlock(block, pe.getSysTag)
            if (bv._1) {
              logMsg(LogType.INFO, "endorser verify blocker sign")
              //出块人的签名验证正确
              if (!hasRepeatOfTrans(block.transactions)) {
                logMsg(LogType.INFO, "endorser trans is repeat")
                //  没有重复交易
                if (asyncVerifyTransactions(block)) {
                  logMsg(LogType.INFO, "endorser verify trans  sign ")
                  //交易签名验证正确
                  if (ExecuteTransactionOfBlock(block)) {
                    logMsg(LogType.INFO, "endorser verify is finish")
                    //交易执行结果一致
                    sendEvent(EventType.PUBLISH_INFO, mediator, selfAddr, Topic.Block, Event.Action.ENDORSEMENT)
                    sender ! ResultOfEndorsed(true, BlockHelp.SignBlock(block, pe.getSysTag), block.hashOfBlock.toStringUtf8())
                  } else {
                    //交易执行结果不一致
                    logMsg(LogType.INFO, "endorser trans verify is error")
                    sender ! ResultOfEndorsed(false, null, block.hashOfBlock.toStringUtf8())
                  }
                } else {
                  //交易签名验证错误
                  logMsg(LogType.INFO, "endorser verify  trans  sign is error")
                  sender ! ResultOfEndorsed(false, null, block.hashOfBlock.toStringUtf8())
                }
              } else {
                //存在重复交易
                logMsg(LogType.INFO, "endorser verify repeat error")
                sender ! ResultOfEndorsed(false, null, block.hashOfBlock.toStringUtf8())
              }
            } else {
              //出块人的签名验证错误
              logMsg(LogType.INFO, "endorser verify blocker  sign is error")
              sender ! ResultOfEndorsed(false, null, block.hashOfBlock.toStringUtf8())
            }
          } else {
            //产生待背书的节点不是出块节点
            logMsg(LogType.INFO, "endorser verify node is not blocker ")
            sender ! ResultOfEndorsed(false, null, block.hashOfBlock.toStringUtf8())
          }
        } else {
          //背书节点不是当前节点的后续块
          logMsg(LogType.INFO, "endorser verify block hash is error ")
          sender ! ResultOfEndorsed(false, null, block.hashOfBlock.toStringUtf8())
        }
      } else {
        //节点不是共识节点
        logMsg(LogType.INFO, "endorser verify node is candidator ")
        sender ! ResultOfEndorsed(false, null, block.hashOfBlock.toStringUtf8())
      }
    } else {
      //节点不是背书节点，如果是自己接到自己的背书请求也会走这个分支
      logMsg(LogType.INFO, "endorser verify itself ")
      //sender ! ResultOfEndorsed(false, null, block.hashOfBlock.toStringUtf8())
    }
  }

  private def isAllowEndorse(info:EndorsementInfo):Int={
    if(info.blocker == pe.getSysTag){
      logMsg(LogType.INFO, "endorser is itself,do not endorse")
      1
    }else{
      if (NodeHelp.isCandidateNow(pe.getSysTag, pe.getNodeMgr.getCandidator)) {
        //是候选节点，可以背书
        if(info.blc.previousBlockHash.toStringUtf8 == pe.getCurrentBlockHash && NodeHelp.isBlocker(info.blocker, pe.getBlocker.blocker)) {
          //可以进入背书
          logMsg(LogType.INFO, "vote result equal，allow entry endorse")
          0
        }else{
          //当前块hash和抽签的出块人都不一致，暂时不能够进行背书，可以进行缓存
          logMsg(LogType.INFO, "block hash is not equal or blocker is not equal")
          2
        }
      }else{
        //不是候选节点，不能够参与背书
        logMsg(LogType.INFO, "it is not candidator node")
        3
      }
    }
  }
  
  private def EndorseHandler={
    val r = isAllowEndorse(this.cache.endorseinfo)
      r match{
        case 0 =>
          //entry endorse
          if(this.cache.result == null){
            
          }else{
            //send result to endorse requester
            if(this.cache.result.result){
              logMsg(LogType.INFO, "endorser is success")
              sendEvent(EventType.PUBLISH_INFO, mediator, selfAddr, Topic.Block, Event.Action.ENDORSEMENT)
            }else{
              logMsg(LogType.INFO, s"endorser is failed,cause={isrepeat=${this.cache.repeatOfChecked},endorsesign=${this.cache.EndorseSignOfChecked},transsign=${this.cache.transSignOfChecked},transexe=${this.cache.transExeOfChecked}}")
            }
            this.cache.requester ! this.cache.result
          }
        case 2 =>
          //cache endorse,waiting revote
          logMsg(LogType.INFO, "endorsement entry cache")
        case 1 =>
          //do not endorse
          logMsg(LogType.INFO, "it is blocker")
        case 3 =>
          //do not endorse
          logMsg(LogType.INFO, "it is not candator,do not endorse")  
      }
  }
  
  private def CheckEndorseInfo(info:EndorsementInfo)={
    if(this.cache == null){
      //直接接收背书并进行处理
      this.cache = CacheOfEndorsement(info,sender,false,false,false,false,null)
      logMsg(LogType.INFO, "new endorsment")
    }else{
      //已经缓存背书了，检查是否是同一个背书请求
      if(info.blc.hashOfBlock.toStringUtf8() == this.cache.endorseinfo.blc.hashOfBlock.toStringUtf8() && info.blocker == this.cache.endorseinfo.blocker){
        //检查为同一个背书
        logMsg(LogType.INFO, "endorsement is same")
      }else{
        //不是同一个背书
        this.cache = CacheOfEndorsement(info,sender,false,false,false,false,null)
        logMsg(LogType.INFO, "new endorsment,refresh cache")
      }
    }
    EndorseHandler
  }
  
  override def receive = {
    //Endorsement block
    case EndorsementInfo(block, blocker) =>
      checkedOfEndorseCondition(block, blocker)
    case _ => //ignore
  }

}