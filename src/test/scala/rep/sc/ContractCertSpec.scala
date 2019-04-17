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

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestKit, TestProbe}
import org.json4s.jackson.Serialization
import org.json4s.native.Serialization.{write, writePretty}
import org.json4s.{DefaultFormats, jackson}
import org.scalatest._
import rep.app.conf.SystemProfile
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType
import rep.network.PeerHelper
import rep.network.module.ModuleManager
import rep.protos.peer.{Certificate, ChaincodeId, Signer}
import rep.sc.TransProcessor.DoTransaction
import rep.sc.TransferSpec.ACTION
import rep.sc.tpl.{CertInfo, CertStatus}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.io.BufferedSource


/**
  * @author zyf
  * @param _system
  */
class ContractCertSpec (_system: ActorSystem)
  extends TestKit(_system) with Matchers with FunSuiteLike with BeforeAndAfterAll {

  def this() = this(ActorSystem("TransferSpec", new ClusterSystem("121000005l35120456.node1", InitType.MULTI_INIT, false).getConf))

  override def afterAll: Unit = { shutdown(system) }

  implicit val serialization: Serialization.type = jackson.Serialization
  // or native.Serialization
  implicit val formats: DefaultFormats.type = DefaultFormats

  val sysName = "121000005l35120456.node1"
  val dbTag = "121000005l35120456.node1"
  val cid =  ChaincodeId(SystemProfile.getAccountChaincodeName,1)
  //建立PeerManager实例是为了调用transactionCreator(需要用到密钥签名)，无他
  val pm: ActorRef = system.actorOf(ModuleManager.props("modulemanager", sysName, false, false,false), "modulemanager")

  val signers : Array[Signer] = Array(
    Signer("node1","121000005l35120456","18912345678",List("node1")) ,
    Signer("node2","12110107bi45jh675g","18912345678",List("node2")) ,
    Signer("node3","122000002n00123567","18912345678",List("node3", "zyf")) ,
    Signer("node4","921000005k36123789","18912345678",List("node4", "c4w")) ,
    Signer("node5","921000006e0012v696","18912345678",List("node5")) ,
    Signer("super_admin","951002007l78123233","18912345678",List("super_admin"))
  )

  val certNode1: BufferedSource = scala.io.Source.fromFile("jks/certs/121000005l35120456.node1.cer")
  val certStr1: String = try certNode1.mkString finally  certNode1.close()
  val certNode2: BufferedSource = scala.io.Source.fromFile("jks/certs/12110107bi45jh675g.node2.cer")
  val certStr2: String = try certNode2.mkString finally  certNode2.close()
  val certNode3: BufferedSource = scala.io.Source.fromFile("jks/certs/122000002n00123567.node3.cer")
  val certStr3: String = try certNode3.mkString finally  certNode3.close()
  val certNode4: BufferedSource = scala.io.Source.fromFile("jks/certs/921000005k36123789.node4.cer")
  val certStr4: String = try certNode4.mkString finally  certNode4.close()
  val certNode5: BufferedSource = scala.io.Source.fromFile("jks/certs/921000006e0012v696.node5.cer")
  val certStr5: String = try certNode5.mkString finally  certNode5.close()
  val certs : mutable.Map[String, String] = mutable.Map("node1" -> certStr1,"node2" -> certStr2,"node3" -> certStr3,"node4" -> certStr4,"node5" -> certStr5)


  //准备探针以验证调用返回结果
  val probe = TestProbe()
  private val sandbox = system.actorOf(TransProcessor.props("sandbox",  probe.ref))

  // 部署合约
  test("Deploy ContractCertTPL") {
    // 部署账户管理合约
    val contractCert = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/ContractCert.scala")
    val contractCertStr = try contractCert.mkString finally  contractCert.close()
    val t = PeerHelper.createTransaction4Deploy(sysName,cid ,
      contractCertStr, "",5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA)
    val msg_send = DoTransaction(t,  "api_"+t.id)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    assert(msg_recv.r.code == 1)
  }

  // 注册node1 账户
  test("ContractCert should can signUp the signerNode1"){
    val signerNode1 = signers(0)
    // 注册账户
    val t =  PeerHelper.createTransaction4Invoke(sysName,cid, ACTION.SignUpSigner, Seq(write(signerNode1)))
    val msg_send = DoTransaction(t,   "api_"+t.id)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    msg_recv.r.code should be (1)
  }

  // 注册node1 证书
  test("ContractCert should can signUp the node1.cer") {
    val certInfo = CertInfo("121000005l35120456", "node1", Certificate(certs.getOrElse("node1", "default"), "SHA256withECDSA", certValid = true , None, None) )
    val t =  PeerHelper.createTransaction4Invoke(sysName,cid, ACTION.SignUpCert, Seq(writePretty(certInfo)))
    val msg_send = DoTransaction(t,   "api_"+t.id)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    msg_recv.r.code should be (1)
  }

  // 重复注册一个证书会提示错误
  test("ContractCert should can't signUp the same Certificate") {
    val certInfo = CertInfo("121000005l35120456", "node1", Certificate(certs.getOrElse("node1", "default"), "SHA256withECDSA", certValid = true , None, None) )
    val t =  PeerHelper.createTransaction4Invoke(sysName,cid, ACTION.SignUpCert, Seq(writePretty(certInfo)))
    val msg_send = DoTransaction(t,   "api_"+t.id)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    msg_recv.r.code should be (0)
    msg_recv.r.reason should be ("证书已存在")
  }

  // 不能将证书注册到一个不存在的账户
  test("ContractCert should can't signUp the node1.cer to signerNode2 which not exists") {
    val certInfo = CertInfo("12110107bi45jh675g", "node2", Certificate(certs.getOrElse("node1", "default"), "SHA256withECDSA", certValid = true , None, None) )
    val t =  PeerHelper.createTransaction4Invoke(sysName,cid, ACTION.SignUpCert, Seq(writePretty(certInfo)))
    val msg_send = DoTransaction(t,   "api_"+t.id)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    msg_recv.r.code should be (0)
    msg_recv.r.reason should be ("账户不存在")
  }

  // 将证书2追加到 node1的账户中，使用node2作为certName
  test("ContractCert can signUp the node2.cer to signerNode1") {
    val certInfo = CertInfo("121000005l35120456", "node2", Certificate(certs.getOrElse("node2", "default"), "SHA256withECDSA", certValid = true , None, None) )
    val t =  PeerHelper.createTransaction4Invoke(sysName,cid, ACTION.SignUpCert, Seq(writePretty(certInfo)))
    val msg_send = DoTransaction(t,   "api_"+t.id)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    msg_recv.r.code should be (1)
  }

  // 更新证书状态,将刚刚追加的node2的证书状态修改为false
  test("updateCertStatus，update the certStatus which exists") {
    val certStatus = CertStatus("121000005l35120456", "node2", status = false )
    val t =  PeerHelper.createTransaction4Invoke(sysName,cid, ACTION.UpdateCertStatus, Seq(writePretty(certStatus)))
    val msg_send = DoTransaction(t,   "api_"+t.id)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    msg_recv.r.code should be (1)
  }

  // 更新证书状态，对应的证书不存在
  test("updateCertStatus，update the certStatus which not exists") {
    val certStatus = CertStatus("12110107bi45jh675g", "node2", status = false )
    val t =  PeerHelper.createTransaction4Invoke(sysName,cid, ACTION.UpdateCertStatus, Seq(writePretty(certStatus)))
    val msg_send = DoTransaction(t,   "api_"+t.id)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    msg_recv.r.code should be (0)
    msg_recv.r.reason should be ("证书不存在")
  }

  // 更新账户信息，将账户node1中的name改为node2，并且修改手机号
  test("modify the signer's information") {
    val signerNode1 = Signer("node2","121000005l35120456","13112345678",List("node1"))
    // 修改账户
    val t =  PeerHelper.createTransaction4Invoke(sysName,cid, ACTION.UpdateSigner, Seq(write(signerNode1)))
    val msg_send = DoTransaction(t,   "api_"+t.id)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    msg_recv.r.code should be (1)
  }
}
