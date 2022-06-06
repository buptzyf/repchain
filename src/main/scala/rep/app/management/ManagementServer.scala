package rep.app.management


import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.server.Directives.{get, getFromResourceDirectory, pathPrefix}
import akka.http.scaladsl.server.RouteConcatenation._
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.japi.Util.immutableSeq
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import rep.crypto.JsseContextHelper
import rep.log.RepLogger
import rep.proto.rc2.Event

object ManagementServer {
  def props(port: Int,ssl_mode: Int,useClientAuth:Boolean): Props = Props(classOf[ManagementServer], port,ssl_mode,useClientAuth:Boolean)

  /** 启动管理服务
   *
   * @param sys ActorSystem
   * @param
   */
  def start(sys: ActorSystem, port: Int,sslMode:Int,useClientAuth:Boolean) {
    implicit val system = sys
    implicit val executionContext = system.dispatcher

    val requestHandler = sys.actorOf(Props[ManagementActor], "ManagementActor")

    //提供静态文件的web访问服务
    val route_evt =
      //提供swagger UI服务
      (get & pathPrefix("swagger")) {
        getFromResourceDirectory("swagger")
      }

    sslMode match {
      case 0 =>
        //不使用ssl协议
        System.out.println(s"^^^^^^^^begin：http management Service:http://localhost:$port^^^^^^^^")
        Http().newServerAt("0.0.0.0", port)
          .bindFlow(
            route_evt
              ~ cors()(
              new ManagementService(requestHandler, false).route
                ~ ManagementSwagger.routes
            ))
        System.out.println(s"^^^^^^^^end：http management Service:http://localhost:$port^^^^^^^^")
        RepLogger.info(RepLogger.System_Logger, s"http management Service online at http://localhost:$port")
      case 1 =>
        //使用国际密码体系（java）
        System.out.println(s"^^^^^^^^begin ssl：https management Service:https://localhost:$port^^^^^^^^")
        val https = ConnectionContext.httpsServer(() => {
          val sslCtx = JsseContextHelper.createJsseContext(sys.settings.config)//EventServer.createJsseContext(repContext)
          val engine = sslCtx.createSSLEngine()
          engine.setUseClientMode(false)
          val cipherSuite = immutableSeq(sys.settings.config.getStringList("akka.remote.artery.ssl.config-ssl-engine.enabled-algorithms")).toSet
          engine.setEnabledCipherSuites(cipherSuite.toArray)
          engine.setEnabledProtocols(Array(
            sys.settings.config.getString("akka.remote.artery.ssl.config-ssl-engine.protocol")))
          engine.setNeedClientAuth(useClientAuth)
          //engine.setWantClientAuth(true)

          engine
        })
        Http().newServerAt("0.0.0.0", port)
          .enableHttps(https)
          .bindFlow(
            route_evt
              ~ cors()(
              new ManagementService(requestHandler, useClientAuth).route
                ~ ManagementSwagger.routes
            ))
        System.out.println(s"^^^^^^^^end ssl：https management Service:https://localhost:$port^^^^^^^^")
        RepLogger.info(RepLogger.System_Logger, s"https(ssl) management Service online at https://localhost:$port")
      case 2 =>
        //使用国家密码体系（china）
        System.out.println(s"^^^^^^^^begin gmssl：https management Service:https://localhost:$port^^^^^^^^")

        System.out.println(s"^^^^^^^^end gmssl：https management Service:https://localhost:$port^^^^^^^^")
        RepLogger.info(RepLogger.System_Logger, s"https(gmssl) management Service online at https://localhost:$port")
    }
  }
}

class ManagementServer(port: Int,sslMode:Int,useClientAuth:Boolean) extends Actor {
  override def preStart(): Unit = {
    ManagementServer.start(context.system, port,sslMode,useClientAuth)
  }

  def receive = {
    case Event =>
  }
}
