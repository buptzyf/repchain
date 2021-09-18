
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

import org.json4s._
import org.json4s.jackson.JsonMethods._
import rep.app.conf.SystemProfile
import rep.protos.peer.ChaincodeId
import rep.utils.IdTool
import rep.sc.scalax.IContract

import rep.sc.scalax.ContractContext
import rep.sc.scalax.ContractException
import rep.protos.peer.ActionResult
import java.util.Base64

class ContractDump extends IContract{

  // 需要跨合约读账户
  val chaincodeName = SystemProfile.getAccountChaincodeName
  val chaincodeVersion = SystemProfile.getAccountChaincodeVersion

  implicit val formats = DefaultFormats

  def init(ctx: ContractContext){
    println(s"tid: $ctx.t.id")
  }

  def set(ctx: ContractContext, data:Map[String,Int]) :ActionResult={
    println(s"set data:$data")
    for((k,v)<-data){
      ctx.api.setVal(k, v)
    }
    null
  }

  def put_proof(ctx: ContractContext, data:Map[String,Any]): ActionResult={
    val sha256str = Base64.getEncoder().encodeToString(ctx.api.HashOfValuesOfPrefix("ContractAssetsTPL"))
    println(s"sha256str:$sha256str")
    ctx.api.setVal("sha256str", sha256str)
    null
  }

  /**
   * 根据action,找到对应的method，并将传入的json字符串parse为method需要的传入参数
   */
  def onAction(ctx: ContractContext,action:String, sdata:String ):ActionResult={
    val json = parse(sdata)
    action match {
      case "set" =>
        set(ctx, json.extract[Map[String,Int]])
      case "putProof" =>
        put_proof(ctx, json.extract[Map[String,Any]])
    }
  }

}
