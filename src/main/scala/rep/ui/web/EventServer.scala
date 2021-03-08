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
import rep.crypto.JsseContextHelper
import rep.proto.rc2.Event
import akka.japi.Util._
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

 /* def createJsseContext(repContext: RepChainSystemContext):SSLContext={
    val config = repContext.getConfig.getSystemConf
    val prefix = "akka.remote.artery.ssl.config-ssl-engine."
    val SSLKeyStore: String = config.getString(prefix+"key-store")
    val SSLTrustStore: String = config.getString(prefix+"trust-store")
    val SSLKeyStorePassword: String = config.getString(prefix+"key-store-password")
    val SSLKeyPassword: String = config.getString(prefix+"key-password")
    val SSLTrustStorePassword: String = config.getString(prefix+"trust-store-password")
    //val SSLEnabledAlgorithms: Set[String] = immutableSeq(config.getStringList(prefix+"enabled-algorithms")).toSet
    val SSLProtocol: String = config.getString(prefix+"protocol")
    val SSLRandomNumberGenerator: String = config.getString(prefix+"random-number-generator")
    //val SSLRequireMutualAuthentication: Boolean = config.getBoolean(prefix+"require-mutual-authentication")
    //val HostnameVerification: Boolean = config.getBoolean(prefix+"hostname-verification")

    try {
      val rng = createSecureRandom(SSLRandomNumberGenerator)
      val ctx = SSLContext.getInstance(SSLProtocol)
      ctx.init(keyManagers(SSLKeyStore, SSLKeyStorePassword,SSLKeyPassword),
        trustManagers(SSLTrustStore, SSLTrustStorePassword), rng)
      ctx
    } catch {
      case e: Exception => null
    }
  }

  private def trustManagers(SSLTrustStore:String, SSLTrustStorePassword:String): Array[TrustManager] = {
    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    trustManagerFactory.init(loadKeystore(SSLTrustStore, SSLTrustStorePassword))
    trustManagerFactory.getTrustManagers
  }

  private def keyManagers(SSLKeyStore: String, SSLKeyStorePassword: String,SSLKeyPassword:String): Array[KeyManager] = {
    val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    factory.init(loadKeystore(SSLKeyStore, SSLKeyStorePassword), SSLKeyPassword.toCharArray)
    factory.getKeyManagers
  }

  private def loadKeystore(filename: String, password: String): KeyStore = {
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
    val fin = Files.newInputStream(Paths.get(filename))
    try keyStore.load(fin, password.toCharArray)
    finally Try(fin.close())
    keyStore
  }

  private def createSecureRandom(randomNumberGenerator: String): SecureRandom = {
    val rng = randomNumberGenerator match {
      case s @ ("SHA1PRNG" | "NativePRNG") =>
        SecureRandom.getInstance(s)
      case "" | "SecureRandom" =>
        new SecureRandom
      case unknown =>
        new SecureRandom
    }
    rng.nextInt()
    rng
  }*/

/** 启动Event服务
 * 传入publish Actor
 * 必须确保ActorSystem 与其他actor处于同一system，context.actorSelection方可正常工作
 *  @param sys ActorSystem
 *  @param repContext RepChainSystemContext
 */
  def start(sys:ActorSystem ,repContext: RepChainSystemContext) {
    implicit val system =sys
    implicit val materializer = ActorMaterializer()
    //implicit val executionContext = system.dispatcher

    implicit val executionContext = system.dispatchers.lookup("http-dispatcher")
    
    val evtactor = system.actorOf(Props[RecvEventActor].withDispatcher("http-dispatcher"),"RecvEventActor")

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
      }~ //提供Event的WebSocket订阅服务
      path("event") {
        get {
          val sourceGraph: Graph[SourceShape[Event], NotUsed] = new EventActor4Stage(evtactor)
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

    //val ra = sys.actorOf(RestActor.props("api"), "api")
    val ra = new RestRouter(actorNumber,sys)

    //允许跨域访问,以支持在应用中发起请求
    //val httpServer = Http()
    System.out.println("^^^^^^^^^^^^^^^^")
    val pe = PeerExtension(sys)
    if(pe.getSSLContext == null && pe.getRepChainContext.getConfig.isUseGM){
      Thread.sleep(2000)
    }

    repContext.getConfig.isUseHttps match{
      case true=>
        var https : HttpsConnectionContext = null
        repContext.getConfig.isUseGM match {
          case true=>
            https = ConnectionContext.httpsServer(() => {
              //val engine = repContext.getSSLContext.getSSLcontext.createSSLEngine()
              val engine = pe.getSSLContext.createSSLEngine()
              engine.setUseClientMode(false)
              engine.setEnabledCipherSuites(Array("GMSSL_ECC_SM4_SM3"))
              engine.setEnabledProtocols(Array("GMSSLv1.1"))
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
                  new DidService(ra).route ~
                  SwaggerDocService.routes))
            System.out.println(s"^^^^^^^^https GM Service:${repContext.getSystemName}^^^^^^^^")
          case false=>
            https = ConnectionContext.httpsServer(() => {
              val sslCtx = JsseContextHelper.createJsseContext(repContext.getConfig.getSystemConf)//EventServer.createJsseContext(repContext)
              val engine = sslCtx.createSSLEngine()
              engine.setUseClientMode(false)
              val cipherSuite = immutableSeq(repContext.getConfig.getSystemConf.getStringList("akka.remote.artery.ssl.config-ssl-engine.enabled-algorithms")).toSet
              engine.setEnabledCipherSuites(cipherSuite.toArray)
              engine.setEnabledProtocols(Array(
                repContext.getConfig.getSystemConf.getString("akka.remote.artery.ssl.config-ssl-engine.protocol")))
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
    if(pe.getSSLContext == null && pe.getRepChainContext.getConfig.isUseGM){
      Thread.sleep(2000)
    }
    EventServer.start(context.system, pe.getRepChainContext)
}

def receive = {
  case Event =>
}
}