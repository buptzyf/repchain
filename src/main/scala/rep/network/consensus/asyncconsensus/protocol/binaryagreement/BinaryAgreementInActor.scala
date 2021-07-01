package rep.network.consensus.asyncconsensus.protocol.binaryagreement

import akka.actor.{ActorRef, Props}
import rep.log.RepLogger
import rep.network.base.ModuleBase
import rep.network.consensus.asyncconsensus.config.ConfigOfManager
import rep.network.consensus.asyncconsensus.protocol.binaryagreement.BinaryAgreement.{AUX, CONF, EST, RepeatBinaryAgreement, StartBinaryAgreement}
import rep.network.consensus.asyncconsensus.protocol.commoncoin.CommonCoinInActor
import rep.network.consensus.asyncconsensus.protocol.msgcenter.{Broadcaster, SenderFactory}
import rep.protos.peer.{ ResultOfCommonCoin}

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-02-13
 * @category	采用响应式编程Actor实现二进制共识协议的消息的发送和接收。
 */
object BinaryAgreementInActor{
  def props( moduleName: String, nodeName:String, pathSchema:String,moduleSchema:String, numOfNode:Int, numOfFault:Int,  caller:ActorRef): Props =
    Props(classOf[BinaryAgreementInActor],moduleName, nodeName, pathSchema,moduleSchema, numOfNode, numOfFault, caller)

}

class BinaryAgreementInActor (moduleName: String,  var nodeName:String, pathSchema:String,moduleSchema:String,var numOfNode:Int, var numOfFault:Int, var caller:ActorRef) extends ModuleBase(moduleName)  {


  //private val actorPath = Broadcaster.getModulePath(self)
  //private val splitModuleName = Broadcaster.splitModuleName(this.moduleName)
  println(s"create BinaryAgreementInActor actor addr=${this.selfAddr}")

  val coin = context.actorOf(CommonCoinInActor.props(nodeName+"-CommonCoin", nodeName,this.pathSchema+"/"+moduleSchema,"{nodeName}"+"-CommonCoin",true,self), nodeName+"-CommonCoin")

  //private val binaryAgree = new BinaryAgreement(nodeName,actorPath, splitModuleName, numOfNode, numOfFault,
  //  new Broadcaster(SenderFactory.getSenderOfScala(this.context.system),
  //    ConfigOfManager.getManager.getNodeNameAndAddr),self,coin,caller)

  private val binaryAgree = new BinaryAgreement(nodeName,pathSchema, moduleSchema, numOfNode, numOfFault,
    new Broadcaster(SenderFactory.getSenderOfScala(this.context.system),
      ConfigOfManager.getManager.getNodeNameAndAddr),self,coin,caller)

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"${this.selfAddr} Start"))
  }

  override def receive: Receive = {
    case start:StartBinaryAgreement=>
      binaryAgree.StartHandle(start)
    case repeat:RepeatBinaryAgreement=>
      binaryAgree.RepeatHandle(repeat)
    case est:EST=>
      binaryAgree.EstHandle(est)
    case aux:AUX=>
      binaryAgree.AuxHandle(aux)
    case conf:CONF=>
      binaryAgree.ConfHandle(conf)
    case coinMsg:ResultOfCommonCoin=>
      binaryAgree.ResultOfCommonCoinHandle(coinMsg)
  }

}