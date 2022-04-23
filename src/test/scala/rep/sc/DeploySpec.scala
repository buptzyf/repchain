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

import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import akka.actor.ActorSystem
import akka.testkit.TestKit

import scala.concurrent.Await
import scala.concurrent.duration._
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType
import org.json4s.{DefaultFormats, jackson}
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.{DefaultFormats, Formats, jackson}
import org.json4s.native.Serialization.writePretty
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}
import rep.crypto.cert.SignTool
import rep.network.autotransaction.PeerHelper
import rep.sc.SandboxDispatcher.DoTransaction
import akka.testkit.TestProbe
import akka.testkit.TestActorRef
import Json4sSupport._
import com.google.protobuf.timestamp.Timestamp
import rep.crypto.Sha256
import rep.sc.TransferSpec.ACTION
import rep.utils.{IdTool, SerializeUtils}
import scalapb.json4s.JsonFormat
import scala.collection.mutable.ArrayBuffer

/** 合约容器实现的单元测试
  *
  * @author c4w
  * @param _system 测试用例所在的actor System.
  *
  */
class DeploySpec(_system: ActorSystem) extends TestKit(_system) with Matchers with FlatSpecLike with BeforeAndAfterAll {
/*
  // or native.Serialization
  implicit val serialization = jackson.Serialization
  implicit val formats = DefaultFormats

  def this() = this(ActorSystem("SandBoxSpec", new ClusterSystem("121000005l35120456.node1", InitType.MULTI_INIT, false).getConf))

  override def afterAll: Unit = Await.ready(system.terminate(), Duration.Inf)

  //Scala实现的资产管理合约测试，包括合约的部署、调用、结果返回验证
  "container" should "deploy asset contract and invoke it" in {

    val sysName = "121000005l35120456.node1"
    val node2Name = "12110107bi45jh675g.node2"
    val superAdmin = "951002007l78123233.super_admin"
    // 初始化配置项，主要是为了初始化存储路径
    SystemProfile.initConfigSystem(system.settings.config, sysName)
    // 加载node1的私钥
    SignTool.loadPrivateKey(sysName, "123", s"${CryptoMgr.getKeyFileSuffix.substring(1)}/" + sysName + "${CryptoMgr.getKeyFileSuffix}")
    // 加载node2的私钥，在这里添加一个私钥的装载，因为系统默认只装载自己的节点私钥
    SignTool.loadPrivateKey(node2Name, "123", s"${CryptoMgr.getKeyFileSuffix.substring(1)}/" + node2Name + "${CryptoMgr.getKeyFileSuffix}")
    // 加载super_admin的私钥
    SignTool.loadPrivateKey(superAdmin, "super_admin", s"${CryptoMgr.getKeyFileSuffix.substring(1)}/" + superAdmin + "${CryptoMgr.getKeyFileSuffix}")

    //加载合约脚本
    val s1 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/ContractAssetsTPL.scala")
    val l1 = try s1.mkString finally s1.close()

    //准备探针以验证调用返回结果
    val probe = TestProbe()
    val sandbox = system.actorOf(TransactionDispatcher.props("transactiondispatcher"), "transactiondispatcher")
    //生成deploy交易
    val cid = new ChaincodeId("Assets", 1)
    val t1 = PeerHelper.createTransaction4Deploy(superAdmin, cid, l1, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA, ContractClassification.CONTRACT_CUSTOM)
    val msg_send1 = DoTransaction(Seq[Transaction](t1), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send1)
    val msg_recv1 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv1(0).getResult.code should be(0)

    //同样合约id不允许重复部署
    val t2 = PeerHelper.createTransaction4Deploy(superAdmin, cid, l1, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA, ContractClassification.CONTRACT_CUSTOM)
    val msg_send2 = DoTransaction(Seq[Transaction](t2), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send2)
    val msg_recv2 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv2(0).getResult.reason should be(SandboxDispatcher.ERR_REPEATED_CID)

    val cid3 = new ChaincodeId("Assets", 2)
    //同一合约部署者允许部署同样名称不同版本合约
    val t3 = PeerHelper.createTransaction4Deploy(superAdmin, cid3, l1, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA, ContractClassification.CONTRACT_CUSTOM)
    val msg_send3 = DoTransaction(Seq[Transaction](t3), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send3)
    val msg_recv3 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv3(0).getResult.code should be(0)

    // 不允不具有权限的用户许部署同样名称不同版本合约
    val cid4 = new ChaincodeId("Assets", 3)
    val t4 = PeerHelper.createTransaction4Deploy(sysName, cid4, l1, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA, ContractClassification.CONTRACT_CUSTOM)
    val msg_send4 = DoTransaction(Seq[Transaction](t4), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send4)
    val msg_recv4 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv4(0).getResult.reason should be(SandboxDispatcher.ERR_NO_OPERATE)

    // 部署账户管理合约
    val cid2 = ChaincodeId(SystemProfile.getAccountChaincodeName, 1)
    val s2 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/did/RdidOperateAuthorizeTPL.scala")
    val l2 = try s2.mkString finally s2.close()
    val t5 = PeerHelper.createTransaction4Deploy(superAdmin, cid2, l2, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA, ContractClassification.CONTRACT_SYSTEM)
    val msg_send5 = DoTransaction(Seq[Transaction](t5), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send5)
    val msg_recv5 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv5(0).getResult.code should be(0)

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
    val t6 = PeerHelper.createTransaction4Invoke(superAdmin, cid2, ACTION.SignUpSigner, Seq(JsonFormat.toJsonString(superSigner)))
    val msg_send6 = DoTransaction(Seq[Transaction](t6), "dbnumber", TypeOfSender.FromPreloader)
    probe.send(sandbox, msg_send6)
    val msg_recv6 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv6(0).getResult.reason.isEmpty should be(true)

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
    val t7 = PeerHelper.createTransaction4Invoke(superAdmin, cid2, ACTION.SignUpSigner, Seq(JsonFormat.toJsonString(node1Signer)))
    val msg_send7 = DoTransaction(Seq[Transaction](t7), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send7)
    val msg_recv7 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv7.head.getResult.reason.isEmpty should be(true)

    val snls = List("transaction.stream", "transaction.postTranByString", "transaction.postTranStream", "transaction.postTran")
    val setStateOpt = rep.protos.peer.Operate(Sha256.hashstr("Assets.deploy"), "修改合约状态", superAdmin.split("\\.")(0), isPublish = false, OperateType.OPERATE_CONTRACT,
      snls, "*", "ContractAssetsTPL.setState", Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)), None, true, "1.0")
    val t8 = PeerHelper.createTransaction4Invoke(superAdmin, cid2, "signUpOperate", Seq(JsonFormat.toJsonString(setStateOpt)))
    probe.send(sandbox, DoTransaction(Seq[Transaction](t8), "dbnumber", TypeOfSender.FromPreloader))
    val msg_recv8 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv8.head.getResult.reason.isEmpty should be(true)

    // superAdmin 为用户授权
    val granteds = new ArrayBuffer[String]
    granteds.+=(sysName.split("\\.")(0))
    millis = System.currentTimeMillis()
    val at = rep.protos.peer.Authorize(IdTool.getRandomUUID, superAdmin.split("\\.")(0), granteds, Seq(Sha256.hashstr("Assets.deploy")),
      TransferType.TRANSFER_REPEATEDLY, Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)), None, true, "1.0")
    val als: List[String] = List(JsonFormat.toJsonString(at))
    val t10 = PeerHelper.createTransaction4Invoke(superAdmin, cid2, "grantOperate", Seq(SerializeUtils.compactJson(als)))
    probe.send(sandbox, DoTransaction(Seq[Transaction](t10), "dbnumber", TypeOfSender.FromAPI))
    val msg_recv10 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv10.head.getResult.reason.isEmpty should be(true)

    // 拥有权限即可升级对应的合约
    val t11 = PeerHelper.createTransaction4Deploy(sysName, cid4, l1, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA, ContractClassification.CONTRACT_CUSTOM)
    val msg_send11 = DoTransaction(Seq[Transaction](t11), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send11)
    val msg_recv11 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv11(0).getResult.code should be(0)

  }*/
}
