package rep.crypto.cert


import java.security._
import javax.net.ssl.SSLContext
import rep.app.system.RepChainSystemContext
import rep.log.RepLogger

class CryptoMgr(ctx:RepChainSystemContext) {
  private val config = ctx.getConfig
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
    if(config.isUseGM){
      MessageDigest.getInstance(Alg4HashInGM)
    }else{
      MessageDigest.getInstance(Alg4HashInDefault)
    }
  }

  def getHashAlgType : String = {
    checkProvider
    if(config.isUseGM){
      Alg4HashInGM
    }else{
      Alg4HashInDefault
    }
  }

  def getSignAlgType : String = {
    checkProvider
    if(config.isUseGM){
      Alg4SignInGM
    }else{
      Alg4SignInDefault
    }
  }

  def getSignaturer : Signature = {
    checkProvider
    if(config.isUseGM){
      Signature.getInstance(Alg4SignInGM)
    }else{
      Signature.getInstance(Alg4SignInDefault)
    }
  }

  def getKeyStorer : KeyStore = {
    checkProvider
    if(config.isUseGM){
      KeyStore.getInstance(keyStoreTypeInGM,config.getGMProviderNameOfJCE)
    }else{
      KeyStore.getInstance(KeyStore.getDefaultType)
    }
  }

  def getKeyFileSuffix : String = {
    var rel = ".jks"
    if(config.isUseGM){
      rel = ".pfx"
    }
    rel
  }

  private def checkProvider={
    if(!isLoadProvider){
      synchronized {
        try{
          if(config.isUseGM){
            val p = Security.getProvider(config.getGMProviderNameOfJCE)
            if(p == null){
              loaderProvider(config.getGMProviderOfJCE)
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
