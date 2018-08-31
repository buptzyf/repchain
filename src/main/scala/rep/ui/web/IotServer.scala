package rep.ui.web


import java.net.InetSocketAddress

import akka.actor.{Actor, ActorSystem, Props}
import org.eclipse.californium.core.CoapServer
import org.eclipse.californium.core.network.CoapEndpoint
import rep.api.rest.{IotService, RestIot}
import rep.app.conf.SystemProfile


/**
  * @author zyf
  */
object IotServer {

  /**
    * 初始化coapServer资源
    * @param sys
    * @param port
    */
  def start(sys: ActorSystem, hostname: String, port: Int): Unit = {
    implicit val system =sys
    val Iot = sys.actorOf(Props[RestIot], "coap")
    val server = new CoapServer
    val builder = new CoapEndpoint.CoapEndpointBuilder
    builder.setInetSocketAddress(new InetSocketAddress(hostname, 5683))
    server.addEndpoint(builder.build)
    server.add(new IotService(Iot).getCoapResource)
    server.start()
    System.out.println("Coap Server online at coap://" + hostname + ":" + port)
  }
//    try {
      // windows下测试一下单机四个节点，就先这样写吧
//      val process = Runtime.getRuntime.exec("cmd /c netstat -ano | findstr \"" + 5683 + "\"")
//      val reader = new BufferedReader(new InputStreamReader(process.getInputStream, "UTF-8"))
//      if (reader.readLine == null) {
//      }
////      reader.close()
//    } catch {
//      case e: IOException =>
//        e.printStackTrace()
//    }
}

/**
  * @author zyf
  */
class IotServer extends Actor {
  override def preStart(): Unit = {
    IotServer.start(context.system, SystemProfile.getCoapServiceHost, SystemProfile.getCoapServicePort)
  }

  def receive = {
    case _ =>
  }
}
