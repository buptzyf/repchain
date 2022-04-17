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



import java.util.UUID

import rep.app.conf.{RepChainConfig, SystemProfile}
import rep.proto.rc2.{CertId, ChaincodeId, Transaction}
//import com.gilt.timeuuid.TimeUuid



object IdTool {
  
  /*def getUUID: String = {
    val uuid = TimeUuid()
    uuid.toString
  }*/

  def getRandomUUID: String = {
    UUID.randomUUID().toString
  }
  

  def isDidContract(systemName:String):Boolean = {
    RepChainConfig.getSystemConfig(systemName).getAccountContractName == "RdidOperateAuthorizeTPL"
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
    chaincodeid.chaincodeName+"-"+chaincodeid.version.toString()
  }
  
  def getSigner4String(certid:CertId):String={
   certid.certName + "." + certid.creditCode
  }
  
  def getCertIdFromName(name:String):CertId={
    if(name != null && name.indexOf(".")> 0){
        CertId(name.substring(0,name.indexOf(".")),
                name.substring(name.indexOf(".")+1,name.length()))
    }else{
      null
    }
  }
  
}