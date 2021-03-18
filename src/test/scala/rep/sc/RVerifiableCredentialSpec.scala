package rep.sc

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestKit, TestProbe}
import com.google.protobuf.timestamp.Timestamp
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import org.json4s.jackson.Serialization.write
import org.json4s.{DefaultFormats, jackson}
import org.scalatest._
import rep.app.conf.SystemProfile
import rep.app.system.ClusterSystem
import rep.app.system.ClusterSystem.InitType
import rep.network.autotransaction.PeerHelper
import rep.network.module.cfrd.ModuleManagerOfCFRD
import rep.protos.peer._
import rep.sc.SandboxDispatcher.DoTransaction
import rep.sc.tpl.did.RVerifiableCredentialTPL
import rep.crypto.Sha256
import rep.crypto.cert.SignTool
import rep.protos.peer.Operate.OperateType
import rep.sc.tpl.did.RVerifiableCredentialTPL.{RevokeVCClaimsParam, SignupCCSAttrParam, SignupCCSParam, SignupVCStatusParam, UpdateCCSStatusParam, UpdateVCStatusParam}
import scalapb.json4s.JsonFormat

import scala.concurrent.duration._

class RVerifiableCredentialSpec(_system: ActorSystem) extends TestKit(_system)
  with Matchers with FunSuiteLike with BeforeAndAfterAll {

  def this() = this(ActorSystem(
    "RVerifiableCredentialSpec",
    new ClusterSystem("121000005l35120456.node1", InitType.MULTI_INIT, false).getConf)
  )

  val sysName = "121000005l35120456.node1"
  SystemProfile.initConfigSystem(
    new ClusterSystem(sysName, InitType.MULTI_INIT, false).getConf,
    sysName
  )
  val deployer = "121000005l35120456.node1"
  val invoker1 = "121000005l35120456.node1"
  val invoker2 = "12110107bi45jh675g.node2"
  val dbTag = "121000005l35120456.node1"
  val contractName = "RVerifiableCredentialTPL"
  val cId = ChaincodeId(contractName, 1)
  //建立PeerManager实例是为了调用transactionCreator(需要用到密钥签名)，无他
  val pm: ActorRef = system.actorOf(ModuleManagerOfCFRD.props("moduleManager", sysName, enableStatistic = false, enableWebSocket = false, isStartup = false), "moduleManager")
  SignTool.loadPrivateKey(invoker2, "123", s"jks/${invoker2}.jks")

  implicit val serialization = jackson.Serialization
  implicit val formats = DefaultFormats

  override def afterAll: Unit = {
    shutdown(system)
  }

  val probe = TestProbe()
  private val sandbox = system.actorOf(TransactionDispatcher.props("transactiondispatcher"), "transactiondispatcher")

  // test base data
  val signupCCSParam = SignupCCSParam(
    id = "CCS-001",
    name = "UniversityDegreeCredential",
    version = "1.0",
    created = ISODateTimeFormat.dateTime().withZoneUTC().print(DateTime.now()),
    description = "大学学位证书",
    attributes = Seq(
      SignupCCSAttrParam(
        name = "serialNumber",
        `type` = "String",
        description = "学位证书编号"
      ),
      SignupCCSAttrParam(
        name = "id",
        `type` = "String",
        description = "学位获得者did标识"
      ),
      SignupCCSAttrParam(
        name = "name",
        `type` = "String",
        description = "学位获得者姓名"
      ),
      SignupCCSAttrParam(
        name = "degree",
        `type` = "String",
        description = "学位名称"
      ),
      SignupCCSAttrParam(
        name = "date",
        `type` = "String",
        description = "学位授予日期"
      ),
      SignupCCSAttrParam(
        name = "university",
        `type` = "String",
        description = "学位授予学校(单位)"
      )
    )
  )
  val signupVCStatusParam = SignupVCStatusParam(
    id = "0123456789abcdef", status = "VALID"
  )

  /* 已在创世块中部署该合约，故废弃该test case*/
//  test("Deploy the contract RVerifiableCredentialTPL successfully") {
//    val contractBufferedSource = scala.io.Source.fromFile(
//      "src/main/scala/rep/sc/tpl/did/RVerifiableCredentialTPL.scala"
//    )
//    val contractStr = try contractBufferedSource.mkString finally contractBufferedSource.close()
//    val tx = PeerHelper.createTransaction4Deploy(
//      deployer, cId, contractStr, "", 5000,
//      rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA,
//      ChaincodeDeploy.ContractClassification.CONTRACT_SYSTEM
//    )
//    val msg2BeSend = DoTransaction(Seq(tx), "dbnumber", TypeOfSender.FromAPI)
//    probe.send(sandbox, msg2BeSend)
//    val msgRecved = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
//    assert(msgRecved(0).result.get.code == 0)
//  }

  /* 已在创世块中注册相关合约方法操作，故废弃该test case*/
//  test("Signup the contract operates (contract functions) to the system successfully") {
//    val conOpsInfo = Seq(
//      (contractName + "." + RVerifiableCredentialTPL.Action.SignupCCS, "注册可验证凭据属性结构"),
//      (contractName + "." + RVerifiableCredentialTPL.Action.UpdateCCSStatus, "更新可验证凭据属性结构有效状态"),
//      (contractName + "." + RVerifiableCredentialTPL.Action.SignupVCStatus, "注册可验证凭据状态"),
//      (contractName + "." + RVerifiableCredentialTPL.Action.UpdateVCStatus, "更新可验证凭据状态"),
//      (contractName + "." + RVerifiableCredentialTPL.Action.RevokeVCClaims, "撤销可验证凭据属性状态"),
//    )
//    var txs = conOpsInfo.map(conOpInfo => {
//      val millis = System.currentTimeMillis()
//      val cId = new ChaincodeId("RdidOperateAuthorizeTPL",1)
//      val op = Operate(
//        Sha256.hashstr(conOpInfo._1), conOpInfo._2,
//        deployer.split("\\.").head, true, OperateType.OPERATE_CONTRACT,
//        List("transaction.stream","transaction.postTranByString","transaction.postTranStream","transaction.postTran"),
//        "*", conOpInfo._1, Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)),
//        _root_.scala.None, true, "1.0"
//      )
//      PeerHelper.createTransaction4Invoke(
//        deployer, cId, "signUpOperate",
//        Seq(JsonFormat.toJsonString(op))
//      )
//    })
//    val msg2BeSend = DoTransaction(txs, "dbnumber", TypeOfSender.FromAPI)
//    probe.send(sandbox, msg2BeSend)
//    val msgRecved = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
//    msgRecved.foreach(tr => {
//      assert(tr.result.get.code == 0)
//    })
//  }

  test("Signup a new CCS successfully") {
    val ccsParam = signupCCSParam
    val tx = PeerHelper.createTransaction4Invoke(
      invoker1, cId, "signupCCS", Seq(write(ccsParam))
    )

    val msg2BeSend = DoTransaction(Seq(tx), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg2BeSend)
    val msgRecved = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    assert(msgRecved(0).result.get.code == RVerifiableCredentialTPL.STATUS_CODE_OK)
  }

  test("Failed to signup a new CCS with wrong contract function params") {
    val ccsParamCorrect = signupCCSParam
    val ccsParamWrong1 = ccsParamCorrect.copy(id = "")
    val ccsParamWrong2 = ccsParamCorrect.copy(name = "")
    val ccsParamWrong3 = ccsParamCorrect.copy(description = "")
    val ccsParamWrong4 = ccsParamCorrect.copy(version = "")
    val ccsParamWrong5 = ccsParamCorrect.copy(attributes = Seq())
    val wrongAttributes1 = ccsParamCorrect.attributes.updated(0, ccsParamCorrect.attributes(0).copy(name = ""))
    val wrongAttributes2 = ccsParamCorrect.attributes.updated(1, ccsParamCorrect.attributes(1).copy(`type` = ""))
    val wrongAttributes3 = ccsParamCorrect.attributes.updated(2, ccsParamCorrect.attributes(2).copy(description = ""))
    val ccsParamWrong6 = ccsParamCorrect.copy(attributes = wrongAttributes1)
    val ccsParamWrong7 = ccsParamCorrect.copy(attributes = wrongAttributes2)
    val ccsParamWrong8 = ccsParamCorrect.copy(attributes = wrongAttributes3)
    val ccsParamWrongs = Seq(
      ccsParamWrong1, ccsParamWrong2, ccsParamWrong3, ccsParamWrong4,
      ccsParamWrong5, ccsParamWrong6, ccsParamWrong7, ccsParamWrong8,
    )

    val txs = ccsParamWrongs.map( w => PeerHelper.createTransaction4Invoke(
      invoker1, cId, "signupCCS", Seq(write(w))
    ))
    val msg2BeSend = DoTransaction(txs, "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg2BeSend)
    val msgRecved = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    msgRecved.foreach( tr => {
        val ar = JsonFormat.parser.fromJsonString(tr.result.get.reason)(ActionResult)
        assert(ar.code == RVerifiableCredentialTPL.STATUS_CODE_BAD_REQUEST)
        assert(ar.reason.matches("^参数.*空.*") )
      }
    )
  }

  test("Failed to signup a new CCS with the existed id") {
    val ccsParam = signupCCSParam.copy(id = "CCS-002")
    val tx1 = PeerHelper.createTransaction4Invoke(
      invoker1, cId, "signupCCS", Seq(write(ccsParam))
    )
    val tx2 = PeerHelper.createTransaction4Invoke(
      invoker1, cId, "signupCCS", Seq(write(ccsParam))
    )

    val msg2BeSend = DoTransaction(Seq(tx1, tx2), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg2BeSend)
    val msgRecved = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    assert(msgRecved(0).result.get.code == RVerifiableCredentialTPL.STATUS_CODE_OK)
    val ar = JsonFormat.parser.fromJsonString(msgRecved(1).result.get.reason)(ActionResult)
    assert(ar.code == RVerifiableCredentialTPL.STATUS_CODE_ALREADY_EXISTS)
  }

  test("Update the CCS status successfully") {
    val ccsParam = signupCCSParam.copy(id = "CCS-003")
    val tx1 = PeerHelper.createTransaction4Invoke(
      invoker1, cId, "signupCCS", Seq(write(ccsParam))
    )
    val ccsStatusParam = UpdateCCSStatusParam(id = "CCS-003", valid = false)
    val tx2 = PeerHelper.createTransaction4Invoke(
      invoker1, cId, "updateCCSStatus", Seq(write(ccsStatusParam))
    )

    val msg2BeSend = DoTransaction(Seq(tx1, tx2), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg2BeSend)
    val msgRecved = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    assert(msgRecved(0).result.get.code == RVerifiableCredentialTPL.STATUS_CODE_OK)
    assert(msgRecved(1).result.get.code == RVerifiableCredentialTPL.STATUS_CODE_OK)
  }

  test("Failed to update the CCS status with the wrong contract function params") {
    val ccsStatusParamWrong1 = UpdateCCSStatusParam(id = "", false)
    val ccsStatusParamWrong2 = """{ "id": "CCS-01" }"""
    val tx1 = PeerHelper.createTransaction4Invoke(
      invoker1, cId, "updateCCSStatus", Seq(write(ccsStatusParamWrong1))
    )
    val tx2 = PeerHelper.createTransaction4Invoke(
      invoker1, cId, "updateCCSStatus", Seq(ccsStatusParamWrong2)
    )

    val msg2BeSend = DoTransaction(Seq(tx1, tx2), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg2BeSend)
    val msgRecved = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    val ar = JsonFormat.parser.fromJsonString(msgRecved(0).result.get.reason)(ActionResult)
    assert(ar.code == RVerifiableCredentialTPL.STATUS_CODE_BAD_REQUEST)
    assert(msgRecved(1).result.get.code == 102)
  }

  test("Failed to update the CCS status with the not existed id") {
    val ccsStatusParam = UpdateCCSStatusParam(id = "notExisted", false)
    val tx = PeerHelper.createTransaction4Invoke(
      invoker1, cId, "updateCCSStatus", Seq(write(ccsStatusParam))
    )

    val msg2BeSend = DoTransaction(Seq(tx), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg2BeSend)
    val msgRecved = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    val ar = JsonFormat.parser.fromJsonString(msgRecved(0).result.get.reason)(ActionResult)
    assert(ar.code == RVerifiableCredentialTPL.STATUS_CODE_NOT_FOUND)
  }

  test("Failed to update the CCS status with the wrong invoker") {
    val ccsParam = signupCCSParam.copy(id = "CCS-004")
    val ccsStatusParam = UpdateCCSStatusParam(id = "CCS-004", false)
    val tx1 = PeerHelper.createTransaction4Invoke(
      invoker1, cId, "signupCCS", Seq(write(ccsParam))
    )
    val tx2 = PeerHelper.createTransaction4Invoke(
      invoker2, cId, "updateCCSStatus", Seq(write(ccsStatusParam))
    )

    val msg2BeSend = DoTransaction(Seq(tx1, tx2), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg2BeSend)
    val msgRecved = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    assert(msgRecved(0).result.get.code == RVerifiableCredentialTPL.STATUS_CODE_OK)
    val ar = JsonFormat.parser.fromJsonString(msgRecved(1).result.get.reason)(ActionResult)
    assert(ar.code == RVerifiableCredentialTPL.STATUS_CODE_UNAUTHORIZED)
  }

  test("Signup a new VCStatus successfully") {
    val vcStatusParam = signupVCStatusParam
    val tx = PeerHelper.createTransaction4Invoke(
      invoker1, cId, "signupVCStatus", Seq(write(vcStatusParam))
    )

    val msg2BeSend = DoTransaction(Seq(tx), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg2BeSend)
    val msgRecved = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    assert(msgRecved(0).result.get.code == RVerifiableCredentialTPL.STATUS_CODE_OK)
  }

  test("Failed to signup a new VCStatus with the wrong contract function params") {
    val vcStatusParamWrong1 = signupVCStatusParam.copy(id = "")
    val vcStatusParamWrong2 = signupVCStatusParam.copy(status = "")
    val tx1 = PeerHelper.createTransaction4Invoke(
      invoker1, cId, "signupVCStatus", Seq(write(vcStatusParamWrong1))
    )
    val tx2 = PeerHelper.createTransaction4Invoke(
      invoker1, cId, "signupVCStatus", Seq(write(vcStatusParamWrong2))
    )

    val msg2BeSend = DoTransaction(Seq(tx1, tx2), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg2BeSend)
    val msgRecved = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    val ar1 = JsonFormat.parser.fromJsonString(msgRecved(0).result.get.reason)(ActionResult)
    val ar2 = JsonFormat.parser.fromJsonString(msgRecved(1).result.get.reason)(ActionResult)
    assert(ar1.code == RVerifiableCredentialTPL.STATUS_CODE_BAD_REQUEST)
    assert(ar2.code == RVerifiableCredentialTPL.STATUS_CODE_BAD_REQUEST)
  }

  test("Failed to signup a new VCStatus with the existed id") {
    val vcStatusParam = signupVCStatusParam.copy(id = "1234")
    val tx1 = PeerHelper.createTransaction4Invoke(
      invoker1, cId, "signupVCStatus", Seq(write(vcStatusParam))
    )
    val tx2 = PeerHelper.createTransaction4Invoke(
      invoker1, cId, "signupVCStatus", Seq(write(vcStatusParam))
    )

    val msg2BeSend = DoTransaction(Seq(tx1, tx2), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg2BeSend)
    val msgRecved = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    assert(msgRecved(0).result.get.code == RVerifiableCredentialTPL.STATUS_CODE_OK)
    val ar = JsonFormat.parser.fromJsonString(msgRecved(1).result.get.reason)(ActionResult)
    assert(ar.code == RVerifiableCredentialTPL.STATUS_CODE_ALREADY_EXISTS)
  }

  test("Update the VCStatus successfully") {
    val vcStatusParam = signupVCStatusParam.copy(id = "9876543210")
    val vcStatusUpdateParam = UpdateVCStatusParam(id = "9876543210", "SUSPENDED")
    val tx1 = PeerHelper.createTransaction4Invoke(
      invoker1, cId, "signupVCStatus", Seq(write(vcStatusParam))
    )
    val tx2 = PeerHelper.createTransaction4Invoke(
      invoker1, cId, "updateVCStatus", Seq(write(vcStatusUpdateParam))
    )

    val msg2BeSend = DoTransaction(Seq(tx1, tx2), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg2BeSend)
    val msgRecved = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    assert(msgRecved(0).result.get.code == RVerifiableCredentialTPL.STATUS_CODE_OK)
    assert(msgRecved(1).result.get.code == RVerifiableCredentialTPL.STATUS_CODE_OK)
  }

  test("Failed to update the VCStatus with the wrong contract function params") {
    val vcStatusUpdateParamWrong1 = UpdateVCStatusParam(id = "", status = "VALID")
    val vcStatusUpdateParamWrong2 = UpdateVCStatusParam(id = "9876543210", status = "")
    val tx1 = PeerHelper.createTransaction4Invoke(
      invoker1, cId, "updateVCStatus", Seq(write(vcStatusUpdateParamWrong1))
    )
    val tx2 = PeerHelper.createTransaction4Invoke(
      invoker1, cId, "signupVCStatus", Seq(write(vcStatusUpdateParamWrong2))
    )

    val msg2BeSend = DoTransaction(Seq(tx1, tx2), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg2BeSend)
    val msgRecved = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    val ar1 = JsonFormat.parser.fromJsonString(msgRecved(0).result.get.reason)(ActionResult)
    val ar2 = JsonFormat.parser.fromJsonString(msgRecved(1).result.get.reason)(ActionResult)
    assert(ar1.code == RVerifiableCredentialTPL.STATUS_CODE_BAD_REQUEST)
    assert(ar2.code == RVerifiableCredentialTPL.STATUS_CODE_BAD_REQUEST)
  }

  test("Failed to update the VCStatus with the not existed id") {
    val vcStatusUpdateParam = UpdateVCStatusParam(id = "notExisted", "INVALID")
    val tx = PeerHelper.createTransaction4Invoke(
      invoker1, cId, "updateVCStatus", Seq(write(vcStatusUpdateParam))
    )

    val msg2BeSend = DoTransaction(Seq(tx), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg2BeSend)
    val msgRecved = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    val ar = JsonFormat.parser.fromJsonString(msgRecved(0).result.get.reason)(ActionResult)
    assert(ar.code == RVerifiableCredentialTPL.STATUS_CODE_NOT_FOUND)
  }

  test("Failed to update the VCStatus with the wrong invoker") {
    val vcStatusParam = signupVCStatusParam.copy(id = "abc123")
    val vcStatusUpdateParam = UpdateVCStatusParam(id = "abc123", "SUSPENDED")
    val tx1 = PeerHelper.createTransaction4Invoke(
      invoker1, cId, "signupVCStatus", Seq(write(vcStatusParam))
    )
    val tx2 = PeerHelper.createTransaction4Invoke(
      invoker2, cId, "updateVCStatus", Seq(write(vcStatusUpdateParam))
    )

    val msg2BeSend = DoTransaction(Seq(tx1, tx2), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg2BeSend)
    val msgRecved = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    assert(msgRecved(0).result.get.code == RVerifiableCredentialTPL.STATUS_CODE_OK)
    val ar = JsonFormat.parser.fromJsonString(msgRecved(1).result.get.reason)(ActionResult)
    assert(ar.code == RVerifiableCredentialTPL.STATUS_CODE_UNAUTHORIZED)
  }

  test("Revoke the VC claims successfully") {
    val vcStatusParam = signupVCStatusParam.copy(id = "122333", status = "INVALID")
    val revokeVCClaimsParam = RevokeVCClaimsParam(id = "122333", revokedClaimIndex = Seq("a", "b", "c"))
    val tx1 = PeerHelper.createTransaction4Invoke(
      invoker1, cId, "signupVCStatus", Seq(write(vcStatusParam))
    )
    val tx2 = PeerHelper.createTransaction4Invoke(
      invoker1, cId, "revokeVCClaims", Seq(write(revokeVCClaimsParam))
    )

    val msg2BeSend = DoTransaction(Seq(tx1, tx2), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg2BeSend)
    val msgRecved = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    assert(msgRecved(0).result.get.code == RVerifiableCredentialTPL.STATUS_CODE_OK)
    assert(msgRecved(1).result.get.code == RVerifiableCredentialTPL.STATUS_CODE_OK)
  }

  test("Failed to revoke the VC claims with the wrong contract function params") {
    val revokeVCClaimParamWrong1 = RevokeVCClaimsParam(id = "", revokedClaimIndex = Seq("a"))
    val revokeVCClaimParamWrong2 = RevokeVCClaimsParam(id = "122333", revokedClaimIndex = Seq())
    val tx1 = PeerHelper.createTransaction4Invoke(
      invoker1, cId, "revokeVCClaims", Seq(write(revokeVCClaimParamWrong1))
    )
    val tx2 = PeerHelper.createTransaction4Invoke(
      invoker1, cId, "revokeVCClaims", Seq(write(revokeVCClaimParamWrong2))
    )

    val msg2BeSend = DoTransaction(Seq(tx1, tx2), "dbnumber", TypeOfSender.FromAPI)
    probe.send(sandbox, msg2BeSend)
    val msgRecved = probe.expectMsgType[Seq[TransactionResult]](1000.seconds)
    val ar1 = JsonFormat.parser.fromJsonString(msgRecved(0).result.get.reason)(ActionResult)
    val ar2 = JsonFormat.parser.fromJsonString(msgRecved(1).result.get.reason)(ActionResult)
    assert(ar1.code == RVerifiableCredentialTPL.STATUS_CODE_BAD_REQUEST)
    assert(ar2.code == RVerifiableCredentialTPL.STATUS_CODE_BAD_REQUEST)
  }
}
