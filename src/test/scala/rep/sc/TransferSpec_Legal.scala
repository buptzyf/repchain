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
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType
import rep.network.autotransaction.PeerHelper
import rep.network.module.cfrd.ModuleManagerOfCFRD
import rep.protos.peer.{Certificate, ChaincodeId, Signer, Transaction}
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
class TransferSpec_Legal(_system: ActorSystem) extends TestKit(_system) with Matchers with FlatSpecLike with BeforeAndAfterAll {

  case class Transfer(from: String, to: String, amount: Int, remind: String)

  def this() = this(ActorSystem("TransferSpec", new ClusterSystem("121000005l35120456.node1", InitType.MULTI_INIT, false).getConf))

  override def afterAll: Unit = {
    shutdown(system)
  }


  "ContractAssetsTPL" should "can set assets and transfer from a to b" in {

    val sysName = "121000005l35120456.node1"
    val dbTag = "121000005l35120456.node1"
    //建立PeerManager实例是为了调用transactionCreator(需要用到密钥签名)，无他
    val pm = system.actorOf(ModuleManagerOfCFRD.props("modulemanager", sysName, false, false, false), "modulemanager")

    // 部署资产管理
    val s1 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/ContractAssets_Legal.scala")
    val l1 = try s1.mkString finally s1.close()
    // 部署账户管理合约
    val s2 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/ContractCert.scala")
    val l2 = try s2.mkString finally s2.close()
    val sm: SetMap = Map("121000005l35120456" -> 50, "12110107bi45jh675g" -> 50, "122000002n00123567" -> 50)
    val sms = toJson(sm)

    //val aa = new ContractCert
    val signer = Signer("node2", "12110107bi45jh675g", "13856789234", Seq("node2"))
    val cert = scala.io.Source.fromFile("jks/certs/12110107bi45jh675g.node2.cer")
    val certStr = try cert.mkString finally cert.close()
    val certinfo = CertInfo("12110107bi45jh675g", "node2", Certificate(certStr, "SHA1withECDSA", true, None, None))
    //准备探针以验证调用返回结果
    val probe = TestProbe()
    val db = ImpDataAccess.GetDataAccess(sysName)
    val sandbox = system.actorOf(TransactionDispatcher.props("transactiondispatcher"), "transactiondispatcher")

    val cid2 = ChaincodeId(SystemProfile.getAccountChaincodeName, 1)
    val cid1 = ChaincodeId("ContractAssetsTPL_Legal", 1)

    //生成deploy交易
    val t1 = PeerHelper.createTransaction4Deploy(sysName, cid1, l1, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA)
    val msg_send1 = DoTransaction(Seq[Transaction](t1), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send1)
    val msg_recv1 = probe.expectMsgType[Seq[Sandbox.DoTransactionResult]](1000.seconds)
    msg_recv1(0).err should be(None)

    val t2 = PeerHelper.createTransaction4Deploy(sysName, cid2, l2, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA)
    val msg_send2 = DoTransaction(Seq[Transaction](t2), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send2)
    val msg_recv2 = probe.expectMsgType[Seq[Sandbox.DoTransactionResult]](1000.seconds)
    msg_recv2(0).err.isEmpty should be(true)

    // 生成invoke交易
    // 注册账户
    val t3 = PeerHelper.createTransaction4Invoke(sysName, cid2, ACTION.SignUpSigner, Seq(toJson(signer)))
    val msg_send3 = DoTransaction(Seq[Transaction](t3), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send3)
    val msg_recv3 = probe.expectMsgType[Seq[Sandbox.DoTransactionResult]](1000.seconds)
    msg_recv3(0).err.isEmpty should be(true)

    // 注册证书
    val t4 = PeerHelper.createTransaction4Invoke(sysName, cid2, ACTION.SignUpCert, Seq(toJson(certinfo)))
    val msg_send4 = DoTransaction(Seq[Transaction](t4), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send4)
    val msg_recv4 = probe.expectMsgType[Seq[Sandbox.DoTransactionResult]](1000.seconds)
    msg_recv4(0).err.isEmpty should be(true)


    //生成invoke交易
    val t5 = PeerHelper.createTransaction4Invoke(sysName, cid1, ACTION.set, Seq(sms))
    val msg_send5 = DoTransaction(Seq[Transaction](t5), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send5)
    val msg_recv5 = probe.expectMsgType[Seq[Sandbox.DoTransactionResult]](1000.seconds)
    msg_recv5(0).r.code should be(1)

    //获得提醒文本
    var p = Transfer("121000005l35120456", "12110107bi45jh675g", 5, null)
    var t = PeerHelper.createTransaction4Invoke(sysName, cid1, ACTION.transfer, Seq(toJson(p)))
    var msg_send = DoTransaction(Seq[Transaction](t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    var msg_recv = probe.expectMsgType[Seq[Sandbox.DoTransactionResult]](1000.seconds)
    msg_recv(0).r.code should be(2)

    //对提醒文本签名
    val remind = msg_recv(0).r.reason
    println(remind)
    p = Transfer("121000005l35120456", "12110107bi45jh675g", 5, remind)
    t = PeerHelper.createTransaction4Invoke(sysName, cid1, ACTION.transfer, Seq(toJson(p)))
    msg_send = DoTransaction(Seq[Transaction](t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    msg_recv = probe.expectMsgType[Seq[Sandbox.DoTransactionResult]](1000.seconds)
    msg_recv(0).r.code should be(1)

  }
}
