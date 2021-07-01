package rep.network.consensus.asyncconsensus.protocol.validatedcommonsubset

import akka.actor.ActorRef
import com.google.protobuf.ByteString
import rep.network.consensus.asyncconsensus.protocol.msgcenter.Broadcaster
import rep.network.consensus.asyncconsensus.protocol.validatedagreement.ValidatedAgreement.{StartValidateAgreement, VABA_Result}
import rep.network.consensus.asyncconsensus.protocol.validatedcommonsubset.ValidatedCommonSubset.{AddDataToVCS, VCSB_Result}
import rep.protos.peer.{ResultOfPRBC, StartOfPRBC}

import scala.collection.immutable.HashMap

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-02-13
 * @category	可验证公共子集协议体的主要实现。
 */

object ValidatedCommonSubset{
  case class AddDataToVCS(leader:String,round:String,data:Array[Byte])
  case class PrbcProofs(leader:String,round:String,content:String,combineSign:String)
  case class VCSB_Result(nodeName:String,round:String,content:HashMap[String,(String,Array[Byte])])
}

class ValidatedCommonSubset (nodeName:String,pathschema:String, moduleSchema:String, numberOfNodes:Int, numberOfFault:Int,
                             broadcaster: Broadcaster,caller:ActorRef,prbc_map:HashMap[String,ActorRef],vaba:ActorRef) {
  private var data:HashMap[String,VCSBData] = new HashMap[String,VCSBData]()

  def AddDataToVCSHandle(addMsg:AddDataToVCS):Unit={
      if(this.nodeName == addMsg.leader){
        var d = this.getData(addMsg.round)
        if(!d.getIsSentPrbc){
          val prbc_start = new StartOfPRBC(this.nodeName,addMsg.round,ByteString.copyFrom(addMsg.data))
          val prbc = this.prbc_map.getOrElse(this.nodeName+"-"+this.nodeName+"_PRBC",null)
          if(prbc != null){
            d.setIsSentPrbc
            prbc ! prbc_start
          }
        }
      }
  }

  def ResultOfPRBCHandle(prbc_result:ResultOfPRBC):Unit={
    var d = this.getData(prbc_result.round)
    if(prbc_result != null){
      if(d.predicate(prbc_result.moduleName,prbc_result.rootHash.toStringUtf8,prbc_result.combineSign.toStringUtf8)){
        d.savePrbcData(prbc_result)
      }
    }

    if(d.predicates(d.getAllPrbcProof) >= this.numberOfNodes - this.numberOfFault && !d.getIsSentVaba){
      d.setIsSentVaba
      val msg_str = d.getPrbcProofToString
      val vaba_msg = new StartValidateAgreement(this.nodeName,prbc_result.round,msg_str)
      this.vaba ! vaba_msg
    }
  }

  def VABA_ResultHandle(vaba_result:VABA_Result):Unit={
    var d = this.getData(vaba_result.round)
    if(d.getIsSentVaba){
      val proofs = d.getPrbcProofFromString(vaba_result.result.content)
      var value : HashMap[String,(String,Array[Byte])] = new HashMap[String,(String,Array[Byte])]()
      proofs.foreach(f=>{
        value += f._1 -> (f._2.content, d.getPrbcValue(f._1))
      })
      val msg = new VCSB_Result(this.nodeName,vaba_result.round,value)
      this.caller ! msg
    }
  }

  private def getData(round:String):VCSBData={
    if(!this.data.contains(round)){
      data += round -> new VCSBData(this.nodeName,round)
    }
    data(round)
  }
}
