package rep.network.consensus.common

import akka.actor.ActorRef
import rep.protos.peer.Block
import rep.utils.GlobalUtils.BlockerInfo


/**
 * Created by jiangbuyun on 2020/03/17.
 * 汇总共识层中所有公共的消息，目前公共的消息由预出块的交易预执行消息以及持久化的存储区块的消息。
 */
object MsgOfConsensus {
  ///////////////////////////////预出块时，预执行交易的相关消息，开始//////////////////////////////
  //发送预出块给交易预执行分配器，预执行预出块里面的所有交易。
  case class PreTransBlock(block: Block, prefixOfDbTag: String)
  case class PreTransBlockOfCache(blockIdentifierInCache:String, prefixOfDbTag: String)
  //预出块的预执行的结果消息
  case class PreTransBlockResult(blc: Block, result: Boolean)
  case class preTransBlockResultOfCache(result:Boolean)
  ///////////////////////////////预出块时，预执行交易的相关消息，结束//////////////////////////////

  ///////////////////////////////存储时，发送给持久化actor的相关消息，开始//////////////////////////////
  //对于单个区块需要存储时，发送下面的消息给持久化actor。
  final case class BlockRestore(blk: Block, SourceOfBlock: Int, blker: ActorRef)
  //这个消息用于出块时，提高存储效率，发送批处理消息。处理机制是，先把确认块存放到缓存，发送消息给持久化，之后持久化actor开始存储。
  final case object BatchStore
  ///////////////////////////////存储时，发送给持久化actor的相关消息，结束//////////////////////////////

  ///////////////////////////////创世块actor的相关消息，开始//////////////////////////////
  case object GenesisBlock
  ///////////////////////////////创世块actor的相关消息，结束//////////////////////////////

  ///////////////////////////////块确认actor的相关消息，开始//////////////////////////////
  //共识完成之后，广播正式出块的消息
  case class ConfirmedBlock(blc: Block, actRef: ActorRef)

  case class ConfirmedBlockInStream(voteinfo:BlockerInfo,currentBlockSerial:Int,blockHash:String,isFirst:Boolean,isLastBlock:Boolean,blc: Block, actRef: ActorRef)
  ///////////////////////////////块确认actor的相关消息，结束//////////////////////////////
}
