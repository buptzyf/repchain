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

import java.io.{File, FileFilter, PrintWriter}
import java.nio.file.Path
import java.util

import com.google.common.io.Files
import com.google.protobuf.timestamp.Timestamp
import org.json4s.jackson.JsonMethods.{pretty, render}
import org.json4s.{DefaultFormats, jackson}
import rep.crypto.cert.SignTool
import rep.network.autotransaction.PeerHelper
import rep.protos.peer._
import scalapb.json4s.JsonFormat

import scala.collection.mutable


/**
  * 将整个jks下所有node的账户都注册上去（遍历node的jks），以及注册相应的证书
  *
  * @author zyf
  */
object GenesisBuilderTool {

  implicit val serialization = jackson.Serialization // or native.Serialization
  implicit val formats = DefaultFormats
  private val setMap = new mutable.HashMap[String, Int]()
  private var jksFile = new File("jks")
  private var certsFile = new File("jks/certs")
  private var contractFile = new File("src/main/scala/rep/sc/tpl/ContractCert.scala")
  private var adminJksName = ""
  private var node1JksName = ""

  def main(args: Array[String]): Unit = {

    println("命令格式为：java -Dgenesis=./genesis.json -cp RepChain.jar rep.utils.GenesisBuilderTool ./jks ./jks/certs ./tpl/ContractCertTPL.scala ./tpl/CustomTPL.scala tpl/CustomTPL1.scala")
    println("命令格式为：java '可选的设置genesis.json写入文件' -cp RepChain.jar rep.utils.GenesisBuilderTool 节点jks目录 节点证书目录 ‘ContractCert.scala’文件所在 可选的业务合约...")

    // 简单的校验
    if (args.length >= 3) {
      jksFile = new File(args(0))
      certsFile = new File(args(1))
      contractFile = new File(args(2))
      if (!jksFile.isDirectory || !certsFile.isDirectory) {
        throw new RuntimeException("节点jks或者certs参数输入为非目录")
      }
    } else if (args.length >= 1 && args.length <= 2) {
      throw new RuntimeException("参数个数应该为0或者3个及以上")
    }

    val signers = fillSigners()
    val certs = fillCerts(signers)

    println(Path.of(jksFile.getPath, adminJksName).toString)

    // 导入管理员的私钥，进行签名操作
    SignTool.loadPrivateKey(adminJksName.substring(0, adminJksName.length - 4), "super_admin", Path.of(jksFile.getPath, adminJksName).toString)
    // 导入node1的私钥
    SignTool.loadPrivateKey(node1JksName.substring(0, node1JksName.length - 4), "123", Path.of(jksFile.getPath, node1JksName).toString)

    val transList = new util.ArrayList[Transaction]

    //交易发起人是超级管理员，注册系统必须的账户管理合约
    val s1 = scala.io.Source.fromFile(contractFile, "UTF-8")
    val l1 = try s1.mkString finally s1.close()
    val cid1 = new ChaincodeId("ContractCert", 1)
    val dep_trans = PeerHelper.createTransaction4Deploy(adminJksName.substring(0, adminJksName.length - 4), cid1, l1, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA)
    transList.add(dep_trans)

    val adminInfo = adminJksName.split("\\.")
    // 注册节点与管理员的账户
    for (i <- signers.indices) {
      transList.add(PeerHelper.createTransaction4Invoke(adminJksName.substring(0, adminJksName.length - 4), cid1, "SignUpSigner", Seq(JsonFormat.toJsonString(signers(i)))))
    }
    // 注册节点与管理员的证书
    for (i <- certs.indices) {
      transList.add(PeerHelper.createTransaction4Invoke(adminJksName.substring(0, adminJksName.length - 4), cid1, "SignUpCert", Seq(JsonFormat.toJsonString(certs(i)))))
    }

    // 可选的部署业务合约
    for (i <- 3 until args.length) {
      val tplFile = new File(args(i))
      val s2 = scala.io.Source.fromFile(tplFile, "UTF-8")
      val l2 = try s2.mkString finally s2.close()
      val tplFileName = tplFile.getName.split("\\.")(0)
      val cid4 = new ChaincodeId(tplFileName, 1)
      val dep_custom_proof = PeerHelper.createTransaction4Deploy(node1JksName.substring(0, node1JksName.length - 4), cid4, l2, "", 5000, rep.protos.peer.ChaincodeDeploy.CodeType.CODE_SCALA)
      transList.add(dep_custom_proof)
    }

    // 构建Block
    var blk = new Block(1, 1, transList.toArray(new Array[Transaction](transList.size())), Seq(), _root_.com.google.protobuf.ByteString.EMPTY,
      _root_.com.google.protobuf.ByteString.EMPTY)

    blk = blk.clearEndorsements
    blk = blk.clearTransactionResults
    val r = MessageToJson.toJson(blk)
    val rStr = pretty(render(r))
    println(rStr)

    // 从系统级属性中获取要存放的genesis.json路径，并写入
    val genesisPath = System.getProperty("genesis") match {
      case null =>
        "./genesis.json"
      case _ => System.getProperty("genesis")
    }

    val pw = new PrintWriter({
      val genesisFile = new File(genesisPath)
      if (!genesisFile.exists) {
        Files.createParentDirs(genesisFile)
      }
      genesisFile
    }, "UTF-8")
    pw.write(rStr)
    pw.flush()
    pw.close()
  }

  /**
    * 将jks目录下的所有节点账户账户数组中
    *
    * @return
    */
  def fillSigners(): Array[Signer] = {
    // 过滤掉非节点node的jks
    val files = jksFile.listFiles(new FileFilter {
      override def accept(file: File): Boolean = {
        val fileName = file.getName
        !file.isDirectory && fileName.endsWith("jks") && (fileName.contains("node") || fileName.contains("super_admin"))
      }
    })

    val signers: Array[Signer] = new Array[Signer](files.length)
    for (i <- 0 until signers.length) {
      val fileName = files(i).getName
      if (fileName.contains("super_admin")) {
        adminJksName = fileName
      } else if (fileName.contains("node1")) {
        node1JksName = fileName
      }
      val fileNameSplit = fileName.split('.')
      signers(i) = Signer(fileNameSplit(1), fileNameSplit(0), "", List(fileNameSplit(1)))
    }
    signers
  }

  def fillCerts(signers: Array[Signer]): Array[Certificate] = {
    val certInfos: Array[Certificate] = new Array[Certificate](signers.length)
    // 过滤掉非节点node的cer
    val files = certsFile.listFiles((pathname: File) => {
      def foo(pathname: File) = {
        val fileName = pathname.getName
        !pathname.isDirectory && fileName.endsWith("cer") && (fileName.contains("node") || fileName.contains("super_admin"))
      }

      foo(pathname)
    })
    for (i <- 0 until certInfos.length) {
      val certfile = scala.io.Source.fromFile(Path.of(certsFile.getPath, signers(i).creditCode + "." + signers(i).name + ".cer").toFile, "UTF-8")
      val certstr = try certfile.mkString finally certfile.close()
      val millis = System.currentTimeMillis()
      val cert = Certificate(certstr, "SHA1withECDSA", true, Option(Timestamp(millis / 1000, ((millis % 1000) * 1000000).toInt)), id = Option(CertId(signers(i).creditCode, signers(i).name)))
      certInfos(i) = cert
    }
    certInfos
  }

}
