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

/**
 * 负责签名和验签的工具了，所有相关的功能都调用该类
 * @author jiangbuyun
 * @version	1.0
 */
object SignTool {
  private var signer: ISigner = null
  private var SignType: String = "ECDSA"
  private var key_password = mutable.HashMap[String, String]()
  private var keyStore = mutable.HashMap[String, KeyStore]()
  private var PublickeyCerts = mutable.HashMap[String, Certificate]()

  synchronized {
    if (this.signer == null) {
      signer = new ImpECDSASigner()
    }
  }

  private def getPrivateKey(alias: String): PrivateKey = {
    val sk = keyStore(alias).getKey(alias, key_password(alias).toCharArray)
    sk.asInstanceOf[PrivateKey]
  }

  private def getPrivateKey(certinfo: CertId): PrivateKey = {
    //todo
    null
  }

  //根据CertId实现签名
  def sign(certinfo: CertId, message: Array[Byte]): Array[Byte] = {
    val key = certinfo.creditCode + "_" + certinfo.certName
    var pk: PrivateKey = null
    if (this.keyStore.contains(key)) {
      pk = getPrivateKey(key)
    } else {
      pk = getPrivateKey(certinfo)
    }
    this.signer.sign(pk, message)
  }

  //根据私钥实现签名
  def sign(privateKey: PrivateKey, message: Array[Byte]): Array[Byte] = {
    this.signer.sign(privateKey, message)
  }

  private def getPublicKeyByName(alias: String): PublicKey = {
    var tmpcert = PublickeyCerts.get(alias)
    if (tmpcert == null && tmpcert != None) {
      throw new RuntimeException("证书不存在")
    }
    if (this.signer.CertificateIsValid(new java.util.Date(), tmpcert.get)) {
      tmpcert.get.getPublicKey
    } else {
      null
    }
  }

  private def getPublicKeyByName(certinfo: CertId): PublicKey = {
    //todo
    null
  }

  //根据CertId实现验签
  def verify(signature: Array[Byte], message: Array[Byte], certinfo: CertId): Boolean = {
    val key = certinfo.creditCode + "_" + certinfo.certName
    var pubkey: PublicKey = null
    pubkey = getPublicKeyByName(key)
    if (pubkey == null) {
      pubkey = getPublicKeyByName(certinfo)
    }
    this.signer.verify(signature, message, pubkey)
  }

  //根据公钥实现签名
  def verify(signature: Array[Byte], message: Array[Byte], publicKey: PublicKey): Boolean = {
    this.signer.verify(signature, message, publicKey)
  }

  //节点启动时需要调用该函数初始化节点私钥
  def InitNodePrivateKey(alias: String, password: String, path: String) = {
    synchronized {
      key_password(alias + "_1") = password
      val fis = new FileInputStream(new File(path))
      val pwd = password.toCharArray()
      if (keyStore.contains(alias)) {
        keyStore(alias).load(fis, pwd)
      } else {
        val pkeys = KeyStore.getInstance(KeyStore.getDefaultType)
        pkeys.load(fis, pwd)
        keyStore(alias + "_1") = pkeys
      }
    }
  }

  //节点启动时需要调用该函数初始化节点公钥
  def InitNodePublicKey(password: String, path: String) = {
    synchronized {
      val fis = new FileInputStream(new File(path))
      val pwd = password.toCharArray()
      var trustKeyStore = KeyStore.getInstance(KeyStore.getDefaultType)
      trustKeyStore.load(fis, pwd)
      val enums = trustKeyStore.aliases()
      while (enums.hasMoreElements) {
        val alias = enums.nextElement()
        val cert = trustKeyStore.getCertificate(alias)
        PublickeyCerts.put(alias + "_1", cert)
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
}
