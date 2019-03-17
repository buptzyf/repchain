/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
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
import rep.network.PeerHelper
import rep.network.module.ModuleManager
import rep.protos.peer.ChaincodeId
import rep.sc.TransProcessor.DoTransaction
import rep.sc.TransferSpec.{ACTION, SetMap, Transfer}
import rep.sc.tpl.ContractAssetsTPL
import rep.storage.ImpDataAccess
import rep.utils.Json4s.compactJson

import scala.concurrent.duration._
import scala.collection.mutable.Map

object TransferSpec {

  case class Transfer(from: String, to: String, account: Int)
  type SetMap = scala.collection.mutable.Map[String,Int]

  object ACTION {
    val transfer = "transfer"
    val set = "set"
  }
}

/**
  * author zyf
  * @param _system
  */
class TransferSpec(_system: ActorSystem)
  extends TestKit(_system) with Matchers with FlatSpecLike with BeforeAndAfterAll {

  def this() = this(ActorSystem("TransferSpec", new ClusterSystem("121000005l35120456.node1", InitType.MULTI_INIT, false).getConf))

  override def afterAll: Unit = { shutdown(system) }

  implicit val serialization = jackson.Serialization
  // or native.Serialization
  implicit val formats = DefaultFormats

  "ContractAssetsTPL" should "can set assets and transfer from a to b" in {
    val sysName = "121000005l35120456.node1"
    val dbTag = "121000005l35120456.node1"
    //建立PeerManager实例是为了调用transactionCreator(需要用到密钥签名)，无他
    val pm = system.actorOf(ModuleManager.props("pm", sysName))
    val s1 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/ContractAssetsTPL.scala")
    val l1 = try s1.mkString finally s1.close()
    val sm: SetMap = Map("zyf" -> 50, "c4w" -> 50, "jby" -> 50)
    val sms = write(sm)
    val z2c = Transfer("zyf", "c4w", 5)
    val z2cs = write(z2c)
    val c2j = Transfer("c4w", "jby", 5)
    val c2js = writePretty(c2j)
    //准备探针以验证调用返回结果
    val probe = TestProbe()
    val db = ImpDataAccess.GetDataAccess(sysName)
    var sandbox = system.actorOf(TransProcessor.props("sandbox", "", probe.ref))

    //生成deploy交易
    val cid = ChaincodeId("ContractAssetsTPL",1)
    val t1 = PeerHelper.createTransaction4Deploy(sysName, cid,
      l1, "",5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA)

    val msg_send1 = DoTransaction(t1, probe.ref, "")
    probe.send(sandbox, msg_send1)
    val msg_recv1 = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    val ol1 = msg_recv1.ol
    val ol1str = compactJson(ol1)

    //生成invoke交易
    //获取deploy生成的chainCodeId
    //初始化资产
    val t2 = PeerHelper.createTransaction4Invoke(sysName,cid, ACTION.set, Seq(sms))
    val msg_send2 = DoTransaction(t2, probe.ref, "")
    probe.send(sandbox, msg_send2)
    val msg_recv2 = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)

    val t3 = PeerHelper.createTransaction4Invoke(sysName, cid, ACTION.transfer, Seq(z2cs))
    val msg_send3 = new DoTransaction(t3, probe.ref, "")
    probe.send(sandbox, msg_send3)
    val msg_recv3 = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)

    println(msg_recv3)
  }
}
