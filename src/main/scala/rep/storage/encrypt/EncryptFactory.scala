package rep.storage.encrypt

import rep.storage.encrypt.imp.ImpEncrypt

/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-08
 * @category	获取落盘加密接口实现类
 * */
object EncryptFactory {
  def getEncrypt:IEncrypt={
    synchronized{
      new ImpEncrypt
    }
  }
}
