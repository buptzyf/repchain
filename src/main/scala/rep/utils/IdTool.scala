package rep.utils

import rep.protos.peer.ChaincodeId
import rep.protos.peer.CertId

object IdTool {
  
  def getCid(chaincodeid:ChaincodeId):String={
    chaincodeid.chaincodeName+"_"+chaincodeid.version.toString()
  }
  
  def getSigner4String(certid:CertId):String={
   certid.certName + "_" + certid.creditCode
  }
  
}