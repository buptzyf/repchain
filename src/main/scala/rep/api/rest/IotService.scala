package rep.api.rest


import akka.actor.ActorRef
import akka.util.Timeout
import org.eclipse.californium.core.server.resources.CoapExchange
import org.eclipse.californium.core.CoapResource
import rep.api.rest.RestIot.{tranResult, tranSign}

import scala.concurrent.duration._
import scala.concurrent.Await
import akka.pattern.ask


object IotService {

    def main(args: Array[String]): Unit = {

    }
}

/**
  * @author zyf
  * @param IotRef
  */
class IotService (IotRef: ActorRef) {

  implicit val timeout = Timeout(3000.seconds)
  /**
    * 资源即访问路径，coap://172.0.0.1:5683/Transaction
    */
  private val tranResource: CoapResource = new CoapResource("Transaction") {
    implicit val iot = IotRef

    /**
      * 接受get请求
      * @param exchange
      */
    override def handleGET(exchange: CoapExchange): Unit = {
      exchange.respond("hello world")
    }

    /**
      * 接受post请求
      * @param exchange
      */
    override def handlePOST(exchange: CoapExchange): Unit = {
      val payload = exchange.getRequestPayload
      val tranSign = new tranSign(payload)
      val future = iot ? tranSign
      val result = Await.result(future, timeout.duration).asInstanceOf[tranResult]
      result.err match {
        case None =>
          exchange.respond(result.toString)
        case Some(e) =>
          //预执行异常,废弃交易，向api调用者发送异常
          exchange.respond(result.err.toString)
      }
//      exchange.respond("Test")  // 后续
      println(exchange.getRequestOptions)
    }
  }

  def getTranResource : CoapResource  = tranResource
}
