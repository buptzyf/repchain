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

package rep.crypto.cert

import java.security.{PrivateKey,PublicKey}
import java.security.cert.{ Certificate, CertificateFactory }


/**
 * 系统签名相关提交给外部的接口，第三方使用不需要直接调用该类
 * @author jiangbuyun
 * @version	1.0
 */

trait ISigner {
  def sign(privateKey: PrivateKey, message: Array[Byte]): Array[Byte] 
  def verify(signature: Array[Byte], message: Array[Byte], publicKey: PublicKey): Boolean
  def CertificateIsValid(date:java.util.Date,  cert:Certificate):Boolean
}