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

import java.text.SimpleDateFormat
import java.util.Date

import akka.actor.Actor
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse
import rep.protos.peer.ActionResult
import rep.sc.scalax.{ContractContext, IContract}
import rep.utils.SerializeUtils

import scala.collection.mutable.HashMap
import scala.collection.mutable
import scala.util.Random   // 也有可能是java.util.Random


object WarningTPL {
  //case class中参数必须为明确的类型，不能是Any，如tranId: String，不能为tranId:Any
  case class PropertyTranData(hash: String, tranId: Any)

}


/**
  *
  * @param hash       交易内容Hash
  * @param tranId     交易ID
  */
// case class中参数必须为明确的类型，不能是Any，如tranId: String，不能为tranId:Any
case class PropertyTranData(hash: String, tranId: String)

/**
  *
  * @param hash       交易hash
  * @param userId     调用检索服务的用户ID
  */
case class RetrievalData(hash: String, userId: String)


/**
  *
  * @param certPem    证书pem字符串
  * @param userInfo   user信息，如，姓名、手机号、邮箱等,JsonString
  */
case class CertData(certPem: String, userInfo: String)

/**
  * 不动产权交易登记与检索
  * @author zyf
  */
class WarningTPL extends IContract with Actor {

  type RetrievalDataMap = HashMap[String, String]

  // 或者是如下，必须有完整的“类路径”
  //  type RetrievalDataMap = scala.collection.mutable.HashMap[String, String]

  override def init(ctx: ContractContext): Unit = {
    println(s"tid: $ctx.t.txid")
  }

  /**
    * 存证，存证不动产权数据
    * @param ctx
    * @param data 产权数据
    * @return
    */
  def propertyTranProof(ctx: ContractContext, data: PropertyTranData): ActionResult = {
    val random = Random.nextInt(8)
    val sdf = new SimpleDateFormat()                    // 格式化时间
    sdf.applyPattern("yyyy-MM-dd HH:mm:ss a") // a为am/pm的标记
    val date = new Date()                               // 获取当前时间
    // 每个节点执行的结果可能不一样，随机数和本地时间，一个是每个节点对随机数的判断不一样，一个是每个节点获取的本地时间不一样
    // 调用ctx.api.setVal()时候，data.tranId + sdf.format(date)在每个节点执行的是不一样的，会导致共识失败
    if (random == 3) {
      ctx.api.setVal(data.hash,data.tranId + sdf.format(date))
      print("propertyTranProof:"+ data.hash + ":" + data.tranId + sdf.format(date))
      return null
    }
    null
  }

  /**
    * 检索，需要将查询人的信息也记录下来
    * @param ctx
    * @param RetrievalDataMap
    * @return
    */
  def propertyTranRetrieval(ctx: ContractContext, dataMap: RetrievalDataMap): ActionResult = {
//    val result = new scala.collection.mutable.HashMap[String,Any]    // 如果上面HashMap引用了全路径，此处就可以直接使用HashMap
    val result = new mutable.HashMap[String,Any]    // 对应于“import scala.collection.mutable”
    for (data <- dataMap) {
      val tranId: Any = ctx.api.getVal(data._1)
      if (tranId == null)
        result.put(data._1, false)
      else
        result.put(data._1, true)
      // 将检索人的信息记录下来
      val retrievalData = RetrievalData(data._1, data._2)
      ctx.api.setVal(data._2, retrievalData)
    }
    SerializeUtils.compactJson(result)
    null
  }

  override def onAction(ctx: ContractContext,action:String, sdata:String ): ActionResult = {

    implicit val formats = DefaultFormats
    val json = parse(sdata)

    action match {
      // 产权交易登记
      case "propertyTranProof" =>
        propertyTranProof(ctx, json.extract[PropertyTranData])

      // 产权交易检索
      case "propertyTranRetrieval" =>
        propertyTranRetrieval(ctx, json.extract[RetrievalDataMap])

    }
  }

  override def receive: Receive = ???
}
