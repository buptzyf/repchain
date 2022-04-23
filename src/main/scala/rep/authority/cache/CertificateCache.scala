package rep.authority.cache

import java.io.{ByteArrayInputStream, StringReader}
import org.bouncycastle.util.io.pem.PemReader
import rep.app.system.RepChainSystemContext
import rep.proto.rc2.Certificate
import rep.sc.tpl.did.DidTplPrefix
import rep.storage.chain.preload.BlockPreload
import rep.utils.IdTool

object CertificateCache{
  case class certData(certId:String,certHash:String,certificate:java.security.cert.Certificate,cert_valid:Boolean)
}

class CertificateCache(ctx : RepChainSystemContext) extends ICache(ctx){
  import CertificateCache._

  override protected def dataTypeConvert(any: Option[Any],blockPreload: BlockPreload): Option[Any] = {
    if(any == None){
      None
    }else{
      var cd : Option[certData] = None
      val cert = any.get.asInstanceOf[Certificate]
      if(cert != null) {
        cd = Some(certData(IdTool.getSigner4String(cert.id.get),cert.certHash,getCertByPem(cert.certificate),cert.certValid))
      }
      cd
    }
  }

  override protected def getPrefix: String = {
    if(IdTool.isDidContract(ctx.getConfig.getAccountContractName)){
      this.common_prefix + this.splitSign + DidTplPrefix.certPrefix
    }else{
      this.common_prefix + this.splitSign
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

}
