package rep.network.sync.parser.cfrd

import rep.log.RepLogger
import rep.network.consensus.byzantium.ConsensusCondition
import rep.network.sync.SyncMsg.AnalysisResult
import rep.network.sync.SyncMsg
import rep.network.sync.parser.ISynchAnalyzer
import rep.network.tools.NodeMgr
import rep.network.util.NodeHelp
import rep.proto.rc2.BlockchainInfo

/**
 * Created by jiangbuyun on 2020/03/18.
 * 基于CFRD共识协议的同步区块信息分析的实现类
 */

class ICFRDOfSynchAnalyzer(systemName: String, lchaininfo: BlockchainInfo, nodemgr: NodeMgr) extends ISynchAnalyzer( systemName,  lchaininfo,  nodemgr) {

  override def Parser(reslist: List[SyncMsg.ResponseInfo], isStartupSynch: Boolean): Unit = {
    val lh = lchaininfo.height
    val nodes = nodemgr.getStableNodes

    if (ConsensusCondition.ConsensusConditionChecked(reslist.length)) {
      //获取到到chaininfo信息的数量，得到大多数节点的响应，进一步判断同步的策略
      //获取返回的chaininfo信息中，大多数节点的相同高度的最大值
      val heightStatis = reslist.groupBy(x => x.response.height).map(x => (x._1, x._2.length)).toList.sortBy(x => -x._2)
      val maxheight = heightStatis.head._1
      var nodesOfmaxHeight = heightStatis.head._2

      RepLogger.debug(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------Info,获取同步的返回信息，结果=${reslist.mkString("|")}"))

      RepLogger.info(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------Info,获取同步的返回信息，统计结果=${heightStatis.mkString("|")}"))

      if (ConsensusCondition.ConsensusConditionChecked(nodesOfmaxHeight)) {
        //得到了真正大多数节点相同的高度
        val agreementResult = checkHashAgreement(maxheight, reslist, nodes.size, 1)
        if (agreementResult._1) {
          //当前同步高度的最后高度的块hash一致
          if (maxheight > lh) {
            this.greaterThanLocalHeight(reslist, maxheight, agreementResult._2)
          } else if (maxheight == lh) {
            this.equalLocalHeight(reslist, maxheight, agreementResult._2)
          } else {
            if (isStartupSynch) {
              this.lessThanLocalHeight(maxheight, agreementResult._2)
            } else {
              this.aresult = AnalysisResult(1, "")
            }
            RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------info,本地高度大于远端高度，停止同步"))
          }
        } else {
          //当前同步高度的最后高度的块hash不一致，输出错误信息，停止同步
          this.aresult = AnalysisResult(0, "当前同步高度的最后高度的块hash不一致，输出错误信息，停止同步")
          RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------error,当前同步高度的最后高度的块hash不一致，输出错误信息，停止同步"))
        }
      } else {
        //最多数量的高度，达不到共识的要求，输出错误信息停止同步
        this.aresult = AnalysisResult(2, s"最多数量的高度，达不到共识的要求，输出错误信息停止同步 response size=${reslist.size}")
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------error,最多数量的高度，达不到共识的要求，输出错误信息停止同步 response size=${reslist.size}"))
      }
    } else {
      //获取到到chaininfo信息的数量，没有得到大多数节点的响应，输出错误信息停止同步
      this.aresult = AnalysisResult(0, s"获取到到chaininfo信息的数量，没有得到大多数节点的响应，输出错误信息停止同步 response size=${reslist.size}")
      RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------error,获取到到chaininfo信息的数量，没有得到大多数节点的响应，输出错误信息停止同步 response size=${reslist.size}"))
    }
  }

}
