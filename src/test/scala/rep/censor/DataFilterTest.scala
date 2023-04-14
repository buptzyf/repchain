package rep.censor

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestKit, TestProbe}
import org.json4s.jackson.Serialization
import org.json4s.{DefaultFormats, jackson}
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike, Matchers}
import rep.app.system.RepChainSystemContext
import rep.crypto.Sha256
import rep.network.autotransaction.TransactionBuilder
import rep.network.consensus.common.MsgOfConsensus.BlockRestore
import rep.network.consensus.util.BlockHelp
import rep.network.module.ModuleActorType.ActorType
import rep.network.module.cfrd.ModuleManagerOfCFRD
import rep.network.persistence.IStorager.SourceOfBlock
import rep.network.tools.{PeerExtension, PeerExtensionImpl}
import rep.proto.rc2.{Block, ChaincodeDeploy, ChaincodeId, Transaction, TransactionResult}
import rep.sc.SandboxDispatcher.DoTransaction
import rep.sc.{TransactionDispatcher, TypeOfSender}
import rep.storage.chain.block.BlockSearcher
import rep.utils.{IdTool, SerializeUtils}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.DurationInt

class DataFilterTest(_system: ActorSystem) extends TestKit(_system) with Matchers with FunSuiteLike with BeforeAndAfterAll {
  def this() = this(ActorSystem("DataFilterTest", new RepChainSystemContext("121000005l35120456.node1").getConfig.getSystemConf))

  val ctx: RepChainSystemContext = new RepChainSystemContext("121000005l35120456.node1")

  val pe: PeerExtensionImpl = PeerExtension(system)
  pe.setRepChainContext(ctx)
  val moduleManager: ActorRef = system.actorOf(ModuleManagerOfCFRD.props("modulemanager", isStartup = false), "modulemanager")
  val blocker: ActorRef = system.actorOf(TransactionDispatcher.props("transactionDispatcher"), "transactionDispatcher")
  val dataFilter = new DataFilter(ctx)

  override def afterAll: Unit = {
    shutdown()
  }

  case class Conclusion(height: Long, blockHash: String, illegalTrans: Map[String, String])

  implicit val serialization: Serialization.type = jackson.Serialization
  implicit val formats: DefaultFormats.type = DefaultFormats

  val sysName = s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}121000005l35120456.node1"
  val superAdmin = s"${ctx.getConfig.getIdentityNetName}${IdTool.DIDPrefixSeparator}951002007l78123233.super_admin"

  val keyFileSuffix: String = ctx.getCryptoMgr.getKeyFileSuffix
  val sha256: Sha256 = ctx.getHashTool
  val transactionTool: TransactionBuilder = ctx.getTransactionBuilder

  ctx.getSignTool.loadPrivateKey("121000005l35120456.node1", "123", s"${keyFileSuffix.substring(1)}/${ctx.getConfig.getChainNetworkId}/" + "121000005l35120456.node1" + s"${keyFileSuffix}")

  ctx.getSignTool.loadPrivateKey("951002007l78123233.super_admin", "super_admin", s"${keyFileSuffix.substring(1)}/${ctx.getConfig.getChainNetworkId}/" + "951002007l78123233.super_admin" + s"${keyFileSuffix}")
  val blockSearcher: BlockSearcher = ctx.getBlockSearch

  val regulateTPL = ChaincodeId("RegulateTPL", 1)
  val contractAssetsTPL = ChaincodeId("ContractAssetsTPL", 1)

  val probe: TestProbe = TestProbe()

  private val sandbox = system.actorOf(TransactionDispatcher.props("transactiondispatcher"), "transactiondispatcher")
  var illegal_block_hash = ""
  var illegal_tid = ""
  val reason = "低俗谩骂"
  var illegal_block_height = 1L
  var preBlockHash = ""
  var blockHeight = 1L
  val da = "dbnumber1"

  test("部署ContractAssetsTPL合约(为了调用put_proof方法上链违规交易)") {
    val contractSource = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/ContractAssetsTPL.scala")
    val contractCertStr = try contractSource.mkString finally contractSource.close()
    val deployTrans = transactionTool.createTransaction4Deploy(superAdmin, contractAssetsTPL, contractCertStr,
      "", 5000, ChaincodeDeploy.CodeType.CODE_SCALA)
    val msg_send = DoTransaction(Seq(deployTrans), da, TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_result = probe.expectMsgType[Seq[TransactionResult]](3000.seconds)
    assert(msg_result.head.err.get.reason.isEmpty)

    val block = packBlock(preBlockHash, blockHeight, Seq(deployTrans), msg_result)
    probe.send(pe.getActorRef(ActorType.storager), BlockRestore(block, SourceOfBlock.TEST_PROBE, blocker))
    val result = probe.expectMsgType[Int](3000.seconds)
    assert(result == 0)
  }

  test("部署监管合约-RegulateTPL") {
    val contractSource = scala.io.Source.fromFile("src/main/scala/rep/censor/tpl/RegulateTPL.scala")
    val contractCertStr = try contractSource.mkString finally contractSource.close()
    val deployTrans = transactionTool.createTransaction4Deploy(superAdmin, regulateTPL, contractCertStr,
      "", 5000, ChaincodeDeploy.CodeType.CODE_SCALA)
    val msg_send = DoTransaction(Seq(deployTrans), da, TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val msg_result = probe.expectMsgType[Seq[TransactionResult]](3000.seconds)
    assert(msg_result.head.err.get.reason.isEmpty)

    val block = packBlock(preBlockHash, blockHeight, Seq(deployTrans), msg_result)
    probe.send(pe.getActorRef(ActorType.storager), BlockRestore(block, SourceOfBlock.TEST_PROBE, blocker))
    val result = probe.expectMsgType[Int](3000.seconds)
    assert(result == 0)
  }

  test("调用putProof方法上链一条违规交易") {
    val parm = Map("test123456" -> "假设这是一条违规交易，内容不宜展示")
    val illegalTrans = transactionTool.createTransaction4Invoke(superAdmin, contractAssetsTPL,
      chaincodeInputFunc = "putProof", params = Seq(SerializeUtils.toJson(parm)))
    val msg_send = DoTransaction(Seq(illegalTrans), da, TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val result_msg = probe.expectMsgType[Seq[TransactionResult]](3000.seconds)
    result_msg.head.err.get.reason.isEmpty should be(true)
    val block = packBlock(preBlockHash, blockHeight, Seq(illegalTrans), result_msg)

    probe.send(pe.getActorRef(ActorType.storager), BlockRestore(block, SourceOfBlock.TEST_PROBE, blocker))
    val result = probe.expectMsgType[Int](3000.seconds)
    assert(result == 0)

    illegal_block_height = block.getHeader.height
    illegal_block_hash = block.getHeader.hashPresent.toStringUtf8
    illegal_tid = illegalTrans.id
  }

  test("调用RegulateTPL.regBlock对违规交易进行监管") {
    val conclusion = Conclusion(illegal_block_height, illegal_block_hash, Map(illegal_tid -> reason))
    val array = new ArrayBuffer[Conclusion]()
    array += conclusion
    val regTrans = transactionTool.createTransaction4Invoke(superAdmin, regulateTPL,
      chaincodeInputFunc = "regBlocks", params = Seq(SerializeUtils.toJson(array)))
    val msg_send = DoTransaction(Seq(regTrans), da, TypeOfSender.FromAPI)
    probe.send(sandbox, msg_send)
    val result_msg = probe.expectMsgType[Seq[TransactionResult]](30000.seconds)
    result_msg.head.err.get.reason.isEmpty should be(true)

    val block = packBlock(preBlockHash, blockHeight, Seq(regTrans), result_msg)
    probe.send(pe.getActorRef(ActorType.storager), BlockRestore(block, SourceOfBlock.TEST_PROBE, blocker))
    val result = probe.expectMsgType[Int](3000.seconds)
    assert(result == 0)
  }

  test("查询区块，获取违规交易，调用DataFilter对区块和交易过滤，校验过滤结果") {
    val block = blockSearcher.getBlockByHeight(illegal_block_height).get
    val transaction = block.transactions.find(t => t.id == illegal_tid).get
    val tranHash = sha256.hashstr(transaction.toByteArray)

    val blockAfterFilter = dataFilter.filterBlock(block)
    val tranAfterFilter = dataFilter.filterTransaction(transaction)

    tranAfterFilter.getIpt.args.head shouldEqual transaction.id
    tranAfterFilter.getIpt.args(1) shouldEqual tranHash

    val tranAfterFilterBlock = blockAfterFilter.transactions.find(t => t.id == illegal_tid).get
    tranAfterFilterBlock.getIpt.args.head shouldEqual transaction.id
    tranAfterFilterBlock.getIpt.args(1) shouldEqual tranHash
  }

  def packBlock(preHash: String, height: Long, trans: Seq[Transaction], tranResult: Seq[TransactionResult]): Block = {
    var block = BlockHelp.buildBlock(preHash, height, trans)
    block = block.withTransactionResults(tranResult)
    block = BlockHelp.AddBlockHeaderHash(block, sha256)
    block = block.withHeader(BlockHelp.AddHeaderSignToBlock(block.getHeader, pe.getSysTag, pe.getRepChainContext.getSignTool))
    preBlockHash = block.getHeader.hashPresent.toStringUtf8
    blockHeight = blockHeight + 1
    block
  }
}
