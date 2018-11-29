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

package rep.sc.tpl

import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse
import org.json4s.jackson.Serialization._
import rep.sc.contract.{ContractContext, IContract}

package object realtyTpl2 {

  val SPLIT_CHAR = "_"

  /**
    * 存证类型，房客源、资金监管
    */
  object Proof {
    val housingProof = "housingProof"
    val fundProof = "fundProof"
  }

  /**
    *
    * @param proofId   存证的key
    * @param proofData 存证的数据
    * @param signData  签名的数据
    */
  case class proofTranData(proofId: String, proofData: String, signData: String)


  /**
    *
    * @param retrievalId
    */
  case class Retrieval(retrievalId: String)

}


/**
  * 房源存证合约
  *
  * 二手房
  * 出租房
  */
class listingTpl extends IContract {

  import realtyTpl2._

  implicit val formats = DefaultFormats

  override def init(ctx: ContractContext): Unit = {
    println(s"tid: $ctx.t.txid")
  }

  /**
    * 房源存证
    *
    * @param ctx
    * @param housingTranData
    * @return
    */
  def housingProof(ctx: ContractContext, housingTranData: proofTranData): Object = {
    // 房源存证id
    val hid = housingTranData.proofId
    // 将存证数据与签名数据构造为一个字符串
    val dataAndSign = Map("proofData" -> housingTranData.proofData, "signData" -> housingTranData.signData)
    val dataAndSignV = write(dataAndSign)
    // 存证
    proof(ctx, hid, dataAndSignV)
  }

  /**
    * 资金监管存证
    *
    * @param ctx
    * @param fundTranData
    * @return
    */
  def fundProof(ctx: ContractContext, fundTranData: proofTranData): Object = {
    // 资金监管存证id
    val fid = fundTranData.proofId
    // 将存证数据与签名数据构造为一个字符串
    val dataAndSign = Map("proofData" -> fundTranData.proofData, "signData" -> fundTranData.signData)
    val dataAndSignV = write(dataAndSign)
    // 存证
    proof(ctx, fid, dataAndSignV)
  }

  /**
    * 检索
    *
    * @param ctx
    * @param index
    * @return
    */
  def retrieval(ctx: ContractContext, index: Retrieval): Object = {
    val proofIndex = index.retrievalId
    val value = ctx.api.getVal(proofIndex).toString()
    if (value == null)
      "nothing be retrievaled"
    else
      value
  }

  /**
    * 注册用户证书
    *
    * @param ctx
    * @param data
    * @return
    */
  def signUp(ctx: ContractContext, data: Map[String, String]): Object = {
    var certAddr = ""
    for ((k, v) <- data) {
      ctx.api.check(ctx.t.cert.toStringUtf8, ctx.t)
      certAddr = ctx.api.signup(k, v)
    }
    certAddr
  }

  /**
    * 合约入口
    *
    * @param ctx
    * @param action
    * @param sdata
    * @return
    */
  override def onAction(ctx: ContractContext, action: String, sdata: String): Object = {
    val json = parse(sdata)

    action match {
      case Proof.housingProof =>
        housingProof(ctx, json.extract[proofTranData])
      case Proof.fundProof =>
        fundProof(ctx, json.extract[proofTranData])
      case "retrieval" =>
        retrieval(ctx, json.extract[Retrieval])
      case "signUp" =>
        println(s"signUp")
        signUp(ctx, json.extract[Map[String, String]])
      case _ => None
    }
  }

  def proof(ctx: ContractContext, key: String, value: String): Object = {
    //先检查该hash是否已经存在,如果已存在,抛异常
    val pv = ctx.api.getVal(key)
    if (pv != null)
      throw new Exception("[" + key + "]已存在，当前值[" + pv + "]")
    ctx.api.setVal(key, value)
    print("putProof:" + key + ":" + value)
    "put_proof ok"
  }
}
