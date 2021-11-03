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
import org.json4s.native.Serialization.{write, writePretty}
import org.json4s.{DefaultFormats, jackson}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import rep.app.conf.SystemProfile
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType
import rep.crypto.cert.SignTool
import rep.network.autotransaction.PeerHelper
import rep.protos.peer.ChaincodeDeploy.ContractClassification
import rep.protos.peer._
import rep.sc.SandboxDispatcher.DoTransaction
import rep.sc.tpl.SupplyType._
import rep.utils.SerializeUtils.{deserialise, toJson}
import scalapb.json4s.JsonFormat

import scala.collection.mutable.Map
import scala.concurrent.Await
import scala.concurrent.duration._

/** 合约容器实现的单元测试
  *
  * @author c4w
  * @param _system 测试用例所在的actor System.
  *
  */
class SupplySpec2(_system: ActorSystem) extends TestKit(_system) with Matchers with FlatSpecLike with BeforeAndAfterAll {

  // or native.Serialization
  implicit val serialization = jackson.Serialization
  implicit val formats = DefaultFormats

  def this() = this(ActorSystem("SupplySpec2", new ClusterSystem("121000005l35120456.node1", InitType.MULTI_INIT, false).getConf))

  override def afterAll: Unit = Await.ready(system.terminate(), Duration.Inf)

  //Scala实现的资产管理合约测试，包括合约的部署、调用、结果返回验证
  "container" can "update contract & share kv" in {

    val sysName = "121000005l35120456.node1"
    val superAdmin = "951002007l78123233.super_admin"
    // 初始化配置项，主要是为了初始化存储路径
    SystemProfile.initConfigSystem(system.settings.config, sysName)
    // 加载node1的私钥
    SignTool.loadPrivateKey(sysName, "123", "jks/" + sysName + ".jks")
    // 加载super_admin的私钥
    SignTool.loadPrivateKey(superAdmin, "super_admin", "jks/" + superAdmin + ".jks")

    val fm: FixedMap = Map("A" -> 0.2, "B" -> 0.2, "C" -> 0.1, "D" -> 0.1)
    val sm: ShareMap = Map(
      "A" -> Array(ShareRatio(0, 100, 0.1, 0), ShareRatio(100, 10000, 0.15, 0)),
      "B" -> Array(ShareRatio(0, 10000, 0, 10)),
      "C" -> Array(ShareRatio(0, 10000, 0.1, 0)),
      "D" -> Array(ShareRatio(0, 100, 0, 10), ShareRatio(100, 10000, 0.15, 0))
    )
    val account_remain = "R"
    val account_sales1 = "S1"
    val account_sales2 = "S2"
    val product_id = "P201806270001"

    //构造签约交易合约模版1输入json字符串，销售1选择了合约模版1
    val ipt2 = IPTSignFixed(account_sales1, product_id, account_remain, fm)
    val l2 = write(ipt2)

    //构造签约交易合约模版2输入json字符串，销售2选择了合约模版2
    val ipt3 = IPTSignShare(account_sales2, product_id, account_remain, sm)
    val l3 = writePretty(ipt3)

    //准备探针以验证调用返回结果
    val probe = TestProbe()
    //    val db = ImpDataAccess.GetDataAccess(sysName)
    val sandbox = system.actorOf(TransactionDispatcher.props("transactiondispatcher"), "transactiondispatcher")

    //生成deploy交易
    //加载合约脚本
    val s1 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/SupplyTPL2.scala")
    val l1 = try s1.mkString finally s1.close()
    val cid = ChaincodeId("Supply", 2)
    val t1 = PeerHelper.createTransaction4Deploy(superAdmin, cid, l1, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA, ContractClassification.CONTRACT_CUSTOM)
    val msg_send1 = DoTransaction(Seq[Transaction](t1), "test-db", TypeOfSender.FromPreloader)
    probe.send(sandbox, msg_send1)
    val msg_recv1 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    val ol1 = msg_recv1(0).ol

    //生成invoke交易
    val t2 = PeerHelper.createTransaction4Invoke(superAdmin, cid, ACTION.SignFixed, Seq(l2))
    val msg_send2 = DoTransaction(Seq[Transaction](t2), "test-db", TypeOfSender.FromPreloader)
    probe.send(sandbox, msg_send2)
    val msg_recv2 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    //由于版本2支持SignFixed 分账方法,因此能够正确处理
    msg_recv2(0).getResult.code should be(0)

    var t = PeerHelper.createTransaction4Invoke(superAdmin, cid, ACTION.SignShare, Seq(l3))
    var msg_send = DoTransaction(Seq[Transaction](t), "test-db", TypeOfSender.FromPreloader)
    probe.send(sandbox, msg_send)
    var msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    //由于版本2不支持SignShare 分账方法,因此无法正确处理
    msg_recv(0).getResult.code should be(102)


    //部署版本3
    val s7 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/SupplyTPL3.scala")
    val l7 = try s7.mkString finally s7.close()
    val cid2 = new ChaincodeId("Supply", 3)
    t = PeerHelper.createTransaction4Deploy(superAdmin, cid2, l7, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA, ContractClassification.CONTRACT_CUSTOM)
    msg_send = DoTransaction(Seq[Transaction](t), "test-db", TypeOfSender.FromPreloader)
    probe.send(sandbox, msg_send)
    msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv(0).getResult.reason.isEmpty should be(true)

    t = PeerHelper.createTransaction4Invoke(superAdmin, cid2, ACTION.SignShare, Seq(l3))
    msg_send = DoTransaction(Seq[Transaction](t), "test-db", TypeOfSender.FromPreloader)
    probe.send(sandbox, msg_send)
    msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    //由于版本3支持SignShare 分账方法,因此正确处理
    msg_recv(0).getResult.reason.isEmpty should be(true)

    t = PeerHelper.createTransaction4Invoke(superAdmin, cid2, ACTION.SignFixed, Seq(l3))
    msg_send = DoTransaction(Seq[Transaction](t), "test-db", TypeOfSender.FromPreloader)
    probe.send(sandbox, msg_send)
    msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    //由于版本3不支持SignFiexed 分账方法,因此无法正确处理
    msg_recv(0).getResult.code should be(102)


    //测试各种金额下的分账结果
    val sr = Array(100, 200, 500, 1000)
    for (el <- sr) {
      //构造分账交易
      val ipt4 = IPTSplit(account_sales1, product_id, el)
      val l4 = write(ipt4)
      val t4 = PeerHelper.createTransaction4Invoke(superAdmin, cid, ACTION.Split, Seq(l4))
      val msg_send4 = DoTransaction(Seq[Transaction](t4), "test-db", TypeOfSender.FromPreloader)
      probe.send(sandbox, msg_send4)
      val msg_recv4 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
      val ol4 = msg_recv4(0).ol
      for (elem <- ol4) {
        val elemStr = JsonFormat.toJsonString(elem)
        println(s"oper log:$elemStr")
      }
      //分账之后总额应保持一致
      var total = 0
      ol4.foreach {
        ol =>
          total += deserialise(ol.newValue.toByteArray()).asInstanceOf[Int]
          if (ol.oldValue != null)
            total -= deserialise(ol.oldValue.toByteArray()).asInstanceOf[Int]
      }
      total should be(el)
    }

    for (el <- sr) {
      //构造分账交易
      val ipt4 = new IPTSplit(account_sales2, product_id, el)
      val l4 = write(ipt4)
      val t4 = PeerHelper.createTransaction4Invoke(superAdmin, cid2, ACTION.Split, Seq(l4))
      val msg_send4 = DoTransaction(Seq[Transaction](t4), "test-db", TypeOfSender.FromPreloader)
      probe.send(sandbox, msg_send4)
      val msg_recv4 = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
      val ol4 = msg_recv4(0).ol
      for (elem <- ol4) {
        val elemStr = JsonFormat.toJsonString(elem)
        println(s"oper log:$elemStr")
      }
      //分账之后总额应保持一致
      var total = 0
      ol4.foreach {
        ol =>
          total += deserialise(ol.newValue.toByteArray()).asInstanceOf[Int]
          //由于不同版本共享kv,前面的分账结果导致账户不为空
          ol.oldValue should not be null
          total -= deserialise(ol.oldValue.toByteArray()).asInstanceOf[Int]
      }
      total should be(el)
    }
  }
}
