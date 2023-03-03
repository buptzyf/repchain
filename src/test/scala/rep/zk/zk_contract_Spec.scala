package rep.zk

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
import rep.authority.check.PermissionVerify
import rep.network.module.cfrd.ModuleManagerOfCFRD
import rep.network.tools.PeerExtension
import rep.proto.rc2.Certificate.CertType
import rep.proto.rc2.Operate.OperateType
import rep.proto.rc2.{ActionResult, CertId, Certificate, ChaincodeDeploy, ChaincodeId, ChaincodeInput, Operate, Signature, Signer, Transaction, TransactionResult}
import rep.sc.SandboxDispatcher.DoTransaction
import rep.sc.{TransactionDispatcher, TypeOfSender}
import rep.sc.TransferSpec.ACTION
import rep.sc.tpl.did.operation.CertOperation
import rep.sc.tpl.did.operation.CertOperation.CertStatus
import rep.utils.{IdTool, TimeUtils}
import scalapb.json4s.JsonFormat

import scala.collection.mutable
import scala.concurrent.duration._
import scala.io.BufferedSource

class zk_contract_Spec(_system: ActorSystem) extends TestKit(_system) with Matchers with FunSuiteLike with BeforeAndAfterAll {
  def this() = this(
    ActorSystem("zk_contract_Spec", new RepChainSystemContext("121000005l35120456.node1").getConfig.getSystemConf)
  )

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

  val cid = ChaincodeId("zksnark", 1)

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

  val cert1 = Certificate(certStr1, "SHA256withECDSA", certValid = true, None, None, Certificate.CertType.CERT_AUTHENTICATION, Some(CertId(s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}121000005l35120456", "CERT1", "1")), sha256.hashstr(IdTool.deleteLine(certStr1)), "1")
  val cert2 = Certificate(certStr2, "SHA256withECDSA", certValid = true, None, None, Certificate.CertType.CERT_AUTHENTICATION, Some(CertId(s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}121000005l35120456", "CERT2", "1")), sha256.hashstr(IdTool.deleteLine(certStr2)), "1")
  val cert3 = Certificate(certStr3, "SHA256withECDSA", certValid = true, None, None, Certificate.CertType.CERT_CUSTOM, Some(CertId(s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}121000005l35120456", "CERT3", "1")), sha256.hashstr(IdTool.deleteLine(certStr3)), "1")


  // 只有AuthCert
  val node1AuthCerts1 = List(cert1)
  // 包含有customCert
  val node1AuthCerts2 = Seq(cert1, cert2)


  val signers: Array[Signer] = Array(
    Signer("node1", s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}121000005l35120456", "18912345678", Seq.empty, Seq.empty, Seq.empty, Seq.empty, node1AuthCerts1, "", None, None, signerValid = true, "1"),
    Signer("node1", s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}121000005l35120456", "18912345678", Seq.empty, Seq.empty, Seq.empty, Seq.empty, node1AuthCerts1, "", None, None, signerValid = true, "1"),
  )

  //准备探针以验证调用返回结果
  val probe = TestProbe()
  private val sandbox = system.actorOf(TransactionDispatcher.props("transactiondispatcher"), "transactiondispatcher")

  // 部署合约
  test("Deploy zksnark") {
    // 部署账户管理合约
    val contractCert = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/zksnark.scala")
    val contractCertStr = try contractCert.mkString finally contractCert.close()
    val t = transactionTool.createTransaction4Deploy(nodeName = superAdmin, cid, contractCertStr, "", 5000,
      ChaincodeDeploy.CodeType.CODE_SCALA)

    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    assert(msg_recv.head.err.get.reason.isEmpty)
  }

  test("调用zksnark") {
    val signerNode1 = signers(0)
    val t = transactionTool.createTransaction4Invoke(superAdmin, chaincodeId = cid, chaincodeInputFunc = "test", params = Seq(JsonFormat.toJsonString(signerNode1)))
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv.head.err.get.reason.isEmpty should be(true)
  }


}
