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
import java.security.MessageDigest

import com.google.protobuf.ByteString

/**
 * @author c4w
 * modify by jiangbuyun
 */
class Sha256(digest:MessageDigest) extends CryptographicHash{
  override val DigestSize: Int = 32
  //def hash(input: Array[Byte]): Array[Byte] = MessageDigest.getInstance("SHA-256").digest(input)
  def hash(input: Array[Byte]): Array[Byte] = {
    try{
      digest.digest(input)
    }catch {
      case e:Exception =>
        e.printStackTrace()
        digest.reset()
        digest.digest(input)
    }

  }

  def hashstr(input: Array[Byte]):String ={
    BytesHex.bytes2hex(hash(input))
  }

  def hashstr(input: String):String ={
    val iptb = ByteString.copyFromUtf8(input)
    BytesHex.bytes2hex(hash(iptb.toByteArray()))
  }

  def hashToBytes(input: String):Array[Byte] ={
    val iptb = ByteString.copyFromUtf8(input)
    hash(iptb.toByteArray())
  }

}