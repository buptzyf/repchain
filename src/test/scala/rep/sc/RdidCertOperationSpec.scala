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

import java.io.{File, FileInputStream}
import java.security.{KeyStore, PrivateKey}

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import org.json4s.jackson.Serialization
import org.json4s.native.Serialization.write
import org.json4s.{DefaultFormats, jackson}
import org.scalatest._
import rep.app.conf.RepChainConfig
import rep.app.system.{ClusterSystem, RepChainSystemContext}
import rep.network.module.cfrd.ModuleManagerOfCFRD
import rep.network.tools.PeerExtension
import rep.proto.rc2.Certificate.CertType
import rep.proto.rc2.Operate.OperateType
import rep.proto.rc2.{ActionResult, CertId, Certificate, ChaincodeDeploy, ChaincodeId, ChaincodeInput, Operate, Signature, Signer, Transaction, TransactionResult}
import rep.sc.SandboxDispatcher.DoTransaction
import rep.sc.TransferSpec.ACTION
import rep.sc.tpl.did.operation.CertOperation
import rep.sc.tpl.did.operation.CertOperation.CertStatus
import rep.utils.{IdTool, TimeUtils}
import scalapb.json4s.JsonFormat

import scala.collection.mutable
import scala.concurrent.duration._
import scala.io.BufferedSource


/**
  * @author zyf
  * @param _system
 * // 国密节点名："215159697776981712.node1"
 * //jks节点名："121000005l35120456.node1"
  */
class RdidCertOperationSpec(_system: ActorSystem) extends TestKit(_system) with Matchers with FunSuiteLike with BeforeAndAfterAll {
  def this() = this(
    ActorSystem("RdidCertOperationSpec", new RepChainConfig("121000005l35120456.node1").getSystemConf)
  )

  val ctx : RepChainSystemContext = new RepChainSystemContext("121000005l35120456.node1",null)
  val pe = PeerExtension(system)
  pe.setRepChainContext(ctx)
  //val moduleManager = system.actorOf(ModuleManagerOfCFRD.props("modulemanager", false), "modulemanager")

  override def afterAll: Unit = {
    shutdown(system)
  }

  // or native.Serialization
  implicit val serialization: Serialization.type = jackson.Serialization
  implicit val formats: DefaultFormats.type = DefaultFormats

  val sysName = "121000005l35120456.node1"
  val superAdmin = "951002007l78123233.super_admin"

  val keyFileSuffix = ctx.getCryptoMgr.getKeyFileSuffix
  val sha256 = ctx.getHashTool
  val transactionTool = ctx.getTransactionBuilder
  // 加载node1的私钥
  ctx.getSignTool.loadPrivateKey(sysName, "123", s"${keyFileSuffix.substring(1)}/${ctx.getConfig.getChainNetworkId}/" + sysName + s"${keyFileSuffix}")
  // 加载super_admin的私钥
  ctx.getSignTool.loadPrivateKey(superAdmin, "super_admin", s"${keyFileSuffix.substring(1)}/${ctx.getConfig.getChainNetworkId}/" + superAdmin + s"${keyFileSuffix}")

  val cid = ChaincodeId("RdidOperateAuthorizeTPL", 1)

  val certNode1: BufferedSource = scala.io.Source.fromFile(s"${keyFileSuffix.substring(1)}/${ctx.getConfig.getChainNetworkId}/121000005l35120456.node1.cer")
  val certStr1: String = try certNode1.mkString finally certNode1.close()
  val certNode2: BufferedSource = scala.io.Source.fromFile(s"${keyFileSuffix.substring(1)}/${ctx.getConfig.getChainNetworkId}/12110107bi45jh675g.node2.cer")
  val certStr2: String = try certNode2.mkString finally certNode2.close()
  val certNode3: BufferedSource = scala.io.Source.fromFile(s"${keyFileSuffix.substring(1)}/${ctx.getConfig.getChainNetworkId}/122000002n00123567.node3.cer")
  val certStr3: String = try certNode3.mkString finally certNode3.close()
  val certNode4: BufferedSource = scala.io.Source.fromFile(s"${keyFileSuffix.substring(1)}/${ctx.getConfig.getChainNetworkId}/921000005k36123789.node4.cer")
  val certStr4: String = try certNode4.mkString finally certNode4.close()
  val certNode5: BufferedSource = scala.io.Source.fromFile(s"${keyFileSuffix.substring(1)}/${ctx.getConfig.getChainNetworkId}/921000006e0012v696.node5.cer")
  val certStr5: String = try certNode5.mkString finally certNode5.close()
  val superCert: BufferedSource = scala.io.Source.fromFile(s"${keyFileSuffix.substring(1)}/${ctx.getConfig.getChainNetworkId}/951002007l78123233.super_admin.cer", "UTF-8")
  val superCertPem: String = try superCert.mkString finally superCert.close()
  val certs: mutable.Map[String, String] = mutable.Map("node1" -> certStr1, "node2" -> certStr2, "node3" -> certStr3, "node4" -> certStr4, "node5" -> certStr5)

  val cert1 = Certificate(certStr1, "SHA256withECDSA", certValid = true, None, None, Certificate.CertType.CERT_AUTHENTICATION, Some(CertId("121000005l35120456", "CERT1", "1")), sha256.hashstr(IdTool.deleteLine(certStr1)), "1")
  val cert2 = Certificate(certStr2, "SHA256withECDSA", certValid = true, None, None, Certificate.CertType.CERT_AUTHENTICATION, Some(CertId("121000005l35120456", "CERT2", "1")), sha256.hashstr(IdTool.deleteLine(certStr2)), "1")
  val cert3 = Certificate(certStr3, "SHA256withECDSA", certValid = true, None, None, Certificate.CertType.CERT_CUSTOM, Some(CertId("121000005l35120456", "CERT3", "1")), sha256.hashstr(IdTool.deleteLine(certStr3)), "1")


  // 只有AuthCert
  val node1AuthCerts1 = List(cert1)
  // 包含有customCert
  val node1AuthCerts2 = Seq(cert1, cert2)


  val signers: Array[Signer] = Array(
    Signer("node1", "121000005l35120456", "18912345678", Seq.empty, Seq.empty, Seq.empty, Seq.empty, node1AuthCerts1, "", None, None, signerValid = true, "1"),
    Signer("node1", "121000005l35120456", "18912345678", Seq.empty, Seq.empty, Seq.empty, Seq.empty, node1AuthCerts1, "", None, None, signerValid = true, "1"),
  )

  //准备探针以验证调用返回结果
  val probe = TestProbe()
  private val sandbox = system.actorOf(TransactionDispatcher.props("transactiondispatcher"), "transactiondispatcher")

  // 部署合约
  test("Deploy RdidOperateAuthorizeTPL") {
    // 部署账户管理合约
    val contractCert = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/did/RdidOperateAuthorizeTPL.scala")
    val contractCertStr = try contractCert.mkString finally contractCert.close()
    val t = transactionTool.createTransaction4Deploy(nodeName = superAdmin, cid, contractCertStr, "", 5000,
      ChaincodeDeploy.CodeType.CODE_SCALA)

    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    assert(msg_recv.head.err.get.reason.isEmpty)
  }

  test("注册superAdmin账户与操作") {
    // 注册账户
    val superCertHash = sha256.hashstr(IdTool.deleteLine(superCertPem))
    val superCertId = CertId("951002007l78123233", "super_admin")
    val millis = System.currentTimeMillis()
    //生成Did的身份证书
    val superAuthCert = Certificate(superCertPem, "SHA256withECDSA", true, Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)), None, CertType.CERT_AUTHENTICATION, Option(superCertId), superCertHash, "1.0")
    // 账户
    val superSigner = Signer("super_admin", "951002007l78123233", "13856789234", Seq.empty, Seq.empty, Seq.empty, Seq.empty, List(superAuthCert), "", Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)), None, true, "1.0")
    val t6 = transactionTool.createTransaction4Invoke(superAdmin, cid, ACTION.SignUpSigner, Seq(JsonFormat.toJsonString(superSigner)))
    val msg_send6 = DoTransaction(Seq[Transaction](t6), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send6)
    val msg_recv6 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv6(0).err.get.reason.isEmpty should be(true)

    val snls = List("transaction.stream", "transaction.postTranByString", "transaction.postTranStream", "transaction.postTran")
    val certOpt = Operate(sha256.hashstr(s"${ctx.getConfig.getChainNetworkId}.RdidOperateAuthorizeTPL.signUpCertificate"), "注册普通证书", superAdmin.split("\\.")(0), isPublish = true, OperateType.OPERATE_CONTRACT,
      snls, "*", s"${ctx.getConfig.getChainNetworkId}.RdidOperateAuthorizeTPL.signUpCertificate", Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)), None, true, "1.0")
    val t8 = transactionTool.createTransaction4Invoke(superAdmin, cid, "signUpOperate", Seq(JsonFormat.toJsonString(certOpt)))
    probe.send(sandbox, DoTransaction(Seq[Transaction](t8), "dbnumber", TypeOfSender.FromAPI))
    val msg_recv8 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv8.head.err.get.reason.isEmpty should be(true)

    val certStatusOpt = Operate(sha256.hashstr(s"${ctx.getConfig.getChainNetworkId}.RdidOperateAuthorizeTPL.updateCertificateStatus"), "修改普通证书状态", superAdmin.split("\\.")(0), isPublish = true, OperateType.OPERATE_CONTRACT,
      snls, "*", s"${ctx.getConfig.getChainNetworkId}.RdidOperateAuthorizeTPL.updateCertificateStatus", Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)), None, true, "1.0")
    val t9 = transactionTool.createTransaction4Invoke(superAdmin, cid, "signUpOperate", Seq(JsonFormat.toJsonString(certStatusOpt)))
    probe.send(sandbox, DoTransaction(Seq[Transaction](t9), "dbnumber", TypeOfSender.FromAPI))
    val msg_recv9 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv9.head.err.get.reason.isEmpty should be(true)
  }

  test("注册node1账户") {
    val signerNode1 = signers(0)
    val t = transactionTool.createTransaction4Invoke(superAdmin, chaincodeId = cid, chaincodeInputFunc = "signUpSigner", params = Seq(JsonFormat.toJsonString(signerNode1)))
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv.head.err.get.reason.isEmpty should be(true)
  }

//  test("使用node1来注册身份证书--这里使用cert2") {
//    val t = createTransaction4Invoke(nodeName = "121000005l35120456.node1", cid, CertId("121000005l35120456", "CERT1", "1"), chaincodeInputFunc = "signUpAllTypeCertificate", params = Seq(JsonFormat.toJsonString(cert2)))
//    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
//    probe.send(sandbox, msg_send)
//    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
//    JsonFormat.parser.fromJsonString(msg_recv.head.getResult.reason)(ActionResult) should be(CertOperation.isAuthCert)
//  }

  test("使用node1来注册普通证书--这里使用cert3") {
    val t = createTransaction4Invoke(sysName, cid, CertId("121000005l35120456", "CERT1", "1"), chaincodeInputFunc = "signUpCertificate", params = Seq(JsonFormat.toJsonString(cert3)))
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](60000.seconds)
    msg_recv.head.err.get.reason.isEmpty should be(true)
  }

  test("使用普通证书来调用signUpCertificate，应该失败， checkAuthCertAndRule") {
    // CERT3被注册为普通证书，使用cert3构建的交易，不能调用signUpCertificate
    val t = createTransaction4Invoke(nodeName = "121000005l35120456.node1", cid, CertId("121000005l35120456", "CERT3", "1"), chaincodeInputFunc = "signUpCertificate", params = Seq(JsonFormat.toJsonString(cert3)))
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
//    SignTool.verify(t.getSignature.signature.toByteArray, t.clearSignature.toByteArray, CertId("121000005l35120456", "CERT3", "1"), sysName)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    JsonFormat.parser.fromJsonString(msg_recv.head.err.get.reason)(ActionResult) should be(CertOperation.posterNotAuthCert)
  }


  test("使用node1来禁用普通证书--这里使用cert3") {
    val t = createTransaction4Invoke(nodeName = "121000005l35120456.node1", cid, CertId("121000005l35120456", "CERT1", "1"), chaincodeInputFunc = "updateCertificateStatus", params = Seq(write(CertStatus("121000005l35120456", "CERT3", false))))
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv.head.err.get.reason.isEmpty should be(true)
  }

  test("禁用不存在的证书") {
    val t = createTransaction4Invoke(nodeName = "121000005l35120456.node1", cid, CertId("121000005l35120456", "CERT1", "1"), chaincodeInputFunc = "updateCertificateStatus", params = Seq(write(CertStatus("121000005l35120456", "CERT4", false))))
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    JsonFormat.parser.fromJsonString(msg_recv.head.err.get.reason)(ActionResult) should be(CertOperation.certNotExists)
  }

  test("被注册证书虽然已存在，但是应该失败，checkAuthCertAndRule") {
    // CERT3被注册为普通证书，使用cert3构建的交易，不能调用signUpCertificate
    val t = createTransaction4Invoke(nodeName = "121000005l35120456.node1", cid, CertId("121000005l35120456", "CERT3", "1"), chaincodeInputFunc = "updateCertificateStatus", params = Seq(write(CertStatus("121000005l35120456", "CERT3", false))))
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    JsonFormat.parser.fromJsonString(msg_recv.head.err.get.reason)(ActionResult) should be(CertOperation.posterNotAuthCert)
  }


  def createTransaction4Invoke(nodeName: String, chaincodeId: ChaincodeId, certid: CertId, chaincodeInputFunc: String, params: Seq[String]): Transaction = {
    var t: Transaction = new Transaction()
    val millis = TimeUtils.getCurrentTime()
    if (chaincodeId == null) t
    val txid = IdTool.getRandomUUID
    val cip = new ChaincodeInput(chaincodeInputFunc, params)
    t = t.withId(txid).withCid(chaincodeId).withIpt(cip).withType(Transaction.Type.CHAINCODE_INVOKE).clearSignature
    var sobj = Signature(Option(certid), Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)))
    val privateKey = loadPrivateKey(nodeName, "123", s"${keyFileSuffix.substring(1)}/${ctx.getConfig.getChainNetworkId}/121000005l35120456.node1${keyFileSuffix}")
    sobj = sobj.withSignature(ByteString.copyFrom(ctx.getSigner.sign(privateKey, t.toByteArray)))
    t = t.withSignature(sobj)
    t
  }

  //节点启动时需要调用该函数初始化节点私钥
  def loadPrivateKey(pkeyname: String, password: String, path: String): PrivateKey = {
    val fis = new FileInputStream(new File(path))
    val pwd = "123".toCharArray
    val pkeys = KeyStore.getInstance(KeyStore.getDefaultType)
    pkeys.load(fis, pwd)
    fis.close()
    pkeys.getKey(pkeyname, "123".toCharArray).asInstanceOf[PrivateKey]
  }


}
