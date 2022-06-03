package rep.app.management


import scala.concurrent.ExecutionContext
import akka.actor.ActorRef
import akka.pattern.{AskTimeoutException, ask}
import akka.http.scaladsl.server.Directives
import Directives.{complete, onSuccess, rejectEmptyResponse, _}
import akka.util.Timeout
import javax.ws.rs._
import javax.ws.rs.Path
import org.json4s.{DefaultFormats, jackson}
import akka.http.scaladsl.server.Directives
import rep.log.RepLogger

@Path("/management")
class ManagementService(handler:ActorRef)(implicit executionContext: ExecutionContext)
  extends Directives {
  import scala.concurrent.duration._
  import akka.pattern.{AskTimeoutException, ask}
  import rep.app.management.ManagementActor.{SystemStatusQuery,SystemStart,SystemStop,QueryResult}

  implicit val serialization = jackson.Serialization
  implicit val formats = DefaultFormats
  implicit val timeout = Timeout(20.seconds)



  val route = SystemStartup ~ QuerySystemStatus ~ SystemShutdown

  @GET
  @Path("/SystemStartup/{nodeName}")
  def SystemStartup =
    path("management" / "system"/ "SystemStartup" / Segment) { nodeName =>
      get {

        //extractClientIP { ip =>
        //  RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} System startup,nodeName=${nodeName}")
          //complete { (handler ? rep.app.management.ManagementActor.SystemStart(nodeName)).mapTo[rep.app.management.ManagementActor.QueryResult] }
        //}
        rejectEmptyResponse {
          onSuccess((handler ? SystemStart(nodeName))) { response =>
            complete(response.toString)
          }
        }
        //complete { (ra ? BlockHeight(blockHeight.toInt)).mapTo[QueryResult] }
      }
    }

  @GET
  @Path("/SystemStatus/{nodeName}")
  def QuerySystemStatus =
    path("management" / "system"/  "SystemStatus" / Segment) { nodeName =>
      get {
        //extractClientIP { ip =>
        //  RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} System startup,nodeName=${nodeName}")
          //complete { (handler ? rep.app.management.ManagementActor.SystemStatus(nodeName)).mapTo[rep.app.management.ManagementActor.QueryResult] }
        //}
        rejectEmptyResponse {
          onSuccess((handler ? SystemStatusQuery(nodeName))) { response =>
            complete(response.toString)
          }
        }
        //complete { (ra ? BlockHeight(blockHeight.toInt)).mapTo[QueryResult] }
      }
    }

  @GET
  @Path("/SystemStop/{nodeName}")
  def SystemShutdown =
    path("management" / "system"/ "SystemStop" / Segment) { nodeName =>
      get {
        //extractClientIP { ip =>
        //  RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} System stop,nodeName=${nodeName}")
        //  complete { (handler ? rep.app.management.ManagementActor.SystemStop(nodeName)).mapTo[rep.app.management.ManagementActor.QueryResult] }
        //}.mapTo[QueryResult]
        //complete { (handler ? rep.app.management.ManagementActor.SystemStop(nodeName)).mapTo[rep.app.management.ManagementActor.QueryResult] }
        rejectEmptyResponse {
          onSuccess((handler ? SystemStop(nodeName))) { response =>
            complete(response.toString)
          }
        }
      }
    }



}
