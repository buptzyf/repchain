/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Fintech Research Center of ISCAS.
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
 */

package rep.crypto

import java.security._
import java.io._
import java.security.cert.{ Certificate, CertificateFactory }
import rep.app.conf.SystemProfile
import com.google.protobuf.ByteString
import fastparse.utils.Base64
import rep.utils.SerializeUtils
import rep.storage._
import scala.collection.mutable
import com.fasterxml.jackson.core.Base64Variants
import java.security.cert.X509Certificate
import javax.xml.bind.DatatypeConverter

/**
 * 系统密钥相关伴生对象
 * @author shidianyue
 * @version	0.7
 */
object ECDSASign extends ECDSASign {
  //TODO kami （现阶段alias和SYSName相同，将来不一定，所以目前在接口层将其分开，但是调用时用的是一个）

  //store itsself key and certification
  var keyStorePath = mutable.HashMap[String, String]()
  var password = mutable.HashMap[String, String]()
  //store the trust list of other nodes' certification
  var trustKeyStorePath = ""
  var passwordTrust = ""

  var keyStore = mutable.HashMap[String, KeyStore]()
  var trustKeyStore = KeyStore.getInstance(KeyStore.getDefaultType)
  var trustkeysPubAddrMap = mutable.HashMap[String, Certificate]()

  def apply(alias: String, jksPath: String, password: String, jksTrustPath: String, passwordTrust: String) = {
    keyStorePath(alias) = jksPath
    this.password(alias) = password
    //TODO kami 如果与之前路径不同，如何处理？
    if (trustKeyStorePath == "") {
      trustKeyStorePath = jksTrustPath
      this.passwordTrust = passwordTrust
    }
  }

  /**
   * 通过参数获取相关的密钥对、证书（动态加载）
   *
   * @param jks_file
   * @param password
   * @param alias
   * @return
   */
  def getKeyPairFromJKS(jks_file: File, password: String, alias: String): (PrivateKey, PublicKey) = {
    val store = KeyStore.getInstance(KeyStore.getDefaultType)
    val fis = new FileInputStream(jks_file)
    val pwd = password.toCharArray()
    store.load(fis, pwd)
    val sk = store.getKey(alias, pwd)
    val cert = store.getCertificate(alias)
    (sk.asInstanceOf[PrivateKey], cert.getPublicKey())
  }

  /**
   * 在信任列表中获取证书（通过alias）
   *
   * @param cert
   * @return
   */
  def getAliasByCert(cert: Certificate): Option[String] = {
    val alias = trustKeyStore.getCertificateAlias(cert)
    if (alias == null) Option.empty else Option(alias)
  }

  /**
   * 获取证书的Base58地址
   * @param cert
   * @return
   */
  def getAddrByCert(cert: Certificate): String = {
    Base58.encode(Sha256.hash(cert.getPublicKey.getEncoded))
  }

  /**
   * 获取证书的短地址（Bitcoin方法）
   * @param cert 对象
   * @return
   */
  def getBitcoinAddrByCert(cert: Certificate): String = {
    BitcoinUtils.calculateBitcoinAddress(cert.getPublicKey.getEncoded)
  }

  /**
   * 获取证书的短地址
   * @param certByte 字节
   * @return
   */
  def getBitcoinAddrByCert(certByte: Array[Byte]): String = {
    val cert = SerializeUtils.deserialise(certByte).asInstanceOf[Certificate]
    BitcoinUtils.calculateBitcoinAddress(cert.getPublicKey.getEncoded)
  }

  /**
   * 获取指定alias的证书地址
   * @param alias
   * @return
   */
  def getAddr(alias: String): String = {
    getAddrByCert(keyStore(alias).getCertificate(alias))
  }

  /**
   * 根据短地址获取证书
   * @param addr
   * @return
   */
  def getCertByBitcoinAddr(addr: String): Option[Certificate] = {
    var tmpcert = trustkeysPubAddrMap.get(addr)
    if(tmpcert ==  null && tmpcert != None) {
      throw new RuntimeException("证书不存在")
    }
    if(checkCertificate(new java.util.Date(), tmpcert.get )){
      tmpcert
    }else{
      throw new RuntimeException("证书已经过期")
    }
  }

  /**
   * 通过配置信息获取证书（动态加载）
   *
   * @param jks_file
   * @param password
   * @param alias
   * @return
   */
  def getCertFromJKS(jks_file: File, password: String, alias: String): Certificate = {
    val store = KeyStore.getInstance(KeyStore.getDefaultType)
    val fis = new FileInputStream(jks_file)
    val pwd = password.toCharArray()
    store.load(fis, pwd)
    val sk = store.getKey(alias, pwd)
    val cert = store.getCertificate(alias)
    cert
  }

  /**
   * 将pem格式证书字符串转换为certificate
   * @param pem
   * @return
   */
  def getCertByPem(pemcert: String): Certificate = {
    val cf = CertificateFactory.getInstance("X.509")
    val cert = cf.generateCertificate(
      new ByteArrayInputStream(
        Base64.Decoder(pemcert.replaceAll("\r\n", "").stripPrefix("-----BEGIN CERTIFICATE-----").stripSuffix("-----END CERTIFICATE-----")).toByteArray))
    cert
  }

  
  /**
   * 获取alias的密钥对和证书（系统初始化）
   *
   * @param alias
   * @return
   */
  def getKeyPair(alias: String): (PrivateKey, PublicKey, Array[Byte]) = {
    val sk = keyStore(alias).getKey(alias, password(alias).toCharArray)
    val cert = keyStore(alias).getCertificate(alias)
    if(checkCertificate(new java.util.Date(),  cert)){
      (sk.asInstanceOf[PrivateKey], cert.getPublicKey(), SerializeUtils.serialise(cert))
    }else{
      throw new RuntimeException("证书已经过期")
    }
  }

  /**
   * 获取alias的证书（系统初始化）
   *
   * @param alias
   * @return
   */
  def getCert(alias: String): Certificate = {
    keyStore(alias).getCertificate(alias)
  }

  /**
   * 在信任列表中获取alias的证书（系统初始化）
   *
   * @param alias
   * @return
   */
  def getKeyPairTrust(alias: String): PublicKey = {
    val sk = trustKeyStore.getKey(alias, passwordTrust.toCharArray)
    val cert = trustKeyStore.getCertificate(alias)
    cert.getPublicKey()
  }

  /**
   * 判断两个证书是否相同
   *
   * @param alias
   * @param cert
   * @return
   */
  def isCertTrust(alias: String, cert: Array[Byte]): Boolean = {
    val sk = trustKeyStore.getKey(alias, passwordTrust.toCharArray)
    val certT = trustKeyStore.getCertificate(alias)
    //寻找方法能够恢复cert？
    certT.getEncoded.equals(cert)
  }

  /**
   * 预加载系统密钥对和信任证书
   *
   * @param alias
   */
  def preLoadKey(alias: String): Unit = {
    val fis = new FileInputStream(new File(keyStorePath(alias)))
    val pwd = password(alias).toCharArray()
    if (keyStore.contains(alias)) keyStore(alias).load(fis, pwd)
    else {
      val keyS = KeyStore.getInstance(KeyStore.getDefaultType)
      keyS.load(fis, pwd)
      keyStore(alias) = keyS
    }

    val fisT = new FileInputStream(new File(trustKeyStorePath))
    val pwdT = passwordTrust.toCharArray()
    trustKeyStore.load(fisT, pwdT)
    loadTrustkeysPubAddrMap()
  }

  /**
   * 初始化信任证书中对短地址和证书的映射
   */
  def loadTrustkeysPubAddrMap(): Unit = {
    val enums = trustKeyStore.aliases()
    while (enums.hasMoreElements) {
      val alias = enums.nextElement()
      val cert = trustKeyStore.getCertificate(alias)
      trustkeysPubAddrMap.put(getBitcoinAddrByCert(cert), cert)
    }
  }

  /**
   * 获取本地证书，得到证书类和其序列化的字节序列
   *
   * @param certPath
   * @return 字节序列（通过base58进行转化）
   */
  def loadCertByPath(certPath: String): (Certificate, Array[Byte], String) = {
    val certF = CertificateFactory.getInstance("X.509")
    val fileInputStream = new FileInputStream(certPath)
    val x509Cert = certF.generateCertificate(fileInputStream)
    val arrayCert = SerializeUtils.serialise(x509Cert)
    (x509Cert, arrayCert, Base64.Encoder(arrayCert).toBase64)
  }

  /**
   * 添加证书到信任列表
   *
   * @param cert 字节数组
   * @param alias
   * @return
   */
  def loadTrustedCert(cert: Array[Byte], alias: String): Boolean = {
    val certTx = SerializeUtils.deserialise(cert).asInstanceOf[Certificate]
    getAliasByCert(certTx).getOrElse(None) match {
      case None =>
        trustKeyStore.setCertificateEntry(alias, certTx)
        trustkeysPubAddrMap.put(getBitcoinAddrByCert(certTx), certTx)
        val fileOutputStream = new FileOutputStream(trustKeyStorePath)
        trustKeyStore.store(fileOutputStream, passwordTrust.toCharArray)
        true
      case _ =>
        false
    }
  }

  /**
   * 添加证书到信任列表
   *
   * @param cert base64字符串
   * @param alias
   * @return
   */
  def loadTrustedCertBase64(cert: String, alias: String): Boolean = {
    val certTx = SerializeUtils.deserialise(Base64.Decoder(cert).toByteArray).asInstanceOf[Certificate]
    getAliasByCert(certTx).getOrElse(None) match {
      case None =>
        trustKeyStore.setCertificateEntry(alias, certTx)
        trustkeysPubAddrMap.put(getBitcoinAddrByCert(certTx), certTx)
        val fileOutputStream = new FileOutputStream(trustKeyStorePath)
        trustKeyStore.store(fileOutputStream, passwordTrust.toCharArray)
        true
      case _ =>
        false
    }
  }
  
  def getCertByNodeAddr(addr: String): Option[Certificate] = {
    if(addr != null){
      trustkeysPubAddrMap.get(addr)
    }else{
      None
    }
  }
  
  def main(args: Array[String]): Unit = {
    println(ByteString.copyFromUtf8(ECDSASign.getBitcoinAddrByCert(ECDSASign.getCertFromJKS(new File("jks/mykeystore_1.jks"), "123", "1"))).toStringUtf8)
    println(ECDSASign.getBitcoinAddrByCert(ECDSASign.getCertFromJKS(new File("jks/mykeystore_2.jks"), "123", "2")))
    println(ECDSASign.getBitcoinAddrByCert(ECDSASign.getCertFromJKS(new File("jks/mykeystore_3.jks"), "123", "3")))
    println(ECDSASign.getBitcoinAddrByCert(ECDSASign.getCertFromJKS(new File("jks/mykeystore_4.jks"), "123", "4")))
    println(ECDSASign.getBitcoinAddrByCert(ECDSASign.getCertFromJKS(new File("jks/mykeystore_5.jks"), "123", "5")))
    
    val (skey1,pkey1) = ECDSASign.getKeyPairFromJKS(new File("jks/mykeystore_1.jks"),"123","1")
    val plainText = "hello".getBytes
     val sig1 = ECDSASign.sign(skey1, plainText)
     
     val hexString: String = "304502207ed7f7bc4cafb928bf59e73595450364fc69417559a8a0fc19b7c8c9b6a620a5022100e81d0b619da4b92595603fb1316b56ebc94571ee7dec02d673ddf8d5365490e4"
     val bs = ByteString.copyFrom(DatatypeConverter.parseHexBinary(hexString) )
     val sig2 = bs.toByteArray
     val vr1 =  ECDSASign.verify(sig1, plainText, pkey1)
     val vr2 = ECDSASign.verify(sig2, plainText, pkey1)
     println(s"vr1: $vr1  vr2: $vr2")
  }

}

/**
 * 系统密钥相关类
 * @author shidianyue
 * @version	0.7
 * @since	1.0
 */
class ECDSASign extends SignFunc {
  /**
   * 签名
   *
   * @param privateKey
   * @param message
   * @return
   */
  def sign(privateKey: PrivateKey, message: Array[Byte]): Array[Byte] = {
    val s1 = Signature.getInstance("SHA1withECDSA");
    s1.initSign(privateKey)
    s1.update(message)
    s1.sign()
  }

  /**
   * 验证
   *
   * @param signature
   * @param message
   * @param publicKey
   * @return
   */
  def verify(signature: Array[Byte], message: Array[Byte], publicKey: PublicKey): Boolean = {
    val s2 = Signature.getInstance("SHA1withECDSA");
    s2.initVerify(publicKey)
    s2.update(message)
    s2.verify(signature)
  }

  
  
  def  getCertWithCheck(certAddr:String,certKey:String,sysTag:String):Option[java.security.cert.Certificate]={
    val cert = ECDSASign.getCertByNodeAddr(certAddr) 
    if(cert != None) {
      if(checkCertificate(new java.util.Date(),  cert.get)){
        cert
      }else{
        throw new RuntimeException("证书已经过期")
      }
    }else{
      if(certKey == null || sysTag == null){
        throw new RuntimeException("没有证书")
      }else{
          try{
              val sr: ImpDataAccess = ImpDataAccess.GetDataAccess(sysTag)
              val cert = Option(sr.Get(certKey))
              if (cert != None){
                if (new String(cert.get) == "null") {
                    throw new RuntimeException("用户证书已经注销")
                  }else{ 
                      val kvcert = SerializeUtils.deserialise(cert.get).asInstanceOf[Certificate]
                      if(kvcert != null){
                          if(checkCertificate(new java.util.Date(), kvcert)){
                            Some(kvcert)
                          }else{
                            throw new RuntimeException("证书已经过期")
                          }
                      }else{
                        throw new RuntimeException("证书内容错误")
                      }
                  }
              }else{
                throw new RuntimeException("没有证书")
              }
          }catch{
            case e : Exception =>throw new RuntimeException(e.getMessage)
          }
        }
    }
  }
  
  def checkCertificate(date:java.util.Date,  cert:Certificate):Boolean={
        var isValid :Boolean = false
        var  start = System.currentTimeMillis()
        try {
          if(cert == null){
            isValid = false
          }else{     
              if(SystemProfile.getCheckCertValidate == 0){
                isValid = true
              }else if(SystemProfile.getCheckCertValidate == 1){
                if(cert.isInstanceOf[X509Certificate]){
                    var  x509cert :X509Certificate = cert.asInstanceOf[X509Certificate]
                    x509cert.checkValidity(date)
                    isValid = true
                }
              }else{
                isValid = true
              }
          }
        } catch{
            case e : Exception => isValid = false
        }
        var end = System.currentTimeMillis()
        //println("check cert validate,spent time="+(end-start))
        isValid;
   }

}