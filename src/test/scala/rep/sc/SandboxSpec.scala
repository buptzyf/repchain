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
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, FunSuiteLike, Matchers}
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType
import rep.crypto.cert.{CryptoMgr, SignTool}
import rep.network.autotransaction.PeerHelper
import rep.network.module.cfrd.ModuleManagerOfCFRD

import scala.concurrent.duration._
import scala.collection.mutable.Map
import rep.sc.SandboxDispatcher.DoTransaction

object SandboxSpec {

  type SetMap = scala.collection.mutable.Map[String, Int]

  object ACTION {
    val transfer = "transfer"
    val set = "set"
    val SignUpSigner = "SignUpSigner"
    val SignUpCert = "SignUpCert"
    val UpdateCertStatus = "UpdateCertStatus"
    val UpdateSigner = "UpdateSigner"
  }

}

/**
  * author zyf
  *
  * @param _system
  */
class SandboxSpec(_system: ActorSystem) extends TestKit(_system) with Matchers with FunSuiteLike with BeforeAndAfterAll {
/*
  def this() = this(ActorSystem("TransferSpec", new ClusterSystem("121000005l35120456.node1", InitType.MULTI_INIT, false).getConf))

  override def afterAll: Unit = {
    shutdown(system)
  }

  // or native.Serialization
  implicit val serialization = jackson.Serialization
  implicit val formats = DefaultFormats

  test("判断合约容器返回交易执行结果的类型") {
    val sysName = "121000005l35120456.node1"
    val superAdmin = "951002007l78123233.super_admin"
    // 初始化配置项，主要是为了初始化存储路径
    SystemProfile.initConfigSystem(system.settings.config, sysName)
    // 加载node1的私钥
    SignTool.loadPrivateKey(sysName, "123", s"${CryptoMgr.getKeyFileSuffix.substring(1)}/" + sysName + "${CryptoMgr.getKeyFileSuffix}")
    // 加载super_admin的私钥
    SignTool.loadPrivateKey(superAdmin, "super_admin", s"${CryptoMgr.getKeyFileSuffix.substring(1)}/" + superAdmin + "${CryptoMgr.getKeyFileSuffix}")

    //准备探针以验证调用返回结果
    val probe = TestProbe()
    val sandbox = system.actorOf(TransactionDispatcher.props("transactiondispatcher"), "transactiondispatcher")
    // 部署账户管理合约
    val cid = ChaincodeId(SystemProfile.getAccountChaincodeName, 1)
    val s1 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/did/RdidOperateAuthorizeTPL.scala")
    val l1 = try s1.mkString finally s1.close()
    val t1 = PeerHelper.createTransaction4Deploy(superAdmin, cid, l1, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA, ContractClassification.CONTRACT_SYSTEM)
    val msg_send1 = DoTransaction(Seq[Transaction](t1), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send1)
    val msg_recv1 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    // 判断合约容器的返回类型
    msg_recv1 shouldBe a[Seq[TransactionResult]]
  }

 */
}
