
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
import rep.proto.rc2.{ActionResult, ChaincodeId}
import rep.sc.scalax.IContract
import rep.sc.scalax.ContractContext
import rep.sc.scalax.ContractException
import rep.sc.tpl.did.DidTplPrefix.signerPrefix
import rep.sc.tpl.ProofDataSingle
import org.json4s.native.Serialization.write
import org.json4s.{DefaultFormats, jackson}
import rep.utils.SerializeUtils

import scala.collection.immutable.HashMap
import scala.collection.mutable

/**
  * 资产管理合约
  */

final case class Transfer(from: String, to: String, amount: Int)

class ContractAssetsTPL extends IContract {

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

  def transfer(ctx: ContractContext, data: Transfer): ActionResult = {
    //System.err.println(s"data.from=${data.from}")
    //System.err.println(s"ctx.t.getSignature.getCertId.creditCode=${ctx.t.getSignature.getCertId.creditCode}")
    //val tmp = ctx.t.getSignature.getCertId.creditCode
    //val creditCode = tmp.substring(tmp.indexOf(IdTool.DIDPrefixSeparator)+1)
    if (!data.from.equals(ctx.t.getSignature.getCertId.creditCode)) {
      throw ContractException("只允许从本人账户转出")
    }

    val signerKey = signerPrefix + data.to
    //发送合约事件到订阅和发送合约事件到日志文件的样例代码
    //implicit val serialization = jackson.Serialization
    //implicit val formats = DefaultFormats
    //var map:Map[String,String] = new HashMap[String,String]()
    //map += "signerKey"-> signerKey
    //val eventString = write(map)
    //ctx.api.sendContractEventToLog("eventString____________")
    //ctx.api.sendContractEventToSubscribe("eventString____________")
    //ctx.api.sendContractEventToLog(eventString)
    //ctx.api.sendContractEventToSubscribe(eventString)
    // 跨合约读账户，该处已经反序列化
    if (ctx.api.getStateEx(ctx.api.getChainNetId, chaincodeName, signerKey) == null)
      throw ContractException("目标账户不存在")
    val sfrom: Any = ctx.api.getVal(data.from)
    val dfrom = sfrom.asInstanceOf[Int]
    if (dfrom < data.amount)
      throw ContractException("余额不足")

    ////跨合约调用
    /*val cid = ChaincodeId("ParallelPutProofTPL", 1)
    val cdata = ProofDataSingle("cky1", dfrom.toString)

    val tmp : String = write(cdata)
    val crs = ctx.api.crossContractCall(cid, "putProofSingle", Seq(tmp))
    if(crs.err != None && crs.err.get.code == 0){
      val k1 = ctx.api.getCrossKey(ctx.api.getChainNetId, cid.chaincodeName,"cky1")
      //可以从返回值中直接读取
      System.out.println("cross result1="+SerializeUtils.deserialise(crs.statesSet.get(k1).get.toByteArray))
      //也可以从跨合约状态中读取，建议在这里读
      System.out.println("cross result2="+ctx.api.getStateEx(ctx.api.getChainNetId, cid.chaincodeName, "cky1"))
    }
    */

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
        transfer(ctx, json.extract[Transfer])
      case "set" =>
        set(ctx, json.extract[Map[String, Int]])
      case "putProof" =>
        put_proof(ctx, json.extract[Map[String, Any]])
    }
  }

}
