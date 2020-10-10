package rep.network.consensus.cfrd.block

import akka.actor.{ActorRef, Address, Props}
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.routing.{ActorRefRoutee, RoundRobinRoutingLogic, Routee, Router}
import rep.app.conf.SystemProfile
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.autotransaction.Topic
import rep.network.base.ModuleBase
import rep.network.consensus.byzantium.ConsensusCondition
import rep.network.consensus.cfrd.MsgOfCFRD.{BlockInfoOfConsensus, CollectEndorsement, RequesterOfEndorsement, ResendEndorseInfo, ResendEndorseInfoInStream, ResultOfEndorseRequester, ResultOfEndorsementInStream}
import rep.network.consensus.common.MsgOfConsensus.{ConfirmedBlock, ConfirmedBlockInStream}
import rep.network.consensus.util.{BlockHelp, BlockVerify}
import rep.network.util.NodeHelp
import rep.protos.peer.{Block, Event, Signature}
import rep.utils.GlobalUtils.{BlockerInfo, EventType}

import scala.collection.immutable.HashMap

object EndorseCollectorInStream{
  def props(name: String): Props = Props(classOf[EndorseCollectorInStream], name)
  case class packageOfEndorse(var consensusInfo:BlockInfoOfConsensus,var result:HashMap[String, Signature],var isFinish:Boolean,var isConfirm : Boolean,var timeOfEndorse:Long)
}

class EndorseCollectorInStream(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.concurrent.duration._
  import scala.collection.immutable._

  private var router: Router = null

  private var currentVoteInfo: BlockerInfo = null
  private var endorseManager : HashMap[Int,EndorseCollectorInStream.packageOfEndorse] = null
  private var SerialOfEndorse : Int = -1

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "EndorseCollectorInStream Start"))
  }

  private def createRouter = {
    if (router == null) {
      var list: Array[Routee] = new Array[Routee](SystemProfile.getVoteNodeList.size())
      for (i <- 0 to SystemProfile.getVoteNodeList.size() - 1) {
        var ca = context.actorOf(EndorsementRequest4FutureInStream.props("endorsementrequester" + i), "endorsementrequester" + i)
        context.watch(ca)
        list(i) = new ActorRefRoutee(ca)
      }
      val rlist: IndexedSeq[Routee] = list.toIndexedSeq
      //RoundRobinRoutingLogic SmallestMailboxRoutingLogic
      router = Router(RoundRobinRoutingLogic(), rlist)
    }
  }

  private def addEndorseInfo(msgOfEndorse: BlockInfoOfConsensus) = {
    if(this.currentVoteInfo == null){
      if(msgOfEndorse.isFirst){
        this.currentVoteInfo = msgOfEndorse.voteinfo
        this.endorseManager = new HashMap[Int,EndorseCollectorInStream.packageOfEndorse]()
        if(!this.endorseManager.contains(msgOfEndorse.currentBlockSerial))
          this.endorseManager += msgOfEndorse.currentBlockSerial->EndorseCollectorInStream.packageOfEndorse(msgOfEndorse,new HashMap[String, Signature],false,false,System.currentTimeMillis())
        this.SerialOfEndorse = msgOfEndorse.currentBlockSerial
        sendEndorseInfo(msgOfEndorse)
      }
    }else{
      if(!this.isChangeBlocker(this.currentVoteInfo,msgOfEndorse.voteinfo) ){
        if(!this.isChangeBlocker(this.currentVoteInfo)){
          if(!this.endorseManager.contains(msgOfEndorse.currentBlockSerial))
            this.endorseManager += msgOfEndorse.currentBlockSerial->EndorseCollectorInStream.packageOfEndorse(msgOfEndorse,new HashMap[String, Signature],false,false,System.currentTimeMillis())
          this.SerialOfEndorse = msgOfEndorse.currentBlockSerial
          sendEndorseInfo(msgOfEndorse)
        }else{
          if(msgOfEndorse.isFirst){
            this.currentVoteInfo = msgOfEndorse.voteinfo
            this.endorseManager = new HashMap[Int,EndorseCollectorInStream.packageOfEndorse]()
            if(!this.endorseManager.contains(msgOfEndorse.currentBlockSerial))
              this.endorseManager += msgOfEndorse.currentBlockSerial->EndorseCollectorInStream.packageOfEndorse(msgOfEndorse,new HashMap[String, Signature],false,false,System.currentTimeMillis())
            this.SerialOfEndorse = msgOfEndorse.currentBlockSerial
            sendEndorseInfo(msgOfEndorse)
          }
        }
      }else{
        if(msgOfEndorse.isFirst){
          this.currentVoteInfo = msgOfEndorse.voteinfo
          this.endorseManager = new HashMap[Int,EndorseCollectorInStream.packageOfEndorse]()
          if(!this.endorseManager.contains(msgOfEndorse.currentBlockSerial))
            this.endorseManager += msgOfEndorse.currentBlockSerial->EndorseCollectorInStream.packageOfEndorse(msgOfEndorse,new HashMap[String, Signature],false,false,System.currentTimeMillis())
          this.SerialOfEndorse = msgOfEndorse.currentBlockSerial
          sendEndorseInfo(msgOfEndorse)
        }
      }
    }
  }

  private def sendEndorseInfo(consensusInfo:BlockInfoOfConsensus): Unit ={
    pe.getNodeMgr.getStableNodes.foreach(f => {
      if(NodeHelp.isBlocker(pe.getSysTag, pe.getBlocker.blocker)){
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"collectioner send endorsement to requester,height=${consensusInfo.blc.height},local height=${pe.getCurrentHeight}"))
        router.route(consensusInfo, self)
      }
    })
  }


  private def clearEndorseInfo = {
    this.SerialOfEndorse = -1
    this.currentVoteInfo = null
    this.endorseManager = null
  }



  private def CheckAndFinishHandler(serail:Int) {
    sendEvent(EventType.PUBLISH_INFO, mediator, pe.getSysTag, Topic.Endorsement, Event.Action.ENDORSEMENT)
    RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("entry collectioner check  "))
    var tmp = this.endorseManager(serail)
    if (ConsensusCondition.ConsensusConditionChecked(tmp.result.size + 1)) {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("collectioner package endorsement to block"))
      tmp.result.foreach(f => {
        tmp.consensusInfo = BlockInfoOfConsensus(tmp.consensusInfo.voteinfo,tmp.consensusInfo.blocker,
          BlockHelp.AddEndorsementToBlock(tmp.consensusInfo.blc, f._2),tmp.consensusInfo.currentBlockSerial,tmp.consensusInfo.isFirst,tmp.consensusInfo.isLastBlock)
      })
      var consensus = tmp.consensusInfo.blc.endorsements.toArray[Signature]
      consensus=BlockVerify.sort(consensus)
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("collectioner endorsement sort"))
      tmp.consensusInfo = BlockInfoOfConsensus(tmp.consensusInfo.voteinfo,tmp.consensusInfo.blocker,
        tmp.consensusInfo.blc.withEndorsements(consensus),tmp.consensusInfo.currentBlockSerial,tmp.consensusInfo.isFirst,tmp.consensusInfo.isLastBlock)
      RepTimeTracer.setEndTime(pe.getSysTag, "Endorsement", System.currentTimeMillis(),tmp.consensusInfo.blc.height,tmp.consensusInfo.blc.transactions.size)
      tmp.isFinish = true
      for(i<-0 to serail) {
        var temp = this.endorseManager(i)
        if (temp.isFinish) {
          if (!temp.isConfirm) {
            mediator ! Publish(Topic.Block, new ConfirmedBlockInStream(temp.consensusInfo.voteinfo,temp.consensusInfo.currentBlockSerial,
              temp.consensusInfo.blc.hashOfBlock.toStringUtf8,temp.consensusInfo.isFirst,temp.consensusInfo.isLastBlock,temp.consensusInfo.blc: Block, sender()))
            tmp.isConfirm = true
          }
        }
      }
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( "collectioner endorsementt finish"))
    }
  }

  override def receive = {
    case BlockInfoOfConsensus(voteinfo,blocker,blc,currentBlockSerial,isFirst,isLastBlock)=>
      if(!pe.isSynching){
        createRouter
        this.addEndorseInfo(BlockInfoOfConsensus(voteinfo,blocker,blc,currentBlockSerial,isFirst,isLastBlock))
      }
    case ResultOfEndorsementInStream(voteinfo,currentBlockSerial,blockHash,isFirst,isLastBlock,result, endor,endorser)=>
      if(!pe.isSynching){
        //block不空，该块的上一个块等于最后存储的hash，背书结果的块hash跟当前发出的块hash一致
        if(this.endorseManager!= null && this.endorseManager.contains(currentBlockSerial)){
          if(this.isChangeBlocker(this.currentVoteInfo,voteinfo) && this.isChangeBlocker(voteinfo)){
            if(result){
              var tmp = this.endorseManager(currentBlockSerial)
              tmp.result += endorser.toString -> endor
              CheckAndFinishHandler(currentBlockSerial)
            }
          }
        }
      }
    case ResendEndorseInfoInStream(voteinfo,currentBlockSerial,blockHash,isFirst,isLastBlock,endorer)=>
      if(!pe.isSynching){
        if(this.isChangeBlocker(this.currentVoteInfo,voteinfo) && this.isChangeBlocker(voteinfo)){
          if(this.router != null){
            var tmp = this.endorseManager(currentBlockSerial)
            sendEndorseInfo(tmp.consensusInfo)
          }else{
            RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"collectioner's router is null,height=${},local height=${pe.getCurrentHeight}"))
          }
        }else{
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"collectioner back out resend endorsement request,local height=${pe.getCurrentHeight}"))
        }
      }
    case _ => //ignore
  }
}