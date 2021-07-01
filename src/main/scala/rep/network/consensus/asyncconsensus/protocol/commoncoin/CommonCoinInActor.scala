package rep.network.consensus.asyncconsensus.protocol.commoncoin

import akka.actor.{ActorRef, Props}
import rep.log.RepLogger
import rep.network.base.ModuleBase
import rep.network.consensus.asyncconsensus.config.ConfigOfManager
import rep.network.consensus.asyncconsensus.protocol.msgcenter.{Broadcaster, SenderFactory}
import rep.protos.peer.{ShareSignOfCommonCoin, StartCommonCoin}


/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-02-13
 * @category	采用响应式编程Actor实现分布式掷币协议的消息的发送和接收。
 */

object CommonCoinInActor {
  def props(name: String, nodeName:String,pathSchema:String,moduleSchema:String,singleBit:Boolean, owner:ActorRef): Props = Props(classOf[CommonCoinInActor], name, nodeName,pathSchema,moduleSchema,singleBit,owner)
}

class CommonCoinInActor(moduleName: String, nodeName:String,pathSchema:String,moduleSchema:String,singleBit:Boolean,owner:ActorRef) extends ModuleBase(moduleName) {

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"${this.selfAddr} Start"))
  }




  //private val actorPath = Broadcaster.getModulePath(self)
  //private val splitModuleName = Broadcaster.splitModuleName(this.moduleName)

  //private val commonCoin = new CommonCoin(nodeName,actorPath,splitModuleName,
  //                              new Broadcaster(SenderFactory.getSenderOfScala(this.context.system),
  //                                ConfigOfManager.getManager.getNodeNameAndAddr),owner)//,this.context.parent.asInstanceOf[ActorRef])
  private val commonCoin = new CommonCoin(nodeName,pathSchema,moduleSchema,
    new Broadcaster(SenderFactory.getSenderOfScala(this.context.system),
      ConfigOfManager.getManager.getNodeNameAndAddr),this.singleBit,owner)//,this.context.parent.asInstanceOf[ActorRef])

  override def receive: Receive = {
    case startMsg: StartCommonCoin =>
      if(startMsg.round == "") {
        println("#StartCommonCoin#################---"+sender().path.toString)
      }
        this.commonCoin.startup(startMsg)

    case shareMsg: ShareSignOfCommonCoin =>
      if(shareMsg.round == "") {
        println("#ShareSignOfCommonCoin#################---"+sender().path.toString)
      }
                          this.commonCoin.handler(shareMsg)
  }

}
