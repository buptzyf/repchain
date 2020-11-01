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

import java.io.{File, FileFilter, FileWriter, PrintWriter}
import java.util

import org.json4s.jackson.JsonMethods.{pretty, render}
import org.json4s.{DefaultFormats, jackson}
import rep.crypto.cert.SignTool
import rep.network.autotransaction.PeerHelper
import rep.protos.peer.{Block, ChaincodeId, Signer, Transaction}
import scalapb.json4s.JsonFormat

import scala.collection.mutable


/**
  * 将整个jks下所有node的账户都注册上去（遍历node的jks），并为账户赋初值，证书暂时没有注册
  *
  * @author zyf
  */
object GenesisBuilderMulti {

  implicit val serialization = jackson.Serialization // or native.Serialization
  implicit val formats = DefaultFormats
  private val setMap = new mutable.HashMap[String, Int]()

  def main(args: Array[String]): Unit = {

    SignTool.loadPrivateKey("121000005l35120456.node1", "123", "jks/121000005l35120456.node1.jks")
    SignTool.loadNodeCertList("changeme", "jks/mytruststore.jks")
    SignTool.loadPrivateKey("951002007l78123233.super_admin", "super_admin", "jks/951002007l78123233.super_admin.jks")

    val transList = new util.ArrayList[Transaction]

    //交易发起人是超级管理员
    //增加scala的资产管理合约
    val s1 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/ContractCert.scala","UTF-8")
    val l1 = try s1.mkString finally s1.close()
    val cid1 = new ChaincodeId("ContractCert", 1)
    val dep_trans = PeerHelper.createTransaction4Deploy("951002007l78123233.super_admin", cid1, l1, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA)

    transList.add(dep_trans)

    val signers = fillSigners()
    signers(0) = Signer("super_admin", "951002007l78123233", "18912345678", List("super_admin"))

    for (i <- signers.indices) {
      transList.add(PeerHelper.createTransaction4Invoke("951002007l78123233.super_admin", cid1, "SignUpSigner", Seq(SerializeUtils.compactJson(signers(i)))))
    }

    val sysName = "121000005l35120456.node1"
    val s2 = scala.io.Source.fromFile("src/main/scala/rep/sc/tpl/ContractAssetsTPL.scala","UTF-8")
    val l2 = try s2.mkString finally s2.close()
    val cid2 = new ChaincodeId("ContractAssetsTPL", 1)
    val dep_asserts_trans = PeerHelper.createTransaction4Deploy(sysName, cid2, l2, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA)

    transList.add(dep_asserts_trans)

    // read invoke scala contract
    //    val s3 = scala.io.Source.fromFile("api_req/json/set.json")
    //    val l3 = try s3.mkString finally s3.close()
    val l3 = SerializeUtils.compactJson(setMap)
    val dep_set_trans = PeerHelper.createTransaction4Invoke("951002007l78123233.super_admin", cid2, "set", Seq(l3))

    transList.add(dep_set_trans)

    var blk = new Block(1, 1, transList.toArray(new Array[Transaction](transList.size())), Seq(), _root_.com.google.protobuf.ByteString.EMPTY,
      _root_.com.google.protobuf.ByteString.EMPTY)

    blk = blk.clearEndorsements
    blk = blk.clearTransactionResults
    val r = JsonFormat.toJson(blk)
    val rStr = pretty(render(r))
    println(rStr)

    val pw = new PrintWriter("json/gensis.json","UTF-8")
    pw.write(rStr)
    pw.flush()
    pw.close()
  }

  /**
    * 将jks目录下的所有节点账户账户数组中
    *
    * @return
    */
  // TODO 排个序，只注册部分
  def fillSigners(): Array[Signer] = {
    val fileDir = new File("jks")
    // 过滤掉非节点node的jks
    val files = fileDir.listFiles(new FileFilter {
      override def accept(file: File): Boolean = {
        val fileName = file.getName
        fileName.endsWith("jks") && fileName.indexOf("node") != -1
      }
    })

    val signers: Array[Signer] = new Array[Signer](files.length + 1)
    for (i <- 1 until signers.length) {
      val fileNameSplit = files(i - 1).getName.split('.')
      signers(i) = Signer(fileNameSplit(1), fileNameSplit(0), "18912345678", List(fileNameSplit(1)))
      setMap.put(fileNameSplit(0), 10000000)
    }
    signers
  }

}
