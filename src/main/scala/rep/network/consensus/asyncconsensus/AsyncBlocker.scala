package rep.network.consensus.asyncconsensus

import akka.actor.{ActorRef, Props}
import akka.util.Timeout
import rep.app.conf.{SystemProfile, TimePolicy}
import rep.log.{RepLogger, RepTimeTracer}
import rep.network.base.ModuleBase
import rep.network.consensus.asyncconsensus.AsyncBlocker.{startup}
import rep.network.consensus.asyncconsensus.config.ConfigOfManager
import rep.network.consensus.asyncconsensus.protocol.block.{AsyncBlock, AsyncBlockInActor}
import rep.network.consensus.asyncconsensus.protocol.block.AsyncBlock.{StartBlock, TPKEShare, TransactionsOfBlock}
import rep.network.consensus.common.MsgOfConsensus.{ConfirmedBlock, GenesisBlock, PreTransBlock, PreTransBlockResult}
import rep.network.consensus.util.BlockHelp
import rep.network.module.ModuleActorType
import rep.network.module.cfrd.CFRDActorType
import rep.protos.peer.{Block, Transaction}
import rep.storage.{ImpDataAccess, ImpDataPreloadMgr}
import scala.concurrent.Await
import akka.pattern.{AskTimeoutException, ask}
import rep.network.consensus.byzantium.ConsensusCondition
import rep.network.sync.SyncMsg.StartSync
import rep.network.util.NodeHelp

object AsyncBlocker{
  def props( moduleName: String, nodeName:String, pathSchema:String,moduleSchema:String, numOfNode:Int, numOfFault:Int,  caller:ActorRef): Props =
    Props(classOf[AsyncBlocker],moduleName, nodeName, pathSchema,moduleSchema, numOfNode, numOfFault, caller)

  case object startup
  case object startPackage
}

class AsyncBlocker (moduleName: String,  var nodeName:String, pathSchema:String,moduleSchema:String,var numOfNode:Int, var numOfFault:Int, var caller:ActorRef) extends ModuleBase(moduleName)  {
  println(s"create AsyncBlockInActor actor addr=${this.selfAddr}")
  import context.dispatcher
  import scala.concurrent.duration._

  val async_block = context.actorOf(AsyncBlockInActor.props(this.nodeName+"-asyncblock", this.nodeName,this.pathSchema+"/"+moduleSchema,"{nodeName}"++"-asyncblock",this.numOfNode,this.numOfFault,self), this.nodeName+"-asyncblock")

  val dataaccess: ImpDataAccess = ImpDataAccess.GetDataAccess(pe.getSysTag)
  private var round:Long=pe.getCurrentHeight
  implicit val timeout = Timeout((TimePolicy.getTimeoutPreload * 6).seconds)
  private val asyncConfig = ConfigOfManager.getManager

  protected def getSystemBlockHash: String = {
    if (pe.getCurrentBlockHash == "") {
      pe.resetSystemCurrentChainStatus(dataaccess.getBlockChainInfo())
    }
    pe.getCurrentBlockHash
  }

  protected def startupHandler(isForce:Boolean) = {
    schedulerLink = clearSched()
    println(s"¥¥¥¥¥¥¥¥¥¥¥ startupHandler nodename=${this.nodeName},block height = ${pe.getCurrentHeight},round=${this.round}")
    if (ConsensusCondition.CheckWorkConditionOfSystem(pe.getNodeMgr.getStableNodes.size)) {
      //只有共识节点符合要求之后开始工作
      if (getSystemBlockHash == "") {
        //系统属于初始化状态
        if (NodeHelp.isSeedNode(pe.getSysTag)) {
          // 建立创世块消息
          pe.getActorRef(CFRDActorType.ActorType.gensisblock) ! GenesisBlock //zhj CFRD?
        }else{
            pe.getActorRef(CFRDActorType.ActorType.synchrequester) ! StartSync(false)
        }
      } else {
        if (!pe.isSynching) {
          this.round = pe.getCurrentHeight
          StartHandle
        }else{
          schedulerLink = scheduler.scheduleOnce((
            TimePolicy.getStableTimeDur).millis, self, startup)
        }
      }
    }else{
      schedulerLink = scheduler.scheduleOnce((
        TimePolicy.getStableTimeDur).millis, self, startup)
    }
  }

  protected def CollectedTransOfBlock(start: Int, num: Int, limitsize: Int): Seq[Transaction] = {
    try {
      val tmplist = pe.getTransPoolMgr.getTransListClone(start, num, pe.getSysTag)
      if (tmplist.size > 0) {
        val currenttime = System.currentTimeMillis() / 1000
        tmplist
      }else{
        if(pe.getTransPoolMgr.getTransLength()>0)
          CollectedTransOfBlock(start + num, num, limitsize)
        else
          Seq.empty
      }
    } finally {
    }
  }

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.Consensus_Logger, this.getLogMsgPrefix( s"${this.selfAddr} Start"))
  }

  private def StartHandle:Unit={
    schedulerLink = clearSched()
    println(s"¥¥¥¥¥¥¥¥¥¥¥ starthandle nodename=${this.nodeName},block height = ${pe.getCurrentHeight},round=${this.round}")
    val trans = this.CollectedTransOfBlock(0,SystemProfile.getLimitBlockTransNum/asyncConfig.getNodeCount,SystemProfile.getBlockLength).reverse
    if (trans.size >= SystemProfile.getMinBlockTransNum) {
      RepTimeTracer.setStartTime(pe.getSysTag, "createBlock", System.currentTimeMillis(),pe.getCurrentHeight,trans.size)
      this.round += 1
      println(s"¥¥¥¥¥¥¥¥¥¥¥ starthandle block nodename=${this.nodeName},block height = ${pe.getCurrentHeight},round=${this.round}")
      val msg = new StartBlock(this.round.toString,this.nodeName,trans)
      async_block ! msg
    }else{
      schedulerLink = scheduler.scheduleOnce((
        TimePolicy.getStableTimeDur).millis, self, startup)
    }
  }

  private def AsyncResultHandle(result:TransactionsOfBlock):Unit={
    val trans = result.transList.sortBy(f=>(f.id))
    var blc = BlockHelp.WaitingForExecutionOfBlock(pe.getCurrentBlockHash, pe.getCurrentHeight + 1, trans)
    RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,height=${blc.height},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
    blc = ExecuteTransactionOfBlock(blc)
    if (blc != null) {
      RepTimeTracer.setEndTime(pe.getSysTag, "PreloadTrans", System.currentTimeMillis(), blc.height, blc.transactions.size)
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,prelaod success,height=${blc.height},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
      //块hash在预执行中生成
      //blc = BlockHelp.AddBlockHash(blc)
      RepLogger.trace(RepLogger.Consensus_Logger, this.getLogMsgPrefix(s"create new block,AddBlockHash success,height=${blc.height},local height=${pe.getBlocker.VoteHeight}" + "~" + selfAddr))
      //BlockHelp.AddSignToBlock(blc, pe.getSysTag)
      pe.getActorRef(CFRDActorType.ActorType.confirmerofblock) ! ConfirmedBlock(blc, self)
      RepTimeTracer.setEndTime(pe.getSysTag, "createBlock", System.currentTimeMillis(), blc.height, blc.transactions.size)
      //self ! startup
    }
  }

    protected def ExecuteTransactionOfBlock(block: Block): Block = {
      try {
        val future = pe.getActorRef(ModuleActorType.ActorType.dispatchofpreload) ? PreTransBlock(block, "preload-"+block.transactions(0).id)
        val result = Await.result(future, timeout.duration).asInstanceOf[PreTransBlockResult]
        if (result.result) {
          result.blc
        } else {
          null
        }
      } catch {
        case e: AskTimeoutException => null
      }finally {
        ImpDataPreloadMgr.Free(pe.getSysTag,"preload-"+block.transactions(0).id)
      }
    }

  override def receive: Receive = {
    case result:TransactionsOfBlock=>
      println(s"¥¥¥¥¥¥¥¥¥¥¥ TransactionsOfBlock nodename=${this.nodeName},block height = ${pe.getCurrentHeight},round=${this.round}")
      if(this.round.toString == result.round && this.round == pe.getCurrentHeight+1){
        AsyncResultHandle(result)
      }else{
        println(s"block height = ${pe.getCurrentHeight},round=${this.round}")
      }
    case startup=>
      this.startupHandler(false)
  }

}