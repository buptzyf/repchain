package rep.network.consensus.cfrd

import akka.actor.{ActorRef, Address}
import rep.protos.peer.{Block, BlockchainInfo, Signature}
import rep.utils.GlobalUtils.BlockerInfo


/**
 * Created by jiangbuyun on 2020/03/17.
 * CFRD共识协议的各类消息汇总
 */
object MsgOfCFRD {
  ////////////////////////////////Vote（抽签）消息，开始//////////////////////////////
  //通知抽签模块可以抽签的消息
  case object VoteOfBlocker
  //通知抽签模块，需要强制抽签
  case object VoteOfForce
  ////////////////////////////////Vote（抽签）消息，结束//////////////////////////////


  ///////////////////////////////Block（出块）消息，开始//////////////////////////////
  //抽签成功之后，向预出块的actor发送建立新块的消息，该消息由抽签actor发出
  case object CreateBlock
  ///////////////////////////////Block（出块）消息，结束//////////////////////////////


  //////////////////////////////endorsement（共识）消息，开始/////////////////////////
  //背书结果消息
  case object ResultFlagOfEndorse{
    val BlockerSelfError = 1
    val CandidatorError = 2
    val BlockHeightError = 3
    val VerifyError  = 4
    val EnodrseNodeIsSynching = 5
    val success = 0
  }

  //背书请求者消息
  case class RequesterOfEndorsement(blc: Block, blocker: String, endorer: Address)
  case class ResendEndorseInfo(endorer: Address)

  //给背书人的背书消息
  case class EndorsementInfo(blc: Block, blocker: String)

  //背书收集者消息
  case class CollectEndorsement(blc: Block, blocker: String)

  //背书人返回的背书结果
  case class ResultOfEndorsed(result: Int, endor: Signature, BlockHash: String,endorserOfChainInfo:BlockchainInfo,endorserOfVote:BlockerInfo)

  //背书请求者返回的结果
  case class ResultOfEndorseRequester(result: Boolean, endor: Signature, BlockHash: String, endorser: Address)
  //////////////////////////////endorsement（共识）消息，结束/////////////////////////


}
