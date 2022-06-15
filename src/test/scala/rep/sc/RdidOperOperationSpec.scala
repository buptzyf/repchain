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

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import com.google.protobuf.timestamp.Timestamp
import org.json4s.jackson.Serialization
import org.json4s.native.Serialization.write
import org.json4s.{DefaultFormats, jackson}
import org.scalatest._
import rep.app.conf.RepChainConfig
import rep.app.system.RepChainSystemContext
import rep.authority.check.PermissionVerify
import rep.network.tools.PeerExtension
import rep.proto.rc2.Certificate.CertType
import rep.proto.rc2.Operate.OperateType
import rep.proto.rc2.{ActionResult, CertId, Certificate, ChaincodeDeploy, ChaincodeId, Operate, Signer, TransactionResult}
import rep.sc.SandboxDispatcher.DoTransaction
import rep.sc.tpl.did.operation.{OperOperation, SignerOperation}
import rep.sc.tpl.did.operation.OperOperation.{OperateStatus, operateNotExists}
import rep.utils.IdTool
import scalapb.json4s.JsonFormat

import scala.collection.mutable
import scala.concurrent.duration._
import scala.io.BufferedSource


/**
  * @author zyf
  * @param _system
  */
class RdidOperOperationSpec(_system: ActorSystem) extends TestKit(_system) with Matchers with FunSuiteLike with BeforeAndAfterAll {

  def this() = this(ActorSystem("RdidOperOperationSpec", new RepChainConfig("121000005l35120456.node1").getSystemConf))

  val ctx : RepChainSystemContext = new RepChainSystemContext("121000005l35120456.node1")
  val pe = PeerExtension(system)
  pe.setRepChainContext(ctx)
  //val moduleManager = system.actorOf(ModuleManagerOfCFRD.props("modulemanager", false), "modulemanager")

  override def afterAll: Unit = {
    shutdown(system)
  }

  // or native.Serialization
  implicit val serialization: Serialization.type = jackson.Serialization
  implicit val formats: DefaultFormats.type = DefaultFormats

  val sysName = s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}121000005l35120456.node1"
  val superAdmin = s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}951002007l78123233.super_admin"

  val keyFileSuffix = ctx.getCryptoMgr.getKeyFileSuffix
  val sha256 = ctx.getHashTool
  val transactionTool = ctx.getTransactionBuilder

  // 加载node1的私钥
  ctx.getSignTool.loadPrivateKey("121000005l35120456.node1", "123", s"${keyFileSuffix.substring(1)}/${ctx.getConfig.getChainNetworkId}/" + "121000005l35120456.node1" + s"${keyFileSuffix}")
  // 加载super_admin的私钥
  ctx.getSignTool.loadPrivateKey("951002007l78123233.super_admin", "super_admin", s"${keyFileSuffix.substring(1)}/${ctx.getConfig.getChainNetworkId}/" + "951002007l78123233.super_admin" + s"${keyFileSuffix}")

  val cid = ChaincodeId("RdidOperateAuthorizeTPL", 1)

  val certNode1: BufferedSource = scala.io.Source.fromFile(s"${keyFileSuffix.substring(1)}/${ctx.getConfig.getChainNetworkId}/121000005l35120456.node1.cer")
  val certStr1: String = try certNode1.mkString finally certNode1.close()
  val certNode2: BufferedSource = scala.io.Source.fromFile(s"${keyFileSuffix.substring(1)}/${ctx.getConfig.getChainNetworkId}/12110107bi45jh675g.node2.cer")
  val certStr2: String = try certNode2.mkString finally certNode2.close()
  val superCert: BufferedSource = scala.io.Source.fromFile(s"${keyFileSuffix.substring(1)}/${ctx.getConfig.getChainNetworkId}/951002007l78123233.super_admin.cer", "UTF-8")
  val superCertPem: String = try superCert.mkString finally superCert.close()

  val certs: mutable.Map[String, String] = mutable.Map("node1" -> certStr1, "node2" -> certStr2, "super_admin" -> superCertPem)

  val node1Cert1 = Certificate(certStr1, "SHA256withECDSA", certValid = true, None, None, Certificate.CertType.CERT_AUTHENTICATION, Some(CertId(s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}121000005l35120456", "node1Cert1", "1")), sha256.hashstr(s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}"+IdTool.deleteLine(certStr1)), "1")

  // 包含有AuthCert
  val node1AuthCerts1 = Seq(node1Cert1)

  val superCertId = CertId(s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}951002007l78123233", "super_admin")
  val millis: Long = System.currentTimeMillis()
  //生成Did的身份证书
  val superAuthCert = Certificate(superCertPem, "SHA256withECDSA", true, Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)), None, CertType.CERT_AUTHENTICATION, Option(superCertId), sha256.hashstr(s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}"+IdTool.deleteLine(superCertPem)), "1.0")

  val signers: Array[Signer] = Array(
    Signer("super_admin", s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}951002007l78123233", "13856789234", Seq.empty, Seq.empty, Seq.empty, Seq.empty, List(superAuthCert), "", Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)), None, true, "1.0"),
    Signer("node1", s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}121000005l35120456", "18912345678", Seq.empty, Seq.empty, Seq.empty, Seq.empty, Seq(node1Cert1), "", None, None, signerValid = true, "1"),
  )

  val operate1 = Operate("operateId12345", "operateId12345", "X21000005l35120678", true, OperateType.OPERATE_CONTRACT, Seq.empty, "https://thyland/transaction", "RdidOperateAuthorizeTPL.signUpCertificate", None, None, true, "1")
  val operate2 = Operate(ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.signUpCertificate"), "operateId12345", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}951002007l78123233", true, OperateType.OPERATE_CONTRACT, Seq.empty, "https://thyland/transaction", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.signUpCertificate", None, None, true, "1")
  val operate3 = Operate(ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.signUpCertificate"), "RdidOperateAuthorizeTPL.signUpCertificate", "X21000005l35120678", true, OperateType.OPERATE_CONTRACT, Seq.empty, "https://thyland/transaction", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.signUpCertificate", None, None, true, "1")
  val operate4 = Operate(ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.operateId12345"), "operateId12345", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}951002007l78123233", true, OperateType.OPERATE_CONTRACT, Seq.empty, "https://thyland/transaction", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.operateId12345", None, None, true, "1")
  //准备探针以验证调用返回结果
  val probe = TestProbe()
  private val sandbox = system.actorOf(TransactionDispatcher.props("transactiondispatcher"), "transactiondispatcher")

  // 部署合约
  test("Deploy RdidOperateAuthorizeTPL") {
    // 部署账户管理合约
    val contractCert = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/did/RdidOperateAuthorizeTPL.scala")
    val contractCertStr = try contractCert.mkString finally contractCert.close()
    val t = transactionTool.createTransaction4Deploy(superAdmin, cid, contractCertStr, "", 5000, ChaincodeDeploy.CodeType.CODE_SCALA)
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    assert(msg_recv.head.err.get.reason.isEmpty)
  }

  test("提交者与操作的注册者creditCode不匹配") {
    val t = transactionTool.createTransaction4Invoke(superAdmin, cid, chaincodeInputFunc = "signUpOperate", params = Seq(JsonFormat.toJsonString(operate3)))
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    JsonFormat.parser.fromJsonString(msg_recv.head.err.get.reason)(ActionResult) should be(OperOperation.registerNotTranPoster)
  }

  test("signer不存在，无法注册Operate") {
    val t = transactionTool.createTransaction4Invoke(superAdmin, cid, chaincodeInputFunc = "signUpOperate", params = Seq(JsonFormat.toJsonString(operate2)))
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    JsonFormat.parser.fromJsonString(msg_recv.head.err.get.reason)(ActionResult) should be(SignerOperation.signerNotExists)
  }

  test("注册账户") {
    val signerNode1 = signers(0)
    val t = transactionTool.createTransaction4Invoke(superAdmin, cid, chaincodeInputFunc = "signUpSigner", params = Seq(JsonFormat.toJsonString(signerNode1)))
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv.head.err.get.reason.isEmpty should be(true)
  }

  test("禁用Operate，Operate不存在") {
    val t = transactionTool.createTransaction4Invoke(superAdmin, cid, chaincodeInputFunc = "updateOperateStatus", params = Seq(write(OperateStatus("operateId12345", false))))
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    JsonFormat.parser.fromJsonString(msg_recv.head.err.get.reason)(ActionResult) should be(OperOperation.operateNotExists)
  }

  test("第一次注册Operate") {
    val t = transactionTool.createTransaction4Invoke(superAdmin, cid, chaincodeInputFunc = "signUpOperate", params = Seq(JsonFormat.toJsonString(operate4)))
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv.head.err.get.reason.isEmpty should be(true)
  }

  test("第二次注册Operate，Operate已存在") {
    val t = transactionTool.createTransaction4Invoke(superAdmin, cid, chaincodeInputFunc = "signUpOperate", params = Seq(JsonFormat.toJsonString(operate4)))
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    JsonFormat.parser.fromJsonString(msg_recv.head.err.get.reason)(ActionResult) should be(OperOperation.operateExists)
  }

  test("禁用Operate") {
    val t = transactionTool.createTransaction4Invoke(superAdmin, cid, chaincodeInputFunc = "updateOperateStatus", params = Seq(write(OperateStatus(operate4.opId, false))))
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv.head.err.get.reason.isEmpty should be(true)
  }
}
