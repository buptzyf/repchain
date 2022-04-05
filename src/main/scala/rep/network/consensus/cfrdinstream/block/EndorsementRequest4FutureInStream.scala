package rep.network.consensus.cfrdinstream.block

import akka.actor.{ActorSelection, Address, Props}
import akka.pattern.{AskTimeoutException, ask}
import akka.util.Timeout
import rep.app.conf.TimePolicy
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.base.ModuleBase
import rep.network.consensus.cfrd.MsgOfCFRD.{EndorsementInfo, EndorsementInfoInStream, RequesterOfEndorsement, RequesterOfEndorsementInStream, ResendEndorseInfo, ResultFlagOfEndorse, ResultOfEndorseRequester, ResultOfEndorsed}
import rep.network.consensus.util.BlockVerify
import rep.network.module.cfrd.CFRDActorType
import rep.network.sync.SyncMsg.StartSync
import rep.network.util.NodeHelp
import rep.proto.rc2.Block
import rep.utils.GlobalUtils.BlockerInfo

import scala.concurrent.{Await, TimeoutException}

object EndorsementRequest4FutureInStream{
  def props(name: String): Props = Props(classOf[EndorsementRequest4FutureInStream], name)
}

class EndorsementRequest4FutureInStream (moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.concurrent.duration._

  implicit val timeout = Timeout((TimePolicy.getTimeoutEndorse* 2).seconds)
  private var voteinfo: BlockerInfo = null
  private var block:Block = null
  //private val endorsementActorName = "/user/modulemanager/endorser"
  //private val endorsementActorName = "/user/modulemanager/dispatchofRecvendorsement"//dispatchofRecvendorsement
  private val endorsementActorName = "/user/modulemanager/dispatchofRecvendorsement"

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "EndorsementRequest4FutureInStream Start"))
  }

  private def ExecuteOfEndorsement(addr: Address, data: EndorsementInfoInStream): ResultOfEndorsed = {
    try {
      val selection: ActorSelection = context.actorSelection(toAkkaUrl(addr, endorsementActorName));
      val future1 = selection ? data
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"--------EndorsementRequest4FutureInStream waiting resultheight=${data.blc.header.get.height},local height=${pe.getCurrentHeight}"))
      Await.result(future1, timeout.duration).asInstanceOf[ResultOfEndorsed]
    } catch {
      case e: AskTimeoutException =>
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"--------EndorsementRequest4FutureInStream timeout,height=${data.blc.header.get.height},local height=${pe.getCurrentHeight}"))
        null
      case te: TimeoutException =>
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"--------EndorsementRequest4FutureInStream java timeout,height=${data.blc.header.get.height},local height=${pe.getCurrentHeight}"))
        null
    }
  }

  private def toAkkaUrl(addr: Address, actorName: String): String = {
    return addr.toString + "/" + actorName;
  }

  private def EndorsementVerify(block: Block, result: ResultOfEndorsed): Boolean = {
    val bb = block.header.get.clearEndorsements.toByteArray
    val ev = BlockVerify.VerifyOneEndorseOfBlock(result.endor, bb, pe.getSysTag)
    ev._1
  }

  private def handler(reqinfo: RequesterOfEndorsementInStream) = {
    schedulerLink = clearSched()

    RepTimeTracer.setStartTime(pe.getSysTag, s"Endorsement-request-${moduleName}", System.currentTimeMillis(),reqinfo.blc.header.get.height,reqinfo.blc.transactions.size)
    val result = this.ExecuteOfEndorsement(reqinfo.endorer, EndorsementInfoInStream(reqinfo.blc, reqinfo.blocker,reqinfo.voteindex,reqinfo.voteHeight))
    if (result != null) {
      if (result.result == ResultFlagOfEndorse.success) {
        if (EndorsementVerify(reqinfo.blc, result)) {
          val re = ResultOfEndorseRequester(true, result.endor, result.BlockHash, reqinfo.endorer)
          context.parent ! re
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"--------EndorsementRequest4FutureInStream, send endorsement, height=${reqinfo.blc.header.get.height},local height=${pe.getCurrentHeight} "))
        } else {
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"--------EndorsementRequest4FutureInStream recv endorsement result is error, result=${result.result},height=${reqinfo.blc.header.get.height},local height=${pe.getCurrentHeight}"))
          context.parent ! ResultOfEndorseRequester(false, null, reqinfo.blc.header.get.hashPresent.toStringUtf8(), reqinfo.endorer)
        }
      } else {
        if (result.result == ResultFlagOfEndorse.BlockHeightError) {
          if (result.endorserOfChainInfo.height > pe.getCurrentHeight + 1) {
            //todo 需要从块缓冲判断是否启动块同步
            pe.getActorRef(CFRDActorType.ActorType.synchrequester) ! StartSync(false)
            context.parent ! ResultOfEndorseRequester(false, null, reqinfo.blc.header.get.hashPresent.toStringUtf8(), reqinfo.endorer)
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"--------EndorsementRequest4FutureInStream recv endorsement result must synch,height=${reqinfo.blc.header.get.height},local height=${pe.getCurrentHeight} "))
          } else {
            context.parent ! ResendEndorseInfo(reqinfo.endorer)
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"--------endorsement node's height low,must resend endorsement ,height=${reqinfo.blc.header.get.height},local height=${pe.getCurrentHeight} "))
          }
        }else if(result.result == ResultFlagOfEndorse.EnodrseNodeIsSynching || result.result == ResultFlagOfEndorse.EndorseNodeNotVote){
          context.parent ! ResendEndorseInfo(reqinfo.endorer)
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"--------endorsement node is synching, must resend endorsement,height=${reqinfo.blc.header.get.height},local height=${pe.getCurrentHeight} "))
        }else if(result.result == ResultFlagOfEndorse.VoteIndexError){
          context.parent ! ResendEndorseInfo(reqinfo.endorer)
          //pe.getActorRef(CFRDActorType.ActorType.voter) ! VoteOfReset
        }
      }
    } else {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"--------EndorsementRequest4FutureInStream recv endorsement result is null,height=${reqinfo.blc.header.get.height},local height=${pe.getCurrentHeight} "))
      //context.parent ! ResultOfEndorseRequester(false, null, reqinfo.blc.hashOfBlock.toStringUtf8(), reqinfo.endorer)
      context.parent ! ResendEndorseInfo(reqinfo.endorer)
    }

    RepTimeTracer.setEndTime(pe.getSysTag, s"Endorsement-request-${moduleName}", System.currentTimeMillis(),reqinfo.blc.header.get.height,reqinfo.blc.transactions.size)
  }

  private def isAcceptEndorseRequest(vi: BlockerInfo, blc: Block): Boolean = {
    var r = true
    /*if (this.voteinfo == null) {
      this.voteinfo = vi
      this.block = null
      r = true
    } else {
      if (!NodeHelp.IsSameVote(vi, pe.getBlocker)) {
        //已经切换出块人，初始化信息
        this.voteinfo = vi
        this.block = null
        r = true
      } else {
        if (this.block == null) {
          if (blc.previousBlockHash.toStringUtf8() == pe.getCurrentBlockHash) {
            r = true
          }
        } else {
          if (blc.previousBlockHash.toStringUtf8 == this.block.hashOfBlock.toStringUtf8) {
            r = true
          }
        }
      }
    }*/
    r
  }


  override def receive = {
    case RequesterOfEndorsementInStream(block, blocker, addr,voteindex,voteheiht) =>
      //待请求背书的块的上一个块的hash不等于系统最新的上一个块的hash，停止发送背书
      if(NodeHelp.isBlocker(pe.getSysTag, pe.getBlocker.blocker)){
        if(isAcceptEndorseRequest(pe.getBlocker, block)){
          this.block = block
          handler(RequesterOfEndorsementInStream(block, blocker, addr,voteindex,voteheiht))
        }else{
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"--------EndorsementRequest4FutureInStream back out  endorsement,prehash not equal  ,height=${block.header.get.height},local height=${pe.getCurrentHeight} "))
        }
      }
    case _ => //ignore
  }
}