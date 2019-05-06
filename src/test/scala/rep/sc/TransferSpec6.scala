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

import akka.util.Timeout
import scala.concurrent.duration._
import akka.pattern.ask
import akka.pattern.AskTimeoutException
import scala.concurrent._

import akka.actor.{ActorSystem,ActorRef,ActorSelection}
import akka.testkit.{TestKit, TestProbe}
import org.json4s.{DefaultFormats, jackson}
import org.json4s.native.Serialization.{write, writePretty}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType
import rep.network.PeerHelper
import rep.network.module.ModuleManager
import rep.protos.peer.{Certificate, ChaincodeId, Signer}
import rep.sc.tpl.ContractCert3.CertInfo
import rep.sc.tpl.Transfer3
import rep.sc.TransferSpec.{SetMap,ACTION}
import rep.storage.ImpDataAccess
import rep.utils.SerializeUtils.toJson
import rep.app.conf.SystemProfile

import scala.concurrent.duration._
import scala.collection.mutable.Map
import rep.utils.GlobalUtils.ActorType
import rep.protos.peer.Transaction

import scala.concurrent.ExecutionContext.Implicits._
import rep.sc.TransProcessor.DoTransaction

import rep.sc.scalax.Compiler
import rep.sc.scalax.IContract
/**
  * author zyf
  * @param _system
  */
class TransferSpec6(_system: ActorSystem)
  extends TestKit(_system) with Matchers with FlatSpecLike with BeforeAndAfterAll {
  import scala.concurrent.duration._
  
  implicit val timeout = Timeout(3 seconds)
  
  
  
  def this() = this(ActorSystem("TransferSpec", new ClusterSystem("121000005l35120456.node1", InitType.MULTI_INIT, false).getConf))

  override def afterAll: Unit = { shutdown(system) }

  implicit val serialization = jackson.Serialization
  // or native.Serialization
  implicit val formats = DefaultFormats

   val s1 = scala.io.Source.fromFile("json/ContractCert6.scala")
   val l1 = try s1.mkString finally s1.close()
  "Contract deployed as CODE_SCALA_PARALLEL " should "executes in parallel" in {    
    val c = Compiler.compilef(l1,"ContractCert6")
    val obj = c.getConstructor().newInstance().asInstanceOf[IContract]
    
    val cert = scala.io.Source.fromFile("jks/certs/12110107bi45jh675g.node2.cer")
    val certStr = try cert.mkString finally  cert.close()
    val certinfo = CertInfo("12110107bi45jh675g", "node2", Certificate(certStr, "SHA1withECDSA", true, None, None) )

    obj.onAction(null, ACTION.SignUpCert, writePretty(certinfo))
  }
}