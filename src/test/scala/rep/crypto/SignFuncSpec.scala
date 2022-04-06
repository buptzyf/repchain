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

import org.scalatest.prop.{GeneratorDrivenPropertyChecks, PropertyChecks}
import org.scalatest.{Matchers, PropSpec}
import rep.crypto._
import java.io._


/** 签名和验证签名的测试
 *  @author c4w
 * 
 */
class SignFuncSpec extends PropSpec
with PropertyChecks
with GeneratorDrivenPropertyChecks
with Matchers {

  import java.util.Arrays
  
  property("signed message should be verifiable with appropriate public key") {
    forAll { (seed1: Array[Byte], seed2: Array[Byte],
              message1: Array[Byte], message2: Array[Byte]) =>
      whenever(!seed1.sameElements(seed2) && !message1.sameElements(message2)) {
        //c4w for keypair from jks
       /* val (skey1,pkey1) = ECDSASign.getKeyPairFromJKS(new File(s"${CryptoMgr.getKeyFileSuffix.substring(1)}/mykeystore_1${CryptoMgr.getKeyFileSuffix}"),"123","1")
        val (skey2,pkey2) = ECDSASign.getKeyPairFromJKS(new File(s"${CryptoMgr.getKeyFileSuffix.substring(1)}/mytruststore${CryptoMgr.getKeyFileSuffix}"),"changeme","1")
        val (skey3,pkey3) = ECDSASign.getKeyPairFromJKS(new File(s"${CryptoMgr.getKeyFileSuffix.substring(1)}/mytruststore${CryptoMgr.getKeyFileSuffix}"),"changeme","2")
        
        val sig = ECDSASign.sign(skey1, message1)
        
        val m1 = "hello repChain"
        val sig2 = Arrays.toString(ECDSASign.sign(skey1, m1.getBytes))
        val sig3 = Arrays.toString(ECDSASign.sign(skey1, m1.getBytes))
        
        
        ECDSASign.verify(sig, message1, pkey1) should be (true)
        ECDSASign.verify(sig, message1, pkey2) should be (true)
        ECDSASign.verify(sig, message2, pkey3) shouldNot be (true)
        (pkey1 == pkey2) should be (true)
        (pkey1 == pkey3) shouldNot be (true)
*/
      }
    }
  }
}