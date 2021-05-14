package rep.ssl

import java.io.{DataInputStream, FileInputStream, InputStream}
import java.math.BigInteger
import java.net.URL
import java.security.cert._
import java.security.{KeyStore, PrivateKey, Security}
import java.util.Date

import akka.actor.ActorSystem
import akka.event.{Logging, MarkerLoggingAdapter}
import akka.remote.artery.tcp.ConfigSSLEngineProvider
import com.typesafe.config.Config
import javax.net.ssl._
import sun.security.x509.{X500Name, X509CRLEntryImpl, X509CRLImpl}


object CustomSSLEngineProvider {
  // initialize certification path checking for the offered certificates and revocation checks against CLRs
  val cpb: CertPathBuilder = CertPathBuilder.getInstance("PKIX")
  private[CustomSSLEngineProvider] val rc: PKIXRevocationChecker = cpb.getRevocationChecker.asInstanceOf[PKIXRevocationChecker]

  def modify(): Unit = {
    null
  }
}

class CustomSSLEngineProvider(override val config: Config, override val log: MarkerLoggingAdapter)
  extends ConfigSSLEngineProvider(config: Config, log: MarkerLoggingAdapter) {

  def this(system: ActorSystem) =
    this(
      system.settings.config.getConfig("akka.remote.artery.ssl.config-ssl-engine"),
      Logging.withMarker(system, classOf[CustomSSLEngineProvider].getName))

  import java.security.cert.{PKIXBuilderParameters, PKIXRevocationChecker, X509CertSelector}
  import java.util

  import CustomSSLEngineProvider.rc

  rc.setOptions(util.EnumSet.of(
    PKIXRevocationChecker.Option.PREFER_CRLS, // prefer CLR over OCSP
    PKIXRevocationChecker.Option.ONLY_END_ENTITY,
    PKIXRevocationChecker.Option.NO_FALLBACK
  )) // don't fall back to OCSP checking
  //  rc.setOcspResponder(URI.create("http://ocsp.sectigo.com"))

  /**
    * Subclass may override to customize `TrustManager`
    */
  override protected def trustManagers: Array[TrustManager] = {

    //    val cacerts: KeyStore = KeyStore.getInstance("JKS")
    //    val tfis: FileInputStream = new FileInputStream("D:\\Program Files\\Java\\zulu13.28.11-ca-jdk13.0.1-win_x64\\lib\\security\\cacerts")
    //    cacerts.load(tfis, "changeit".toCharArray)
    //    val certList = new util.ArrayList[Certificate]()
    //    cacerts.aliases().asIterator().forEachRemaining(alias => certList.add(cacerts.getCertificate(alias)))

    Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())
    val cacerts: KeyStore = KeyStore.getInstance("JKS")
    val cafis: FileInputStream = new FileInputStream("jks/p12s/trust.p12")
    cacerts.load(cafis, "changeit".toCharArray)
    // 准备好创建 CRL 所需的私钥和证书
    val caPrivateKey = cacerts.getKey("trust", "changeit".toCharArray).asInstanceOf[PrivateKey]
    val caCertificate = cacerts.getCertificate("trust").asInstanceOf[X509Certificate]

    val crlBuilder = new X509CRLImpl(new X500Name(caCertificate.getSubjectDN.getName), new Date(), new Date(System.currentTimeMillis() + 86400 * 1000),
      Array[X509CRLEntry](new X509CRLEntryImpl(new BigInteger("1576032999"), new Date())))
    crlBuilder.sign(caPrivateKey, "sha256withecdsa")
    //    crlBuilder.setNextUpdate(new Date(System.currentTimeMillis() + 86400 * 1000)); // 1 天有效期

    //    val contentSignerBuilder = new JcaContentSignerBuilder("SHA256WithECDSA")
    //    contentSignerBuilder.setProvider("BC")
    //    val crlHolder = crlBuilder.build(contentSignerBuilder.build(caPrivateKey))
    //    val converter = new JcaX509CRLConverter()
    //    converter.setProvider("BC")
    //    val cacrl = converter.getCRL(crlHolder)

    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    val ts = loadKeystore(SSLTrustStore, SSLTrustStorePassword)
    val pkixParams = new PKIXBuilderParameters(ts, new X509CertSelector)
    pkixParams.addCertPathChecker(rc)
    val url = new URL("http://crl3.digicert.com/GeoTrustCNRSACAG1.crl")
    val crl = generateCRLFromURL(url)
        val collectionCertStoreParameters = new CollectionCertStoreParameters(util.Arrays.asList(crlBuilder))
//    pkixParams.addCertStore(CertStore.getInstance("Collection", collectionCertStoreParameters))
    //    pkixParams.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(util.Arrays.asList(ts.getCertificate("trust")))))
    //    pkixParams.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(certList)))
    // TODO 写个合约动态添加
//        collectionCertStoreParameters.getCollection.add(None)

    pkixParams.setRevocationEnabled(true)
    //    pkixParams.setDate(new Date(121, 9, 10))
    import java.security.Security
    Security.setProperty("ocsp.enable", "true")
    System.setProperty("com.sun.net.ssl.checkRevocation", "true")
    System.setProperty("com.sun.security.enableCRLDP", "true")
    rc.getOptions
    trustManagerFactory.init(new CertPathTrustManagerParameters(pkixParams))
    trustManagerFactory.getTrustManagers
  }

  def generateCRLFromURL(url: URL): CRL = {
    val connection = url.openConnection()
    connection.setDoInput(true)
    connection.setUseCaches(false)
    val inStream = new DataInputStream(connection.getInputStream)
    try {
      generateCRL(inStream)
    } finally {
      inStream.close()
    }
  }

  def generateCRL(inputStream: InputStream): CRL = {
    val cf = CertificateFactory.getInstance("X509")
    val crl = cf.generateCRL(inputStream).asInstanceOf[X509CRL]
    crl
  }


}
