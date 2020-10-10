package rep.network.consensus.cfrd.block

import akka.actor.Props
import rep.app.conf.SystemProfile
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.autotransaction.Topic
import rep.network.consensus.cfrd.MsgOfCFRD.{BlockInfoOfConsensus, CollectEndorsement, CreateBlock, RequestPreLoadBlock}
import rep.network.consensus.common.block.IBlocker
import rep.network.consensus.util.BlockHelp
import rep.network.module.cfrd.CFRDActorType
import rep.network.util.NodeHelp
import rep.protos.peer.{Block, Event, Transaction}
import rep.utils.GlobalUtils.{BlockerInfo, EventType}
import scala.util.control.Breaks.{break, breakable}


object BlockerOfCFRDInStream {
  def props(name: String): Props = Props(classOf[BlockerOfCFRDInStream], name)
}

class BlockerOfCFRDInStream(moduleName: String) extends IBlocker(moduleName) {
  private var currentVoteInfo: BlockerInfo = null
  private var preloadBlocks: Seq[BlockInfoOfConsensus] = Seq.empty[BlockInfoOfConsensus]


  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("BlockerOfCFRDInStream module start"))
    super.preStart()
  }

  protected def createBlock(trans: Seq[Transaction], h: Long, prevHash: String): Block = {
    RepTimeTracer.setStartTime(pe.getSysTag, "createBlock", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
    //此处建立新块必须采用抽签模块的抽签结果来进行出块，否则出现刚抽完签，马上有新块的存储完成，就会出现错误
    var blc = BlockHelp.WaitingForExecutionOfBlock(prevHash, h, trans)
    RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,height=${blc.height},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
    RepTimeTracer.setStartTime(pe.getSysTag, "PreloadTrans", System.currentTimeMillis(), blc.height, blc.transactions.size)
    blc = ExecuteTransactionOfBlockInStream(blc,this.currentVoteInfo.voteBlockHash)

    RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans", System.currentTimeMillis(), blc.height, blc.transactions.size)
    RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,prelaod success,height=${blc.height},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
    //块hash在预执行中生成
    //blc = BlockHelp.AddBlockHash(blc)
    RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,AddBlockHash success,height=${blc.height},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
    BlockHelp.AddSignToBlock(blc, pe.getSysTag)
  }


  private def CreateBlockHandler = {

    val blcCount = SystemProfile.getBlockNumberOfBlocker
    if (this.preloadBlocks.isEmpty) {
      var start = 0
      var initHeight = this.currentVoteInfo.VoteHeight + 1
      var prevHash = this.currentVoteInfo.voteBlockHash
      RepTimeTracer.setStartTime(pe.getSysTag, "Block", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
      breakable(
        op = for (i <- 1 to blcCount) {
          RepTimeTracer.setStartTime(pe.getSysTag, "collectTransToBlock", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, 0)
          val tmpList = pe.getTransPoolMgr.getTransListClone(SystemProfile.getLimitBlockTransNum, pe.getSysTag).reverse
          RepTimeTracer.setEndTime(pe.getSysTag, "collectTransToBlock", System.currentTimeMillis(), pe.getBlocker.VoteHeight + 1, tmpList.size)
          var isLast = false
          if (tmpList.length >= SystemProfile.getLimitBlockTransNum && i < blcCount) {
            isLast = false
            val tmpList1 = pe.getTransPoolMgr.getTransListClone(SystemProfile.getLimitBlockTransNum, pe.getSysTag)
            if (tmpList1.length <= 0) {
              isLast = true
            }
          } else {
            isLast = true
          }
          var isFirst = false
          if(i == 1) isFirst = true

          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,CollectedTransOfBlock success,height=${pe.getBlocker.VoteHeight + 1},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
          var blc = createBlock(tmpList, initHeight, prevHash)
          start = start + tmpList.length
          initHeight = initHeight + 1
          prevHash = blc.hashOfBlock.toStringUtf8
          val bc = BlockInfoOfConsensus(this.currentVoteInfo, pe.getSysTag, blc, i - 1,isFirst, isLast)
          this.preloadBlocks = this.preloadBlocks :+ bc
          //发出背书消息
          pe.getActorRef(CFRDActorType.ActorType.endorsementcollectioner) ! bc
          RepTimeTracer.setStartTime(pe.getSysTag, "Endorsement-"+blc.height.toString, System.currentTimeMillis(), blc.height, blc.transactions.size)
          if (isLast) break
        })
    }
  }

  override def receive = {
    //创建块请求（给出块人）
    case CreateBlock =>
      if (!pe.isSynching) {
        val voteInfo = pe.getBlocker
        if (NodeHelp.isBlocker(voteInfo.blocker, pe.getSysTag)) {
          sendEvent(EventType.PUBLISH_INFO, mediator, pe.getSysTag, Topic.Block, Event.Action.CANDIDATOR)
          if (this.currentVoteInfo == null) {
            //直接进入预出块
            this.currentVoteInfo = voteInfo
            this.preloadBlocks = Seq.empty[BlockInfoOfConsensus]
            CreateBlockHandler
          } else if (this.isChangeBlocker(voteInfo)) {
            //出块人切换，检查预出块中是否存在预出块
            this.currentVoteInfo = voteInfo
            this.preloadBlocks = Seq.empty[BlockInfoOfConsensus]
            CreateBlockHandler
          }
        } else {
          //本节点不是出块人
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,node is not Blocker,,height=${pe.getCurrentHeight}" + "~" + selfAddr))
        }
      } else {
        //节点状态不对
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,node status error,status is synching,height=${pe.getCurrentHeight}" + "~" + selfAddr))
      }
    case _ => //ignore
  }
}
