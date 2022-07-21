package rep.crypto.nodedynamicmanagement

import java.security.{KeyStore, KeyStoreException}
import java.security.cert.Certificate
import java.util.concurrent.ConcurrentHashMap

import javax.net.ssl.{TrustManager, TrustManagerFactory, X509ExtendedTrustManager}
import rep.app.system.RepChainSystemContext
import rep.crypto.cert.{CertificateUtil, CryptoMgr}
import rep.log.RepLogger

import scala.collection.mutable.{ArrayBuffer, HashMap}
import scala.util.control.Breaks.{break, breakable}

class ReloadableTrustManager private(ctx: RepChainSystemContext){
  private var proxy : X509TrustManagerProxy = null
  private var trustCertificates: HashMap[String, Certificate] = new HashMap[String, Certificate]()
  private val lock: Object = new Object
  initializa


  private def initializa:Unit={
    loadUpdateTrustCertificateManager(null)
  }

  def getProxyInstance:TrustManager={
    this.proxy.Wrapper()
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

  class UpdateThread(updateCertInfo: HashMap[String, Array[Byte]]) extends Runnable {
    override def run(): Unit = {
      try {
        loadUpdateTrustCertificateManager(updateCertInfo)
      } catch {
        case ex: Exception =>
          RepLogger.trace(RepLogger.System_Logger, "ReloadableTrustManager 接收更新线程异常，msg=" + ex.getMessage)
      }

    }
  }

  ///////////////////////信任证书装载/////////////////////////////////////////////////////////////////////
  private def loadUpdateTrustCertificateManager(updateCertInfo: HashMap[String, Array[Byte]]): Unit = {
    this.lock.synchronized({
      try {

        var oldCertificates: HashMap[String, Certificate] = null
        if (trustCertificates != null && !trustCertificates.isEmpty) {
          oldCertificates = trustCertificates
        }
        val tmpTrustCerts = if (updateCertInfo == null)
          CertificateUtil.loadTrustCertificate(ctx)
        else
          CertificateUtil.loadTrustCertificateFromBytes(updateCertInfo)
        val certsOfDeleted = findDeleteCerts(tmpTrustCerts, oldCertificates)
        val keyStore = loadTrustStores(tmpTrustCerts)
        val tm = loadTrustManager(keyStore)

        //发送更新给systemcertList和SignTool
        //ctx.getSystemCertList.updateCertList(tmpTrustCerts.keySet.toArray)
        ctx.getSignTool.updateCertList(tmpTrustCerts)
        //shutdown 被删除的节点
        if (certsOfDeleted.length > 0) {
          ctx.shutDownNode(certsOfDeleted)
        }
        this.trustCertificates = tmpTrustCerts
        if(this.proxy == null){
          this.proxy = new X509TrustManagerProxy(ctx.getSystemName,tm)
        }else{
          this.proxy.setTarget(tm)
        }
        RepLogger.trace(RepLogger.System_Logger, "ReloadableTrustManager 装载更新数据，certs=" + tmpTrustCerts.mkString(","))
      } catch {
        case ex: Exception =>
          RepLogger.trace(RepLogger.System_Logger, "ReloadableTrustManager 装载更新数据异常，msg=" + ex.getMessage)
      }
    })
  }

  private def findDeleteCerts(recentCerts: HashMap[String, Certificate], oldCerificates: HashMap[String, Certificate]): Array[String] = {
    val delList = new ArrayBuffer[String]()
    if (oldCerificates != null) {
      oldCerificates.keySet.foreach(k => {
        if (!recentCerts.contains(k)) {
          delList += k
        }
      })
    }
    delList.toArray
  }

  private def loadTrustStores(recentCerts: HashMap[String, Certificate]): KeyStore = {
    try {
      val Store = if(ctx.getConfig.isUseGM) KeyStore.getInstance(CryptoMgr.keyStoreTypeInGM,ctx.getConfig.getGMProviderNameOfJCE) else KeyStore.getInstance(KeyStore.getDefaultType())
      Store.load(null, null)
      recentCerts.foreach(f => {
        val k = f._1
        val cert = f._2
        Store.setCertificateEntry(k, cert);
      })
      Store
    } catch {
      case e: KeyStoreException =>
        throw e
    }
  }

  private def loadTrustManager(recentStore: KeyStore): X509ExtendedTrustManager = {
    var rtm: X509ExtendedTrustManager = null
    val tmf: TrustManagerFactory = if(ctx.getConfig.isUseGM) TrustManagerFactory.getInstance("PKIX", ctx.getConfig.getGMJsseProviderName) else TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
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
}
object ReloadableTrustManager{
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