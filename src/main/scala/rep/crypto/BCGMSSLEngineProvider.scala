package rep.crypto

import java.io.{FileNotFoundException, IOException}
import java.nio.file.{Files, Paths}
import java.security.{GeneralSecurityException, KeyStore, Provider, SecureRandom, Security}

import akka.actor.{ActorSystem, Props}
import akka.event.{Logging, MarkerLoggingAdapter}
import akka.japi.Util.immutableSeq
import akka.remote.artery.tcp.{SSLEngineProvider, SslTransportException}
import akka.stream.TLSRole
import com.typesafe.config.Config
import javax.net.ssl.{KeyManager, KeyManagerFactory, SSLContext, SSLEngine, SSLSession, TrustManager}
import rep.app.system.RepChainSystemContext
import rep.network.module.ModuleActorType
import rep.network.tools.PeerExtension
import rep.ui.web.EventServer

import scala.util.Try
import scala.util.control.Breaks.{break, breakable}

class BCGMSSLEngineProvider (protected val config: Config, protected val log: MarkerLoggingAdapter)
  extends SSLEngineProvider
{
  private var system: ActorSystem = null
  def this(system: ActorSystem) = {
    this(
      system.settings.config.getConfig("akka.remote.artery.ssl.config-ssl-engine"),
      Logging.withMarker(system, classOf[BCGMSSLEngineProvider].getName))
    this.system = system
  }


  private val keyStoreType = "PKCS12"
  private val jceProviderName = "BC"
  private val jsseProviderName = "BCJSSE"
  System.out.println("%%%%%%%%%%%%%%%%")
  loadProvider("org.bouncycastle.jce.provider.BouncyCastleProvider",1)
  loadProvider("org.bouncycastle.jsse.provider.BouncyCastleJsseProvider",2)
  System.out.println("%%%%%%%%%%%%%%%%")

  private def loadProvider(cname:String,serial:Int):Unit={
    try{
      val cls = this.getClass.getClassLoader.loadClass(cname)
      System.out.println(s"get getClassLoader = ${cls.getName}")
      val csts =  cls.getConstructors()
      System.out.println(s"get Constructors = ${csts.length}")
      if(csts.length > 0){
        breakable(
          csts.foreach(cst=>{
            if(cst.getParameterCount == 0){
              System.out.println(s"get Constructors0 = ${cst.getName},params=${cst.getParameterCount}")
              val p = cst.newInstance().asInstanceOf[Provider]
              System.out.println(s"get instance = ${p.getName}")
              Security.insertProviderAt(p,serial)
              //Security.addProvider(p)
              break
            }
          }))
      }
    }catch {
      case e:Exception => System.out.println(s"loadProvider error,msg=${e.getMessage}")
    }
  }

  val SSLKeyStore: String = config.getString("key-store")
  val SSLTrustStore: String = config.getString("trust-store")
  val SSLKeyStorePassword: String = config.getString("key-store-password")
  val SSLKeyPassword: String = config.getString("key-password")
  val SSLTrustStorePassword: String = config.getString("trust-store-password")
  val SSLEnabledAlgorithms: Set[String] = immutableSeq(config.getStringList("enabled-algorithms")).toSet
  val SSLProtocol: String = config.getString("protocol")
  val SSLRandomNumberGenerator: String = config.getString("random-number-generator")
  val SSLRequireMutualAuthentication: Boolean = config.getBoolean("require-mutual-authentication")
  val HostnameVerification: Boolean = config.getBoolean("hostname-verification")

  private lazy val sslContext: SSLContext = {
    // log hostname verification warning once
    if (HostnameVerification)
      log.debug("TLS/SSL hostname verification is enabled.")
    else
      System.out.println("rep.crypto.ConfigSSSLEngineProvider warning:TLS/SSL hostname verification is disabled. " +
        "Please configure akka.remote.artery.ssl.config-ssl-engine.hostname-verification=on " +
        "and ensure the X.509 certificate on the host is correct to remove this warning. " +
        "See Akka reference documentation for more information.")

    constructContext()
  }

  def getSSLContext:SSLContext={
    sslContext
  }

  private def constructContext(): SSLContext = {
    try {
      val rng = createSecureRandom()
      val ctx = SSLContext.getInstance(SSLProtocol, jsseProviderName)
      //ctx.init(keyManagers, trustManagers, rng) //new TrustAllManager()
      ctx.init(keyManagers, trustManagers, rng)
      val pe = PeerExtension(this.system)
      pe.setSSLContext(ctx)
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
  protected def loadKeystore(filename: String, password: String): KeyStore = {
    val keyStore = KeyStore.getInstance(keyStoreType,jceProviderName)
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
  protected def keyManagers: Array[KeyManager] = {
    val factory = KeyManagerFactory.getInstance("PKIX")
    factory.init(loadKeystore(SSLKeyStore, SSLKeyStorePassword), SSLKeyPassword.toCharArray)
    factory.getKeyManagers
  }

  /**
   * Subclass may override to customize `TrustManager`
   */
  protected def trustManagers: Array[TrustManager] = {
    /*val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
    trustManagerFactory.init(loadKeystore(SSLTrustStore, SSLTrustStorePassword))
    trustManagerFactory.getTrustManagers*/
    Array(new TrustAllManager)
  }

  def createSecureRandom(): SecureRandom = {
    SecureRandomFactoryInGM.createSecureRandom(SSLRandomNumberGenerator, log)
  }

  override def createServerSSLEngine(hostname: String, port: Int): SSLEngine =
    createSSLEngine(akka.stream.Server, hostname, port)

  override def createClientSSLEngine(hostname: String, port: Int): SSLEngine =
    createSSLEngine(akka.stream.Client, hostname, port)

  private def createSSLEngine(role: TLSRole, hostname: String, port: Int): SSLEngine = {
    createSSLEngine(sslContext, role, hostname, port)
  }

  private def createSSLEngine(sslContext: SSLContext, role: TLSRole, hostname: String, port: Int): SSLEngine = {

    val engine = sslContext.createSSLEngine(hostname, port)

    if (HostnameVerification && role == akka.stream.Client) {
      val sslParams = sslContext.getDefaultSSLParameters
      sslParams.setEndpointIdentificationAlgorithm("HTTPS")
      engine.setSSLParameters(sslParams)
    }

    engine.setUseClientMode(role == akka.stream.Client)
    //engine.setEnabledCipherSuites(engine.getSupportedCipherSuites)
    engine.setEnabledCipherSuites(SSLEnabledAlgorithms.toArray)
    engine.setEnabledProtocols(Array(SSLProtocol))



    if ((role != akka.stream.Client) && SSLRequireMutualAuthentication)
      engine.setNeedClientAuth(true)

    engine
  }

  override def verifyClientSession(hostname: String, session: SSLSession): Option[Throwable] =
    None

  override def verifyServerSession(hostname: String, session: SSLSession): Option[Throwable] =
    None

}

private object SecureRandomFactoryInGM {
  def createSecureRandom(randomNumberGenerator: String, log: MarkerLoggingAdapter): SecureRandom = {
    val rng = randomNumberGenerator match {
      case s @ ("SHA1PRNG" | "NativePRNG") =>
        log.debug("SSL random number generator set to: {}", s)
        // SHA1PRNG needs /dev/urandom to be the source on Linux to prevent problems with /dev/random blocking
        // However, this also makes the seed source insecure as the seed is reused to avoid blocking (not a problem on FreeBSD).
        SecureRandom.getInstance(s)

      case "" | "SecureRandom" =>
        log.debug("SSL random number generator set to [SecureRandom]")
        new SecureRandom

      case unknown =>
        System.out.println(s"repchain.crypt.SecureRandomFactory println msg:Unknown SSL random number generator [${unknown}] falling back to SecureRandom")
        new SecureRandom
    }
    rng.nextInt() // prevent stall on first access
    rng
  }
}