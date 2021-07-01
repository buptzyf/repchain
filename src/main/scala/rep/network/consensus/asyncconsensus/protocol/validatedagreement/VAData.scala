package rep.network.consensus.asyncconsensus.protocol.validatedagreement

import com.fasterxml.jackson.core.`type`.TypeReference
import rep.network.consensus.asyncconsensus.common.crypto.ThresholdSignatureAPI
import rep.network.consensus.asyncconsensus.config.ConfigOfManager
import rep.network.consensus.asyncconsensus.protocol.consistentbroadcast.ConsistentBroadcast.CBC_RESULT
import rep.storage.util.pathUtil
import org.json4s.native.{Json, Serialization}
import org.json4s.DefaultFormats

import scala.collection.immutable.HashMap
import scala.math.pow
import com.fasterxml.jackson.databind.ObjectMapper
import rep.network.consensus.asyncconsensus.protocol.validatedagreement.ValidatedAgreement.VABA_VOTE
import rep.network.consensus.asyncconsensus.protocol.validatedcommonsubset.ValidatedCommonSubset.PrbcProofs

class VAData(nodeName:String,round:String) {
  case class randomNumber(var number:Long,var generateSerial:Int)
  private var voteList:Array[String] = ConfigOfManager.getManager.getNodeNames.toSeq.sortBy(f=>(f)).toArray

  private var signTool : ThresholdSignatureAPI = null;
  private var is_cbc_delivered : HashMap[String,Int] = new HashMap[String,Int]()
  private var is_commit_delivered : HashMap[String,Int] = new HashMap[String,Int]()
  private var cbc_value : HashMap[String,CBC_RESULT] = new HashMap[String,CBC_RESULT]()
  private var commit_value : HashMap[String,HashMap[String,Int]] = new HashMap[String,HashMap[String,Int]]()

  private var voteResult : HashMap[Int,HashMap[String,VABA_VOTE]] = new HashMap[Int,HashMap[String,VABA_VOTE]]()

  def getAllCBCValue:HashMap[String,CBC_RESULT]={
    this.cbc_value
  }

  def getRound:String={
    this.round
  }

  def saveVoteResult(epoch:Int,vabaresult: VABA_VOTE):Unit={
    if(this.voteResult.contains(epoch)){
      var tmp = this.voteResult(epoch)
      tmp += vabaresult.sendName -> vabaresult
      this.voteResult += epoch -> tmp
    }else{
      var temp = new HashMap[String,VABA_VOTE]()
      temp += vabaresult.sendName -> vabaresult
      this.voteResult += epoch -> temp
    }
  }

  def getVoteResultCount(epoch:Int):Int={
    if(this.voteResult.contains(epoch)){
      this.voteResult(epoch).size
    }else{
      0
    }
  }

  def getVoteResult(epoch:Int):HashMap[String,VABA_VOTE]={
    if(this.voteResult.contains(epoch)){
      this.voteResult(epoch)
    }else{
      new HashMap[String,VABA_VOTE]()
    }
  }

  private var isVoteCoin = false
  private var isCbc = false
  private var isCommit = false
  private var isBa = false
  private var isSendVoteResult = false

  private var r = 0

  def getVoteRound:Int={
    this.r
  }

  def addVoteRound:Int={
    this.r += 1
    this.r
  }

  def getIsVoteCoin:Boolean={
    this.isVoteCoin
  }

  def getIsCbc:Boolean={
    this.isCbc
  }

  def getIsCommit:Boolean={
    this.isCommit
  }

  def getIsBa:Boolean={
    this.isBa
  }

  def getIsSendVoteResult:Boolean={
    this.isSendVoteResult
  }

  def setIsVoteCoin:Unit={
    this.isVoteCoin = true
  }

  def setIsCbc:Unit={
    this.isCbc = true
  }

  def setIsCommit:Unit={
    this.isCommit = true
  }

  def setIsBa(value:Boolean):Unit={
    this.isBa = value
  }

  def setIsSendVoteResult:Unit={
    this.isSendVoteResult = true
  }

  private var voteCoin : String = ""

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

  def saveCBCValue(cbc_result:CBC_RESULT):Unit={
    if(cbc_result.ctype == "cbc"){
      //if(!this.cbc_value.contains(cbc_result.dataNodeName)){
        this.cbc_value  += cbc_result.leader -> cbc_result
      //}
    }
  }

  def getCBCValue(name:String): CBC_RESULT ={
    this.cbc_value.getOrElse(name,null)
  }

  def saveCBCCommitValue(name:String,cbc_result:HashMap[String,Int]):Unit={
      if(!this.commit_value.contains(name)){
        this.commit_value  += name -> cbc_result
      }
  }

  def getCBCCommitValue(name:String):HashMap[String,Int]={
    this.commit_value.getOrElse(name,null)
  }

  def setCBCDelivered(name:String):Unit={
    this.is_cbc_delivered += name -> 1
  }

  def hasCBCDelivered(name:String):Boolean={
    this.is_cbc_delivered.contains(name)
  }

  def getCBCDeliveredCount:Int={
    this.is_cbc_delivered.values.sum
  }

  def getCBCDeliveredString:String={
    var hmStr = ""
    try{
      hmStr = Json(DefaultFormats).write(this.is_cbc_delivered)
    }catch {
      case e:Exception=>
        e.printStackTrace()
    }
    hmStr
  }

  def getCBCCommitDeliveredString:String={
    var hmStr = ""
    val om = new ObjectMapper()
    try{
      hmStr = Json(DefaultFormats).write(this.is_commit_delivered)
    }catch {
      case e:Exception=>
        e.printStackTrace()
    }
    hmStr
  }

  def stringToHashMap(str:String):HashMap[String,Int]={
    var tmp : HashMap[String,Int] = new HashMap[String,Int]()
    try{
      val temp = Json(DefaultFormats).read[Map[String,Int]](str)
      temp.foreach(p=>{
        tmp += p._1 -> p._2
      })
    }catch {
      case e:Exception=>
        e.printStackTrace()
    }
    tmp
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

  def predicate(leader:String,cmbSign:String,content:String):Boolean={
    this.signTool.verifyCombineSign(cmbSign,leader+"_"+content)
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

  def setCBCCommitDelivered(name:String):Unit={
    this.is_commit_delivered += name -> 1
  }

  def hasCBCCommitDelivered(name:String):Boolean={
    this.is_commit_delivered.contains(name)
  }

  def getCBCCommitDeliveredCount:Int={
    this.is_commit_delivered.values.sum
  }

  def setVoteCoin(coin:String):Unit={
    this.voteCoin = coin
    this.voteList = candidators
  }

  def getVote( position:Int): String = {
    if(this.voteList.nonEmpty){
      var pos = position
      if(position >= this.voteList.size){
        pos = position % this.voteList.size
      }
      this.voteList(pos)
    }else{
      null
    }
  }

  private def getRandomList(seed:Long,candidatorTotal:Int):Array[randomNumber]={
    val m = pow(2,20).toLong
    val a = 2045
    val b = 1
    var randomArray = new Array[randomNumber](candidatorTotal)
    var hashSeed = seed.abs
    for(i<-0 until (candidatorTotal) ){
      var tmpSeed = (a * hashSeed + b) % m
      tmpSeed = tmpSeed.abs
      if(tmpSeed == hashSeed) tmpSeed = tmpSeed + 1
      hashSeed = tmpSeed
      var randomobj = new randomNumber(hashSeed,i)
      randomArray(i) = randomobj
    }

    randomArray = randomArray.sortWith(
      (randomNumber_left,randomNumber_right)=> randomNumber_left.number < randomNumber_right.number)

    randomArray
  }

  private def candidators: Array[String] = {
    var nodesSeq = this.voteList.toSeq
    var len = this.voteList.size / 2 + 1
    val min_len = 4
    len = if(len<min_len){
      if(this.voteList.size < min_len) this.voteList.size
      else min_len
    }else len
    if(len<4){
      null
    }else{
      var candidate = new Array[String](len)
      var hashSeed:Long = pathUtil.bytesToInt(this.voteCoin.getBytes("UTF-8"))
      var randomList = getRandomList(hashSeed,this.voteList.size)

      for(j<-0 until len){
        var e = randomList(j)
        candidate(j) = nodesSeq(e.generateSerial)
      }
      candidate
    }
  }

}
