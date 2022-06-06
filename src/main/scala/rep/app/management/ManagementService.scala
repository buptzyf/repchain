package rep.app.management


import java.io.StringWriter

import scala.concurrent.ExecutionContext
import akka.actor.ActorRef
import akka.util.Timeout
import javax.ws.rs._
import javax.ws.rs.Path
import org.json4s.{DefaultFormats, jackson}
import akka.http.scaladsl.server.Directives
import akka.stream.scaladsl.FileIO
import java.security.cert.X509Certificate
import akka.http.scaladsl.model.headers.`Tls-Session-Info`
import javax.net.ssl.SSLPeerUnverifiedException
import scala.util.{Failure, Success}

@Path("/management")
class ManagementService(handler: ActorRef,isCheckPeerCertificate:Boolean)(implicit executionContext: ExecutionContext)
  extends Directives {

  import scala.concurrent.duration._
  import akka.pattern.{ask}
  import rep.app.management.ManagementActor.{SystemStatusQuery, SystemStart, SystemStop, SystemNetworkQuery}

  implicit val serialization = jackson.Serialization
  implicit val formats = DefaultFormats
  implicit val timeout = Timeout(20.seconds)


  val route = SystemStartup ~ QuerySystemStatus ~ SystemShutdown ~ QuerySystemNetwork ~ postConfigOfNode

  @GET
  @Path("/SystemStartup/{nodeName}")
  def SystemStartup =
    path("management" / "system" / "SystemStartup" / Segment) { nodeName =>
      get {
        withRequestTimeout(300.seconds) {
          if(isCheckPeerCertificate){
            headerValueByType[`Tls-Session-Info`]() { sessionInfo =>
              val sslSession = sessionInfo.getSession()
              try{
                val client_cert = sslSession.getPeerCertificates
                val cert = client_cert(0).asInstanceOf[X509Certificate]
                if(cert != null){
                  //System.err.println(cert)
                  rejectEmptyResponse {
                    onSuccess((handler ? SystemStart(nodeName))) { response =>
                      complete(response.toString)
                    }
                  }
                }else{
                  complete("Failed to get client certificate")
                }
              }catch {
                case e: SSLPeerUnverifiedException =>
                  complete("Failed to get client certificate")
              }
            }
          }else {
            rejectEmptyResponse {
              onSuccess((handler ? SystemStart(nodeName))) { response =>
                complete(response.toString)
              }
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
          if(isCheckPeerCertificate){
            headerValueByType[`Tls-Session-Info`]() { sessionInfo =>
              val sslSession = sessionInfo.getSession()
              try{
                val client_cert = sslSession.getPeerCertificates
                val cert = client_cert(0).asInstanceOf[X509Certificate]
                if(cert != null)
                rejectEmptyResponse {
                  onSuccess((handler ? SystemStatusQuery(nodeName))) { response =>
                    complete(response.toString)
                  }
                }
              }catch {
                case e: SSLPeerUnverifiedException =>
                  complete("Failed to get client certificate")
              }
            }
          }else {
            rejectEmptyResponse {
              onSuccess((handler ? SystemStatusQuery(nodeName))) { response =>
                complete(response.toString)
              }
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
          if(isCheckPeerCertificate){
            headerValueByType[`Tls-Session-Info`]() { sessionInfo =>
              val sslSession = sessionInfo.getSession()
              try{
                val client_cert = sslSession.getPeerCertificates
                val cert = client_cert(0).asInstanceOf[X509Certificate]
                System.err.println(cert)
                //todo verify cert
                rejectEmptyResponse {
                  onSuccess((handler ? SystemStop(nodeName))) { response =>
                    complete(response.toString)
                  }
                }
              }catch {
                case e: SSLPeerUnverifiedException =>
                  complete("Failed to get client certificate")
              }
            }
          }else {
            rejectEmptyResponse {
              onSuccess((handler ? SystemStop(nodeName))) { response =>
                complete(response.toString)
              }
            }
          }
        }
      }
    }

  @GET
  @Path("/SystemNetwork/{nodeName}")
  def QuerySystemNetwork =
    path("management" / "system" / "SystemNetwork" / Segment) { nodeName =>
      get {
        withRequestTimeout(300.seconds) {
          if(isCheckPeerCertificate){
            headerValueByType[`Tls-Session-Info`]() { sessionInfo =>
              val sslSession = sessionInfo.getSession()
              try{
                val client_cert = sslSession.getPeerCertificates
                val cert = client_cert(0).asInstanceOf[X509Certificate]
                System.err.println(cert)
                //todo verify cert
                rejectEmptyResponse {
                  onSuccess((handler ? SystemNetworkQuery(nodeName))) { response =>
                    complete(response.toString)
                  }
                }
              }catch {
                case e: SSLPeerUnverifiedException =>
                  complete("Failed to get client certificate")
              }
            }
          }else {
            rejectEmptyResponse {
              onSuccess((handler ? SystemNetworkQuery(nodeName))) { response =>
                complete(response.toString)
              }
            }
          }
        }
      }
    }

  //以字节流提交签名交易
  @POST
  @Path("/postConfigFile")
  def postConfigOfNode =
    path("management" / "system" / "postConfigFile") {
      post {
        if(isCheckPeerCertificate){
          headerValueByType[`Tls-Session-Info`]() { sessionInfo =>
            val sslSession = sessionInfo.getSession()
            try{
              val client_cert = sslSession.getPeerCertificates
              val cert = client_cert(0).asInstanceOf[X509Certificate]
              System.err.println(cert)
              //todo verify cert
              extractRequestContext { ctx =>
                implicit val materializer = ctx.materializer

                formFields("node_name", 'file_type, 'network_name ) { (node_name, file_type,network_name) =>
                  System.out.println(file_type)
                  System.out.println(node_name)
                  fileUpload("upload_file") {
                    case (fileInfo, fileStream) =>
                      val path = RepChainConfigFilePathMgr.getSavePath(network_name,node_name,file_type,fileInfo.fileName)
                      //val sink = FileIO.toPath(Paths.get("conf") resolve fileInfo.fileName)
                      val sink = FileIO.toPath(path)
                      val writeResult = fileStream.runWith(sink)

                      onSuccess(writeResult) { result =>
                        result.status match {
                          case Success(_) => complete(s"Successfully submited ${result.count} bytes，file name=${fileInfo.fieldName}")
                          case Failure(e) => throw e
                        }
                      }
                  }
                }
              }
            }catch {
              case e: SSLPeerUnverifiedException =>
                complete("Failed to get client certificate")
            }
          }
        }else {
          extractRequestContext { ctx =>
            implicit val materializer = ctx.materializer

            formFields("node_name", 'file_type, 'network_name ) { (node_name, file_type,network_name) =>
              System.out.println(file_type)
              System.out.println(node_name)
              fileUpload("upload_file") {
                case (fileInfo, fileStream) =>
                  val path = RepChainConfigFilePathMgr.getSavePath(network_name,node_name,file_type,fileInfo.fileName)
                  //val sink = FileIO.toPath(Paths.get("conf") resolve fileInfo.fileName)
                  val sink = FileIO.toPath(path)
                  val writeResult = fileStream.runWith(sink)

                  onSuccess(writeResult) { result =>
                    result.status match {
                      case Success(_) => complete(s"Successfully submited ${result.count} bytes，file name=${fileInfo.fieldName}")
                      case Failure(e) => throw e
                    }
                  }
              }
            }
          }
        }
      }
    }
}
