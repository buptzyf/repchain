package rep.ssl

import java.io.{FileInputStream, InputStream}
import java.security.cert.{CertStore, PKIXBuilderParameters, PKIXRevocationChecker, X509CertSelector, _}
import java.util
import java.util.concurrent.{Executors, TimeUnit}

import akka.actor.ActorSystem
import akka.event.{Logging, MarkerLoggingAdapter}
import akka.remote.artery.tcp.ConfigSSLEngineProvider
import com.typesafe.config.Config
import javax.net.ssl._
import rep.app.conf.TimePolicy
import rep.sc.scalax.{ContractContext, ContractException}

/**
  * @author zyf
  */
object CustomSSLEngine {

  // initialize certification path checking for the offered certificates and revocation checks against CLRs
  val cpb: CertPathBuilder = CertPathBuilder.getInstance("PKIX")
  private[CustomSSLEngine] val rc: PKIXRevocationChecker = cpb.getRevocationChecker.asInstanceOf[PKIXRevocationChecker]
  private[CustomSSLEngine] val crlList = new util.ArrayList[X509CRL]
//  private[CustomSSLEngine] var sr: ImpDataAccess = _


  /**
    * 清除初始化的crlList
    *
    * @param ctx
    */
  def clearCrlList(ctx: ContractContext): Unit = {
    if (isSuperAdmin(ctx)) {
      try {
//        // TODO 看一下能不能删除
//        sr = ImpDataAccess.GetDataAccess("CustomSSLEngine")
//        // TODO super_admin的creditCode要写到配置文件中
//        val pkey = WorldStateKeyPreFix + "ManageNodeCert" + "_" + "crl_" + "951002007l78123233"
//        val pvalue = sr.Get(pkey)
//        if (pvalue != null) {
          crlList.remove(0)
//        }
        System.err.print("*************************" + crlList.size())
      } catch {
        case ex: Exception => throw ContractException(ex.getMessage, ex)
      }
    }
  }

  /**
    * 清除初始化的crlList
    *
    */
  private[CustomSSLEngine] def _clearCrlList: Unit = {
    try {
      System.err.print("^^^^^^^^^^^^^^^^^" + crlList.size())
      crlList.remove(0)
      System.err.print("^^^^^^^^^^^^^^^^^" + crlList.size())
    } catch {
      case ex: Exception => throw ContractException(ex.getMessage, ex)
    }
  }

  /**
    * 更新crl列表
    *
    * @param ctx
    * @param crl
    * @return
    */
  def updateCrlList(ctx: ContractContext, crl: X509CRL): Boolean = {
    var res = false
    if (isSuperAdmin(ctx)) {
      try {
        crlList.remove(0)
        crlList.add(0, crl)
        res = true
      } catch {
        case _: Exception => res = false
      }
    }
    res
  }

  /**
    * 获取crl列表
    *
    * @param ctx
    * @return
    */
  def getCrlList(ctx: ContractContext): X509CRL = {
    try {
      crlList.get(0)
    } catch {
      case _: Exception => null
    }
  }


  /**
    * 判断是否是super_admin
    *
    * @param ctx
    * @return
    */
  def isSuperAdmin(ctx: ContractContext): Boolean = {
    var res = false
    val creditCode = ctx.t.getSignature.getCertId.creditCode
    val certName = ctx.t.getSignature.getCertId.certName
    if (ctx.api.bNodeCreditCode(creditCode) && certName.equals("super_admin")) {
      res = true
    }
    res
  }

  /**
    *
    * @param inputStream
    * @return
    */
  def generateCRL(inputStream: InputStream): X509CRL = {
    val cf = CertificateFactory.getInstance("X509")
    val crl = cf.generateCRL(inputStream).asInstanceOf[X509CRL]
    crl
  }

}

/**
  * @author zyf
  * @param config
  * @param log
  */
class CustomSSLEngine(override val config: Config, override val log: MarkerLoggingAdapter)
  extends ConfigSSLEngineProvider(config: Config, log: MarkerLoggingAdapter) {

  def this(system: ActorSystem) = this(
    system.settings.config.getConfig("akka.remote.artery.ssl.config-ssl-engine"),
    Logging.withMarker(system, classOf[CustomSSLEngine].getName)
  )

  // 初始化的crl路径已写到配置文件里
  val SSLTrustCrl: String = config.getString("trust-crl")

  import CustomSSLEngine.{_clearCrlList, crlList, rc}

  rc.setOptions(util.EnumSet.of(
    // prefer CLR over OCSP
    PKIXRevocationChecker.Option.PREFER_CRLS,
    PKIXRevocationChecker.Option.ONLY_END_ENTITY
    // don't fall back to OCSP checking
    // PKIXRevocationChecker.Option.NO_FALLBACK
  ))

  /**
    * 一次自动清除自动清除
    */
  Executors.newSingleThreadScheduledExecutor().schedule(new Runnable {
    override def run(): Unit = _clearCrlList
  }, TimePolicy.getTranscationWaiting, TimeUnit.SECONDS)

  /**
    * Subclass may override to customize `TrustManager`
    */
  override protected def trustManagers: Array[TrustManager] = {

    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    val ts = loadKeystore(SSLTrustStore, SSLTrustStorePassword)

    val pkixParams = new PKIXBuilderParameters(ts, new X509CertSelector)
    pkixParams.addCertPathChecker(rc)

    // 获取初始化CRL
    val crl: X509CRL = CustomSSLEngine.generateCRL(new FileInputStream(SSLTrustCrl))
    crlList.add(0, crl)
    val collectionCertStoreParameters = new CollectionCertStoreParameters(crlList)
    // 初始化CRL到CertStore中
    pkixParams.addCertStore(CertStore.getInstance("Collection", collectionCertStoreParameters))

    pkixParams.setRevocationEnabled(true)

    trustManagerFactory.init(new CertPathTrustManagerParameters(pkixParams))
    trustManagerFactory.getTrustManagers

  }

}
