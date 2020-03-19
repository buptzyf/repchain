package rep.network.consensus.cfrd.block

import akka.actor.Props
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.autotransaction.Topic
import rep.network.consensus.cfrd.MsgOfCFRD.CreateBlock
import rep.network.consensus.cfrd.MsgOfCFRD.VoteOfBlocker
import rep.network.consensus.common.block.IBlocker
import rep.network.module.cfrd.CFRDActorType
import rep.network.util.NodeHelp
import rep.protos.peer.{Block, Event}
import rep.utils.GlobalUtils.EventType
import rep.network.consensus.cfrd.MsgOfCFRD.CollectEndorsement

/**
 * Created by jiangbuyun on 2020/03/17.
 * CFRD共识协议的出块人actor
 */

object BlockerOfCFRD {
  def props(name: String): Props = Props(classOf[BlockerOfCFRD], name)
}

class BlockerOfCFRD(moduleName: String) extends IBlocker(moduleName){
  var preblock: Block = null

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("CFRDBlocker module start"))
    super.preStart()
  }

  private def CreateBlockHandler = {
    var blc : Block = null

    //blc = PackedBlock(0,pe.getBlocker.VoteHeight + 1)
    blc = PackedBlock(0)
    if (blc != null) {
      RepTimeTracer.setEndTime(pe.getSysTag, "createBlock", System.currentTimeMillis(), blc.height, blc.transactions.size)
      this.preblock = blc
      schedulerLink = clearSched()
      //在发出背书时，告诉对方我是当前出块人，取出系统的名称
      RepTimeTracer.setStartTime(pe.getSysTag, "Endorsement", System.currentTimeMillis(), blc.height, blc.transactions.size)
      pe.getActorRef(CFRDActorType.ActorType.endorsementcollectioner) ! CollectEndorsement(this.preblock, pe.getSysTag)
    } else {
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix("create new block error,CreateBlock is null" + "~" + selfAddr))
      pe.getActorRef(CFRDActorType.ActorType.voter) ! VoteOfBlocker
    }
  }

  override def receive = {
    //创建块请求（给出块人）
    case CreateBlock =>
      if (!pe.isSynching) {
        if (NodeHelp.isBlocker(pe.getBlocker.blocker, pe.getSysTag) && pe.getBlocker.voteBlockHash == pe.getCurrentBlockHash) {
          sendEvent(EventType.PUBLISH_INFO, mediator, pe.getSysTag, Topic.Block, Event.Action.CANDIDATOR)
          //是出块节点
          if (preblock == null || (preblock.previousBlockHash.toStringUtf8() != pe.getBlocker.voteBlockHash)) {
            CreateBlockHandler
          }
        } else {
          //出块标识错误,暂时不用做任何处理
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,do not blocker or blocker hash not equal current hash,height=${pe.getCurrentHeight}" + "~" + selfAddr))
        }
      } else {
        //节点状态不对
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,node status error,status is synching,height=${pe.getCurrentHeight}" + "~" + selfAddr))
      }

    case _ => //ignore
  }
}
