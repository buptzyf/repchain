package rep.sc.isCLWasm

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import org.scalatest._
import org.json4s.jackson.Serialization
import org.json4s.{DefaultFormats, jackson}
import rep.app.system.RepChainSystemContext
import rep.network.tools.PeerExtension
import rep.proto.rc2.{ChaincodeDeploy, ChaincodeId, TransactionResult}
import rep.sc.SandboxDispatcher.DoTransaction
import rep.sc.{TransactionDispatcher, TypeOfSender}
import rep.utils.{IdTool, SerializeUtils}
import scala.concurrent.duration._

import java.nio.file.{Files, Paths}

object IsCLWasmIntegrationTest {
  final val node1Name = "121000005l35120456.node1"
  final val superAdminName = "951002007l78123233.super_admin"
  final val superAdminKeypairPasswd = "super_admin"
}

class IsCLWasmIntegrationTest(_system: ActorSystem) extends TestKit(_system) with Matchers with FunSpecLike with BeforeAndAfterAll {

  import IsCLWasmIntegrationTest._

  def this() = this(ActorSystem("IsCLWasmIntegrationTest", new RepChainSystemContext(IsCLWasmIntegrationTest.node1Name).getConfig.getSystemConf))

  val ctx: RepChainSystemContext = new RepChainSystemContext(node1Name)
  val pe = PeerExtension(system)
  pe.setRepChainContext(ctx)

  override def afterAll: Unit = {
    shutdown(system)
    deleteCompiledChaincodeFile("simple_1.wasmbinary")
    deleteCompiledChaincodeFile("proof_1.wasmbinary")
    deleteCompiledChaincodeFile("erc20-like_1.wasmbinary")
    deleteCompiledChaincodeFile("erc20-like_2.wasmbinary")
  }

  implicit val serialization: Serialization.type = jackson.Serialization
  implicit val formats: DefaultFormats.type = DefaultFormats

  val superAdmin = ctx.getConfig.getIdentityNetName + IdTool.DIDPrefixSeparator + superAdminName
  val node1AccountId = ctx.getConfig.getIdentityNetName + IdTool.DIDPrefixSeparator + node1Name.split("\\.")(0)

  val keyFileSuffix = ctx.getCryptoMgr.getKeyFileSuffix
  val sha256 = ctx.getHashTool
  val transactionTool = ctx.getTransactionBuilder

  ctx.getSignTool.loadPrivateKey(
    superAdminName,
    superAdminKeypairPasswd,
    s"${keyFileSuffix.substring(1)}/${ctx.getConfig.getChainNetworkId}/" + superAdminName + s"${keyFileSuffix}"
  )

  val chaincodeContentPathPrefix = "src/test/scala/rep/sc/isCLWasm/"
  val chaincodeCompiledPathPrefix = "custom_contract/wasm/"
  val cidOfSimple = ChaincodeId("simple", 1)
  val cidOfProof = ChaincodeId("proof", 1)
  val cidOfErc20Like = ChaincodeId("erc20-like", 1)
  val contractStringOfSimple = readChaincodeContentFile("simple.txt")
  val contractStringOfProof = readChaincodeContentFile("proof.txt")
  val contractStringOfErc20Like = readChaincodeContentFile("erc20-like.txt")

  //准备探针以验证调用返回结果
  val probe = TestProbe()
  private val sandbox = system.actorOf(TransactionDispatcher.props("transactiondispatcher"), "transactiondispatcher")


  private def readChaincodeContentFile(fileNameSuffix: String) = {
    Files.readString(Paths.get(chaincodeContentPathPrefix + fileNameSuffix))
  }

  private def deleteCompiledChaincodeFile(fileNameSuffix: String) = {
    Files.deleteIfExists(Paths.get(
      chaincodeCompiledPathPrefix
        + ctx.getConfig.getIdentityNetName
        + IdTool.WorldStateKeySeparator
        + fileNameSuffix
    ))
  }

  describe("测试简单合约") {
    it("部署合约应能成功") {
      val t = transactionTool.createTransaction4Deploy(superAdmin, cidOfSimple, contractStringOfSimple, "", 5000,
        ChaincodeDeploy.CodeType.CODE_VCL_WASM, ChaincodeDeploy.RunType.RUN_SERIAL, //default RUN_SERIAL
        ChaincodeDeploy.StateType.STATE_BLOCK, //default STATE_BLOCK
        ChaincodeDeploy.ContractClassification.CONTRACT_CUSTOM, //default CONTRACT_CUSTOM
        0)
      val msg_send = DoTransaction(Seq(t), "db0", TypeOfSender.FromAPI)
      probe.send(sandbox, msg_send)
      val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
      msg_recv.head.err.get.reason.isEmpty should be(true)
      // 合约部署者id
      SerializeUtils.deserialise(
        msg_recv.head.statesSet.get(msg_recv.head.statesSet.keys.find(key => key.endsWith("simple")).get)
          .get.toByteArray()
      ).asInstanceOf[String] should include(superAdmin.split("\\.")(0))
      // 合约初始状态：启用
      SerializeUtils.deserialise(
        msg_recv.head.statesSet.get(msg_recv.head.statesSet.keys.find(key => key.endsWith("STATE")).get)
          .get.toByteArray()
      ).asInstanceOf[Boolean] should be(true)
      // 部署合约的交易id
      SerializeUtils.deserialise(
        msg_recv.head.statesSet.get(msg_recv.head.statesSet.keys.find(key => key.endsWith("simple_1")).get)
          .get.toByteArray()
      ).asInstanceOf[String] should include(t.id)
      Files.exists(Paths.get(chaincodeCompiledPathPrefix + s"${ctx.getConfig.getIdentityNetName}${IdTool.WorldStateKeySeparator}simple_1.wasmbinary")) should be(true)
    }

    it("重复部署已部署合约应失败") {
      // 第一次部署合约
      val t = transactionTool.createTransaction4Deploy(superAdmin, cidOfSimple, contractStringOfSimple, "", 5000,
        ChaincodeDeploy.CodeType.CODE_VCL_WASM, ChaincodeDeploy.RunType.RUN_SERIAL, //default RUN_SERIAL
        ChaincodeDeploy.StateType.STATE_BLOCK, //default STATE_BLOCK
        ChaincodeDeploy.ContractClassification.CONTRACT_CUSTOM, //default CONTRACT_CUSTOM
        0)
      var msg_send = DoTransaction(Seq(t), "db1", TypeOfSender.FromAPI)
      probe.send(sandbox, msg_send)
      var msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
      msg_recv.head.err.get.reason.isEmpty should be(true)

      // 再次部署相同合约
      msg_send = DoTransaction(Seq(t), "db1", TypeOfSender.FromAPI)
      probe.send(sandbox, msg_send)
      msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
      msg_recv.head.err.get.reason should include("存在重复的合约")
    }

    // 统一于此部署合约数据(存在于数据库实例"db2"中)
    val t = transactionTool.createTransaction4Deploy(superAdmin, cidOfSimple, contractStringOfSimple, "", 5000,
      ChaincodeDeploy.CodeType.CODE_VCL_WASM, ChaincodeDeploy.RunType.RUN_SERIAL, //default RUN_SERIAL
      ChaincodeDeploy.StateType.STATE_BLOCK, //default STATE_BLOCK
      ChaincodeDeploy.ContractClassification.CONTRACT_CUSTOM, //default CONTRACT_CUSTOM
      0)
    val msg_send = DoTransaction(Seq(t), "db2", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv.head.err.get.reason.isEmpty should be(true)

    // 以下测试例基于上述已部署合约数据(存在于数据库实例"db2"中)

    it("调用非写状态的合约方法应能成功") {
      val t = transactionTool.createTransaction4Invoke(
        superAdmin,
        cidOfSimple,
        "get_from_list",
        Seq[String]("[1,2,3]", "1")
      )
      val msg_send = DoTransaction(Seq(t), "db2", TypeOfSender.FromAPI)
      probe.send(sandbox, msg_send)
      val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
      msg_recv.head.err.get.reason.isEmpty should be(true)
    }

    it("调用写状态的合约方法应能成功") {
      val t = transactionTool.createTransaction4Invoke(
        superAdmin,
        cidOfSimple,
        "g",
        Seq[String]("1")
      )
      val msg_send = DoTransaction(Seq(t), "db2", TypeOfSender.FromAPI)
      probe.send(sandbox, msg_send)
      val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
      msg_recv.head.err.get.reason.isEmpty should be(true)
      // s1 should be a c instance of struct S:
      //  typedef struct { int a; _string b; } S;
      //  typedef struct { int _len; char *_data } _string;
      SerializeUtils.deserialise(msg_recv.head.statesSet.get(msg_recv.head.statesSet.keys.find(
        key => key.endsWith("s1" + InvokerOfIsCLWasm.STORAGE_VARIABLE_NAME_SUFFIX)
      ).get)
        .get.toByteArray).asInstanceOf[Array[Byte]].sameElements(Array[Byte](
        2, 0, 0, 0, // for int a whose value is 2
        3, 0, 0, 0, 97, 98, 99 // for _string b whose value is "abc"
      )) should be(true)
    }

    it("调用合约方法出错应能返回异常信息") {
      val t = transactionTool.createTransaction4Invoke(
        superAdmin,
        cidOfSimple,
        "g",
        Seq[String]("3") // index参数超出List(1,2,3)范围
      )
      val msg_send = DoTransaction(Seq(t), "db2", TypeOfSender.FromAPI)
      probe.send(sandbox, msg_send)
      val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
      msg_recv.head.err.get.reason should include("index out of bounds")
    }

    it("禁用合约应能成功") {
      var t = transactionTool.createTransaction4State(
        superAdmin,
        cidOfSimple,
        false
      )
      var msg_send = DoTransaction(Seq(t), "db2", TypeOfSender.FromAPI)
      probe.send(sandbox, msg_send)
      var msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
      msg_recv.head.err.get.reason.isEmpty should be(true)
      // 禁用前状态应为启用
      SerializeUtils.deserialise(
        msg_recv.head.statesGet.get(msg_recv.head.statesGet.keys.find(key => key.endsWith("STATE")).get)
          .get.toByteArray()
      ).asInstanceOf[Boolean] should be(true)
      // 禁用后状态应为禁用
      SerializeUtils.deserialise(
        msg_recv.head.statesSet.get(msg_recv.head.statesSet.keys.find(key => key.endsWith("STATE")).get)
          .get.toByteArray()
      ).asInstanceOf[Boolean] should be(false)

      // 还原状态为启用
      t = transactionTool.createTransaction4State(
        superAdmin,
        cidOfSimple,
        true
      )
      msg_send = DoTransaction(Seq(t), "db2", TypeOfSender.FromAPI)
      probe.send(sandbox, msg_send)
      msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
      msg_recv.head.err.get.reason.isEmpty should be(true)
      SerializeUtils.deserialise(
        msg_recv.head.statesSet.get(msg_recv.head.statesSet.keys.find(key => key.endsWith("STATE")).get)
          .get.toByteArray()
      ).asInstanceOf[Boolean] should be(true)
    }

    it("禁用合约后调用合约方法应失败") {
      // 先禁用合约
      var t = transactionTool.createTransaction4State(
        superAdmin,
        cidOfSimple,
        false
      )
      var msg_send = DoTransaction(Seq(t), "db2", TypeOfSender.FromAPI)
      probe.send(sandbox, msg_send)
      var msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
      msg_recv.head.err.get.reason.isEmpty should be(true)

      // 调用方法应失败
      t = transactionTool.createTransaction4Invoke(
        superAdmin,
        cidOfSimple,
        "g",
        Seq[String]("1")
      )
      msg_send = DoTransaction(Seq(t), "db2", TypeOfSender.FromAPI)
      probe.send(sandbox, msg_send)
      msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
      msg_recv.head.err.get.reason should include("合约处于禁用状态")
    }
  }

  describe("测试存证合约") {
    // 统一于此部署合约数据(存在于数据库实例"db2"中)
    val t = transactionTool.createTransaction4Deploy(superAdmin, cidOfProof, contractStringOfProof, "", 5000,
      ChaincodeDeploy.CodeType.CODE_VCL_WASM, ChaincodeDeploy.RunType.RUN_SERIAL, //default RUN_SERIAL
      ChaincodeDeploy.StateType.STATE_BLOCK, //default STATE_BLOCK
      ChaincodeDeploy.ContractClassification.CONTRACT_CUSTOM, //default CONTRACT_CUSTOM
      0)
    val msg_send = DoTransaction(Seq(t), "db2", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv.head.err.get.reason.isEmpty should be(true)

    // 以下测试例基于上述已部署合约数据(存在于数据库实例"db2"中)

    it("调用存证合约方法应能成功") {
      val t = transactionTool.createTransaction4Invoke(
        superAdmin,
        cidOfProof,
        "putProof",
        Seq[String]("\"key1\"", "\"value1\"")
      )
      val msg_send = DoTransaction(Seq(t), "db2", TypeOfSender.FromAPI)
      probe.send(sandbox, msg_send)
      val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
      msg_recv.head.err.get.reason.isEmpty should be(true)
      // proof should be a c instance of struct _map_string_string:
      //  typedef struct { int _capacity; int _len; _string *_key; _string *_value; } _map_string_string;
      //  typedef struct { int _len; char *_data } _string;
      SerializeUtils.deserialise(msg_recv.head.statesSet.get(msg_recv.head.statesSet.keys.find(
        key => key.endsWith("proof" + InvokerOfIsCLWasm.STORAGE_VARIABLE_NAME_SUFFIX)
      ).get)
        .get.toByteArray).asInstanceOf[Array[Byte]].sameElements(Array[Byte](
        4, 0, 0, 0, // for int _capacity whose value is 4
        1, 0, 0, 0, // for int _len whose value is 1
        4, 0, 0, 0, 107, 101, 121, 49, // for _string _key[0] whose value is "key1"
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        6, 0, 0, 0, 118, 97, 108, 117, 101, 49, // for _string _value[0] whose value is "value1"
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
      )) should be(true)
    }

    it("调用存证合约方法存证重复数据应失败") {
      // 存证数据
      var t = transactionTool.createTransaction4Invoke(
        superAdmin,
        cidOfProof,
        "putProof",
        Seq[String]("\"key2\"", "\"value2\"")
      )
      var msg_send = DoTransaction(Seq(t), "db2", TypeOfSender.FromAPI)
      probe.send(sandbox, msg_send)
      var msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
      msg_recv.head.err.get.reason.isEmpty should be(true)

      // 重复存证已存在的数据
      t = transactionTool.createTransaction4Invoke(
        superAdmin,
        cidOfProof,
        "putProof",
        Seq[String]("\"key2\"", "\"value3\"")
      )
      msg_send = DoTransaction(Seq(t), "db2", TypeOfSender.FromAPI)
      probe.send(sandbox, msg_send)
      msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
      msg_recv.head.err.get.reason should include("existed value for key: key2")
    }
  }

  describe("测试转账合约") {
    it("部署合约时自动调用初始化合约方法应能成功") {
      val t = transactionTool.createTransaction4Deploy(superAdmin, cidOfErc20Like, contractStringOfErc20Like, "", 5000,
        ChaincodeDeploy.CodeType.CODE_VCL_WASM, ChaincodeDeploy.RunType.RUN_SERIAL, //default RUN_SERIAL
        ChaincodeDeploy.StateType.STATE_BLOCK, //default STATE_BLOCK
        ChaincodeDeploy.ContractClassification.CONTRACT_CUSTOM, //default CONTRACT_CUSTOM
        0, "[\"1000000\"]")
      val msg_send = DoTransaction(Seq(t), "db3", TypeOfSender.FromAPI)
      probe.send(sandbox, msg_send)
      val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
      msg_recv.head.err.get.reason.isEmpty should be(true)
      // ledger should be a c instance of struct _map_string_int:
      //  typedef struct { int _capacity; int _len; _string *_key; int *_value; } _map_string_int;
      //  typedef struct { int _len; char *_data } _string;
      SerializeUtils.deserialise(
        msg_recv.head.statesSet.get(msg_recv.head.statesSet.keys.find(
          key => key.endsWith("ledger" + InvokerOfIsCLWasm.STORAGE_VARIABLE_NAME_SUFFIX)
        ).get).get.toByteArray
      ).asInstanceOf[Array[Byte]].sameElements(Array[Byte](
        4, 0, 0, 0, // for int _capacity whose value is 4
        1, 0, 0, 0, // for int _len whose value is 1
        // for _string _key[0] whose value is "identity-net:951002007l78123233"
        31, 0, 0, 0, 105, 100, 101, 110, 116, 105, 116, 121, 45, 110, 101, 116, 58, 57, 53, 49, 48, 48, 50, 48, 48, 55, 108, 55, 56, 49, 50, 51, 50, 51, 51,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        64, 66, 15, 0, // for int _value[0] whose value is 1000000
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
      )) should be(true)
    }

    // 统一在此部署合约数据(存在于数据库实例"db2"中)
    val t = transactionTool.createTransaction4Deploy(superAdmin, cidOfErc20Like, contractStringOfErc20Like, "", 5000,
      ChaincodeDeploy.CodeType.CODE_VCL_WASM, ChaincodeDeploy.RunType.RUN_SERIAL, //default RUN_SERIAL
      ChaincodeDeploy.StateType.STATE_BLOCK, //default STATE_BLOCK
      ChaincodeDeploy.ContractClassification.CONTRACT_CUSTOM, //default CONTRACT_CUSTOM
      0, "[\"1000000\"]")
    val msg_send = DoTransaction(Seq(t), "db2", TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msg_recv.head.err.get.reason.isEmpty should be(true)

    // 以下测试例基于上述已部署合约数据(存在于数据库实例"db2"中)

    it("调用转账合约方法应成功") {
      val t = transactionTool.createTransaction4Invoke(
        superAdmin,
        cidOfErc20Like,
        "transfer",
        Seq(s"""\"${node1AccountId}\"""", "100")
      )
      val msg_send = DoTransaction(Seq(t), "db2", TypeOfSender.FromAPI)
      probe.send(sandbox, msg_send)
      val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
      msg_recv.head.err.get.reason.isEmpty should be(true)
      // ledger should be a c instance of struct _map_string_int:
      //  typedef struct { int _capacity; int _len; _string *_key; int *_value; } _map_string_int;
      //  typedef struct { int _len; char *_data } _string;
      SerializeUtils.deserialise(msg_recv.head.statesSet.get(
        msg_recv.head.statesSet.keys.find(
          key => key.endsWith("ledger" + InvokerOfIsCLWasm.STORAGE_VARIABLE_NAME_SUFFIX)
        ).get)
        .get.toByteArray).asInstanceOf[Array[Byte]].sameElements(Array[Byte](
        4, 0, 0, 0, // for int _capacity whose value is 4
        2, 0, 0, 0, // for int _len whose value is 2
        // for _string _key[0] whose value is "identity-net:951002007l78123233"
        31, 0, 0, 0, 105, 100, 101, 110, 116, 105, 116, 121, 45, 110, 101, 116, 58, 57, 53, 49, 48, 48, 50, 48, 48, 55, 108, 55, 56, 49, 50, 51, 50, 51, 51,
        // for _string _key[1] whose value is "identity-net:121000005l35120456"
        31, 0, 0, 0, 105, 100, 101, 110, 116, 105, 116, 121, 45, 110, 101, 116, 58, 49, 50, 49, 48, 48, 48, 48, 48, 53, 108, 51, 53, 49, 50, 48, 52, 53, 54,
        0, 0, 0, 0, 0, 0, 0, 0,
        -36, 65, 15, 0, // for int _value[0] whose value is 999900
        100, 0, 0, 0, // for _value[1] whose value is 100
        0, 0, 0, 0, 0, 0, 0, 0
      )) should be(true)
    }

    it("调用转账合约方法超出余额时应失败") {
      val t = transactionTool.createTransaction4Invoke(
        superAdmin,
        cidOfErc20Like,
        "transfer",
        Seq(s"""\"${node1AccountId}\"""", "1000000000")
      )
      val msg_send = DoTransaction(Seq(t), "db2", TypeOfSender.FromAPI)
      probe.send(sandbox, msg_send)
      val msg_recv = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
      msg_recv.head.err.get.reason should include("no enough balance")
    }
  }
}