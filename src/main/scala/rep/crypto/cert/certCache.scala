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
import org.bouncycastle.util.io.pem.PemReader
import rep.protos.peer.Certificate
import rep.storage._
import rep.utils.{IdTool, SerializeUtils}
import rep.app.conf.SystemProfile
import java.util.concurrent.ConcurrentHashMap
import rep.authority.cache.certcache.ImpCertCache
import rep.log.RepLogger
import scala.collection.JavaConverters._

object certCache {
  private  var caches = new ConcurrentHashMap[String, (Boolean, java.security.cert.Certificate)] asScala
  //private  var caches = new ConcurrentHashMap[String, (Boolean, java.security.PublicKey)] asScala


  def getCertByPem(pemcert: String): java.security.cert.Certificate = {
    val cf = java.security.cert.CertificateFactory.getInstance("X.509")
    val pemReader = new PemReader(new StringReader(pemcert))
    val certByte = pemReader.readPemObject().getContent
    val cert = cf.generateCertificate(new ByteArrayInputStream(certByte))
    cert
  }

  private def getCertForUser4Normal(certKey: String, sysTag: String): java.security.cert.Certificate={
    var rcert: java.security.cert.Certificate = null
    //var rcert: java.security.PublicKey = null
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


  def getCertForUser(certid: String, sysTag: String, pd:ImpDataPreload): java.security.cert.Certificate = {
    var rcert: java.security.cert.Certificate = null
    val certcache = ImpCertCache.GetCertCache(sysTag)
    val certdata = certcache.getCertificateData(certid,pd)
    if(certdata != null && certdata.cert_valid){
      rcert = certdata.certificate
    }
    rcert
  }

  private def getCertForUser4Did(certid: String, sysTag: String): java.security.cert.Certificate = {
    getCertForUser(certid, sysTag, null)
  }

  def getCertForUser(certid: String, sysTag: String): java.security.cert.Certificate = {
    if(IdTool.isDidContract){
      getCertForUser4Did(certid, sysTag)
    }else{
      getCertForUser4Normal(certid, sysTag)
    }
  }

  def getCertIdForHash(certHash:String,sysTag: String, pd:ImpDataPreload):String={
    var certid : String = null

    val certcache = ImpCertCache.GetCertCache(sysTag)
    certid = certcache.getCertId4CertHash(certHash,pd)

    certid
  }

  def getCertIdForHash(certHash:String,sysTag: String):String={
    getCertIdForHash(certHash,sysTag,null)
  }
}
