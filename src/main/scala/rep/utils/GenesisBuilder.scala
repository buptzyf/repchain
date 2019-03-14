/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
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

import java.io.File
import rep.network.PeerHelper
import com.typesafe.config.ConfigFactory
import com.google.protobuf.ByteString
import com.google.protobuf.timestamp.Timestamp
import rep.protos.peer._
import com.trueaccord.scalapb.json.JsonFormat
import rep.crypto.{ Sha256}
import org.json4s.{DefaultFormats, Formats, jackson}
import org.json4s.jackson.JsonMethods._
import org.json4s.DefaultFormats._
import rep.network.consensus.block.BlockHelper
import rep.crypto.cert.SignTool
import scala.collection.mutable

/**
 * 用于生成创世块json文件,该json文件可以在链初始化时由节点加载
 * 创世块中预置了deploy基础方法的交易
 *@author shidianyue
 */
object GenesisBuilder {

  implicit val serialization = jackson.Serialization // or native.Serialization
  implicit val formats       = DefaultFormats

  def main(args: Array[String]): Unit = {
    SignTool.loadPrivateKey("121000005L35120456.node1", "123", "jks/121000005L35120456.node1.jks")
    SignTool.loadNodeCertList("changeme", "jks/mytruststore.jks")
    SignTool.loadPrivateKey("951002007L78123233.super_admin", "super_admin", "jks/951002007L78123233.super_admin.jks")
    
   
     
    //交易发起人是超级管理员
    //增加scala的资产管理合约   
    // read deploy funcs
    val s1 = scala.io.Source.fromFile("src/main/scala/ContractAssetsTPL.scala")
    val l1 = try s1.mkString finally s1.close()
    //todo 运行时请定义名称
    val chaincodeId = new ChaincodeId("chaincodename_1",1)
    val t1 = PeerHelper.createTransaction4Deploy("super_admin", chaincodeId, l1, "", 
        100, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA)
    //val t1 = transactionCreator("super_admin", rep.protos.peer.Transaction.Type.CHAINCODE_DEPLOY,
    //  "", "", List(), l1, None, rep.protos.peer.ChaincodeSpec.CodeType.CODE_SCALA)

    // read invoke scala contract
    val s2 = scala.io.Source.fromFile("scripts/set.json")
    val l2 = try s2.mkString finally s2.close()
    
    val t2 = PeerHelper.createTransaction4Invoke("super_admin", chaincodeId, "set",Seq(l2))
    
    //val t2 = transactionCreator("super_admin",rep.protos.peer.Transaction.Type.CHAINCODE_INVOKE,
    //    "", "set" ,Seq(l2),"", Option(t1.payload.get.chaincodeID.get.name))  
    
    //create gensis block
    val millis = ConfigFactory.load().getLong("akka.genesisblock.creationBlockTime")
    var blk = new Block(1, 1,
      List(t1,t2),
      Seq(),
      _root_.com.google.protobuf.ByteString.EMPTY, _root_.com.google.protobuf.ByteString.EMPTY)
    //获得管理员证书和签名
//    val (priKA, pubKA, certA) = ECDSASign.getKeyPair("super_admin")
//    val (prik, pubK, cert) = ECDSASign.getKeyPair("1")
    val blk_hash = blk.toByteArray
    //签名之前不再使用hash
    //val blk_hash = Sha256.hash(blk.toByteArray)
    //超级管理员背书（角色）
    //创建者背书（1）
    blk = blk.withEndorsements(Seq(BlockHelper.endorseBlock4NonHash(blk_hash,"super_admin"),
      BlockHelper.endorseBlock4NonHash(blk_hash,"1")))
//    blk = blk.withConsensusMetadata(Seq(Endorsement(ByteString.copyFromUtf8(ECDSASign.getBitcoinAddrByCert(certA)),
//      ByteString.copyFrom(ECDSASign.sign(priKA, blk_hash))),
//      Endorsement(ByteString.copyFromUtf8(ECDSASign.getBitcoinAddrByCert(cert)),ByteString.copyFrom(ECDSASign.sign(prik,blk_hash)))))
    
    val r = JsonFormat.toJson(blk)   
    val rstr = pretty(render(r))
    println(rstr)
    val blk2 = JsonFormat.fromJsonString[Block](rstr)
    val t = blk2.transactions.head
//    println(t.cert.toStringUtf8)
    val t_ = blk2.endorsements.tail.head
//    println(t_.endorser.toStringUtf8)
  }
}