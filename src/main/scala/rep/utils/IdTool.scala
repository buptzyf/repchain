package rep.utils

import rep.protos.peer.ChaincodeId
import rep.protos.peer.CertId
import java.util.UUID
import com.gilt.timeuuid.TimeUuid

object IdTool {
  
  def getUUID(): String = {
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
   certid.certName + "_" + certid.creditCode
  }
  
}