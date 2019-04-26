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

import java.io.{ByteArrayInputStream, StringReader}
import java.security.cert.{CertificateFactory, X509Certificate}

import org.bouncycastle.util.io.pem.PemReader
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse
import rep.crypto.ECDSASign
import rep.sc.Shim.{ERR_CERT_EXIST, PRE_CERT, PRE_CERT_INFO}
import rep.sc.contract.{ContractContext, IContract}
import rep.utils.{Json4s, SerializeUtils}


/**
  * 不动产权交易登记与检索
  * @author zyf
  */
class PropertyTPL2 extends IContract{

  /**
    *
    * @param hash       交易内容Hash
    * @param tranId     交易ID
    */
  case class PropertyTranData(hash: String, tranId: String)


  /**
    *
    * @param hash       交易hash
    * @param userId     调用检索服务的用户ID
    */
  case class RetrievalData(hash: String, userId: String)
  type RetrievalDataMap = scala.collection.mutable.HashMap[String, String]


  /**
    *
    * @param certPem    证书pem字符串
    * @param userInfo   user信息，如，姓名、手机号、邮箱等,JsonString
    */
  case class CertData(certPem: String, userInfo: String)


  override def init(ctx: ContractContext): Unit = {
    println(s"tid: $ctx.t.txid")
  }

  /**
    * 存证，存证不动产权数据
    * @param ctx
    * @param data 产权数据
    * @return
    */
  def propertyTranProof(ctx: ContractContext, data: PropertyTranData): Object = {
    // 产权信息可能变动，hash始终是唯一的
    ctx.api.setVal(data.hash,data.tranId)
    print("propertyTranProof:"+ data.hash + ":" + data.tranId)
    "propertyTranProof ok"
  }

  /**
    * 检索，需要将查询人的信息也记录下来
    * @param ctx
    * @param RetrievalDataMap
    * @return
    */
  def propertyTranRetrieval(ctx: ContractContext, dataMap: RetrievalDataMap): Object = {
    val result = new scala.collection.mutable.HashMap[String,Any]
    for (data <- dataMap) {
      val tranId = ctx.api.getVal(data._1)
      if (tranId == null)
        result.put(data._1, false)
      else
        result.put(data._1, true)
      // 将检索人的信息记录下来
      val retrievalData = RetrievalData(data._1, data._2)
      ctx.api.setVal(data._2, retrievalData)
    }
    Json4s.compactJson(result)
  }

  /**
    * 注册证书
    * @param ctx
    * @param certData
    * @return
    */
  def signUp(ctx: ContractContext, certData: CertData): Object = {
    ctx.api.check(ctx.t.cert.toStringUtf8,ctx.t)
    val cert = generateX509Cert(certData.certPem)
    if (cert.isDefined) {
      val addr = ECDSASign.getBitcoinAddrByCert(cert.get)
      val certKey = PRE_CERT + addr
      val certInfoKey = PRE_CERT_INFO + addr
      val value = ctx.api.getVal(certKey)
      if( value != null && value != "null"){
          throw new RuntimeException(ERR_CERT_EXIST)
      }
      val certBytes = SerializeUtils.serialise(cert.get)
      ctx.api.setState(certKey,certBytes)             // 只能用setState不能用setVal，json序列化有问题
      ctx.api.setVal(certInfoKey,certData.userInfo)
      println("证书短地址： "+ addr)
      addr
    } else {
      throw new RuntimeException("证书构建错误，请查验PEM字符串")
    }
  }

  /**
    * 注销证书
    * @param ctx
    * @param certAddr  证书短地址
    * @return
    */
  def destroyCert(ctx: ContractContext, certAddr: String): Object = {
    val certKey = PRE_CERT + certAddr
    val certInfoKey = PRE_CERT_INFO + certAddr
    try {
      val cert = Option(SerializeUtils.deserialise(ctx.api.getState(certKey)).asInstanceOf[X509Certificate])
      val value = ctx.api.getVal(certKey)
      if (cert.isEmpty) {
        throw new RuntimeException("不存在该用户证书")
      } else {
        if(value == "null")
          throw new RuntimeException("该证书已经注销")
      }
    } catch {
      case ex: Exception =>
        throw new RuntimeException(ex.getMessage)
    }
    ctx.api.setVal(certKey,"null")        // 注销证书，置空
    ctx.api.setVal(certInfoKey, "null")   // 证书信息也注销掉
    "destroy cert"
  }

  /**
    * 根据pem字符串生成证书
    * @param certPem       证书pem字符串
    * @return
    */
  def generateX509Cert(certPem: String): Option[X509Certificate] = {
    try {
      val cf = CertificateFactory.getInstance("X.509")
      val pemReader = new PemReader(new StringReader(certPem))
      val certByte = pemReader.readPemObject().getContent()
      val x509Cert = cf.generateCertificate(new ByteArrayInputStream(certByte))
      Some(x509Cert.asInstanceOf[X509Certificate])
    } catch {
      case ex: Exception =>
        None
    }
  }

  override def onAction(ctx: ContractContext, action: String, sdata: String): Object = {

    implicit val formats = DefaultFormats
    val json = parse(sdata)

    action match {
      // 产权交易登记
      case "propertyTranProof" =>
        propertyTranProof(ctx, json.extract[PropertyTranData])

      // 产权交易检索
      case "propertyTranRetrieval" =>
        propertyTranRetrieval(ctx, json.extract[RetrievalDataMap])

      // 证书注册
      case "signUp" =>
        signUp(ctx, json.extract[CertData])

      // 注销证书
      case "destroyCert" =>
        destroyCert(ctx, json.extract[String])

    }
  }
}
