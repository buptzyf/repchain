package rep.crypto.nodedynamicmanagement

import java.io.StringReader
import java.security.cert.X509Certificate
import java.util.concurrent.{Executors, TimeUnit}
import org.bouncycastle.util.io.pem.PemReader
import rep.app.system.RepChainSystemContext
import rep.crypto.cert.CertificateUtil
import rep.utils.IdTool

import scala.collection.mutable

class ReloadableTrustManagerTest4Inner(ctx:RepChainSystemContext) {
  var scheduledExecutorService = Executors.newSingleThreadScheduledExecutor
  private val testName = "379552050023903168.node5"//"921000006e0012v696.node5"
  private val hm = getCertificates
  private val testNode = (testName,hm(testName))

  private def getCertificates:mutable.HashMap[String,Array[Byte]]={
    val rhm = new mutable.HashMap[String,Array[Byte]]()
    val tmp = CertificateUtil.loadTrustCertificate(ctx)
    tmp.foreach(f=>{
      val pemReader = new PemReader(new StringReader(IdTool.toPemString(f._2.asInstanceOf[X509Certificate])))
      val certBytes = pemReader.readPemObject().getContent
      rhm(f._1) = certBytes
    })
    rhm
  }

  def StartClusterStub={
    this.scheduledExecutorService.scheduleWithFixedDelay(//).scheduleAtFixedRate(
      new ClusterTestStub,120,120, TimeUnit.SECONDS
    )
  }

  class ClusterTestStub extends Runnable{
    override def run(){
      try{
        if(!hm.contains(testName)){
          //add
          hm(testName) = testNode._2
          System.err.println(s"*****add ${testName} certificate")
        }else{
          //sub
          hm -= testName
          System.err.println(s"****delete ${testName} certificate")
        }
        System.err.println(s"****notify change ${testName} certificate")
        ctx.getReloadTrustStore.notifyTrustChange(hm)
      }catch{
        case e:Exception=>e.printStackTrace()
      }
    }
  }
}
