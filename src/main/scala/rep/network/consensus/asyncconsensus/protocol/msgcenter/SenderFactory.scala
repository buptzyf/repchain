package rep.network.consensus.asyncconsensus.protocol.msgcenter

import akka.actor.{ActorContext, ActorSystem}


/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-03-13
 * @category	建立广播的实现类。
 */
object SenderFactory {
  def getSenderOfScala(ctx:ActorSystem):IMessageOfSender={
    new ImpMessageOfSenderInScala(ctx)
  }
}
