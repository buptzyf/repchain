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

import SerializeUtils._
/**对操作日志进行JSon的序列化和反序列化测试
 * @author c4w
 * 
 */
class SerializeSpec extends PropSpec with TableDrivenPropertyChecks with Matchers {
  val ol = List(Oper("key1",null,BigInt(8).toByteArray),Oper("key1",BigInt(8).toByteArray,BigInt(8).toByteArray))
  
  val examples =
    Table(
    "v",  // First tuple defines column names
    "hello world",
    666,
    true,
    Map("red" -> "#FF0000", "azure" -> "#F0FFFF")
     //ol
   )
  property("Any value can be serialise and deserialise") {
    forAll(examples) { (v:Any) =>
      val b1 = serialise(v)
      val b2 = serialiseJson(v)
      println(s"type:${v.getClass().getName} len_ser:${b1.length}  len_json:${b2.length}")
      val v1 = deserialise(b1)
      val v2 = deserialiseJson(b2)
      v1 should equal (v)
      v2 should equal (v)
   }
   
   val len = 100000
   val s1 = System.currentTimeMillis()
   for( a <- 1 to len){
      forAll(examples) { (v:Any) =>
        val b1 = serialise(v)
        val v1 = deserialise(b1)
     }
    }
   val s2 = System.currentTimeMillis()
   for( a <- 1 to len){
      forAll(examples) { (v:Any) =>
        val b1 = serialiseJson(v)
        val v1 = deserialiseJson(b1)
     }
    }
   val s3 = System.currentTimeMillis()
   
   println(s"tser:${s2-s1} tjson:${s3-s2}")
       
  }
}