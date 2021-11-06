package rep.network.consensus.cfrdinstream.block

import java.util.concurrent.ConcurrentHashMap

import akka.actor.{ActorRef, Address, Props}
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.routing.{ActorRefRoutee, RoundRobinRoutingLogic, Routee, Router, SmallestMailboxRoutingLogic}
import rep.app.conf.SystemProfile
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.autotransaction.Topic
import rep.network.base.ModuleBase
import rep.network.consensus.byzantium.ConsensusCondition
import rep.network.consensus.cfrd.MsgOfCFRD.{BlockInfoOfConsensus, CollectEndorsement, EndorsementFinishMsgInStream, ForceVoteInfo, RequesterOfEndorsement, RequesterOfEndorsementInStream, ResendEndorseInfo, ResultOfEndorseRequester}
import rep.network.consensus.util.{BlockHelp, BlockVerify}
import rep.network.module.cfrd.CFRDActorType
import rep.network.util.NodeHelp
import rep.protos.peer.{Block, Event, Signature}
import rep.storage.ImpDataPreloadMgr
import rep.utils.GlobalUtils.{BlockerInfo, EventType}

import scala.util.control.Breaks.{break, breakable}
import scala.collection.JavaConverters._
import scala.collection.immutable.HashMap
import scala.util.control.Breaks.breakable

object EndorseCollectorInStream {
  def props(name: String): Props = Props(classOf[EndorseCollectorInStream], name)

  case class packageOfEndorse(var consensusInfo: BlockInfoOfConsensus, var result: HashMap[String, Signature], var isFinish: Boolean, var isConfirm: Boolean, var timeOfEndorse: Long)

}

class EndorseCollectorInStream(moduleName: String) extends ModuleBase(moduleName) {

  import context.dispatcher
  import scala.concurrent.duration._
  import scala.collection.immutable._

  private var voteinfo: BlockerInfo = null
  private var router: Router = null
  private var pActor:ActorRef = null
  private var block: Block = null
  private var blocker: String = null
  private var recvedEndorse = new HashMap[String, Signature]()

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("EndorseCollectorInStream Start"))
  }

  private def createRouter = {
    if (router == null) {
      var list: Array[Routee] = new Array[Routee](SystemProfile.getVoteNodeList.size() )
      for (i <- 0 to SystemProfile.getVoteNodeList.size()  - 1) {
        var ca = context.actorOf(EndorsementRequest4FutureInStream.props("endorsementrequester" + i), "endorsementrequester" + i)
        context.watch(ca)
        list(i) = new ActorRefRoutee(ca)
      }
      val rlist: IndexedSeq[Routee] = list.toIndexedSeq
      router = Router(SmallestMailboxRoutingLogic(), rlist)
    }
  }

  private def resetEndorseInfo(block: Block, blocker: ForceVoteInfo) = {
    this.block = block
    this.blocker = blocker.blocker
    this.recvedEndorse = this.recvedEndorse.empty
  }

  private def clearEndorseInfo = {
    this.block = null
    this.blocker = null
    this.recvedEndorse = this.recvedEndorse.empty
  }

  private def CheckAndFinishHandler {
    sendEvent(EventType.PUBLISH_INFO, mediator, pe.getSysTag, Topic.Endorsement, Event.Action.ENDORSEMENT)
    RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("entry collectioner check  "))
    if (ConsensusCondition.ConsensusConditionChecked(this.recvedEndorse.size + 1)) {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("collectioner package endorsement to block"))
      this.recvedEndorse.foreach(f => {
        this.block = BlockHelp.AddEndorsementToBlock(this.block, f._2)
      })
      var consensus = this.block.endorsements.toArray[Signature]
      consensus = BlockVerify.sort(consensus)
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("collectioner endorsement sort"))
      this.block = this.block.withEndorsements(consensus)
      RepTimeTracer.setEndTime(pe.getSysTag, "Endorsement", System.currentTimeMillis(), this.block.height, this.block.transactions.size)
      //mediator ! Publish(Topic.Block, new ConfirmedBlock(this.block, sender))
      //发出请求给父actor
      if(this.pActor != null) this.pActor ! EndorsementFinishMsgInStream(this.block,true)
      //pe.getActorRef(CFRDActorType.ActorType.endorsementcollectionerinstream) ! EndorsementFinishMsgInStream(this.block,true)
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("collectioner endorsementt finish"))
      clearEndorseInfo
    } else {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"collectioner check is error,get size=${this.recvedEndorse.size}"))
    }
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
    case CollectEndorsement(block, blocker) =>
      if (!pe.isSynching) {
        createRouter
        if (this.block != null && this.block.hashOfBlock.toStringUtf8() == block.hashOfBlock.toStringUtf8()) {
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"collectioner is waiting endorse result,height=${block.height},local height=${pe.getCurrentHeight}"))
        } else {
          if (isAcceptEndorseRequest(pe.getBlocker, block)) {
            this.pActor = sender()
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"collectioner recv endorsement,height=${block.height},local height=${pe.getCurrentHeight}"))
            resetEndorseInfo(block, blocker)
            pe.getNodeMgr.getStableNodes.foreach(f => {
              if (NodeHelp.isBlocker(pe.getSysTag, pe.getBlocker.blocker)) {
                RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"collectioner send endorsement to requester,height=${block.height},local height=${pe.getCurrentHeight}"))
                router.route(RequesterOfEndorsementInStream(block, blocker.blocker, f, pe.getBlocker.VoteIndex,pe.getBlocker.VoteHeight), self)
              }
            })
          } else {
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"collectioner back out endorsement request,height=${block.height},local height=${pe.getCurrentHeight}"))
          }
        }
      }
    case ResultOfEndorseRequester(result, endors, blockhash, endorser) =>
      if (!pe.isSynching) {
        //block不空，该块的上一个块等于最后存储的hash，背书结果的块hash跟当前发出的块hash一致
        if (this.block != null  && this.block.hashOfBlock.toStringUtf8() == blockhash) {
          if (result) {
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"collectioner recv endorsement result,height=${block.height},local height=${pe.getCurrentHeight}"))
            recvedEndorse += endorser.toString -> endors
            CheckAndFinishHandler
          } else {
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"collectioner recv endorsement result,is error,height=${block.height},local height=${pe.getCurrentHeight}"))
          }
        } else {
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"collectioner back out endorsement result,local height=${pe.getCurrentHeight}"))
        }
      }
    case ResendEndorseInfo(endorer) =>
      if (!pe.isSynching) {
        if (NodeHelp.isBlocker(pe.getSysTag, pe.getBlocker.blocker)) {
          if (this.block != null && this.block.previousBlockHash.toStringUtf8() == pe.getCurrentBlockHash) {
            if (this.router != null) {
              router.route(RequesterOfEndorsementInStream(this.block, this.blocker, endorer, pe.getBlocker.VoteIndex,pe.getBlocker.VoteHeight), self)
            } else {
              RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"collectioner's router is null,height=${block.height},local height=${pe.getCurrentHeight}"))
            }
          } else {
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"collectioner back out resend endorsement request,local height=${pe.getCurrentHeight}"))
          }
        }
      }
    case _ => //ignore
  }
}