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

package rep.utils



import java.io.StringWriter
import java.security.cert.X509Certificate
import java.util.UUID

import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import rep.app.conf.RepChainConfig
import rep.proto.rc2.{CertId, ChaincodeId, Transaction}


object IdTool {
  val DIDPrefixSeparator : String = ":"
  val WorldStateKeySeparator : String = "_"
  val NameSpaceSeparator : String = "."

  def getRandomUUID: String = {
    UUID.randomUUID().toString
  }

  def toPemString(x509: X509Certificate): String = {
    val writer = new StringWriter
    val pemWriter = new JcaPEMWriter(writer)
    try{
      pemWriter.writeObject(x509)
      try{pemWriter.close()}catch {case e:Exception=>e.printStackTrace()}
      try{writer.close()}catch {case e:Exception=>e.printStackTrace()}
      writer.toString
    }catch{
      case e:Exception=> ""
    }
  }

  def deleteLine(src:String):String={
    var rStr = ""
    if(src == null) {
      rStr
    }else{
      rStr = src.replaceAll("\\r\\n|\\n|\\r|\\s","")
    }
    rStr
  }

  def isDidContract(contractName:String):Boolean = {
    contractName == "RdidOperateAuthorizeTPL"
  }

  /** 从部署合约的交易，获得其部署的合约的链码id
   *  @param t 交易对象
   *  @return 链码id
   */
  def getTXCId(t: Transaction): String = {
    val t_cid = t.cid.get
    getCid(t_cid)
  } 
  
  def getCid(chaincodeid:ChaincodeId):String={
    chaincodeid.chaincodeName+IdTool.WorldStateKeySeparator+chaincodeid.version.toString()
  }

  def getCompleteOpName(config:RepChainConfig,opName:String):String={
    val flag = config.getChainNetworkId+IdTool.DIDPrefixSeparator
    if(opName.indexOf(flag) < 0){
      flag + opName
    }else{
      opName
    }
  }

  def getCompleteSignerName(config:RepChainConfig,signerName:String):String={
    val flag1 = config.getChainNetworkId+IdTool.DIDPrefixSeparator
    val flag2 = config.getIdentityNetName + IdTool.DIDPrefixSeparator
    if(signerName.indexOf(flag1) < 0 && signerName.indexOf(flag2) < 0){
      flag1 + signerName
    }else{
      signerName
    }
  }

  def getNodeSignerName(config:RepChainConfig,signerName:String):String={
    val flag1 = config.getChainNetworkId+IdTool.DIDPrefixSeparator
    val flag2 = config.getIdentityNetName + IdTool.DIDPrefixSeparator
    if(signerName.indexOf(flag1) >= 0){
        signerName.substring(signerName.indexOf(flag1)+flag1.length)
    }else if(signerName.indexOf(flag2) >= 0){
      signerName.substring(signerName.indexOf(flag2)+flag2.length)
    }else{
      signerName
    }
  }

  def getSignerFromCreditAndName(credit:String,name:String): String ={
    credit + IdTool.NameSpaceSeparator + name
  }

  def getSignerFromCertId(certid:CertId):String={
    var str = ""
    if(certid != null){
      str = certid.creditCode + IdTool.NameSpaceSeparator + certid.certName
    }
    str
  }

  def getCertIdFromCreditAndName(credit:String,name:String):CertId={
    CertId(credit,name)
  }

  //creditcode 已经修改成：网络id+did分隔符+id字符串
  def getCertIdFromName(name:String):CertId={
    if(name != null && name.lastIndexOf(IdTool.NameSpaceSeparator)> 0){
        CertId(name.substring(0,name.lastIndexOf(IdTool.NameSpaceSeparator)),
                name.substring(name.lastIndexOf(IdTool.NameSpaceSeparator)+1,name.length()))
    }else{
      null
    }
  }
  
}