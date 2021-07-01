package rep.network.consensus.asyncconsensus.protocol.validatedagreement

import akka.actor.{ActorRef, Props}
import rep.log.RepLogger
import rep.network.base.ModuleBase
import rep.network.consensus.asyncconsensus.config.ConfigOfManager
import rep.network.consensus.asyncconsensus.protocol.binaryagreement.BinaryAgreementInActor
import rep.network.consensus.asyncconsensus.protocol.binaryagreement.BinaryAgreement.{AUX, CONF, EST, RepeatBinaryAgreement, ResultOfBA, StartBinaryAgreement}
import rep.network.consensus.asyncconsensus.protocol.commoncoin.CommonCoinInActor
import rep.network.consensus.asyncconsensus.protocol.consistentbroadcast.ConsistentBroadcast.CBC_RESULT
import rep.network.consensus.asyncconsensus.protocol.consistentbroadcast.ConsistentBroadcastInActor
import rep.network.consensus.asyncconsensus.protocol.msgcenter.{Broadcaster, SenderFactory}
import rep.network.consensus.asyncconsensus.protocol.validatedagreement.ValidatedAgreement.{StartValidateAgreement, VABA_VOTE}
import rep.protos.peer.ResultOfCommonCoin

import scala.collection.immutable.HashMap

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-02-13
 * @category	采用响应式编程Actor实现可验证一致性协议的消息的发送和接收。
 */

object ValidatedAgreementInActor{
  def props( moduleName: String, nodeName:String, pathSchema:String,moduleSchema:String, numOfNode:Int, numOfFault:Int,  caller:ActorRef): Props =
    Props(classOf[ValidatedAgreementInActor],moduleName, nodeName, pathSchema,moduleSchema, numOfNode, numOfFault, caller)
}

class ValidatedAgreementInActor  (moduleName: String,  var nodeName:String, pathSchema:String,moduleSchema:String,var numOfNode:Int, var numOfFault:Int, var caller:ActorRef) extends ModuleBase(moduleName)  {
  println(s"create ValidatedAgreementInActor actor addr=${this.selfAddr}")

  val vote_coin_actor = context.actorOf(CommonCoinInActor.props(nodeName+"-VoteCommonCoin", nodeName,this.pathSchema+"/"+moduleSchema,"{nodeName}"+"-VoteCommonCoin",false,self), nodeName+"-VoteCommonCoin")

  val ba_actor = context.actorOf(BinaryAgreementInActor.props(nodeName+"-binaryargree", nodeName,this.pathSchema+"/"+moduleSchema,"{nodeName}"+"-binaryargree",numOfNode,numOfFault,self), nodeName+"-binaryargree")

  var cbc_actors = new HashMap[String,ActorRef]()
  val nodeNames = ConfigOfManager.getManager.nodeNames
  nodeNames.foreach(name=>{
    cbc_actors += (this.nodeName+"-"+name+"_CBC") -> context.actorOf(ConsistentBroadcastInActor.props(this.nodeName+"-"+name+"_CBC",this.nodeName,this.pathSchema+"/"+moduleSchema,"{nodeName}-"+name+"_CBC", this.numOfNode,this.numOfFault,self), this.nodeName+"-"+name+"_CBC")
  })

  var cbc_commit_actors = new HashMap[String,ActorRef]()
  nodeNames.foreach(name=>{
    cbc_commit_actors += (this.nodeName+"-"+name+"_COMMITCBC") -> context.actorOf(ConsistentBroadcastInActor.props(this.nodeName+"-"+name+"_COMMITCBC",this.nodeName,this.pathSchema+"/"+moduleSchema,"{nodeName}-"+name+"_COMMITCBC", this.numOfNode,this.numOfFault,self), this.nodeName+"-"+name+"_COMMITCBC")
  })

  private val validateAgreement = new ValidatedAgreement(nodeName,pathSchema, moduleSchema, numOfNode, numOfFault,
    new Broadcaster(SenderFactory.getSenderOfScala(this.context.system),
      ConfigOfManager.getManager.getNodeNameAndAddr),caller,cbc_actors,cbc_commit_actors,vote_coin_actor,ba_actor)


  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"${this.selfAddr} Start"))
  }

  override def receive: Receive = {
    case start:StartValidateAgreement=>
      validateAgreement.StartHandle(start)
    case cbc_result:CBC_RESULT=>
      validateAgreement.CBCResultHandle(cbc_result)
    case vote_coin_result:ResultOfCommonCoin=>
      validateAgreement.VoteCoinHandle(vote_coin_result)
    case vaba_vote:VABA_VOTE=>
      validateAgreement.VABA_VOTEHandle(vaba_vote)
    case ba_result:ResultOfBA=>
      validateAgreement.ResultOfBAHandle(ba_result)
  }

}