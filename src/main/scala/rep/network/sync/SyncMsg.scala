package rep.network.sync

import rep.protos.peer._
import akka.actor.{ ActorRef, Props }

object SyncMsg {
  case class StartSync(isNoticeModuleMgr:Boolean)
  
  case object ChainInfoOfRequest
  
  case class ResponseInfo(response: BlockchainInfo, responser: ActorRef)
  
  case class GreatMajority(addr: ActorRef, height: Long)

  case class BlockDataOfRequest(startHeight:Long)
  
  case class BlockDataOfResponse(data: Block)
  
  case class  SyncRequestOfStorager(responser:ActorRef,maxHeight:Long)

}