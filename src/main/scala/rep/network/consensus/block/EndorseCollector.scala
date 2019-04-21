package rep.network.consensus.block

import akka.actor.{ Actor, ActorRef, Props, Address, ActorSelection }
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.routing._;
import rep.app.conf.{ SystemProfile, TimePolicy }
import rep.network.base.ModuleBase
import rep.network.consensus.endorse.EndorseMsg.{ RequesterOfEndorsement, ResultOfEndorseRequester, CollectEndorsement }
import rep.network.consensus.block.Blocker.ConfirmedBlock
import rep.network.tools.PeerExtension
import rep.network.Topic
import rep.protos.peer._
import rep.utils.GlobalUtils.{ EventType }
import rep.utils._
import scala.collection.mutable._
import rep.network.consensus.util.BlockVerify
import scala.util.control.Breaks
import rep.network.util.NodeHelp
import rep.network.consensus.util.BlockHelp
import rep.network.consensus.util.BlockVerify
import rep.log.RepLogger

object EndorseCollector {
  case object ResendEndorseInfo
  def props(name: String): Props = Props(classOf[EndorseCollector], name)
}

class EndorseCollector(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.concurrent.duration._
  import scala.collection.immutable._

  private var router: Router = null
  private var block: Block = null
  private var blocker: String = null
  private var recvedEndorse = new HashMap[String, Signature]()

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "EndorseCollector Start"))
  }

  private def createRouter = {
    if (router == null) {
      var list: Array[Routee] = new Array[Routee](SystemProfile.getVoteNodeList.size())
      for (i <- 0 to SystemProfile.getVoteNodeList.size() - 1) {
        //EndorsementRequest4Future
        //var ca = context.actorOf(EnodorsementRequester.props("endorsementrequester" + i), "endorsementrequester" + i)
        var ca = context.actorOf(EndorsementRequest4Future.props("endorsementrequester" + i), "endorsementrequester" + i)
        context.watch(ca)
        list(i) = new ActorRefRoutee(ca)
      }
      val rlist: IndexedSeq[Routee] = list.toIndexedSeq
      router = Router(SmallestMailboxRoutingLogic(), rlist)
    }
  }

  private def resetEndorseInfo(block: Block, blocker: String) = {
    this.block = block
    this.blocker = blocker
    this.recvedEndorse = this.recvedEndorse.empty
    //schedulerLink = clearSched()
  }

  private def clearEndorseInfo = {
    this.block = null
    this.blocker = null
    this.recvedEndorse = this.recvedEndorse.empty
    //schedulerLink = clearSched()
  }

  private def resendEndorser = {
    //schedulerLink = clearSched()
    pe.getNodeMgr.getStableNodes.foreach(f => {
      if (!recvedEndorse.contains(f.toString)) {
        router.route(RequesterOfEndorsement(block, blocker, f), self)
      }
    })
    //schedulerLink = scheduler.scheduleOnce(TimePolicy.getTimeoutEndorse seconds, self, EndorseCollector.ResendEndorseInfo)
  }

  private def CheckAndFinishHandler {
    RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("collectioner check is finish "))
    if (NodeHelp.ConsensusConditionChecked(this.recvedEndorse.size + 1, pe.getNodeMgr.getNodes.size)) {
      //schedulerLink = clearSched()
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("collectioner package endorsement to block"))
      this.recvedEndorse.foreach(f => {
        this.block = BlockHelp.AddEndorsementToBlock(this.block, f._2)
      })
      var consensus = this.block.endorsements.toArray[Signature]
      BlockVerify.sort(consensus)
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("collectioner endorsement sort"))
      this.block = this.block.withEndorsements(consensus)
      mediator ! Publish(Topic.Block, new ConfirmedBlock(this.block, sender))
      sendEvent(EventType.RECEIVE_INFO, mediator, selfAddr, Topic.Block,
        Event.Action.ENDORSEMENT)
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "collectioner endorsementt finish"))
      clearEndorseInfo
    } else {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"collectioner check is error,get size=${this.recvedEndorse.size}"))
    }
  }

  override def receive = {
    case CollectEndorsement(block, blocker) =>
      createRouter
      if (this.block != null && this.block.hashOfBlock.toStringUtf8().equals(block.hashOfBlock.toStringUtf8())) {
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("collectioner is waiting endorse result"))
      } else {
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "collectioner recv endorsement"))
        resetEndorseInfo(block, blocker)
        pe.getNodeMgr.getStableNodes.foreach(f => {
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "collectioner send endorsement to requester"))
          router.route(RequesterOfEndorsement(block, blocker, f), self)
        })
        //schedulerLink = scheduler.scheduleOnce(TimePolicy.getTimeoutEndorse seconds, self, EndorseCollector.ResendEndorseInfo)
      }

    /*case EndorseCollector.ResendEndorseInfo =>
      if (this.block != null) {
        logMsg(LogType.INFO, "collectioner resend endorsement")
        resendEndorser
      }*/
    case ResultOfEndorseRequester(result, endors, blockhash, endorser) =>
      if (this.block != null) {
        if (this.block.hashOfBlock.toStringUtf8().equals(blockhash)) {
          if (result) {
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "collectioner recv endorsement result"))
            recvedEndorse += endorser.toString -> endors
            CheckAndFinishHandler
          } else {
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("collectioner recv endorsement result,is error"))
          }
        }
      }
    case _ => //ignore
  }
}