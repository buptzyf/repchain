package rep.storage.encrypt

import rep.storage.encrypt.imp.{ImpEncryptWithAES, ImpEncryptWithAESTest}
import java.util.concurrent.ConcurrentHashMap

/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-08
 * @category	获取落盘加密接口实现类
 * */
object EncryptFactory {
  private val EncryptInstances = new ConcurrentHashMap[String, IEncrypt]()

  def getEncrypt(isUseGM:Boolean,enKey:String,keyServer:String):IEncrypt={
    var instance: IEncrypt = null
    val encryptName = if(isUseGM) "gm" else "java"

    if (EncryptInstances.containsKey(encryptName)) {
      instance = EncryptInstances.get(encryptName)
    } else {
      instance = if(!isUseGM) new ImpEncryptWithAES(enKey,keyServer) else null
      val old = EncryptInstances.putIfAbsent(encryptName, instance)
      if (old != null) {
        instance = old
      }
    }
    instance
  }
}
