/*
 * Copyright  2019 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BA SIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package rep.ui.web

import java.nio.file.{Files, Paths}
import java.security.{KeyStore, SecureRandom}

import akka.actor.ActorSystem
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.{Flow, Source}
import akka.actor._
import akka.http.scaladsl.model.HttpEntity.Strict
import akka.http.javadsl.model.ws._
import akka.stream.scaladsl.Sink
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import StatusCodes._
import Directives._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import akka.util.ByteString
import rep.api.SwaggerDocService
import rep.api.rest._
import rep.sc.Sandbox.SandboxException
import rep.log.RepLogger
import rep.log.RecvEventActor
import rep.log.EventActor4Stage
import akka.stream.Graph
import akka.stream.SourceShape
import akka.NotUsed
import rep.network.tools.PeerExtension
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.japi.Util.immutableSeq
import javax.net.ssl.{KeyManager, KeyManagerFactory, SSLContext, TrustManager, TrustManagerFactory}
import rep.app.system.RepChainSystemContext
import rep.proto.rc2.Event
import akka.japi.Util._
import rep.crypto.nodedynamicmanagement.JsseContextHelper
import rep.crypto.nodedynamicmanagement4gm.GMJsseContextHelper

import scala.util.Try

/** Event服务伴生对象
 *  @author c4w
 */
object EventServer {
//def props(name: String): Props = Props(classOf[EventServer], name)
  
  implicit def myExceptionHandler = ExceptionHandler {
    //合约执行异常，回送HTTP 200，包容chrome的跨域尝试
    case e: SandboxException =>
      extractUri { uri =>
        complete(HttpResponse(Accepted,
          entity = HttpEntity(ContentTypes.`application/json`,
            s"""{"err": "${e.getMessage}"}"""))
        )
      }
  }


/** 启动Event服务
 * 传入publish Actor
 * 必须确保ActorSystem 与其他actor处于同一system，context.actorSelection方可正常工作
 *  @param sys ActorSystem
 *  @param repContext RepChainSystemContext
 */
  def start(sys:ActorSystem ,repContext: RepChainSystemContext) {
    implicit val system =sys
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val evtActor = system.actorOf(Props[RecvEventActor],"RecvEventActor")

    val port = repContext.getConfig.getHttpServicePort
    val actorNumber = repContext.getConfig.getHttpServiceActorNumber
    
    //提供静态文件的web访问服务
    val route_evt =
      //提供swagger UI服务
     (get & pathPrefix("swagger")) {
        getFromResourceDirectory("swagger")
      }~ //提供静态文件的web访问服务
     (get & pathPrefix("web")) {
        getFromResourceDirectory("web")
      }~
     (get & pathPrefix("")) {
       pathEndOrSingleSlash {
         getFromResource("web/index.html")
       }
      }~
    //提供Event的WebSocket订阅服务
      path("event") {
        get {
          val sourceGraph: Graph[SourceShape[Event], NotUsed] = new EventActor4Stage(evtActor)
          val source: Source[Event, NotUsed] = Source.fromGraph(sourceGraph)

          extractWebSocketUpgrade
          //extractUpgradeToWebSocket
          { upgrade =>
            complete(upgrade.handleMessagesWithSinkSource(Sink.ignore, source.map(evt => BinaryMessage(ByteString(evt.toByteArray)))))
          }
         //extractUpgradeToWebSocket { upgrade =>
         //   complete(upgrade.handleMessagesWithSinkSource(Sink.ignore, source))
         // }
        }
      }

    val ra = sys.actorOf(RestActor.props("api"), "api")

    //允许跨域访问,以支持在应用中发起请求
    //val httpServer = Http()
    System.out.println("^^^^^^^^^^^^^^^^")


    repContext.getConfig.isUseHttps match{
      case true=>
        var https : HttpsConnectionContext = null
        repContext.getConfig.isUseGM match {
          case true=>
            https = ConnectionContext.httpsServer(() => {
              val sslCtx = GMJsseContextHelper.createGMContext(repContext.getConfig,true,repContext.getConfig.getSystemName)
              val engine = sslCtx.createSSLEngine()
              engine.setUseClientMode(false)
              engine.setEnabledCipherSuites(repContext.getConfig.getAlgorithm.toArray)
              engine.setEnabledProtocols(Array(repContext.getConfig.getProtocol))
              engine.setNeedClientAuth(repContext.getConfig.isNeedClientAuth)
              engine
            })
            Http().newServerAt("0.0.0.0", port)
              .enableHttps(https)
              .bindFlow(route_evt
                ~ cors() (
                new BlockService(ra,repContext,repContext.getConfig.isNeedClientAuth).route ~
                  new ChainService(ra).route ~
                  new TransactionService(ra,repContext,repContext.getConfig.isNeedClientAuth).route ~
                  new DbService(ra,repContext,repContext.getConfig.isNeedClientAuth).route ~
                  new NodeService(ra).route ~
                  SwaggerDocService.routes))
            System.out.println(s"^^^^^^^^https GM Service:${repContext.getSystemName}^^^^^^^^")
          case false=>
            https = ConnectionContext.httpsServer(() => {
              val sslCtx = JsseContextHelper.createJsseContext(repContext.getConfig,"TLSv1.2")
              val engine = sslCtx.createSSLEngine()
              engine.setUseClientMode(false)
              engine.setEnabledCipherSuites(repContext.getConfig.getAlgorithm.toArray)
              engine.setEnabledProtocols(Array(repContext.getConfig.getProtocol))
              engine.setNeedClientAuth(repContext.getConfig.isNeedClientAuth)
              engine
            })
            Http().newServerAt("0.0.0.0", port)
              .enableHttps(https)
              .bindFlow(route_evt
                ~ cors() (
                new BlockService(ra,repContext,repContext.getConfig.isNeedClientAuth).route ~
                  new ChainService(ra).route ~
                  new TransactionService(ra,repContext,repContext.getConfig.isNeedClientAuth).route ~
                  new DbService(ra,repContext,repContext.getConfig.isNeedClientAuth).route ~
                  new NodeService(ra).route ~
                  SwaggerDocService.routes))
            System.out.println(s"^^^^^^^^https TLS Service:${repContext.getSystemName}^^^^^^^^")
        }
      case false=>
        Http().newServerAt("0.0.0.0", port)
          // .enableHttps(https)
          .bindFlow(route_evt
            ~ cors() (
            new BlockService(ra,repContext,false).route ~
              new ChainService(ra).route ~
              new TransactionService(ra,repContext,false).route ~
              new DbService(ra,repContext,false).route ~
              new NodeService(ra).route ~
              SwaggerDocService.routes
          ))
        System.out.println(s"^^^^^^^^http Service:${repContext.getSystemName}^^^^^^^^")
    }


    RepLogger.info(RepLogger.System_Logger, s"Event Server online at http://localhost:$port")
  }
}

/** Event服务类，提供web实时图、swagger-UI、ws的Event订阅服务
 * @author c4w
 */
class EventServer extends Actor{
  override def preStart(): Unit = {
    context.system.settings.config
    val pe = PeerExtension(context.system)
    EventServer.start(context.system, pe.getRepChainContext)
}

def receive = {
  case Event =>
}
}