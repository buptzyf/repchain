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
import rep.sc.tpl.did.DidTplPrefix
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.collection.JavaConverters._

object certCache {
  private  var caches = new ConcurrentHashMap[String, (Boolean,String,String, java.security.cert.Certificate)] asScala
  private  var caches_hash = new ConcurrentHashMap[String, String] asScala
  private  var caches_future= new ConcurrentHashMap[String,Future[Option[(Boolean,String,String, java.security.cert.Certificate)]]] asScala
  private  var caches_hash_future= new ConcurrentHashMap[String,Future[Option[(Boolean,String,String, java.security.cert.Certificate)]]] asScala


  private def getCertByPem(pemcert: String): java.security.cert.Certificate = {
    val cf = java.security.cert.CertificateFactory.getInstance("X.509")
    val pemReader = new PemReader(new StringReader(pemcert))
    val certByte = pemReader.readPemObject().getContent
    val cert = cf.generateCertificate(new ByteArrayInputStream(certByte))
    cert
  }

  def getCertForUser(certid: String, sysTag: String): java.security.cert.Certificate = {
    var rcert: java.security.cert.Certificate = null
    //检查数据缓存是否包含了k，如果包含直接返回给调用者
    if(this.caches.contains(certid)){
      val tmp = this.caches.get(certid).getOrElse(null)
      if(tmp != null && tmp._1){
          rcert = tmp._4
      }
    }else{
      //数据缓存没有包含k，说明数据未加载到缓存，需要异步计算
      var tmpOperater : Future[Option[(Boolean,String,String, java.security.cert.Certificate)]] = this.caches_future.get(certid).getOrElse(null)
      if(tmpOperater == null){
        tmpOperater = asyncGetOperate(certid,sysTag,1)
        tmpOperater = this.caches_future.putIfAbsent(certid,tmpOperater).get
      }
      val result = Await.result(tmpOperater, 30.seconds).asInstanceOf[Option[(Boolean,String,String, java.security.cert.Certificate)]]
      //将结果放到数据缓存，结果有可能是None
      this.caches.put(certid,result.get)
      this.caches_hash.put(result.get._2,result.get._3)
      this.caches_future.remove(certid)
      if(result != None && result.get._1){
          rcert = result.get._4
      }
    }
    rcert
  }

  def getCertIdForHash(certhash: String, sysTag: String):String={
    var r: String = null
    //检查数据缓存是否包含了k，如果包含直接返回给调用者
    if(this.caches_hash.contains(certhash)){
      val certid = this.caches_hash.get(certhash).getOrElse(null)
      if(certid != null ){
        r = certid
      }
    }else{
      //数据缓存没有包含k，说明数据未加载到缓存，需要异步计算
      var tmpOperater : Future[Option[(Boolean,String,String, java.security.cert.Certificate)]] = this.caches_hash_future.get(certhash).getOrElse(null)
      if(tmpOperater == null){
        tmpOperater = asyncGetOperate(certhash,sysTag,2)
        tmpOperater = this.caches_hash_future.putIfAbsent(certhash,tmpOperater).get
      }
      val result = Await.result(tmpOperater, 30.seconds).asInstanceOf[Option[(Boolean,String,String, java.security.cert.Certificate)]]
      //将结果放到数据缓存，结果有可能是None
      this.caches.put(result.get._3,result.get)
      this.caches_hash.put(result.get._2,result.get._3)
      this.caches_hash_future.remove(certhash)
      if(result != None &&  result.get._1){
        r = result.get._3
      }
    }
    r
  }

  def getCertForHash(certhash: String, sysTag: String): java.security.cert.Certificate = {
    var rcert: java.security.cert.Certificate = null
    //检查数据缓存是否包含了k，如果包含直接返回给调用者
    if(this.caches_hash.contains(certhash)){
      val certid = this.caches_hash.get(certhash).getOrElse(null)
      if(certid != null ){
        rcert = getCertForUser(certid, sysTag)
      }
    }else{
      //数据缓存没有包含k，说明数据未加载到缓存，需要异步计算
      var tmpOperater : Future[Option[(Boolean,String,String, java.security.cert.Certificate)]] = this.caches_hash_future.get(certhash).getOrElse(null)
      if(tmpOperater == null){
        tmpOperater = asyncGetOperate(certhash,sysTag,2)
        tmpOperater = this.caches_hash_future.putIfAbsent(certhash,tmpOperater).get
      }
      val result = Await.result(tmpOperater, 30.seconds).asInstanceOf[Option[(Boolean,String,String, java.security.cert.Certificate)]]
      //将结果放到数据缓存，结果有可能是None
      this.caches.put(result.get._3,result.get)
      this.caches_hash.put(result.get._2,result.get._3)
      this.caches_hash_future.remove(certhash)
      if(result != None && result.get._1){
        rcert = result.get._4
      }
    }
    rcert
  }

  private def asyncGetOperate(kid:String,sysTag:String,mode:Int): Future[Option[(Boolean,String,String, java.security.cert.Certificate)]] = Future {
    var r : Option[(Boolean,String,String, java.security.cert.Certificate)] = None
    try{
      val sr: ImpDataAccess = ImpDataAccess.GetDataAccess(sysTag)
      val accountChaincodeName = SystemProfile.getAccountChaincodeName
      var bcert : Option[Array[Byte]] = null
      if(mode == 1) {
        bcert = Option(sr.Get(IdxPrefix.WorldStateKeyPreFix + accountChaincodeName + "_"+
                                DidTplPrefix.certPrefix + kid))
      }else if(mode == 2){
        bcert = Option(sr.Get(IdxPrefix.WorldStateKeyPreFix + accountChaincodeName + "_"+
          DidTplPrefix.hashPrefix + kid))
      }

      if (bcert != None && !(new String(bcert.get)).equalsIgnoreCase("null")) {
        val kvcert = SerializeUtils.deserialise(bcert.get).asInstanceOf[Certificate]
        if (kvcert != null ) {
            //从worldstate中获取证书，如果证书以及证书是有效时，返回证书信息
            r = Some((kvcert.certValid,kvcert.certHash,IdTool.getSigner4String(kvcert.id.get),getCertByPem(kvcert.certificate)))
        }
      }
    }catch{
      case e:Exception => throw e
    }
    r
  } recover { case e: Exception =>  None}




  def CertStatusUpdate(ck:String)={
    if(ck != null){
      val pos = ck.lastIndexOf("_"+DidTplPrefix.certPrefix)
      if(pos > 0){
        val ckey = ck.substring(ck.lastIndexOf("_")+1)
        if(this.caches.contains(ckey)){
          this.caches -= ckey
        }
      }
    }
  }

}
