package rep.sc

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestKit, TestProbe}
import org.json4s.{DefaultFormats, jackson}
import org.json4s.native.Serialization.{write, writePretty}
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType
import rep.network.module.cfrd.ModuleManagerOfCFRD
import rep.protos.peer.{Certificate, ChaincodeId, Signer}
import rep.sc.TransferSpec.{ACTION, SetMap}
import rep.storage.ImpDataAccess
import rep.utils.SerializeUtils.toJson
import rep.app.conf.SystemProfile
import rep.network.autotransaction.PeerHelper
import rep.protos.peer.Certificate.CertType.CERT_CUSTOM
import scala.concurrent.duration._
import scala.collection.mutable.Map
import rep.sc.SandboxDispatcher.DoTransaction
import rep.protos.peer.Transaction
import rep.sc.BlockStubActor.WriteBlockStub
import rep.sc.tpl._
//.{CertStatus,CertInfo}
//import rep.utils.CaseClassToString

object ContractTest {

  type SetMap = scala.collection.mutable.Map[String, Int]

  object ACTION {
    val transfer = "transfer"
    val set = "set"
    val SignUpSigner = "SignUpSigner"
    val SignUpCert = "SignUpCert"
    val UpdateCertStatus = "UpdateCertStatus"
    val UpdateSigner = "UpdateSigner"
  }

}

class ContractTest(_system: ActorSystem)
  extends TestKit(_system) with Matchers with FlatSpecLike with BeforeAndAfterAll {

  def this() = this(ActorSystem("TransferSpec", new ClusterSystem("121000005l35120456.node1", InitType.MULTI_INIT, false).getConf))

  override def afterAll: Unit = {
    shutdown(system)
  }

  implicit val serialization = jackson.Serialization
  // or native.Serialization
  implicit val formats = DefaultFormats

  // 构建Deploy交易，用来部署合约ContractAssetsTPL
  private def get_ContractAssetsTPL_Deploy_Trans(sysName: String, version: Int): Transaction = {
    // 部署资产管理
    val s1 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/ContractAssetsTPL.scala")
    val l1 = try s1.mkString finally s1.close()
    val cid1 = ChaincodeId("ContractAssetsTPL", version)
    //生成deploy交易
    val t1 = PeerHelper.createTransaction4Deploy(sysName, cid1,
      l1, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA)
    t1
  }

  // 构建Invoke交易，用调用ContractAssetsTPL
  private def createAssertTransInvoke(sysName: String, version: Int, action: String, param: String): Transaction = {
    val cid2 = ChaincodeId("ContractAssetsTPL", version)
    PeerHelper.createTransaction4Invoke(sysName, cid2, action, Seq(param))
  }

  // 构建Deploy交易，用来部署合约ContractCert
  private def get_ContractCert_Deploy_Trans(sysName: String, version: Int): Transaction = {
    val s2 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/ContractCert.scala")
    val l2 = try s2.mkString finally s2.close()
    val cid2 = ChaincodeId(SystemProfile.getAccountChaincodeName, version)
    val t2 = PeerHelper.createTransaction4Deploy(sysName, cid2,
      l2, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA)
    t2
  }

  // 构建Invoke交易，用调用ContractCertTPL
  private def createCertTransInvoke(sysName: String, version: Int, action: String, param: String): Transaction = {
    val cid2 = ChaincodeId(SystemProfile.getAccountChaincodeName, version)
    PeerHelper.createTransaction4Invoke(sysName, cid2, action, Seq(param))
  }

  // 构建Deploy交易，用来部署合约parallelPutProofTPL
  private def get_parallelPutProofTPL_Deploy_Trans(sysName: String, version: Int): Transaction = {
    val s3 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/parallelPutProofTPL.scala")
    val l3 = try s3.mkString finally s3.close()
    val cid3 = ChaincodeId("parallelPutProofTPL", version)
    val t3 = PeerHelper.createTransaction4Deploy(sysName, cid3,
      l3, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA_PARALLEL)
    t3
  }

  // 构建Invoke交易，用来调用合约parallelPutProofTPL
  private def createParallelTransInvoke(sysName: String, version: Int, action: String, param: String): Transaction = {
    val cid2 = ChaincodeId("parallelPutProofTPL", version)
    PeerHelper.createTransaction4Invoke(sysName, cid2, action, Seq(param))
  }

  // 执行交易
  private def ExecuteTrans(probe: TestProbe, sandbox: ActorRef, t: Transaction, snapshotName: String, sendertype: TypeOfSender.Value, serial: Int, eresult: Boolean) = {
    val msg_send1 = DoTransaction(t, snapshotName, sendertype)
    probe.send(sandbox, msg_send1)
    val msg_recv1 = probe.expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
    if (msg_recv1.err.isEmpty) {
      println(s"serial:${serial},expect result:${eresult},exeresult:true")
      msg_recv1.err.isEmpty should be(true)
    } else {
      println(msg_recv1.err.get.toString())
      println(s"serial:${serial},expect result:${eresult},exeresult:false")
    }
  }


  "ContractAssetsTPL" should "can set assets and transfer from a to b" in {

    val sysName = "121000005l35120456.node1"
    val dbTag = "121000005l35120456.node1"
    //建立PeerManager实例是为了调用transactionCreator(需要用到密钥签名)，无他
    val pm = system.actorOf(ModuleManagerOfCFRD.props("modulemanager", sysName, false, false, false), "modulemanager")

    val sm: SetMap = Map("121000005l35120456" -> 50, "12110107bi45jh675g" -> 50, "122000002n00123567" -> 50)
    val sms = write(sm)

    /*val tcs = Array(
          Transfer("121000005l35120456", "12110107bi45jh675g", 5),
          Transfer("121000005l35120456", "12110107bi45jh675g0", 5),
           Transfer("121000005l35120456", "12110107bi45jh675g", 500))
    val rcs = Array(None,  "目标账户不存在", "余额不足")*/

    //val aa = new ContractCert

    val signer = Signer("node2", "12110107bi45jh675g", "13856789234", Seq("node2"))
    val cert = scala.io.Source.fromFile("jks/certs/12110107bi45jh675g.node2.cer")
    val certStr = try cert.mkString finally cert.close()

    /*var aa  = new CaseClassToString()
    aa.AddElement("credit_code", "12110107bi45jh675g")
    aa.AddElement("name", "node2")
    aa.AddElement("cert", Certificate(certStr, "SHA1withECDSA", true, None, None))*/


    val certinfo = CertInfo("12110107bi45jh675g", "node2", Certificate(certStr, "SHA1withECDSA", true, None, None,
      rep.protos.peer.Certificate.CertType.CERT_CUSTOM,None,"xxxxx","1"))
    
    val certstatus = CertStatus("12110107bi45jh675g", "node2", false)
    //val certinfo = aa.toJsonString

    //准备探针以验证调用返回结果
    val probe = TestProbe()
    val sandbox = system.actorOf(TransactionDispatcher.props("transactiondispatcher"), "transactiondispatcher")
    val blocker = system.actorOf(BlockStubActor.props("tmpblockactor"), "tmpblockactor")

    //内存中没有合约，建立合约，此时合约在快照中
    val t1 = this.get_ContractCert_Deploy_Trans(sysName, 1)
    ExecuteTrans(probe, sandbox, t1, "dbnumber1", TypeOfSender.FromAPI, 1, true)

    //内存中有合约，可以调用
    val t2 = this.createCertTransInvoke(sysName, 1, ACTION.SignUpSigner, write(signer))
    ExecuteTrans(probe, sandbox, t2, "dbnumber1", TypeOfSender.FromAPI, 2, true)

    //这个应该错误，应该是找不到合约
    val t3 = this.createCertTransInvoke(sysName, 1, ACTION.SignUpSigner, write(signer))
    ExecuteTrans(probe, sandbox, t3, "dbnumber2", TypeOfSender.FromAPI, 3, false)

    //修改合约状态
    val t4 = PeerHelper.createTransaction4State(sysName, ChaincodeId(SystemProfile.getAccountChaincodeName, 1), false)
    ExecuteTrans(probe, sandbox, t4, "dbnumber1", TypeOfSender.FromAPI, 4, true)

    //val t5 =  this.createCertTransInvoke(sysName,1, ACTION.SignUpCert, writePretty(certinfo))
    // t4未持久化，可以继续invoke该合约
    val t5 = this.createCertTransInvoke(sysName, 1, ACTION.SignUpCert, writePretty(certinfo))
    ExecuteTrans(probe, sandbox, t5, "dbnumber1", TypeOfSender.FromAPI, 5, true)
    
    val t51 = this.createCertTransInvoke(sysName, 1, ACTION.UpdateCertStatus, writePretty(certstatus))
    ExecuteTrans(probe, sandbox, t51, "dbnumber1", TypeOfSender.FromAPI, 51, true)

    //同一快照中，再次部署同一版本合约（ContractCert），会失败，对比 t1
    val t6 = this.get_ContractCert_Deploy_Trans(sysName, 1)
    ExecuteTrans(probe, sandbox, t6, "dbnumber1", TypeOfSender.FromAPI, 6, false)

    //不同快照中，再次部署同一版本合约（ContractCert），会成功
    val t7 = this.get_ContractCert_Deploy_Trans(sysName, 1)
    ExecuteTrans(probe, sandbox, t7, "dbnumber2", TypeOfSender.FromAPI, 7, true)

    // 通一快照中，部署升级版本的合约（ContractCertV2），会成功
    val t8 = this.get_ContractCert_Deploy_Trans(sysName, 2)
    ExecuteTrans(probe, sandbox, t8, "dbnumber1", TypeOfSender.FromAPI, 8, true)

    val t81 = this.get_parallelPutProofTPL_Deploy_Trans(sysName, 1)
    ExecuteTrans(probe, sandbox, t81, "dbnumber1", TypeOfSender.FromAPI, 81, true)

    //持久化这些交易
    val tsls = Array(t1, t2, t3, t4, t5,t51, t81)
    val sets = tsls.toSeq
    probe.send(blocker, WriteBlockStub(sets))
    val msg_recv1 = probe.expectMsgType[Int](1000.seconds)
    msg_recv1 == 0 should be(true)
    println("store finish 1= " + msg_recv1.toString())

    //已经持久化合约，应该要失败
    val t9 = this.get_ContractCert_Deploy_Trans(sysName, 1)
    ExecuteTrans(probe, sandbox: ActorRef, t9, "dbnumber1", TypeOfSender.FromAPI, 9, false)

    val signer3 = Signer("node3", "122000002n00123567", "13856789274", Seq("node3"))
    val cert3 = scala.io.Source.fromFile("jks/certs/122000002n00123567.node3.cer")
    val certStr3 = try cert3.mkString finally cert3.close()

    /*var aa1  = new CaseClassToString()
   aa1.AddElement("credit_code", "122000002n00123567")
   aa1.AddElement("name", "node3")
   aa1.AddElement("cert", Certificate(certStr3, "SHA1withECDSA", true, None, None))*/

    val certinfo3 = CertInfo("122000002n00123567", "node3", Certificate(certStr3, "SHA1withECDSA", true, None, None))
    //val certinfo3 = aa1.toJsonString

    //合约状态为disable，会失败
    val t10 = this.createCertTransInvoke(sysName, 1, ACTION.SignUpSigner, write(signer3))
    ExecuteTrans(probe, sandbox: ActorRef, t10, "dbnumber1", TypeOfSender.FromAPI, 10, false)

    //合约状态为disable，会失败
    val t11 = this.createCertTransInvoke(sysName, 1, ACTION.SignUpCert, writePretty(certinfo))
    ExecuteTrans(probe, sandbox: ActorRef, t11, "dbnumber1", TypeOfSender.FromAPI, 11, false)

    //重新设置合约状态为enable
    var t12 = PeerHelper.createTransaction4State(sysName, ChaincodeId(SystemProfile.getAccountChaincodeName, 1), true)
    ExecuteTrans(probe, sandbox: ActorRef, t12, "dbnumber1", TypeOfSender.FromAPI, 12, true)

    //持久化这些交易
    probe.send(blocker, WriteBlockStub(Array(t10, t11, t12).toSeq))
    val msg_recv2 = probe.expectMsgType[Int](1000.seconds)
    msg_recv2 == 0 should be(true)
    println("store finish 2= " + msg_recv2.toString())

    //合约状态持久化为enable，会成功
    val t13 = this.createCertTransInvoke(sysName, 1, ACTION.SignUpSigner, write(signer3))
    ExecuteTrans(probe, sandbox: ActorRef, t13, "dbnumber1", TypeOfSender.FromAPI, 13, true)

    //证书已经存在，会失败
    val t14 = this.createCertTransInvoke(sysName, 1, ACTION.SignUpCert, writePretty(certinfo))
    ExecuteTrans(probe, sandbox: ActorRef, t14, "dbnumber1", TypeOfSender.FromAPI, 14, false)

    var probes = new Array[TestProbe](10)
    var doparams = new Array[DoTransaction](10)

    // 并行执行parallelPutProofTPL
    for (i <- 0 to 9) {
      val p = proofDataSingle("paeallel_key_" + i, "value_" + i)
      val t = this.createParallelTransInvoke(sysName, 1, "putProofSingle", writePretty(p))
      doparams(i) = DoTransaction(t, "dbnumber1", TypeOfSender.FromAPI)
      probes(i) = TestProbe()
    }

    for (i <- 0 to 9) {
      probes(i).send(sandbox, doparams(i))
    }

    for (i <- 0 to 9) {
      val msg_recv1 = probes(i).expectMsgType[Sandbox.DoTransactionResult](1000.seconds)
      if (msg_recv1.err.isEmpty) {
        println(s"serial:${15 + i},expect result:${true},exeresult:true")
        msg_recv1.err.isEmpty should be(true)
      } else {
        println(msg_recv1.err.get.toString())
        println(s"serial:${15 + i},expect result:${true},exeresult:false")
      }
    }

    /*val p = proofDataSingle("paeallel_key_1","value_1")
    val t15 =  this.createParallelTransInvoke(sysName,1, "createParallelTransInvoke", writePretty(p))
    ExecuteTrans(probe,sandbox:ActorRef,t15,"dbnumber1",TypeOfSender.FromAPI,15,true)*/

  }
}

