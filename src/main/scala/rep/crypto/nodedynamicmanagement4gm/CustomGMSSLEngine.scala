package rep.crypto.nodedynamicmanagement4gm


import java.security.SecureRandom
import akka.actor.ActorSystem
import akka.event.{Logging, MarkerLoggingAdapter}
import akka.remote.artery.tcp.SSLEngineProvider
import akka.stream.TLSRole
import com.typesafe.config.Config
import javax.net.ssl.{SSLContext, SSLEngine, SSLSession}
import rep.app.system.RepChainSystemContext

class CustomGMSSLEngine  (protected val config: Config, protected val log: MarkerLoggingAdapter)
  extends SSLEngineProvider
{
  private var ctx : RepChainSystemContext = null
  def this(system: ActorSystem) = {
    this(
      system.settings.config.getConfig("akka.remote.artery.ssl.config-ssl-engine"),
      Logging.withMarker(system, classOf[CustomGMSSLEngine].getName))
  }

  val sysName = config.getString("node-name")
  ctx = RepChainSystemContext.getCtx(sysName)

  ////////////静态装载信任列表方法/////////////////////////////////////
  //val tmpTrustCerts = CertificateUtil.loadTrustCertificate(ctx)
  //发送更新给systemcertList和SignTool
  //ctx.getSignTool.updateCertList(tmpTrustCerts)
  ////////////静态装载信任列表方法/////////////////////////////////////

  val SSLEnabledAlgorithms: Set[String] = ctx.getConfig.getAlgorithm
  val SSLProtocol: String = ctx.getConfig.getProtocol
  val SSLRandomNumberGenerator: String = ctx.getConfig.getSecureRandom

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

  private def constructContext(): SSLContext = {
    GMJsseContextHelper.createGMContext(ctx.getConfig,true,ctx.getConfig.getSystemName)
  }

  def createSecureRandom(): SecureRandom = {
    GMJsseContextHelper.createSecureRandom(SSLRandomNumberGenerator)
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
