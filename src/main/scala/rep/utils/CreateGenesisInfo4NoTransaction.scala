package rep.utils

import java.io.PrintWriter

import com.google.protobuf.timestamp.Timestamp
import com.typesafe.config.ConfigFactory
import org.json4s.{DefaultFormats, jackson}
import org.json4s.jackson.JsonMethods.{pretty, render}
import rep.app.system.RepChainSystemContext
import rep.network.consensus.util.BlockHelp
import rep.proto.rc2.Authorize.TransferType
import rep.proto.rc2.Certificate.CertType
import rep.proto.rc2.ChaincodeDeploy.{CodeType, RunType, StateType}
import rep.proto.rc2.Operate.OperateType
import rep.proto.rc2.{Authorize, Certificate, ChaincodeDeploy, ChaincodeId, Operate, Signer, Transaction}
import rep.sc.tpl.did.RVerifiableCredentialTPL
import rep.storage.util.pathUtil
import scalapb.json4s.JsonFormat

import scala.collection.mutable.ArrayBuffer

object CreateGenesisInfo4NoTransaction {
  implicit val serialization = jackson.Serialization // or native.Serialization
  implicit val formats = DefaultFormats

  def main(args: Array[String]): Unit = {

    val sysName = "330597659476689954.node6"
    val sys_credit = "121000005l35120456"
    val superAdmin = "951002007l78123233.super_admin"
    val super_credit = "951002007l78123233"

    val ctx = new RepChainSystemContext(sysName,null)
    ctx.getSignTool.loadPrivateKey(sysName, "123", s"${ctx.getCryptoMgr.getKeyFileSuffix.substring(1)}/${ctx.getConfig.getChainNetworkId}/$sysName${ctx.getCryptoMgr.getKeyFileSuffix}")
    //ctx.getSignTool.loadNodeCertList("changeme", s"${ctx.getCryptoMgr.getKeyFileSuffix.substring(1)}/${ctx.getConfig.getChainNetworkId}/mytruststore${ctx.getCryptoMgr.getKeyFileSuffix}")
    ctx.getSignTool.loadPrivateKey(superAdmin, "super_admin", s"${ctx.getCryptoMgr.getKeyFileSuffix.substring(1)}/${ctx.getConfig.getChainNetworkId}/$superAdmin${ctx.getCryptoMgr.getKeyFileSuffix}")

    val translist: ArrayBuffer[Transaction] = new ArrayBuffer[Transaction]


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

    pathUtil.MkdirAll(s"json/${ctx.getConfig.getChainNetworkId}")

    val pw = new PrintWriter(s"json/${ctx.getConfig.getChainNetworkId}/genesis.json", "UTF-8")
    pw.write(rstr)
    pw.flush()
    pw.close()
  }
}