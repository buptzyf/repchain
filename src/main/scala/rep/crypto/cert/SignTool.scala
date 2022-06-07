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
package rep.crypto.cert

import java.security.{KeyStore, PrivateKey, PublicKey}
import java.security.cert.Certificate

import scala.collection.mutable
import java.io._
import java.util.{ArrayList, List}

import rep.app.system.RepChainSystemContext
import rep.authority.cache.CertificateCache
import rep.log.RepLogger
import rep.proto.rc2.CertId
import rep.sc.tpl.did.DidTplPrefix
import rep.utils.IdTool

import scala.util.control.Breaks._

/**
 * 负责签名和验签的工具了，所有相关的功能都调用该类
 * @author jiangbuyun
 * @version	1.0
 */
class SignTool(ctx:RepChainSystemContext) {
  private val signer:ISigner = ctx.getSigner
  private val keyPassword = mutable.HashMap[String, String]()
  private val keyStores = mutable.HashMap[String, KeyStore]()
  private val PublicKeyCerts = mutable.HashMap[String, Certificate]()
  private val TrustNodeList: List[String] = new ArrayList[String]
  private var isAddPublicKey = false

  private def getPrivateKey(pkeyname: String): PrivateKey = {
    val sk = keyStores(pkeyname).getKey(pkeyname, keyPassword(pkeyname).toCharArray())
    sk.asInstanceOf[PrivateKey]
  }

  def sign(pkeyname: String, message: Array[Byte]): Array[Byte] = {
    var pk: PrivateKey = null
    if (this.keyStores.contains(pkeyname)) {
      pk = getPrivateKey(pkeyname)
    }
    this.signer.sign(pk,message)
  }

  //根据CertId实现签名
  def sign4CertId(certinfo: CertId, message: Array[Byte]): Array[Byte] = {
    val pkeyname = certinfo.creditCode + "." + certinfo.certName
    sign(pkeyname, message)
  }

  //根据私钥实现签名
  private def sign4PrivateKey(privateKey: PrivateKey, message: Array[Byte]): Array[Byte] = {
    this.signer.sign(privateKey, message)
  }

  private def getVerifyCert(pubkeyname: String): PublicKey = {
    var pkcert: Certificate = null

    if (PublicKeyCerts.contains(pubkeyname)) {
      pkcert = PublicKeyCerts.get(pubkeyname).get
    } else {
      val cache = ctx.getPermissionCacheManager.getCache(DidTplPrefix.certPrefix).asInstanceOf[CertificateCache]
      val cert = cache.get(pubkeyname,null)
      if(cert != None){
        if(cert.get.cert_valid){
          pkcert = cert.get.certificate
        }else{
          throw new RuntimeException("验证签名时证书已经失效！")
        }
      }
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
    var r = false
    try{
      val k = certinfo.creditCode + "." + certinfo.certName
      val pk = getVerifyCert(k)
      r = this.signer.verify(signature, message, pk)
    }catch {
      case e:Exception=>{
        RepLogger.trace(RepLogger.System_Logger,s"验签异常失败，certinfo=${IdTool.getSigner4String(certinfo)}")
      }
    }
    if(!r){
      RepLogger.trace(RepLogger.System_Logger,s"验签失败，certinfo=${IdTool.getSigner4String(certinfo)}")
    }
    r
  }

  //根据公钥实现签名
  def verify(signature: Array[Byte], message: Array[Byte], publicKey: PublicKey): Boolean = {
    this.signer.verify(signature, message, publicKey)
  }

  //节点启动时需要调用该函数初始化节点私钥
  def loadPrivateKey(pkeyname: String, password: String, path: String) = {
    synchronized {
      keyPassword(pkeyname) = password
      val fis = new FileInputStream(new File(path))
      val pwd = password.toCharArray()
      if (keyStores.contains(pkeyname)) {
        keyStores(pkeyname).load(fis, pwd)
      } else {
        val pkeys = ctx.getCryptoMgr.getKeyStorer
        pkeys.load(fis, pwd)
        keyStores(pkeyname) = pkeys
      }
    }
  }

  def loadPrivateKey4CertId(certinfo: CertId, password: String, path: String) = {
    val pkeyname = certinfo.creditCode + "." + certinfo.certName
    loadPrivateKey(pkeyname, password, path)
  }

  //节点启动时需要调用该函数初始化节点公钥
  def loadNodeCertList(password: String, path: String) = {
    synchronized {
      if (!this.isAddPublicKey) {
        val fis = new FileInputStream(new File(path))
        val pwd = password.toCharArray()
        val trustKeyStore = ctx.getCryptoMgr.getKeyStorer
        trustKeyStore.load(fis, pwd)
        val enums = trustKeyStore.aliases()
        while (enums.hasMoreElements) {
          val alias = enums.nextElement()
          val cert = trustKeyStore.getCertificate(alias)
          this.TrustNodeList.add(alias)
          PublicKeyCerts.put(alias, cert)
        }
        this.isAddPublicKey = true
      }
    }
  }

  //提供给共识获取证书列表
  def getAliasOfTrustkey: List[String] = {
    this.TrustNodeList
  }

  //判断某个名称是否是共识节点
  def isNode4Credit(credit: String): Boolean = {
    var r: Boolean = false
    val n = credit + "."
    val size = this.TrustNodeList.size() - 1
    var i : Int = 0
    breakable(
      while (i <= size) {
        if (TrustNodeList.get(i).indexOf(n) == 0) {
          r = true
          break
        }
        i = i + 1
      })
    r
  }
}
