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

import java.io.StringReader

import org.bouncycastle.util.io.pem.PemReader
import org.json4s._
import org.json4s.jackson.JsonMethods._
import rep.protos.peer.ActionResult
import rep.sc.scalax.{ContractContext, ContractException, IContract}

import scala.collection.mutable.HashMap


/**
  * @author zyf
  */
class ManageNodeCert extends IContract {

  val prefix = "tsdb_"

  def init(ctx: ContractContext) {
    println(s"tid: ${ctx.t.id}, execute the contract which name is ${ctx.t.getCid.chaincodeName} and version is ${ctx.t.getCid.version}")
  }

  /**
    * 只能初始化一次
    *
    * @param ctx
    * @param data (节点名 -> 证书pem字符串)
    * @return
    */
  def initNodeCert(ctx: ContractContext, data: Map[String, String]): ActionResult = {
    val certKey = prefix + ctx.t.getSignature.getCertId.creditCode
    if (ctx.api.getVal(certKey) != null) {
      throw ContractException("已经初始化了，请使用updateNodeCert来更新")
    }
    // 初始化nodeCertMap
    val certMap = HashMap[String, Array[Byte]]()
    for ((alias, certPem) <- data) {
      val pemReader = new PemReader(new StringReader(certPem))
      val certBytes = pemReader.readPemObject().getContent
      certMap.put(alias, certBytes)
    }
    ctx.api.setVal(certKey, certMap)
    null
  }

  /**
    *
    * @param ctx
    * @param data (节点名 -> 证书pem字符串)，如果是(节点名 -> "")，则移除该节点的证书
    * @return
    */
  def updateNodeCert(ctx: ContractContext, data: Map[String, String]): ActionResult = {
    val certKey = prefix + ctx.t.getSignature.getCertId.creditCode
    if (ctx.api.getVal(certKey) == null) {
      throw ContractException("未始化了，请使用initNodeCert来初始化")
    }
    val certMap = ctx.api.getVal(certKey).asInstanceOf[HashMap[String, Array[Byte]]]
    for ((alias, certPem) <- data) {
      if (certPem.equals("")) {
        certMap.remove(alias)
      } else {
        val pemReader = new PemReader(new StringReader(certPem))
        val certBytes = pemReader.readPemObject().getContent
        certMap.put(alias, certBytes)
      }
    }
    ctx.api.setVal(certKey, certMap)
    null
  }

  /**
    * 根据action,找到对应的method，并将传入的json字符串parse为method需要的传入参数
    */
  def onAction(ctx: ContractContext, action: String, sdata: String): ActionResult = {

    implicit val formats = DefaultFormats
    val json = parse(sdata)

    action match {
      case "initNodeCert" =>
        initNodeCert(ctx, json.extract[Map[String, String]])
      case "updateNodeCert" =>
        updateNodeCert(ctx, json.extract[Map[String, String]])
    }
  }

}
