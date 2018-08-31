package rep.api.rest


import akka.actor.ActorRef
import org.eclipse.californium.core.server.resources.CoapExchange
import org.eclipse.californium.core.{CoapResource, CoapServer}
import rep.api.rest.RestIot.tranSign
import rep.network.base.ModuleHelper
import rep.protos.peer.Transaction
import rep.utils.GlobalUtils.ActorType


object IotService {

    def main(args: Array[String]): Unit = {

    }
}

/**
  * @author zyf
  * @param IotRef
  */
class IotService (IotRef: ActorRef) {

  /**
    * 资源即访问路径，coap://172.0.0.1:5683/Transaction
    */
  private val coapResource: CoapResource = new CoapResource("Transaction") {
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
      iot ! tranSign
      exchange.respond("Test")  // 后续
      println(exchange.getRequestOptions)
    }
  }

  def getCoapResource : CoapResource  = coapResource
}
