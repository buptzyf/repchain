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

package rep.sc
import org.scalatest._
import rep.sc.tpl.SupplyTPL
import prop._
import scala.collection.immutable._
import scala.collection.mutable.Map

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, Formats, jackson}
import org.json4s.native.Serialization.writePretty
import org.json4s.native.Serialization
import org.json4s.native.Serialization.{read, write}


class SplitSpec extends PropSpec with TableDrivenPropertyChecks with Matchers {
  import rep.sc.tpl.SupplyType._
  import rep.sc.tpl.SupplyType.FixedMap
  implicit val formats = DefaultFormats
  //implicit val formats = Serialization.formats(NoTypeHints)
  
  val fm :FixedMap = Map("A" -> 0.2, "B"-> 0.2, "C"-> 0.1, "D" -> 0.1)
  val sm :ShareMap = Map("A" -> Array(new ShareRatio(0,100,0.1,0), new ShareRatio(100,10000,0.15,0)),
      "B" -> Array(new ShareRatio(0,10000,0,10)),
      "C" -> Array(new ShareRatio(0,10000,0.1,0)),
      "D" -> Array(new ShareRatio(0,100,0,10), new ShareRatio(100,10000,0.15,0)))
  val account_remain = "R"
  val account_sales = "S"
  val product_id = "P201806270001"
  
  val s1 = new SupplyTPL

  val examples =
    Table(
      "sr",
      100,
      200,
      500,
      1000
    )    
  property("JSon format works for IPTSignShare") {
    val ipt1 = new IPTSignShare(account_sales,product_id,account_remain,sm)
    val jstr = writePretty(ipt1)
    println(s"${jstr}")
    val ipt2 = read[IPTSignShare](jstr)
    ipt2.account_remain should be (ipt1.account_remain)
    ipt2.tpl_param("A").length should be (ipt1.tpl_param("A").length)
    ipt2.tpl_param("A")(0) should be (ipt1.tpl_param("A")(0))
  }
  property("JSon format works for IPTSignFixed") {
    val ipt1 = new IPTSignFixed(account_sales,product_id,account_remain,fm)
    val jstr = write(ipt1)
    println(s"${jstr}")
    val ipt2 = read[IPTSignFixed](jstr)
    ipt2.account_remain should be (ipt1.account_remain)
    ipt2.ratio should be (ipt1.ratio)
  }
  property("JSon format works for IPTSplit") {
    val ipt1 = new IPTSplit(account_sales,product_id,1000)
    val jstr = write(ipt1)
    println(s"${jstr}")
    val ipt2 = read[IPTSplit](jstr)
    ipt2.product_id should be (ipt1.product_id)
  }
  property(" Splitting sales-revenue by splitFixedRatio") {
    forAll(examples) { sr =>
      val rm = s1.splitFixedRatio(sr, account_remain,fm)
      var total = 0
      print(s"total:${sr}  ")
      for ((k, v) <- rm) {
        total += v
        print(s" ${k}: ${v} ")
      }     
      println("")
      total should be (sr)      
    }
  }
  property(" Splitting sales-revenue by splitShare") {
    forAll(examples) { sr =>
      val rm = s1.splitShare(sr, account_remain,sm)
      var total = 0
      print(s"total:${sr}  ")
      for ((k, v) <- rm) {
        total += v
        print(s" ${k}: ${v} ")
      }     
      println("")
      total should be (sr)      
    }
  }

}