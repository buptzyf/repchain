package rep.storage.encrypt

/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-08
 * @category	数据库落盘加密接口
 * */
trait IEncrypt {
  def encrypt(plaintext:Array[Byte]):Array[Byte]
  def decrypt(cipherText:Array[Byte]):Array[Byte]
}
