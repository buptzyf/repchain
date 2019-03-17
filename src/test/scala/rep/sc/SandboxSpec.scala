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

import org.scalatest.{ BeforeAndAfterAll, FlatSpecLike, Matchers }
import akka.actor.ActorSystem
import akka.testkit.TestKit

import scala.concurrent.Await
import scala.concurrent.duration._
import rep.protos.peer._
import rep.sc.Sandbox._

import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType

import rep.network.PeerHelper
import org.json4s.{ DefaultFormats, jackson }
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s._
import rep.network.module.ModuleManager
import rep.storage.ImpDataAccess
import rep.utils.Json4s._
import rep.sc.contract._
import java.io.IOException
import java.io.PrintWriter
import java.io.FileWriter
import java.io.File

/** 合约容器实现的单元测试
 *  @author c4w
 *  @param _system 测试用例所在的actor System.
 * 
 */
class SandboxSpec(_system: ActorSystem)
  extends TestKit(_system)
  with Matchers
  with FlatSpecLike
  with BeforeAndAfterAll {

  import rep.sc.TransProcessor.DoTransaction
  import rep.sc.Sandbox.DoTransactionResult

  import akka.testkit.TestProbe
  import akka.testkit.TestActorRef
  import Json4sSupport._

  implicit val serialization = jackson.Serialization
  // or native.Serialization
  implicit val formats = DefaultFormats

  def this() = this(ActorSystem("SandBoxSpec", new ClusterSystem("1", InitType.MULTI_INIT, false).getConf))

  override def afterAll: Unit = Await.ready(system.terminate(), Duration.Inf)

  //Scala实现的资产管理合约测试，包括合约的部署、调用、结果返回验证
  "container" should "deploy scala contract and call it then" in {
    val sysName = "1"
    val dbTag = "1"
    //加载合约脚本
    //    val s1 = scala.io.Source.fromFile("src/main/scala/ContractAssetsTPL.scala")
    val s1 = scala.io.Source.fromFile("src/main/scala/NewContract.scala")
    val l1 = try s1.mkString finally s1.close()

    //val clazz = Compiler.compilef(l1,null)

    //初始化资产
    val l2 =
      """
{
  "1AqZs6vhcLiiTvFxqS5CEqMw6xWuX9xqyi" : 1000000,
  "1GvvHCFZPajq5yVY44n7bdmSfv2MJ5LyLs" : 1000000,
  "16SrzMbzdLyGEUKY5FsdE8SVt5tQV1qmBY" : 100,
  "12kAzqqhuq7xYgm9YTph1b9zmCpZPyUWxf" : 1000000,
  "1MH9xedPTkWThJUgT8ZYehiGCM7bEZTVGN" : 1000000
}
"""
    //加载合约调用脚本
    val l3 =
      """
{
  "from" : "1AqZs6vhcLiiTvFxqS5CEqMw6xWuX9xqyi",
  "to" : "1GvvHCFZPajq5yVY44n7bdmSfv2MJ5LyLs",
  "amount" : 50
}        
      """
    //准备探针以验证调用返回结果
    val probe = TestProbe()
    val db = ImpDataAccess.GetDataAccess(sysName)
    var sandbox = system.actorOf(TransProcessor.props("sandbox", "", probe.ref))
    //生成deploy交易
    def encodeHex(src: Array[Byte]): String = {
      val stringBuilder = new StringBuilder("")
      if (src == null || src.length <= 0) {
        return null;
      }
      for (x <- src) {
        val v = x & 0xFF
        val hv = Integer.toHexString(v).toUpperCase()
        if (hv.length() < 2) {
          stringBuilder.append(0)
        }
        stringBuilder.append(hv)
      }
      return stringBuilder.toString()
    }
    val cid = new ChaincodeId("Supply",1)
    val t1 = PeerHelper.createTransaction4Deploy(sysName, cid,
       l1, "",5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA)
    val msg_send1 = new DoTransaction(t1, probe.ref, "")
    probe.send(sandbox, msg_send1)
    val msg_recv1 = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    val ol1 = msg_recv1.ol
    val ol1str = compactJson(ol1)

    //生成invoke交易
    //获取deploy生成的chainCodeId
    //初始化资产
    val t2 = PeerHelper.createTransaction4Invoke(sysName,cid, "set", Seq(l2))
    val msg_send2 = new DoTransaction(t2, probe.ref, "")
    probe.send(sandbox, msg_send2)
    val msg_recv2 = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)

    val t3 = PeerHelper.createTransaction4Invoke(sysName,cid, "transfer", Seq(l3))
    val msg_send3 = new DoTransaction(t3, probe.ref, "")

    for (i <- 1 to 10) {
      println(s"----Span doTransaction-----")
      probe.send(sandbox, msg_send3)
      val msg_recv3 = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
      val ol3 = msg_recv3.ol
      val ol3str = compactJson(ol3)
      //获得交易返回值
      val rv3 = msg_recv3.r.asInstanceOf[JValue]
      val re3 = rv3.extract[String]
      re3 should be("transfer ok")
    }
  }

}
