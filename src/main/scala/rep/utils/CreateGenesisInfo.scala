package rep.utils

import java.io.PrintWriter

import com.google.protobuf.timestamp.Timestamp
import com.typesafe.config.ConfigFactory
import org.json4s.{DefaultFormats, jackson}
import org.json4s.jackson.JsonMethods.{pretty, render}
import rep.crypto.Sha256
import rep.crypto.cert.SignTool
import rep.network.autotransaction.PeerHelper
import rep.protos.peer.Authorize.TransferType
import rep.protos.peer.Certificate.CertType
import rep.protos.peer.Operate.OperateType
import rep.protos.peer.{Block, ChaincodeId, Signer, Transaction}
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

    //使用超级管理员（链密钥的持有人）部署内置的RDID权限管理合约
    val s1 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/did/RdidOperateAuthorizeTPL.scala","UTF-8")
    val l1 = try s1.mkString finally s1.close()
    val cid = new ChaincodeId("RdidOperateAuthorizeTPL",1)

    var translist : Array[Transaction] = new Array[Transaction] (39)
    val deploy_trans = PeerHelper.createTransaction4Deploy(superAdmin, cid,
      l1, "",5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA,
      rep.protos.peer.ChaincodeDeploy.ContractClassification.CONTRACT_SYSTEM)
    translist(0) = deploy_trans
    //注册合约的管理者，默认注册某个节点的DiD，并授予角色管理权限
    //注册节点DiD
    var nodes : Array[(String,String,String)] = new Array[(String,String,String)](6)
    nodes(0) = ("super_admin","951002007l78123233","18912345678")
    nodes(1) = ("node1","121000005l35120456","18912345678")
    nodes(2) = ("node2","12110107bi45jh675g","18912345678")
    nodes(3) = ("node3","122000002n00123567","18912345678")
    nodes(4) = ("node4","921000005k36123789","18912345678")
    nodes(5) = ("node5","921000006e0012v696","18912345678")
    for(i<-0 to 5){
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
      translist(i+1) = PeerHelper.createTransaction4Invoke(superAdmin, cid,
        "signUpSigner", Seq(JsonFormat.toJsonString(signer_tmp)))
    }
    //注册操作
    //权限管理合约操作注册
    var opsOfContract : Array[(String,String,String)] = new Array[(String,String,String)](11)
    opsOfContract(0) = (IdTool.getRandomUUID,"注册RDID","RdidOperateAuthorizeTPL.signUpSigner")
    opsOfContract(1) = (IdTool.getRandomUUID,"禁用RDID","RdidOperateAuthorizeTPL.disableSigner")
    opsOfContract(2) = (IdTool.getRandomUUID,"注册证书","RdidOperateAuthorizeTPL.signUpCertificate")
    opsOfContract(3) = (IdTool.getRandomUUID,"禁用证书","RdidOperateAuthorizeTPL.disableCertificate")
    opsOfContract(4) = (IdTool.getRandomUUID,"注册操作","RdidOperateAuthorizeTPL.signUpOperate")
    opsOfContract(5) = (IdTool.getRandomUUID,"禁用操作","RdidOperateAuthorizeTPL.disableOperate")
    opsOfContract(6) = (IdTool.getRandomUUID,"授权操作","RdidOperateAuthorizeTPL.grantOperate")
    opsOfContract(7) = (IdTool.getRandomUUID,"禁用授权","RdidOperateAuthorizeTPL.disableGrantOperate")
    opsOfContract(8) = (IdTool.getRandomUUID,"绑定证书到授权操作","RdidOperateAuthorizeTPL.bindCertToAuthorize")
    opsOfContract(9) = (IdTool.getRandomUUID,"发布合约操作","*.deploy")//*表示可以发布任意合约
    opsOfContract(10) = (IdTool.getRandomUUID,"改变合约状态操作","*.setState")//*表示可以设置任意合约状态

    for(i<-0 to 10){
      val millis = System.currentTimeMillis()
      val snls = List("transaction.stream","transaction.postTranByString","transaction.postTranStream","transaction.postTran")
      //生成Operate
      val op = rep.protos.peer.Operate(opsOfContract(i)._1,opsOfContract(i)._2,super_credit,false,OperateType.OPERATE_CONTRACT,
                                      snls,"*",opsOfContract(i)._3,Option(Timestamp(millis/1000 , ((millis % 1000) * 1000000).toInt)),
                                      _root_.scala.None,true,"1.0")
      translist(i+7) = PeerHelper.createTransaction4Invoke(superAdmin, cid,
        "signUpOperate", Seq(JsonFormat.toJsonString(op)))
    }

    //api操作注册
    var opsOfAPI : Array[(String,String,String)] = new Array[(String,String,String)](15)
    opsOfAPI(0) = (IdTool.getRandomUUID,"获取链信息","chaininfo.chaininfo")
    opsOfAPI(1) = (IdTool.getRandomUUID,"返回组网节点数量","chaininfo.node")
    opsOfAPI(2) = (IdTool.getRandomUUID,"返回系统缓存交易数量","chaininfo.getcachetransnumber")
    opsOfAPI(3) = (IdTool.getRandomUUID,"返回系统接收到的交易数量","chaininfo.getAcceptedTransNumber")

    opsOfAPI(4) = (IdTool.getRandomUUID,"返回指定id的区块","block.hash")
    opsOfAPI(5) = (IdTool.getRandomUUID,"返回指定高度的区块","block.blockHeight")
    opsOfAPI(6) = (IdTool.getRandomUUID,"返回指定高度区块包含的交易数","block.getTransNumberOfBlock")
    opsOfAPI(7) = (IdTool.getRandomUUID,"返回指定高度的区块的出块时间","block.blocktime")
    opsOfAPI(8) = (IdTool.getRandomUUID,"返回指定交易的入块时间","block.blocktimeoftran")
    opsOfAPI(9) = (IdTool.getRandomUUID,"返回指定高度的区块字节流","block.stream")

    opsOfAPI(10) = (IdTool.getRandomUUID,"返回指定id的交易","transaction")
    opsOfAPI(11) = (IdTool.getRandomUUID,"返回指定id的交易字节流","transaction.stream")
    opsOfAPI(12) = (IdTool.getRandomUUID,"提交带签名的交易","transaction.postTranByString")
    opsOfAPI(13) = (IdTool.getRandomUUID,"提交带签名的交易字节流","transaction.postTranStream")
    opsOfAPI(14) = (IdTool.getRandomUUID,"提交交易","transaction.postTran")

    for(i<-0 to 14){
      val millis = System.currentTimeMillis()
      val snls = List("transaction.stream","transaction.postTranByString","transaction.postTranStream","transaction.postTran")
      //生成Operate
      val op = rep.protos.peer.Operate(opsOfAPI(i)._1,opsOfAPI(i)._2,
        super_credit,false,OperateType.OPERATE_SERVICE,
        List(opsOfAPI(i)._3),"*","",Option(Timestamp(millis/1000 , ((millis % 1000) * 1000000).toInt)),
        _root_.scala.None,true,"1.0")
      translist(i+18) = PeerHelper.createTransaction4Invoke(superAdmin, cid,
        "signUpOperate", Seq(JsonFormat.toJsonString(op)))
    }

    //授权节点操作
    val granteds = new Array[String](5)
    for(i<-1 to 5){
      granteds(i-1) = nodes(i)._2
    }

    val opids = new Array[String](26)
    for(i<-0 to 10){
      opids(i) = opsOfContract(i)._1
    }

    for(i<-0 to 14){
      opids(i+11) = opsOfAPI(i)._1
    }

    val tmpmillis = System.currentTimeMillis()
    val at = rep.protos.peer.Authorize(IdTool.getRandomUUID,super_credit,granteds,opids,
      TransferType.TRANSFER_REPEATEDLY,Option(Timestamp(tmpmillis/1000 , ((tmpmillis % 1000) * 1000000).toInt)),
      _root_.scala.None,true,"1.0")
    translist(33) = PeerHelper.createTransaction4Invoke(superAdmin, cid,
      "grantOperate", Seq(JsonFormat.toJsonString(at)))

    //部署应用合约--分账合约
    val s2 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/ContractAssetsTPL.scala","UTF-8")
    val c2 = try s2.mkString finally s2.close()
    val cid2 = new ChaincodeId("ContractAssetsTPL",1)
    val dep_asserts_trans = PeerHelper.createTransaction4Deploy(sysName, cid2,
      c2, "",5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA,
      rep.protos.peer.ChaincodeDeploy.ContractClassification.CONTRACT_CUSTOM)
    translist(34) = dep_asserts_trans

    //建立应用合约的操作
    var opsOfCustomContract : Array[(String,String,String)] = new Array[(String,String,String)](3)
    opsOfCustomContract(0) = (IdTool.getRandomUUID,"转账交易","ContractAssetsTPL.transfer")
    opsOfCustomContract(1) = (IdTool.getRandomUUID,"初始化账户","ContractAssetsTPL.set")
    opsOfCustomContract(2) = (IdTool.getRandomUUID,"存证", "ContractAssetsTPL.putProof")

    val tmillis = System.currentTimeMillis()
    val snls = List("transaction.stream","transaction.postTranByString","transaction.postTranStream","transaction.postTran")
    //生成Operate 转账操作属于公开的，任何人都可以发起转账，无需赋权
    val op1 = rep.protos.peer.Operate(opsOfCustomContract(0)._1,opsOfCustomContract(0)._2,super_credit,true,OperateType.OPERATE_CONTRACT,
      snls,"*",opsOfCustomContract(0)._3,Option(Timestamp(tmillis/1000 , ((tmillis % 1000) * 1000000).toInt)),
      _root_.scala.None,true,"1.0")
    translist(35) = PeerHelper.createTransaction4Invoke(superAdmin, cid,
      "signUpOperate", Seq(JsonFormat.toJsonString(op1)))

    //生成Operate 存证操作属于公开的，任何人都可以发起存证，无需赋权
    val op2 = rep.protos.peer.Operate(opsOfCustomContract(2)._1,opsOfCustomContract(2)._2,super_credit,true,OperateType.OPERATE_CONTRACT,
      snls,"*",opsOfCustomContract(2)._3,Option(Timestamp(tmillis/1000 , ((tmillis % 1000) * 1000000).toInt)),
      _root_.scala.None,true,"1.0")
    translist(36) = PeerHelper.createTransaction4Invoke(superAdmin, cid,
      "signUpOperate", Seq(JsonFormat.toJsonString(op2)))

    //生成Operate 初始化只能是超级节点可以做，注册操作，但是不授权给其他人
    val op3 = rep.protos.peer.Operate(opsOfCustomContract(1)._1,opsOfCustomContract(1)._2,super_credit,false,OperateType.OPERATE_CONTRACT,
      snls,"*",opsOfCustomContract(1)._3,Option(Timestamp(tmillis/1000 , ((tmillis % 1000) * 1000000).toInt)),
      _root_.scala.None,true,"1.0")
    translist(37) = PeerHelper.createTransaction4Invoke(superAdmin, cid,
      "signUpOperate", Seq(JsonFormat.toJsonString(op3)))

    // 设置账户初始金额
    val s3 = scala.io.Source.fromFile("api_req/json/set.json","UTF-8")
    val ct1 = try s3.mkString finally s3.close()
    translist(38) = PeerHelper.createTransaction4Invoke("951002007l78123233.super_admin", cid2,
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
    val r = JsonFormat.toJson(blk)
    val rstr = pretty(render(r))
    println(rstr)

    val pw = new PrintWriter("json/gensis.json","UTF-8")
    pw.write(rstr)
    pw.flush()
    pw.close()
  }
}