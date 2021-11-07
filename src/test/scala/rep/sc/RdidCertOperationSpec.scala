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

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestKit, TestProbe}
import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import org.json4s.jackson.Serialization
import org.json4s.native.Serialization.write
import org.json4s.{DefaultFormats, jackson}
import org.scalatest._
import rep.app.conf.SystemProfile
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType
import rep.crypto.Sha256
import rep.crypto.cert.{ImpECDSASigner, SignTool}
import rep.network.autotransaction.PeerHelper
import rep.network.module.cfrd.ModuleManagerOfCFRD
import rep.protos.peer.Certificate.CertType
import rep.protos.peer.Operate.OperateType
import rep.protos.peer._
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
  */
class RdidCertOperationSpec(_system: ActorSystem) extends TestKit(_system) with Matchers with FunSuiteLike with BeforeAndAfterAll {

  def this() = this(ActorSystem("RdidCertOperationSpec", new ClusterSystem("121000005l35120456.node1", InitType.MULTI_INIT, false).getConf))

  override def afterAll: Unit = {
    shutdown(system)
  }

  // or native.Serialization
  implicit val serialization: Serialization.type = jackson.Serialization
  implicit val formats: DefaultFormats.type = DefaultFormats

  val sysName = "121000005l35120456.node1"
  val superAdmin = "951002007l78123233.super_admin"
  // 初始化配置项，主要是为了初始化存储路径
  SystemProfile.initConfigSystem(system.settings.config, sysName)
  // 加载node1的私钥
  SignTool.loadPrivateKey(sysName, "123", "jks/" + sysName + ".jks")
  // 加载super_admin的私钥
  SignTool.loadPrivateKey(superAdmin, "super_admin", "jks/" + superAdmin + ".jks")

  val cid = ChaincodeId("RdidOperateAuthorizeTPL", 1)

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
  val superCert: BufferedSource = scala.io.Source.fromFile("jks/certs/951002007l78123233.super_admin.cer", "UTF-8")
  val superCertPem: String = try superCert.mkString finally superCert.close()
  val certs: mutable.Map[String, String] = mutable.Map("node1" -> certStr1, "node2" -> certStr2, "node3" -> certStr3, "node4" -> certStr4, "node5" -> certStr5)

  val cert1 = Certificate(certStr1, "SHA256withECDSA", certValid = true, None, None, Certificate.CertType.CERT_AUTHENTICATION, Some(CertId("121000005l35120456", "CERT1", "1")), Sha256.hashstr(certStr1), "1")
  val cert2 = Certificate(certStr2, "SHA256withECDSA", certValid = true, None, None, Certificate.CertType.CERT_AUTHENTICATION, Some(CertId("121000005l35120456", "CERT2", "1")), Sha256.hashstr(certStr2), "1")
  val cert3 = Certificate(certStr3, "SHA256withECDSA", certValid = true, None, None, Certificate.CertType.CERT_CUSTOM, Some(CertId("121000005l35120456", "CERT3", "1")), Sha256.hashstr(certStr3), "1")


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
    val t = PeerHelper.createTransaction4Deploy(nodeName = superAdmin, cid, contractCertStr, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA, ChaincodeDeploy.ContractClassification.CONTRACT_SYSTEM)
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    assert(msg_recv.head.getResult.reason.isEmpty)
  }

  test("注册superAdmin账户与操作") {
    // 注册账户
    val superCertHash = Sha256.hashstr(superCertPem)
    val superCertId = CertId("951002007l78123233", "super_admin")
    val millis = System.currentTimeMillis()
    //生成Did的身份证书
    val superAuthCert = rep.protos.peer.Certificate(superCertPem, "SHA256withECDSA", true, Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)), None, CertType.CERT_AUTHENTICATION, Option(superCertId), superCertHash, "1.0")
    // 账户
    val superSigner = Signer("super_admin", "951002007l78123233", "13856789234", Seq.empty, Seq.empty, Seq.empty, Seq.empty, List(superAuthCert), "", Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)), None, true, "1.0")
    val t6 = PeerHelper.createTransaction4Invoke(superAdmin, cid, ACTION.SignUpSigner, Seq(JsonFormat.toJsonString(superSigner)))
    val msg_send6 = DoTransaction(Seq[Transaction](t6), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send6)
    val msg_recv6 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv6(0).getResult.reason.isEmpty should be(true)

    val snls = List("transaction.stream", "transaction.postTranByString", "transaction.postTranStream", "transaction.postTran")
    val certOpt = rep.protos.peer.Operate(Sha256.hashstr("RdidOperateAuthorizeTPL.signUpCertificate"), "注册普通证书", superAdmin.split("\\.")(0), isPublish = true, OperateType.OPERATE_CONTRACT,
      snls, "*", "RdidOperateAuthorizeTPL.signUpCertificate", Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)), None, true, "1.0")
    val t8 = PeerHelper.createTransaction4Invoke(superAdmin, cid, "signUpOperate", Seq(JsonFormat.toJsonString(certOpt)))
    probe.send(sandbox, DoTransaction(Seq[Transaction](t8), "dbnumber", TypeOfSender.FromAPI))
    val msg_recv8 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv8.head.getResult.reason.isEmpty should be(true)

    val certStatusOpt = rep.protos.peer.Operate(Sha256.hashstr("RdidOperateAuthorizeTPL.updateCertificateStatus"), "修改普通证书状态", superAdmin.split("\\.")(0), isPublish = true, OperateType.OPERATE_CONTRACT,
      snls, "*", "RdidOperateAuthorizeTPL.updateCertificateStatus", Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)), None, true, "1.0")
    val t9 = PeerHelper.createTransaction4Invoke(superAdmin, cid, "signUpOperate", Seq(JsonFormat.toJsonString(certStatusOpt)))
    probe.send(sandbox, DoTransaction(Seq[Transaction](t9), "dbnumber", TypeOfSender.FromAPI))
    val msg_recv9 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv9.head.getResult.reason.isEmpty should be(true)
  }

  test("注册node1账户") {
    val signerNode1 = signers(0)
    val t = PeerHelper.createTransaction4Invoke(superAdmin, chaincodeId = cid, chaincodeInputFunc = "signUpSigner", params = Seq(JsonFormat.toJsonString(signerNode1)))
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv.head.getResult.reason.isEmpty should be(true)
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
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv.head.getResult.reason.isEmpty should be(true)
  }

  test("使用普通证书来调用signUpCertificate，应该失败， checkAuthCertAndRule") {
    // CERT3被注册为普通证书，使用cert3构建的交易，不能调用signUpCertificate
    val t = createTransaction4Invoke(nodeName = "121000005l35120456.node1", cid, CertId("121000005l35120456", "CERT3", "1"), chaincodeInputFunc = "signUpCertificate", params = Seq(JsonFormat.toJsonString(cert3)))
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
//    SignTool.verify(t.getSignature.signature.toByteArray, t.clearSignature.toByteArray, CertId("121000005l35120456", "CERT3", "1"), sysName)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    JsonFormat.parser.fromJsonString(msg_recv.head.getResult.reason)(ActionResult) should be(CertOperation.posterNotAuthCert)
  }


  test("使用node1来禁用普通证书--这里使用cert3") {
    val t = createTransaction4Invoke(nodeName = "121000005l35120456.node1", cid, CertId("121000005l35120456", "CERT1", "1"), chaincodeInputFunc = "updateCertificateStatus", params = Seq(write(CertStatus("121000005l35120456", "CERT3", false))))
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv.head.getResult.reason.isEmpty should be(true)
  }

  test("禁用不存在的证书") {
    val t = createTransaction4Invoke(nodeName = "121000005l35120456.node1", cid, CertId("121000005l35120456", "CERT1", "1"), chaincodeInputFunc = "updateCertificateStatus", params = Seq(write(CertStatus("121000005l35120456", "CERT4", false))))
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    JsonFormat.parser.fromJsonString(msg_recv.head.getResult.reason)(ActionResult) should be(CertOperation.certNotExists)
  }

  test("被注册证书虽然已存在，但是应该失败，checkAuthCertAndRule") {
    // CERT3被注册为普通证书，使用cert3构建的交易，不能调用signUpCertificate
    val t = createTransaction4Invoke(nodeName = "121000005l35120456.node1", cid, CertId("121000005l35120456", "CERT3", "1"), chaincodeInputFunc = "updateCertificateStatus", params = Seq(write(CertStatus("121000005l35120456", "CERT3", false))))
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    JsonFormat.parser.fromJsonString(msg_recv.head.getResult.reason)(ActionResult) should be(CertOperation.posterNotAuthCert)
  }


  def createTransaction4Invoke(nodeName: String, chaincodeId: ChaincodeId, certid: CertId, chaincodeInputFunc: String, params: Seq[String]): Transaction = {
    var t: Transaction = new Transaction()
    val millis = TimeUtils.getCurrentTime()
    if (chaincodeId == null) t
    val txid = IdTool.getRandomUUID
    val cip = new ChaincodeInput(chaincodeInputFunc, params)
    t = t.withId(txid).withCid(chaincodeId).withIpt(cip).withType(rep.protos.peer.Transaction.Type.CHAINCODE_INVOKE).clearSignature
    var sobj = Signature(Option(certid), Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)))
    val privateKey = loadPrivateKey(nodeName, "123", "jks/121000005l35120456.node1.jks")
    sobj = sobj.withSignature(ByteString.copyFrom(new ImpECDSASigner().sign(privateKey, t.toByteArray)))
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
