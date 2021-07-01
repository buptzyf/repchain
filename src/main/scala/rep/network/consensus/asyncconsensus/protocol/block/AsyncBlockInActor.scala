package rep.network.consensus.asyncconsensus.protocol.block

import akka.actor.{ActorRef, Props}
import rep.log.RepLogger
import rep.network.base.ModuleBase
import rep.network.consensus.asyncconsensus.config.ConfigOfManager
import rep.network.consensus.asyncconsensus.protocol.block.AsyncBlock.{StartBlock, TPKEShare}
import rep.network.consensus.asyncconsensus.protocol.msgcenter.{Broadcaster, SenderFactory}
import rep.network.consensus.asyncconsensus.protocol.provablereliablebroadcast.ProvableReliableBroadcastInActor
import rep.network.consensus.asyncconsensus.protocol.validatedagreement.ValidatedAgreement.VABA_Result
import rep.network.consensus.asyncconsensus.protocol.validatedagreement.ValidatedAgreementInActor
import rep.network.consensus.asyncconsensus.protocol.validatedcommonsubset.{ValidatedCommonSubetInActor, ValidatedCommonSubset}
import rep.network.consensus.asyncconsensus.protocol.validatedcommonsubset.ValidatedCommonSubset.{AddDataToVCS, VCSB_Result}
import rep.protos.peer.ResultOfPRBC

import scala.collection.immutable.HashMap

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-02-13
 * @category	采用响应式编程Actor实现dumbo出块协议的消息的发送和接收。
 */
object AsyncBlockInActor{
  def props( moduleName: String, nodeName:String, pathSchema:String,moduleSchema:String, numOfNode:Int, numOfFault:Int,  caller:ActorRef): Props =
    Props(classOf[AsyncBlockInActor],moduleName, nodeName, pathSchema,moduleSchema, numOfNode, numOfFault, caller)
}

class AsyncBlockInActor  (moduleName: String,  var nodeName:String, pathSchema:String,moduleSchema:String,var numOfNode:Int, var numOfFault:Int, var caller:ActorRef) extends ModuleBase(moduleName)  {
  println(s"create AsyncBlockInActor actor addr=${this.selfAddr}")


  val vcsb_actor = context.actorOf(ValidatedCommonSubetInActor.props(this.nodeName+"-vcsb", this.nodeName,this.pathSchema+"/"+moduleSchema,"{nodeName}"++"-vcsb",this.numOfNode,this.numOfFault,self), this.nodeName+"-vcsb")

  private val asyncBlock = new AsyncBlock(nodeName,pathSchema, moduleSchema, numOfNode, numOfFault,
    new Broadcaster(SenderFactory.getSenderOfScala(this.context.system),
      ConfigOfManager.getManager.getNodeNameAndAddr),caller,vcsb_actor)


  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"${this.selfAddr} Start"))
  }

  override def receive: Receive = {
    case start:StartBlock=>
      asyncBlock.StartBlockHandle(start)
    case vcsb_result:VCSB_Result=>
      asyncBlock.VCSB_Result_Handle(vcsb_result)
    case share_result:TPKEShare=>
      asyncBlock.TPKEShareHandle(share_result)
  }

}