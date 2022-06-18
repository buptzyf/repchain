package rep.utils.credence

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
import rep.sc.tpl.did.RVerifiableCredentialTPL
import rep.storage.util.pathUtil
import rep.utils.{IdTool, SerializeUtils}
import scalapb.json4s.JsonFormat

import java.io.PrintWriter
import scala.collection.mutable.ArrayBuffer

object CreateCredenceGenesisInfo {
  implicit val serialization = jackson.Serialization // or native.Serialization
  implicit val formats = DefaultFormats

  def main(args: Array[String]): Unit = {
    val ctx = new RepChainSystemContext("330597659476689954.node6")
    ctx.getSignTool.loadPrivateKey("951002007l78123233.super_admin", "super_admin", s"${ctx.getCryptoMgr.getKeyFileSuffix.substring(1)}/${ctx.getConfig.getChainNetworkId}/951002007l78123233.super_admin${ctx.getCryptoMgr.getKeyFileSuffix}")
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
    val flag = s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}"
    val nodes: Array[(String, String, String)] = new Array[(String, String, String)](6)
    nodes(0) = ("super_admin", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}951002007l78123233", "18912345678")
    nodes(1) = ("node6", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}330597659476689954", "18912345678")
    nodes(2) = ("node7", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}044934755127708189", "18912345678")
    nodes(3) = ("node8", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}201353514191149590", "18912345678")
    nodes(4) = ("node9", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}734747416095474396", "18912345678")
    nodes(5) = ("node10", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}710341838996249513", "18912345678")

    //注册操作
    //权限管理合约操作注册
    val opsOfContract: Array[(String, String, String)] = new Array[(String, String, String)](14)
    opsOfContract(0) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.signUpOperate"), "注册操作，自己注册自己", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.signUpOperate")
    opsOfContract(1) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.updateOperateStatus"), "禁用或启用操作，自己更新自己名下的操作", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.updateOperateStatus")
    opsOfContract(2) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.grantOperate"), "授权操作", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.grantOperate")
    opsOfContract(3) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.updateGrantOperateStatus"), "禁用或启用授权", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.updateGrantOperateStatus")
    opsOfContract(4) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.bindCertToAuthorize"), "绑定证书到授权操作", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}RdidOperateAuthorizeTPL.bindCertToAuthorize")
    opsOfContract(5) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}*.deploy"), "发布合约操作", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}*.deploy") //*表示可以发布任意合约
    opsOfContract(6) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}*.setState"), "改变合约状态操作", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}*.setState") //*表示可以设置任意合约状态

    for (i <- 0 to 6) {
      val millis = System.currentTimeMillis()
      val snls = List("transaction.stream", "transaction.postTranByString", "transaction.postTranStream", "transaction.postTran")
      //生成Operate
      var op: Operate = null
      if (i == 0 || i == 1 || i == 2 || i == 3 || i == 4) {
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
    val opsOfAPI: Array[(String, String, String, Boolean)] = new Array[(String, String, String, Boolean)](17)
    opsOfAPI(0) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}chaininfo.chaininfo"), "获取链信息", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}chaininfo.chaininfo", true)
    opsOfAPI(1) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}chaininfo.node"), "返回组网节点数量", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}chaininfo.node", true)
    opsOfAPI(2) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}chaininfo.getcachetransnumber"), "返回系统缓存交易数量", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}chaininfo.getcachetransnumber", true)
    opsOfAPI(3) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}chaininfo.getAcceptedTransNumber"), "返回系统接收到的交易数量", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}chaininfo.getAcceptedTransNumber", true)

    opsOfAPI(4) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}block.hash"), "返回指定id的区块", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}block.hash", false)
    opsOfAPI(5) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}block.blockHeight"), "返回指定高度的区块", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}block.blockHeight", false)
    opsOfAPI(6) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}block.getTransNumberOfBlock"), "返回指定高度区块包含的交易数", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}block.getTransNumberOfBlock", true)
    opsOfAPI(7) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}block.blocktime"), "返回指定高度的区块的出块时间", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}block.blocktime", true)
    opsOfAPI(8) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}block.blocktimeoftran"), "返回指定交易的入块时间", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}block.blocktimeoftran", true)
    opsOfAPI(9) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}block.stream"), "返回指定高度的区块字节流", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}block.stream", false)

    opsOfAPI(10) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}transaction"), "返回指定id的交易", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}transaction", false)
    opsOfAPI(11) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}transaction.stream"), "返回指定id的交易字节流", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}transaction.stream", false)
    opsOfAPI(12) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}transaction.postTranByString"), "提交带签名的交易", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}transaction.postTranByString", true)
    opsOfAPI(13) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}transaction.postTranStream"), "提交带签名的交易字节流", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}transaction.postTranStream", true)
    opsOfAPI(14) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}transaction.postTran"), "提交交易", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}transaction.postTran", true)
    opsOfAPI(15) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}transaction.tranInfoAndHeight"), "回指定id的交易信息及所在区块高度", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}transaction.tranInfoAndHeight", false)
    opsOfAPI(16) = (ctx.getHashTool.hashstr(s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}db.query"), "查询合约存储在DB中的数据", s"${ctx.getConfig.getChainNetworkId}${IdTool.DIDPrefixSeparator}db.query", false)


    for (i <- 0 to 16) {
      val millis = System.currentTimeMillis()
      val snls = List("transaction.stream", "transaction.postTranByString", "transaction.postTranStream", "transaction.postTran")
      //生成Operate，不是公开的
      val op = Operate(opsOfAPI(i)._1, opsOfAPI(i)._2, super_credit, opsOfAPI(i)._4, OperateType.OPERATE_SERVICE,
        snls, "*", opsOfAPI(i)._3, Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)),
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
    for (i <- 0 to 6) {
      opids += opsOfContract(i)._1
    }

    // 授权节点所有api相关的操作
    for (i <- 0 to 16) {
      opids += opsOfAPI(i)._1
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
