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

package rep.utils

import org.scalatest._
import org.scalatest._
import prop._
import scala.collection.immutable._
import scala.math.BigInt
import com.google.protobuf.ByteString
import SerializeUtils._
/**对操作日志进行JSon的序列化和反序列化测试
 * @author c4w
 * 
 */
class SerializeSpec extends PropSpec with TableDrivenPropertyChecks with Matchers {/*
  val ol = List(OperLog("key1",null,ByteString.copyFrom(BigInt(8).toByteArray)),
      OperLog("key1",ByteString.copyFrom(BigInt(8).toByteArray),null))
  val tr = TransactionResult("txid-0001",  ol, Some(new ActionResult(1)))
  
  val examples =
    Table(
    "v",  // First tuple defines column names
    "hello world",
    666,
    true,
    Map("red" -> "#FF0000", "azure" -> "#F0FFFF")
   )
  property("Any value can be serialise and deserialise") {
    forAll(examples) { (v:Any) =>
      val b1 = serialise(v)
      val v1 = deserialise(b1)
      v1 should equal (v)
   }       
  }
  val e2 =
    Table(
    "v",  // First tuple defines column names
     ol
   )
  property("Any value can be serialise and deserialise") {
    forAll(e2) { (v:Any) =>
      val b1 = v.asInstanceOf[TransactionResult]
      val v1 = b1.toByteArray
      val v2 = TransactionResult.parseFrom(v1)
      println(v2.toString())
   }       
  }
  
  */
}