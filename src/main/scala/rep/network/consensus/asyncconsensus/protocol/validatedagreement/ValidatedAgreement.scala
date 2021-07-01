package rep.network.consensus.asyncconsensus.protocol.validatedagreement

import akka.actor.ActorRef
import rep.network.consensus.asyncconsensus.protocol.binaryagreement.BinaryAgreement.{ResultOfBA, StartBinaryAgreement}
import rep.network.consensus.asyncconsensus.protocol.consistentbroadcast.ConsistentBroadcast.{CBC_RESULT, StartConsistenBroadcast}
import rep.network.consensus.asyncconsensus.protocol.msgcenter.Broadcaster
import rep.network.consensus.asyncconsensus.protocol.validatedagreement.ValidatedAgreement.{StartValidateAgreement, VABA_Result, VABA_VOTE}
import rep.protos.peer.{ResultOfCommonCoin, StartCommonCoin}
import scala.collection.immutable.HashMap

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-02-13
 * @category	可验证一致性协议体的主要实现。
 */
object ValidatedAgreement{
  case class StartValidateAgreement(leader:String,round:String,data:String)
  case class VABA_VOTE(sendName:String,round:String,epoch:Int,nodeName:String,voteValue:Int,cbcValue:Option[CBC_RESULT])
  case class VABA_Result(sendName:String,round:String,result:CBC_RESULT)
}

class ValidatedAgreement (nodeName:String,pathschema:String, moduleSchema:String, numberOfNodes:Int, numberOfFault:Int,
                          broadcaster: Broadcaster,caller:ActorRef,cbcmap:HashMap[String,ActorRef],cbcCommit:HashMap[String,ActorRef],voteCoin:ActorRef,BA:ActorRef) {
  private var data:HashMap[String,VAData] = new HashMap[String,VAData]()

  def StartHandle(start:StartValidateAgreement):Unit={
    var d = this.getData(start.round)
    if(this.nodeName == start.leader && !d.getIsCbc){
      if(d.predicates(d.getPrbcProofFromString(start.data)) >= this.numberOfNodes-this.numberOfFault){
        d.setIsCbc
        val cbcStart = new StartConsistenBroadcast("cbc",start.round,this.nodeName,this.nodeName,start.data)
        val cbcActor = this.cbcmap.getOrElse(this.nodeName+"-"+this.nodeName+"_CBC",null)
        if(cbcActor != null){
          cbcActor ! cbcStart
        }
      }
    }
  }

  def CBCResultHandle(cbc_resut:CBC_RESULT):Unit={
    if(cbc_resut.ctype == "cbc"){
      CBCHandle(cbc_resut)
    }else if(cbc_resut.ctype == "cbccommit"){
      CBCCommitHandle(cbc_resut)
    }
  }

  private def CBCHandle(cbc_resut:CBC_RESULT):Unit={
    var d = this.getData(cbc_resut.round)
    if(d.predicate(cbc_resut.leader,cbc_resut.cmbSign,cbc_resut.content)){
      d.saveCBCValue(cbc_resut)
      d.setCBCDelivered(cbc_resut.leader)
      if(d.getCBCDeliveredCount >= this.numberOfNodes - this.numberOfFault && !d.getIsCommit){
        d.setIsCommit
        val cbcCommitStart = new StartConsistenBroadcast("cbccommit",cbc_resut.round,this.nodeName,this.nodeName,d.getCBCDeliveredString)
        val cbcCommitActor = this.cbcCommit.getOrElse(this.nodeName+"-"+this.nodeName+"_COMMITCBC",null)
        if(cbcCommitActor != null){
          cbcCommitActor ! cbcCommitStart
        }
      }
    }
  }

  private def CBCCommitHandle(cbc_resut:CBC_RESULT):Unit={
    var d = this.getData(cbc_resut.round)
    if(d.predicate(cbc_resut.leader,cbc_resut.cmbSign,cbc_resut.content)){
      var hs = d.stringToHashMap(cbc_resut.content)
      if(hs.values.sum >= this.numberOfNodes - this.numberOfFault ){
        d.saveCBCCommitValue(cbc_resut.leader,hs)
        d.setCBCCommitDelivered(cbc_resut.leader)
        if(d.getCBCCommitDeliveredCount >= this.numberOfNodes - this.numberOfFault && !d.getIsVoteCoin){
          d.setIsVoteCoin
          val msg = new StartCommonCoin(cbc_resut.round+"_permutation",false,cbc_resut.round)
          this.voteCoin ! msg
        }
      }
    }
  }

  def VoteCoinHandle(votecoin:ResultOfCommonCoin):Unit={
    val d = getData(votecoin.round)
    if(d.getIsVoteCoin && !d.getIsSendVoteResult){
      val seed = votecoin.result
      d.setVoteCoin(seed)
      startupVote(d)
    }
  }

  private def startupVote(d:VAData):Unit={
    val voteNode = d.getVote(d.getVoteRound)
    var vaba : VABA_VOTE = null
    if(d.hasCBCDelivered(voteNode)){
      vaba = VABA_VOTE(this.nodeName,d.getRound,d.getVoteRound,voteNode,1,Some(d.getCBCValue(voteNode)))
    }else{
      vaba = VABA_VOTE(this.nodeName,d.getRound,d.getVoteRound,voteNode,0,None)
    }
    d.setIsSendVoteResult
    this.broadcaster.broadcastInSchema(this.pathschema,this.moduleSchema,vaba)
  }

  def VABA_VOTEHandle(vabaVote:VABA_VOTE): Unit ={
    val d = this.getData(vabaVote.round)
    if(vabaVote.nodeName == d.getVote(d.getVoteRound) && (vabaVote.voteValue == 1 || vabaVote.voteValue == 0)){
      if(vabaVote.voteValue == 1){
        val cbcvalue = vabaVote.cbcValue.get
        //todo predicate
        if(d.predicates(d.getPrbcProofFromString(cbcvalue.content)) >= this.numberOfNodes-this.numberOfFault){
          d.saveVoteResult(vabaVote.epoch,vabaVote)
        }
      }else{
        if(d.getCBCCommitValue(vabaVote.nodeName) != null && d.getCBCCommitValue(vabaVote.nodeName).getOrElse(vabaVote.nodeName,0) == 0){
          d.saveVoteResult(vabaVote.epoch,vabaVote)
        }
      }
    }

    if(d.getVoteResultCount(vabaVote.epoch) >= this.numberOfNodes-this.numberOfFault && !d.getIsBa){
      var aba_r_input = 0
      val _vote = d.getVoteResult(vabaVote.epoch)
      _vote.keys.foreach(key=>{
        val msg = _vote(key)
        if(msg.voteValue == 1){
          aba_r_input = 1
          d.saveCBCValue(msg.cbcValue.get)
        }
      })

      d.setIsBa(true)
      val bmsg = new StartBinaryAgreement(this.nodeName,vabaVote.round+"_"+d.getVoteRound,0,aba_r_input)
      this.BA ! bmsg
    }
  }

  def ResultOfBAHandle(resultOfBa:ResultOfBA): Unit ={
    var tmpRound : String = resultOfBa.round
    if(resultOfBa.round.indexOf("_")>=0){
      val tmp = resultOfBa.round.split("_")
      tmpRound = tmp(0)
    }
    val d = this.getData(tmpRound)
    println(s"==========agreement recv ba result,round=${resultOfBa.round},epoch=${resultOfBa.epoch},value=${resultOfBa.binValue}------recver=${this.nodeName}")
    println(s"==========agreement recv ba result parser,round=${d.getRound},epoch=${d.getVoteRound},isBa=${d.getIsBa}------recver=${this.nodeName}")
    if(d.getIsBa && d.getRound+"_"+ d.getVoteRound==resultOfBa.round){
      if(resultOfBa.binValue == 1){
        val msg = new VABA_Result(this.nodeName,d.getRound,d.getCBCValue(d.getVote(d.getVoteRound)))
        this.caller ! msg
      }else{
        d.addVoteRound
        d.setIsBa(false)
        startupVote(d)
      }
    }
  }

  private def getData(round:String):VAData={
    if(!this.data.contains(round)){
      data += round -> new VAData(this.nodeName,round)
    }
    data(round)
  }

}
