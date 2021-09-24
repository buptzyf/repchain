
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
import rep.app.conf.SystemProfile
import rep.protos.peer.ChaincodeId
import rep.utils.IdTool
import rep.sc.scalax.{ContractContext, ContractException, IContract}
import rep.protos.peer.ActionResult

/**
  * 资产管理合约
  */


final case class Transfer2(from: String, to: String, amount: Int)

class ContractAssetsTPLWithRetract extends IContract {

  // 需要跨合约读账户
  val chaincodeName = SystemProfile.getAccountChaincodeName
  val chaincodeVersion = SystemProfile.getAccountChaincodeVersion
  //val prefix = IdTool.getCid(ChaincodeId(chaincodeName, chaincodeVersion))

  implicit val formats = DefaultFormats

  def init(ctx: ContractContext) {
    println(s"tid: $ctx.t.id")
  }

  def set(ctx: ContractContext, data: Map[String, Int]): ActionResult = {
    println(s"set data:$data")
    for ((k, v) <- data) {
      ctx.api.setVal(k, v)
    }
    null
  }

  /*  def set_tx(ctx: ContractContext, data: Map[String, Transfer2]): ActionResult = {
      println(s"set data:$data")
      for ((k, v) <- data) {
        ctx.api.setVal(k, v)
      }
      null
    }*/

  def transfer(ctx: ContractContext, data: Transfer2): ActionResult = {
    val signerKey = data.to
    // 跨合约读账户，该处并未反序列化
    val sfrom: Any = ctx.api.getVal(data.from)
    var dfrom = sfrom.asInstanceOf[Int]
    if (dfrom < data.amount)
      throw ContractException("余额不足")
    ctx.api.setVal(data.from, dfrom - data.amount)
    var dto = ctx.api.getVal(data.to).toString.toInt
    ctx.api.setVal(data.to, dto + data.amount)
    ctx.api.setVal(ctx.t.id, data) //交易发生时将对应的合约id以及交易的transfer信息写入
    null
  }

  /*def retract(ctx: ContractContext, data: Map[String, Any]): ActionResult = {
    val tid = data.get("retract_tid")
    tid match {
      case Some(id) => {
        val t: Transfer2 = ctx.api.getVal(id.asInstanceOf[String]).asInstanceOf[Transfer2]
        val newData = Transfer2(t.to, t.from, t.amount)
        transfer(ctx, newData)
      }
      case None =>
        println("no retract_id")
      }
    null
    }*/

  def retract(ctx: ContractContext, data: Map[String, String]): ActionResult = {
    val tid: Option[String] = data.get("retract_tid")
    tid match {
      case Some(tid) => {
        val value = ctx.api.getVal(tid)
        if (null == value) {
          println("retract_id为空")
          return null
        }
        val re_t: Transfer2 = value.asInstanceOf[Transfer2]
        val newData = Transfer2(re_t.to, re_t.from, re_t.amount)
        transfer(ctx, newData)
      }
      case None =>
        println("no retract_id")
    }
    null
  }


  def put_proof(ctx: ContractContext, data: Map[String, Any]): ActionResult = {
    //先检查该hash是否已经存在,如果已存在,抛异常
    for ((k, v) <- data) {
      var pv0: Any = ctx.api.getVal(k)
      if (pv0 != null)
      //        throw new Exception("["+k+"]已存在，当前值["+pv0+"]");
        throw ContractException(s"$k 已存在，当前值为 $pv0")
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
        transfer(ctx, json.extract[Transfer2])
      case "set" =>
        set(ctx, json.extract[Map[String, Int]])
      case "putProof" =>
        put_proof(ctx, json.extract[Map[String, Any]])
      case "retract" =>
        retract(ctx, json.extract[Map[String, String]])
    }
  }

}
