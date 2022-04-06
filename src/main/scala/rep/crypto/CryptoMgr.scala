package rep.crypto

import java.io.File
import java.security.{KeyStore, MessageDigest, Provider, Security, Signature}

import akka.actor.Props
import com.typesafe.config.ConfigFactory
import javax.net.ssl.SSLContext
import rep.app.conf.SystemProfile
import rep.log.RepLogger
import rep.network.module.ModuleActorType
import rep.ui.web.EventServer

object CryptoMgr {
  private val Alg4SignInGM : String = "SM3withSM2" //SM3withSM2,SHA256withECDSA
  private val Alg4HashInGM : String = "SM3" //SHA-256,SM3
  private val Alg4SignInDefault : String = "SHA256withECDSA" //SM3withSM2,SHA256withECDSA
  private val Alg4HashInDefault : String = "SHA-256" //SHA-256,SM3
  private val keyStoreTypeInGM : String = "PKCS12"
  private var isLoadProvider : Boolean = false

  private var sslContext: Option[SSLContext] = None
  private var startWebApi:Boolean = false

  def setSslContext(ctx: SSLContext):Unit={
    synchronized(
      if(this.sslContext == None) {
        this.sslContext = Some(ctx)
      }
    )

  }

  def getSslContext:SSLContext={
    synchronized(
      if(this.sslContext != None){
        this.sslContext.get
      }else{
        null
      }
    )
  }

  def getInstance : MessageDigest = {
    checkProvider
    if(SystemProfile.getIsUseGm){
      MessageDigest.getInstance(Alg4HashInGM)
    }else{
      MessageDigest.getInstance(Alg4HashInDefault)
    }
  }

  def getHashAlgType : String = {
    checkProvider
    if(SystemProfile.getIsUseGm){
      Alg4HashInGM
    }else{
      Alg4HashInDefault
    }
  }

  def getSignAlgType : String = {
    checkProvider
    if(SystemProfile.getIsUseGm){
      Alg4SignInGM
    }else{
      Alg4SignInDefault
    }
  }

  def getSignaturer : Signature = {
    checkProvider
    if(SystemProfile.getIsUseGm){
      Signature.getInstance(Alg4SignInGM)
    }else{
      Signature.getInstance(Alg4SignInDefault)
    }
  }

  def getKeyStorer : KeyStore = {
    checkProvider
    if(SystemProfile.getIsUseGm){
      KeyStore.getInstance(keyStoreTypeInGM,SystemProfile.getGmJCEProviderName)
    }else{
      KeyStore.getInstance(KeyStore.getDefaultType)
    }
  }

  def getKeyFileSuffix : String = {
    var rel = ".jks"
    if(SystemProfile.getIsUseGm){
      rel = ".pfx"
    }
    rel
  }

  def loadSystemConfInDebug={
    val userConfFile = new File("conf/system.conf")
    val combined_conf = ConfigFactory.parseFile(userConfFile)
    val final_conf = ConfigFactory.load(combined_conf)
    SystemProfile.initConfigSystem(final_conf,"215159697776981712.node1" )
  }

  private def checkProvider={
    if(!isLoadProvider){
      synchronized {
        try{
          if(SystemProfile.getIsUseGm){
            val p = Security.getProvider(SystemProfile.getGmJCEProviderName)
            if(p == null){
              loaderProvider(SystemProfile.getGmJCEProvider)
              isLoadProvider = true
            }
          }else{
            isLoadProvider = true
          }
        }catch {
          case e:Exception=>
            RepLogger.System_Logger.error("cryptoMgr checkProvider Exception,msg="+e.getMessage)
        }

      }
    }
  }

  private def loaderProvider(gmClassName:String) = {
    try{
      val cls = this.getClass.getClassLoader.loadClass(gmClassName)
      val csts =  cls.getConstructors()
      if(csts.length > 0){
        val cst = csts(0)
        val p = cst.newInstance()
        if(p != null){
          val provider = p.asInstanceOf[Provider]
          Security.addProvider(provider)
          RepLogger.System_Logger.debug(s"crypto's alg use gm,classname=${gmClassName}")
        }
      }
    }catch {
      case e :Exception  =>
        RepLogger.System_Logger.error("cryptoMgr loader Exception,msg="+e.getMessage)
    }
  }
}
