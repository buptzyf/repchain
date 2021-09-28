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
import org.json4s.{DefaultFormats, jackson}
import org.json4s.native.Serialization.{write, writePretty}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType
import rep.network.autotransaction.PeerHelper
import rep.network.module.cfrd.ModuleManagerOfCFRD
import rep.protos.peer.{Certificate, ChaincodeId, Signer}
import rep.sc.TransferSpec.{ACTION, SetMap}
import rep.sc.tpl._
//.{CertStatus,CertInfo}
import rep.sc.tpl.Transfer
import rep.storage.ImpDataAccess
import rep.utils.SerializeUtils.toJson
import rep.app.conf.SystemProfile

import scala.concurrent.duration._
import scala.collection.mutable.Map
import rep.sc.SandboxDispatcher.DoTransaction


/**
  * author zyf
  *
  * @param _system
  */
class TransferSpec2(_system: ActorSystem) extends TestKit(_system) with Matchers with FlatSpecLike with BeforeAndAfterAll {

  def this() = this(ActorSystem("TransferSpec", new ClusterSystem("121000005l35120456.node1", InitType.MULTI_INIT, false).getConf))

  override def afterAll: Unit = {
    shutdown(system)
  }

  implicit val serialization = jackson.Serialization
  // or native.Serialization
  implicit val formats = DefaultFormats

  "Contract deployed as CODE_SCALA " should "executes serially" in {

    val sysName = "121000005l35120456.node1"
    val dbTag = "121000005l35120456.node1"
    //建立PeerManager实例是为了调用transactionCreator(需要用到密钥签名)，无他
    val pm = system.actorOf(ModuleManagerOfCFRD.props("modulemanager", sysName, false, false, false), "modulemanager")

    // 部署资产管理
    val s1 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/ContractAssetsTPL2.scala")
    val l1 = try s1.mkString finally s1.close()

    // 部署账户管理合约
    val s2 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/ContractCert.scala")
    val l2 = try s2.mkString finally s2.close()

    // 账户初始化赋值
    val sm: SetMap = Map("121000005l35120456" -> 50, "12110107bi45jh675g" -> 50, "122000002n00123567" -> 50)
    val sms = write(sm)

    // 测试集
    val tcs = Array(
      Transfer("121000005l35120456", "12110107bi45jh675g", 5),
      Transfer("121000005l35120456", "12110107bi45jh675g", 3),
      Transfer("121000005l35120456", "12110107bi45jh675g", 2)
    )

    // 结果集
    val rcs = Array(None, None, None)

    //val aa = new ContractCert
    // 账户与证书
    val signer = Signer("node2", "12110107bi45jh675g", "13856789234", Seq("node2"))
    val cert = scala.io.Source.fromFile("jks/certs/12110107bi45jh675g.node2.cer")
    val certStr = try cert.mkString finally cert.close()
    val certinfo = CertInfo("12110107bi45jh675g", "node2", Certificate(certStr, "SHA256withECDSA", true, None, None))

    //准备探针以验证调用返回结果
    val probe = TestProbe()
    val db = ImpDataAccess.GetDataAccess(sysName)
    val sandbox = system.actorOf(TransactionDispatcher.props("transactiondispatcher"), "transactiondispatcher")

    val cid1 = ChaincodeId("ContractAssetsTPL2", 1)
    val cid2 = ChaincodeId(SystemProfile.getAccountChaincodeName, 1)

    // 生成deploy交易，部署ContractAssetsTPL2
    val t1 = PeerHelper.createTransaction4Deploy(sysName, cid1, l1, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA)
    val msg_send1 = DoTransaction(t1, "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send1)
    val msg_recv1 = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    msg_recv1.err.isEmpty should be(true)

    // 生成deploy交易，部署ContractCert
    val t2 = PeerHelper.createTransaction4Deploy(sysName, cid2, l2, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA)
    val msg_send2 = DoTransaction(t2, "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send2)
    val msg_recv2 = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    msg_recv2.err.isEmpty should be(true)

    // 生成invoke交易
    // 注册账户
    val t3 = PeerHelper.createTransaction4Invoke(sysName, cid2, ACTION.SignUpSigner, Seq(write(signer)))
    val msg_send3 = DoTransaction(t3, "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send3)
    val msg_recv3 = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    msg_recv3.err.isEmpty should be(true)

    // 注册证书
    val t4 = PeerHelper.createTransaction4Invoke(sysName, cid2, ACTION.SignUpCert, Seq(writePretty(certinfo)))
    val msg_send4 = DoTransaction(t4, "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send4)
    val msg_recv4 = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    msg_recv4.err.isEmpty should be(true)

    //生成invoke交易
    val t5 = PeerHelper.createTransaction4Invoke(sysName, cid1, ACTION.set, Seq(sms))
    val msg_send5 = DoTransaction(t5, "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send5)
    val msg_recv5 = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    msg_recv5.err.isEmpty should be(true)

    for (i <- 0 until tcs.length) {
      val t6 = PeerHelper.createTransaction4Invoke(sysName, cid1, ACTION.transfer, Seq(write(tcs(i))))
      val msg_send6 = DoTransaction(t6, "dbnumber", TypeOfSender.FromAPI)
      probe.send(sandbox, msg_send6)
    }

    for (i <- 0 until tcs.length) {
      val msg_recv6 = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
      msg_recv6.err.isEmpty should be(true)
      // println(msg_recv6.r.reason)
      msg_recv6.r shouldBe (null)
    }
  }
}
