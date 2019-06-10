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
import rep.protos.peer._
import rep.sc.Sandbox._
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType

import rep.network.PeerHelper
import org.json4s.{DefaultFormats, jackson}
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s._
import rep.network.module.ModuleManager
import rep.storage.ImpDataAccess
import rep.utils.SerializeUtils.deserialise

import java.nio.ByteBuffer

import java.io.IOException
import java.io.PrintWriter
import java.io.FileWriter
import java.io.File

import scala.collection.mutable.Map
import org.json4s.{DefaultFormats, Formats, jackson}
import org.json4s.native.Serialization.writePretty
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}

/** 合约容器实现的单元测试
  *
  * @author c4w
  * @param _system 测试用例所在的actor System.
  *
  */
class DeliveryAndStorageProofSpec(_system: ActorSystem)
  extends TestKit(_system)
    with Matchers
    with FlatSpecLike
    with BeforeAndAfterAll {

  import rep.sc.Sandbox.DoTransactionResult
  import rep.sc.SandboxDispatcher.DoTransaction
  import akka.testkit.TestProbe
  import akka.testkit.TestActorRef
  import Json4sSupport._
  import rep.sc.tpl.SupplyType._
  import rep.utils.SerializeUtils.toJson

  implicit val serialization = jackson.Serialization
  // or native.Serialization
  implicit val formats = DefaultFormats

  def this() = this(ActorSystem("DeliveryAndStorageSpec", new ClusterSystem("121000005l35120456.node1", InitType.MULTI_INIT, false).getConf))

  override def afterAll: Unit = Await.ready(system.terminate(), Duration.Inf)

  //Scala实现的资产管理合约测试，包括合约的部署、调用、结果返回验证
  "container" should "deploy supply contract and call it for splitting then" in {

    val sysName = "121000005l35120456.node1"
    val dbTag = "121000005l35120456.node1"
    //建立PeerManager实例是为了调用transactionCreator(需要用到密钥签名)，无他
    val pm = system.actorOf(ModuleManager.props("modulemanager", sysName, false, false, false), "modulemanager")

    //加载合约脚本
    val s1 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/DeliveryAndStorageProof.scala")
    val l1 = try s1.mkString finally s1.close()


    //准备探针以验证调用返回结果
    val probe = TestProbe()
    val db = ImpDataAccess.GetDataAccess(sysName)
    var sandbox = system.actorOf(TransactionDispatcher.props("transactiondispatcher"), "transactiondispatcher")

    //生成deploy交易
    val cid = new ChaincodeId("DeliveryAndStorageProof", 1)
    val t1 = PeerHelper.createTransaction4Deploy(sysName, cid, l1, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA)
    val msg_send1 = new DoTransaction(t1, "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send1)
    val msg_recv1 = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
     msg_recv1.err should be(None)
  }
}
