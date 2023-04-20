package rep.storage.encrypt.imp

import rep.storage.encrypt.IEncrypt

class ImpEncryptWithSM4 (enKey:String,keyServer:String) extends IEncrypt{
  override def encrypt(plaintext: Array[Byte]): Array[Byte] = ???

  override def decrypt(cipherText: Array[Byte]): Array[Byte] = ???
}
