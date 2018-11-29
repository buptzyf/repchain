/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
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
import rep.sc.Shim.Oper

import org.scalatest._
import prop._
import scala.collection.immutable._

import org.scalatest._
import prop._
import scala.collection.immutable._
import scala.math.BigInt

/**对操作日志进行JSon的序列化和反序列化测试
 * @author c4w
 * 
 */
class Json4sSpec extends PropSpec with TableDrivenPropertyChecks with Matchers {
  val ol1 = List(Oper("key1",null,BigInt(8).toByteArray),Oper("key1",BigInt(8).toByteArray,BigInt(8).toByteArray))
  val ol2 = List(Oper("key2",null,BigInt(8).toByteArray),Oper("key2",BigInt(8).toByteArray,BigInt(8).toByteArray))
  
  val examples =
    Table(
    "olist",  // First tuple defines column names
    ol1,ol2
   )
  property("Oper list can be convert to json and from json") {
    forAll(examples) { olist =>
      val jstr = Json4s.compactJson(olist)
      val jobj = Json4s.parseJson[List[Oper]](jstr)
      jobj.head.key should be( olist.head.key)
      jobj.head.newValue should be (olist.head.newValue)
   }
  }
}