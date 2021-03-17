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
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

import scala.concurrent.{ExecutionContext, Future}
import akka.actor.{ActorRef, ActorSelection}
import akka.util.Timeout
import akka.http.scaladsl.model.Uri.Path.Segment
import akka.http.scaladsl.server.Directives
import io.swagger.v3.core.util.PrimitiveType
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.enums.{ParameterIn, ParameterStyle}
import io.swagger.v3.oas.annotations.{Parameter, Parameters}
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.{ApiResponse, ApiResponses}
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.media.BinarySchema
import javax.ws.rs._
import javax.ws.rs.core.MediaType
import javax.ws.rs.Path
//import org.glassfish.jersey.media.multipart.FormDataParam
//import io.swagger.annotations._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import StatusCodes._
import Directives._
import rep.sc.Sandbox.SandboxException
import rep.sc.Sandbox._
import rep.sc.Shim._
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import rep.protos.peer._
import rep.api.rest.RestActor._
import spray.json.DefaultJsonProtocol._
import org.json4s.{DefaultFormats, Formats, jackson}
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport
import akka.http.scaladsl.model.{ContentTypes, HttpCharsets}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import akka.stream.scaladsl.StreamConverters

import scala.xml.NodeSeq
import rep.log.RepLogger

/**
  * 获得区块链的概要信息
  *
  * @author c4w
  */
@Tag(name = "chaininfo", description = "获得当前区块链信息")
@Path("/chaininfo")
class ChainService(ra: RestRouter)(implicit executionContext: ExecutionContext)
  extends Directives {

  import akka.pattern.ask
  import scala.concurrent.duration._

  import Json4sSupport._

  implicit val serialization = jackson.Serialization // or native.Serialization
  implicit val formats = DefaultFormats
  implicit val timeout = Timeout(20.seconds)



  val route = getBlockChainInfo ~ getNodeNumber ~ getCacheTransNumber ~ getAcceptedTransNumber ~ loadBlockInfoToCache ~ IsLoadBlockInfoToCache

  @GET
  @Operation(tags = Array("chaininfo"), summary = "返回块链信息", description = "getChainInfo", method = "GET")
  @ApiResponse(responseCode = "200", description = "返回块链信息", content = Array(new Content(mediaType = "application/json",schema = new Schema(implementation = classOf[QueryResult]))))
  def getBlockChainInfo =
    path("chaininfo") {
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get chaininfo")
          complete { (ra.getRestActor ? ChainInfo).mapTo[QueryResult] }
        }
      }
    }

  @GET
  @Path("/node")
  @Operation(tags = Array("chaininfo"),  summary = "返回组网节点数量", description = "getNodeNumber", method = "GET")
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回组网节点数量", content =  Array(new Content(mediaType = "application/json",schema = new Schema(implementation = classOf[QueryResult])))))
  )
  def getNodeNumber =
    path("chaininfo" / "node") {
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get node number")
          complete { (ra.getRestActor ? NodeNumber).mapTo[QueryResult] }
        }
      }
    }

  @GET
  @Path("/loadBlockInfoToCache")
  @Operation(tags = Array("chaininfo"), summary = "初始化装载区块索引到缓存",  description= "loadBlockInfoToCache", method = "GET")
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "初始化装载区块索引到缓存量", content =  Array(new Content(mediaType = "application/json",schema = new Schema(implementation = classOf[QueryResult])))))
  )
  def loadBlockInfoToCache =
    path("chaininfo" / "loadBlockInfoToCache") {
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get loadBlockInfoToCache")
          complete { (ra.getRestActor ? LoadBlockInfo).mapTo[QueryResult] }
        }
      }
    }

  @GET
  @Path("/IsLoadBlockInfoToCache")
  @Operation(tags = Array("chaininfo"), summary  = "是否完成始化装载区块索引到缓存", description  = "IsLoadBlockInfoToCache", method = "GET")
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "是否完成初始化装载区块索引到缓存量", content =  Array(new Content(mediaType = "application/json",schema = new Schema(implementation = classOf[QueryResult])))))
  )
  def IsLoadBlockInfoToCache =
    path("chaininfo" / "IsLoadBlockInfoToCache") {
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get IsLoadBlockInfoToCache")
          complete { (ra.getRestActor ? IsLoadBlockInfo).mapTo[QueryResult] }
        }
      }
    }

  @GET
  @Path("/getcachetransnumber")
  @Operation(tags = Array("chaininfo"), summary  = "返回系统缓存交易数量", description = "getCacheTransNumber", method = "GET")
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回系统缓存交易数量", content =  Array(new Content(mediaType = "application/json",schema = new Schema(implementation = classOf[QueryResult])))))
  )
  def getCacheTransNumber =
    path("chaininfo" / "getcachetransnumber") {
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get number of cache")
          complete { (ra.getRestActor ? TransNumber).mapTo[QueryResult] }
        }
      }
    }

  @GET
  @Path("/getAcceptedTransNumber")
  @Operation(tags = Array("chaininfo"), summary  = "返回系统接收到的交易数量", description = "getAcceptedTransNumber", method = "GET")
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回系统接收到的交易数量", content =  Array(new Content(mediaType = "application/json",schema = new Schema(implementation = classOf[QueryResult])))))
  )
  def getAcceptedTransNumber =
    path("chaininfo" / "getAcceptedTransNumber") {
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get number of accepted")
          complete {
            (ra.getRestActor ? AcceptedTransNumber).mapTo[QueryResult]
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
class BlockService(ra: RestRouter)(implicit executionContext: ExecutionContext)
  extends Directives {

  import akka.pattern.ask
  import scala.concurrent.duration._

  implicit val timeout = Timeout(20.seconds)

  import Json4sSupport._

  implicit val serialization = jackson.Serialization // or native.Serialization
  implicit val formats = DefaultFormats

  val route = getBlockById ~ getBlockByHeight ~ getBlockByHeightToo ~ getTransNumberOfBlock ~ getBlockStreamByHeight ~ getBlockTimeOfCreate ~ getBlockTimeOfTxrByTxid ~ getBlockTimeOfTransaction

  @GET
  @Path("/hash/{blockId}")
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("block"),summary = "返回指定id的区块",  description = "getBlockById", method = "GET",
    parameters = Array(new Parameter(name = "blockId", description = "区块id", required = true, in = ParameterIn.PATH)),
    responses = Array(new ApiResponse(responseCode = "200", description = "返回区块json内容", content =  Array(new Content(mediaType = "application/json",schema = new Schema(implementation = classOf[QueryResult])))))
  )
  //  @ApiResponses(Array(
  //    new ApiResponse(responseCode = "200", description = "返回区块json内容", content =  Array(new Content(mediaType = "application/json",schema = new Schema(implementation = classOf[QueryResult])))))
  //  )
  def getBlockById =
    path("block" / "hash" / Segment) { blockId =>
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get block for id,block id=${blockId}")
          complete { (ra.getRestActor ? BlockId(blockId)).mapTo[QueryResult] }
        }
      }
    }

  @GET
  @Path("/{blockHeight}")
  @Operation(tags = Array("block"), summary  = "返回指定高度的区块", description = "getBlockByHeight", method = "GET")
  @Parameters(Array(
    new Parameter(name = "blockHeight", description = "区块高度", required = true, schema = new Schema(implementation = classOf[Int]), in = ParameterIn.PATH, example = "1")))
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回区块json内容", content =  Array(new Content(mediaType = "application/json",schema = new Schema(implementation = classOf[QueryResult])))))
  )
  def getBlockByHeightToo =
    path("block" / Segment) { blockHeight =>
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get block for Height,block height=${blockHeight}")
          complete { (ra.getRestActor ? BlockHeight(blockHeight.toInt)).mapTo[QueryResult] }
        }

        //complete { (ra ? BlockHeight(blockHeight.toInt)).mapTo[QueryResult] }
      }
    }

  @POST
  @Path("/blockHeight")
  @Operation(tags = Array("block"), summary  = "返回指定高度的区块", description = "getBlockByHeight", method = "POST",
    requestBody = new RequestBody(description = "区块高度", required = true,
      content = Array(new Content(mediaType = MediaType.APPLICATION_JSON, schema = new Schema(name = "height", description = "height", `type` = "string", example = "{\"height\":1}")))))
  //  @Parameters(Array(
  //    new Parameter(name = "height", description = "区块高度", required = true, schema = new Schema(implementation = classOf[String], `type` = "string"), in = ParameterIn.DEFAULT)))
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回区块json内容", content =  Array(new Content(mediaType = "application/json",schema = new Schema(implementation = classOf[QueryResult])))))
  )
  def getBlockByHeight =
    path("block" / "blockHeight") {
      post {
        entity(as[Map[String, Int]]) { blockQuery =>
          complete {
            (ra.getRestActor ? BlockHeight(blockQuery("height"))).mapTo[QueryResult]
          }
        }
      }
    }

  @POST
  @Path("/getTransNumberOfBlock")
  @Operation(tags = Array("block"),summary  = "返回指定高度区块包含的交易数", description   = "getTransNumberOfBlock", method = "POST",
    requestBody = new RequestBody(description = "区块高度，最小为2", required = true,
      content = Array(new Content(mediaType = MediaType.APPLICATION_JSON, schema = new Schema(name = "height", description = "height, 最小为2", `type` = "string", example = "{\"height\":2}")))))
  //    @Parameters(Array(
  //      new Parameter(name = "height", description = "区块高度", required = true, schema = new Schema(`type` = String))))
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回指定高度区块包含的交易数", content =  Array(new Content(mediaType = "application/json",schema = new Schema(implementation = classOf[QueryResult])))))
  )
  def getTransNumberOfBlock =
    path("block" / "getTransNumberOfBlock") {
      post {
        entity(as[Map[String, Long]]) { blockQuery =>
          complete {
            (ra.getRestActor ? TransNumberOfBlock(blockQuery("height"))).mapTo[QueryResult]
          }
        }
      }
    }

  @GET
  @Path("/blocktime/{blockHeight}")
  @Operation(tags = Array("block"), summary  = "返回指定高度的区块的出块时间",description  = "getBlockTimeOfCreate", method = "GET")
  @Parameters(Array(
    new Parameter(name = "blockHeight", description = "区块高度, 最小为2", required = true, schema = new Schema(description = "height, 最小为2", `type` = "string"), in = ParameterIn.PATH, example = "2")))
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回指定高度的区块的出块时间", content =  Array(new Content(mediaType = "application/json",schema = new Schema(implementation = classOf[QueryResult])))))
  )
  def getBlockTimeOfCreate =
    path("block" / "blocktime" / Segment) { blockHeight =>
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get block time for Height,block height=${blockHeight}")
          complete { (ra.getRestActor ? BlockTimeForHeight(blockHeight.toLong)).mapTo[QueryResult] }
        }

        //complete { (ra ? BlockHeight(blockHeight.toInt)).mapTo[QueryResult] }
      }
    }

  @GET
  @Path("/blocktimeoftran/{transid}")
  @Operation(tags = Array("block"), summary  = "返回指定交易的入块时间", description  =  "getBlockTimeOfTransaction", method = "GET")
  @Parameters(Array(
    new Parameter(name = "transid", description = "交易id", required = true, schema = new Schema(`type` = "string"), in = ParameterIn.PATH)))
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回指定交易的入块时间", content =  Array(new Content(mediaType = "application/json",schema = new Schema(implementation = classOf[QueryResult])))))
  )
  def getBlockTimeOfTransaction =
    path("block" / "blocktimeoftran" / Segment) { transid =>
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get block time for txid,txid=${transid}")
          complete { (ra.getRestActor ? BlockTimeForTxid(transid)).mapTo[QueryResult] }
        }

        //complete { (ra ? BlockHeight(blockHeight.toInt)).mapTo[QueryResult] }
      }
    }


  @POST
  @Path("/blocktimeoftran")
  @Operation(tags = Array("block"), summary  = "返回指定交易的入块时间", description = "getBlockTimeOfTransaction", method = "POST",
    requestBody = new RequestBody(description = "交易id", required = true,
      content = Array(new Content(mediaType = MediaType.APPLICATION_JSON, schema = new Schema(name = "交易ID", description = "交易id", `type` = "string", example = "{\"txid\":\"8128801f-bb5e-4934-8fdb-0b89747bd2e6\"}")))))
  //  @Parameters(Array(
  //    new Parameter(name = "txid", value = "交易id", required = true, dataType = "String", paramType = "body")))
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回指定交易的入块时间", content =  Array(new Content(mediaType = "application/json",schema = new Schema(implementation = classOf[QueryResult])))))
  )
  def getBlockTimeOfTxrByTxid =
    path("block" / "blocktimeoftran") {
      post {
        entity(as[Map[String, String]]) { trans =>
          complete { (ra.getRestActor ? BlockTimeForTxid(trans("txid"))).mapTo[QueryResult] }
        }
      }
    }

  @GET
  @Path("/stream/{blockHeight}")
  @Operation(tags = Array("block"), summary  = "返回指定高度的区块字节流", description = "getBlockStreamByHeight", method = "GET")
  @Parameters(Array(
    new Parameter(name = "blockHeight", description = "区块高度", required = true, schema = new Schema(`type` = "integer", format = "int64"), in = ParameterIn.PATH, example = "1")))
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "blockbytes", content =  Array(new Content(mediaType = "application/octet-stream",schema = new Schema(implementation = classOf[Block]))))))
  def getBlockStreamByHeight =
    path("block" / "stream" / Segment) { blockHeight =>
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get block stream for Height,block height=${blockHeight}")
          complete((ra.getRestActor ? BlockHeightStream(blockHeight.toInt)).mapTo[HttpResponse])
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
class TransactionService(ra: RestRouter)(implicit executionContext: ExecutionContext)
  extends Directives {

  import akka.pattern.ask
  import scala.concurrent.duration._
  import java.io.FileInputStream

  implicit val timeout = Timeout(20.seconds)

  import Json4sSupport._
  import ScalaXmlSupport._
  import akka.stream.scaladsl.FileIO
  import akka.util.ByteString
  import java.nio.file.{ Paths, Files }
  import akka.stream.scaladsl.Framing

  implicit val serialization = jackson.Serialization // or native.Serialization
  implicit val formats = DefaultFormats

  implicit val specFormat = jsonFormat10(CSpec)
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
          (x \ "state").text.toBoolean)
    },
    //只能处理application/json
    unmarshaller[CSpec].forContentTypes(MediaTypes.`application/json`))

  val route = getTransaction ~ getTransactionStream ~ tranInfoAndHeightOfTranId ~ postSignTransaction ~ postTransaction ~ postSignTransactionStream

  @GET
  @Path("/{transactionId}")
  @Operation(tags = Array("transaction"), summary  = "返回指定id的交易", description= "getTransaction", method = "GET")
  @Parameters(Array(
    new Parameter(name = "transactionId", description = "交易id", required = false, schema = new Schema(`type` = "string"), in = ParameterIn.PATH)))
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回交易json内容", content =  Array(new Content(mediaType = "application/json",schema = new Schema(implementation = classOf[QueryResult])))))
  )
  def getTransaction =
    path("transaction" / Segment) { transactionId =>
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get transaction for txid,txid=${transactionId}")
          complete { (ra.getRestActor ? TransactionId(transactionId)).mapTo[QueryResult] }
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
    new ApiResponse(responseCode = "200", description = "返回交易字节流", content =  Array(new Content(mediaType = "application/octet-stream",schema = new Schema(implementation = classOf[Transaction], `type` = "string", format = "binary")))))
  )
  def getTransactionStream =
    path("transaction" / "stream" / Segment) { transactionId =>
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get transaction stream for txid,txid=${transactionId}")
          complete((ra.getRestActor ? TransactionStreamId(transactionId)).mapTo[HttpResponse])
        }
      }
    }

  @GET
  @Path("/tranInfoAndHeight/{transactionId}")
  @Operation(tags = Array("transaction"), summary = "返回指定id的交易信息及所在区块高度", description = "tranInfoAndHeightOfTranId", method = "GET")
  @Parameters(Array(
    new Parameter(name = "transactionId", description = "交易id", required = false, schema = new Schema(`type` = "string"), in = ParameterIn.PATH)))
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回指定id的交易信息及所在区块高度", content =  Array(new Content(mediaType = "application/json",schema = new Schema(implementation = classOf[QueryResult])))))
  )
  def tranInfoAndHeightOfTranId =
    path("transaction"/"tranInfoAndHeight"/Segment) { transactionId =>
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get transactionInfo and blockHeight for txid,txid=${transactionId}")
          complete((ra.getRestActor ? TranInfoAndHeightId(transactionId)).mapTo[QueryResult])
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
    new ApiResponse(responseCode = "200", description = "返回交易id以及执行结果", content =  Array(new Content(mediaType = "application/json",schema = new Schema(implementation = classOf[PostResult])))),
    new ApiResponse(responseCode = "202", description = "处理存在异常", content =  Array(new Content(mediaType = "application/json",schema = new Schema(implementation = classOf[PostResult])))))
  )
  def postSignTransaction =
    path("transaction" / "postTranByString") {
      post {
        entity(as[String]) { trans =>
          complete { (ra.getRestActor ? tranSign(trans)).mapTo[PostResult] }
          //          complete { (StatusCodes.Accepted, PostResult("hahhaha",None, Some("处理存在异常"))) }
        }
      }
    }

  case class SignedTransData(var signedTrans: File)
  //以字节流提交签名交易
  @POST
  @Path("/postTranStream")
  @Operation(tags = Array("transaction"), summary = "提交带签名的交易字节流", description  = "postSignTransactionStream", method = "POST",
    //    parameters = Array(new Parameter(name = "signedTrans", schema = new Schema(`type` = "string", format = "binary"), style = ParameterStyle.FORM, explode = Explode.TRUE))
    requestBody = new RequestBody(description = "签名交易的二进制文件", required = true,
      content = Array(new Content(mediaType = MediaType.MULTIPART_FORM_DATA, schema = new Schema(name = "signedTrans", implementation = classOf[SignedTransData])))
    )
  )
  //  @Parameter(name = "signedTrans", schema = new Schema(`type` = "string", format = "binary"), style = ParameterStyle.FORM, explode = Explode.TRUE)
  //  @ApiImplicitParams(Array(
  //    // new ApiImplicitParam(name = "signer", value = "签名者", required = true, dataType = "string", paramType = "formData"),
  //    new ApiImplicitParam(name = "signedTrans", value = "交易内容", required = true, dataType = "file", paramType = "formData")))
  @ApiResponses(Array(
    new ApiResponse(responseCode = "200", description = "返回交易id以及执行结果", content =  Array(new Content(mediaType = "application/json",schema = new Schema(implementation = classOf[PostResult])))),
    new ApiResponse(responseCode = "202", description = "处理存在异常", content =  Array(new Content(mediaType = "application/json",schema = new Schema(implementation = classOf[PostResult])))))
  )
  def postSignTransactionStream =
    path("transaction" / "postTranStream") {
      post {
        extractRequestContext { ctx =>
          implicit val materializer = ctx.materializer

          fileUpload("signedTrans") {
            case (fileInfo, fileStream) =>
              val sink = StreamConverters.asInputStream()
              val inputStream = fileStream.runWith(sink)
              complete { (ra.getRestActor ? Transaction.parseFrom(inputStream)).mapTo[PostResult] }
//              val fp = Paths.get("/tmp") resolve fileInfo.fileName
//              val sink = FileIO.toPath(fp)
//              val writeResult = fileStream.runWith(sink)
//              onSuccess(writeResult) { result =>
//                //TODO protobuf 反序列化字节流及后续处理
//                complete(s"Successfully written ${result.count} bytes")
//                complete { (ra.getRestActor ? Transaction.parseFrom(new FileInputStream(fp.toFile()))).mapTo[PostResult] }
//              }
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
    new ApiResponse(responseCode = "200", description = "返回交易id以及执行结果", content =  Array(new Content(mediaType = "application/json",schema = new Schema(implementation = classOf[PostResult])))),
    new ApiResponse(responseCode = "202", description = "处理存在异常", content =  Array(new Content(mediaType = "application/json",schema = new Schema(implementation = classOf[PostResult])))))
  )
  def postTransaction =
    path("transaction" / "postTran") {
      post {
        import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
        entity(as[CSpec]) { request =>
          complete { (ra.getRestActor ? request).mapTo[PostResult] }
        }
      }
    }
}

/**
 * DID resolver API
 *
 * @author jayTsang created
 */

@Tag(name = "did", description = "去中心化身份标识解析")
class DidService(ra: RestRouter)(implicit executionContext: ExecutionContext)
  extends Directives {

  import akka.pattern.ask
  import scala.concurrent.duration._

  implicit val timeout = Timeout(20.seconds)

  import Json4sSupport._

  implicit val serialization = jackson.Serialization // or native.Serialization
  implicit val formats = DefaultFormats

  val route = getDidDocument ~ getDidPubKey

  @GET
  @Path("/didDocuments/{did}")
  @Produces(Array(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN))
  @Operation(
    tags = Array("did"),
    summary = "根据did标识获取对应的did document信息",
    description = "resolve did", method = "GET",
    parameters = Array(new Parameter(
      name = "did",
      description = "did标识",
      required = true,
      in = ParameterIn.PATH)
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "返回对应did document信息",
        content = Array(new Content(
          mediaType = "application/json",
          schema = new Schema(implementation = classOf[DidDocument])
        ))
      ),
      new ApiResponse(
        responseCode = "400",
        description = "返回错误信息：请求中did格式错误",
        content = Array(new Content(
          mediaType = "application/text"
        ))
      ),
      new ApiResponse(
        responseCode = "404",
        description = "返回错误信息：did不存在",
        content = Array(new Content(
          mediaType = "application/text"
        ))
      )
    )
  )
  def getDidDocument =
    path("didDocuments" / Segment) { did =>
      get {
        extractClientIP { ip =>
          RepLogger.debug(
            RepLogger.APIAccess_Logger,
            s"remoteAddr=${ip} get did document for did: ${did}"
          )
          // 原始的did字符串下，DidDocumentReq(did)被JSON序列化会出现错误
          val didAlteredStr = s""""${did}""""
          complete {
            (ra.getRestActor ? DidDocumentReq(didAlteredStr)).mapTo[HttpResponse]
          }
        }
      }
    }

  @GET
  @Path("/didPubKeys/{pubKeyId}")
  @Produces(Array(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN))
  @Operation(
    tags = Array("did"),
    summary = "根据did公钥标识返回公钥信息",
    description = "resolve did PubKey", method = "GET",
    parameters = Array(new Parameter(
      name = "pubKeyId",
      description = "did公钥标识",
      required = true,
      in = ParameterIn.PATH)
    ),
    responses = Array(
      new ApiResponse(
        responseCode = "200",
        description = "返回对应did PubKey信息",
        content = Array(new Content(
          mediaType = "application/json",
          schema = new Schema(implementation = classOf[DidPubKey])
        ))
      ),
      new ApiResponse(
        responseCode = "400",
        description = "返回错误信息：请求格式错误",
        content = Array(new Content(
          mediaType = "application/text"
        ))
      ),
      new ApiResponse(
        responseCode = "404",
        description = "返回错误信息：did PubKey不存在",
        content = Array(new Content(
          mediaType = "application/text"
        ))
      )
    )
  )
  def getDidPubKey =
    path("didPubKeys" / Segment) { pubKeyId =>
      get {
        extractClientIP { ip =>
          RepLogger.debug(
            RepLogger.APIAccess_Logger,
            s"remoteAddr=${ip} get did PubKey for pubKeyId: ${pubKeyId}"
          )
          // 原始的pubKeyId字符串下，DidPubKeyReq(did)被JSON序列化会出现错误
          val pubKeyIdAlteredStr = s""""${pubKeyId}""""
          complete {
            (ra.getRestActor ? DidPubKeyReq(pubKeyIdAlteredStr)).mapTo[HttpResponse]
          }
        }
      }
    }
}

/**
  * levelDB相关操作
  *
  * @author zyf
  */
@Tag(name = "leveldb", description = "查询合约存储在levelDB中的数据")
@Path("/leveldb")
class LevelDbService(ra: RestRouter)(implicit executionContext: ExecutionContext)
  extends Directives {

  import akka.pattern.ask
  import scala.concurrent.duration._
  import Json4sSupport._

  implicit val serialization = jackson.Serialization // or native.Serialization
  implicit val formats = DefaultFormats
  implicit val timeout = Timeout(20.seconds)

  val route = queryLevelDB

  @POST
  @Path("/query")
  @Operation(tags = Array("leveldb"), summary = "查询合约存储在levelDB中的数据", description = "queryLevelDB", method = "POST",
    requestBody = new RequestBody(description = "合约名，以及对应的key", required = true,
      content = Array(new Content(mediaType = MediaType.APPLICATION_JSON, schema = new Schema(implementation = classOf[QueryLevelDB])))))
  @ApiResponse(responseCode = "200", description = "返回对应于某个key的value", content = Array(new Content(mediaType = "application/json",schema = new Schema(implementation = classOf[QueryResult]))))
  def queryLevelDB =
    path("leveldb" / "query") {
      post {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} query levelDB")
          entity(as[QueryLevelDB]) { query: QueryLevelDB =>
            complete {
              (ra.getRestActor ? query).mapTo[QueryResult]
            }
          }
        }
      }
    }
}