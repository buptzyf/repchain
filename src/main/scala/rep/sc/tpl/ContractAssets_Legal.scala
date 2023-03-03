
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
import rep.proto.rc2.ActionResult
import rep.sc.scalax.{ContractContext, ContractException, IContract}


/**
  * 资产管理合约
  */

final case class TransferForLegal(from: String, to: String, amount: Int, remind: String)

class ContractAssetsTPL_Legal extends IContract {

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
    new ActionResult(1)
  }

  def transfer(ctx: ContractContext, data: TransferForLegal): ActionResult = {
    if (!data.from.equals(ctx.t.getSignature.getCertId.creditCode))
      return new ActionResult(-1, "只允许从本人账户转出")
    // 跨合约读账户，该处并未反序列化
    //todo 跨合约读需要修改
    /*if (ctx.api.isDidContract) {
      if (ctx.api.getStateEx(chaincodeName, "signer_" + data.to) == null)
        return new ActionResult(-2, "目标账户不存在")
    } else {
      if (ctx.api.getStateEx(chaincodeName, data.to) == null)
        return new ActionResult(-2, "目标账户不存在")
    }*/
    val sfrom: Any = ctx.api.getVal(data.from)
    val dfrom = sfrom.asInstanceOf[Int]
    if (dfrom < data.amount)
      return new ActionResult(-3, "余额不足")

    val rstr =
      s"""确定签名从本人所持有的账户【${data.from}】
          向账户【${data.to}】
          转账【${data.amount}】元"""
    //预执行获得结果提醒
    if (data.remind == null) {
      new ActionResult(2, rstr)
    } else {
      if (!data.remind.equals(rstr))
        new ActionResult(-4, "提醒内容不符")
      else {
        val dto = ctx.api.getVal(data.to).toString.toInt
        ctx.api.setVal(data.from, dfrom - data.amount)
        ctx.api.setVal(data.to, dto + data.amount)
        new ActionResult(1)
      }
    }
  }

  /**
    * 根据action,找到对应的method，并将传入的json字符串parse为method需要的传入参数
    */
  def onAction(ctx: ContractContext, action: String, sdata: String): ActionResult = {
    val json = parse(sdata)
    action match {
      case "transfer" =>
        transfer(ctx, json.extract[TransferForLegal])
      case "set" =>
        set(ctx, json.extract[Map[String, Int]])
    }
  }

}
