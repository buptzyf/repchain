package rep.crypto.nodedynamicmanagement

import javax.net.ssl._
import java.io.{ByteArrayInputStream, File, FileInputStream, StringReader}
import java.net.Socket
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import scala.util.control.Breaks.{break, breakable}
import scala.collection.mutable.HashMap
import rep.app.system.RepChainSystemContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.bouncycastle.util.io.pem.PemReader
import rep.log.RepLogger
import rep.storage.chain.KeyPrefixManager
import rep.storage.db.factory.DBFactory
import rep.utils.{IdTool, SerializeUtils}
import scala.collection.JavaConverters._


/**
 * 节点网络里，阻止新的节点进入可通过server端对client端做验证，因此server端(getAcceptedIssuers)重加载TrustManager即可满足,<p>
 * 当然也可在client端(CheckServerTrusted)重加载TrustManager
 *
 * @author zyf
 */
final  class ReloadableTrustManager private(ctx: RepChainSystemContext) extends X509ExtendedTrustManager {
  // 默认不检查证书的有效时间
  private var checkValid = false
  private var trustStore: KeyStore = null
  private var trustManager: X509ExtendedTrustManager = null
  private val trustsMap = new ConcurrentHashMap[String, Certificate]() asScala
  private val trustStatus = new AtomicBoolean(false) //需要更新设置为true，装载完成设置false

  //初始化装载
  reloadTrustManager

  override def checkClientTrusted(chain: Array[X509Certificate], authType: String, socket: Socket): Unit = {
    trustManager.checkClientTrusted(chain, authType, socket)
  }

  override def checkServerTrusted(chain: Array[X509Certificate], authType: String, socket: Socket): Unit = {
    trustManager.checkServerTrusted(chain, authType, socket)
  }

  override def checkClientTrusted(chain: Array[X509Certificate], authType: String, engine: SSLEngine): Unit = {
    checkValid(chain)
    trustManager.checkClientTrusted(chain, authType, engine)
  }

  override def checkServerTrusted(chain: Array[X509Certificate], authType: String, engine: SSLEngine): Unit = {
    checkValid(chain)
    trustManager.checkServerTrusted(chain, authType, engine)
  }

  override def checkClientTrusted(chain: Array[X509Certificate], authType: String): Unit = {
    checkTrusted("client", chain, authType)
  }

  override def checkServerTrusted(chain: Array[X509Certificate], authType: String): Unit = {
    checkTrusted("server", chain, authType)
  }

  override def getAcceptedIssuers: Array[X509Certificate] = {
    try {
      reloadTrustManager
    } catch {
      case ex: Exception =>
        ex.printStackTrace();
    }
    trustManager.getAcceptedIssuers();
  }

  private def checkTrusted(role: String, chain: Array[X509Certificate], authType: String): Unit = {
    System.out.println(role + " check " + chain.length)
    var result = false
    try {
      if (chain == null || chain.length == 0 || authType == null || authType.length() == 0) {
        throw new IllegalArgumentException()
      }
      if (this.trustStatus.get()) {
        reloadTrustManager
      }
      chain.foreach(certificate => {
        if (checkValid) {
          certificate.checkValidity()
        }
      })
      result = this.isExistTrustCertificate(chain)
    } catch {
      case ex: Exception =>
        throw new CertificateException(ex.getMessage(), ex.getCause())
    }
    if (!result) {
      throw new CertificateException()
    }
  }

  private def isExistTrustCertificate(chain: Array[X509Certificate]): Boolean = {
    var r = false
    try {
      breakable(
        chain.foreach(certificate => {
          if (this.trustStore.getCertificateAlias(certificate) != null) {
            r = true
            break
          }
        }
        )
      )
    } catch {
      case e: Exception => r = false
    }
    r
  }

  private def checkValid(chain: Array[X509Certificate]): Unit = {
    try {
      if (checkValid) {
        chain.foreach(certificate => {
          certificate.checkValidity()
        })
      }
    } catch {
      case ex: Exception =>
        throw new CertificateException(ex.getMessage(), ex.getCause());
    }
  }

  private def reloadTrustManager: Unit = {
    this.trustStatus.set(false)
    this.loadTrustCertificate
    this.loadTrustStore
    this.loadTrustManager
  }

  private def loadTrustCertificateFromTrustFile: Unit = {
    val fis = new FileInputStream(new File(ctx.getConfig.getTrustStore))
    val pwd = ctx.getConfig.getTrustPassword.toCharArray()
    val trustKeyStore = KeyStore.getInstance(KeyStore.getDefaultType())
    trustKeyStore.load(fis, pwd)
    val enums = trustKeyStore.aliases()
    while (enums.hasMoreElements) {
      val alias = enums.nextElement()
      val cert = trustKeyStore.getCertificate(alias)
      this.trustsMap(alias) = cert
    }
    if (!this.trustsMap.isEmpty) {
      writeTrustCertificateToDB
    }
  }

  private def writeTrustCertificateToDB: Unit = {
    val map = new HashMap[String, Array[Byte]]
    this.trustsMap.foreach(f => {
      val alias = f._1
      val cert = f._2
      val pemReader = new PemReader(new StringReader(IdTool.toPemString(cert.asInstanceOf[X509Certificate])))
      val certBytes = pemReader.readPemObject().getContent
      map(alias) = certBytes
    })
    if (!map.isEmpty) {
      val db = DBFactory.getDBAccess(ctx.getConfig)
      db.putBytes(KeyPrefixManager.getWorldStateKey(ctx.getConfig, ReloadableTrustManager.key_trust_stores,
        "ManageNodeCert"), SerializeUtils.serialise(map))
    }
  }

  private def loadTrustCertificate = {
    this.trustsMap.clear()
    val db = DBFactory.getDBAccess(ctx.getConfig)
    val keyValue = db.getObject[HashMap[String, Array[Byte]]](
      KeyPrefixManager.getWorldStateKey(ctx.getConfig, ReloadableTrustManager.key_trust_stores,
        "ManageNodeCert"))
    if (keyValue == None) {
      loadTrustCertificateFromTrustFile
    } else {
      val certFactory: CertificateFactory = CertificateFactory.getInstance("X.509")
      val keyMap = keyValue.getOrElse(null)
      keyMap.foreach(item => {
        val k = item._1
        val v = item._2
        val nodeCert: Certificate = certFactory.generateCertificate(new ByteArrayInputStream(v))
        this.trustsMap(k) = nodeCert
      })
    }
  }

  private def loadTrustStore = {
    this.trustStore = KeyStore.getInstance(KeyStore.getDefaultType())
    this.trustsMap.foreach(f => {
      val k = f._1
      val cert = f._2
      this.trustStore.setCertificateEntry(k, cert);
    })
  }

  private def loadTrustManager = {
    val tmf: TrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(this.trustStore)

    val tm: Array[TrustManager] = tmf.getTrustManagers()
    if (tm != null) {
      breakable(
        tm.foreach(manager => {
          if (manager.isInstanceOf[X509ExtendedTrustManager]) {
            this.trustManager = manager.asInstanceOf[X509ExtendedTrustManager]
            break
          }
        })
      )
    }
  }

  def isCheckValid: Boolean = {
    checkValid
  }

  def setCheckValid(checkValid: Boolean): Unit = {
    this.checkValid = checkValid
  }

  def notifyTrustChange: Unit = {
    this.trustStatus.set(true)
  }

  def getTrustCertificate(name: String): Certificate = {
    if (this.trustsMap.contains(name)) {
      this.trustsMap(name)
    } else {
      null
    }
  }

  def getTrustNameList: Array[String] = {
    this.trustsMap.keySet.toArray[String]
  }
}

object ReloadableTrustManager {
  val key_trust_stores = "TSDb-Trust-Stores"
  private val TrustManagerInstances = new ConcurrentHashMap[String, ReloadableTrustManager]()


  def createReloadableTrustManager(ctx: RepChainSystemContext): ReloadableTrustManager = {
    var instance: ReloadableTrustManager = null

    if (TrustManagerInstances.containsKey(ctx.getSystemName)) {
      RepLogger.trace(RepLogger.System_Logger, s"ReloadableTrustManager exist, name=${ctx.getSystemName}")
      instance = TrustManagerInstances.get(ctx.getSystemName)
    } else {
      RepLogger.trace(RepLogger.System_Logger, s"ReloadableTrustManager not exist,create new Instance, name=${ctx.getSystemName}")
      instance = new ReloadableTrustManager(ctx)
      val old = TrustManagerInstances.putIfAbsent(ctx.getSystemName, instance)
      if (old != null) {
        instance = old
      }
    }
    instance
  }

  def getReloadableTrustManager(name: String): ReloadableTrustManager = {
    this.TrustManagerInstances.get(name)
  }

}



