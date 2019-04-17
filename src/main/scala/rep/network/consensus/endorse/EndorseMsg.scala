package rep.network.consensus.endorse

import akka.actor.{ Address}
import rep.protos.peer.{Signature,Block}

object EndorseMsg {
  //背书请求者消息
  case class RequesterOfEndorsement(blc: Block, blocker: String, endorer: Address)
  
  //给背书人的背书消息
  case class EndorsementInfo(blc: Block, blocker: String)

  //背书收集者消息
  case class CollectEndorsement(blc: Block, blocker: String)

  //背书人返回的背书结果
  case class ResultOfEndorsed(result: Boolean, endor: Signature, BlockHash: String)

  //背书请求者返回的结果
  case class ResultOfEndorseRequester(result: Boolean, endor: Signature, BlockHash: String, endorser: Address)
  
  
  case class verifyTransSignOfEndorsement(blc: Block, blocker: String)
  
  case class verifyTransExeOfEndorsement(blc: Block, blocker: String)
  
  case class VerfiyBlockEndorseOfEndorsement(blc: Block, blocker: String)
  
  case class VerifyResultOfEndorsement(blockhash:String,blocker:String,verifyType:Int,result:Boolean)
  
  case object VerifyTypeOfEndorsement{
    val transSignVerify = 1
    val transExeVerify = 2
    val endorsementVerify = 3
  }
  
  case class VerifyCacher(blc: Block, blocker: String,result:VerifyResultOfEndorsement)
  
  case object ConsensusOfVote
}