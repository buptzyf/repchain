package rep.network.consensus.asyncconsensus.protocol.validatedcommonsubset

import akka.actor.{ActorRef, Props}
import rep.log.RepLogger
import rep.network.base.ModuleBase
import rep.network.consensus.asyncconsensus.config.ConfigOfManager
import rep.network.consensus.asyncconsensus.protocol.consistentbroadcast.ConsistentBroadcastInActor
import rep.network.consensus.asyncconsensus.protocol.msgcenter.{Broadcaster, SenderFactory}
import rep.network.consensus.asyncconsensus.protocol.provablereliablebroadcast.ProvableReliableBroadcastInActor
import rep.network.consensus.asyncconsensus.protocol.validatedagreement.{ValidatedAgreement, ValidatedAgreementInActor}
import rep.network.consensus.asyncconsensus.protocol.validatedagreement.ValidatedAgreement.{StartValidateAgreement, VABA_Result, VABA_VOTE}
import rep.network.consensus.asyncconsensus.protocol.validatedcommonsubset.ValidatedCommonSubset.AddDataToVCS
import rep.protos.peer.ResultOfPRBC

import scala.collection.immutable.HashMap

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-02-13
 * @category	采用响应式编程Actor实现可验证公共子集协议的消息的发送和接收。
 */
object ValidatedCommonSubetInActor{
  def props( moduleName: String, nodeName:String, pathSchema:String,moduleSchema:String, numOfNode:Int, numOfFault:Int,  caller:ActorRef): Props =
    Props(classOf[ValidatedCommonSubetInActor],moduleName, nodeName, pathSchema,moduleSchema, numOfNode, numOfFault, caller)
}


class ValidatedCommonSubetInActor   (moduleName: String,  var nodeName:String, pathSchema:String,moduleSchema:String,var numOfNode:Int, var numOfFault:Int, var caller:ActorRef) extends ModuleBase(moduleName)  {
  println(s"create ValidatedAgreementInActor actor addr=${this.selfAddr}")


  var prbc_actors = new HashMap[String,ActorRef]()
  val nodeNames = ConfigOfManager.getManager.nodeNames
  nodeNames.foreach(name=>{
    prbc_actors +=
      (this.nodeName+"-"+name+"_PRBC") ->
        //context.actorOf(ConsistentBroadcastInActor.props(this.nodeName+"-"+name+"_PRBC",this.nodeName,
      //this.pathSchema+"/"+moduleSchema,"{nodeName}-"+name+"_PRBC", this.numOfNode,this.numOfFault,self), this.nodeName+"-"+name+"_PRBC")
        context.actorOf(ProvableReliableBroadcastInActor.props(this.nodeName+"-"+name+"_PRBC",this.nodeName,
          this.pathSchema+"/"+moduleSchema,"{nodeName}-"+name+"_PRBC", this.numOfNode,this.numOfFault,self), this.nodeName+"-"+name+"_PRBC")
  })

  val vaba_actor = context.actorOf(ValidatedAgreementInActor.props(this.nodeName+"-vaba", this.nodeName,this.pathSchema+"/"+moduleSchema,"{nodeName}"++"-vaba",this.numOfNode,this.numOfFault,self), this.nodeName+"-vaba")

  private val validateCommonSubset = new ValidatedCommonSubset(nodeName,pathSchema, moduleSchema, numOfNode, numOfFault,
    new Broadcaster(SenderFactory.getSenderOfScala(this.context.system),
      ConfigOfManager.getManager.getNodeNameAndAddr),caller,prbc_actors,vaba_actor)


  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"${this.selfAddr} Start"))
  }

  override def receive: Receive = {
    case addToVcs:AddDataToVCS=>
      validateCommonSubset.AddDataToVCSHandle(addToVcs)
    case prbc_result:ResultOfPRBC=>
      validateCommonSubset.ResultOfPRBCHandle(prbc_result)
    case baba_result:VABA_Result=>
      validateCommonSubset.VABA_ResultHandle(baba_result)
  }

}
