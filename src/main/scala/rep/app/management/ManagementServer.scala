package rep.app.management


import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.{Http}
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import rep.log.{RepLogger}
import rep.proto.rc2.Event

object ManagementServer {
  def props(port: Int): Props = Props(classOf[ManagementServer], port)

  /** 启动管理服务
   *
   * @param sys ActorSystem
   * @param
   */
  def start(sys: ActorSystem, port: Int) {
    implicit val system = sys
    implicit val executionContext = system.dispatcher

    val requestHandler = sys.actorOf(Props[ManagementActor], "ManagementActor")
    Http().newServerAt("0.0.0.0", port)
      .bindFlow(cors()(
        new ManagementService(requestHandler).route
      ))
    System.out.println(s"^^^^^^^^http management Service:^^^^^^^^")

    RepLogger.info(RepLogger.System_Logger, s"http management Service online at http://localhost:$port")
  }
}

class ManagementServer(port: Int) extends Actor {
  override def preStart(): Unit = {
    ManagementServer.start(context.system, port)
  }

  def receive = {
    case Event =>
  }
}
