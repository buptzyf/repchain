package rep.network.consensus.common.vote

import akka.actor.Props
import rep.crypto.Sha256
import rep.log.RepLogger
import rep.network.base.ModuleBase
import rep.network.consensus.byzantium.ConsensusCondition
import rep.network.consensus.cfrd.MsgOfCFRD.ForceVoteInfo
import rep.network.consensus.common.algorithm.IAlgorithmOfVote
import rep.network.module.cfrd.CFRDActorType
import rep.network.util.NodeHelp
import rep.utils.GlobalUtils.BlockerInfo
import rep.network.consensus.common.MsgOfConsensus.GenesisBlock
import rep.network.sync.SyncMsg.StartSync
import rep.storage.chain.block.BlockSearcher
/**
 * Created by jiangbuyun on 2020/03/17.
 * 抽象抽签类
 */

object IVoter{
  def props(name: String): Props = Props(classOf[IVoter], name)
}

abstract class IVoter(moduleName: String) extends ModuleBase(moduleName) {
  val searcher = pe.getRepChainContext.getBlockSearch

  protected var candidator: Array[String] = Array.empty[String]
  protected var Blocker: BlockerInfo = BlockerInfo("", -1, Long.MaxValue, "", -1)
  protected var voteCount = 0
  protected var algorithmInVoted:IAlgorithmOfVote = null
  private var InitDelayTime : Long = -1


  protected def checkTranNum: Boolean = {
    pe.getRepChainContext.getTransactionPool.getCachePoolSize >= pe.getRepChainContext.getConfig.getMinTransactionNumberOfBlock
  }

  protected def cleanVoteInfo = {
    this.voteCount = 0
    candidator = Array.empty[String]
    this.Blocker = BlockerInfo("", -1, Long.MaxValue, "", -1)
    pe.resetBlocker(this.Blocker)
  }

  protected def getSystemBlockHash: String = {
    if (pe.getCurrentBlockHash == "") {
      pe.resetSystemCurrentChainStatus(searcher.getChainInfo)
    }
    pe.getCurrentBlockHash
  }

  protected def resetCandidator(currentblockhash: String) = {
    candidator = algorithmInVoted.candidators(pe.getSysTag, currentblockhash, pe.getRepChainContext.getSystemCertList.getVoteList, pe.getRepChainContext.getHashTool.hash(currentblockhash))
  }

  protected def resetBlocker(idx: Int, currentblockhash: String, currentheight: Long) = {
    if(!candidator.isEmpty){
      RepLogger.trace(RepLogger.Vote_Logger, this.getLogMsgPrefix(s"sysname=${pe.getSysTag},votelist=${candidator.mkString("|")},idx=${idx}"))
      this.Blocker = BlockerInfo(algorithmInVoted.blocker(candidator, idx), idx, System.currentTimeMillis(), currentblockhash, currentheight)
      pe.resetBlocker(this.Blocker)
      NoticeBlockerMsg
    }
  }

  protected def NoticeBlockerMsg:Unit

  protected def DelayVote:Unit

  protected def vote(isForce:Boolean,forceInfo:ForceVoteInfo):Unit



  protected def voteMsgHandler(isForce:Boolean,forceInfo:ForceVoteInfo) = {
    if (new ConsensusCondition(pe.getRepChainContext).CheckWorkConditionOfSystem(pe.getRepChainContext.getNodeMgr.getStableNodes.size)) {
      //只有共识节点符合要求之后开始工作
      if (getSystemBlockHash == "") {
        //系统属于初始化状态
        if (NodeHelp.isSeedNode(pe.getSysTag,pe.getRepChainContext.getConfig.getGenesisNodeName)) {
          // 建立创世块消息
          pe.getActorRef(CFRDActorType.ActorType.gensisblock) ! GenesisBlock //zhj CFRD?
        }else{
          if(this.InitDelayTime == -1){
            this.InitDelayTime = System.currentTimeMillis()
          }else if((System.currentTimeMillis()- this.InitDelayTime)/1000 > 10){
            this.InitDelayTime = -1
            pe.getActorRef(CFRDActorType.ActorType.synchrequester) ! StartSync(false)
          }
        }
      } else {
        if (!pe.isSynching) {
          vote(isForce,forceInfo)
        }
      }
    }
    DelayVote
  }
}
