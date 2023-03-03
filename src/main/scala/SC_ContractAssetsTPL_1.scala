
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

import org.json4s._
import org.json4s.jackson.JsonMethods._
import rep.proto.rc2.ActionResult
import rep.sc.scalax.IContract
import rep.sc.scalax.ContractContext
import rep.sc.scalax.ContractException
import rep.sc.tpl.did.DidTplPrefix.signerPrefix
import rep.utils.IdTool

/**
  * 资产管理合约
  */

final case class SC_ContractAssetsTPL_1Transfer(from: String, to: String, amount: Int)

class SC_ContractAssetsTPL_1 extends IContract{

  // 需要跨合约读账户
  var chaincodeName = ""
  var chaincodeVersion = 0
  //val prefix = IdTool.getCid(ChaincodeId(chaincodeName, chaincodeVersion))

  implicit val formats = DefaultFormats

  def init(ctx: ContractContext) {
    chaincodeName = ctx.api.getAccountContractCodeName
    chaincodeVersion = ctx.api.getAccountContractVersion
    println(s"tid: $ctx.t.id")
  }

  def set(ctx: ContractContext, data: Map[String, Int]): ActionResult = {
    println(s"set data:$data")
    for ((k, v) <- data) {
      ctx.api.setVal(k, v)
    }
    null
  }

  def transfer(ctx: ContractContext, data:SC_ContractAssetsTPL_1Transfer): ActionResult = {
    //System.err.println(s"data.from=${data.from}")
    //System.err.println(s"ctx.t.getSignature.getCertId.creditCode=${ctx.t.getSignature.getCertId.creditCode}")
    //val tmp = ctx.t.getSignature.getCertId.creditCode
    //val creditCode = tmp.substring(tmp.indexOf(IdTool.DIDPrefixSeparator)+1)
    if (!data.from.equals(ctx.t.getSignature.getCertId.creditCode))
      throw ContractException("只允许从本人账户转出")
    val signerKey = signerPrefix + data.to
    // 跨合约读账户，该处已经反序列化
    if (ctx.api.getStateEx(ctx.api.getChainNetId, chaincodeName, signerKey) == null)
      throw ContractException("目标账户不存在")
    val sfrom: Any = ctx.api.getVal(data.from)
    val dfrom = sfrom.asInstanceOf[Int]
    if (dfrom < data.amount)
      throw ContractException("余额不足")
    ctx.api.setVal(data.from, dfrom - data.amount)
    val dto = ctx.api.getVal(data.to).toString.toInt
    ctx.api.setVal(data.to, dto + data.amount)
    null
  }

  def put_proof(ctx: ContractContext, data: Map[String, Any]): ActionResult = {
    //先检查该hash是否已经存在,如果已存在,抛异常
    for ((k, v) <- data) {
      val pv0: Any = ctx.api.getVal(k)
      if (pv0 != null)
        throw ContractException(s"[$k] 已存在，当前值为 [$pv0]")
      ctx.api.setVal(k, v)
      print("putProof:" + k + ":" + v)
    }
    null
  }

  /**
    * 根据action,找到对应的method，并将传入的json字符串parse为method需要的传入参数
    */
  def onAction(ctx: ContractContext, action: String, sdata: String): ActionResult = {
    val json = parse(sdata)
    action match {
      case "transfer" =>
        transfer(ctx, json.extract[SC_ContractAssetsTPL_1Transfer])
      case "set" =>
        set(ctx, json.extract[Map[String, Int]])
      case "putProof" =>
        put_proof(ctx, json.extract[Map[String, Any]])
    }
  }

}
