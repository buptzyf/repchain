package rep.network.consensus.asyncconsensus.protocol.msgcenter

import akka.actor.{ActorRef, ActorSelection, ActorSystem}

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-03-13
 * @category	实现响应式的广播接口。
 */
class ImpMessageOfSenderInScala(ctx:ActorSystem) extends IMessageOfSender {
  override def sendMsg(addr:String,MsgBody: Any): Unit = {
    try{
      val selection: ActorSelection = ctx.actorSelection(addr);
      selection ! MsgBody
    }catch {
      case e:Exception =>
        e.printStackTrace()
    }
  }

  override def sendMsgToObject(recver: Any, MsgBody: Any): Unit = {
    if(recver.isInstanceOf[ActorRef]){
      recver.asInstanceOf[ActorRef] ! MsgBody
    }
  }
}
