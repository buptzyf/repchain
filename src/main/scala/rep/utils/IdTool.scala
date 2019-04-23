package rep.utils

import rep.protos.peer.ChaincodeId
import rep.protos.peer.CertId
import java.util.UUID
import com.gilt.timeuuid.TimeUuid


object IdTool {
  
  def getUUID: String = {
    val uuid = TimeUuid()
    uuid.toString
  }

  def getRandomUUID: String = {
    UUID.randomUUID().toString
  }
  
  def getCid(chaincodeid:ChaincodeId):String={
    chaincodeid.chaincodeName+"_"+chaincodeid.version.toString()
  }
  
  def getSigner4String(certid:CertId):String={
   certid.certName + "." + certid.creditCode
  }
  
  def getCertIdFromName(name:String):CertId={
    if(name != null){
      if(name.indexOf(".")> 0){
        CertId(name.substring(0,name.indexOf(".")),
            name.substring(name.indexOf(".")+1,name.length()))
      }else{
        null
      }
    }else{
      null
    }
  }
  
  
  
}