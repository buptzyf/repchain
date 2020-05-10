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

package rep.sc

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestKit, TestProbe}
import org.json4s.jackson.Serialization
import org.json4s.native.Serialization.write
import org.json4s.{DefaultFormats, jackson}
import org.scalatest._
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType
import rep.network.autotransaction.PeerHelper
import rep.network.module.cfrd.ModuleManagerOfCFRD
import rep.protos.peer._
import rep.sc.tpl.did.operation.SignerOperation
import rep.sc.tpl.did.operation.SignerOperation.SignerStatus
import scalapb.json4s.JsonFormat
//.{CertStatus,CertInfo}

import rep.sc.SandboxDispatcher.DoTransaction

import scala.collection.mutable
import scala.concurrent.duration._
import scala.io.BufferedSource


/**
  * @author zyf
  * @param _system
  */
class RdidOperateAuthorizeSpec(_system: ActorSystem) extends TestKit(_system) with Matchers with FunSuiteLike with BeforeAndAfterAll {

  def this() = this(ActorSystem("TransferSpec", new ClusterSystem("121000005l35120456.node1", InitType.MULTI_INIT, false).getConf))

  override def afterAll: Unit = {
    shutdown(system)
  }

  implicit val serialization: Serialization.type = jackson.Serialization
  // or native.Serialization
  implicit val formats: DefaultFormats.type = DefaultFormats

  val sysName = "121000005l35120456.node1"
  val dbTag = "121000005l35120456.node1"
  val cid = ChaincodeId("RdidOperateAuthorizeTPL", 1)
  //建立PeerManager实例是为了调用transactionCreator(需要用到密钥签名)，无他
  val pm: ActorRef = system.actorOf(ModuleManagerOfCFRD.props("moduleManager", sysName, enableStatistic = false, enableWebSocket = false, isStartup = false), "moduleManager")

  val certNode1: BufferedSource = scala.io.Source.fromFile("jks/certs/121000005l35120456.node1.cer")
  val certStr1: String = try certNode1.mkString finally certNode1.close()
  val certNode2: BufferedSource = scala.io.Source.fromFile("jks/certs/12110107bi45jh675g.node2.cer")
  val certStr2: String = try certNode2.mkString finally certNode2.close()
  val certNode3: BufferedSource = scala.io.Source.fromFile("jks/certs/122000002n00123567.node3.cer")
  val certStr3: String = try certNode3.mkString finally certNode3.close()
  val certNode4: BufferedSource = scala.io.Source.fromFile("jks/certs/921000005k36123789.node4.cer")
  val certStr4: String = try certNode4.mkString finally certNode4.close()
  val certNode5: BufferedSource = scala.io.Source.fromFile("jks/certs/921000006e0012v696.node5.cer")
  val certStr5: String = try certNode5.mkString finally certNode5.close()
  val certs: mutable.Map[String, String] = mutable.Map("node1" -> certStr1, "node2" -> certStr2, "node3" -> certStr3, "node4" -> certStr4, "node5" -> certStr5)

  val node1Cert1 = Certificate(certStr1, "SHA256withECDSA", certValid = true, None, None, certType = Certificate.CertType.CERT_AUTHENTICATION, Some(CertId("121000005l35120456", "node1Cert1", "1")), "hash12345", "1")
  val node1Cert2 = Certificate(certStr2, "SHA256withECDSA", certValid = true, None, None, Certificate.CertType.CERT_CUSTOM, Some(CertId("121000005l35120456", "node1Cert1", "1")), "hash54321", "1")
  val node1Cert3 = Certificate(certStr2, "SHA256withECDSA", certValid = true, None, None, certType = Certificate.CertType.CERT_AUTHENTICATION, Some(CertId("121000005l35120456", "node1Cert1", "1")), "hash12345", "1")
  val node1Cert4 = Certificate(certStr2, "SHA256withECDSA", certValid = true, None, None, certType = Certificate.CertType.CERT_AUTHENTICATION, Some(CertId("121000005l3512v587", "node1Cert1", "1")), "hash54321", "1")
  val node1Cert5 = Certificate(certStr2, "SHA256withECDSA", certValid = true, None, None, certType = Certificate.CertType.CERT_AUTHENTICATION, Some(CertId("121000005l3512v123", "node1Cert1", "1")), "hash54322", "1")

  // 只有AuthCert
  val node1AuthCerts1 = List(node1Cert1)
  // 包含有customCert
  val node1AuthCerts2 = Seq(node1Cert1, node1Cert2)
  // 两个同样的AuthCert证书
  val node1AuthCerts3 = Seq(node1Cert1, node1Cert1)
  // signer 与 cert 的 creditCode 不一致
  val node1AuthCerts4 = Seq(node1Cert4, node1Cert5)

  val signers: Array[Signer] = Array(
    Signer("node1", "121000005l35120456", "18912345678", Seq.empty, Seq.empty, Seq.empty, Seq.empty, node1AuthCerts1, "", None, None, signerValid = true, "1"),
    Signer("node1", "121000005l35120456", "18912345678", Seq.empty, Seq.empty, Seq.empty, Seq.empty, node1AuthCerts1, "", None, None, signerValid = true, "1"),
    Signer("node2", "12110107bi45jh675g", "18912345678", certNames = List("node1"), Seq.empty, Seq.empty, Seq.empty, node1AuthCerts1, "", None, None, signerValid = true, "1"),
    Signer("node3", "122000002n00123567", "18912345678", Seq.empty, Seq.empty, operateIds = Seq("operTest123"), Seq.empty, node1AuthCerts1, "", None, None, signerValid = true, "1"),
    Signer("node4", "921000005k36123789", "18912345678", Seq.empty, Seq.empty, Seq.empty, Seq.empty, node1AuthCerts2, "", None, None, signerValid = true, "1"),
    Signer("node5", "921000006e0012v696", "18912345678", Seq.empty, Seq.empty, Seq.empty, Seq.empty, node1AuthCerts3, "", None, None, signerValid = true, "1"),
    Signer("node1", "121000005l3512v587", "18912345678", Seq.empty, Seq.empty, Seq.empty, Seq.empty, node1AuthCerts4, "", None, None, signerValid = true, "1"),
    //    Signer("super_admin", "951002007l78123233", "18912345678", List("super_admin"))
  )

  //准备探针以验证调用返回结果
  val probe = TestProbe()
  private val sandbox = system.actorOf(TransactionDispatcher.props("transactiondispatcher"), "transactiondispatcher")

  // 部署合约
  test("Deploy RdidOperateAuthorizeTPL") {
    // 部署账户管理合约
    val contractCert = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/did/RdidOperateAuthorizeTPL.scala")
    val contractCertStr = try contractCert.mkString finally contractCert.close()
    val t = PeerHelper.createTransaction4Deploy(sysName, cid, contractCertStr, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA)
    val msg_send = DoTransaction(t, "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    assert(msg_recv.err.isEmpty)
  }

  test("注册账户-node1") {
    val signerNode1 = signers(0)
    val t = PeerHelper.createTransaction4Invoke(sysName, cid, chaincodeInputFunc = "signUpSigner", params = Seq(JsonFormat.toJsonString(signerNode1)))
    val msg_send = DoTransaction(t, "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    msg_recv.err.isEmpty should be(true)
  }

  test("注册账户-node1，账户已存在") {
    val signerNode1 = signers(1)
    val t = PeerHelper.createTransaction4Invoke(sysName, cid, chaincodeInputFunc = "signUpSigner", params = Seq(JsonFormat.toJsonString(signerNode1)))
    val msg_send = DoTransaction(t, "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    JsonFormat.parser.fromJsonString(msg_recv.err.get.cause.getMessage)(ActionResult) should be(SignerOperation.signerExists)
  }

  test("注册账户-node2，certNames不为空") {
    val signerNode2 = signers(2)
    val t = PeerHelper.createTransaction4Invoke(sysName, cid, chaincodeInputFunc = "signUpSigner", params = Seq(JsonFormat.toJsonString(signerNode2)))
    val msg_send = DoTransaction(t, "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    JsonFormat.parser.fromJsonString(msg_recv.err.get.cause.getMessage)(ActionResult) should be(SignerOperation.someFieldsNonEmpty)
  }

  test("注册账户-node3，operatesId不为空") {
    val signerNode3 = signers(3)
    val t = PeerHelper.createTransaction4Invoke(sysName, cid, chaincodeInputFunc = "signUpSigner", params = Seq(JsonFormat.toJsonString(signerNode3)))
    val msg_send = DoTransaction(t, "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    JsonFormat.parser.fromJsonString(msg_recv.err.get.cause.getMessage)(ActionResult) should be(SignerOperation.someFieldsNonEmpty)
  }

  test("注册账户-node4，存在普通证书，注册账户时不能有普通证书") {
    val signerNode4 = signers(4)
    val t = PeerHelper.createTransaction4Invoke(sysName, cid, chaincodeInputFunc = "signUpSigner", params = Seq(JsonFormat.toJsonString(signerNode4)))
    val msg_send = DoTransaction(t, "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    JsonFormat.parser.fromJsonString(msg_recv.err.get.cause.getMessage)(ActionResult) should be(SignerOperation.customCertExists)
  }

  test("注册账户-node5，两个一模一样的证书") {
    val signerNode5 = signers(5)
    val t = PeerHelper.createTransaction4Invoke(sysName, cid, chaincodeInputFunc = "signUpSigner", params = Seq(JsonFormat.toJsonString(signerNode5)))
    val msg_send = DoTransaction(t, "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    JsonFormat.parser.fromJsonString(msg_recv.err.get.cause.getMessage)(ActionResult).code should be(SignerOperation.authCertExistsCode)
  }

  test("注册账户-node1，signer 与 cert 的 creditCode 不一致") {
    val signerNode6 = signers(6)
    val t = PeerHelper.createTransaction4Invoke(sysName, cid, chaincodeInputFunc = "signUpSigner", params = Seq(JsonFormat.toJsonString(signerNode6)))
    val msg_send = DoTransaction(t, "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    JsonFormat.parser.fromJsonString(msg_recv.err.get.cause.getMessage)(ActionResult) should be(SignerOperation.SignerCertificateNotMatch)
  }

  test("禁用账户") {
    val t = PeerHelper.createTransaction4Invoke(sysName, cid, chaincodeInputFunc = "disableSigner", params = Seq(write(SignerStatus("121000005l35120456", false))))
    val msg_send = DoTransaction(t, "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    msg_recv.err.isEmpty should be(true)
  }

}
