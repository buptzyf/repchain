package rep.sc.wasmer

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import org.apache.commons.codec.binary.BinaryCodec
import org.json4s.jackson.Serialization
import org.json4s.native.Serialization.write
import org.json4s.{DefaultFormats, jackson}
import org.scalatest._
import rep.app.system.RepChainSystemContext
import rep.network.tools.PeerExtension
import rep.proto.rc2.{ActionResult, CertId, Certificate, ChaincodeDeploy, ChaincodeId, Signer, TransactionResult}
import rep.sc.tpl.did.operation.SignerOperation
import rep.sc.tpl.did.operation.SignerOperation.SignerStatus
import scalapb.json4s.JsonFormat
import rep.sc.SandboxDispatcher.DoTransaction
import rep.sc.{TransactionDispatcher, TypeOfSender}
import rep.utils.{IdTool, SerializeUtils}
import org.json4s.{DefaultFormats, jackson}

import java.nio.file.{Files, Paths}
import java.util
import scala.concurrent.duration._
import scala.tools.nsc.io.Path



/**
 * 这个测试例在运行之前，请先运行repchain，创建创世块，建立账户管理和权限管理合约。
 * */
class ContractTestForWasmer(_system: ActorSystem) extends TestKit(_system) with Matchers with FunSuiteLike with BeforeAndAfterAll {


  def this() = this(ActorSystem("ContractTestForWasmer", new RepChainSystemContext("121000005l35120456.node1").getConfig.getSystemConf))

  val ctx: RepChainSystemContext = new RepChainSystemContext("121000005l35120456.node1")
  val pe = PeerExtension(system)
  pe.setRepChainContext(ctx)

  override def afterAll: Unit = {
    shutdown(system)
  }

  implicit val serialization: Serialization.type = jackson.Serialization
  implicit val formats: DefaultFormats.type = DefaultFormats

  val superAdmin = s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}951002007l78123233.super_admin"

  val keyFileSuffix = ctx.getCryptoMgr.getKeyFileSuffix
  val sha256 = ctx.getHashTool
  val transactionTool = ctx.getTransactionBuilder

  ctx.getSignTool.loadPrivateKey("951002007l78123233.super_admin", "super_admin", s"${keyFileSuffix.substring(1)}/${ctx.getConfig.getChainNetworkId}/" + "951002007l78123233.super_admin" + s"${keyFileSuffix}")

  val cid = ChaincodeId("simple", 1)
  val contractString = getContractCode("src/main/scala/rep/sc/tpl/wasmer/simple.wasm")

  //准备探针以验证调用返回结果
  val probe = TestProbe()
  private val sandbox = system.actorOf(TransactionDispatcher.props("transactiondispatcher"), "transactiondispatcher")

  // 部署合约
  test("第一次部署在wasm容器运行合约=simple contract 成功") {
    // 部署账户管理合约
    //部署是调用init函数，初始化两个账户的初始余额
    val list  = new util.ArrayList[String]()
    //Array[String]("121000005l35120456.node1","100000","12110107bi45jh675g.node2","100000")
    list.add("121000005l35120456.node1")
    list.add("100000")
    list.add("12110107bi45jh675g.node2")
    list.add("100000")

    val pp = SerializeUtils.toJson(list)
      //JsonFormat.toJsonString(list)
    val t = transactionTool.createTransaction4Deploy(superAdmin, cid, contractString, "", 5000,
                ChaincodeDeploy.CodeType.CODE_WASM, rep.proto.rc2.ChaincodeDeploy.RunType.RUN_SERIAL, //default RUN_SERIAL
                rep.proto.rc2.ChaincodeDeploy.StateType.STATE_BLOCK, //default STATE_BLOCK
                rep.proto.rc2.ChaincodeDeploy.ContractClassification.CONTRACT_CUSTOM, //default CONTRACT_CUSTOM
                0,pp)
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    assert(msg_recv.head.err.get.reason.isEmpty)
  }

  test("再次部署在wasm容器运行合约=simple contract 失败") {
    // 部署账户管理合约
    //部署是调用init函数，初始化两个账户的初始余额
    val list = new util.ArrayList[String]()
    //Array[String]("121000005l35120456.node1","100000","12110107bi45jh675g.node2","100000")
    list.add("121000005l35120456.node1")
    list.add("100000")
    list.add("12110107bi45jh675g.node2")
    list.add("100000")

    val pp = SerializeUtils.toJson(list)
    //JsonFormat.toJsonString(list)
    val t = transactionTool.createTransaction4Deploy(superAdmin, cid, contractString, "", 5000,
      ChaincodeDeploy.CodeType.CODE_WASM, rep.proto.rc2.ChaincodeDeploy.RunType.RUN_SERIAL, //default RUN_SERIAL
      rep.proto.rc2.ChaincodeDeploy.StateType.STATE_BLOCK, //default STATE_BLOCK
      rep.proto.rc2.ChaincodeDeploy.ContractClassification.CONTRACT_CUSTOM, //default CONTRACT_CUSTOM
      0, pp)
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    System.out.println(msg_recv.head.err.get.reason)
    assert(msg_recv.head.err.get.reason.equals("存在重复的合约Id"))
  }

  test("调用wasm容器的合约的转账方法=transfer 成功") {
    // 部署账户管理合约
    //部署是调用init函数，初始化两个账户的初始余额
    val list = new util.ArrayList[String]()
    //Array[String]("121000005l35120456.node1","100000","12110107bi45jh675g.node2","100000")
    list.add("121000005l35120456.node1")
    list.add("12110107bi45jh675g.node2")
    list.add("1")
    val t = transactionTool.createTransaction4Invoke(superAdmin, cid, chaincodeInputFunc = "transfer",
      Seq("121000005l35120456.node1","12110107bi45jh675g.node2","1"))

    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    System.out.println(msg_recv.head.err.get.reason)
    assert(msg_recv.head.err.get.reason.isEmpty)
  }

  test("禁用wasm容器的合约 成功") {
    //建立禁用合约交易
    val t = transactionTool.createTransaction4State(superAdmin, cid, false)

    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    System.out.println(msg_recv.head.err.get.reason)
    assert(msg_recv.head.err.get.reason.isEmpty)
  }

  test("合约已经禁用，无法调用合约方法，调用wasm容器的合约的转账方法=transfer 失败") {
    // 部署账户管理合约
    //部署是调用init函数，初始化两个账户的初始余额
    val list = new util.ArrayList[String]()
    //Array[String]("121000005l35120456.node1","100000","12110107bi45jh675g.node2","100000")
    list.add("121000005l35120456.node1")
    list.add("12110107bi45jh675g.node2")
    list.add("1")
    val t = transactionTool.createTransaction4Invoke(superAdmin, cid, chaincodeInputFunc = "transfer",
      Seq("121000005l35120456.node1", "12110107bi45jh675g.node2", "1"))

    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    System.out.println(msg_recv.head.err.get.reason)
    assert(!msg_recv.head.err.get.reason.isEmpty)
  }

  test("重新启用wasm容器的合约 成功") {
    //建立禁用合约交易
    val t = transactionTool.createTransaction4State(superAdmin, cid, true)
    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    System.out.println(msg_recv.head.err.get.reason)
    assert(msg_recv.head.err.get.reason.isEmpty)
  }

  test("合约重新启用，调用wasm容器的合约的转账方法=transfer 成功") {
    // 部署账户管理合约
    //部署是调用init函数，初始化两个账户的初始余额
    val list = new util.ArrayList[String]()
    //Array[String]("121000005l35120456.node1","100000","12110107bi45jh675g.node2","100000")
    list.add("121000005l35120456.node1")
    list.add("12110107bi45jh675g.node2")
    list.add("1")
    val t = transactionTool.createTransaction4Invoke(superAdmin, cid, chaincodeInputFunc = "transfer",
      Seq("121000005l35120456.node1", "12110107bi45jh675g.node2", "1"))

    val msg_send = DoTransaction(Seq(t), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    System.out.println(msg_recv.head.err.get.reason)
    assert(msg_recv.head.err.get.reason.isEmpty)
  }

  private def getContractCode(fn:String):String={
    val fb = Files.readAllBytes(Paths.get(fn))
    BinaryCodec.toAsciiString(fb)
  }

}