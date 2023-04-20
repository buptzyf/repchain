package rep.storage.encrypt.imp

import org.apache.commons.codec.binary.Base64
import rep.storage.encrypt.{IEncrypt, KeyServer}

import java.security.{Key, SecureRandom}
import javax.crypto.spec.SecretKeySpec
import javax.crypto.{Cipher, KeyGenerator}

class ImpEncryptWithAES(enKey:String,keyServer:String) extends IEncrypt{
  private var key : Key = null
  private var cipher_en : Cipher = null
  private var cipher_de : Cipher = null
  this.InitSecurity()

  private def InitSecurity():Unit={
    val generator = KeyGenerator.getInstance("AES")
    generator.init(128)
    val keyValue : String = KeyServer.getKey(enKey,keyServer)
    try {
      val secureRandom : SecureRandom  = SecureRandom.getInstance("SHA1PRNG")
      secureRandom.setSeed(keyValue.getBytes())
      generator.init(secureRandom)
      val tempKey = generator.generateKey()
      val raw = tempKey.getEncoded
      this.key = new SecretKeySpec(raw, "AES")
      this.cipher_en = Cipher.getInstance("AES")
      this.cipher_de = Cipher.getInstance("AES")
    } catch  {
      case e:Exception => throw new RuntimeException(e)
    }
  }
  override def encrypt(plaintext: Array[Byte]): Array[Byte] = {
    try{
      if(this.cipher_en == null || this.key == null){
        throw new RuntimeException("Encryption component initialization failed.")
      }
      cipher_en.init(Cipher.ENCRYPT_MODE, key)
      cipher_en.doFinal(plaintext)
    }catch {
      case e:Exception => throw new RuntimeException(e)
    }
  }

  override def decrypt(cipherText: Array[Byte]): Array[Byte] = {
    try {
      if (this.cipher_de == null || this.key == null) {
        throw new RuntimeException("Encryption component initialization failed.")
      }
      cipher_de.init(Cipher.DECRYPT_MODE, key)
      cipher_de.doFinal(cipherText)
    } catch {
      case e: Exception => throw new RuntimeException(e)
    }
  }
}

object ImpEncryptWithAESTest{
  def main(args:Array[String]):Unit={
    val message:String = "hello,aes!"
    val aes1 = new ImpEncryptWithAES("sdfsdffsf","localhost:5001")
    val cs1 = aes1.encrypt(message.getBytes())
    val ps1 = aes1.decrypt(cs1)
    System.out.println("destring1="+new String(ps1))
    val aes2 = new ImpEncryptWithAES("sdfsdffsf", "localhost:5001")
    val ps2 = aes2.decrypt(cs1)
    val pwd = "tswocbYY1_@_lb"
    val ps = Base64.encodeBase64String(aes1.encrypt(pwd.getBytes()))
    System.out.println("destring2=" + new String(ps2))
    System.out.println("pwdentrypt=" + ps)
  }
}