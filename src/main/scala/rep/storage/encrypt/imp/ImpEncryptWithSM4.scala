package rep.storage.encrypt.imp

import org.apache.commons.codec.binary.Base64
import rep.storage.encrypt.{IEncrypt, KeyServer}
import javax.crypto.KeyGenerator
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import org.bouncycastle.jce.provider.BouncyCastleProvider


class ImpEncryptWithSM4 (enKey:String,keyServer:String) extends IEncrypt{
  private val ALGORITHM_NAME = "SM4"
  private val ALGORITHM_ECB_PKCS5PADDING = "SM4/ECB/PKCS5Padding"
  private val DEFAULT_KEY_SIZE = 128

  private var key : SecretKeySpec = null
  private var cipher_en: Cipher = null
  private var cipher_de: Cipher = null

  InitSecurity

  private def InitSecurity: Unit = {
    synchronized(
      if (null == Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)) {
        Security.addProvider(new BouncyCastleProvider())
      }
    )

    try {
      val keyValue = KeyServer.getKey(enKey, keyServer)
      val kg = KeyGenerator.getInstance(ALGORITHM_NAME, BouncyCastleProvider.PROVIDER_NAME)
      val random = SecureRandom.getInstance("SHA1PRNG")
      random.setSeed(keyValue.getBytes())
      kg.init(DEFAULT_KEY_SIZE,random )
      val secretKey = kg.generateKey
      key = new SecretKeySpec(secretKey.getEncoded, ALGORITHM_NAME)
      cipher_en = Cipher.getInstance(ALGORITHM_ECB_PKCS5PADDING, BouncyCastleProvider.PROVIDER_NAME)
      cipher_de = Cipher.getInstance(ALGORITHM_ECB_PKCS5PADDING, BouncyCastleProvider.PROVIDER_NAME)
    } catch {
      case e: Exception => throw new RuntimeException(e)
    }
  }

  override def encrypt(plaintext: Array[Byte]): Array[Byte] = {
    try {
      if (this.cipher_en == null || this.key == null) {
        throw new RuntimeException("Encryption component initialization failed.")
      }
      cipher_en.init(Cipher.ENCRYPT_MODE, key)
      cipher_en.doFinal(plaintext)
    } catch {
      case e: Exception => throw new RuntimeException(e)
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

object ImpEncryptWithSM4Test extends App{
  val message: String = "hello,sm4!"
  val sm1 = new ImpEncryptWithSM4("AA345556785731D43B7F973AC76A1D38", "localhost:5001")
  val cs1 = sm1.encrypt(message.getBytes())
  val ps1 = sm1.decrypt(cs1)
  System.out.println("smtring1=" + new String(ps1))
  val sm2 = new ImpEncryptWithSM4("AA345556785731D43B7F973AC76A1D38", "localhost:5001")
  val ps2 = sm2.decrypt(cs1)
  val pwd = "tswocbYY1_@_lb"
  val ps = Base64.encodeBase64String(sm1.encrypt(pwd.getBytes()))
  System.out.println("smtring2=" + new String(ps2))
  System.out.println("pwdentrypt=" + ps)
}