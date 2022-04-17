package rep.network.sync.parser.raft

import rep.log.RepLogger
import rep.network.sync.SyncMsg.{AnalysisResult, MaxBlockInfo, RollbackAction, SynchAction}
import rep.network.sync.SyncMsg
import rep.network.sync.parser.ISynchAnalyzer
import rep.network.tools.NodeMgr
import rep.proto.rc2.BlockchainInfo

/**
 * Created by jiangbuyun on 2020/03/18.
 * 基于RAFT共识协议的同步区块信息分析的实现类
 */

class IRAFTSynchAnalyzer(systemName: String, lchaininfo: BlockchainInfo, nodemgr: NodeMgr) extends ISynchAnalyzer( systemName,  lchaininfo,  nodemgr) {
  override def Parser(reslist: List[SyncMsg.ResponseInfo], isStartupSynch: Boolean): Unit = {
    val lh = lchaininfo.height
    val lhash = this.lchaininfo.currentBlockHash.toStringUtf8()
    val lprehash = this.lchaininfo.previousBlockHash.toStringUtf8()
    val nodes = nodemgr.getStableNodes

    //获取到到chaininfo信息的数量，得到大多数节点的响应，进一步判断同步的策略
    //获取返回的chaininfo信息中，大多数节点的相同高度的最大值
    val heightStatis = reslist.groupBy(x => x.response.height).map(x => (x._1, x._2.length)).toList.sortBy(x => -x._1)
    val maxheight = heightStatis.head._1
    val maxresponseinfo = reslist.filter(_.response.height == maxheight).head

    RepLogger.debug(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------Info,获取同步的返回信息，结果=${reslist.mkString("|")}"))
    RepLogger.info(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------Info,获取同步的返回信息，统计结果=${heightStatis.mkString("|")}"))

    if (maxheight > lh) {
      if (lhash == maxresponseinfo.ChainInfoOfSpecifiedHeight.currentBlockHash.toStringUtf8()) {
        //本地最后块的hash与远端的前序块hash一致，可以直接进入同步
        this.aresult = AnalysisResult(1, "")
        this.maxinfo = MaxBlockInfo(maxresponseinfo.response.height, maxresponseinfo.response.currentBlockHash.toStringUtf8())
        this.saction = SynchAction(lh, maxheight, maxresponseinfo.responser)
      } else {
        //由于与远端的前序块的hash不一致，需要进一步进入回滚当前块的判断
        if (lprehash == maxresponseinfo.ChainInfoOfSpecifiedHeight.previousBlockHash.toStringUtf8()) {
          //本地块的前一块的hash与远端前导块的前一块的hash一致，可以调用块回滚到前一个块，直接调用存储持久化代码，进行块回滚，回滚之后载同步
          this.aresult = AnalysisResult(1, "")
          this.raction = RollbackAction(lh - 1)
          this.maxinfo = MaxBlockInfo(maxresponseinfo.response.height, maxresponseinfo.response.currentBlockHash.toStringUtf8())
          this.saction = SynchAction(lh - 1, maxheight, maxresponseinfo.responser)
        } else {
          //本地块的前一块的hash与远端前导块的前一块的hash不一致，输出错误，停止同步
          this.aresult = AnalysisResult(0, s"大于本地高度，本地块的前一块的hash与远端前导块的前一块的hash不一致，输出错误，停止同步,local height=${lh},maxheight=${maxheight}")
          RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------error,大于本地高度，本地块的前一块的hash与远端前导块的前一块的hash不一致，输出错误，停止同步,local height=${lh},maxheight=${maxheight}"))
        }
      }
    } else if (maxheight == lh) {
      if (lhash == maxresponseinfo.response.currentBlockHash.toStringUtf8()) {
        this.aresult = AnalysisResult(1, "")
        this.maxinfo = MaxBlockInfo(maxresponseinfo.response.height, maxresponseinfo.response.currentBlockHash.toStringUtf8())
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------error,等于本地高度，最后的hash一致，停止同步,local height=${lh},maxheight=${maxheight}"))
      } else {
        //由于与远端的前序块的hash不一致，需要进一步进入回滚当前块的判断
        if (lprehash == maxresponseinfo.ChainInfoOfSpecifiedHeight.previousBlockHash.toStringUtf8()) {
          //本地块的前一块的hash与远端前导块的前一块的hash一致，可以调用块回滚到前一个块，直接调用存储持久化代码，进行块回滚，回滚之后载同步
          this.aresult = AnalysisResult(1, "")
          this.raction = RollbackAction(lh - 1)
          this.maxinfo = MaxBlockInfo(maxresponseinfo.response.height, maxresponseinfo.response.currentBlockHash.toStringUtf8())
          this.saction = SynchAction(lh - 1, maxheight, maxresponseinfo.responser)
        } else {
          //本地块的前一块的hash与远端前导块的前一块的hash不一致，输出错误，停止同步
          this.aresult = AnalysisResult(0, s"等于本地高度，本地块的前一块的hash与远端前导块的前一块的hash不一致，输出错误，停止同步,local height=${lh},maxheight=${maxheight}")
          RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------error,大于本地高度，本地块的前一块的hash与远端前导块的前一块的hash不一致，输出错误，停止同步,local height=${lh},maxheight=${maxheight}"))
        }
      }
    } else {
      this.aresult = AnalysisResult(0, "小于本地高度，不需要同步")
      RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------error,小于本地高度，不需要同步，停止同步"))
    }
  }
}
