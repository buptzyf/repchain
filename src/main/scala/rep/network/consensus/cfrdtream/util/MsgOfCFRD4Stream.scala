package rep.network.consensus.cfrdtream.util

import rep.utils.GlobalUtils.BlockerInfo

object MsgOfCFRD4Stream {
  case object JoinVoteSyncMsg
  case class ReponseVoteSyncMsg(nodeName:String,voteInfo:BlockerInfo)
  case class TransformBlocker(reason:Int,voteInfo:BlockerInfo)
}
