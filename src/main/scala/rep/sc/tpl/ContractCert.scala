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


import org.json4s.jackson.JsonMethods._

import scala.collection.mutable.Map
import org.json4s.DefaultFormats
import rep.proto.rc2.{ActionResult, Certificate, Signer}
import rep.sc.scalax.{ContractContext, ContractException, IContract}
import scalapb.json4s.JsonFormat

/**
 * @author zyf
 */
final case class CertStatus(credit_code: String, name: String, status: Boolean)

class ContractCert extends IContract {
  //case class CertStatus(credit_code: String, name: String, status: Boolean)
  //case class CertInfo(credit_code: String, name: String, cert: Certificate)
  
  implicit val formats = DefaultFormats

  val notNodeCert = "非管理员操作"
  val signerExists = "账户已存在"
  val signerNotExists = "账户不存在"
  val certExists = "证书已存在"
  val certNotExists = "证书不存在"
  val unknownError = "未知错误"
  //val prefix = IdTool.getCid(ChaincodeId(chaincodeName, chaincodeVersion))
  val underline = "_"
  val dot = "."
  // 锚点，错误回退
  var anchor: Map[String, Any] = Map()

  object ACTION {
    val SignUpSigner = "SignUpSigner"
    val SignUpCert = "SignUpCert"
    val UpdateCertStatus = "UpdateCertStatus"
    val UpdateSigner = "UpdateSigner"
  }

  /**
   * 注册Signer账户
   * @param ctx
   * @param data
   * @return
   */
  def signUpSigner(ctx: ContractContext, data: Signer): ActionResult = {
    val isNodeCert = ctx.api.bNodeCreditCode(ctx.t.getSignature.getCertId.creditCode)
    if (!isNodeCert) {
      throw ContractException(notNodeCert)
    }
    // 存Signer账户
    //val signerKey = prefix + underline + data.creditCode
    val signer = ctx.api.getVal(data.creditCode)
    // 如果是null，表示已注销，如果不是null，则判断是否有值
    if (signer == null) {
      ctx.api.setVal(data.creditCode, data)
      null
    } else {
      throw ContractException(signerExists)
    }
  }

  /**
   * 注册用户证书：1、将name加到账户中；2、将Certificate保存
   * @param ctx
   * @param cert
   * @return
   */
  def signUpCert(ctx: ContractContext, cert: Certificate): ActionResult = {
    val isNodeCert = ctx.api.bNodeCreditCode(ctx.t.getSignature.getCertId.creditCode)
    if (!isNodeCert) {
      throw ContractException(notNodeCert)
    }
    val certKey = cert.getId.creditCode + dot + cert.getId.certName
    val certInfo = ctx.api.getVal(certKey)
    val signerKey = cert.getId.creditCode
    val signerContent = ctx.api.getVal(signerKey)
    // 先判断证书，若证书不存在，则向账户添加name
    if (certInfo == null) {
      if (signerContent == null) {
        throw ContractException(signerNotExists)
      } else {
        ctx.api.setVal(certKey, cert)
        //val signer = SerializeUtils.deserialise(signerContent).asInstanceOf[Signer]
        val signer = signerContent.asInstanceOf[Signer]
        if (!signer.certNames.contains(cert.getId.certName)) {
          val signerNew = signer.addCertNames(cert.getId.certName)
          ctx.api.setVal(signerKey, signerNew)
        }
      }
      null
    } else {
      throw ContractException(certExists)
    }
  }

  /**
   * 用户证书禁用、启用
   * @param ctx
   * @param data
   * @return
   */
  def updateCertStatus(ctx: ContractContext, data: CertStatus): ActionResult = {
    val isNodeCert = ctx.api.bNodeCreditCode(ctx.t.getSignature.getCertId.creditCode)
    if (!isNodeCert) {
      throw ContractException(notNodeCert)
    }
    val certKey = data.credit_code + dot + data.name
    val certInfo = ctx.api.getVal(certKey)
    if (certInfo == null) {
      throw ContractException(certNotExists)
    } else {
      //val cert = SerializeUtils.deserialise(certInfo).asInstanceOf[Certificate]
      val cert = certInfo.asInstanceOf[Certificate]
      val certNew = cert.withCertValid(data.status)
      ctx.api.setVal(certKey, certNew)
      null
    }
  }

  /**
   * 更新账户相关信息
   * @param ctx
   * @param data
   * @return
   */
  def updateSigner(ctx: ContractContext, data: Signer): ActionResult = {
    val isNodeCert = ctx.api.bNodeCreditCode(ctx.t.getSignature.getCertId.creditCode)
    if (!isNodeCert) {
      throw ContractException(notNodeCert)
    }
    val signer = ctx.api.getVal(data.creditCode)
    // 如果是null，账户不存在，不存在则不能更新
    if (signer == null) {
      throw ContractException(signerNotExists)
    } else {
      ctx.api.setVal(data.creditCode, data)
      null
    }
  }

  
  override def init(ctx: ContractContext) {
    println(s"tid: $ctx.t.id")
  }

  /**
   * 合约方法入口
   */
  override def onAction(ctx: ContractContext, action: String, sdata: String): ActionResult = {
    val json = parse(sdata)

    action match {
      case ACTION.SignUpSigner =>
        println("SignUpSigner")
        signUpSigner(ctx, JsonFormat.fromJson[Signer](json))
      case ACTION.SignUpCert =>
        println("SignUpCert")
        signUpCert(ctx, JsonFormat.fromJson[Certificate](json))
      case ACTION.UpdateCertStatus =>
        println("UpdateCertStatus")
        updateCertStatus(ctx, json.extract[CertStatus])
      case ACTION.UpdateSigner =>
        println("UpdateSigner")
        updateSigner(ctx, json.extract[Signer])
    }
  }

}