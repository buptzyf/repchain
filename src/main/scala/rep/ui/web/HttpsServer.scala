package rep.ui.web

import java.io.{File, FileInputStream, InputStream}
import java.security.{KeyStore, SecureRandom}

import akka.actor.ActorSystem
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext, UseHttp2}
import akka.stream.{ActorMaterializer, TLSClientAuth}
import com.typesafe.sslconfig.akka.AkkaSSLConfig
import javax.net.ssl.{KeyManagerFactory, SSLContext, SSLParameters, TrustManagerFactory}

import scala.collection.immutable


/**
  * @author zyf
  */
object HttpsServer {

  // ConnectionContext
  def getHttpsContext(
             sslContext: SSLContext,
             sslConfig: Option[AkkaSSLConfig]                   = None,
             enabledCipherSuites: Option[immutable.Seq[String]] = None,
             enabledProtocols: Option[immutable.Seq[String]]    = None,
             clientAuth: Option[TLSClientAuth]                  = Some(TLSClientAuth.Need),
             sslParameters: Option[SSLParameters]               = None,
             http2: UseHttp2                                    = UseHttp2.Negotiated)
  = new HttpsConnectionContext(sslContext, sslConfig, enabledCipherSuites, enabledProtocols, clientAuth, sslParameters, http2)

  // Manual HTTPS configuration
  def HttpsContext: HttpsConnectionContext = {

    // do not store passwords in code, read them from somewhere safe!
    val password: Array[Char] = "123".toCharArray

    val cks: KeyStore = KeyStore.getInstance("JKS")
    val keystore: InputStream = new FileInputStream(new File("jks/ssl/serverr1.jks"))

    require(keystore != null, "Keystore required!")
    cks.load(keystore, password)

    val kf: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    kf.init(cks, password)

    val tks: KeyStore = KeyStore.getInstance("JKS")
    val trustkeystore: InputStream = new FileInputStream(new File("jks/ssl/clientr1.jks"))

    require(trustkeystore != null, "Keystore required!")
    tks.load(trustkeystore, password)

    val tmf: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
    tmf.init(tks)

    val sslContext: SSLContext = SSLContext.getInstance("TLS")
    sslContext.init(kf.getKeyManagers, tmf.getTrustManagers, new SecureRandom)
    val https: HttpsConnectionContext = getHttpsContext(sslContext)

    https
  }

  def start(actorSystem: ActorSystem, port: Int): Unit = {
    implicit val system = actorSystem
    implicit val materializer = ActorMaterializer()
    val sslConfig = AkkaSSLConfig.get(system)
//    Http().bindAndHandle(routes, "127.0.0.1", 9090, connectionContext = httpsConnectionContext)

  }
}
