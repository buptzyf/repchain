package rep.network.consensus.asyncconsensus.protocol.consistentbroadcast

import akka.actor.{ActorRef, Props}
import rep.log.RepLogger
import rep.network.base.ModuleBase
import rep.network.consensus.asyncconsensus.config.ConfigOfManager
import rep.network.consensus.asyncconsensus.protocol.consistentbroadcast.ConsistentBroadcast.{CBC_ECHO, CBC_FINAL, CBC_SEND, StartConsistenBroadcast}
import rep.network.consensus.asyncconsensus.protocol.msgcenter.{Broadcaster, SenderFactory}

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-02-13
 * @category	采用响应式编程Actor实现一致性广播协议的消息的发送和接收。
 */
object ConsistentBroadcastInActor{
  def props( moduleName: String, nodeName:String,pathSchema:String,moduleSchema:String,  numOfNode:Int, numOfFault:Int,  caller:ActorRef): Props =
    Props(classOf[ConsistentBroadcastInActor],moduleName, nodeName,pathSchema,moduleSchema,  numOfNode, numOfFault, caller)
}

class ConsistentBroadcastInActor(moduleName: String,  var nodeName:String,pathSchema:String,moduleSchema:String,  var numOfNode:Int, var numOfFault:Int, var caller:ActorRef) extends ModuleBase(moduleName)  {
  println(s"create ConsistentBroadcastInActor,addr=${this.selfAddr}")
  println(s"create ConsistentBroadcastInActor actor nodename=${this.nodeName},processname=${this.moduleName}")

  private val consistentBroadcast = new ConsistentBroadcast(nodeName,pathSchema, moduleSchema, numOfNode, numOfFault,
    new Broadcaster(SenderFactory.getSenderOfScala(this.context.system),
      ConfigOfManager.getManager.getNodeNameAndAddr),caller)

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"${this.selfAddr} Start"))
  }

  override def receive: Receive = {
    case start:StartConsistenBroadcast=>
      consistentBroadcast.Start_Handle(start)
    case cbcSend:CBC_SEND=>
      consistentBroadcast.CBC_SEND_Handle(cbcSend)
    case cbcEcho:CBC_ECHO=>
      consistentBroadcast.CBC_ECHO_Handle(cbcEcho)
    case cbcFinal:CBC_FINAL=>
      consistentBroadcast.CBC_FINAL_Handle(cbcFinal)
  }

}
