package rep.network.sync

import rep.protos.peer._
import akka.actor.{ ActorRef, Props }

object SyncMsg {
  case object StartSync
  
  case object ChainInfoOfRequest

  case class ChainInfoOfResponse(response: BlockchainInfo)
  
  case class BlockDataOfRequest(startHeight:Long)
  
  case class BlockDataOfResponse(data: Block)
  
  case object SyncTimeout
  
  case class  SyncRequestOfStorager(responser:ActorRef,StartHeight:Long,LastHeight:Long)

}