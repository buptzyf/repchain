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

import scala.concurrent.{ ExecutionContext, Future }
import akka.actor.{ ActorRef, ActorSelection }
import akka.util.Timeout
import akka.http.scaladsl.model.Uri.Path.Segment
import akka.http.scaladsl.server.Directives
import io.swagger.annotations._
import javax.ws.rs.Path
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
import org.json4s.{ DefaultFormats, Formats, jackson }
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport
import akka.http.scaladsl.model.{ ContentTypes, HttpCharsets, MediaTypes }
import akka.http.scaladsl.unmarshalling.{ FromEntityUnmarshaller, Unmarshaller }

import scala.xml.NodeSeq
import rep.log.RepLogger

/**
 * 获得区块链的概要信息
 *
 * @author c4w
 */
@Api(value = "/chaininfo", description = "获得当前区块链信息", produces = "application/json")
@Path("chaininfo")
class ChainService(ra: ActorRef)(implicit executionContext: ExecutionContext)
  extends Directives {

  import akka.pattern.ask
  import scala.concurrent.duration._

  import Json4sSupport._

  implicit val serialization = jackson.Serialization // or native.Serialization
  implicit val formats = DefaultFormats
  implicit val timeout = Timeout(20.seconds)

  val route = getBlockChainInfo ~ getNodeNumber ~ getCacheTransNumber ~ getAcceptedTransNumber

  @ApiOperation(value = "返回块链信息", notes = "", nickname = "getChainInfo", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "返回块链信息", response = classOf[QueryResult])))
  def getBlockChainInfo =
    path("chaininfo") {
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get chaininfo")
          complete { (ra ? ChainInfo).mapTo[QueryResult] }
        }
      }
    }

  @Path("/node")
  @ApiOperation(value = "返回组网节点数量", notes = "", nickname = "getNodeNumber", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "返回组网节点数量", response = classOf[QueryResult])))
  def getNodeNumber =
    path("chaininfo" / "node") {
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get node number")
          complete { (ra ? NodeNumber).mapTo[QueryResult] }
        }
      }
    }

  @Path("/getcachetransnumber")
  @ApiOperation(value = "返回系统缓存交易数量", notes = "", nickname = "getCacheTransNumber", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "返回系统缓存交易数量", response = classOf[QueryResult])))
  def getCacheTransNumber =
    path("chaininfo" / "getcachetransnumber") {
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get number of cache")
          complete { (ra ? TransNumber).mapTo[QueryResult] }
        }
      }
    }

  @Path("/getAcceptedTransNumber")
  @ApiOperation(value = "返回系统接收到的交易数量", notes = "", nickname = "getAcceptedTransNumber", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "返回系统接收到的交易数量", response = classOf[QueryResult])))
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

@Api(value = "/block", description = "获得区块数据", produces = "application/json")
@Path("block")
class BlockService(ra: ActorRef)(implicit executionContext: ExecutionContext)
  extends Directives {

  import akka.pattern.ask
  import scala.concurrent.duration._

  implicit val timeout = Timeout(20.seconds)

  import Json4sSupport._

  implicit val serialization = jackson.Serialization // or native.Serialization
  implicit val formats = DefaultFormats

  val route = getBlockById ~ getBlockByHeight ~ getBlockByHeightToo ~ getTransNumberOfBlock ~ getBlockStreamByHeight ~ getBlockTimeOfCreate ~ getBlockTimeOfTxrByTxid ~ getBlockTimeOfTransaction

  @Path("/hash/{blockId}")
  @ApiOperation(value = "返回指定id的区块", notes = "", nickname = "getBlockById", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "blockId", value = "区块id", required = true, dataType = "string", paramType = "path")))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "返回区块json内容", response = classOf[QueryResult])))
  def getBlockById =
    path("block" / "hash" / Segment) { blockId =>
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get block for id,block id=${blockId}")
          complete { (ra ? BlockId(blockId)).mapTo[QueryResult] }
        }
      }
    }

  @Path("/{blockHeight}")
  @ApiOperation(value = "返回指定高度的区块", notes = "", nickname = "getBlockByHeight", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "blockHeight", value = "区块高度", required = true, dataType = "int", paramType = "path")))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "返回区块json内容", response = classOf[QueryResult])))
  def getBlockByHeightToo =
    path("block" / Segment) { blockHeight =>
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get block for Height,block height=${blockHeight}")
          complete { (ra ? BlockHeight(blockHeight.toInt)).mapTo[QueryResult] }
        }

        //complete { (ra ? BlockHeight(blockHeight.toInt)).mapTo[QueryResult] }
      }
    }

  @Path("/blockHeight")
  @ApiOperation(value = "返回指定高度的区块", notes = "", nickname = "getBlockByHeight", httpMethod = "POST")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "height", value = "区块高度", required = true, dataType = "String", paramType = "body")))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "返回区块json内容", response = classOf[QueryResult])))
  def getBlockByHeight =
    path("block" / "blockHeight") {
      post {
        entity(as[Map[String, Int]]) { blockQuery =>
          complete {
            (ra ? BlockHeight(blockQuery("height"))).mapTo[QueryResult]
          }
        }
      }
    }

  @Path("/getTransNumberOfBlock")
  @ApiOperation(value = "返回指定高度区块包含的交易数", notes = "", nickname = "getTransNumberOfBlock", httpMethod = "POST")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "height", value = "区块高度", required = true, dataType = "String", paramType = "body")))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "返回指定高度区块包含的交易数", response = classOf[QueryResult])))
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

  @Path("/blocktime/{blockHeight}")
  @ApiOperation(value = "返回指定高度的区块的出块时间", notes = "", nickname = "getBlockTimeOfCreate", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "blockHeight", value = "区块高度", required = true, dataType = "long", paramType = "path")))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "返回指定高度的区块的出块时间", response = classOf[QueryResult])))
  def getBlockTimeOfCreate =
    path("block" / "blocktime" / Segment) { blockHeight =>
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get block time for Height,block height=${blockHeight}")
          complete { (ra ? BlockTimeForHeight(blockHeight.toLong)).mapTo[QueryResult] }
        }

        //complete { (ra ? BlockHeight(blockHeight.toInt)).mapTo[QueryResult] }
      }
    }

  @Path("/blocktimeoftran/{transid}")
  @ApiOperation(value = "返回指定交易的入块时间", notes = "", nickname = "getBlockTimeOfTransaction", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "transid", value = "交易id", required = true, dataType = "String", paramType = "path")))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "返回指定交易的入块时间", response = classOf[QueryResult])))
  def getBlockTimeOfTransaction =
    path("block" / "blocktimeoftran" / Segment) { transid =>
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get block time for txid,txid=${transid}")
          complete { (ra ? BlockTimeForTxid(transid)).mapTo[QueryResult] }
        }

        //complete { (ra ? BlockHeight(blockHeight.toInt)).mapTo[QueryResult] }
      }
    }

  @Path("/blocktimeoftran")
  @ApiOperation(value = "返回指定交易的入块时间", notes = "", nickname = "getBlockTimeOfTransaction", httpMethod = "POST")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "txid", value = "交易id", required = true, dataType = "String", paramType = "body")))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "返回指定交易的入块时间", response = classOf[QueryResult])))
  def getBlockTimeOfTxrByTxid =
    path("block" / "blocktimeoftran") {
      post {
        entity(as[Map[String, String]]) { trans =>
          complete { (ra ? BlockTimeForTxid(trans("txid"))).mapTo[QueryResult] }
        }
      }
    }

  @Path("/stream/{blockHeight}")
  @ApiOperation(value = "返回指定高度的区块字节流", notes = "", nickname = "getBlockStreamByHeight", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "blockHeight", value = "区块高度", required = true, dataType = "int", paramType = "path")))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "blockbytes")))
  def getBlockStreamByHeight =
    path("block" / "stream" / Segment) { blockHeight =>
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get block stream for Height,block height=${blockHeight}")
          complete((ra ? BlockHeightStream(blockHeight.toInt)).mapTo[HttpResponse])
        }
      }
    }
}

/**
 * 获得指定交易的详细信息，提交签名交易
 *
 * @author c4w
 */
@Api(value = "/transaction", description = "获得交易数据", consumes = "application/json,application/xml", produces = "application/json,application/xml")
@Path("transaction")
class TransactionService(ra: ActorRef)(implicit executionContext: ExecutionContext)
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

  val route = getTransaction ~ getTransactionStream ~ postSignTransaction ~ postTransaction ~ postSignTransactionStream

  @Path("/{transactionId}")
  @ApiOperation(value = "返回指定id的交易", notes = "", nickname = "getTransaction", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "transactionId", value = "交易id", required = false, dataType = "string", paramType = "path")))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "返回交易json内容", response = classOf[QueryResult])))
  def getTransaction =
    path("transaction" / Segment) { transactionId =>
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get transaction for txid,txid=${transactionId}")
          complete { (ra ? TransactionId(transactionId)).mapTo[QueryResult] }
        }
      }
    }

  @Path("/stream/{transactionId}")
  @ApiOperation(value = "返回指定id的交易字节流", notes = "", nickname = "getTransactionStream", httpMethod = "GET", produces = "application/octet-stream")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "transactionId", value = "交易id", required = false, dataType = "string", paramType = "path")))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "返回交易字节流", response = classOf[QueryResult])))
  def getTransactionStream =
    path("transaction" / "stream" / Segment) { transactionId =>
      get {
        extractClientIP { ip =>
          RepLogger.debug(RepLogger.APIAccess_Logger, s"remoteAddr=${ip} get transaction stream for txid,txid=${transactionId}")
          complete((ra ? TransactionStreamId(transactionId)).mapTo[HttpResponse])
        }
      }
    }

  //以十六进制字符串提交签名交易
  @Path("/postTranByString")
  @ApiOperation(value = "提交带签名的交易", notes = "", nickname = "postSignTransaction", httpMethod = "POST")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "body", value = "交易内容", required = true, dataType = "string", paramType = "body")))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "返回交易id以及执行结果", response = classOf[PostResult]),
    new ApiResponse(code = 202, message = "处理存在异常", response = classOf[PostResult])))
  def postSignTransaction =
    path("transaction" / "postTranByString") {
      post {
        entity(as[String]) { trans =>
          complete { (ra ? tranSign(trans)).mapTo[PostResult] }
        }
      }
    }

  //以字节流提交签名交易
  @Path("/postTranStream")
  @ApiOperation(value = "提交带签名的交易字节流", notes = "", consumes = "multipart/form-data", nickname = "postSignTransactionStream", httpMethod = "POST")
  @ApiImplicitParams(Array(
    // new ApiImplicitParam(name = "signer", value = "签名者", required = true, dataType = "string", paramType = "formData"),
    new ApiImplicitParam(name = "signedTrans", value = "交易内容", required = true, dataType = "file", paramType = "formData")))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "返回交易id以及执行结果", response = classOf[PostResult]),
    new ApiResponse(code = 202, message = "处理存在异常", response = classOf[PostResult])))
  def postSignTransactionStream =
    path("transaction" / "postTranStream") {
      post {
        extractRequestContext { ctx =>
          implicit val materializer = ctx.materializer

          fileUpload("signedTrans") {
            case (fileInfo, fileStream) =>
              val fp = Paths.get("/tmp") resolve fileInfo.fileName
              val sink = FileIO.toPath(fp)
              val writeResult = fileStream.runWith(sink)
              onSuccess(writeResult) { result =>
                //TODO protobuf 反序列化字节流及后续处理
                complete(s"Successfully written ${result.count} bytes")
                complete { (ra ? Transaction.parseFrom(new FileInputStream(fp.toFile()))).mapTo[PostResult] }
              }
          }
        }
      }
    }

  @Path("/postTran")
  @ApiOperation(value = "提交交易", notes = "", nickname = "postTransaction", httpMethod = "POST")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "body", value = "交易内容", required = true,
      dataTypeClass = classOf[CSpec], paramType = "body")))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "返回交易id以及执行结果", response = classOf[PostResult]),
    new ApiResponse(code = 202, message = "处理存在异常", response = classOf[PostResult])))
  def postTransaction =
    path("transaction" / "postTran") {
      post {
        import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
        entity(as[CSpec]) { request =>
          complete { (ra ? request).mapTo[PostResult] }
        }
      }
    }
}