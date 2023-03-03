package rep.storage.encrypt.imp

import rep.storage.encrypt.IEncrypt

/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-08
 * @category	默认实现落盘加密接口
 * */
class ImpEncrypt extends IEncrypt {
  override def encrypt(plaintext: Array[Byte]): Array[Byte] = {
    plaintext
  }

  override def decrypt(cipherText: Array[Byte]): Array[Byte] = {
    cipherText
  }
}
