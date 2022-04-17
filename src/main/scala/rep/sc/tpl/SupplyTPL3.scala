

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

import scala.collection.mutable.Map
import org.json4s.native.Serialization.{read, write}
import org.json4s.{DefaultFormats, Formats, jackson}
import rep.proto.rc2.ActionResult
import rep.sc.tpl.SupplyType._
import rep.sc.scalax.IContract
import rep.sc.scalax.ContractContext

/**
  * 供应链分账合约
  */
class SupplyTPL3 extends IContract {

  val SPLIT_CHAR = "_"
  val TPL_MODE = "_PM"
  implicit val formats = DefaultFormats


  def init(ctx: ContractContext) {
    println(s"tid: $ctx.t.id")
  }

  /**
    * 设计方、原料方、生产方、销售方 签订对销售额的分成合约, 对于销售方账号+产品型号决定唯一的分账合约
    */
  def signShare(ctx: ContractContext, data: IPTSignShare): ActionResult = {
    val sid = data.account_sale + SPLIT_CHAR + data.product_id
    val pid = sid + TPL_MODE
    //签约输入持久化,默认的类型转换无法胜任，以json字符串形式持久化
    ctx.api.setVal(sid, write(data))
    ctx.api.setVal(pid, TPL.Share)
    null
  }

  def signFixed(ctx: ContractContext, data: IPTSignFixed): ActionResult = {
    val sid = data.account_sale + SPLIT_CHAR + data.product_id
    val pid = sid + TPL_MODE
    //签约输入持久化
    ctx.api.setVal(sid, write(data))
    ctx.api.setVal(pid, TPL.Fixed)
    null
  }

  /**
    * 分账的调度方法，负责根据调用相应的分账模版, 传入模版定制参数和销售数据,进行分账
    */
  def split(ctx: ContractContext, data: IPTSplit): ActionResult = {
    //根据销售方账号和产品Id获得分账脚本
    val sid = data.account_sale + SPLIT_CHAR + data.product_id
    val pid = sid + TPL_MODE
    val tm = ctx.api.getVal(pid).asInstanceOf[String]

    //根据签约时选择的分账方式模版,验证定制参数
    val mr = tm match {
      case TPL.Share =>
        val sp0: Any = ctx.api.getVal(sid)
        val sp = read[IPTSignShare](ctx.api.getVal(sid).asInstanceOf[String])
        splitShare(data.amount, sp.account_remain, sp.tpl_param)
      case TPL.Fixed =>
        val sp = read[IPTSignFixed](ctx.api.getVal(sid).asInstanceOf[String])
        splitFixedRatio(data.amount, sp.account_remain, sp.ratio)
    }
    //返回分账计算结果
    addToAccount(ctx, mr)
    null
  }

  /**
    * 将分账结果增加到账户并持久化
    */
  def addToAccount(ctx: ContractContext, mr: Map[String, Int]) {
    for ((k, v) <- mr) {
      val sk: Any = ctx.api.getVal(k)
      var dk = if (sk == null) 0 else sk.toString.toInt
      ctx.api.setVal(k, dk + v)
    }
  }

  /**
    * 合约方法入口
    */
  def onAction(ctx: ContractContext, action: String, sdata: String): ActionResult = {
    val json = parse(sdata)

    action match {
      case ACTION.SignShare =>
        signShare(ctx, json.extract[IPTSignShare])
      //for test 合约升级变体
      //case ACTION.SignFixed =>
      //  signFixed(ctx,json.extract[IPTSignFixed])
      case ACTION.Split =>
        split(ctx, json.extract[IPTSplit])
    }
  }

  /**
    * 内部函数, 获得分阶段的分成
    */
  def getShare(sr: Int, ar: Array[ShareRatio]): Int = {
    var rv = 0
    for (el <- ar) {
      //击中金额范围
      if (sr > el.from && sr <= el.to) {
        //固定金额
        if (el.fixed > 0)
          rv = el.fixed
        else //按比例分成
          rv = (sr * el.ratio).toInt
      }
    }
    rv
  }

  /**
    * 合约中内置多种分账模版，签约时可选择模版,如果出现新的分账模版，则部署一版新的合约
    * 分成模型, 除了销售方之外, 其他各方要求一个最低金额，分成按照金额阶段有所不同。
    */
  def splitShare(sr: Int, account_remain: String, rule: ShareMap): Map[String, Int] = {
    //分账结果
    val rm: Map[String, Int] = Map()
    //分账余额
    var remain = sr
    for ((k, v) <- rule) {
      val rv = getShare(sr, v)
      rm += (k -> rv)
      remain -= rv
    }
    rm += (account_remain -> remain)
  }


  /**
    * 各方固定比例分成，此模版仅仅为了合约对多模版的支持，可能无实际用途
    */
  def splitFixedRatio(sr: Int, account_remain: String, mr: FixedMap): Map[String, Int] = {
    val rm: Map[String, Int] = Map()
    var remain = sr
    //根据固定分成
    for ((k, v) <- mr) {
      val rv = (sr * v).toInt
      rm += (k -> rv)
      remain -= rv
    }
    //剩余的分给指定的余额账户
    rm += (account_remain -> remain)
  }
}