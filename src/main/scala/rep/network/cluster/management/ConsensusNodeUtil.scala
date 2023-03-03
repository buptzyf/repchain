package rep.network.cluster.management

import rep.app.system.RepChainSystemContext
import rep.log.RepLogger
import rep.storage.chain.KeyPrefixManager
import rep.storage.db.factory.DBFactory
import rep.utils.{SerializeUtils}

object ConsensusNodeUtil {
  val vote_node_list = "TSDb-Vote-List"

  def loadVoteList(ctx:RepChainSystemContext):Array[String] = {
    var tmpList : Array[String] = Array()

    val db = DBFactory.getDBAccess(ctx.getConfig)
    val keyValue = db.getObject[Array[String]](
      KeyPrefixManager.getWorldStateKey(ctx.getConfig, ConsensusNodeUtil.vote_node_list,
        ctx.getConfig.getMemberManagementContractName))
    if (keyValue == None) {
      tmpList = ctx.getConfig.getVoteNodeList.toArray
      writeVoteListToDB(tmpList,ctx)
    } else {
      tmpList = keyValue.getOrElse(null)
      RepLogger.trace(RepLogger.System_Logger, "ConsensusNodeUtil 从DB装载共识节点名称列表="+tmpList.mkString(","))
    }
    tmpList
  }

  private def writeVoteListToDB(voteList:Array[String],ctx:RepChainSystemContext): Unit = {
    if (voteList != null && voteList.length >= ctx.getConfig.getMinVoteNumber) {
      val db = DBFactory.getDBAccess(ctx.getConfig)
      db.putBytes(KeyPrefixManager.getWorldStateKey(ctx.getConfig, ConsensusNodeUtil.vote_node_list,
        ctx.getConfig.getMemberManagementContractName), SerializeUtils.serialise(voteList))
      RepLogger.trace(RepLogger.System_Logger, "ConsensusNodeUtil 将共识节点名称列表写入到DB="+voteList.mkString(","))
    }
  }
}
