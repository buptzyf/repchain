/*
 * Copyright  2019 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BA SIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package rep.network.sync

import rep.network.sync.SyncMsg.{ ResponseInfo, AnalysisResult, SynchAction, RollbackAction }
import rep.network.util.NodeHelp
import rep.protos.peer.{ BlockchainInfo, Block }
import rep.log.RepLogger
import akka.actor.{ Address, ActorRef }
import rep.storage.ImpDataAccess
import scala.util.control.Breaks._
import rep.network.tools.NodeMgr

class SynchResponseInfoAnalyzer(val systemName: String, val lchaininfo: BlockchainInfo, val nodemgr: NodeMgr) {
  private var aresult: AnalysisResult = null
  private var saction: SynchAction = null
  private var raction: RollbackAction = null

  def getResult: AnalysisResult = {
    this.aresult
  }

  def getSynchActiob: SynchAction = {
    this.saction
  }

  def getRollbackAction: RollbackAction = {
    this.raction
  }

  private def checkHashAgreement(h: Long, ls: List[ResponseInfo], ns: Int, checkType: Int): (Boolean, String) = {
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

  private def getLogMsgPrefix(msg: String): String = {
    s"${systemName}~synchronized requester~${msg}~"
  }

  private def greaterThanLocalHeight(reslist: List[ResponseInfo], maxheight: Long, AgreementHash: String) = {
    val lh = this.lchaininfo.height
    val nodes = this.nodemgr.getNodes
    val lhash = this.lchaininfo.currentBlockHash.toStringUtf8()
    val lprehash = this.lchaininfo.previousBlockHash.toStringUtf8()
    //待同步高度大于本地高度
    if (lh == 0) {
      //当前本地高度0，没有前序块，不需要检查前序块hash，可以直接进入同步
      this.aresult = AnalysisResult(true, "")
      this.saction = SynchAction(lh, maxheight, reslist.filter(_.response.height == maxheight).head.responser)
      RepLogger.info(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------info,大于本地高度，同步完成,local height=${lh},maxheight=${maxheight}"))
    } else {
      //本地高度大于0，需要检查前序块的hash是否一致
      val leadingAgreementResult = checkHashAgreement(maxheight, reslist, nodes.size, 2)
      if (leadingAgreementResult._1) {
        //远端前序块hash检查一致
        if (leadingAgreementResult._2 == lhash) {
          //本地最后块的hash与远端的前序块hash一致，可以直接进入同步
          this.aresult = AnalysisResult(true, "")
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

              this.aresult = AnalysisResult(true, "")
              this.raction = RollbackAction(lh - 1)
              this.saction = SynchAction(lh, maxheight, reslist.filter(_.response.height == maxheight).
                filter(_.response.currentBlockHash.toStringUtf8() == AgreementHash).
                filter(_.ChainInfoOfSpecifiedHeight.currentBlockHash.toStringUtf8() == leadingAgreementResult._2).head.responser)
            } else {
              //本地块的前一块的hash与远端前导块的前一块的hash不一致，输出错误，停止同步
              this.aresult = AnalysisResult(false, s"大于本地高度，本地块的前一块的hash与远端前导块的前一块的hash不一致，输出错误，停止同步,local height=${lh},maxheight=${maxheight}")
              RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------error,大于本地高度，本地块的前一块的hash与远端前导块的前一块的hash不一致，输出错误，停止同步,local height=${lh},maxheight=${maxheight}"))
            }
          } else {
            //找不到正确的响应信息，输出错误，停止同步
            this.aresult = AnalysisResult(false, "大于本地高度，找不到正确的响应信息，输出错误，停止同步,local height=${lh},maxheight=${maxheight}")
            RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------error,大于本地高度，找不到正确的响应信息，输出错误，停止同步,local height=${lh},maxheight=${maxheight}"))
          }
        }
      } else {
        //远端前序块hash不一致，输出错误，停止同步
        this.aresult = AnalysisResult(false, s"大于本地高度，远端前序块hash不一致，输出错误，停止同步,local height=${lh},maxheight=${maxheight}")
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------error,大于本地高度，远端前序块hash不一致，输出错误，停止同步,local height=${lh},maxheight=${maxheight}"))
      }
    }
  }

  private def FindSeedNode(reslist: List[ResponseInfo]): ActorRef = {
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

  private def equalLocalHeight(reslist: List[ResponseInfo], maxheight: Long, AgreementHash: String) = {
    val lh = this.lchaininfo.height
    val nodes = this.nodemgr.getNodes
    val lhash = this.lchaininfo.currentBlockHash.toStringUtf8()
    val lprehash = this.lchaininfo.previousBlockHash.toStringUtf8()
    //待同步高度等于本地高度
    if (lh == 0) {
      //当前本地高度0，又与大多数节点相同，说明系统处于初始状态，寻找种子节点是否已经建立创世块
      val seedactor = FindSeedNode(reslist)
      if (seedactor != null) {
        this.aresult = AnalysisResult(true, "")
        this.saction = SynchAction(lh, 1, seedactor)
        RepLogger.info(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------info,等于本地高度，找到创世节点，启动同步创世块,local height=${lh},maxheight=${maxheight}"))
      } else {
        ////没有找到创世节点，输出信息，停止同步
        this.aresult = AnalysisResult(false, "--------error,等于本地高度，//找到创世节点，输出信息，停止同步")
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------error,等于本地高度，//找到创世节点，输出信息，停止同步,local height=${lh},maxheight=${maxheight}"))
      }
    } else {
      //系统非初始状态，检查hash一致性
      //当前最后高度的块hash一致，检查自己是否与他们相等
      if (lhash == AgreementHash) {
        //自己同大多数节点hash一致，完成同步
        this.aresult = AnalysisResult(true, "")
        RepLogger.info(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------info,等于本地高度，自己同大多数节点hash一致，完成同步,local height=${lh},maxheight=${maxheight}"))
      } else {
        //开始检查回滚
        val tmpres = reslist.filter(_.response.height == maxheight).
          filter(_.response.currentBlockHash.toStringUtf8() == AgreementHash).head
        if (lprehash == tmpres.response.previousBlockHash.toStringUtf8()) {
          //可以开始回滚
          this.aresult = AnalysisResult(true, "")
          this.raction = RollbackAction(lh - 1)
          this.saction = SynchAction(lh - 1, maxheight, tmpres.responser)
          RepLogger.info(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------info,等于本地高度，回滚完成，完成同步,local height=${lh},maxheight=${maxheight}"))
        } else {
          //前一个块的hash不一致，输出错误，停止同步
          this.aresult = AnalysisResult(false, s"等于本地高度，前一个块的hash不一致，输出错误，停止同步,local height=${lh},maxheight=${maxheight}")
          RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------error,等于本地高度，前一个块的hash不一致，输出错误，停止同步,local height=${lh},maxheight=${maxheight}"))
        }
      }
    }
  }

  private def lessThanLocalHeight(maxheight: Long, AgreementHash: String) = {
    val lh = this.lchaininfo.height
    //待同步高度小于本地高度
    if (maxheight == 0) {
      //待同步高度等于0，本地高于待同步高度，说明本节点是种子节点，并且高度必须是1
      if (lh == 1) {
        if (NodeHelp.isSeedNode(this.systemName)) {
          //当前系统是种子节点，并且是创世块，完成同步
          this.aresult = AnalysisResult(true, "小于本地高度，当前系统是种子节点，并且是创世块，完成同步")
          RepLogger.info(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------info,小于本地高度，当前系统是种子节点，并且是创世块，完成同步,local height=${lh},maxheight=${maxheight}"))
        } else {
          this.aresult = AnalysisResult(true, "小于本地高度，当前系统不是种子节点，可能是创世块，完成同步")
          RepLogger.info(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------info,小于本地高度，当前系统不是种子节点，只有创世块，完成同步,local height=${lh},maxheight=${maxheight}"))
        }
      } else {
        this.aresult = AnalysisResult(false, "小于本地高度，当前系统不是种子节点，高度大于1，系统错误，停止同步")
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------error,小于本地高度，当前系统不是种子节点，高度大于1，系统错误，停止同步,local height=${lh},maxheight=${maxheight}"))
      }
    } else {
      //大多数节点的高度，hash相同，本地节点高于这些节点，需要回滚，检查回滚信息
      val da = ImpDataAccess.GetDataAccess(this.systemName)
      val block = da.getBlockByHash(AgreementHash)
      if (block != null) {
        //需要回滚
        this.aresult = AnalysisResult(true, "")
        this.raction = RollbackAction(maxheight)
      } else {
        this.aresult = AnalysisResult(false, "小于本地高度，本地找不到对应大多数节点的hash的块，停止同步")
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------error,小于本地高度，本地找不到对应大多数节点的hash的块，停止同步,local height=${lh},maxheight=${maxheight}"))
      }
    }
  }

  def Parser(reslist: List[ResponseInfo],isStartupSynch:Boolean) = {
    val lh = lchaininfo.height
    val nodes = nodemgr.getNodes

    if (NodeHelp.ConsensusConditionChecked(reslist.length , nodes.size)) {
      //获取到到chaininfo信息的数量，得到大多数节点的响应，进一步判断同步的策略
      //获取返回的chaininfo信息中，大多数节点的相同高度的最大值
      val heightStatis = reslist.groupBy(x => x.response.height).map(x => (x._1, x._2.length)).toList.sortBy(x => -x._2)
      val maxheight = heightStatis.head._1
      var nodesOfmaxHeight = heightStatis.head._2

      if (NodeHelp.ConsensusConditionChecked(nodesOfmaxHeight, nodes.size)) {
        //得到了真正大多数节点相同的高度
        val agreementResult = checkHashAgreement(maxheight, reslist, nodes.size, 1)
        if (agreementResult._1) {
          //当前同步高度的最后高度的块hash一致
          if (maxheight > lh) {
            this.greaterThanLocalHeight(reslist, maxheight, agreementResult._2)
          } else if (maxheight == lh) {
            this.equalLocalHeight(reslist, maxheight, agreementResult._2)
          } else {
            if(isStartupSynch){
              this.lessThanLocalHeight( maxheight, agreementResult._2)
            }else{
              this.aresult = AnalysisResult(true, "")
            }
            RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------info,本地高度大于远端高度，停止同步"))
          }
        } else {
          //当前同步高度的最后高度的块hash不一致，输出错误信息，停止同步
          this.aresult = AnalysisResult(false, "当前同步高度的最后高度的块hash不一致，输出错误信息，停止同步")
          RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix("--------error,当前同步高度的最后高度的块hash不一致，输出错误信息，停止同步"))
        }
      } else {
        //最多数量的高度，达不到共识的要求，输出错误信息停止同步
        this.aresult = AnalysisResult(false, s"最多数量的高度，达不到共识的要求，输出错误信息停止同步 response size=${reslist.size}")
        RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------error,最多数量的高度，达不到共识的要求，输出错误信息停止同步 response size=${reslist.size}"))
      }
    } else {
      //获取到到chaininfo信息的数量，没有得到大多数节点的响应，输出错误信息停止同步
      this.aresult = AnalysisResult(false, s"获取到到chaininfo信息的数量，没有得到大多数节点的响应，输出错误信息停止同步 response size=${reslist.size}")
      RepLogger.error(RepLogger.BlockSyncher_Logger, this.getLogMsgPrefix(s"--------error,获取到到chaininfo信息的数量，没有得到大多数节点的响应，输出错误信息停止同步 response size=${reslist.size}"))
    }
  }

}