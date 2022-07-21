package rep.crypto.nodedynamicmanagement

import java.nio.file.{Files, Paths}
import java.security.{KeyStore}
import javax.net.ssl._
import rep.app.conf.RepChainConfig
import rep.crypto.nodedynamicmanagement4gm.GMJsseContextHelper
import scala.util.Try

object JsseContextHelper {
  def createJsseContext(conf:RepChainConfig,protocol:String=""):SSLContext={
    val SSLKeyStore: String = conf.getKeyStore
    val SSLTrustStore: String = conf.getTrustStore
    val SSLKeyStorePassword: String = conf.getKeyStorePassword
    val SSLKeyPassword: String = conf.getKeyPassword
    val SSLTrustStorePassword: String = conf.getTrustPassword
    val SSLProtocol: String = if(protocol == "") conf.getProtocol else protocol
    val SSLRandomNumberGenerator: String = conf.getSecureRandom

    try {
      val rng = GMJsseContextHelper.createSecureRandom(SSLRandomNumberGenerator)
      val ctx = SSLContext.getInstance(SSLProtocol)
      ctx.init(keyManagers(SSLKeyStore, SSLKeyStorePassword,SSLKeyPassword),
        trustManagers(SSLTrustStore, SSLTrustStorePassword), rng)
      ctx
    } catch {
      case e: Exception => null
    }
  }

  private def trustManagers(SSLTrustStore:String, SSLTrustStorePassword:String): Array[TrustManager] = {
    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    trustManagerFactory.init(loadKeystore(SSLTrustStore, SSLTrustStorePassword))
    trustManagerFactory.getTrustManagers
  }

  private def keyManagers(SSLKeyStore: String, SSLKeyStorePassword: String,SSLKeyPassword:String): Array[KeyManager] = {
    val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    factory.init(loadKeystore(SSLKeyStore, SSLKeyStorePassword), SSLKeyPassword.toCharArray)
    factory.getKeyManagers
  }

  private def loadKeystore(filename: String, password: String): KeyStore = {
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
    val fin = Files.newInputStream(Paths.get(filename))
    try keyStore.load(fin, password.toCharArray)
    finally Try(fin.close())
    keyStore
  }

}
