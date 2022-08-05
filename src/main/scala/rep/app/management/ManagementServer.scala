package rep.app.management


import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.server.Directives.{get, getFromResourceDirectory, pathPrefix}
import akka.http.scaladsl.server.RouteConcatenation._
import akka.http.scaladsl.{ConnectionContext, Http}
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import rep.app.conf.RepChainConfig
import rep.crypto.nodedynamicmanagement.JsseContextHelper
import rep.crypto.nodedynamicmanagement4gm.GMJsseContextHelper
import rep.log.RepLogger
import rep.proto.rc2.Event

object ManagementServer {
  def props(conf:RepChainConfig): Props = Props(classOf[ManagementServer], conf)

  /** 启动管理服务
   *
   * @param sys ActorSystem
   * @param
   */
  def start(sys: ActorSystem, conf:RepChainConfig) {
    implicit val system = sys
    implicit val executionContext = system.dispatcher

    val requestHandler = sys.actorOf(Props[ManagementActor], "ManagementActor")

    //提供静态文件的web访问服务
    val route_evt =
      //提供swagger UI服务
      (get & pathPrefix("swagger")) {
        getFromResourceDirectory("swagger")
      }

    conf.isUseHttps match {
      case false =>
        //不使用ssl协议
        System.out.println(s"^^^^^^^^begin：http management Service:http://localhost:${conf.getHttpServicePort}^^^^^^^^")
        Http().newServerAt("0.0.0.0", conf.getHttpServicePort)
          .bindFlow(
            route_evt
              ~ cors()(
              new ManagementService(requestHandler, false).route
                ~ ManagementSwagger.routes
            ))
        System.out.println(s"^^^^^^^^end：http management Service:http://localhost:${conf.getHttpServicePort}^^^^^^^^")
        RepLogger.info(RepLogger.System_Logger, s"http management Service online at http://localhost:${conf.getHttpServicePort}")
      case true =>
        conf.isUseGM match{
          case false=>
            //使用国际密码体系（java）
            System.out.println(s"^^^^^^^^begin ssl：https management Service:https://localhost:${conf.getHttpServicePort}^^^^^^^^")
            val https = ConnectionContext.httpsServer(() => {
              val sslCtx = JsseContextHelper.createJsseContext(conf,"TLSv1.2")
              val engine = sslCtx.createSSLEngine()
              engine.setUseClientMode(false)
              engine.setEnabledCipherSuites(conf.getAlgorithm.toArray)
              engine.setEnabledProtocols(Array(conf.getProtocol))
              engine.setNeedClientAuth(conf.isNeedClientAuth)
              engine
            })
            Http().newServerAt("0.0.0.0", conf.getHttpServicePort)
            .enableHttps(https)
            .bindFlow(
            route_evt
            ~ cors()(
            new ManagementService(requestHandler, conf.isNeedClientAuth).route
            ~ ManagementSwagger.routes
            ))
            System.out.println(s"^^^^^^^^end ssl：https management Service:https://localhost:${conf.getHttpServicePort}^^^^^^^^")
            RepLogger.info(RepLogger.System_Logger, s"https(ssl) management Service online at https://localhost:${conf.getHttpServicePort}")
          case true =>
            //使用国家密码体系（china）
            System.out.println(s"^^^^^^^^begin gmssl：https management Service:https://localhost:${conf.getHttpServicePort}^^^^^^^^")

            val https = ConnectionContext.httpsServer(() => {
              val sslCtx = GMJsseContextHelper.createGMContext(conf,true,conf.getSystemName)
              val engine = sslCtx.createSSLEngine()
              engine.setUseClientMode(false)
              engine.setEnabledCipherSuites(conf.getAlgorithm.toArray)
              engine.setEnabledProtocols(Array(conf.getProtocol))
              engine.setNeedClientAuth(conf.isNeedClientAuth)
              engine
            })
            Http().newServerAt("0.0.0.0", conf.getHttpServicePort)
              .enableHttps(https)
              .bindFlow(
                route_evt
                  ~ cors()(
                  new ManagementService(requestHandler, conf.isNeedClientAuth).route
                    ~ ManagementSwagger.routes
                ))
            System.out.println(s"^^^^^^^^end gmssl：https management Service:https://localhost:${conf.getHttpServicePort}^^^^^^^^")
            RepLogger.info(RepLogger.System_Logger, s"https(gmssl) management Service online at https://localhost:${conf.getHttpServicePort}")
        }
    }
  }
}

class ManagementServer(conf:RepChainConfig) extends Actor {
  override def preStart(): Unit = {
    ManagementServer.start(context.system, conf)
  }

  def receive = {
    case Event =>
  }
}
