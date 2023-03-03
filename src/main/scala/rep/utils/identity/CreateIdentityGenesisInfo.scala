package rep.utils.identity

import com.google.protobuf.timestamp.Timestamp
import com.typesafe.config.ConfigFactory
import org.json4s.jackson.JsonMethods.{pretty, render}
import org.json4s.{DefaultFormats, jackson}
import rep.app.system.RepChainSystemContext
import rep.network.consensus.util.BlockHelp
import rep.proto.rc2.Authorize.TransferType
import rep.proto.rc2.Certificate.CertType
import rep.proto.rc2.ChaincodeDeploy.{CodeType, RunType, StateType}
import rep.proto.rc2.Operate.OperateType
import rep.proto.rc2._
import rep.storage.util.pathUtil
import rep.utils.{IdTool, SerializeUtils}
import scalapb.json4s.JsonFormat

import java.io.PrintWriter
import scala.collection.mutable.ArrayBuffer

object CreateIdentityGenesisInfo {
  implicit val serialization = jackson.Serialization // or native.Serialization
  implicit val formats = DefaultFormats

  def main(args: Array[String]): Unit = {
    val ctx = new RepChainSystemContext("121000005l35120456.node1")
    ctx.getSignTool.loadPrivateKey("951002007l78123233.super_admin", "super_admin",
      s"${ctx.getCryptoMgr.getKeyFileSuffix.substring(1)}/${ctx.getConfig.getChainNetworkId}/951002007l78123233.super_admin${ctx.getCryptoMgr.getKeyFileSuffix}")
    val superAdmin = s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}951002007l78123233.super_admin"
    val super_credit = s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}951002007l78123233"

    val translist: ArrayBuffer[Transaction] = new ArrayBuffer[Transaction]

    //使用超级管理员（链密钥的持有人）部署内置的RDID权限管理合约
    val s1 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/did/RdidOperateAuthorizeTPL.scala", "UTF-8")
    val l1 = try s1.mkString finally s1.close()
    val cid1 = new ChaincodeId("RdidOperateAuthorizeTPL", 1)
    val deploy_trans = ctx.getTransactionBuilder.createTransaction4Deploy(superAdmin, cid1, l1, "", 5000, CodeType.CODE_SCALA, RunType.RUN_SERIAL, StateType.STATE_BLOCK,
      ChaincodeDeploy.ContractClassification.CONTRACT_SYSTEM, 0)
    translist += deploy_trans

    //注册合约的管理者，默认注册某个节点的DiD，并授予角色管理权限
    //注册节点DiD
    val nodes: Array[(String, String, String)] = new Array[(String, String, String)](12)
    val identityFlag = ctx.getConfig.getChainNetworkId + IdTool.DIDPrefixSeparator
    val credenceNetWorkId = "credence-net"
    val credenceFlag = credenceNetWorkId + IdTool.DIDPrefixSeparator
    // 身份链
    nodes(0) = ("super_admin", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}951002007l78123233", "18912345678")
    nodes(1) = ("node1", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}121000005l35120456", "18912345678")
    nodes(2) = ("node2", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}12110107bi45jh675g", "18912345678")
    nodes(3) = ("node3", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}122000002n00123567", "18912345678")
    nodes(4) = ("node4", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}921000005k36123789", "18912345678")
    nodes(5) = ("node5", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}921000006e0012v696", "18912345678")
    // 业务链
    nodes(6) = ("super_admin", s"$credenceNetWorkId${IdTool.DIDPrefixSeparator}951002007l78123233", "18912345678")
    nodes(7) = ("node6", s"$credenceNetWorkId${IdTool.DIDPrefixSeparator}330597659476689954", "18912345678")
    nodes(8) = ("node7", s"$credenceNetWorkId${IdTool.DIDPrefixSeparator}044934755127708189", "18912345678")
    nodes(9) = ("node8", s"$credenceNetWorkId${IdTool.DIDPrefixSeparator}201353514191149590", "18912345678")
    nodes(10) = ("node9", s"$credenceNetWorkId${IdTool.DIDPrefixSeparator}734747416095474396", "18912345678")
    nodes(11) = ("node10", s"$credenceNetWorkId${IdTool.DIDPrefixSeparator}710341838996249513", "18912345678")
    for (i <- nodes.indices) {
      val certfile =
        if (i <= 5)
          scala.io.Source.fromFile(s"${ctx.getCryptoMgr.getKeyFileSuffix.substring(1)}/${ctx.getConfig.getChainNetworkId}/" + nodes(i)._2.substring(nodes(i)._2.indexOf(identityFlag) + identityFlag.length) + "." + nodes(i)._1 + ".cer", "UTF-8")
        else
          scala.io.Source.fromFile(s"${ctx.getCryptoMgr.getKeyFileSuffix.substring(1)}/$credenceNetWorkId/" + nodes(i)._2.substring(nodes(i)._2.indexOf(credenceFlag) + credenceFlag.length) + "." + nodes(i)._1 + ".cer", "UTF-8")
      val certstr = try certfile.mkString finally certfile.close()
      val certstrhash = ctx.getHashTool.hashstr(IdTool.deleteLine(certstr))
      val certid = IdTool.getCertIdFromCreditAndName(nodes(i)._2, nodes(i)._1)
      val millis = System.currentTimeMillis()
      //生成Did的身份证书
      val authcert = Certificate(certstr, "SHA256withECDSA", true, Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)),
        _root_.scala.None, CertType.CERT_AUTHENTICATION, Option(certid), certstrhash, "1.0")
      val signer_tmp = Signer(nodes(i)._1, nodes(i)._2, nodes(i)._3, _root_.scala.Seq.empty, Seq.empty, Seq.empty, Seq.empty, List(authcert), "",
        Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)), _root_.scala.None, true, "1.0")
      translist += ctx.getTransactionBuilder.createTransaction4Invoke(superAdmin, cid1, "signUpSigner", Seq(JsonFormat.toJsonString(signer_tmp)))
    }


    //注册操作
    //权限管理合约操作注册
    val opsOfContract: Array[(String, String, String)] = new Array[(String, String, String)](14)
    opsOfContract(0) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.signUpSigner"), "注册RDID", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.signUpSigner")
    opsOfContract(1) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.updateSignerStatus"), "禁用或启用RDID", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.updateSignerStatus")
    opsOfContract(2) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.updateSigner"), "更新信息", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.updateSigner")
    opsOfContract(3) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.signUpCertificate"), "用户注册证书", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.signUpCertificate")
    opsOfContract(4) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.updateCertificateStatus"), "用户禁用或启用证书", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.updateCertificateStatus")
    opsOfContract(5) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.signUpAllTypeCertificate"), "用户可为所有人注册证书，需授权", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.signUpAllTypeCertificate")
    opsOfContract(6) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.updateAllTypeCertificateStatus"), "用户可为所有人禁用或启用证书，需授权，super_admin特殊处理", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.updateAllTypeCertificateStatus")

    for (i <- 0 to 6) {
      val millis = System.currentTimeMillis()
      val snls = List("transaction.stream", "transaction.postTranByString", "transaction.postTranStream", "transaction.postTran")
      //生成Operate
      var op: Operate = null
      if (i == 3 || i == 4) {
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

    //授权credence-net:super_admin操作
    val granteds = new ArrayBuffer[String]
    granteds += nodes(6)._2

    // 授权节点所有合约相关的操作
    val opids = new ArrayBuffer[String]
    for (i <- 0 to 6) {
      opids += opsOfContract(i)._1
    }

    val als = new ArrayBuffer[String]
    granteds.foreach(granted => {
      opids.foreach(op => {
        val gs = Array {
          granted
        }
        val os = Array {
          op
        }
        val tmpmillis = System.currentTimeMillis()
        val at = Authorize(IdTool.getRandomUUID, super_credit, gs, os,
          TransferType.TRANSFER_REPEATEDLY, Option(Timestamp(tmpmillis / 1000, ((tmpmillis % 1000) * 1000000).toInt)),
          _root_.scala.None, true, "1.0")
        als += JsonFormat.toJsonString(at)
      })
    })
    translist += ctx.getTransactionBuilder.createTransaction4Invoke(superAdmin, cid1, "grantOperate", Seq(SerializeUtils.compactJson(als)))

    //create gensis block
    val millis = ConfigFactory.load().getLong("akka.genesisblock.creationBlockTime")
    var blk = BlockHelp.buildBlock("", 1, translist)
    blk = blk.withHeader(blk.getHeader.clearEndorsements)
    blk = blk.clearTransactionResults
    val r = JsonFormat.toJson(blk)
    val rstr = pretty(render(r))
    println(rstr)

    pathUtil.MkdirAll(s"json/${ctx.getConfig.getChainNetworkId}")

    val pw = new PrintWriter(s"json/${ctx.getConfig.getChainNetworkId}/genesis.json", "UTF-8")
    pw.write(rstr)
    pw.flush()
    pw.close()
  }
}
