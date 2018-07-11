/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Fintech Research Center of ISCAS.
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
 */

package rep.crypto

import rep.crypto.BytesHex.bytes2hex

import org.scalatest.prop.{GeneratorDrivenPropertyChecks, PropertyChecks}
import org.scalatest.{Matchers, PropSpec}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global



trait HashTest extends PropSpec
with PropertyChecks
with GeneratorDrivenPropertyChecks
with Matchers {
  val emptyBytes: Array[Byte] = Array.empty

  def hashCheckString(hash: CryptographicHash, external: Map[String, String]): Unit =
    hashCheck(hash, external.map(x => (x._1.getBytes -> x._2)))

  def hashCheck(hash: CryptographicHash, external: Map[Array[Byte], String]): Unit = {

    property(s"${hash.getClass.getSimpleName} size of hash should be DigestSize") {
      forAll { data: Array[Byte] =>
        hash.hash(data).length shouldBe hash.DigestSize
      }
    }

    property(s"${hash.getClass.getSimpleName} no collisions") {
      forAll { (x: Array[Byte], y: Array[Byte]) =>
        whenever(!x.sameElements(y)) {
          hash.hash(x) should not equal hash.hash(y)
        }
      }
    }

    property(s"${hash.getClass.getSimpleName} comparing with externally computed value") {
      external.foreach { m =>
        bytes2hex(hash.hash(m._1)) shouldBe m._2.toLowerCase
      }
    }

    property(s"${hash.getClass.getSimpleName} is thread safe") {
      val singleThreadHashes = (0 until 100).map(i => hash.hash(i.toString))
      val multiThreadHashes = Future.sequence((0 until 100).map(i => Future(hash.hash(i.toString))))
      singleThreadHashes.map(bytes2hex(_)) shouldBe Await.result(multiThreadHashes, 1.minute).map(bytes2hex(_))
    }

    property(s"${hash.getClass.getSimpleName} apply method") {
      forAll { (string: String, bytes: Array[Byte]) =>
        hash(string) shouldEqual hash.hash(string)
        hash(string) shouldEqual hash(string.getBytes)
        hash(bytes) shouldEqual hash.hash(bytes)
      }
    }
  }

}
