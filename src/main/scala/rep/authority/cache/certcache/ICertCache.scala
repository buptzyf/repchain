package rep.authority.cache.certcache

import java.io.{ByteArrayInputStream, StringReader}
import java.util.concurrent.ConcurrentHashMap

import org.bouncycastle.util.io.pem.PemReader
import rep.authority.check.PermissionKeyPrefix
import rep.log.RepLogger
import rep.proto.rc2.Certificate
import rep.utils.{IdTool, SerializeUtils}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
 * Created by jiangbuyun on 2020/07/5.
 * 实现证书缓存
 */

object ICertCache{
  case class certData(certId:String,certHash:String,certificate:java.security.cert.Certificate,cert_valid:Boolean)
}

abstract class ICertCache(sysTag:String){
  import ICertCache.certData
  protected implicit var cert_map_cache = new ConcurrentHashMap[String, Option[certData]] asScala
  protected implicit var cert_hash_map_cache = new ConcurrentHashMap[String, String] asScala
  protected implicit var cert_read_map_cache = new ConcurrentHashMap[String, Future[Option[certData]]] asScala

  protected def getDataFromStorage(key:String):Array[Byte]

  def ChangeValue(key:String)={
    val idx = key.lastIndexOf("_")
    if(idx > 0){
      val id = key.substring(idx+1)
      if(this.cert_map_cache.contains(id)){
        this.cert_map_cache.remove(id)
      }
    }
  }

  private def AsyncHandle(cKey:String):Future[Option[certData]]=Future{
    var r : Option[certData] = None

    try {
      var bb :Array[Byte] = null
      bb = getDataFromStorage(PermissionKeyPrefix.certPrefix+cKey)
      if(bb != null){
        val cert = ValueToCertificate(bb)
        if(cert != null){
          RepLogger.Permission_Logger.trace(s"ICertCache.AsynHandle get auth object,key=${cKey}")
          val cd = certToCertData(cert)
          if(cd != None) r = cd
        }
      }
    }catch {
      case e : Exception =>
        RepLogger.Permission_Logger.error(s"ICertCache.AsynHandle get op error" +
          s", info=${e.getMessage},key=${cKey}")
    }
    r
  }

  private def getCertId(bb:Array[Byte]):String={
    var cKey : String = null

    try{
      if(bb != null)
        cKey = SerializeUtils.deserialise(bb).asInstanceOf[String]
    }catch {
      case e: Exception => {
        //todo add exception to log file
        RepLogger.Permission_Logger.error(s"ICertCache.getCertId deserialise error,info=${e.getMessage}")
      }
    }
    cKey
  }

  def FindCertificate(certId:String):certData={
    var cd : certData = null

    //检查数据缓存是否包含了certid，如果包含直接返回给调用者
    if(this.cert_map_cache.contains(certId)){
      cd = this.cert_map_cache.get(certId).get.getOrElse(null)
    }else{
      //数据缓存没有包含certid，说明数据未加载到缓存，需要异步计算
      var tmpcert : Future[Option[certData]] = this.cert_read_map_cache.get(certId).getOrElse(null)
      if(tmpcert == null){
        tmpcert = AsyncHandle(certId)
        this.cert_read_map_cache.putIfAbsent(certId,tmpcert)
      }
      val result = Await.result(tmpcert, 30.seconds).asInstanceOf[Option[certData]]
      //将结果放到数据缓存，结果有可能是None
      cd = result.getOrElse(null)
      this.cert_map_cache.put(certId,result)
      this.cert_read_map_cache.remove(certId)
    }

    cd
  }

  def FindCertId4CertHash(certHash:String):String={
    var certid : String = null
    if(cert_hash_map_cache.contains(certHash)){
      val certid = this.cert_hash_map_cache.get(certHash)
    }else{
      synchronized{
        val key = getCertId(getDataFromStorage(PermissionKeyPrefix.certHashPrefix+certHash))
        if(key != null){
          certid = key
          cert_hash_map_cache.put(certHash,key)
        }
      }
    }
    certid
  }

  def FindCertificate4CertHash(certHash:String):certData={
    var cd : certData = null

    val certid = FindCertId4CertHash(certHash)
    if(certid != null){
      cd = FindCertificate(certid)
    }

    cd
  }

  protected def ValueToCertificate(value:Array[Byte]):Certificate={
    var cert : Certificate = null

    try{
      if(value != null)
        cert = SerializeUtils.deserialise(value).asInstanceOf[Certificate]
    }catch {
      case e: Exception => {
        //todo add exception to log file
        RepLogger.Permission_Logger.error(s"ICertCache.ValueToCertificate deserialise error,info=${e.getMessage}")
      }
    }
    cert
  }

  private def getCertByPem(pemcert: String): java.security.cert.Certificate = {
    val cf = java.security.cert.CertificateFactory.getInstance("X.509")
    val pemReader = new PemReader(new StringReader(pemcert))
    val certByte = pemReader.readPemObject().getContent
    val cert = cf.generateCertificate(new ByteArrayInputStream(certByte))
    cert
  }

  protected def certToCertData(cert:Certificate):Option[certData] = {
    var cd : Option[certData] = None
    if(cert != null) {
      cd = Some(new certData(IdTool.getSigner4String(cert.id.get),cert.certHash,getCertByPem(cert.certificate),cert.certValid))
    }
    cd
  }
}
