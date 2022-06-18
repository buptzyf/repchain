package rep.authority.cache

import java.io.{ByteArrayInputStream, StringReader}

import org.bouncycastle.util.io.pem.PemReader
import rep.authority.cache.PermissionCacheManager.CommonDataOfCache
import rep.proto.rc2.Certificate
import rep.sc.tpl.did.DidTplPrefix
import rep.storage.chain.preload.BlockPreload
import rep.utils.IdTool

object CertificateCache{
  case class certData(certId:String,certHash:String,certificate:java.security.cert.Certificate,cert_valid:Boolean)
}

class CertificateCache(cd:CommonDataOfCache,mgr:PermissionCacheManager) extends ICache(cd,mgr){
  import CertificateCache._

  override protected def dataTypeConvert(any: Option[Any],blockPreload: BlockPreload): Option[Any] = {
    if(any == None){
      None
    }else{
      var cd : Option[certData] = None
      val cert = any.get.asInstanceOf[Certificate]
      if(cert != null) {
        cd = Some(certData(IdTool.getSignerFromCertId(cert.id.get),cert.certHash,getCertByPem(cert.certificate),cert.certValid))
      }
      cd
    }
  }

  private def getCertByPem(pemCert: String): java.security.cert.Certificate = {
    val cf = java.security.cert.CertificateFactory.getInstance("X.509")
    val pemReader = new PemReader(new StringReader(pemCert))
    val certByte = pemReader.readPemObject().getContent
    val cert = cf.generateCertificate(new ByteArrayInputStream(certByte))
    cert
  }

  def get(key:String,blockPreload: BlockPreload):Option[certData]={
    val d = this.getData(key,blockPreload)
    if(d == None)
      None
    else
      Some(d.get.asInstanceOf[certData])
  }


  override protected def getCacheType: String = {
    DidTplPrefix.certPrefix
  }
}
