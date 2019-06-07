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

import java.io._
import fastparse.utils.Base64
import java.util.concurrent.locks._
import rep.protos.peer.Certificate
import rep.storage._
import rep.utils.SerializeUtils
import rep.app.conf.SystemProfile
import rep.storage.IdxPrefix.WorldStateKeyPreFix
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._
import rep.log.RepLogger

object certCache {
  private implicit var caches = new ConcurrentHashMap[String, (Boolean, java.security.cert.Certificate)] asScala
  
  
  def getCertByPem(pemcert: String): java.security.cert.Certificate = {
    val cf = java.security.cert.CertificateFactory.getInstance("X.509")
    val cert = cf.generateCertificate(
      new ByteArrayInputStream(
        Base64.Decoder(pemcert.replaceAll("\r\n", "").stripPrefix("-----BEGIN CERTIFICATE-----").stripSuffix("-----END CERTIFICATE-----")).toByteArray))
    cert
  }

  def getCertForUser(certKey: String, sysTag: String): java.security.cert.Certificate = {
    var rcert: java.security.cert.Certificate = null
    try {
      if (caches.contains(certKey)) {
        val ck = caches(certKey)
        if(ck._1){
          //证书有效时返回
          rcert = caches(certKey)._2
        }
      } else {
        val sr: ImpDataAccess = ImpDataAccess.GetDataAccess(sysTag)
        val accountChaincodeName = SystemProfile.getAccountChaincodeName
        val cert = Option(sr.Get(IdxPrefix.WorldStateKeyPreFix + accountChaincodeName + "_" + certKey))
        if (cert != None && !(new String(cert.get)).equalsIgnoreCase("null")) {
          val kvcert = SerializeUtils.deserialise(cert.get).asInstanceOf[Certificate]
          if (kvcert != null ) {
            if(kvcert.certValid){
              //从worldstate中获取证书，如果证书以及证书是有效时，返回证书信息
              rcert = getCertByPem(kvcert.certificate)
            }
            caches += certKey -> (kvcert.certValid, rcert)
          }
        }
      }
    } catch {
      case e:Exception => RepLogger.trace(RepLogger.System_Logger, s"${certKey}, getCertForUser execept,msg=${e.getMessage}")
    }
    rcert
  }
  
  
  def CertStatusUpdate(ck:String)={
    if(ck != null){
      val pos = ck.lastIndexOf("_")
      if(pos > 0){
        val ckey = ck.substring(ck.lastIndexOf("_")+1)
        if(this.caches.contains(ckey)){
          this.caches -= ckey
        }
      }
    }
  }
  
}
