/*
 * Copyright  2019 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BA SIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package rep.crypto

import java.security.{MessageDigest, Security}

import cn.com.tsg.jce.provider.TsgJceProvider
import com.google.protobuf.ByteString
import org.bouncycastle.jce.provider.BouncyCastleProvider

/**
  * @author zyf
  */
// TODO 直接使用java实现
object Sm3 extends CryptographicHash {

  override val DigestSize: Int = 32

  def hash(input: Array[Byte]): Array[Byte] = MessageDigest.getInstance("SM3", "TsgJCE").digest(input)

  def hashstr(input: Array[Byte]): String = {
    BytesHex.bytes2hex(hash(input))
  }

  def hashstr(input: String): String = {
    val iptb = ByteString.copyFromUtf8(input)
    BytesHex.bytes2hex(hash(iptb.toByteArray()))
  }

  def hashToBytes(input: String): Array[Byte] = {
    val iptb = ByteString.copyFromUtf8(input)
    hash(iptb.toByteArray())
  }
}

class Sm3 {

  {
    Security.insertProviderAt(new BouncyCastleProvider(), 1)
    Security.addProvider(new TsgJceProvider())
  }

}
