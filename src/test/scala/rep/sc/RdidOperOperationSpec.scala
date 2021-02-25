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
import rep.protos.peer.Operate.OperateType
import rep.protos.peer._
import rep.sc.SandboxDispatcher.DoTransaction
import rep.sc.tpl.did.operation.{DidOperation, OperOperation, SignerOperation}
import rep.sc.tpl.did.operation.OperOperation.{OperateStatus, operateNotExists}
import scalapb.json4s.JsonFormat

import scala.collection.mutable
import scala.concurrent.duration._
import scala.io.BufferedSource


/**
  * @author zyf
  * @param _system
  */
class RdidOperOperationSpec(_system: ActorSystem) extends TestKit(_system) with Matchers with FunSuiteLike with BeforeAndAfterAll {

  def this() = this(ActorSystem("RdidOperOperationSpec", new ClusterSystem("121000005l35120456.node1", InitType.MULTI_INIT, false).getConf))

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

  val certs: mutable.Map[String, String] = mutable.Map("node1" -> certStr1, "node2" -> certStr2)

  val node1Cert1 = Certificate(certStr1, "SHA256withECDSA", certValid = true, None, None, Certificate.CertType.CERT_AUTHENTICATION, Some(CertId("121000005l35120456", "node1Cert1", "1")), "hash12345", "1")

  // 包含有AuthCert
  val node1AuthCerts1 = Seq(node1Cert1)

  val signers: Array[Signer] = Array(
    Signer("node1", "121000005l35120456", "18912345678", Seq.empty, Seq.empty, Seq.empty, Seq.empty, node1AuthCerts1, "", None, None, signerValid = true, "1"),
  )

  val operate1 = Operate("operateId12345", "operateId12345", "X21000005l35120678", true, OperateType.OPERATE_CONTRACT, Seq.empty, "https://thyland/transaction", "CertOperation.signUpCertificate", None, None, true, "1")
  val operate2 = Operate("operateId12345", "operateId12345", "121000005l35120456", true, OperateType.OPERATE_CONTRACT, Seq.empty, "https://thyland/transaction", "CertOperation.signUpCertificate", None, None, true, "1")

  //准备探针以验证调用返回结果
  val probe = TestProbe()
  private val sandbox = system.actorOf(TransactionDispatcher.props("transactiondispatcher"), "transactiondispatcher")

  // 部署合约
  test("Deploy RdidOperateAuthorizeTPL") {
    // 部署账户管理合约
    val contractCert = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/did/RdidOperateAuthorizeTPL.scala")
    val contractCertStr = try contractCert.mkString finally contractCert.close()
    val t = PeerHelper.createTransaction4Deploy(sysName, cid, contractCertStr, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA,ChaincodeDeploy.ContractClassification.CONTRACT_SYSTEM)
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    assert(msg_recv.err.isEmpty)
  }

  test("提交者与操作的注册者creditCode不匹配") {
    val t = PeerHelper.createTransaction4Invoke(sysName, cid, chaincodeInputFunc = "signUpOperate", params = Seq(JsonFormat.toJsonString(operate1)))
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    JsonFormat.parser.fromJsonString(msg_recv.err.get.cause.getMessage)(ActionResult) should be(OperOperation.registerNotTranPoster)
  }

  test("signer不存在，无法注册Operate") {
    val t = PeerHelper.createTransaction4Invoke(sysName, cid, chaincodeInputFunc = "signUpOperate", params = Seq(JsonFormat.toJsonString(operate2)))
    val msg_send = DoTransaction(t, "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    JsonFormat.parser.fromJsonString(msg_recv.err.get.cause.getMessage)(ActionResult) should be(SignerOperation.signerNotExists)
  }

  test("注册账户") {
    val signerNode1 = signers(0)
    val t = PeerHelper.createTransaction4Invoke(sysName, cid, chaincodeInputFunc = "signUpSigner", params = Seq(JsonFormat.toJsonString(signerNode1)))
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    msg_recv.err.isEmpty should be(true)
  }

  test("禁用Operate，Operate不存在") {
    val t = PeerHelper.createTransaction4Invoke(sysName, cid, chaincodeInputFunc = "disableOperate", params = Seq(write(OperateStatus("operateId12345", false))))
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    JsonFormat.parser.fromJsonString(msg_recv.err.get.cause.getMessage)(ActionResult) should be(OperOperation.operateNotExists)
  }

  test("第一次注册Operate") {
    val t = PeerHelper.createTransaction4Invoke(sysName, cid, chaincodeInputFunc = "signUpOperate", params = Seq(JsonFormat.toJsonString(operate2)))
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    msg_recv.err.isEmpty should be(true)
  }

  test("第二次注册Operate，Operate已存在") {
    val t = PeerHelper.createTransaction4Invoke(sysName, cid, chaincodeInputFunc = "signUpOperate", params = Seq(JsonFormat.toJsonString(operate2)))
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    JsonFormat.parser.fromJsonString(msg_recv.err.get.cause.getMessage)(ActionResult) should be(OperOperation.operateExists)
  }

  test("禁用Operate") {
    val t = PeerHelper.createTransaction4Invoke(sysName, cid, chaincodeInputFunc = "disableOperate", params = Seq(write(OperateStatus("operateId12345", false))))
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    msg_recv.err.isEmpty should be(true)
  }

}
