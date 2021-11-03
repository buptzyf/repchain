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

package rep.sc.tpl

import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse
import rep.protos.peer.ActionResult
import rep.sc.scalax.{ContractContext, IContract}

final case class ProofDataSingle(key: String, value: String)
class ParallelPutProofTPL extends IContract{

  type proofDataMap = Map[String, Any]
  val split = "_"

  override def init(ctx: ContractContext): Unit = {
    println(s"tid: $ctx.t.id")
  }

  /**
    * 存多参数
    * @param ctx
    * @param data
    * @return
    */
  def putProof(ctx: ContractContext, data: proofDataMap): ActionResult = {
    data.foreach(
      entry => {
        ctx.api.setVal(ctx.t.id + split + entry._1,entry._2)
        print("putProof:"+ entry._1 + ":" + entry._2)
      }
    )
    null
  }

  /**
    * 存单参数
    * @param ctx
    * @param data
    * @return
    */
  def putProof(ctx: ContractContext, data: ProofDataSingle): ActionResult = {
    ctx.api.setVal(ctx.t.id + split + data.key, data.value)
    print("putProof:"+ data.key + ":" + data.value)
    null
  }


  override def onAction(ctx: ContractContext, action: String, sdata: String): ActionResult = {

    implicit val formats = DefaultFormats
    //println("-----------"+sdata)
    val json = parse(sdata)

    action match {
      case "putProofSingle" =>
        println("-----------"+sdata)
        putProof(ctx, json.extract[ProofDataSingle])
      case "putProofMap" =>
        putProof(ctx, json.extract[proofDataMap])
    }

  }

}
