package rep.crypto

import java.nio.file.{Files, Paths}
import java.security.{KeyStore, SecureRandom}
import com.typesafe.config.Config
import javax.net.ssl.{KeyManager, KeyManagerFactory, SSLContext, TrustManager, TrustManagerFactory}
import scala.util.Try
//import akka.japi.Util._

object JsseContextHelper {
  def createJsseContext(config:Config):SSLContext={
    val prefix = "akka.remote.artery.ssl.config-ssl-engine."
    val SSLKeyStore: String = config.getString(prefix+"key-store")
    val SSLTrustStore: String = config.getString(prefix+"trust-store")
    val SSLKeyStorePassword: String = config.getString(prefix+"key-store-password")
    val SSLKeyPassword: String = config.getString(prefix+"key-password")
    val SSLTrustStorePassword: String = config.getString(prefix+"trust-store-password")
    //val SSLEnabledAlgorithms: Set[String] = immutableSeq(config.getStringList(prefix+"enabled-algorithms")).toSet
    val SSLProtocol: String = config.getString(prefix+"protocol")
    val SSLRandomNumberGenerator: String = config.getString(prefix+"random-number-generator")
    //val SSLRequireMutualAuthentication: Boolean = config.getBoolean(prefix+"require-mutual-authentication")
    //val HostnameVerification: Boolean = config.getBoolean(prefix+"hostname-verification")

    try {
      val rng = createSecureRandom(SSLRandomNumberGenerator)
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

  private def createSecureRandom(randomNumberGenerator: String): SecureRandom = {
    val rng = randomNumberGenerator match {
      case s @ ("SHA1PRNG" | "NativePRNG") =>
        SecureRandom.getInstance(s)
      case "" | "SecureRandom" =>
        new SecureRandom
      case unknown =>
        new SecureRandom
    }
    rng.nextInt()
    rng
  }
}
