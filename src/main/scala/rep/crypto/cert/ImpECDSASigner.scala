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

import java.security._
import java.io._
import java.security.cert.{ Certificate, CertificateFactory }
import rep.app.conf.SystemProfile
import com.google.protobuf.ByteString
//import fastparse.utils.Base64
import rep.utils.SerializeUtils
import rep.storage._
import scala.collection.mutable
//import com.fasterxml.jackson.core.Base64Variants
import java.security.cert.X509Certificate
//import javax.xml.bind.DatatypeConverter
import java.util.ArrayList
import java.util.List
import sun.security.ec.ECPublicKeyImpl

/**
 * 实现系统签名和验签功能，第三方使用不需要直接调用该类
 * @author jiangbuyun
 * @version	1.0
 */
class ImpECDSASigner extends ISigner {
  private val alg = "SHA1withECDSA"

  override def sign(privateKey: PrivateKey, message: Array[Byte]): Array[Byte] = {
    if(privateKey == null) throw new RuntimeException("签名时私钥为空！") 
    if(message == null || message.length <= 0 ) throw new RuntimeException("待签名内容为空！")
    val s1 = Signature.getInstance(alg);
    s1.initSign(privateKey)
    s1.update(message)
    s1.sign()
  }

  override def verify(signature: Array[Byte], message: Array[Byte], publicKey: PublicKey): Boolean = {
    if(publicKey == null) throw new RuntimeException("验证签名时公钥为空！") 
    if(signature == null || signature.length <= 0) throw new RuntimeException("待验证的签名信息为空！") 
    if(message == null || message.length <= 0 ) throw new RuntimeException("待签名内容为空！")
    val s2 = Signature.getInstance(alg);
    s2.initVerify(publicKey)
    s2.update(message)
    s2.verify(signature)
  }

  override def CertificateIsValid(date:java.util.Date,  cert:Certificate):Boolean={
        var isValid :Boolean = false
        var  start = System.currentTimeMillis()
        try {
          if(cert == null){
            isValid = false
          }else{     
              if(SystemProfile.getCheckCertValidate == 1){
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

