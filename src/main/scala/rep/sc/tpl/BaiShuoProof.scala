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
import rep.utils.SerializeUtils

class BaiShuoProof extends IContract{

  implicit val formats = DefaultFormats

  /**
    *
    * @param certPem
    * @param userInfo
    */
  case class certData(certPem: String, userInfo: String)

  /**
    *
    * @param userId       用户id
    * @param fileHashCode 文件hash值
    * @param creatAt      创建交易时间 (时间戳)
    */
  case class proofData(userId: String, fileHashCode: String, creatAt: String)


  def init(ctx: ContractContext){
    println(s"tid: $ctx.t.txid")
  }

  /**
    * 存证数据
    * @param ctx
    * @param data
    * @return
    */
  def putProof(ctx: ContractContext, data: proofData): Object={
    //先检查该hash是否已经存在,如果已存在,抛异常
    val key = data.fileHashCode
    val value = ctx.api.getVal(key)
    if(value != null)
      throw new Exception(s"[ " + key + " ]已存在，当前值[ " + value +" ]")
    ctx.api.setVal(key, data)
    print("putProof: "+ key +" : " + value)
    "putProof ok"
  }

  /**
    * 注册证书
    * @param ctx
    * @param certData
    * @return
    */
  def signUp(ctx: ContractContext, certData: certData): Object = {
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

  /**
    * 根据action,找到对应的method，并将传入的json字符串parse为method需要的传入参数
    */
  def onAction(ctx: ContractContext,action:String, sdata:String ): Object={

    val json = parse(sdata)

    action match {
      case "putProof" =>
        println("putProof data")
        putProof(ctx, json.extract[proofData])
      case "signUp" =>
        println("signUp certificate")
        signUp(ctx, json.extract[certData])
    }
  }
}
