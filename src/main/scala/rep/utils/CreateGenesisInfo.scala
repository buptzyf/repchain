package rep.utils

import java.io.PrintWriter

import com.google.protobuf.timestamp.Timestamp
import com.typesafe.config.ConfigFactory
import org.json4s.jackson.JsonMethods.{pretty, render}
import org.json4s.{DefaultFormats, jackson}
import rep.crypto.Sha256
import rep.crypto.cert.SignTool
import rep.network.autotransaction.PeerHelper
import rep.protos.peer.Authorize.TransferType
import rep.protos.peer.Certificate.CertType
import rep.protos.peer.Operate.OperateType
import rep.protos.peer.{Block, ChaincodeId, Operate, Transaction}
import scalapb.json4s.JsonFormat

object CreateGenesisInfo {
  implicit val serialization = jackson.Serialization // or native.Serialization
  implicit val formats       = DefaultFormats

  def main(args: Array[String]): Unit = {
    SignTool.loadPrivateKey("121000005l35120456.node1", "123", "jks/121000005l35120456.node1.jks")
    SignTool.loadNodeCertList("changeme", "jks/mytruststore.jks")
    SignTool.loadPrivateKey("951002007l78123233.super_admin", "super_admin", "jks/951002007l78123233.super_admin.jks")
    val sysName = "121000005l35120456.node1"
    val superAdmin = "951002007l78123233.super_admin"
    val super_credit = "951002007l78123233"

    //使用超级管理员（链密钥的持有人）部署内置的RDID权限管理合约以及RVC管理合约
    val s1 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/did/RdidOperateAuthorizeTPL.scala","UTF-8")
    val l1 = try s1.mkString finally s1.close()
    val cid = new ChaincodeId("RdidOperateAuthorizeTPL",1)
    val sVC = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/did/RVerifiableCredentialTPL.scala","UTF-8")
    val sourceCodeVC = try sVC.mkString finally sVC.close()
    val cidVC = new ChaincodeId("RVerifiableCredentialTPL",1)

    var translist : Array[Transaction] = new Array[Transaction] (50)
    var curTxIndex : Int = -1
    val deploy_trans = PeerHelper.createTransaction4Deploy(superAdmin, cid,
      l1, "",5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA,
      rep.protos.peer.ChaincodeDeploy.ContractClassification.CONTRACT_SYSTEM)
    val deployVCTx = PeerHelper.createTransaction4Deploy(superAdmin, cidVC,
      sourceCodeVC, "",5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA,
      rep.protos.peer.ChaincodeDeploy.ContractClassification.CONTRACT_SYSTEM)
    curTxIndex += 1; translist(curTxIndex) = deploy_trans
    curTxIndex += 1; translist(curTxIndex) = deployVCTx
    //注册合约的管理者，默认注册某个节点的DiD，并授予角色管理权限
    //注册节点DiD
    var nodes : Array[(String,String,String)] = new Array[(String,String,String)](6)
    nodes(0) = ("super_admin","951002007l78123233","18912345678")
    nodes(1) = ("node1","121000005l35120456","18912345678")
    nodes(2) = ("node2","12110107bi45jh675g","18912345678")
    nodes(3) = ("node3","122000002n00123567","18912345678")
    nodes(4) = ("node4","921000005k36123789","18912345678")
    nodes(5) = ("node5","921000006e0012v696","18912345678")
    for(i<-0 to (nodes.length - 1)){
      val certfile = scala.io.Source.fromFile("jks/"+nodes(i)._2+"."+nodes(i)._1+".cer","UTF-8")
      val certstr = try certfile.mkString finally certfile.close()
      val certstrhash = Sha256.hashstr(certstr)
      val certid = IdTool.getCertIdFromName(nodes(i)._2+"."+nodes(i)._1)
      val millis = System.currentTimeMillis()
      //生成Did的身份证书
      val authcert = rep.protos.peer.Certificate(certstr,"SHA1withECDSA",true,Option(Timestamp(millis/1000 , ((millis % 1000) * 1000000).toInt)),
        _root_.scala.None,CertType.CERT_AUTHENTICATION,Option(certid),certstrhash,"1.0")
      val signer_tmp = rep.protos.peer.Signer(nodes(i)._1,nodes(i)._2,nodes(i)._3,_root_.scala.Seq.empty,
            _root_.scala.Seq.empty,_root_.scala.Seq.empty,_root_.scala.Seq.empty,List(authcert), "",
            Option(Timestamp(millis/1000 , ((millis % 1000) * 1000000).toInt)),_root_.scala.None,true,"1.0")
      curTxIndex += 1; translist(curTxIndex) = PeerHelper.createTransaction4Invoke(superAdmin, cid,
        "signUpSigner", Seq(JsonFormat.toJsonString(signer_tmp)))
    }
    //注册操作
    var opsOfContract : Array[(String,String)] = new Array[(String,String)](21)

    //权限管理合约操作注册
    opsOfContract(0) = ("RdidOperateAuthorizeTPL.signUpSigner", "注册RDID")
    opsOfContract(1) = ("RdidOperateAuthorizeTPL.disableSigner", "禁用RDID")
    opsOfContract(2) = ("RdidOperateAuthorizeTPL.updateSigner", "更新信息")
    opsOfContract(3) = ("RdidOperateAuthorizeTPL.enableSigner", "启用RDID")
    opsOfContract(4) = ("RdidOperateAuthorizeTPL.signUpCertificate", "注册证书")
    opsOfContract(5) = ("RdidOperateAuthorizeTPL.disableCertificate", "禁用证书")
    opsOfContract(6) = ("RdidOperateAuthorizeTPL.enableCertificate","启用证书")
    opsOfContract(7) = ("RdidOperateAuthorizeTPL.signUpOperate", "注册操作")
    opsOfContract(8) = ("RdidOperateAuthorizeTPL.disableOperate", "禁用操作")
    opsOfContract(9) = ("RdidOperateAuthorizeTPL.enableOperate", "启用操作")
    opsOfContract(10) = ("RdidOperateAuthorizeTPL.grantOperate", "授权操作")
    opsOfContract(11) = ("RdidOperateAuthorizeTPL.disableGrantOperate", "禁用授权")
    opsOfContract(12) = ("RdidOperateAuthorizeTPL.enableGrantOperate", "启用授权")
    opsOfContract(13) = ("RdidOperateAuthorizeTPL.bindCertToAuthorize", "绑定证书到授权操作")
    opsOfContract(14) = ("*.deploy", "发布合约操作") //*表示可以发布任意合约
    opsOfContract(15) = ("*.setState", "改变合约状态操作") //*表示可以设置任意合约状态

    //可验证凭据管理合约操作
    opsOfContract(16) = ("RVerifiableCredentialTPL.signupCCS", "注册可验证凭据属性结构")
    opsOfContract(17) = ("RVerifiableCredentialTPL.updateCCSStatus", "更新可验证凭据属性结构有效状态")
    opsOfContract(18) = ("RVerifiableCredentialTPL.signupVCStatus", "注册可验证凭据状态")
    opsOfContract(19) = ("RVerifiableCredentialTPL.updateVCStatus", "更新可验证凭据状态")
    opsOfContract(20) = ("RVerifiableCredentialTPL.revokeVCClaims", "撤销可验证凭据属性状态")

    for(i<-0 to (opsOfContract.length - 1)){
      val millis = System.currentTimeMillis()
      val snls = List("transaction.stream","transaction.postTranByString","transaction.postTranStream","transaction.postTran")
      //生成Operate
      var op : Operate = null
      if(i == 13) {
        op = rep.protos.peer.Operate(Sha256.hashstr(opsOfContract(i)._1),opsOfContract(i)._2,super_credit,true,OperateType.OPERATE_CONTRACT,
          snls,"*",opsOfContract(i)._1,Option(Timestamp(millis/1000 , ((millis % 1000) * 1000000).toInt)),
          _root_.scala.None,true,"1.0")
      }else {
        op = rep.protos.peer.Operate(Sha256.hashstr(opsOfContract(i)._1),opsOfContract(i)._2,super_credit,false,OperateType.OPERATE_CONTRACT,
          snls,"*",opsOfContract(i)._1,Option(Timestamp(millis/1000 , ((millis % 1000) * 1000000).toInt)),
          _root_.scala.None,true,"1.0")
      }
      curTxIndex += 1; translist(curTxIndex) = PeerHelper.createTransaction4Invoke(superAdmin, cid,
        "signUpOperate", Seq(JsonFormat.toJsonString(op)))
    }

    //api操作注册
    var opsOfAPI : Array[(String,String)] = new Array[(String,String)](15)
    opsOfAPI(0) = ("chaininfo.chaininfo", "获取链信息")
    opsOfAPI(1) = ("chaininfo.node", "返回组网节点数量")
    opsOfAPI(2) = ("chaininfo.getcachetransnumber", "返回系统缓存交易数量")
    opsOfAPI(3) = ("chaininfo.getAcceptedTransNumber", "返回系统接收到的交易数量")

    opsOfAPI(4) = ("block.hash", "返回指定id的区块")
    opsOfAPI(5) = ("block.blockHeight", "返回指定高度的区块")
    opsOfAPI(6) = ("block.getTransNumberOfBlock", "返回指定高度区块包含的交易数")
    opsOfAPI(7) = ("block.blocktime", "返回指定高度的区块的出块时间")
    opsOfAPI(8) = ("block.blocktimeoftran", "返回指定交易的入块时间")
    opsOfAPI(9) = ("block.stream", "返回指定高度的区块字节流")

    opsOfAPI(10) = ("transaction", "返回指定id的交易")
    opsOfAPI(11) = ("transaction.stream", "返回指定id的交易字节流")
    opsOfAPI(12) = ("transaction.postTranByString", "提交带签名的交易")
    opsOfAPI(13) = ("transaction.postTranStream", "提交带签名的交易字节流")
    opsOfAPI(14) = ("transaction.postTran", "提交交易")

    for(i<-0 to (opsOfAPI.length - 1)){
      val millis = System.currentTimeMillis()
      val snls = List("transaction.stream","transaction.postTranByString","transaction.postTranStream","transaction.postTran")
      //生成Operate
      val op = rep.protos.peer.Operate(Sha256.hashstr(opsOfAPI(i)._1),opsOfAPI(i)._2,
        super_credit,false,OperateType.OPERATE_SERVICE,
        List(opsOfAPI(i)._1),"*","",Option(Timestamp(millis/1000 , ((millis % 1000) * 1000000).toInt)),
        _root_.scala.None,true,"1.0")
      curTxIndex += 1; translist(curTxIndex) = PeerHelper.createTransaction4Invoke(superAdmin, cid,
        "signUpOperate", Seq(JsonFormat.toJsonString(op)))
    }

    //授权节点操作
    val granteds = new Array[String](nodes.length - 1)
    for(i<-1 to granteds.length){
      granteds(i-1) = nodes(i)._2
    }

    val opids = new Array[String](opsOfContract.length + opsOfAPI.length)
    for(i<-0 to (opsOfContract.length - 1)){
      opids(i) = Sha256.hashstr(opsOfContract(i)._1)
    }

    for(i<-0 to (opsOfAPI.length - 1)){
      opids(i + opsOfContract.length) = Sha256.hashstr(opsOfAPI(i)._1)
    }

    val tmpmillis = System.currentTimeMillis()
    val at = rep.protos.peer.Authorize(IdTool.getRandomUUID,super_credit,granteds,opids,
      TransferType.TRANSFER_REPEATEDLY,Option(Timestamp(tmpmillis/1000 , ((tmpmillis % 1000) * 1000000).toInt)),
      _root_.scala.None,true,"1.0")
    var als : List[String]  =  List(JsonFormat.toJsonString(at))
    curTxIndex += 1; translist(curTxIndex) = PeerHelper.createTransaction4Invoke(superAdmin, cid,
      "grantOperate", Seq(SerializeUtils.compactJson(als)))

    //部署应用合约--分账合约
    val s2 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/ContractAssetsTPL.scala","UTF-8")
    val c2 = try s2.mkString finally s2.close()
    val cid2 = new ChaincodeId("ContractAssetsTPL",1)
    val dep_asserts_trans = PeerHelper.createTransaction4Deploy(sysName, cid2,
      c2, "",5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA,
      rep.protos.peer.ChaincodeDeploy.ContractClassification.CONTRACT_CUSTOM)
    curTxIndex += 1; translist(curTxIndex) = dep_asserts_trans

    //建立应用合约的操作
    var opsOfCustomContract : Array[(String,String)] = new Array[(String,String)](3)
    opsOfCustomContract(0) = ("ContractAssetsTPL.transfer", "转账交易")
    opsOfCustomContract(1) = ("ContractAssetsTPL.set", "初始化账户")
    opsOfCustomContract(2) = ("ContractAssetsTPL.putProof", "存证")

    val tmillis = System.currentTimeMillis()
    val snls = List("transaction.stream","transaction.postTranByString","transaction.postTranStream","transaction.postTran")
    //生成Operate 转账操作属于公开的，任何人都可以发起转账，无需赋权
    val op1 = rep.protos.peer.Operate(Sha256.hashstr(opsOfCustomContract(0)._1),opsOfCustomContract(0)._2,super_credit,true,OperateType.OPERATE_CONTRACT,
      snls,"*",opsOfCustomContract(0)._1,Option(Timestamp(tmillis/1000 , ((tmillis % 1000) * 1000000).toInt)),
      _root_.scala.None,true,"1.0")
    curTxIndex += 1; translist(curTxIndex) = PeerHelper.createTransaction4Invoke(superAdmin, cid,
      "signUpOperate", Seq(JsonFormat.toJsonString(op1)))

    //生成Operate 存证操作属于公开的，任何人都可以发起存证，无需赋权
    val op2 = rep.protos.peer.Operate(Sha256.hashstr(opsOfCustomContract(2)._1),opsOfCustomContract(2)._2,super_credit,true,OperateType.OPERATE_CONTRACT,
      snls,"*",opsOfCustomContract(2)._1,Option(Timestamp(tmillis/1000 , ((tmillis % 1000) * 1000000).toInt)),
      _root_.scala.None,true,"1.0")
    curTxIndex += 1; translist(curTxIndex) = PeerHelper.createTransaction4Invoke(superAdmin, cid,
      "signUpOperate", Seq(JsonFormat.toJsonString(op2)))

    //生成Operate 初始化只能是超级节点可以做，注册操作，但是不授权给其他人
    val op3 = rep.protos.peer.Operate(Sha256.hashstr(opsOfCustomContract(1)._1),opsOfCustomContract(1)._2,super_credit,false,OperateType.OPERATE_CONTRACT,
      snls,"*",opsOfCustomContract(1)._1,Option(Timestamp(tmillis/1000 , ((tmillis % 1000) * 1000000).toInt)),
      _root_.scala.None,true,"1.0")
    curTxIndex += 1; translist(curTxIndex) = PeerHelper.createTransaction4Invoke(superAdmin, cid,
      "signUpOperate", Seq(JsonFormat.toJsonString(op3)))

    // 设置账户初始金额
    val s3 = scala.io.Source.fromFile("api_req/json/set.json","UTF-8")
    val ct1 = try s3.mkString finally s3.close()
    curTxIndex += 1; translist(curTxIndex) = PeerHelper.createTransaction4Invoke("951002007l78123233.super_admin", cid2,
      "set", Seq(ct1))



    //create gensis block
    val millis = ConfigFactory.load().getLong("akka.genesisblock.creationBlockTime")

    var blk = new Block(1,1,translist,Seq(),_root_.com.google.protobuf.ByteString.EMPTY,
      _root_.com.google.protobuf.ByteString.EMPTY)

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
    blk = blk.clearEndorsements
    blk = blk.clearTransactionResults
    val r = MessageToJson.toJson(blk)
    val rstr = pretty(render(r))
    println(rstr)

    val pw = new PrintWriter("json/genesis.json","UTF-8")
    pw.write(rstr)
    pw.flush()
    pw.close()
  }
}