package rep.network.consensus.cfrd

import akka.actor.{ActorRef, Address}
import rep.protos.peer.{Block, BlockchainInfo, Signature, Transaction, TransactionResult}
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
  case object VoteOfReset
  ////////////////////////////////Vote（抽签）消息，结束//////////////////////////////


  ///////////////////////////////Block（出块）消息，开始//////////////////////////////
  //抽签成功之后，向预出块的actor发送建立新块的消息，该消息由抽签actor发出
  case object CreateBlock
  case class  RequestPreLoadBlock(voteinfo:BlockerInfo)
  //case class CreateBlockTPS(ts : Seq[Transaction], trs : Seq[TransactionResult]) //zhjtps
  ///////////////////////////////Block（出块）消息，结束//////////////////////////////


  //////////////////////////////endorsement（共识）消息，开始/////////////////////////
  //背书结果消息
  case object ResultFlagOfEndorse{
    val BlockerSelfError = 1
    val CandidatorError = 2
    val BlockHeightError = 3
    val VerifyError  = 4
    val EnodrseNodeIsSynching = 5
    val EndorseNodeNotVote = 6
    val EndorseNodeUnkonwReason = 7
    val VoteIndexError = 8
    val success = 0
  }

  //背书请求者消息
  case class BlockInfoOfConsensus(voteinfo:BlockerInfo,blocker:String,blc:Block,currentBlockSerial:Int,isFirst:Boolean,isLastBlock:Boolean)
  case class ResultOfEndorsementInStream(voteinfo:BlockerInfo,currentBlockSerial:Int,blockHash:String,isFirst:Boolean,isLastBlock:Boolean,result: Boolean, endor: Signature,endorser: Address)
  case class ResendEndorseInfoInStream(voteinfo:BlockerInfo,currentBlockSerial:Int,blockHash:String,isFirst:Boolean,isLastBlock:Boolean,endorer: Address)

  case class RequesterOfEndorsement(blc: Block, blocker: String, endorer: Address,voteindex:Int)
  case class ResendEndorseInfo(endorer: Address)

  //给背书人的背书消息
  case class EndorsementInfo(blc: Block, blocker: String,voteindex:Int)

  case class verifyTransOfEndorsement(blc: Block, blocker: String)
  case class verifyTransRepeatOfEndorsement(blc: Block, blocker: String)
  case class verifyTransPreloadOfEndorsement(blc: Block, blocker: String)


  //背书收集者消息
  case class CollectEndorsement(blc: Block, blocker: String)

  //背书人返回的背书结果
  case class ResultOfEndorsed(result: Int, endor: Signature, BlockHash: String,endorserOfChainInfo:BlockchainInfo,endorserOfVote:BlockerInfo)

  //背书请求者返回的结果
  case class ResultOfEndorseRequester(result: Boolean, endor: Signature, BlockHash: String, endorser: Address)



  //////////////////////////////endorsement（共识）消息，结束/////////////////////////


  //CFRD Stream
  case class PreBlockMsg(preBlockHash:String,currentHeight:Long,currentBlockTxids:Seq[String])
  case class RequestTransaction(txid:String,recver:ActorRef)
  case class RequestTransactions(txids:Seq[String],recver:ActorRef)
  case class PreloadTransaction(txids:Seq[String],preBlockHash:String,height:Long,dbtag:String)
}
