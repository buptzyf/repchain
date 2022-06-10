package rep.crypto.nodedynamicmanagement

import javax.net.ssl._
import java.net.Socket
import java.security.{KeyStore, KeyStoreException}
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import scala.util.control.Breaks.{break, breakable}
import scala.collection.mutable.{ArrayBuffer, HashMap}
import rep.app.system.RepChainSystemContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import rep.crypto.cert.CertificateUtil
import rep.log.RepLogger



/**
 * 节点网络里，阻止新的节点进入可通过server端对client端做验证，因此server端(getAcceptedIssuers)重加载TrustManager即可满足,<p>
 * 当然也可在client端(CheckServerTrusted)重加载TrustManager
 *
 * @author zyf
 */
final  class ReloadableTrustManager private(ctx: RepChainSystemContext) extends X509ExtendedTrustManager {
  // 默认不检查证书的有效时间
  private val checkValid = new AtomicBoolean(false)
  private var trustCertificateManager : ReloadableTrustManager.Manager =  null

  private val isUpdateTrustCerts = new AtomicBoolean(false) //需要更新设置为true，装载完成设置false
  private val lock : Object = new Object

  //初始化装载
  loadUpdateTrustCertificateManager(null)

  override def checkClientTrusted(chain: Array[X509Certificate], authType: String, socket: Socket): Unit = {
    this.lock.synchronized({
      this.trustCertificateManager.trustManager.checkClientTrusted(chain, authType, socket)
    })
  }

  override def checkServerTrusted(chain: Array[X509Certificate], authType: String, socket: Socket): Unit = {
    this.lock.synchronized({this.trustCertificateManager.trustManager.checkServerTrusted(chain, authType, socket)})
  }

  override def checkClientTrusted(chain: Array[X509Certificate], authType: String, engine: SSLEngine): Unit = {
    checkValid(chain)
    this.lock.synchronized({this.trustCertificateManager.trustManager.checkClientTrusted(chain, authType, engine)})
  }

  override def checkServerTrusted(chain: Array[X509Certificate], authType: String, engine: SSLEngine): Unit = {
    checkValid(chain)
    this.lock.synchronized({this.trustCertificateManager.trustManager.checkServerTrusted(chain, authType, engine)})
  }

  override def checkClientTrusted(chain: Array[X509Certificate], authType: String): Unit = {
    checkTrusted("client", chain, authType)
  }

  override def checkServerTrusted(chain: Array[X509Certificate], authType: String): Unit = {
    checkTrusted("server", chain, authType)
  }

  override def getAcceptedIssuers: Array[X509Certificate] = {
    this.lock.synchronized({
      this.trustCertificateManager.trustManager.getAcceptedIssuers()
    })
  }

  private def checkTrusted(role: String, chain: Array[X509Certificate], authType: String): Unit = {
    System.out.println(role + " check " + chain.length)
    var result = false
    try {
      if (chain == null || chain.length == 0 || authType == null || authType.length() == 0) {
        throw new IllegalArgumentException()
      }
      this.lock.synchronized({
        chain.foreach(certificate => {
          if (checkValid.get()) {
            certificate.checkValidity()
          }
        })
        result = this.isExistTrustCertificate(chain)
      })
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
          if (this.trustCertificateManager.trustKeyStore.getCertificateAlias(certificate) != null) {
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
      if (checkValid.get()) {
        chain.foreach(certificate => {
          certificate.checkValidity()
        })
      }
    } catch {
      case ex: Exception =>
        throw new CertificateException(ex.getMessage(), ex.getCause());
    }
  }


  class UpdateThread(updateCertInfo:HashMap[String,Array[Byte]]) extends Runnable{

    override def run(): Unit = {
      try{
        loadUpdateTrustCertificateManager(updateCertInfo)
      }catch {
        case ex:Exception=>
          RepLogger.trace(RepLogger.System_Logger, "ReloadableTrustManager 接收更新线程异常，msg="+ex.getMessage)
      }

    }
  }

  ///////////////////////信任证书装载/////////////////////////////////////////////////////////////////////
  private def loadUpdateTrustCertificateManager(updateCertInfo:HashMap[String,Array[Byte]]): Unit = {
    try{
      this.lock.synchronized({
      var oldCertificates:HashMap[String,Certificate] = null
      if(trustCertificateManager != null && trustCertificateManager.trustCertificates != null){
        oldCertificates = trustCertificateManager.trustCertificates
      }
      val tmpTrustCerts = if(updateCertInfo == null)
                            CertificateUtil.loadTrustCertificate(ctx)
                          else
                            CertificateUtil.loadTrustCertificateFromBytes(updateCertInfo)
      val certsOfDeleted = findDeleteCerts(tmpTrustCerts,oldCertificates)
      val keyStore = loadTrustStores(tmpTrustCerts)
      val tm = loadTrustManager(keyStore)
        this.trustCertificateManager = ReloadableTrustManager.Manager(tmpTrustCerts,certsOfDeleted,keyStore,tm)
        //发送更新给systemcertList和SignTool
        ctx.getSystemCertList.updateCertList(tmpTrustCerts.keySet.toArray)
        ctx.getSignTool.updateCertList(tmpTrustCerts)
        //shutdown 被删除的节点
        if(certsOfDeleted.length > 0){
          ctx.shutDownNode(certsOfDeleted)
        }
        this.isUpdateTrustCerts.set(true)
        RepLogger.trace(RepLogger.System_Logger, "ReloadableTrustManager 装载更新数据，certs="+tmpTrustCerts.mkString(","))
      })
    }catch {
      case ex:Exception=>
        RepLogger.trace(RepLogger.System_Logger, "ReloadableTrustManager 装载更新数据异常，msg="+ex.getMessage)
    }
  }

  private def findDeleteCerts(recentCerts:HashMap[String,Certificate],oldCerificates:HashMap[String,Certificate]): Array[String] ={
    val delList = new ArrayBuffer[String]()
    if(oldCerificates != null){
      oldCerificates.keySet.foreach(k=>{
        if(!recentCerts.contains(k)){
          delList += k
        }
      })
    }
    delList.toArray
  }

  private def loadTrustStores(recentCerts:HashMap[String,Certificate]):KeyStore = {
    try{
      val Store = KeyStore.getInstance(KeyStore.getDefaultType())
      Store.load(null, null)
      recentCerts.foreach(f => {
        val k = f._1
        val cert = f._2
        Store.setCertificateEntry(k, cert);
      })
      Store
    }catch {
      case e:KeyStoreException=>
        throw e
    }
  }

  private def loadTrustManager(recentStore:KeyStore) : X509ExtendedTrustManager= {
    var rtm : X509ExtendedTrustManager= null
    val tmf: TrustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
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
  ///////////////////////信任证书装载--完成/////////////////////////////////////////////////////////////////////

  def isCheckValid: Boolean = {
    checkValid.get()
  }

  def setCheckValid(checkValid: Boolean): Unit = {
    this.checkValid.set(checkValid)
  }

  def notifyTrustChange(data:HashMap[String, Array[Byte]]): Unit = {
    try{
      val thread = new Thread(new UpdateThread(data))
      thread.start()
    }catch {
      case ex:Exception=>
        RepLogger.trace(RepLogger.System_Logger, "ReloadableTrustManager 通知更新异常，msg="+ex.getMessage)
    }

  }

}

object ReloadableTrustManager {
  case class Manager(trustCertificates:HashMap[String, Certificate],certificatesOfDeleted:Array[String],
                     trustKeyStore:KeyStore,trustManager: X509ExtendedTrustManager)
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



