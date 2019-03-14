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
package rep.crypto.cert

import java.security.{ PrivateKey, PublicKey, KeyStore }
import java.security.cert.{ Certificate, CertificateFactory }
import rep.protos.peer.CertId
import scala.collection.mutable
import java.io._
import java.util.{ ArrayList, List }
import rep.app.conf.SystemProfile
import scala.util.control.Breaks._

/**
 * 负责签名和验签的工具了，所有相关的功能都调用该类
 * @author jiangbuyun
 * @version	1.0
 */
object SignTool {
  private var signer: ISigner = null
  private var SignType: String = "ECDSA"
  private var key_password = mutable.HashMap[String, String]()
  private var keyStores = mutable.HashMap[String, KeyStore]()
  private var PublickeyCerts = mutable.HashMap[String, Certificate]()
  private var thirdPublicKeyCerts = mutable.HashMap[String, Certificate]()

  synchronized {
    if (this.signer == null) {
      signer = new ImpECDSASigner()
    }
  }

  private def getNodePrivateKey(alias: String): PrivateKey = {
    val sk = keyStores(alias).getKey(alias, key_password(alias).toCharArray())
    sk.asInstanceOf[PrivateKey]
  }

  //根据CertId实现签名
  def sign4Node(nodeName: String, message: Array[Byte]): Array[Byte] = {
    var pk: PrivateKey = null
    if (this.keyStores.contains(nodeName)) {
      pk = getNodePrivateKey(nodeName)
    }
    this.signer.sign(pk, message)
  }

  //根据私钥实现签名
  private def sign(privateKey: PrivateKey, message: Array[Byte]): Array[Byte] = {
    this.signer.sign(privateKey, message)
  }

  private def getNodePublicKeyByName(alias: String): Certificate = {
    PublickeyCerts.get(alias).get
  }

  private def getThirdPublicKeyByName(certinfo: CertId): Certificate = {
    //todo
    null
  }

  private def getVerifyCert(certinfo: CertId): PublicKey = {
    var pkcert: Certificate = null
    if (certinfo.certName.equalsIgnoreCase("")) {
      pkcert = getNodePublicKeyByName(certinfo.creditCode)
    }

    if (pkcert == null) {
      pkcert = getThirdPublicKeyByName(certinfo)
    }

    if (pkcert == null) {
      throw new RuntimeException("验证签名时证书为空！")
    }

    if (!this.signer.CertificateIsValid(new java.util.Date(), pkcert)) {
      throw new RuntimeException("验证签名时证书已经过期！")
    }
    pkcert.getPublicKey
  }

  //根据CertId实现验签
  def verify(signature: Array[Byte], message: Array[Byte], certinfo: CertId): Boolean = {
    var pk = getVerifyCert(certinfo)
    this.signer.verify(signature, message, pk)
  }

  //根据公钥实现签名
  def verify(signature: Array[Byte], message: Array[Byte], publicKey: PublicKey): Boolean = {
    this.signer.verify(signature, message, publicKey)
  }

  //节点启动时需要调用该函数初始化节点私钥
  def loadNodePrivateKey(nodeName: String, password: String, path: String) = {
    synchronized {
      key_password(nodeName) = password
      val fis = new FileInputStream(new File(path))
      val pwd = password.toCharArray()
      if (keyStores.contains(nodeName)) {
        keyStores(nodeName).load(fis, pwd)
      } else {
        val pkeys = KeyStore.getInstance(KeyStore.getDefaultType)
        pkeys.load(fis, pwd)
        keyStores(nodeName) = pkeys
      }
    }
  }

  //节点启动时需要调用该函数初始化节点公钥
  def loadNodePublicKey(password: String, path: String) = {
    synchronized {
      if (this.PublickeyCerts.isEmpty) {
        val fis = new FileInputStream(new File(path))
        val pwd = password.toCharArray()
        var trustKeyStore = KeyStore.getInstance(KeyStore.getDefaultType)
        trustKeyStore.load(fis, pwd)
        val enums = trustKeyStore.aliases()
        while (enums.hasMoreElements) {
          val alias = enums.nextElement()
          val cert = trustKeyStore.getCertificate(alias)
          PublickeyCerts.put(alias, cert)
        }
      }
    }
  }

  //提供给共识获取证书列表
  def getAliasOfTrustkey: List[String] = {
    var list: List[String] = new ArrayList[String]
    val enums = PublickeyCerts.iterator
    while (enums.hasNext) {
      val alias = enums.next()
      list.add(alias._1)
    }
    list
  }

  //判断某个名称是否是共识节点
  def isNode4Credit(credit: String): Boolean = {
    var r: Boolean = false
    val n = credit + "."
    breakable(
      this.PublickeyCerts.foreach(f => {
        val name = f._1
        if (name.indexOf(n) == 0) {
          r = true
          break
        }
      }))
    r
  }
}
