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

package rep.api.rest

import java.io.File
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.ByteString
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scala.concurrent.{ExecutionContext, Future}
import akka.util.Timeout
import akka.http.scaladsl.model.headers.`Tls-Session-Info`
import org.json4s.jackson.JsonMethods
import org.json4s.string2JsonInput
import rep.app.management.RepChainConfigFilePathMgr
import rep.app.system.RepChainSystemContext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.enums.{ParameterIn, ParameterStyle}
import io.swagger.v3.oas.annotations.{Parameter, Parameters}
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.{ApiResponse, ApiResponses}
import io.swagger.v3.oas.annotations.tags.Tag
import javax.ws.rs._
import javax.ws.rs.core.MediaType
import javax.ws.rs.Path
import rep.proto.rc2.{Block, Transaction}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import rep.api.rest.RestActor._
import spray.json.DefaultJsonProtocol._
import org.json4s.{DefaultFormats, jackson}
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport
import akka.http.scaladsl.model.HttpCharsets
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import rep.authority.check.PermissionVerify

import scala.xml.NodeSeq
import rep.log.RepLogger

/**
 * 获得区块链的概要信息
 *
 * @author c4w
 */
@Tag(name = "chaininfo", description = "获得当前区块链信息")
@Path("/chaininfo")
class ChainService(ra: ActorRef)(implicit executionContext: ExecutionContext) extends Directives {

  import Json4sSupport._

  implicit val serialization = jackson.Serialization // or native.Serialization
  implicit val formats = DefaultFormats
  implicit val timeout = Timeout(20.seconds)

  val route = getBlockChainInfo ~ getNodeNumber ~ getCacheTransNumber ~ getAcceptedTransNumber

  @GET
  @Operation(tags = Array("chaininfo"), summary = "返回块链信息", description = "getChainInfo", method = "GET")
  @ApiResponse(responseCode = "200", description = "返回块链信息", content = Array(new Content(mediaType = "application/json", schema = new Schema(implementation = classOf[QueryResult]))))
  def getBlockChainInfo =
    path("chaininfo") {
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get chaininfo")
          complete {
            (ra ? ChainInfo).mapTo[QueryResult]
          }
        }
      }
    }

  @GET
  @Path("/node")
  @Operation(tags = Array("chaininfo"), summary = "返回组网节点数量", description = "getNodeNumber", method = "GET")
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回组网节点数量", content = Array(new Content(mediaType = "application/json", schema = new Schema(implementation = classOf[QueryResult])))))
  )
  def getNodeNumber =
    path("chaininfo" / "node") {
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get node number")
          complete {
            (ra ? NodeNumber).mapTo[QueryResult]
          }
        }
      }
    }


  @GET
  @Path("/getcachetransnumber")
  @Operation(tags = Array("chaininfo"), summary = "返回系统缓存交易数量", description = "getCacheTransNumber", method = "GET")
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回系统缓存交易数量", content = Array(new Content(mediaType = "application/json", schema = new Schema(implementation = classOf[QueryResult])))))
  )
  def getCacheTransNumber =
    path("chaininfo" / "getcachetransnumber") {
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get number of cache")
          complete {
            (ra ? TransNumber).mapTo[QueryResult]
          }
        }
      }
    }

  @GET
  @Path("/getAcceptedTransNumber")
  @Operation(tags = Array("chaininfo"), summary = "返回系统接收到的交易数量", description = "getAcceptedTransNumber", method = "GET")
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回系统接收到的交易数量", content = Array(new Content(mediaType = "application/json", schema = new Schema(implementation = classOf[QueryResult])))))
  )
  def getAcceptedTransNumber =
    path("chaininfo" / "getAcceptedTransNumber") {
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get number of accepted")
          complete {
            (ra ? AcceptedTransNumber).mapTo[QueryResult]
          }
        }
      }
    }
}

/**
 * 获得指定区块的详细信息
 *
 * @author c4w
 */

@Tag(name = "block", description = "获得区块数据")
@Path("/block")
class BlockService(ra: ActorRef, repContext: RepChainSystemContext, isCheckClientPermission: Boolean)(implicit executionContext: ExecutionContext)
  extends Directives {

  import Json4sSupport._

  implicit val timeout = Timeout(20.seconds)
  implicit val serialization = jackson.Serialization // or native.Serialization
  implicit val formats = DefaultFormats

  val route = getBlockById ~ getBlockByHeightToo ~ getTransNumberOfBlock ~ getBlockStreamByHeight ~ getBlockTimeOfCreate ~ getBlockTimeOfTransaction

  @GET
  @Path("/hash/{blockId}")
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("block"), summary = "返回指定id的区块", description = "getBlockById", method = "GET",
    parameters = Array(new Parameter(name = "blockId", description = "区块id", required = true, in = ParameterIn.PATH)),
    responses = Array(new ApiResponse(responseCode = "200", description = "返回区块json内容", content = Array(new Content(mediaType = "application/json", schema = new Schema(implementation = classOf[QueryResult])))))
  )
  def getBlockById =
    path("block" / "hash" / Segment) { blockId =>
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get block for id,block id=${blockId}")
          if (isCheckClientPermission) {
            headerValueByType[`Tls-Session-Info`]() { sessionInfo =>
              val cert = RepChainConfigFilePathMgr.getCert(sessionInfo)
              try {
                if (cert != null && repContext.getPermissionVerify.CheckPermissionOfX509Certificate(cert, "block.hash", null)) {
                  complete {
                    (ra ? BlockId(blockId)).mapTo[QueryResult]
                  }
                } else {
                  complete(QueryResult(Option(JsonMethods.parse(string2JsonInput(PermissionVerify.errorInfo_None_Permission)))))
                }
              } catch {
                case e: Exception =>
                  complete(QueryResult(Option(JsonMethods.parse(string2JsonInput(PermissionVerify.errorInfo_Cert_or_permission)))))
              }
            }
          } else {
            complete {
              (ra ? BlockId(blockId)).mapTo[QueryResult]
            }
          }
        }
      }
    }

  @GET
  @Path("/{blockHeight}")
  @Operation(tags = Array("block"), summary = "返回指定高度的区块", description = "getBlockByHeight", method = "GET")
  @Parameters(Array(
    new Parameter(name = "blockHeight", description = "区块高度", required = true, schema = new Schema(implementation = classOf[Int]), in = ParameterIn.PATH, example = "1")))
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回区块json内容", content = Array(new Content(mediaType = "application/json", schema = new Schema(implementation = classOf[QueryResult])))))
  )
  def getBlockByHeightToo =
    path("block" / Segment) { blockHeight =>
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get block for Height,block height=${blockHeight}")
          if (isCheckClientPermission) {
            headerValueByType[`Tls-Session-Info`]() { sessionInfo =>
              val cert = RepChainConfigFilePathMgr.getCert(sessionInfo)
              try {
                if (cert != null && repContext.getPermissionVerify.CheckPermissionOfX509Certificate(cert, "block.blockHeight", null)) {
                  complete {
                    (ra ? BlockHeight(blockHeight.toInt)).mapTo[QueryResult]
                  }
                } else {
                  complete(QueryResult(Option(JsonMethods.parse(string2JsonInput(PermissionVerify.errorInfo_None_Permission)))))
                }
              } catch {
                case e: Exception =>
                  complete(QueryResult(Option(JsonMethods.parse(string2JsonInput(PermissionVerify.errorInfo_Cert_or_permission)))))
              }
            }
          } else {
            complete {
              (ra ? BlockHeight(blockHeight.toInt)).mapTo[QueryResult]
            }
          }
        }
      }
    }


  @POST
  @Path("/getTransNumberOfBlock")
  @Operation(tags = Array("block"), summary = "返回指定高度区块包含的交易数", description = "getTransNumberOfBlock", method = "POST",
    requestBody = new RequestBody(description = "区块高度，最小为2", required = true,
      content = Array(new Content(mediaType = MediaType.APPLICATION_JSON, schema = new Schema(name = "height", description = "height, 最小为2", `type` = "string", example = "{\"height\":2}")))))
  //    @Parameters(Array(
  //      new Parameter(name = "height", description = "区块高度", required = true, schema = new Schema(`type` = String))))
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回指定高度区块包含的交易数", content = Array(new Content(mediaType = "application/json", schema = new Schema(implementation = classOf[QueryResult])))))
  )
  def getTransNumberOfBlock =
    path("block" / "getTransNumberOfBlock") {
      post {
        entity(as[Map[String, Long]]) { blockQuery =>
          complete {
            (ra ? TransNumberOfBlock(blockQuery("height"))).mapTo[QueryResult]
          }
        }
      }
    }

  @GET
  @Path("/blocktime/{blockHeight}")
  @Operation(tags = Array("block"), summary = "返回指定高度的区块的出块时间", description = "getBlockTimeOfCreate", method = "GET")
  @Parameters(Array(
    new Parameter(name = "blockHeight", description = "区块高度, 最小为2", required = true, schema = new Schema(description = "height, 最小为2", `type` = "string"), in = ParameterIn.PATH, example = "2")))
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回指定高度的区块的出块时间", content = Array(new Content(mediaType = "application/json", schema = new Schema(implementation = classOf[QueryResult])))))
  )
  def getBlockTimeOfCreate =
    path("block" / "blocktime" / Segment) { blockHeight =>
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get block time for Height,block height=${blockHeight}")
          complete {
            (ra ? BlockTimeForHeight(blockHeight.toLong)).mapTo[QueryResult]
          }
        }
      }
    }

  @GET
  @Path("/blocktimeoftran/{transid}")
  @Operation(tags = Array("block"), summary = "返回指定交易的入块时间", description = "getBlockTimeOfTransaction", method = "GET")
  @Parameters(Array(
    new Parameter(name = "transid", description = "交易id", required = true, schema = new Schema(`type` = "string"), in = ParameterIn.PATH)))
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回指定交易的入块时间", content = Array(new Content(mediaType = "application/json", schema = new Schema(implementation = classOf[QueryResult])))))
  )
  def getBlockTimeOfTransaction =
    path("block" / "blocktimeoftran" / Segment) { transid =>
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get block time for txid,txid=${transid}")
          complete {
            (ra ? BlockTimeForTxid(transid)).mapTo[QueryResult]
          }
        }
      }
    }


  @GET
  @Path("/stream/{blockHeight}")
  @Operation(tags = Array("block"), summary = "返回指定高度的区块字节流", description = "getBlockStreamByHeight", method = "GET")
  @Parameters(Array(
    new Parameter(name = "blockHeight", description = "区块高度", required = true, schema = new Schema(`type` = "integer", format = "int64"), in = ParameterIn.PATH, example = "1")))
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "blockbytes", content = Array(new Content(mediaType = "application/octet-stream", schema = new Schema(implementation = classOf[Block]))))))
  def getBlockStreamByHeight =
    path("block" / "stream" / Segment) { blockHeight =>
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get block stream for Height,block height=${blockHeight}")
          if (isCheckClientPermission) {
            headerValueByType[`Tls-Session-Info`]() { sessionInfo =>
              val cert = RepChainConfigFilePathMgr.getCert(sessionInfo)
              try {
                if (cert != null && repContext.getPermissionVerify.CheckPermissionOfX509Certificate(cert, "block.stream", null)) {
                  complete((ra ? BlockHeightStream(blockHeight.toInt)).mapTo[HttpResponse])
                } else {
                  complete(QueryResult(Option(JsonMethods.parse(string2JsonInput(PermissionVerify.errorInfo_None_Permission)))))
                }
              } catch {
                case e: Exception =>
                  complete(QueryResult(Option(JsonMethods.parse(string2JsonInput(PermissionVerify.errorInfo_Cert_or_permission)))))
              }
            }
          } else {
            complete((ra ? BlockHeightStream(blockHeight.toInt)).mapTo[HttpResponse])
          }
        }
      }
    }
}

/**
 * 获得指定交易的详细信息，提交签名交易
 *
 * @author c4w
 */
@Tag(name = "transaction", description = "获得交易数据")
@Consumes(Array(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.MULTIPART_FORM_DATA))
@Produces(Array(MediaType.APPLICATION_JSON))
@Path("/transaction")
class TransactionService(ra: ActorRef, repContext: RepChainSystemContext, isCheckClientPermission: Boolean)(implicit executionContext: ExecutionContext)
  extends Directives {

  import Json4sSupport._
  import ScalaXmlSupport._

  implicit val timeout = Timeout(20.seconds)
  implicit val serialization = jackson.Serialization // or native.Serialization
  implicit val formats = DefaultFormats
  implicit val specFormat = jsonFormat15(CSpec)
  implicit val specUnmarshaller: FromEntityUnmarshaller[CSpec] = Unmarshaller.firstOf(
    //只能处理application/xml
    nodeSeqUnmarshaller(MediaTypes.`application/xml` withCharset HttpCharsets.`UTF-8`) map {
      case NodeSeq.Empty =>
        throw Unmarshaller.NoContentException
      case x =>
        CSpec(
          (x \ "stype").text.toInt,
          (x \ "chaincodename").text,
          (x \ "chaincodeversion").text.toInt,
          (x \ "iptFunc").text,
          Seq((x \ "iptArgs").text),
          (x \ "timeout").text.toInt,
          (x \ "legal_prose").text,
          (x \ "code").text,
          (x \ "ctype").text.toInt,
          (x \ "state").text.toBoolean,

          (x \ "gasLimited").text.toInt,
          (x \ "oid").text,
          (x \ "runType").text.toInt,
          (x \ "stateType").text.toInt,
          (x \ "contractLevel").text.toInt
        )
    },
    //只能处理application/json
    unmarshaller[CSpec].forContentTypes(MediaTypes.`application/json`))

  val route = getTransaction ~ getTransactionStream ~ tranInfoAndHeightOfTranId ~ postSignTransaction ~ postTransaction ~ postSignTransactionStream

  @GET
  @Path("/{transactionId}")
  @Operation(tags = Array("transaction"), summary = "返回指定id的交易", description = "getTransaction", method = "GET")
  @Parameters(Array(
    new Parameter(name = "transactionId", description = "交易id", required = false, schema = new Schema(`type` = "string"), in = ParameterIn.PATH)))
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回交易json内容", content = Array(new Content(mediaType = "application/json", schema = new Schema(implementation = classOf[QueryResult])))))
  )
  def getTransaction =
    path("transaction" / Segment) { transactionId =>
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get transaction for txid,txid=${transactionId}")
          if (isCheckClientPermission) {
            headerValueByType[`Tls-Session-Info`]() { sessionInfo =>
              val cert = RepChainConfigFilePathMgr.getCert(sessionInfo)
              try {
                if (cert != null && repContext.getPermissionVerify.CheckPermissionOfX509Certificate(cert, "transaction", null)) {
                  complete {
                    (ra ? TransactionId(transactionId)).mapTo[QueryResult]
                  }
                } else {
                  complete(QueryResult(Option(JsonMethods.parse(string2JsonInput(PermissionVerify.errorInfo_None_Permission)))))
                }
              } catch {
                case e: Exception =>
                  complete(QueryResult(Option(JsonMethods.parse(string2JsonInput(PermissionVerify.errorInfo_Cert_or_permission)))))
              }
            }
          } else {
            complete {
              (ra ? TransactionId(transactionId)).mapTo[QueryResult]
            }
          }
        }
      }
    }

  @GET
  @Path("/stream/{transactionId}")
  @Produces(Array(MediaType.APPLICATION_OCTET_STREAM))
  @Operation(tags = Array("transaction"), description = "返回指定id的交易字节流", summary = "getTransactionStream", method = "GET")
  @Parameters(Array(
    new Parameter(name = "transactionId", description = "交易id", required = false, schema = new Schema(`type` = "string"), in = ParameterIn.PATH)))
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回交易字节流", content = Array(new Content(mediaType = "application/octet-stream", schema = new Schema(implementation = classOf[Transaction], `type` = "string", format = "binary")))))
  )
  def getTransactionStream =
    path("transaction" / "stream" / Segment) { transactionId =>
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get transaction stream for txid,txid=${transactionId}")
          if (isCheckClientPermission) {
            headerValueByType[`Tls-Session-Info`]() { sessionInfo =>
              val cert = RepChainConfigFilePathMgr.getCert(sessionInfo)
              try {
                if (cert != null && repContext.getPermissionVerify.CheckPermissionOfX509Certificate(cert, "transaction.stream", null)) {
                  complete((ra ? TransactionStreamId(transactionId)).mapTo[HttpResponse])
                } else {
                  complete(QueryResult(Option(JsonMethods.parse(string2JsonInput(PermissionVerify.errorInfo_None_Permission)))))
                }
              } catch {
                case e: Exception =>
                  complete(QueryResult(Option(JsonMethods.parse(string2JsonInput(PermissionVerify.errorInfo_Cert_or_permission)))))
              }
            }
          } else {
            complete((ra ? TransactionStreamId(transactionId)).mapTo[HttpResponse])
          }
        }
      }
    }

  @GET
  @Path("/tranInfoAndHeight/{transactionId}")
  @Operation(tags = Array("transaction"), summary = "返回指定id的交易信息及所在区块高度", description = "tranInfoAndHeightOfTranId", method = "GET")
  @Parameters(Array(
    new Parameter(name = "transactionId", description = "交易id", required = false, schema = new Schema(`type` = "string"), in = ParameterIn.PATH)))
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回指定id的交易信息及所在区块高度", content = Array(new Content(mediaType = "application/json", schema = new Schema(implementation = classOf[QueryResult])))))
  )
  def tranInfoAndHeightOfTranId =
    path("transaction" / "tranInfoAndHeight" / Segment) { transactionId =>
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get transactionInfo and blockHeight for txid,txid=${transactionId}")
          val errorInfo_None_Permission = "{\"error info\":\"You do not have this operation{transaction.tranInfoAndHeight} permission, please contact the administrator.\"}"
          if (isCheckClientPermission) {
            headerValueByType[`Tls-Session-Info`]() { sessionInfo =>
              val cert = RepChainConfigFilePathMgr.getCert(sessionInfo)
              try {
                if (cert != null && repContext.getPermissionVerify.CheckPermissionOfX509Certificate(cert, "transaction.tranInfoAndHeight", null)) {
                  complete((ra ? TranInfoAndHeightId(transactionId)).mapTo[QueryResult])
                } else {
                  complete(QueryResult(Option(JsonMethods.parse(string2JsonInput(PermissionVerify.errorInfo_None_Permission)))))
                }
              } catch {
                case e: Exception =>
                  complete(QueryResult(Option(JsonMethods.parse(string2JsonInput(PermissionVerify.errorInfo_Cert_or_permission)))))
              }
            }
          } else {
            complete((ra ? TranInfoAndHeightId(transactionId)).mapTo[QueryResult])
          }


        }
      }
    }

  //以十六进制字符串提交签名交易
  @POST
  @Path("/postTranByString")
  @Operation(tags = Array("transaction"), summary = "提交带签名的交易", description = "postSignTransaction", method = "POST",
    requestBody = new RequestBody(description = "签名交易的16进制字符串", required = true,
      content = Array(new Content(mediaType = MediaType.APPLICATION_JSON, schema = new Schema(name = "签名交易Hex字符串", description = "签名交易", `type` = "string")))))
  //  @Parameters(Array(
  //    new Parameter(name = "body", value = "交易内容", required = true, dataType = "string", paramType = "body")))
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回交易id以及执行结果", content = Array(new Content(mediaType = "application/json", schema = new Schema(implementation = classOf[PostResult])))),
    new ApiResponse(responseCode = "202", description = "处理存在异常", content = Array(new Content(mediaType = "application/json", schema = new Schema(implementation = classOf[PostResult])))))
  )
  def postSignTransaction =
    path("transaction" / "postTranByString") {
      post {
        entity(as[String]) { trans =>
          complete {
            (ra ? signedTran(trans)).mapTo[PostResult]
          }
          //          complete { (StatusCodes.Accepted, PostResult("hahhaha",None, Some("处理存在异常"))) }
        }
      }
    }

  case class SignedTransData(var signedTrans: File)

  //以字节流提交签名交易
  @POST
  @Path("/postTranStream")
  @Operation(tags = Array("transaction"), summary = "提交带签名的交易字节流", description = "postSignTransactionStream", method = "POST",
    //    parameters = Array(new Parameter(name = "signedTrans", schema = new Schema(`type` = "string", format = "binary"), style = ParameterStyle.FORM, explode = Explode.TRUE))
    requestBody = new RequestBody(description = "签名交易的二进制文件", required = true,
      content = Array(new Content(mediaType = MediaType.MULTIPART_FORM_DATA, schema = new Schema(name = "signedTrans", implementation = classOf[SignedTransData])))
    )
  )
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回交易id以及执行结果", content = Array(new Content(mediaType = "application/json", schema = new Schema(implementation = classOf[PostResult])))),
    new ApiResponse(responseCode = "202", description = "处理存在异常", content = Array(new Content(mediaType = "application/json", schema = new Schema(implementation = classOf[PostResult])))))
  )
  def postSignTransactionStream =
    path("transaction" / "postTranStream") {
      post {
        extractRequestContext { ctx =>
          implicit val materializer = ctx.materializer

          fileUpload("signedTrans") {
            case (fileInfo, fileStream) =>
              RepLogger.debug(RepLogger.APIAccess_Logger, s"流式提交交易，fileInfo=$fileInfo")
              val tranFuture: Future[ByteString] = fileStream.runFold(ByteString.empty)(_ ++ _)
              onComplete(tranFuture) {
                case Success(tranByteString) =>
                  complete {
                    (ra ? Transaction.parseFrom(tranByteString.toArray)).mapTo[PostResult]
                  }
                case Failure(ex) =>
                  complete(StatusCodes.InternalServerError, ex.getMessage)
              }
          }
        }
      }
    }


  @POST
  @Path("/postTran")
  @Operation(tags = Array("transaction"), summary = "提交交易", description = "postTransaction", method = "POST",
    requestBody = new RequestBody(description = "描述交易的xml/json", required = true,
      content = Array(new Content(mediaType = MediaType.APPLICATION_XML, schema = new Schema(implementation = classOf[CSpec], description = "描述交易的xml")),
        new Content(mediaType = MediaType.APPLICATION_JSON, schema = new Schema(implementation = classOf[CSpec], description = "描述交易的json")))))
  //  @Parameters(Array(
  //    new Parameter(name = "body", value = "交易内容", required = true,
  //      dataTypeClass = classOf[CSpec], paramType = "body")))
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回交易id以及执行结果", content = Array(new Content(mediaType = "application/json", schema = new Schema(implementation = classOf[PostResult])))),
    new ApiResponse(responseCode = "202", description = "处理存在异常", content = Array(new Content(mediaType = "application/json", schema = new Schema(implementation = classOf[PostResult])))))
  )
  def postTransaction =
    path("transaction" / "postTran") {
      post {
        import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
        entity(as[CSpec]) { request =>
          complete {
            (ra ? request).mapTo[PostResult]
          }
        }
      }
    }
}

/**
 * DB相关操作
 *
 * @author zyf
 */
@Tag(name = "db", description = "查询合约存储在DB中的数据")
@Path("/db")
class DbService(ra: ActorRef, repContext: RepChainSystemContext, isCheckClientPermission: Boolean)(implicit executionContext: ExecutionContext)
  extends Directives {

  import Json4sSupport._

  implicit val serialization = jackson.Serialization // or native.Serialization
  implicit val formats = DefaultFormats
  implicit val timeout = Timeout(20.seconds)

  val route = queryDB

  @POST
  @Path("/query")
  @Operation(tags = Array("db"), summary = "查询合约存储在DB中的数据", description = "queryDB", method = "POST",
    requestBody = new RequestBody(description = "合约名，以及对应的key", required = true,
      content = Array(new Content(mediaType = MediaType.APPLICATION_JSON, schema = new Schema(implementation = classOf[QueryDB])))))
  @ApiResponse(responseCode = "200", description = "返回对应于某个key的value", content = Array(new Content(mediaType = "application/json", schema = new Schema(implementation = classOf[QueryResult]))))
  def queryDB =
    path("db" / "query") {
      post {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} query levelDB or rocksDB")
          if (isCheckClientPermission) {
            headerValueByType[`Tls-Session-Info`]() { sessionInfo =>
              val cert = RepChainConfigFilePathMgr.getCert(sessionInfo)
              try {
                if (cert != null && repContext.getPermissionVerify.CheckPermissionOfX509Certificate(cert, "db.query", null)) {
                  entity(as[QueryDB]) { query: QueryDB =>
                    complete {
                      (ra ? query).mapTo[QueryResult]
                    }
                  }
                } else {
                  complete(QueryResult(Option(JsonMethods.parse(string2JsonInput(PermissionVerify.errorInfo_None_Permission)))))
                }
              } catch {
                case e: Exception =>
                  complete(QueryResult(Option(JsonMethods.parse(string2JsonInput(PermissionVerify.errorInfo_Cert_or_permission)))))
              }
            }
          } else {
            entity(as[QueryDB]) { query: QueryDB =>
              complete {
                (ra ? query).mapTo[QueryResult]
              }
            }
          }
        }
      }
    }
}

/**
 * 查询节点相关信息
 *
 * @author zyf
 */
@Tag(name = "nodeinfo", description = "获得当前节点信息")
@Path("/nodeinfo")
class NodeService(ra: ActorRef)(implicit executionContext: ExecutionContext) extends Directives {

  import Json4sSupport._

  implicit val serialization = jackson.Serialization // or native.Serialization
  implicit val formats = DefaultFormats
  implicit val timeout = Timeout(20.seconds)

  val route = getNodeInfo

  @GET
  @Operation(tags = Array("nodeinfo"), summary = "返回节点信息", description = "getNodeInfo", method = "GET")
  @ApiResponse(responseCode = "200", description = "返回节点信息", content = Array(new Content(mediaType = "application/json", schema = new Schema(implementation = classOf[QueryResult]))))
  def getNodeInfo =
    path("nodeinfo") {
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get nodeinfo")
          complete {
            (ra ? NodeInfo).mapTo[QueryResult]
          }
        }
      }
    }
}