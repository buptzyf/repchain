/*
 * Copyright  2019 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BA SIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package rep.utils

import java.io.PrintWriter
import com.typesafe.config.ConfigFactory
import com.google.protobuf.timestamp.Timestamp
import scalapb.json4s.JsonFormat
import org.json4s.{DefaultFormats, jackson}
import org.json4s.jackson.JsonMethods._
import rep.app.system.RepChainSystemContext
import rep.network.consensus.util.BlockHelp
import rep.proto.rc2.{CertId, Certificate, ChaincodeDeploy, ChaincodeId, Signer, Transaction}

/**
 * 用于生成创世块json文件,该json文件可以在链初始化时由节点加载
 * 创世块中预置了deploy基础方法的交易
 *@author shidianyue
 */
object GenesisBuilder1 {

  implicit val serialization = jackson.Serialization // or native.Serialization
  implicit val formats       = DefaultFormats

  def main(args: Array[String]): Unit = {
    val ctx = new RepChainSystemContext("121000005l35120456.node1")

    ctx.getSignTool.loadPrivateKey("121000005l35120456.node1", "123", s"${ctx.getCryptoMgr.getKeyFileSuffix.substring(1)}/121000005l35120456.node1${ctx.getCryptoMgr.getKeyFileSuffix}")
    //ctx.getSignTool.loadNodeCertList("changeme", s"${ctx.getCryptoMgr.getKeyFileSuffix.substring(1)}/mytruststore${ctx.getCryptoMgr.getKeyFileSuffix}")
    ctx.getSignTool.loadPrivateKey("951002007l78123233.super_admin", "super_admin", s"${ctx.getCryptoMgr.getKeyFileSuffix.substring(1)}/951002007l78123233.super_admin${ctx.getCryptoMgr.getKeyFileSuffix}")
    val sysName = "121000005l35120456.node1"
    //交易发起人是超级管理员
    //增加scala的资产管理合约   
    // read deploy funcs
    val s1 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/ContractCert.scala","UTF-8")
    val l1 = try s1.mkString finally s1.close()
    
    val cid = new ChaincodeId("ContractCert",1)
    
    var translist : Array[Transaction] = new Array[Transaction] (15)
    
    
    //val dep_trans = PeerHelper.createTransaction4Deploy(sysName, cid,
    //           l1, "",5000, ChaincodeDeploy.CodeType.CODE_SCALA)
    val dep_trans = ctx.getTransactionBuilder.createTransaction4Deploy("951002007l78123233.super_admin", cid,
               l1, "",5000, ChaincodeDeploy.CodeType.CODE_SCALA)
    translist(0) = dep_trans
    
    //val dep_trans_state = PeerHelper.createTransaction4State(sysName, cid, true)
    //translist(1) = dep_trans_state
    
    //System.out.println(Json4s.compactJson(dep_trans))
    
    var signers : Array[Signer] = new Array[Signer](6)
    signers(0) = Signer("node1","121000005l35120456","18912345678",List("node1")) 
    signers(1) = Signer("node2","12110107bi45jh675g","18912345678",List("node2")) 
    signers(2) = Signer("node3","122000002n00123567","18912345678",List("node3")) 
    signers(3) = Signer("node4","921000005k36123789","18912345678",List("node4")) 
    signers(4) = Signer("node5","921000006e0012v696","18912345678",List("node5")) 
    signers(5) = Signer("super_admin","951002007l78123233","18912345678",List("super_admin")) 
    
    
    
    for(i<-0 to 5){
        translist(i+1) = ctx.getTransactionBuilder.createTransaction4Invoke("951002007l78123233.super_admin", cid,
                    "SignUpSigner", Seq(JsonFormat.toJsonString(signers(i))))
    }
    
    
    for(i<-0 to 5){
      val certfile = scala.io.Source.fromFile(s"${ctx.getCryptoMgr.getKeyFileSuffix.substring(1)}/"+signers(i).creditCode+"."+signers(i).name+".cer","UTF-8")
      val certstr = try certfile.mkString finally certfile.close()
     // val cert = SignTool.getCertByFile(s"${CryptoMgr.getKeyFileSuffix.substring(1)}/"+signers(i).creditCode+"."+signers(i).name+".cer")
      val millis = System.currentTimeMillis()
      
      //val tmp = Certificate(certstr,"SHA256withECDSA",true,Option(Timestamp(millis/1000 , ((millis % 1000) * 1000000).toInt)), id = Option(CertId(signers(i).creditCode, signers(i).name)))
      val tmp = Certificate(certstr,"SHA256withECDSA",true,Option(Timestamp(millis/1000 , ((millis % 1000) * 1000000).toInt)), id = Option(CertId(signers(i).creditCode, signers(i).name)))
       //val aa = new ContractCert
      translist(i+7) = ctx.getTransactionBuilder.createTransaction4Invoke("951002007l78123233.super_admin", cid,
                    "SignUpCert", Seq(JsonFormat.toJsonString(tmp)))
    }
    
    
     val s2 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/ContractAssetsTPL.scala","UTF-8")
    val c2 = try s2.mkString finally s2.close()
    val cid2 = new ChaincodeId("ContractAssetsTPL",1)
    val dep_asserts_trans = ctx.getTransactionBuilder.createTransaction4Deploy(sysName, cid2,
               c2, "",5000, ChaincodeDeploy.CodeType.CODE_SCALA)
    translist(13) = dep_asserts_trans
    
    // read invoke scala contract
    val s3 = scala.io.Source.fromFile("api_req/json/set.json","UTF-8")
    val ct1 = try s3.mkString finally s3.close()
    
    translist(14) = ctx.getTransactionBuilder.createTransaction4Invoke("951002007l78123233.super_admin", cid2,
                    "set", Seq(ct1))
    
    
    
    //create gensis block
    val millis = ConfigFactory.load().getLong("akka.genesisblock.creationBlockTime")
     
    var blk = BlockHelp.buildBlock("",1,translist)
      //new Block(1,1,translist,Seq(),_root_.com.google.protobuf.ByteString.EMPTY,
      //  _root_.com.google.protobuf.ByteString.EMPTY)
     
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
        blk = blk.withHeader(blk.getHeader.clearEndorsements)
        blk = blk.clearTransactionResults
    val r = MessageToJson.toJson(blk)
    val rstr = pretty(render(r))
    println(rstr)

    val pw = new PrintWriter(s"json/${ctx.getConfig.getChainNetworkId}/genesis.json","UTF-8")
    pw.write(rstr)
    pw.flush()
    pw.close()
  }
}