package rep.accumulator.verkle.util

import com.fasterxml.jackson.databind.ObjectMapper
import rep.utils.SerializeUtils
import java.util.LinkedHashMap

object ProofSerialize {
  case class serialObj(p:String,n:String)
  private def getProofType(p: Any): String = {
    if (p.isInstanceOf[VerkleProofOfMembership]) {
      "VerkleProofOfMembership"
    } else if (p.isInstanceOf[VerkleProofOfNonMembership]) {
      "VerkleProofOfNonMembership"
    } else {
      ""
    }
  }

  private def getProofObj(p:String,n:String):Any={
    val objectMapper = new ObjectMapper()
    if(n.equalsIgnoreCase("VerkleProofOfMembership")){
      val tmp = objectMapper.readValue(p, classOf[Any])
      if(tmp.isInstanceOf[LinkedHashMap[String,Object]]){
        val hm = tmp.asInstanceOf[LinkedHashMap[String,Object]]
        VerkleProofOfMembership.Deserial(hm)
      }else{
        null
      }
    }else if(n.equalsIgnoreCase("VerkleProofOfNonMembership")){
      val tmp = objectMapper.readValue(p, classOf[Any])
      if (tmp.isInstanceOf[LinkedHashMap[String, Object]]) {
        val hm = tmp.asInstanceOf[LinkedHashMap[String, Object]]
        VerkleProofOfNonMembership.Deserial(hm)
      } else {
        null
      }
    }else{
      objectMapper.readValue(p, classOf[Any])
    }
  }

  def SerialProof(proofs: Array[Any]): String = {
    val rs = new Array[String](proofs.length)
    val objectMapper = new ObjectMapper()
    for (i <- 0 to proofs.length - 1) {
      val tmp = objectMapper.writeValueAsString(proofs(i))
      val name = getProofType(proofs(i))
      rs(i) = new String(org.apache.commons.codec.binary.Hex.encodeHex(SerializeUtils.serialise(serialObj(tmp,name))))
        //new String(SerializeUtils.serialise(serialObj(tmp,name)))
    }
    val json = SerializeUtils.serialise(rs)
    val hJson = new String(org.apache.commons.codec.binary.Hex.encodeHex(json))
    hJson
  }

  def DeserialProof(hstr: String): Array[Any] = {
    val json = org.apache.commons.codec.binary.Hex.decodeHex(hstr)
    val strs = SerializeUtils.deserialise(json)
    if (strs.isInstanceOf[Array[String]]) {
      val strArray = strs.asInstanceOf[Array[String]]
      val proofs = new Array[Any](strArray.length)
      for (i <- 0 to strArray.length - 1) {
        val tmp = SerializeUtils.deserialise(org.apache.commons.codec.binary.Hex.decodeHex(strArray(i)))
          //SerializeUtils.deserialise(strArray(i).getBytes)
        if(tmp.isInstanceOf[serialObj]){
          val sObj = tmp.asInstanceOf[serialObj]
          proofs(i) = getProofObj(sObj.p,sObj.n)
        }
      }
      proofs
    }else{
      null
    }
  }
}
