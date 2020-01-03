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

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
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
import rep.protos.peer._
import akka.util.ByteString
import rep.api.SwaggerDocService
import rep.api.rest._
import rep.utils.GlobalUtils
import rep.sc.Sandbox.SandboxException
import rep.log.RepLogger
import rep.app.conf.SystemProfile

import rep.log.RecvEventActor
import rep.log.EventActor4Stage
import akka.stream.Graph
import akka.stream.SourceShape
import akka.NotUsed

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
 *  @param port 指定侦听的端口
 */
  def start(sys:ActorSystem ,port:Int) {
    implicit val system =sys
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher
    
    val evtactor = system.actorOf(Props[RecvEventActor],"RecvEventActor")
    

    
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
          //must ref to the same actor
         //val source = Source.actorPublisher[Event](Props[EventActor]).map(evt =>  BinaryMessage(ByteString(evt.toByteArray))) 
         //val sourceGraph: Graph[SourceShape[Event], NotUsed] = new EventActor4Stage(system)
          val sourceGraph: Graph[SourceShape[Event], NotUsed] = new EventActor4Stage(evtactor)
          val source: Source[Event, NotUsed] = Source.fromGraph(sourceGraph)
          
          
          extractUpgradeToWebSocket { upgrade =>
            complete(upgrade.handleMessagesWithSinkSource(Sink.ignore, source.map(evt => BinaryMessage(ByteString(evt.toByteArray)))))
          }
         /*extractUpgradeToWebSocket { upgrade =>
            complete(upgrade.handleMessagesWithSinkSource(Sink.ignore, source))
          }*/
        }
      }

    val ra = sys.actorOf(RestActor.props("api"), "api")
    //允许跨域访问,以支持在应用中发起请求
    Http().bindAndHandle(
      route_evt
        ~ cors() (
            new BlockService(ra).route ~
            new ChainService(ra).route ~
            new TransactionService(ra).route ~
            SwaggerDocService.routes),
      "0.0.0.0", port)
    RepLogger.info(RepLogger.System_Logger, s"Event Server online at http://localhost:$port")
  }
}

/** Event服务类，提供web实时图、swagger-UI、ws的Event订阅服务
 * @author c4w
 */
class EventServer extends Actor{
  override def preStart(): Unit =EventServer.start(context.system, SystemProfile.getHttpServicePort)

  def receive = {
    case Event =>
  }
}