package rep.crypto.cert

import java.io.{ByteArrayInputStream, File, FileInputStream, StringReader}
import java.security.KeyStore
import java.security.cert.{Certificate, CertificateFactory, X509Certificate}
import org.bouncycastle.util.io.pem.PemReader
import rep.app.system.RepChainSystemContext
import rep.crypto.nodedynamicmanagement.ReloadableTrustManager
import rep.crypto.nodedynamicmanagement4gm.GMJsseContextHelper
import rep.log.RepLogger
import rep.storage.chain.KeyPrefixManager
import rep.storage.db.factory.DBFactory
import rep.utils.{IdTool, SerializeUtils}
import scala.collection.mutable.HashMap

object CertificateUtil {

  def loadTrustCertificate(ctx:RepChainSystemContext):HashMap[String, Certificate] = {
    var tmpMap = new HashMap[String, Certificate]()

    val db = DBFactory.getDBAccess(ctx.getConfig)
    val keyValue = db.getObject[HashMap[String, Array[Byte]]](
      KeyPrefixManager.getWorldStateKey(ctx.getConfig, ReloadableTrustManager.key_trust_stores,
        ctx.getConfig.getMemberManagementContractName))
    if (keyValue == None) {
      tmpMap = loadTrustCertificateFromTrustFile(ctx)
    } else {
      val certFactory: CertificateFactory = CertificateFactory.getInstance("X.509")
      val keyMap = keyValue.getOrElse(null)
      keyMap.foreach(item => {
        val k = item._1
        val v = item._2
        val nodeCert: Certificate = certFactory.generateCertificate(new ByteArrayInputStream(v))
        tmpMap(k) = nodeCert
      })
      RepLogger.trace(RepLogger.System_Logger, "CertificateUtil 从DB装载信任证书="+tmpMap.mkString(","))
    }
    tmpMap
  }

  def loadTrustCertificateFromBytes(keyMap:HashMap[String, Array[Byte]]):HashMap[String, Certificate] = {
    val tmpMap = new HashMap[String, Certificate]()
    val certFactory: CertificateFactory = CertificateFactory.getInstance("X.509")
    keyMap.foreach(item => {
      val k = item._1
      val v = item._2
      val nodeCert: Certificate = certFactory.generateCertificate(new ByteArrayInputStream(v))
      tmpMap(k) = nodeCert
    })
    RepLogger.trace(RepLogger.System_Logger, "CertificateUtil 从字节数据装载信任证书="+tmpMap.mkString(","))

    tmpMap
  }


  private def loadTrustCertificateFromTrustFile(ctx:RepChainSystemContext): HashMap[String, Certificate] = {
    val tmpMap = new HashMap[String, Certificate]()
    val fis = new FileInputStream(new File(ctx.getConfig.getTrustStore))
    val pwd = ctx.getConfig.getTrustPassword.toCharArray()

    var trustKeyStore : KeyStore = null
    if(ctx.getConfig.isUseGM){
      trustKeyStore = GMJsseContextHelper.loadKeystore(ctx.getConfig.getTrustStore,ctx.getConfig.getTrustPassword,ctx.getConfig.getGMProviderNameOfJCE)
    }else{
      trustKeyStore = KeyStore.getInstance(KeyStore.getDefaultType())
      trustKeyStore.load(fis, pwd)
    }

    val enums = trustKeyStore.aliases()
    while (enums.hasMoreElements) {
      var alias = enums.nextElement()
      val cert = trustKeyStore.getCertificate(alias)
      ///todo 信任证书列表有问题，包含了文件名的后缀
      if(alias.indexOf(".cer") > 0){
        alias = alias.substring(0,alias.indexOf(".cer"))
      }
      //////////////////////////////////////////
      tmpMap(alias)=cert
    }
    RepLogger.trace(RepLogger.System_Logger, "CertificateUtil 在文件中装载信任证书="+tmpMap.mkString(","))

    if(!tmpMap.isEmpty){
      writeTrustCertificateToDB(tmpMap,ctx)
    }
    tmpMap
  }

  private def writeTrustCertificateToDB(tmpMap:HashMap[String, Certificate],ctx:RepChainSystemContext): Unit = {
    val map = new HashMap[String, Array[Byte]]
    tmpMap.foreach(f => {
      val alias = f._1
      val cert = f._2
      val pemReader = new PemReader(new StringReader(IdTool.toPemString(cert.asInstanceOf[X509Certificate])))
      val certBytes = pemReader.readPemObject().getContent
      map(alias) = certBytes
    })
    if (!map.isEmpty) {
      val db = DBFactory.getDBAccess(ctx.getConfig)
      db.putBytes(KeyPrefixManager.getWorldStateKey(ctx.getConfig, ReloadableTrustManager.key_trust_stores,
        ctx.getConfig.getMemberManagementContractName), SerializeUtils.serialise(map))
      RepLogger.trace(RepLogger.System_Logger, "CertificateUtil 将信任证书写入到DB="+tmpMap.mkString(","))
    }
  }
}
