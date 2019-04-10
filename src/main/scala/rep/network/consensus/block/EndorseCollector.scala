package rep.network.consensus.block

import akka.actor.{ Actor, ActorRef, Props, Address, ActorSelection }
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.routing._;
import rep.app.conf.{ SystemProfile, TimePolicy }
import rep.network.base.ModuleBase
import rep.network.consensus.block.Blocker.{ RequesterOfEndorsement, ResultOfEndorseRequester,CollectEndorsement,ConfirmedBlock }
import rep.network.tools.PeerExtension
import rep.network.Topic
import rep.protos.peer._
import rep.utils.GlobalUtils.{EventType}
import rep.utils._
import scala.collection.mutable._
import rep.log.trace.LogType
import rep.network.consensus.util.BlockVerify
import scala.util.control.Breaks
import rep.network.util.NodeHelp
import rep.network.consensus.util.BlockHelp
import rep.network.consensus.util.BlockVerify

object EndorseCollector {
  case object ResendEndorseInfo
  def props(name: String): Props = Props(classOf[EndorseCollector], name)
}

class EndorseCollector(moduleName: String) extends ModuleBase(moduleName) {
  import context.dispatcher
  import scala.concurrent.duration._
  import scala.collection.immutable._

  private var router :Router = null
  private var block : Block = null
  private var blocker :String = null
  private var recvedEndorse = new HashMap[ String, Signature ]()
  
  
  override def preStart(): Unit = {
    logMsg(LogType.INFO, "EndorseCollector Start")
  }
  
  private def createRouter={
    if(router == null){
      var list : ListBuffer[Routee] = new ListBuffer[Routee]()
      for(i <- 0 to pe.getNodeMgr.getStableNodes.size-1){
        var ca = context.actorOf(Props[EnodorsementRequester])
        context.watch(ca)
        list(i) =  new ActorRefRoutee(ca)
      }
      val rlist : IndexedSeq[Routee] = list.toIndexedSeq
      router = Router(SmallestMailboxRoutingLogic(),rlist)
    }
  }

  private def resetEndorseInfo(block:Block,blocker:String)={
      this.block = block
      this.blocker = blocker
      this.recvedEndorse.empty
      schedulerLink = clearSched()
   }
  
  private def clearEndorseInfo={
      this.block = null
      this.blocker = null
      this.recvedEndorse.empty
      schedulerLink = clearSched()
  }
  
  private def resendEndorser={
    pe.getNodeMgr.getStableNodes.foreach(f=>{
            if(!recvedEndorse.contains(f.toString)){
              router.route(RequesterOfEndorsement(block,blocker,f), self) 
            }
       })
  }
  
  
  private def CheckAndFinishHandler{
    if(NodeHelp.ConsensusConditionChecked(this.recvedEndorse.size+1,pe.getNodeMgr.getNodes.size)){
            this.recvedEndorse.foreach(f=>{
              this.block = BlockHelp.AddEndorsementToBlock(this.block, f._2)
            })
             var consensus = this.block.endorsements.toArray[Signature]
          BlockVerify.sort(consensus)
        this.block = this.block.withEndorsements(consensus)
        mediator ! Publish(Topic.Block, new ConfirmedBlock(this.block, sender))
        //sendEvent(EventType.RECEIVE_INFO, mediator, selfAddr, Topic.Block,
        //                        Event.Action.ENDORSEMENT)   
             clearEndorseInfo                   
          }
  }
  
  override def receive = {
    case CollectEndorsement(block, blocker) =>
      createRouter
      resetEndorseInfo(block,blocker)
      pe.getNodeMgr.getStableNodes.foreach(f=>{
        router.route(RequesterOfEndorsement(block,blocker,f), self) 
      })
      schedulerLink = scheduler.scheduleOnce(TimePolicy.getTimeoutEndorse*1.5 seconds, self, EndorseCollector.ResendEndorseInfo)
    case EndorseCollector.ResendEndorseInfo =>
      if(this.block != null){
         resendEndorser
      }
    case ResultOfEndorseRequester(result,endors,blockhash,endorser)=>
      if(this.block != null){
        if(this.block.hashOfBlock.toStringUtf8().equals(blockhash)){
        if(result){
          recvedEndorse += endorser.toString -> endors
          CheckAndFinishHandler
        }
      }
      }
    case _ => //ignore
  }
}