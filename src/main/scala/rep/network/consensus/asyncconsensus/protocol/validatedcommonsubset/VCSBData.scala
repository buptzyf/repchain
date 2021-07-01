package rep.network.consensus.asyncconsensus.protocol.validatedcommonsubset

import org.json4s.DefaultFormats
import org.json4s.native.Json
import rep.network.consensus.asyncconsensus.common.crypto.ThresholdSignatureAPI
import rep.network.consensus.asyncconsensus.config.ConfigOfManager
import rep.network.consensus.asyncconsensus.protocol.validatedcommonsubset.ValidatedCommonSubset.PrbcProofs
import rep.protos.peer.ResultOfPRBC
import scala.collection.immutable.HashMap

class VCSBData(nodeName:String,round:String) {
  private var signTool : ThresholdSignatureAPI = null;
  private var isSentPrbc = false
  private var isSentVaba = false
  private var prbc_proofs : HashMap[String,PrbcProofs] = new HashMap[String,PrbcProofs]()
  private var prbc_values: HashMap[String,Array[Byte]] = new HashMap[String,Array[Byte]]()

  Init

  private def Init={
    try{
      val cfg = ConfigOfManager.getManager.getConfig(nodeName)
      this.signTool = new ThresholdSignatureAPI(cfg.getSignPublicKey23,cfg.getSignPrivateKey23)
    }catch {
      case e:Exception=>
        e.printStackTrace()
    }
  }

  def getSignTool:ThresholdSignatureAPI={
    this.signTool
  }

  def predicate(leader:String,content:String,sign:String):Boolean={
    val source = leader + "_" + content
    this.signTool.verifyCombineSign(sign,source)
  }

  def predicates(proofs:HashMap[String,PrbcProofs]):Int={
    var count = 0
    proofs.foreach(f=>{
      if(this.signTool.verifyCombineSign(f._2.combineSign,f._2.leader+"_"+f._2.content)){
        count += 1
      }
    })
    count
  }

  def savePrbcData(prbc_result:ResultOfPRBC):Unit={
    if(prbc_result != null){
      if(!this.prbc_values.contains(prbc_result.moduleName)){
        this.prbc_values += prbc_result.moduleName -> prbc_result.transationsCipher.toByteArray
      }
      if(!this.prbc_proofs.contains(prbc_result.moduleName)){
        this.prbc_proofs += prbc_result.moduleName -> new PrbcProofs(prbc_result.moduleName,prbc_result.round,prbc_result.rootHash.toStringUtf8,prbc_result.combineSign.toStringUtf8)
      }
    }
  }

  def getPrbcProof(name:String):PrbcProofs={
    this.prbc_proofs.getOrElse(name,null)
  }

  def getAllPrbcProof:HashMap[String,PrbcProofs]={
    this.prbc_proofs
  }

  def getAllPrbcValue:HashMap[String,Array[Byte]]={
    this.prbc_values
  }

  def getPrbcValue(name:String):Array[Byte]={
    this.prbc_values.getOrElse(name,null)
  }

  def getPrbcProofCount:Int={
    this.prbc_proofs.size
  }

  def getPrbcValueCount:Int={
    this.prbc_values.size
  }

  def getPrbcProofToString:String={
    var hmStr = ""
    try{
      hmStr = Json(DefaultFormats).write(this.prbc_proofs)
    }catch {
      case e:Exception=>
        e.printStackTrace()
    }
    hmStr
  }

  def getPrbcProofFromString(jsonstr:String):HashMap[String,PrbcProofs]={
    var tmp : HashMap[String,PrbcProofs] = new HashMap[String,PrbcProofs]()
    try{
      val temp = Json(DefaultFormats).read[Map[String,PrbcProofs]](jsonstr)
      temp.foreach(p=>{
        tmp += p._1 -> p._2
      })
    }catch {
      case e:Exception=>
        e.printStackTrace()
    }
    tmp
  }

  def getIsSentPrbc:Boolean={
    this.isSentPrbc
  }

  def setIsSentPrbc:Unit={
    this.isSentPrbc = true
  }

  def getIsSentVaba:Boolean={
    this.isSentVaba
  }

  def setIsSentVaba:Unit={
    this.isSentVaba = true
  }
}
