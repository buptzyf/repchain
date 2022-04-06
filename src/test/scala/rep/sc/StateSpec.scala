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
import org.json4s.native.Serialization.write
import org.json4s.{DefaultFormats, jackson}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import rep.app.conf.SystemProfile
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType
import rep.crypto.{CryptoMgr, Sha256}
import rep.crypto.cert.SignTool
import rep.network.autotransaction.PeerHelper
import rep.network.tools.PeerExtension
import rep.protos.peer.Authorize.TransferType
import rep.protos.peer.Certificate.CertType
import rep.protos.peer.ChaincodeDeploy.ContractClassification
import rep.protos.peer.Operate.OperateType
import rep.protos.peer._
import rep.sc.SandboxDispatcher.DoTransaction
import rep.sc.TransferSpec.{ACTION, SetMap}
import rep.sc.tpl.Transfer
import rep.utils.{IdTool, SerializeUtils}
import scalapb.json4s.JsonFormat

import scala.collection.mutable.{ArrayBuffer, Map}
import scala.concurrent.duration._

object StateSpec {

  type SetMap = scala.collection.mutable.Map[String, Int]

  object ACTION {
    val transfer = "transfer"
    val set = "set"
    val SignUpSigner = "SignUpSigner"
    val SignUpCert = "SignUpCert"
  }

}

/**
  * author c4w
  *
  * @param _system
  */
class StateSpec(_system: ActorSystem) extends TestKit(_system) with Matchers with FlatSpecLike with BeforeAndAfterAll {

  def this() = this(ActorSystem("StateSpec", new ClusterSystem("121000005l35120456.node1", InitType.MULTI_INIT, false).getConf))

  override def afterAll: Unit = {
    shutdown(system)
  }

  // or native.Serialization
  implicit val serialization = jackson.Serialization
  implicit val formats = DefaultFormats

  "ContractAssetsTPL" should "can set assets and transfer from a to b" in {

    val sysName = "121000005l35120456.node1"
    val node2Name = "12110107bi45jh675g.node2"
    val superAdmin = "951002007l78123233.super_admin"
    // 初始化配置项，主要是为了初始化存储路径
    SystemProfile.initConfigSystem(system.settings.config, sysName)
    PeerExtension(system).setSysTag(sysName)
    // 加载node1的私钥
    SignTool.loadPrivateKey(sysName, "123", s"${CryptoMgr.getKeyFileSuffix.substring(1)}/" + sysName + "${CryptoMgr.getKeyFileSuffix}")
    // 加载node2的私钥
    SignTool.loadPrivateKey(node2Name, "123", s"${CryptoMgr.getKeyFileSuffix.substring(1)}/" + node2Name + "${CryptoMgr.getKeyFileSuffix}")
    // 加载super_admin的私钥
    SignTool.loadPrivateKey(superAdmin, "super_admin", s"${CryptoMgr.getKeyFileSuffix.substring(1)}/" + superAdmin + "${CryptoMgr.getKeyFileSuffix}")

    // 部署资产管理
    val cid1 = ChaincodeId("ContractAssetsTPL", 1)
    val s1 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/ContractAssetsTPL.scala")
    val l1 = try s1.mkString finally s1.close()

    // 部署账户管理合约
    val cid2 = ChaincodeId(SystemProfile.getAccountChaincodeName, 1)
    val s2 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/did/RdidOperateAuthorizeTPL.scala")
    val l2 = try s2.mkString finally s2.close()

    //准备探针以验证调用返回结果
    val probe = TestProbe()
    val sandbox = system.actorOf(TransactionDispatcher.props("transactiondispatcher"), "transactiondispatcher")

    //生成deploy交易
    val t1 = PeerHelper.createTransaction4Deploy(superAdmin, cid1, l1, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA, ContractClassification.CONTRACT_CUSTOM)
    val msg_send1 = DoTransaction(Seq[Transaction](t1), "test-db", TypeOfSender.FromPreloader)
    probe.send(sandbox, msg_send1)
    val msg_recv1 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv1(0).getResult.code should be(0)

    val t2 = PeerHelper.createTransaction4Deploy(superAdmin, cid2, l2, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA, ContractClassification.CONTRACT_SYSTEM)
    val msg_send2 = DoTransaction(Seq[Transaction](t2), "test-db", TypeOfSender.FromPreloader)
    probe.send(sandbox, msg_send2)
    val msg_recv2 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv2(0).getResult.code should be(0)

    // 注册账户
    val superCert = scala.io.Source.fromFile(s"${CryptoMgr.getKeyFileSuffix.substring(1)}/certs/951002007l78123233.super_admin.cer", "UTF-8")
    val superCertPem = try superCert.mkString finally superCert.close()
    val superCertHash = Sha256.hashstr(superCertPem)
    val superCertId = CertId("951002007l78123233", "super_admin")
    var millis = System.currentTimeMillis()
    //生成Did的身份证书
    val superAuthCert = rep.protos.peer.Certificate(superCertPem, "SHA256withECDSA", true, Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)), None, CertType.CERT_AUTHENTICATION, Option(superCertId), superCertHash, "1.0")
    // 账户
    val superSigner = Signer("super_admin", "951002007l78123233", "13856789234", Seq.empty, Seq.empty, Seq.empty, Seq.empty, List(superAuthCert), "", Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)), None, true, "1.0")
    val t3 = PeerHelper.createTransaction4Invoke(superAdmin, cid2, ACTION.SignUpSigner, Seq(JsonFormat.toJsonString(superSigner)))
    val msg_send3 = DoTransaction(Seq[Transaction](t3), "test-db", TypeOfSender.FromPreloader)
    probe.send(sandbox, msg_send3)
    val msg_recv3 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv3(0).getResult.reason.isEmpty should be(true)

    // 注册账户
    val node1CertFile = scala.io.Source.fromFile(s"${CryptoMgr.getKeyFileSuffix.substring(1)}/certs/121000005l35120456.node1.cer", "UTF-8")
    val node1CertPem = try node1CertFile.mkString finally node1CertFile.close()
    val node1CertHash = Sha256.hashstr(node1CertPem)
    val node1CertId = CertId("121000005l35120456", "node1")
    millis = System.currentTimeMillis()
    //生成Did的身份证书
    val node1AuthCert = rep.protos.peer.Certificate(node1CertPem, "SHA256withECDSA", true, Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)), None, CertType.CERT_AUTHENTICATION, Option(node1CertId), node1CertHash, "1.0")
    // 账户
    val node1Signer = Signer("node1", "121000005l35120456", "13856789234", Seq.empty, Seq.empty, Seq.empty, Seq.empty, List(node1AuthCert), "", Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)), None, true, "1.0")
    val t9 = PeerHelper.createTransaction4Invoke(superAdmin, cid2, ACTION.SignUpSigner, Seq(JsonFormat.toJsonString(node1Signer)))
    val msg_send9 = DoTransaction(Seq[Transaction](t9), "test-db", TypeOfSender.FromPreloader)
    probe.send(sandbox, msg_send9)
    val msg_recv9 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv9.head.getResult.reason.isEmpty should be(true)

    // 注册账户
    val node2CertFile = scala.io.Source.fromFile(s"${CryptoMgr.getKeyFileSuffix.substring(1)}/certs/12110107bi45jh675g.node2.cer", "UTF-8")
    val node2CertPem = try node2CertFile.mkString finally node2CertFile.close()
    val node2CertHash = Sha256.hashstr(node2CertPem)
    val node2CertId = CertId("12110107bi45jh675g", "node2")
    millis = System.currentTimeMillis()
    //生成Did的身份证书
    val node2AuthCert = rep.protos.peer.Certificate(node2CertPem, "SHA256withECDSA", true, Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)), None, CertType.CERT_AUTHENTICATION, Option(node2CertId), node2CertHash, "1.0")
    // 账户
    val node2Signer = Signer("node2", "12110107bi45jh675g", "13856789234", Seq.empty, Seq.empty, Seq.empty, Seq.empty, List(node2AuthCert), "", Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)), None, true, "1.0")
    val t4 = PeerHelper.createTransaction4Invoke(superAdmin, cid2, ACTION.SignUpSigner, Seq(JsonFormat.toJsonString(node2Signer)))
    val msg_send4 = DoTransaction(Seq[Transaction](t4), "test-db", TypeOfSender.FromPreloader)
    probe.send(sandbox, msg_send4)
    val msg_recv4 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv4.head.getResult.reason.isEmpty should be(true)

    //生成invoke交易，调用cd1的set方法，给账户预置金额；122000002n00123567这个账户不存证不可以设置金额？？？？？？？？？
    val sm: SetMap = Map("121000005l35120456" -> 50, "12110107bi45jh675g" -> 50, "122000002n00123567" -> 50)
    val sms = write(sm)
    val t5 = PeerHelper.createTransaction4Invoke(superAdmin, cid1, ACTION.set, Seq(sms))
    val msg_send5 = DoTransaction(Seq[Transaction](t5), "test-db", TypeOfSender.FromPreloader)
    probe.send(sandbox, msg_send5)
    val msg_recv5 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv5(0).getResult.reason.isEmpty should be(true)

    // superAdmin 为自己注册操作（superAdmin不注册操作也能通过授权 ），目的是为了给其他用户授权
    val snls = List("transaction.stream", "transaction.postTranByString", "transaction.postTranStream", "transaction.postTran")
    // 公开操作，无需授权
    val transferOpt = rep.protos.peer.Operate(Sha256.hashstr("ContractAssetsTPL.transfer"), "转账交易", superAdmin.split("\\.")(0), true, OperateType.OPERATE_CONTRACT,
      snls, "*", "ContractAssetsTPL.transfer", Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)), None, true, "1.0")
    val t7 = PeerHelper.createTransaction4Invoke(superAdmin, cid2, "signUpOperate", Seq(JsonFormat.toJsonString(transferOpt)))
    probe.send(sandbox, DoTransaction(Seq[Transaction](t7), "test-db", TypeOfSender.FromPreloader))
    val msg_recv7 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv7.head.getResult.reason.isEmpty should be(true)

    //正常调用
    val tcs = Array(
      Transfer("121000005l35120456", "12110107bi45jh675g", 5),
      Transfer("121000005l35120456", "12110107bi45jh675g0", 5),
      Transfer("121000005l35120456", "12110107bi45jh675g", 500))
    val rcs = Array(None, "目标账户不存在", "余额不足")

    for (i <- 0 until 1) {
      val t6 = PeerHelper.createTransaction4Invoke(sysName, cid1, ACTION.transfer, Seq(write(tcs(i))))
      val msg_send6 = DoTransaction(Seq[Transaction](t6), "test-db", TypeOfSender.FromPreloader)
      probe.send(sandbox, msg_send6)
      val msg_recv6 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
      if (msg_recv6(0).getResult.reason.isEmpty && i == 0)
        msg_recv6(0).getResult.reason.isEmpty should be(true)
      else
        msg_recv6(0).getResult.reason should be(rcs(i))
    }

     //不具有操作者，不能禁用合约
    var t = PeerHelper.createTransaction4State(node2Name, cid1, false)
    var msg_send = DoTransaction(Seq[Transaction](t), "test-db", TypeOfSender.FromPreloader)
    probe.send(sandbox, msg_send)
    var msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv(0).getResult.reason should equal(SandboxDispatcher.ERR_NO_OPERATE)

    val setStateOpt = rep.protos.peer.Operate(Sha256.hashstr("ContractAssetsTPL.setState"), "修改合约状态", superAdmin.split("\\.")(0), isPublish = false, OperateType.OPERATE_CONTRACT,
      snls, "*", "ContractAssetsTPL.setState", Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)), None, true, "1.0")
    val t8 = PeerHelper.createTransaction4Invoke(superAdmin, cid2, "signUpOperate", Seq(JsonFormat.toJsonString(setStateOpt)))
    probe.send(sandbox, DoTransaction(Seq[Transaction](t8), "test-db", TypeOfSender.FromPreloader))
    val msg_recv8 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv8.head.getResult.reason.isEmpty should be(true)

    // superAdmin 为用户授权
    val granteds = new ArrayBuffer[String]
    //granteds.+=(sysName.split("\\.")(0))
    granteds.+=(node2Name.split("\\.")(0))
    millis = System.currentTimeMillis()
    val at = rep.protos.peer.Authorize(IdTool.getRandomUUID, superAdmin.split("\\.")(0), granteds, Seq(Sha256.hashstr("ContractAssetsTPL.setState")),
      TransferType.TRANSFER_REPEATEDLY, Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)), None, true, "1.0")
    val als: List[String] = List(JsonFormat.toJsonString(at))
    val t10 = PeerHelper.createTransaction4Invoke(superAdmin, cid2, "grantOperate", Seq(SerializeUtils.compactJson(als)))
    probe.send(sandbox, DoTransaction(Seq[Transaction](t10), "test-db", TypeOfSender.FromPreloader))
    val msg_recv10 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv10.head.getResult.reason.isEmpty should be(true)

    //被授权者可以禁用合约
    val t11 = PeerHelper.createTransaction4State(node2Name, cid1, state = false)
    val msg_send11 = DoTransaction(Seq[Transaction](t11), "test-db", TypeOfSender.FromPreloader)
    probe.send(sandbox, msg_send11)
    val msg_recv11 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv11(0).getResult.reason.isEmpty should be(true)

    //禁用合约之后，无法Invoke合约
    for (i <- 0 until 1) {
      val t6 = PeerHelper.createTransaction4Invoke(sysName, cid1, ACTION.transfer, Seq(write(tcs(i))))
      val msg_send6 = DoTransaction(Seq[Transaction](t6), "test-db", TypeOfSender.FromPreloader)
      probe.send(sandbox, msg_send6)
      val msg_recv6 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
      msg_recv6(0).getResult.reason should be(SandboxDispatcher.ERR_DISABLE_CID)
    }

    //非授权者无法启用合约
     t = PeerHelper.createTransaction4State(sysName, cid1, true)
     msg_send = DoTransaction(Seq[Transaction](t), "test-db", TypeOfSender.FromPreloader)
    probe.send(sandbox, msg_send)
     msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv(0).getResult.reason should be(SandboxDispatcher.ERR_NO_OP_IN_AUTHORIZE)

    //授权者可以启用合约
     t = PeerHelper.createTransaction4State(node2Name, cid1, true)
     msg_send = DoTransaction(Seq[Transaction](t), "test-db", TypeOfSender.FromPreloader)
    probe.send(sandbox, msg_send)
     msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv4(0).getResult.reason.isEmpty should be(true)

    //启用合约之后,可以Invoke合约
    for (i <- 0 until 1) {
      val t6 = PeerHelper.createTransaction4Invoke(sysName, cid1, ACTION.transfer, Seq(write(tcs(i))))
      val msg_send6 = DoTransaction(Seq[Transaction](t6), "test-db", TypeOfSender.FromPreloader)
      probe.send(sandbox, msg_send6)
      val msg_recv6 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
      if (msg_recv6(0).getResult.reason.isEmpty && i == 0)
        msg_recv6(0).getResult.reason.isEmpty should be(true)
      else
        msg_recv6(0).getResult.reason should equal(rcs(i))
    }
  }
}
