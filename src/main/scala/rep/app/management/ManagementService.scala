package rep.app.management


import scala.concurrent.ExecutionContext
import akka.actor.ActorRef
import akka.util.Timeout
import javax.ws.rs._
import javax.ws.rs.Path
import org.json4s.{DefaultFormats, jackson}
import akka.http.scaladsl.server.Directives

@Path("/management")
class ManagementService(handler: ActorRef)(implicit executionContext: ExecutionContext)
  extends Directives {

  import scala.concurrent.duration._
  import akka.pattern.{ask}
  import rep.app.management.ManagementActor.{SystemStatusQuery, SystemStart, SystemStop}

  implicit val serialization = jackson.Serialization
  implicit val formats = DefaultFormats
  implicit val timeout = Timeout(20.seconds)


  val route = SystemStartup ~ QuerySystemStatus ~ SystemShutdown

  @GET
  @Path("/SystemStartup/{nodeName}")
  def SystemStartup =
    path("management" / "system" / "SystemStartup" / Segment) { nodeName =>
      get {
        withRequestTimeout(300.seconds) {
          rejectEmptyResponse {
            onSuccess((handler ? SystemStart(nodeName))) { response =>
              complete(response.toString)
            }
          }
        }
      }
    }

  @GET
  @Path("/SystemStatus/{nodeName}")
  def QuerySystemStatus =
    path("management" / "system" / "SystemStatus" / Segment) { nodeName =>
      get {
        withRequestTimeout(300.seconds) {
          rejectEmptyResponse {
            onSuccess((handler ? SystemStatusQuery(nodeName))) { response =>
              complete(response.toString)
            }
          }
        }
      }
    }

  @GET
  @Path("/SystemStop/{nodeName}")
  def SystemShutdown =
    path("management" / "system" / "SystemStop" / Segment) { nodeName =>
      get {
        withRequestTimeout(300.seconds) {
          rejectEmptyResponse {
            onSuccess((handler ? SystemStop(nodeName))) { response =>
              complete(response.toString)
            }
          }
        }
      }
    }

}
