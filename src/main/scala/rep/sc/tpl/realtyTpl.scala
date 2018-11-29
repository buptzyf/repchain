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

import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory

import fastparse.utils.Base64
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse
import rep.crypto.ECDSASign
import rep.sc.Shim.{ERR_CERT_EXIST, PRE_CERT, PRE_CERT_INFO}
import rep.sc.contract.{ContractContext, IContract}
import rep.utils.SerializeUtils

package object realtyType {

  val SPLIT_CHAR  = "_"
  val PRE_CERT_ID = "CERT_ID_"

  /**
    * 存证类型，房客源、资金监管
    */
  object Proof {
    val housingProof = "housingProof"
    val fundProof = "fundProof"
  }


  /**
    *
    * @param proofType 具体类别，exg：esf、zf、xf、office、fund、receive、takeout、change等
    * @param id id
    * @param proofData json数据
    */
  case class proofTranData(proofType: String, id: String, proofData: String)

  /**
    *
    * @param memberId  用户Id
    * @param pemCert   pem格式证书字符串，Base64
    * @param certInfo  证书信息(用户名，手机号，邮箱)，Base64
    */
  case class signCertData(memberId: Int, pemCert: String, certInfo: String)

  /**
    *
    * @param proofType
    * @param id
    */
  case class Retrieval(proofType: String, id: String)

}


/**
  * 房源存证合约
  * 二手房
  * 出租房
  */
class realtyTpl extends IContract {

  import realtyType._
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
    // 构造房源存证id
    val hid = housingTranData.proofType + SPLIT_CHAR + housingTranData.id
    // 存证
    proof(ctx, hid, housingTranData.proofData)
  }

  /**
    * 资金监管存证
    *
    * @param ctx
    * @param fundTranData
    * @return
    */
  def fundProof(ctx: ContractContext, fundTranData: proofTranData): Object = {
    val fid = fundTranData.proofType + SPLIT_CHAR + fundTranData.id
    // 存证
    proof(ctx, fid, fundTranData.proofData)
  }

  /**
    * 检索
    *
    * @param ctx
    * @param index
    * @return
    */
  def retrieval(ctx: ContractContext, index: Retrieval): Object = {
    val proofIndex = index.proofType + SPLIT_CHAR + index.id
    val value = ctx.api.getVal(proofIndex).asInstanceOf[String]
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
  def signUp(ctx: ContractContext, data: signCertData): Object = {
    ctx.api.check(ctx.t.cert.toStringUtf8, ctx.t)
    val cf = CertificateFactory.getInstance("X.509")
    val cert = cf.generateCertificate(
      new ByteArrayInputStream(
        Base64.Decoder(data.pemCert).toByteArray()
      )
    )
    val serialCert = SerializeUtils.serialise(cert)
    val certAddr = ECDSASign.getBitcoinAddrByCert(serialCert)
    val certkey = PRE_CERT + certAddr
    val dert = ctx.api.getVal(certkey)
    if( dert!= null){
      val dert = new String(ctx.api.getState(certkey))
      println(dert)
      if(dert != "null")
        throw new Exception(ERR_CERT_EXIST)
    }
    //保存证书
    ctx.api.setVal(PRE_CERT_ID, data.memberId)
    ctx.api.setState(certkey, serialCert)
    ctx.api.setVal(PRE_CERT_INFO + certAddr, data.certInfo)
    println("证书短地址： "+ certAddr)
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
        signUp(ctx, json.extract[signCertData])
      case _ => None
    }
  }

  def proof(ctx: ContractContext, key: String, value: String): Object = {
    //先检查该hash是否已经存在,如果已存在,抛异常
    val pv = ctx.api.getVal(key)
    if(pv != null)
      throw new Exception("[" + key + "]已存在，当前值[" + pv + "]")
    ctx.api.setVal(key,value)
    print("putProof:"+ key + ":" + value)
    "put_proof ok"
  }
}
