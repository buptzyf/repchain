package rep.network.consensus.cfrd.block

import akka.actor.Props
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.autotransaction.Topic
import rep.network.consensus.cfrd.MsgOfCFRD.{CollectEndorsement, CreateBlock, ForceVoteInfo, VoteOfBlocker}
import rep.network.consensus.common.block.IBlocker
import rep.network.module.cfrd.CFRDActorType
import rep.network.util.NodeHelp
import rep.proto.rc2.{Block, Event}
import rep.utils.GlobalUtils.{BlockerInfo, EventType}

/**
 * Created by jiangbuyun on 2020/03/17.
 * CFRD共识协议的出块人actor
 */

object BlockerOfCFRD {
  def props(name: String): Props = Props(classOf[BlockerOfCFRD], name)
}

class BlockerOfCFRD(moduleName: String) extends IBlocker(moduleName){
  var preblock: Block = null
  var blockerInfo : BlockerInfo = null

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix("CFRDBlocker module start"))
    super.preStart()
  }

  private def CreateBlockHandler = {
    var blc : Block = null

    if (blc ==null)
      blc = PackedBlock(0)
    if (blc != null) {
      RepTimeTracer.setEndTime(pe.getSysTag, "createBlock", System.currentTimeMillis(), blc.getHeader.height, blc.transactions.size)
      this.preblock = blc
      this.blockerInfo = pe.getBlocker
      schedulerLink = clearSched()
      //在发出背书时，告诉对方我是当前出块人，取出系统的名称
      RepTimeTracer.setStartTime(pe.getSysTag, "Endorsement", System.currentTimeMillis(), blc.getHeader.height, blc.transactions.size)
      //RepLogger.print(RepLogger.zLogger,"send CollectEndorsement, " + pe.getSysTag
      //  + ", " + pe.getCurrentBlockHash+ ", " + blc.previousBlockHash.toStringUtf8)
      //pe.getActorRef(CFRDActorType.ActorType.endorsementcollectioner) ! CollectEndorsement(this.preblock, pe.getSysTag)
      //pe.getActorRef(CFRDActorType.ActorType.endorsementcollectioner) ! CollectEndorsement(this.preblock, pe.getSysTag,pe.getBlocker.VoteIndex)
      pe.getActorRef(CFRDActorType.ActorType.endorsementcollectioner) ! CollectEndorsement(this.preblock, ForceVoteInfo(this.blockerInfo.voteBlockHash,this.blockerInfo.VoteHeight,this.blockerInfo.VoteIndex,pe.getSysTag))
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
          if (preblock == null || (preblock.getHeader.hashPrevious.toStringUtf8() != pe.getBlocker.voteBlockHash)) {
            CreateBlockHandler
          }else{
            if(preblock != null && preblock.getHeader.hashPrevious.toStringUtf8() == pe.getBlocker.voteBlockHash && this.blockerInfo != null
              && this.blockerInfo.VoteIndex < pe.getBlocker.VoteIndex ){
              //这种情况说明系统在出块超时重新抽签是又抽到了自己，由于之前已经在同一高度上建立了新块，但是在背书上已经失败，背书失败的原因是可能是抽签不同步导致背书失败。
              // 由于在窄带上考虑带宽的问题，限制了背书信息的发送，背书发送模块停止，在此发送消息，启动再次背书。
              //此处就是发送再次背书的消息，以启动出块
              //更新抽签索引
              this.blockerInfo = pe.getBlocker
              //发送背书启动消息给背书收集器
              RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"created new block,restart endorsement,new height=${this.preblock.getHeader.height},local height=${pe.getCurrentHeight}" + "~" + selfAddr))
              //pe.getActorRef(CFRDActorType.ActorType.endorsementcollectioner) ! CollectEndorsement(this.preblock, pe.getSysTag)
              //pe.getActorRef(CFRDActorType.ActorType.endorsementcollectioner) ! CollectEndorsement(this.preblock, pe.getSysTag,pe.getBlocker.VoteIndex)
              pe.getActorRef(CFRDActorType.ActorType.endorsementcollectioner) ! CollectEndorsement(this.preblock, ForceVoteInfo(this.blockerInfo.voteBlockHash,this.blockerInfo.VoteHeight,this.blockerInfo.VoteIndex,pe.getSysTag))
            }
          }
        } else {
          //出块标识错误,暂时不用做任何处理
          RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,do not blocker or blocker hash not equal current hash,height=${pe.getCurrentHeight}" + "~" + selfAddr))
        }
      } else {
        //节点状态不对
        RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,node status error,status is synching,height=${pe.getCurrentHeight}" + "~" + selfAddr))
      }

      //zhjtps
   /* case CreateBlockTPS(ts : Seq[Transaction], trs : Seq[TransactionResult]) =>
      tsTPS = ts
      trsTPS = trs*/

    case _ => //ignore
  }
}
