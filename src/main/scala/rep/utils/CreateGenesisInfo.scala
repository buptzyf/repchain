package rep.utils

import java.io.PrintWriter

import com.google.protobuf.timestamp.Timestamp
import com.typesafe.config.ConfigFactory
import org.json4s.jackson.JsonMethods.{pretty, render}
import org.json4s.{DefaultFormats, jackson}
import rep.app.system.RepChainSystemContext
import rep.crypto.Sha256
import rep.network.consensus.util.BlockHelp
import rep.proto.rc2.Authorize.TransferType
import rep.proto.rc2.Certificate.CertType
import rep.proto.rc2.ChaincodeDeploy.{CodeType, RunType, StateType}
import rep.proto.rc2.Operate.OperateType
import rep.proto.rc2.{Authorize, Block, Certificate, ChaincodeDeploy, ChaincodeId, Operate, Signer, Transaction}
import scalapb.json4s.JsonFormat

import scala.collection.mutable.ArrayBuffer

object CreateGenesisInfo {
  implicit val serialization = jackson.Serialization // or native.Serialization
  implicit val formats = DefaultFormats

  def main(args: Array[String]): Unit = {
    val ctx = new RepChainSystemContext("121000005l35120456.node1")
    //CryptoMgr.loadSystemConfInDebug

    ctx.getSignTool.loadPrivateKey("121000005l35120456.node1", "123", s"${ctx.getCryptoMgr.getKeyFileSuffix.substring(1)}/121000005l35120456.node1${ctx.getCryptoMgr.getKeyFileSuffix}")
    ctx.getSignTool.loadNodeCertList("changeme", s"${ctx.getCryptoMgr.getKeyFileSuffix.substring(1)}/mytruststore${ctx.getCryptoMgr.getKeyFileSuffix}")
    ctx.getSignTool.loadPrivateKey("951002007l78123233.super_admin", "super_admin", s"${ctx.getCryptoMgr.getKeyFileSuffix.substring(1)}/951002007l78123233.super_admin${ctx.getCryptoMgr.getKeyFileSuffix}")
    val sysName = "121000005l35120456.node1"
    val superAdmin = "951002007l78123233.super_admin"
    val super_credit = "951002007l78123233"
    val sys_credit = "121000005l35120456"

    val translist: ArrayBuffer[Transaction] = new ArrayBuffer[Transaction]

    //使用超级管理员（链密钥的持有人）部署内置的RDID权限管理合约
    val s1 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/did/RdidOperateAuthorizeTPL.scala", "UTF-8")
    val l1 = try s1.mkString finally s1.close()
    val cid1 = new ChaincodeId("RdidOperateAuthorizeTPL", 1)

    val deploy_trans = ctx.getTransactionBuilder.createTransaction4Deploy(superAdmin, cid1, l1, "", 5000,
                                                  CodeType.CODE_SCALA,RunType.RUN_SERIAL,StateType.STATE_BLOCK,
                                                  ChaincodeDeploy.ContractClassification.CONTRACT_SYSTEM,0)
    translist += deploy_trans

    //注册合约的管理者，默认注册某个节点的DiD，并授予角色管理权限
    //注册节点DiD
    val nodes: Array[(String, String, String)] = new Array[(String, String, String)](6)
    nodes(0) = ("super_admin", "951002007l78123233", "18912345678")
    nodes(1) = ("node1", "121000005l35120456", "18912345678")
    nodes(2) = ("node2", "12110107bi45jh675g", "18912345678")
    nodes(3) = ("node3", "122000002n00123567", "18912345678")
    nodes(4) = ("node4", "921000005k36123789", "18912345678")
    nodes(5) = ("node5", "921000006e0012v696", "18912345678")
    for (i <- 0 to 5) {
      val certfile = scala.io.Source.fromFile(s"${ctx.getCryptoMgr.getKeyFileSuffix.substring(1)}/" + nodes(i)._2 + "." + nodes(i)._1 + ".cer", "UTF-8")
      val certstr = try certfile.mkString finally certfile.close()
      val certstrhash = ctx.getHashTool.hashstr(certstr)
      val certid = IdTool.getCertIdFromName(nodes(i)._2 + "." + nodes(i)._1)
      val millis = System.currentTimeMillis()
      //生成Did的身份证书
      val authcert = Certificate(certstr, "SHA256withECDSA", true, Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)),
        _root_.scala.None, CertType.CERT_AUTHENTICATION, Option(certid), certstrhash, "1.0")
      val signer_tmp = Signer(nodes(i)._1, nodes(i)._2, nodes(i)._3, _root_.scala.Seq.empty,
        _root_.scala.Seq.empty, _root_.scala.Seq.empty, _root_.scala.Seq.empty, List(authcert), "",
        Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)), _root_.scala.None, true, "1.0")
      translist += ctx.getTransactionBuilder.createTransaction4Invoke(superAdmin, cid1, "signUpSigner", Seq(JsonFormat.toJsonString(signer_tmp)))
    }
    //注册操作
    //权限管理合约操作注册
    val opsOfContract: Array[(String, String, String)] = new Array[(String, String, String)](14)
    opsOfContract(0) = (ctx.getHashTool.hashstr("RdidOperateAuthorizeTPL.signUpSigner"), "注册RDID", "RdidOperateAuthorizeTPL.signUpSigner")
    opsOfContract(1) = (ctx.getHashTool.hashstr("RdidOperateAuthorizeTPL.updateSignerStatus"), "禁用或启用RDID", "RdidOperateAuthorizeTPL.updateSignerStatus")
    opsOfContract(2) = (ctx.getHashTool.hashstr("RdidOperateAuthorizeTPL.updateSigner"), "更新信息", "RdidOperateAuthorizeTPL.updateSigner")
    opsOfContract(3) = (ctx.getHashTool.hashstr("RdidOperateAuthorizeTPL.signUpCertificate"), "用户注册证书", "RdidOperateAuthorizeTPL.signUpCertificate")
    opsOfContract(4) = (ctx.getHashTool.hashstr("RdidOperateAuthorizeTPL.updateCertificateStatus"), "用户禁用或启用证书", "RdidOperateAuthorizeTPL.updateCertificateStatus")
    opsOfContract(5) = (ctx.getHashTool.hashstr("RdidOperateAuthorizeTPL.signUpAllTypeCertificate"), "用户可为所有人注册证书，需授权", "RdidOperateAuthorizeTPL.signUpAllTypeCertificate")
    opsOfContract(6) = (ctx.getHashTool.hashstr("RdidOperateAuthorizeTPL.updateAllTypeCertificateStatus"), "用户可为所有人禁用或启用证书，需授权，super_admin特殊处理", "RdidOperateAuthorizeTPL.updateAllTypeCertificateStatus")
    opsOfContract(7) = (ctx.getHashTool.hashstr("RdidOperateAuthorizeTPL.signUpOperate"), "注册操作，自己注册自己", "RdidOperateAuthorizeTPL.signUpOperate")
    opsOfContract(8) = (ctx.getHashTool.hashstr("RdidOperateAuthorizeTPL.updateOperateStatus"), "禁用或启用操作，自己更新自己名下的操作", "RdidOperateAuthorizeTPL.updateOperateStatus")
    opsOfContract(9) = (ctx.getHashTool.hashstr("RdidOperateAuthorizeTPL.grantOperate"), "授权操作", "RdidOperateAuthorizeTPL.grantOperate")
    opsOfContract(10) = (ctx.getHashTool.hashstr("RdidOperateAuthorizeTPL.updateGrantOperateStatus"), "禁用或启用授权", "RdidOperateAuthorizeTPL.updateGrantOperateStatus")
    opsOfContract(11) = (ctx.getHashTool.hashstr("RdidOperateAuthorizeTPL.bindCertToAuthorize"), "绑定证书到授权操作", "RdidOperateAuthorizeTPL.bindCertToAuthorize")
    opsOfContract(12) = (ctx.getHashTool.hashstr("*.deploy"), "发布合约操作", "*.deploy") //*表示可以发布任意合约
    opsOfContract(13) = (ctx.getHashTool.hashstr("*.setState"), "改变合约状态操作", "*.setState") //*表示可以设置任意合约状态

    for (i <- 0 to 13) {
      val millis = System.currentTimeMillis()
      val snls = List("transaction.stream", "transaction.postTranByString", "transaction.postTranStream", "transaction.postTran")
      //生成Operate
      var op: Operate = null
      if (i == 3 || i == 4 || i ==7 || i == 8 || i == 9 || i ==10 || i == 11) {
        // 公开操作，无需授权，普通用户可以绑定给自己的证书
        op = Operate(opsOfContract(i)._1, opsOfContract(i)._2, super_credit, true, OperateType.OPERATE_CONTRACT,
          snls, "*", opsOfContract(i)._3, Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)),
          _root_.scala.None, true, "1.0")
      } else {
        op = Operate(opsOfContract(i)._1, opsOfContract(i)._2, super_credit, false, OperateType.OPERATE_CONTRACT,
          snls, "*", opsOfContract(i)._3, Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)),
          _root_.scala.None, true, "1.0")
      }
      translist += ctx.getTransactionBuilder.createTransaction4Invoke(superAdmin, cid1, "signUpOperate", Seq(JsonFormat.toJsonString(op)))
    }

    //api操作注册
    val opsOfAPI: Array[(String, String, String)] = new Array[(String, String, String)](15)
    opsOfAPI(0) = (ctx.getHashTool.hashstr("chaininfo.chaininfo"), "获取链信息", "chaininfo.chaininfo")
    opsOfAPI(1) = (ctx.getHashTool.hashstr("chaininfo.node"), "返回组网节点数量", "chaininfo.node")
    opsOfAPI(2) = (ctx.getHashTool.hashstr("chaininfo.getcachetransnumber"), "返回系统缓存交易数量", "chaininfo.getcachetransnumber")
    opsOfAPI(3) = (ctx.getHashTool.hashstr("chaininfo.getAcceptedTransNumber"), "返回系统接收到的交易数量", "chaininfo.getAcceptedTransNumber")

    opsOfAPI(4) = (ctx.getHashTool.hashstr("block.hash"), "返回指定id的区块", "block.hash")
    opsOfAPI(5) = (ctx.getHashTool.hashstr("block.blockHeight"), "返回指定高度的区块", "block.blockHeight")
    opsOfAPI(6) = (ctx.getHashTool.hashstr("block.getTransNumberOfBlock"), "返回指定高度区块包含的交易数", "block.getTransNumberOfBlock")
    opsOfAPI(7) = (ctx.getHashTool.hashstr("block.blocktime"), "返回指定高度的区块的出块时间", "block.blocktime")
    opsOfAPI(8) = (ctx.getHashTool.hashstr("block.blocktimeoftran"), "返回指定交易的入块时间", "block.blocktimeoftran")
    opsOfAPI(9) = (ctx.getHashTool.hashstr("block.stream"), "返回指定高度的区块字节流", "block.stream")

    opsOfAPI(10) = (ctx.getHashTool.hashstr("transaction"), "返回指定id的交易", "transaction")
    opsOfAPI(11) = (ctx.getHashTool.hashstr("transaction.stream"), "返回指定id的交易字节流", "transaction.stream")
    opsOfAPI(12) = (ctx.getHashTool.hashstr("transaction.postTranByString"), "提交带签名的交易", "transaction.postTranByString")
    opsOfAPI(13) = (ctx.getHashTool.hashstr("transaction.postTranStream"), "提交带签名的交易字节流", "transaction.postTranStream")
    opsOfAPI(14) = (ctx.getHashTool.hashstr("transaction.postTran"), "提交交易", "transaction.postTran")

    for (i <- 0 to 14) {
      val millis = System.currentTimeMillis()
      val snls = List("transaction.stream", "transaction.postTranByString", "transaction.postTranStream", "transaction.postTran")
      //生成Operate，不是公开的
      val op = Operate(opsOfAPI(i)._1, opsOfAPI(i)._2, super_credit, false, OperateType.OPERATE_SERVICE,
        List(opsOfAPI(i)._3), "*", "", Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)),
        _root_.scala.None, true, "1.0")
      translist += ctx.getTransactionBuilder.createTransaction4Invoke(superAdmin, cid1, "signUpOperate", Seq(JsonFormat.toJsonString(op)))
    }

    //授权节点操作
    val granteds = new ArrayBuffer[String]
    for (i <- 1 to 5) {
      granteds += nodes(i)._2
    }

    // 授权节点所有合约相关的操作
    val opids = new ArrayBuffer[String]
    for (i <- 0 to 13) {
      opids += opsOfContract(i)._1
    }

    // 授权节点所有api相关的操作
    for (i <- 0 to 14) {
      opids += opsOfAPI(i)._1
    }

    val tmpmillis = System.currentTimeMillis()
    val at = Authorize(IdTool.getRandomUUID, super_credit, granteds, opids,
      TransferType.TRANSFER_REPEATEDLY, Option(Timestamp(tmpmillis / 1000, ((tmpmillis % 1000) * 1000000).toInt)),
      _root_.scala.None, true, "1.0")
    var als: List[String] = List(JsonFormat.toJsonString(at))
    translist += ctx.getTransactionBuilder.createTransaction4Invoke(superAdmin, cid1, "grantOperate", Seq(SerializeUtils.compactJson(als)))

    //部署应用合约--分账合约
    val s2 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/ContractAssetsTPL.scala", "UTF-8")
    val c2 = try s2.mkString finally s2.close()
    val cid2 = new ChaincodeId("ContractAssetsTPL", 1)
    val dep_asserts_trans = ctx.getTransactionBuilder.createTransaction4Deploy(sysName, cid2, c2, "", 5000,
      CodeType.CODE_SCALA,RunType.RUN_SERIAL,StateType.STATE_BLOCK,
      ChaincodeDeploy.ContractClassification.CONTRACT_SYSTEM,0)
    translist += dep_asserts_trans

    //建立应用合约的操作
    val opsOfCustomContract: Array[(String, String, String)] = new Array[(String, String, String)](3)
    opsOfCustomContract(0) = (ctx.getHashTool.hashstr("ContractAssetsTPL.transfer"), "转账交易", "ContractAssetsTPL.transfer")
    opsOfCustomContract(1) = (ctx.getHashTool.hashstr("ContractAssetsTPL.set"), "初始化账户", "ContractAssetsTPL.set")
    opsOfCustomContract(2) = (ctx.getHashTool.hashstr("ContractAssetsTPL.putProof"), "存证", "ContractAssetsTPL.putProof")

    val tmillis = System.currentTimeMillis()
    val snls = List("transaction.stream", "transaction.postTranByString", "transaction.postTranStream", "transaction.postTran")
    //生成Operate 转账操作属于公开的，任何人都可以发起转账，无需赋权
    val op1 = Operate(opsOfCustomContract(0)._1, opsOfCustomContract(0)._2, sys_credit, true, OperateType.OPERATE_CONTRACT,
      snls, "*", opsOfCustomContract(0)._3, Option(Timestamp(tmillis / 1000, ((tmillis % 1000) * 1000000).toInt)),
      _root_.scala.None, true, "1.0")
    translist += ctx.getTransactionBuilder.createTransaction4Invoke(sysName, cid1, "signUpOperate", Seq(JsonFormat.toJsonString(op1)))

    //生成Operate 初始化只能是超级节点可以做，注册操作，但是不授权给其他人
    val op2 = Operate(opsOfCustomContract(1)._1, opsOfCustomContract(1)._2, sys_credit, false, OperateType.OPERATE_CONTRACT,
      snls, "*", opsOfCustomContract(1)._3, Option(Timestamp(tmillis / 1000, ((tmillis % 1000) * 1000000).toInt)),
      _root_.scala.None, true, "1.0")
    translist += ctx.getTransactionBuilder.createTransaction4Invoke(sysName, cid1, "signUpOperate", Seq(JsonFormat.toJsonString(op2)))

    //生成Operate 存证操作属于公开的，任何人都可以发起存证，无需赋权
    val op3 = Operate(opsOfCustomContract(2)._1, opsOfCustomContract(2)._2, sys_credit, true, OperateType.OPERATE_CONTRACT,
      snls, "*", opsOfCustomContract(2)._3, Option(Timestamp(tmillis / 1000, ((tmillis % 1000) * 1000000).toInt)),
      _root_.scala.None, true, "1.0")
    translist += ctx.getTransactionBuilder.createTransaction4Invoke(sysName, cid1, "signUpOperate", Seq(JsonFormat.toJsonString(op3)))

    // 设置账户初始金额
    val s3 = scala.io.Source.fromFile("api_req/json/set.json", "UTF-8")
    val ct1 = try s3.mkString finally s3.close()
    translist += ctx.getTransactionBuilder.createTransaction4Invoke("951002007l78123233.super_admin", cid2, "set", Seq(ct1))



    //create gensis block
    val millis = ConfigFactory.load().getLong("akka.genesisblock.creationBlockTime")

    var blk = BlockHelp.buildBlock("",1,translist)
    //new Block(1, 1, translist, Seq(), _root_.com.google.protobuf.ByteString.EMPTY, _root_.com.google.protobuf.ByteString.EMPTY)

    //获得管理员证书和签名
    //    val (priKA, pubKA, certA) = ECDSASign.getKeyPair("super_admin")
    //    val (prik, pubK, cert) = ECDSASign.getKeyPair("1")
    //val blk_hash = blk.toByteArray
    //签名之前不再使用hash
    //val blk_hash = Sha256.hash(blk.toByteArray)
    //超级管理员背书（角色）
    //创建者背书（1）
    /*blk = blk.withEndorsements(Seq(
        BlockHelp.SignDataOfBlock(blk_hash,"951002007l78123233.super_admin"),
        BlockHelp.SignDataOfBlock(blk_hash,"121000005l35120456.node1")))*/
    blk =  blk.withHeader(blk.getHeader.clearEndorsements)
    blk = blk.clearTransactionResults
    val r = JsonFormat.toJson(blk)
    val rstr = pretty(render(r))
    println(rstr)

    val pw = new PrintWriter("json/genesis.json", "UTF-8")
    pw.write(rstr)
    pw.flush()
    pw.close()
  }
}