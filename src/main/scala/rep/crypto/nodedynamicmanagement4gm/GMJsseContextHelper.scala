package rep.crypto.nodedynamicmanagement4gm

import java.io.{FileNotFoundException, IOException}
import java.nio.file.{Files, Paths}
import java.security.{GeneralSecurityException, KeyStore, SecureRandom}
import akka.remote.artery.tcp.SslTransportException
import javax.net.ssl._
import rep.app.conf.RepChainConfig
import rep.crypto.cert.CryptoMgr
import rep.crypto.nodedynamicmanagement.ReloadableTrustManager
import rep.log.RepLogger
import scala.util.Try

object GMJsseContextHelper {

  def createGMContext(conf:RepChainConfig,isSync:Boolean=false,sysName:String="",protocol:String=""):SSLContext={
    val SSLKeyStore: String = conf.getKeyStore
    val SSLTrustStore: String = conf.getTrustStore
    val SSLKeyStorePassword: String = conf.getKeyStorePassword
    val SSLKeyPassword: String = conf.getKeyPassword
    val SSLTrustStorePassword: String = conf.getTrustPassword
    val SSLProtocol: String = if(protocol == "") conf.getProtocol else protocol
    val SSLRandomNumberGenerator: String = conf.getSecureRandom

    try {
      val rng = createSecureRandom(SSLRandomNumberGenerator)
      val ctx = SSLContext.getInstance(SSLProtocol, conf.getGMJsseProviderName)
      ctx.init(keyManagers(SSLKeyStore, SSLKeyStorePassword,SSLKeyPassword,conf.getGMProviderNameOfJCE),
        trustManagers(SSLTrustStore, SSLTrustStorePassword,conf.getGMJsseProviderName,conf.getGMProviderNameOfJCE,isSync,sysName), rng)
      ctx
    } catch {
      case e: FileNotFoundException =>
        throw new SslTransportException(
          "Server SSL connection could not be established because key store could not be loaded",
          e)
      case e: IOException =>
        throw new SslTransportException("Server SSL connection could not be established because: " + e.getMessage, e)
      case e: GeneralSecurityException =>
        throw new SslTransportException(
          "Server SSL connection could not be established because SSL context could not be constructed",
          e)
    }
  }

  /**
   * Subclass may override to customize loading of `KeyStore`
   */
  def loadKeystore(filename: String, password: String,jceProviderName:String): KeyStore = {
    val keyStore = KeyStore.getInstance(CryptoMgr.keyStoreTypeInGM,jceProviderName)
    val fin = Files.newInputStream(Paths.get(filename))
    try keyStore.load(fin, password.toCharArray)
    finally Try(fin.close())
    keyStore
  }

  /**
   * Subclass may override to customize `KeyManager`
   * PKCS12，SunX509
   *
   */
  private def keyManagers(SSLKeyStore: String, SSLKeyStorePassword: String,SSLKeyPassword:String,jceProviderName:String): Array[KeyManager] = {
    val factory = KeyManagerFactory.getInstance("PKIX")
    factory.init(loadKeystore(SSLKeyStore, SSLKeyStorePassword,jceProviderName), SSLKeyPassword.toCharArray)
    factory.getKeyManagers
  }

  /**
   * Subclass may override to customize `TrustManager`
   */
  def trustManagers(SSLTrustStore:String, SSLTrustStorePassword:String,jsseProviderName:String,jceProviderName:String,isSynch:Boolean,sysName:String): Array[TrustManager] = {

    if(isSynch){
      Array(
        ReloadableTrustManager.getReloadableTrustManager(sysName).getProxyInstance
      )
    }else{
      var trustManagerFactory : TrustManagerFactory = null
      try {
        trustManagerFactory = TrustManagerFactory.getInstance("PKIX", jsseProviderName)
        trustManagerFactory.init(loadKeystore(SSLTrustStore, SSLTrustStorePassword,jceProviderName))
      } catch {
        case e: Exception =>
          trustManagerFactory = null
          e.printStackTrace()
      }
      if(trustManagerFactory == null){
        null
      }else{
        trustManagerFactory.getTrustManagers
      }

      //TrustCertLoader.getTrustManager(SSLTrustStore,SSLTrustStorePassword,jceProviderName,jsseProviderName)

      //Array(new TrustAllManager)
    }
  }

 def createSecureRandom(randomNumberGenerator: String): SecureRandom = {
    val rng = randomNumberGenerator match {
      case s @ ("SHA1PRNG" | "NativePRNG") =>
        RepLogger.debug(RepLogger.System_Logger,s"SSL random number generator set to: ${s}")
        // SHA1PRNG needs /dev/urandom to be the source on Linux to prevent problems with /dev/random blocking
        // However, this also makes the seed source insecure as the seed is reused to avoid blocking (not a problem on FreeBSD).
        SecureRandom.getInstance(s)

      case "" | "SecureRandom" =>
        RepLogger.debug(RepLogger.System_Logger,s"SSL random number generator set to [SecureRandom]")
        new SecureRandom

      case unknown =>
        System.out.println(s"repchain.crypt.SecureRandomFactory println msg:Unknown SSL random number generator [${unknown}] falling back to SecureRandom")
        new SecureRandom
    }
    rng.nextInt() // prevent stall on first access
    rng
  }
}
