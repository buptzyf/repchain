/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
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

package rep.sc

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import org.json4s.{DefaultFormats, jackson}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType
import rep.network.PeerHelper.transactionCreator
import rep.network.module.ModuleManager
import rep.sc.Sandbox.DoTransactionResult
import rep.sc.TransProcessor.DoTransaction
import rep.storage.ImpDataAccess
import rep.utils.Json4s.compactJson
import org.json4s.native.Serialization.{writePretty,write}

import scala.concurrent.Await
import scala.concurrent.duration._


object PropertySpec {

  case class propertyData(hash: String, certId: String)

  case class retrievalData(hash: String, userId: String)

  case class certData(certPem: String, userInfo: String)

  case class certInfo(id: String, iphone: String, email: String)

  object ACTION {
    val propertyProof = "propertyProof"
    val propertyRetrieval = "propertyRetrieval"
    val signUp = "signUp"
    val destroyCert = "destroyCert"
  }
}

class PropertySpec(_system: ActorSystem)
  extends TestKit(_system) with Matchers with FlatSpecLike with BeforeAndAfterAll{

  def this() = this(ActorSystem("PropertySpec", new ClusterSystem("1", InitType.MULTI_INIT, false).getConf))
  override def afterAll: Unit = Await.ready(system.terminate(), Duration.Inf)

  implicit val serialization = jackson.Serialization
  // or native.Serialization
  implicit val formats = DefaultFormats

  "PropertySpec" should "can save hash and retrieval" in {

    import PropertySpec._

    val sysName = "1"

    //建立PeerManager实例是为了调用transactionCreator(需要用到密钥签名)，无他
    val pm = system.actorOf(ModuleManager.props("pm", sysName))

    //加载合约脚本
    val s1 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/PropertyTPL.scala")
    val l1 = try s1.mkString finally s1.close()

    //准备探针以验证调用返回结果
    val probe = TestProbe()
    val sandbox = system.actorOf(TransProcessor.props("sandbox", "", probe.ref))

    //生成deploy交易
    val t1 = transactionCreator(sysName, rep.protos.peer.Transaction.Type.CHAINCODE_DEPLOY,
      "", "", List(), l1, None, rep.protos.peer.ChaincodeSpec.CodeType.CODE_SCALA)
    val msg_send1 = DoTransaction(t1, probe.ref, "")
    probe.send(sandbox, msg_send1)
    val msg_recv1 = probe.expectMsgType[DoTransactionResult](1000.seconds)
    val ol1 = msg_recv1.ol
    val ol1str = compactJson(ol1)

    val cname = t1.payload.get.chaincodeID.get.name

    //生成invoke交易
    //获取deploy生成的chainCodeId
    // 存证
    val saveData = propertyData("hash_123","propertyId_123")
    val l2 = write(saveData)
    val t2 = transactionCreator(sysName, rep.protos.peer.Transaction.Type.CHAINCODE_INVOKE,
      "", ACTION.propertyProof, Seq(l2), "", Option(cname))
    val msg_send2 = DoTransaction(t2, probe.ref, "")
    probe.send(sandbox, msg_send2)
    val msg_recv2 = probe.expectMsgType[DoTransactionResult](1000.seconds)
    println(msg_recv2)
    msg_recv2.r.extract[String] should be("propertyProof ok")

    // 检索
    val retrievaldata = retrievalData("hash_123", "certId_456")
    val l3 = write(retrievaldata)
    val t3 = transactionCreator(sysName, rep.protos.peer.Transaction.Type.CHAINCODE_INVOKE,
      "", ACTION.propertyRetrieval, Seq(l3), "", Option(cname))
    val msg_send3 = DoTransaction(t3, probe.ref, "")
    probe.send(sandbox, msg_send3)
    val msg_recv3 = probe.expectMsgType[DoTransactionResult](1000.seconds)
    println(msg_recv3)
    msg_recv3.r.extract[String] should be ("retrieval ok")

    // 注册证书
    val cert =
      """
        |-----BEGIN CERTIFICATE-----
        |MIIBmjCCAT+gAwIBAgIEWWV+AzAKBggqhkjOPQQDAjBWMQswCQYDVQQGEwJjbjEL
        |MAkGA1UECAwCYmoxCzAJBgNVBAcMAmJqMREwDwYDVQQKDAhyZXBjaGFpbjEOMAwG
        |A1UECwwFaXNjYXMxCjAIBgNVBAMMATEwHhcNMTcwNzEyMDE0MjE1WhcNMTgwNzEy
        |MDE0MjE1WjBWMQswCQYDVQQGEwJjbjELMAkGA1UECAwCYmoxCzAJBgNVBAcMAmJq
        |MREwDwYDVQQKDAhyZXBjaGFpbjEOMAwGA1UECwwFaXNjYXMxCjAIBgNVBAMMATEw
        |VjAQBgcqhkjOPQIBBgUrgQQACgNCAAT6VLE/eF9+sK1ROn8n6x7hKsBxehW42qf1
        |IB8quBn5OrQD3x2H4yZVDwPgcEUCjH8PcFgswdtbo8JL/7f66yECMAoGCCqGSM49
        |BAMCA0kAMEYCIQCud+4/3njnfUkG9ffSqcHhnsuZNMQwaW62EVXbcjoiBgIhAPoL
        |JK1D06IMoholYcsgTQb5Trrej/erZONMm1cS1iP+
        |-----END CERTIFICATE-----
      """.stripMargin
    val certInfoData = certInfo("1402222222","13150462988","test@163.com")
    val l4 = writePretty(certData(cert,write(certInfoData)))
    val t4 = transactionCreator(sysName, rep.protos.peer.Transaction.Type.CHAINCODE_INVOKE,
      "", ACTION.signUp, Seq(l4), "", Option(cname))
    val msg_send4 = DoTransaction(t4, probe.ref, "")
    probe.send(sandbox, msg_send4)
    val msg_recv4 = probe.expectMsgType[DoTransactionResult](1000.seconds)
    println(msg_recv4)
    msg_recv4.r.extract[String] should equal("1Luv5vq4v1CRkTN98YMhqQV1F18nGv11gX")

    // 注销证书
    val addr = msg_recv4.r.extract[String]
    val l5 = write(addr)
    val t5 = transactionCreator(sysName, rep.protos.peer.Transaction.Type.CHAINCODE_INVOKE,
      "", ACTION.destroyCert, Seq(l5), "", Option(cname))
    val msg_send5 = DoTransaction(t5, probe.ref, "")
    probe.send(sandbox, msg_send5)
    val msg_recv5 = probe.expectMsgType[DoTransactionResult](1000.seconds)
    println(msg_recv5)
    msg_recv5.r.extract[String] should be ("destroy cert")

    // 可以重新注册
    val t6 = transactionCreator(sysName, rep.protos.peer.Transaction.Type.CHAINCODE_INVOKE,
      "", ACTION.signUp, Seq(l4), "", Option(cname))
    val msg_send6 = DoTransaction(t6, probe.ref, "")
    probe.send(sandbox, msg_send6)
    val msg_recv6 = probe.expectMsgType[DoTransactionResult](1000.seconds)
    println(msg_recv6)
    msg_recv6.r.extract[String] should equal("1Luv5vq4v1CRkTN98YMhqQV1F18nGv11gX")
  }

}
