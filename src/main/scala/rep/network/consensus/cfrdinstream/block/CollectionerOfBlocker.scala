package rep.network.consensus.cfrdinstream.block

import akka.actor.{Props}
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.util.Timeout
import rep.app.conf.{SystemProfile, TimePolicy}
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.base.ModuleBase
import rep.network.consensus.cfrd.MsgOfCFRD.{CollectEndorsement, EndorsementFinishMsgInStream}
import rep.network.module.cfrd.CFRDActorType
import rep.network.util.NodeHelp
import rep.proto.rc2.Block
import akka.pattern.{AskTimeoutException, ask}
import rep.network.autotransaction.Topic
import rep.network.consensus.common.MsgOfConsensus.ConfirmedBlock
import rep.utils.GlobalUtils.BlockerInfo
import scala.concurrent.{Await, TimeoutException}

object CollectionerOfBlocker {
  def props(name: String): Props = Props(classOf[CollectionerOfBlocker], name)
}

class CollectionerOfBlocker(moduleName: String) extends ModuleBase(moduleName) {

  import scala.concurrent.duration._

  implicit val timeout = Timeout((TimePolicy.getTimeoutEndorse * 4).seconds)
  private var voteinfo: BlockerInfo = null
  private var lastBlock: Block = null

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("CollectionerOfBlocker Start"))
  }

  private def ExecuteOfEndorsementInStream(data: CollectEndorsement): EndorsementFinishMsgInStream = {
    try {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"--------ExecuteOfEndorsementInStream waiting resultheight=${data.blc.header.get.height},local height=${pe.getCurrentHeight}"))
      val future1 = pe.getActorRef(CFRDActorType.ActorType.endorsementcollectioner) ? data
      Await.result(future1, timeout.duration).asInstanceOf[EndorsementFinishMsgInStream]
    } catch {
      case e: AskTimeoutException =>
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"--------ExecuteOfEndorsement timeout,height=${data.blc.header.get.height},local height=${pe.getCurrentHeight}"))
        null
      case te: TimeoutException =>
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"--------ExecuteOfEndorsement java timeout,height=${data.blc.header.get.height},local height=${pe.getCurrentHeight}"))
        null
    }
  }


  private def isAcceptEndorseRequest(vi: BlockerInfo, block: Block): Boolean = {
    var r = false
    if (this.voteinfo == null) {
      this.voteinfo = vi
      this.lastBlock = null
      r = true
    } else {
      if (!NodeHelp.IsSameVote(vi, pe.getBlocker)) {
        //已经切换出块人，初始化信息
        this.voteinfo = vi
        this.lastBlock = null
        r = true
      } else {
        if (this.lastBlock == null) {
          r = true
        } else {
          if (this.lastBlock.header.get.height <= (this.voteinfo.VoteHeight + SystemProfile.getBlockNumberOfRaft) && !pe.getZeroOfTransNumFlag) {
            if(block.header.get.hashPrevious.toStringUtf8 == this.lastBlock.header.get.hashPresent.toStringUtf8){
              RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"--------accept endorse,bheight=${block.header.get.height}"))
              r = true
            }
          }
        }
      }
    }
    r
  }

  override def receive = {
    //case CollectEndorsement(block, blocker,index) =>
    case CollectEndorsement(block, blocker) =>
      //待请求背书的块的上一个块的hash不等于系统最新的上一个块的hash，停止发送背书
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"--------recv endorse new block,bheight=${block.header.get.height}"))
      if (NodeHelp.isBlocker(pe.getSysTag, pe.getBlocker.blocker)) {
        if (isAcceptEndorseRequest(pe.getBlocker, block)) {
          //pe.setConfirmHeight(block.height)
          this.lastBlock = block
          RepTimeTracer.setStartTime(pe.getSysTag, "Endorsement", System.currentTimeMillis(), block.header.get.height, block.transactions.size)
          //val re = ExecuteOfEndorsementInStream(CollectEndorsement(block, blocker,index))
          val re = ExecuteOfEndorsementInStream(CollectEndorsement(block, blocker))
          if (re!= null && re.result) {
            pe.setConfirmHeight(block.header.get.height)
            this.lastBlock = re.block
            mediator ! Publish(Topic.Block, new ConfirmedBlock(re.block, sender))
            pe.getTransPoolMgr.cleanPreloadCache("blockidentifier_"+this.lastBlock.header.get.height)
          }else{
            pe.getTransPoolMgr.rollbackTransaction("blockidentifier_"+this.lastBlock.header.get.height)
          }
          RepTimeTracer.setEndTime(pe.getSysTag, "Endorsement", System.currentTimeMillis(), block.header.get.height, block.transactions.size)
        }
      }else{
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"--------recv endorse new block,local not blocker,bheight=${block.header.get.height}"))
      }
    case _ => //ignore
  }
}