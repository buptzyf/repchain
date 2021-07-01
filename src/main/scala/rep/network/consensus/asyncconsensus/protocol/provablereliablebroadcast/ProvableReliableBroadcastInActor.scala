package rep.network.consensus.asyncconsensus.protocol.provablereliablebroadcast

import akka.actor.{ActorRef, Props}
import rep.log.RepLogger
import rep.network.base.ModuleBase
import rep.network.consensus.asyncconsensus.config.ConfigOfManager
import rep.network.consensus.asyncconsensus.protocol.msgcenter.{Broadcaster, IMessageOfSender, SenderFactory}
import rep.protos.peer.{DataOfPRBC, ReadyOfPRBC, StartOfPRBC}


/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-02-13
 * @category	采用响应式编程Actor包装可证明的可靠性广播协议。
 */
object ProvableReliableBroadcastInActor {
  def props( moduleName: String, nodeName:String,pathSchema:String,moduleSchema:String,  numOfNode:Int, numOfFault:Int,  caller:ActorRef): Props =
    Props(classOf[ProvableReliableBroadcastInActor],moduleName, nodeName,pathSchema,moduleSchema,  numOfNode, numOfFault, caller)

}

class ProvableReliableBroadcastInActor(moduleName: String,  var nodeName:String,pathSchema:String,moduleSchema:String,  var numOfNode:Int, var numOfFault:Int, var caller:ActorRef) extends ModuleBase(moduleName)  {


  //private val actorPath = Broadcaster.getModulePath(self)
  //private val splitModuleName = Broadcaster.splitModuleName(this.moduleName)
  println(s"create ProvableReliableBroadcastInActor,addr=${this.selfAddr}")
  println(s"create ProvableReliableBroadcast actor nodename=${this.nodeName},processname=${this.moduleName}")
  //private val provableReliableBroadcast = new ProvableReliableBroadcast(nodeName,actorPath, splitModuleName, numOfNode, numOfFault,
  //                                                                      new Broadcaster(SenderFactory.getSenderOfScala(this.context.system),
  //                                                                      ConfigOfManager.getManager.getNodeNameAndAddr),caller)
  private val provableReliableBroadcast = new ProvableReliableBroadcast(nodeName,pathSchema, moduleSchema, numOfNode, numOfFault,
    new Broadcaster(SenderFactory.getSenderOfScala(this.context.system),
      ConfigOfManager.getManager.getNodeNameAndAddr),caller)

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"${this.selfAddr} Start"))
  }

  override def receive: Receive = {
    case start:StartOfPRBC=>
      provableReliableBroadcast.StartHandler(start)
    case data:DataOfPRBC=>
      provableReliableBroadcast.recvHandler(data)
    case ready:ReadyOfPRBC=>
      provableReliableBroadcast.HandlerOfReady(ready)
  }

}
