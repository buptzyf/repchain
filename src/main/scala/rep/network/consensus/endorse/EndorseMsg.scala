package rep.network.consensus.endorse

import akka.actor.{ Address}
import rep.protos.peer.{Signature,Block,BlockchainInfo}
import rep.utils.GlobalUtils.{BlockerInfo}

object EndorseMsg {
  
  case object ResultFlagOfEndorse{
    val BlockerSelfError = 1
    val CandidatorError = 2
    val BlockHeightError = 3
    val VerifyError  = 4
    val success = 0
  }
  
  //背书请求者消息
  case class RequesterOfEndorsement(blc: Block, blocker: String, endorer: Address)
  
  //给背书人的背书消息
  case class EndorsementInfo(blc: Block, blocker: String)

  //背书收集者消息
  case class CollectEndorsement(blc: Block, blocker: String)

  //背书人返回的背书结果
  case class ResultOfEndorsed(result: Int, endor: Signature, BlockHash: String,endorserOfChainInfo:BlockchainInfo,endorserOfVote:BlockerInfo)

  //背书请求者返回的结果
  case class ResultOfEndorseRequester(result: Boolean, endor: Signature, BlockHash: String, endorser: Address)
  
  
 
}