package rep.crypto.nodedynamicmanagement4gm

import java.security.{KeyStore, KeyStoreException}
import java.security.cert.Certificate

import javax.net.ssl.{TrustManager, TrustManagerFactory, X509ExtendedTrustManager}
import rep.log.RepLogger
import scala.collection.mutable.HashMap
import scala.util.control.Breaks.{break, breakable}

object TrustCertLoader {

  def getTrustManager(fileName:String,password:String,jceName:String,jsseName:String):Array[TrustManager]={
    val certs = loadTrustCertificateFromTrustFile(fileName, password, jceName)
    val ks = loadTrustStores(certs,jceName)
    val xtm = loadTrustManager(ks,jsseName)
    Array[TrustManager](xtm)
  }

  private def loadTrustCertificateFromTrustFile(fileName:String,password:String,jceName:String): HashMap[String, Certificate] = {
    val tmpMap = new HashMap[String, Certificate]()

    var trustKeyStore : KeyStore = null
    trustKeyStore = GMJsseContextHelper.loadKeystore(fileName,password,jceName)

    val enums = trustKeyStore.aliases()
    while (enums.hasMoreElements) {
      var alias = enums.nextElement()
      val cert = trustKeyStore.getCertificate(alias)
      ///todo 信任证书列表有问题，包含了文件名的后缀
      if(alias.indexOf(".cer") > 0){
        alias = alias.substring(0,alias.indexOf(".cer"))
      }
      //////////////////////////////////////////
      tmpMap(alias)=cert
    }
    RepLogger.trace(RepLogger.System_Logger, "CertificateUtil 在文件中装载信任证书="+tmpMap.mkString(","))

    tmpMap
  }

  private def loadTrustStores(recentCerts: HashMap[String, Certificate],jceName:String): KeyStore = {
    try {
      val Store = KeyStore.getInstance("PKCS12",jceName)
      Store.load(null, null)
      recentCerts.foreach(f => {
        var k = f._1
        val cert = f._2

        if(k.lastIndexOf(".cer") > 0){
          k = k.substring(0,k.lastIndexOf(".cer"))
        }
        Store.setCertificateEntry(k, cert);
      })
      Store
    } catch {
      case e: KeyStoreException =>
        throw e
    }
  }

  private def loadTrustManager(recentStore: KeyStore,jsseName:String): X509ExtendedTrustManager = {
    var rtm: X509ExtendedTrustManager = null
    val tmf: TrustManagerFactory = TrustManagerFactory.getInstance("PKIX", jsseName)
    tmf.init(recentStore)
    val tm: Array[TrustManager] = tmf.getTrustManagers()
    if (tm != null) {
      breakable(
        tm.foreach(manager => {
          if (manager.isInstanceOf[X509ExtendedTrustManager]) {
            rtm = manager.asInstanceOf[X509ExtendedTrustManager]
            break
          }
        })
      )
    }
    rtm
  }
}
