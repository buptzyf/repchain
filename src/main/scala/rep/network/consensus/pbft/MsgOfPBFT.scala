package rep.network.consensus.pbft

import akka.actor.{ActorRef, Address}
import rep.proto.rc2.{Block, BlockchainInfo, Signature}
import rep.utils.GlobalUtils.BlockerInfo
import rep.utils.SerializeUtils

//zhj
/**
 * Created by zhaohuanjun on 2020/03/20.
 * PBFT共识协议的各类消息汇总
 */
object MsgOfPBFT {
  ////////////////////////////////Vote（抽签）消息，开始//////////////////////////////
  //通知抽签模块可以抽签的消息
  //case object VoteOfBlocker

  case class VoteOfBlocker(flag:String)

  //通知抽签模块，需要强制抽签
  case object VoteOfForce
  ////////////////////////////////Vote（抽签）消息，结束//////////////////////////////

  case class ConfirmedBlock(blc: Block, actRef: ActorRef, replies : Seq[MPbftReply])

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
  //case class EndorsementInfo(blc: Block, blocker: String)
  //EndorsementInfo => MsgPbftPrePrepare
  //给背书人的背书消息
  case class MsgPbftPrePrepare(senderPath:String,block: Block, blocker: String)
  case class MsgPbftPrePrepareResend(senderPath:String,block: Block, blocker: String)

  //背书收集者消息
  case class CollectEndorsement(blc: Block, blocker: String)

  //背书人返回的背书结果
  case class ResultOfEndorsed(result: Int, endor: Signature, BlockHash: String,endorserOfChainInfo:BlockchainInfo,endorserOfVote:BlockerInfo)

  //背书请求者返回的结果
  case class ResultOfEndorseRequester(result: Boolean, endor: Signature, BlockHash: String, endorser: Address)
  //////////////////////////////endorsement（共识）消息，结束/////////////////////////


  case class MsgPbftPrepare(senderPath:String,result: Int, block:Block, blocker: String, prepare: MPbftPrepare, chainInfo : BlockchainInfo)
  case class MsgPbftCommit(senderPath:String,block: Block, blocker: String, commit: MPbftCommit, chainInfo : BlockchainInfo)
  case class MsgPbftReply(block: Block, reply: MPbftReply, chainInfo : BlockchainInfo)
  case class MsgPbftReplyOk(block: Block, replies : Seq[MPbftReply])

  //以下三个类的定义从proto文件中迁移
  case class MPbftPrepare(signature:Option[Signature])
  case class MPbftCommit(prepares:Seq[MPbftPrepare],signature:Option[Signature])
  case class MPbftReply(commits:Seq[MPbftCommit],signature:Option[Signature])

}
