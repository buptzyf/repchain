package rep.network.sync.parser

import akka.actor.ActorRef
import rep.log.RepLogger
import rep.network.sync.SyncMsg._
import rep.network.tools.NodeMgr
import rep.network.util.NodeHelp
import rep.protos.peer.BlockchainInfo
import rep.storage.ImpDataAccess

import scala.util.control.Breaks.{break, breakable}

/**
 * Created by jiangbuyun on 2020/03/18.
 * 同步区块信息分析的抽象类
 */

abstract class ISynchAnalyzer(val systemName: String, val lchaininfo: BlockchainInfo, val nodemgr: NodeMgr) {
  protected var aresult: AnalysisResult = null
  protected var saction: SynchAction = null
  protected var raction: RollbackAction = null
  protected var maxinfo: MaxBlockInfo = null

  def getResult: AnalysisResult = {
    this.aresult
  }

  def getSynchActiob: SynchAction = {
    this.saction
  }

  def getRollbackAction: RollbackAction = {
    this.raction
  }

  def getMaxBlockInfo: MaxBlockInfo = {
    this.maxinfo
  }

  protected def checkHashAgreement(h: Long, ls: List[ResponseInfo], ns: Int, checkType: Int): (Boolean, String) = {
    val hls = ls.filter(_.response.height == h)
    var gls: List[(String, Int)] = null
    checkType match {
      case 1 =>
        //检查远端的最后一个块的hash的一致性
        gls = hls.groupBy(x => x.response.currentBlockHash.toStringUtf8()).map(x => (x._1, x._2.length)).toList.sortBy(x => -x._2)
      case 2 =>
        //检查远端指定高度块的一致性
        gls = hls.groupBy(x => x.ChainInfoOfSpecifiedHeight.currentBlockHash.toStringUtf8()).map(x => (x._1, x._2.length)).toList.sortBy(x => -x._2)
    }
    val tmpgHash = gls.head._1
    val tmpgCount = gls.head._2
    if (NodeHelp.ConsensusConditionChecked(tmpgCount, ns)) {
      (true, tmpgHash)
    } else {
      (false, "")
    }
  }

  protected def getLogMsgPrefix(msg: String): String = {
    s"${systemName}~synchronized requester~${msg}~"
  }

  protected def greaterThanLocalHeight(reslist: List[ResponseInfo], maxheight: Long, AgreementHash: String) = {
    val lh = this.lchaininfo.height
    val nodes = this.nodemgr.getStableNodes
    val lhash = this.lchaininfo.currentBlockHash.toStringUtf8()
    val lprehash = this.lchaininfo.previousBlockHash.toStringUtf8()
    //待同步高度大于本地高度
    if (lh == 0) {
      //当前本地高度0，没有前序块，不需要检查前序块hash，可以直接进入同步
      this.aresult = AnalysisResult(1, "")
      this.saction = SynchAction(lh, maxheight, reslist.filter(_.response.height == maxheight).head.responser)
      RepLogger.info(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------info,大于本地高度，同步完成,local height=${lh},maxheight=${maxheight}"))
    } else {
      //本地高度大于0，需要检查前序块的hash是否一致
      val leadingAgreementResult = checkHashAgreement(maxheight, reslist, nodes.size, 2)
      if (leadingAgreementResult._1) {
        //远端前序块hash检查一致
        if (leadingAgreementResult._2 == lhash) {
          //本地最后块的hash与远端的前序块hash一致，可以直接进入同步
          this.aresult = AnalysisResult(1, "")
          this.saction = SynchAction(lh, maxheight, reslist.filter(_.response.height == maxheight).
            filter(_.response.currentBlockHash.toStringUtf8() == AgreementHash).
            filter(_.ChainInfoOfSpecifiedHeight.currentBlockHash.toStringUtf8() == leadingAgreementResult._2).head.responser)
          RepLogger.info(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------info,大于本地高度，同步完成,local height=${lh},maxheight=${maxheight}"))
        } else {
          //由于与远端的前序块的hash不一致，需要进一步进入回滚当前块的判断
          val fls = reslist.filter(_.response.height == maxheight).
            filter(_.ChainInfoOfSpecifiedHeight.currentBlockHash.toStringUtf8() == leadingAgreementResult._2)
          if (fls.nonEmpty) {
            val chinfo = fls.head
            if (lprehash == chinfo.ChainInfoOfSpecifiedHeight.previousBlockHash.toStringUtf8()) {
              //本地块的前一块的hash与远端前导块的前一块的hash一致，可以调用块回滚到前一个块，直接调用存储持久化代码，进行块回滚，回滚之后载同步

              this.aresult = AnalysisResult(1, "")
              this.raction = RollbackAction(lh - 1)
              this.saction = SynchAction(lh, maxheight, reslist.filter(_.response.height == maxheight).
                filter(_.response.currentBlockHash.toStringUtf8() == AgreementHash).
                filter(_.ChainInfoOfSpecifiedHeight.currentBlockHash.toStringUtf8() == leadingAgreementResult._2).head.responser)
            } else {
              //本地块的前一块的hash与远端前导块的前一块的hash不一致，输出错误，停止同步
              this.aresult = AnalysisResult(0, s"大于本地高度，本地块的前一块的hash与远端前导块的前一块的hash不一致，输出错误，停止同步,local height=${lh},maxheight=${maxheight}")
              RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------error,大于本地高度，本地块的前一块的hash与远端前导块的前一块的hash不一致，输出错误，停止同步,local height=${lh},maxheight=${maxheight}"))
            }
          } else {
            //找不到正确的响应信息，输出错误，停止同步
            this.aresult = AnalysisResult(0, "大于本地高度，找不到正确的响应信息，输出错误，停止同步,local height=${lh},maxheight=${maxheight}")
            RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------error,大于本地高度，找不到正确的响应信息，输出错误，停止同步,local height=${lh},maxheight=${maxheight}"))
          }
        }
      } else {
        //远端前序块hash不一致，输出错误，停止同步
        this.aresult = AnalysisResult(0, s"大于本地高度，远端前序块hash不一致，输出错误，停止同步,local height=${lh},maxheight=${maxheight}")
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------error,大于本地高度，远端前序块hash不一致，输出错误，停止同步,local height=${lh},maxheight=${maxheight}"))
      }
    }
  }

  protected def FindSeedNode(reslist: List[ResponseInfo]): ActorRef = {
    var r: ActorRef = null
    val resinfo = reslist.filter(_.response.height == 1)
    if (resinfo.nonEmpty) {
      var actorref: ActorRef = null
      breakable(
        resinfo.foreach(f => {
          if (NodeHelp.isSeedNode(nodemgr.getStableNodeName4Addr(f.responser.path.address))) {
            r = f.responser
            break
          }
        }))
    }
    r
  }

  protected def equalLocalHeight(reslist: List[ResponseInfo], maxheight: Long, AgreementHash: String) = {
    val lh = this.lchaininfo.height
    val nodes = this.nodemgr.getStableNodes
    val lhash = this.lchaininfo.currentBlockHash.toStringUtf8()
    val lprehash = this.lchaininfo.previousBlockHash.toStringUtf8()
    //待同步高度等于本地高度
    if (lh == 0) {
      //当前本地高度0，又与大多数节点相同，说明系统处于初始状态，寻找种子节点是否已经建立创世块
      val seedactor = FindSeedNode(reslist)
      if (seedactor != null) {
        this.aresult = AnalysisResult(1, "")
        this.saction = SynchAction(lh, 1, seedactor)
        RepLogger.info(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------info,等于本地高度，找到创世节点，启动同步创世块,local height=${lh},maxheight=${maxheight}"))
      } else {
        ////没有找到创世节点，输出信息，停止同步
        this.aresult = AnalysisResult(0, "--------error,等于本地高度，//找到创世节点，输出信息，停止同步")
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------error,等于本地高度，//找到创世节点，输出信息，停止同步,local height=${lh},maxheight=${maxheight}"))
      }
    } else {
      //系统非初始状态，检查hash一致性
      //当前最后高度的块hash一致，检查自己是否与他们相等
      if (lhash == AgreementHash) {
        //自己同大多数节点hash一致，完成同步
        this.aresult = AnalysisResult(1, "")
        RepLogger.info(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------info,等于本地高度，自己同大多数节点hash一致，完成同步,local height=${lh},maxheight=${maxheight}"))
      } else {
        //开始检查回滚
        val tmpres = reslist.filter(_.response.height == maxheight).
          filter(_.response.currentBlockHash.toStringUtf8() == AgreementHash).head
        if (lprehash == tmpres.response.previousBlockHash.toStringUtf8()) {
          //可以开始回滚
          this.aresult = AnalysisResult(1, "")
          this.raction = RollbackAction(lh - 1)
          this.saction = SynchAction(lh - 1, maxheight, tmpres.responser)
          RepLogger.info(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------info,等于本地高度，回滚完成，完成同步,local height=${lh},maxheight=${maxheight}"))
        } else {
          //前一个块的hash不一致，输出错误，停止同步
          this.aresult = AnalysisResult(0, s"等于本地高度，前一个块的hash不一致，输出错误，停止同步,local height=${lh},maxheight=${maxheight}")
          RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------error,等于本地高度，前一个块的hash不一致，输出错误，停止同步,local height=${lh},maxheight=${maxheight}"))
        }
      }
    }
  }

  protected def lessThanLocalHeight(maxheight: Long, AgreementHash: String) = {
    val lh = this.lchaininfo.height

    //待同步高度小于本地高度
    if (maxheight == 0) {
      //待同步高度等于0，本地高于待同步高度，说明本节点是种子节点，并且高度必须是1
      if (lh == 1) {
        if (NodeHelp.isSeedNode(this.systemName)) {
          //当前系统是种子节点，并且是创世块，完成同步
          this.aresult = AnalysisResult(1, "小于本地高度，当前系统是种子节点，并且是创世块，完成同步")
          RepLogger.info(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------info,小于本地高度，当前系统是种子节点，并且是创世块，完成同步,local height=${lh},maxheight=${maxheight}"))
        } else {
          this.aresult = AnalysisResult(1, "小于本地高度，当前系统不是种子节点，可能是创世块，完成同步")
          RepLogger.info(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------info,小于本地高度，当前系统不是种子节点，只有创世块，完成同步,local height=${lh},maxheight=${maxheight}"))
        }
      } else {
        this.aresult = AnalysisResult(0, "小于本地高度，当前系统不是种子节点，高度大于1，系统错误，停止同步")
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------error,小于本地高度，当前系统不是种子节点，高度大于1，系统错误，停止同步,local height=${lh},maxheight=${maxheight}"))
      }
    } else {
      //大多数节点的高度，hash相同，本地节点高于这些节点，需要回滚，检查回滚信息
      val da = ImpDataAccess.GetDataAccess(this.systemName)
      val block = da.getBlockByHash(AgreementHash)
      if (block != null) {
        //需要回滚
        this.aresult = AnalysisResult(1, "")
        this.raction = RollbackAction(maxheight)
      } else {
        this.aresult = AnalysisResult(0, "小于本地高度，本地找不到对应大多数节点的hash的块，停止同步")
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------error,小于本地高度，本地找不到对应大多数节点的hash的块，停止同步,local height=${lh},maxheight=${maxheight}"))
      }
    }
  }


   def Parser(reslist: List[ResponseInfo], isStartupSynch: Boolean)

}
