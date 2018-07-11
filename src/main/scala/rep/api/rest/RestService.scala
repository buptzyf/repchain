/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Fintech Research Center of ISCAS.
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
import akka.http.scaladsl.model.{ContentTypes, HttpCharsets, MediaTypes}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}

import scala.xml.NodeSeq


/** 获得区块链的概要信息
 *  @author c4w
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

  val route = getBlockChainInfo

  @ApiOperation(value = "返回块链信息", notes = "", nickname = "getChainInfo", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "返回块链信息", response = classOf[QueryResult])))
  def getBlockChainInfo =
    path("chaininfo") {
      get {
        complete { (ra ? ChainInfo).mapTo[QueryResult] }
      }
    }
}

/** 获得指定区块的详细信息
 *  @author c4w
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

  val route = getBlockById ~ getBlockByHeight

  @Path("/hash/{blockId}")
  @ApiOperation(value = "返回指定id的区块", notes = "", nickname = "getBlockById", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "blockId", value = "区块id", required = true, dataType = "string", paramType = "path")))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "返回区块json内容", response = classOf[QueryResult])))
  def getBlockById =
    path("block" / "hash" / Segment) { blockId =>
      get {
        complete { (ra ? BlockId(blockId)).mapTo[QueryResult] }
      }
    }

  @Path("/{blockHeight}")
  @ApiOperation(value = "返回指定高度的区块", notes = "", nickname = "getBlockByHeight", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "blockHeight", value = "区块高度", required = true, dataType = "int", paramType = "path")))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "返回区块json内容", response = classOf[QueryResult])))
  def getBlockByHeight =
    path("block" / Segment) { blockHeight =>
      get {
        complete { (ra ? BlockHeight(blockHeight.toInt)).mapTo[QueryResult] }
      }
    }

}

/** 获得指定交易的详细信息，提交签名交易
 *  @author c4w
 */

@Api(value = "/transaction", description = "获得交易数据", consumes = "application/json,application/xml", produces = "application/json,application/xml")
@Path("transaction")
class TransactionService(ra: ActorRef)(implicit executionContext: ExecutionContext)
  extends Directives {

  import akka.pattern.ask
  import scala.concurrent.duration._

  implicit val timeout = Timeout(20.seconds)
  import Json4sSupport._
  import ScalaXmlSupport._

  implicit val serialization = jackson.Serialization // or native.Serialization
  implicit val formats = DefaultFormats
  
  implicit val specFormat = jsonFormat9(CSpec)
  implicit val specUnmarshaller: FromEntityUnmarshaller[CSpec] = Unmarshaller.firstOf(
    //只能处理application/xml
    nodeSeqUnmarshaller(MediaTypes.`application/xml` withCharset HttpCharsets.`UTF-8`) map {
      case NodeSeq.Empty => 
        throw Unmarshaller.NoContentException
      case x =>
        CSpec(
          (x \ "stype").text.toInt,
          (x \ "idPath").text,
          Some((x \ "idName").text),
          (x \ "iptFunc").text,
          Seq((x \ "iptArgs").text),
          (x \ "timeout").text.toInt,
          (x \ "secureContext").text,
          (x \ "code").text,
          (x \ "ctype").text.toInt
        )
    },    
    //只能处理application/json
    unmarshaller[CSpec].forContentTypes(MediaTypes.`application/json`)   
  ) 

  val route = getTransaction ~ postSignTransaction ~ postTransaction

  @Path("/{transactionId}")
  @ApiOperation(value = "返回指定id的交易", notes = "", nickname = "getTransaction", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "transactionId", value = "交易id", required = false, dataType = "string", paramType = "path")))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "返回交易json内容", response = classOf[QueryResult])))
  def getTransaction =
    path("transaction" / Segment) { transactionId =>
      get {
        complete { (ra ? TransactionId(transactionId)).mapTo[QueryResult] }
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
          //str=>
          //complete(OK, trans) 
          complete { (ra ? tranSign(trans)).mapTo[PostResult] }
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

@Api(value = "/certAddr", description = "获得证书短地址", produces = "application/json")
@Path("certAddr")
class CertService(ra: ActorRef)(implicit executionContext: ExecutionContext)
  extends Directives {

  import akka.pattern.ask
  import scala.concurrent.duration._

  import Json4sSupport._
  implicit val serialization = jackson.Serialization // or native.Serialization
  implicit val formats = DefaultFormats
  implicit val timeout = Timeout(20.seconds)

  val route = getAddrByCert ~ getCertByAddr
  
  @Path("/getAddrByCert")
  @ApiOperation(value = "返回证书短地址", notes = "", nickname = "getAddrByCert", httpMethod = "POST")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "body", value = "证书", required = true, dataTypeClass = classOf[PostCert], paramType = "body")))
  @ApiResponses(Array(
  new ApiResponse(code = 200, message = "查询证书短地址", response = classOf[QueryAddr])))
  def getAddrByCert =
    path("certAddr" / "getAddrByCert") {
      post {
         entity(as[PostCert]) { PostCert =>
          complete { (ra ? PostCert).mapTo[QueryAddr] }
        }
      }
    }

  @Path("/getCertByAddr")
  @ApiOperation(value = "返回证书字符串", notes = "", nickname = "getCertByAddr", httpMethod = "POST")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "body", value = "短地址", required = true, dataTypeClass = classOf[PostAddr], paramType = "body")))
  @ApiResponses(Array(
  new ApiResponse(code = 200, message = "查询证书字符串", response = classOf[QueryCert])))
    def getCertByAddr =
    path("certAddr" / "getCertByAddr") {
      post {
         entity(as[PostAddr]) { PostAddr =>
          complete { (ra ? PostAddr).mapTo[QueryCert] }
        }
      }
    }
  }


@Api(value = "/hash", description = "验证hash是否存在", produces = "application/json")
@Path("hash")
class HashVerifyService(ra: ActorRef)(implicit executionContext: ExecutionContext)
  extends Directives {

  import akka.pattern.ask
  import scala.concurrent.duration._

  import Json4sSupport._
  implicit val serialization = jackson.Serialization // or native.Serialization
  implicit val formats = DefaultFormats
  implicit val timeout = Timeout(20.seconds)

  val route = verifyImageHash
  
  @Path("/verifyHash")
  @ApiOperation(value = "返回hash是否存在", notes = "", nickname = "verifyHash", httpMethod = "POST")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "body", value = "hash值与cid", required = true, dataTypeClass = classOf[PostHash], paramType = "body")))
  @ApiResponses(Array(new ApiResponse(code = 200, message = "验证hash值", response = classOf[QueryHash])))
  def verifyImageHash =
    path("hash" / "verifyHash") {
      post {
         entity(as[PostHash]) { PostHash =>
          complete { (ra ? PostHash).mapTo[QueryHash] }
        }
      }
    }
  }