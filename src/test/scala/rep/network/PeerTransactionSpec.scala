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

package rep.network

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestKit
import com.trueaccord.scalapb.json.JsonFormat
import org.json4s.{DefaultFormats, jackson, _}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType
import rep.network.PeerHelper
import rep.network.module.ModuleManager
import rep.protos.peer._
import rep.sc._
import rep.storage.{ImpDataAccess, ImpDataPreload, ImpDataPreloadMgr}
import rep.utils.TimeUtils

import scala.concurrent.Await
import scala.concurrent.duration._


class PeerTransactionSpec(_system: ActorSystem)
  extends TestKit(_system)
    with Matchers
    with FlatSpecLike
    with BeforeAndAfterAll {

  import akka.testkit.TestProbe
  import rep.sc.TransProcessor.DoTransaction

  implicit val serialization = jackson.Serialization
  // or native.Serialization
  implicit val formats = DefaultFormats

  def this() = this(ActorSystem("SandBoxSpec",new ClusterSystem("1",InitType.MULTI_INIT,false).getConf))

  override def afterAll: Unit = Await.ready(system.terminate(), Duration.Inf)


  "peerTransaction" should "deploy functions and call them with 1 node then" in {
/*    val sysName = "1"
    val dbTag = "1"
    //建立PeerManager实例是为了调用transactionCreator(需要用到密钥签名)，无他
    val pm = system.actorOf(ModuleManager.props("pm",sysName))

    //获得创世块
    val blkJson = scala.io.Source.fromFile("json/gensis.json")
    val blkStr = try blkJson.mkString finally blkJson.close()
    val gen_blk = JsonFormat.fromJsonString[ Block ](blkStr)

    val preload :ImpDataPreload = ImpDataPreloadMgr.GetImpDataPreload(sysName,dbTag)

    val sd = scala.io.Source.fromFile("scripts/example_deploy.js")
    val ld = try sd.mkString finally sd.close()

    val sg = scala.io.Source.fromFile("scripts/example_gensis_invoke.js")
    val lg = try sg.mkString finally sg.close()

    val si1 = scala.io.Source.fromFile("scripts/example_invoke_1.js")
    val li1 = try si1.mkString finally si1.close()

    //    Runnable runnableT = new Runnable {
    //      override def run(): Unit = {
    //
    //      }
    //    }
    val probe = TestProbe()
    var sandbox = system.actorOf(TransProcessor.props("sandbox", sysName, probe.ref))
    //deploy
    val td = transactionCreator(sysName, rep.protos.peer.Transaction.Type.CHAINCODE_DEPLOY,
      "", "", List(), ld, None)
    val msg_send1 = new DoTransaction(td,probe.ref,dbTag)
    probe.send(sandbox, msg_send1)
    val msg_recv1 = probe.expectMsgType[ Sandbox.DoTransactionResult ](1000.seconds)

    //invoke set
    val cname = td.payload.get.chaincodeID.get.name
    val tg = transactionCreator(sysName, rep.protos.peer.Transaction.Type.CHAINCODE_INVOKE,
      "", lg, List(), "", Option(cname))
    val msg_send2 = new DoTransaction(tg,probe.ref,dbTag)
    probe.send(sandbox, msg_send2)
    val msg_recv2 = probe.expectMsgType[ Sandbox.DoTransactionResult ](1000.seconds)

    //invoke
    var loopCount = 10000 //模拟出块次数
    while (loopCount > 0) {
      val timeStart = TimeUtils.getCurrentTime()
      println(sysName + " : loopTime Start ~ " + loopCount + " - " + timeStart)
      var count = 50
      while (count > 0) {
        val t = transactionCreator(sysName, rep.protos.peer.Transaction.Type.CHAINCODE_INVOKE,
          "", li1, List(), "", Option(cname))
        val msg_send = new DoTransaction(t,probe.ref,dbTag)
        probe.send(sandbox, msg_send)
        count -= 1
      }
      count = 50
      while (count > 0) {
        val msg_recv = probe.expectMsgType[ Sandbox.DoTransactionResult ](1000.seconds)
        val rv = msg_recv.r.asInstanceOf[ JValue ]
        val re = rv.extract[ Int ]
        //        println(re)
        count -= 1
      }
      val timeEnd = TimeUtils.getCurrentTime()
      println(sysName + " : loopTime End ~ " + loopCount + " - " + timeEnd)
      println(sysName + " : loopTime Cost ~ " + loopCount + " - " + (timeEnd - timeStart))
      loopCount -= 1
    }*/
    0 should be(0)
  }
/*
  "peerTransaction" should "deploy functions and call them with 2 node then" in {
    val sysName = "2"
    val dbTag = "2"
    //建立PeerManager实例是为了调用transactionCreator(需要用到密钥签名)，无他
    val pm = system.actorOf(ModuleManager.props("pm",sysName))

    //获得创世块
    val blkJson = scala.io.Source.fromFile("json/gensis.json")
    val blkStr = try blkJson.mkString finally blkJson.close()
    val gen_blk = JsonFormat.fromJsonString[ Block ](blkStr)

    val preload :ImpDataPreload = ImpDataPreloadMgr.GetImpDataPreload(sysName,dbTag)

    val sd = scala.io.Source.fromFile("scripts/example_deploy.js")
    val ld = try sd.mkString finally sd.close()

    val sg = scala.io.Source.fromFile("scripts/example_gensis_invoke.js")
    val lg = try sg.mkString finally sg.close()

    val si1 = scala.io.Source.fromFile("scripts/example_invoke_2.js")
    val li1 = try si1.mkString finally si1.close()

    //    Runnable runnableT = new Runnable {
    //      override def run(): Unit = {
    //
    //      }
    //    }

    val probe = TestProbe()
    var sandbox = system.actorOf(TransProcessor.props("sandbox",sysName, probe.ref))
    //deploy
    val td = transactionCreator(sysName, rep.protos.peer.Transaction.Type.CHAINCODE_DEPLOY,
      "", "", List(), ld, None)
    val msg_send1 = new DoTransaction(td,probe.ref,dbTag)
    probe.send(sandbox, msg_send1)
    val msg_recv1 = probe.expectMsgType[ Sandbox.DoTransactionResult ](1000.seconds)

    //invoke set
    val cname = td.payload.get.chaincodeID.get.name
    val tg = transactionCreator(sysName, rep.protos.peer.Transaction.Type.CHAINCODE_INVOKE,
      "", lg, List(), "", Option(cname))
    val msg_send2 = new DoTransaction(tg,probe.ref,dbTag)
    probe.send(sandbox, msg_send2)
    val msg_recv2 = probe.expectMsgType[ Sandbox.DoTransactionResult ](1000.seconds)

    //invoke
    var loopCount = 10000 //模拟出块次数
    while (loopCount > 0) {
      val timeStart = TimeUtils.getCurrentTime()
      println(sysName + " : loopTime Start ~ " + loopCount + " - " + timeStart)
      var count = 50
      while (count > 0) {
        val t = transactionCreator(sysName, rep.protos.peer.Transaction.Type.CHAINCODE_INVOKE,
          "", li1, List(), "", Option(cname))
        val msg_send = new DoTransaction(t,probe.ref,dbTag)
        probe.send(sandbox, msg_send)
        count -= 1
      }
      count = 50
      while (count > 0) {
        val msg_recv = probe.expectMsgType[ Sandbox.DoTransactionResult ](1000.seconds)
        val rv = msg_recv.r.asInstanceOf[ JValue ]
        val re = rv.extract[ Int ]
        //        println(re)
        count -= 1
      }
      val timeEnd = TimeUtils.getCurrentTime()
      println(sysName + " : loopTime End ~ " + loopCount + " - " + timeEnd)
      println(sysName + " : loopTime Cost ~ " + loopCount + " - " + (timeEnd - timeStart))
      loopCount -= 1
    }
    0 should be(0)
  }

  "peerTransaction" should "deploy functions and call them with 3 node then" in {
    val sysName = "3"
    val dbTag = "3"
    //建立PeerManager实例是为了调用transactionCreator(需要用到密钥签名)，无他
    val pm = system.actorOf(ModuleManager.props("pm",sysName))

    //获得创世块
    val blkJson = scala.io.Source.fromFile("json/gensis.json")
    val blkStr = try blkJson.mkString finally blkJson.close()
    val gen_blk = JsonFormat.fromJsonString[ Block ](blkStr)

    val preload :ImpDataPreload = ImpDataPreloadMgr.GetImpDataPreload(sysName,dbTag)

    val sd = scala.io.Source.fromFile("scripts/example_deploy.js")
    val ld = try sd.mkString finally sd.close()

    val sg = scala.io.Source.fromFile("scripts/example_gensis_invoke.js")
    val lg = try sg.mkString finally sg.close()

    val si1 = scala.io.Source.fromFile("scripts/example_invoke_3.js")
    val li1 = try si1.mkString finally si1.close()

    //    Runnable runnableT = new Runnable {
    //      override def run(): Unit = {
    //
    //      }
    //    }

    val probe = TestProbe()
    var sandbox = system.actorOf(TransProcessor.props("sandbox",sysName,  probe.ref))
    //deploy
    val td = transactionCreator(sysName, rep.protos.peer.Transaction.Type.CHAINCODE_DEPLOY,
      "", "", List(), ld, None)
    val msg_send1 = new DoTransaction(td,probe.ref,dbTag)
    probe.send(sandbox, msg_send1)
    val msg_recv1 = probe.expectMsgType[ Sandbox.DoTransactionResult ](1000.seconds)

    //invoke set
    val cname = td.payload.get.chaincodeID.get.name
    val tg = transactionCreator(sysName, rep.protos.peer.Transaction.Type.CHAINCODE_INVOKE,
      "", lg, List(), "", Option(cname))
    val msg_send2 = new DoTransaction(tg,probe.ref,dbTag)
    probe.send(sandbox, msg_send2)
    val msg_recv2 = probe.expectMsgType[ Sandbox.DoTransactionResult ](1000.seconds)

    //invoke
    var loopCount = 10000 //模拟出块次数
    while (loopCount > 0) {
      val timeStart = TimeUtils.getCurrentTime()
      println(sysName + " : loopTime Start ~ " + loopCount + " - " + timeStart)
      var count = 50
      while (count > 0) {
        val t = transactionCreator(sysName, rep.protos.peer.Transaction.Type.CHAINCODE_INVOKE,
          "", li1, List(), "", Option(cname))
        val msg_send = new DoTransaction(t,probe.ref,dbTag)
        probe.send(sandbox, msg_send)
        count -= 1
      }
      count = 50
      while (count > 0) {
        val msg_recv = probe.expectMsgType[ Sandbox.DoTransactionResult ](1000.seconds)
        val rv = msg_recv.r.asInstanceOf[ JValue ]
        val re = rv.extract[ Int ]
        //        println(re)
        count -= 1
      }
      val timeEnd = TimeUtils.getCurrentTime()
      println(sysName + " : loopTime End ~ " + loopCount + " - " + timeEnd)
      println(sysName + " : loopTime Cost ~ " + loopCount + " - " + (timeEnd - timeStart))
      loopCount -= 1
    }
    0 should be(0)
  }*/
}

