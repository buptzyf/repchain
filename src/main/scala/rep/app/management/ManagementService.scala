package rep.app.management


import java.io.File
import scala.concurrent.ExecutionContext
import akka.actor.ActorRef
import akka.http.scaladsl.model.{HttpEntity, MediaTypes}
import akka.util.Timeout

import javax.ws.rs._
import javax.ws.rs.Path
import org.json4s.{DefaultFormats, jackson}
import akka.http.scaladsl.server.Directives
import akka.stream.scaladsl.FileIO
import akka.http.scaladsl.model.headers.`Tls-Session-Info`
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.{Operation, Parameter, Parameters}
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.{ApiResponse, ApiResponses}

import java.nio.file.Paths
import javax.ws.rs.core.MediaType
import scala.util.{Failure, Success}

@Path("/management")
class ManagementService(handler: ActorRef, isCheckPeerCertificate: Boolean)(implicit executionContext: ExecutionContext)
  extends Directives {

  import scala.concurrent.duration._
  import akka.pattern.{ask}
  import rep.app.management.ManagementActor.{SystemStatusQuery, SystemStart, SystemStop, SystemNetworkQuery}

  implicit val serialization = jackson.Serialization
  implicit val formats = DefaultFormats
  implicit val timeout = Timeout(20.seconds)


  val route = SystemStartup ~ QuerySystemStatus ~ SystemShutdown ~ QuerySystemNetwork ~ postConfigOfNode ~ getConfigOfNode

  @GET
  @Path("system/SystemStartup/{nodeName}")
  @Operation(tags = Array("SystemStartup"), summary = "启动一个节点", description = "StartNode", method = "GET",
    parameters = Array(new Parameter(name = "nodeName", description = "节点名", required = true, in = ParameterIn.PATH)),
    responses = Array(new ApiResponse(responseCode = "200", description = "返回节点启动结果", content = Array(new Content(mediaType = "text/plain", schema = new Schema(implementation = classOf[String])))))
  )
  def SystemStartup =
    path("management" / "system" / "SystemStartup" / Segment) { nodeName =>
      get {
        withRequestTimeout(300.seconds) {
          if (isCheckPeerCertificate) {
            headerValueByType[`Tls-Session-Info`]() { sessionInfo =>
              val cert = RepChainConfigFilePathMgr.getCert(sessionInfo)
              if (cert != null) {
                rejectEmptyResponse {
                  onSuccess((handler ? SystemStart(nodeName))) { response =>
                    complete(response.toString)
                  }
                }
              } else {
                complete("Failed to get client certificate")
              }

            }
          } else {
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
  @Path("system/SystemStatus/{nodeName}")
  @Operation(tags = Array("SystemStatus"), summary = "查询节点状态", description = "NodeStatus", method = "GET")
  @Parameters(Array(
    new Parameter(name = "nodeName", description = "节点名称", required = true, schema = new Schema(implementation = classOf[String]), in = ParameterIn.PATH)))
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回节点状态的结果", content = Array(new Content(mediaType = "text/plain", schema = new Schema(implementation = classOf[String])))))
  )
  def QuerySystemStatus =
    path("management" / "system" / "SystemStatus" / Segment) { nodeName =>
      get {
        withRequestTimeout(300.seconds) {
          if (isCheckPeerCertificate) {
            headerValueByType[`Tls-Session-Info`]() { sessionInfo =>
              val cert = RepChainConfigFilePathMgr.getCert(sessionInfo)
              if (cert != null) {
                rejectEmptyResponse {
                  onSuccess((handler ? SystemStatusQuery(nodeName))) { response =>
                    complete(response.toString)
                  }
                }
              } else {
                complete("Failed to get client certificate")
              }
            }
          } else {
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
  @Path("system/SystemStop/{nodeName}")
  @Operation(tags = Array("SystemStop"), summary = "停止指定名称的节点", description = "SystemStop", method = "GET")
  @Parameters(Array(
    new Parameter(name = "nodeName", description = "节点名称", required = true, schema = new Schema(implementation = classOf[String]), in = ParameterIn.PATH)))
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回节点停止的结果", content = Array(new Content(mediaType = "text/plain", schema = new Schema(implementation = classOf[String])))))
  )
  def SystemShutdown =
    path("management" / "system" / "SystemStop" / Segment) { nodeName =>
      get {
        withRequestTimeout(300.seconds) {
          if (isCheckPeerCertificate) {
            headerValueByType[`Tls-Session-Info`]() { sessionInfo =>
              val cert = RepChainConfigFilePathMgr.getCert(sessionInfo)
              if (cert != null) {
                rejectEmptyResponse {
                  onSuccess((handler ? SystemStop(nodeName))) { response =>
                    complete(response.toString)
                  }
                }
              } else {
                complete("Failed to get client certificate")
              }

            }
          } else {
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
  @Path("system/SystemNetwork/{nodeName}")
  @Operation(tags = Array("SystemNetwork"), summary = "查询网络ID", description = "SystemNetwork", method = "GET",
    parameters = Array(new Parameter(name = "nodeName", description = "节点名", required = true, in = ParameterIn.PATH)),
    responses = Array(new ApiResponse(responseCode = "200", description = "返回节点所在网络ID", content = Array(new Content(mediaType = "text/plain", schema = new Schema(implementation = classOf[String])))))
  )
  def QuerySystemNetwork =
    path("management" / "system" / "SystemNetwork" / Segment) { nodeName =>
      get {
        withRequestTimeout(300.seconds) {
          if (isCheckPeerCertificate) {
            headerValueByType[`Tls-Session-Info`]() { sessionInfo =>
              val cert = RepChainConfigFilePathMgr.getCert(sessionInfo)
              if (cert != null) {
                rejectEmptyResponse {
                  onSuccess((handler ? SystemNetworkQuery(nodeName))) { response =>
                    complete(response.toString)
                  }
                }
              } else {
                complete("Failed to get client certificate")
              }
            }
          } else {
            rejectEmptyResponse {
              onSuccess((handler ? SystemNetworkQuery(nodeName))) { response =>
                complete(response.toString)
              }
            }
          }
        }
      }
    }

  case class ConfigFile(node_name: String, file_type: String, network_name: String, upload_file: File)

  //提交节点的配置文件
  @POST
  @Path("system/postConfigFile")
  @Operation(tags = Array("PostConfigFile"), summary = "上传节点配置文件", description = "postConfigOfNode", method = "POST",
    requestBody = new RequestBody(description = "配置文件", required = true,
      content = Array(new Content(mediaType = MediaType.MULTIPART_FORM_DATA, schema = new Schema(name = "configFile", implementation = classOf[ConfigFile])))
    )
  )
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回上传结果", content = Array(new Content(mediaType = "text/plain", schema = new Schema(implementation = classOf[String]))))
  ))
  def postConfigOfNode =
    path("management" / "system" / "postConfigFile") {
      post {
        if (isCheckPeerCertificate) {
          headerValueByType[`Tls-Session-Info`]() { sessionInfo =>
            val cert = RepChainConfigFilePathMgr.getCert(sessionInfo)
            if (cert != null) {
              extractRequestContext { ctx =>
                implicit val materializer = ctx.materializer

                formFields("node_name", 'file_type, 'network_name) { (node_name, file_type, network_name) =>
                  System.out.println(file_type)
                  System.out.println(node_name)
                  fileUpload("upload_file") {
                    case (fileInfo, fileStream) =>
                      val path = RepChainConfigFilePathMgr.getSavePath(network_name, node_name, file_type, fileInfo.fileName)
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
            } else {
              complete("Failed to get client certificate")
            }
          }
        } else {
          extractRequestContext { ctx =>
            implicit val materializer = ctx.materializer

            formFields("node_name", 'file_type, 'network_name) { (node_name, file_type, network_name) =>
              System.out.println(file_type)
              System.out.println(node_name)
              fileUpload("upload_file") {
                case (fileInfo, fileStream) =>
                  val path = RepChainConfigFilePathMgr.getSavePath(network_name, node_name, file_type, fileInfo.fileName)
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

  //获取节点的配置文件
  @GET
  @Path("system/getConfigFile/{networkName}/{nodeName}")
  @Operation(tags = Array("GetConfigFile"), summary = "获取节点配置文件", description = "getConfigOfNode", method = "GET",
    parameters = Array(
      new Parameter(name = "networkName", description = "网络名", required = true, in = ParameterIn.PATH),
      new Parameter(name = "nodeName", description = "节点名", required = true, in = ParameterIn.PATH),
      new Parameter(name = "file_type", description = "配置文件类型", required = true, in = ParameterIn.QUERY),
      new Parameter(name = "file_name", description = "配置文件名称", required = true, in = ParameterIn.QUERY)
    ),
    responses = Array(new ApiResponse(responseCode = "200", description = "返回节点配置文件", content = Array(new Content(mediaType = "application/octet-stream"))))
  )
  def getConfigOfNode =
    path("management" / "system" / "getConfigFile" / Segment / Segment) { (networkName, nodeName) =>
      get {
        parameters("file_type", "file_name") { (fileType, fileName) => {
          if (isCheckPeerCertificate) {
            headerValueByType[`Tls-Session-Info`]() { sessionInfo =>
              val cert = RepChainConfigFilePathMgr.getCert(sessionInfo)
              if (cert == null) {
                complete("Failed to get client certificate")
              } else {
                getConfigOfNodeCore(networkName, nodeName, fileType, fileName)
              }
            }
          } else {
            getConfigOfNodeCore(networkName, nodeName, fileType, fileName)
          }
        }}
      }
    }
  def getConfigOfNodeCore(networkName: String, nodeName: String, fileType: String, fileName: String) = extractRequestContext { ctx =>
    implicit val materializer = ctx.materializer

    val path = RepChainConfigFilePathMgr.getSavePath(networkName, nodeName, fileType, fileName)
    complete(HttpEntity.fromFile(MediaTypes.`application/octet-stream`, new File(path.toString)))
  }
}
