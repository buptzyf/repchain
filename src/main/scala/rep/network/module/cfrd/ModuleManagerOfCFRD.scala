package rep.network.module.cfrd

import akka.actor.Props
import rep.log.RepLogger
import rep.network.cache.TransactionOfCollectioner
import rep.network.confirmblock.common.ConfirmOfBlock
import rep.network.consensus.cfrd.block.EndorseCollector
import rep.network.consensus.cfrd.endorse.DispatchOfRecvEndorsement
import rep.network.consensus.cfrd.MsgOfCFRD.VoteOfBlocker
import rep.network.module.{IModuleManager, ModuleActorType}
import rep.network.sync.response.SynchronizeResponser
import rep.network.sync.request.cfrd.SynchRequesterOfCFRD
import rep.network.consensus.cfrd.block.BlockerOfCFRD
import rep.network.consensus.cfrd.vote.VoterOfCFRD
import rep.network.persistence.cfrd.StoragerOfCFRD
import rep.network.sync.request.raft.SynchRequesterOfRAFT


/**
 * Created by jiangbuyun on 2020/03/15.
 * CFRD的模块管理类，继承公共的模块管理类，实现CFRD的自己的模块
 */
object ModuleManagerOfCFRD{
  def props(name: String, isStartup: Boolean): Props = Props(classOf[ModuleManagerOfCFRD], name, isStartup: Boolean)

}

class ModuleManagerOfCFRD(moduleName: String, isStartup: Boolean) extends IModuleManager(moduleName, isStartup){

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix( "ModuleManagerOfCFRD Start"))
  }

  //启动共识模块，启动CFRD共识
  override def startupConsensus: Unit = {
    pe.getActorRef(CFRDActorType.ActorType.voter) ! VoteOfBlocker
  }

  override def loadConsensusModule = {
    //pe.register(ModuleActorType.ActorType.transactionpool, context.actorOf(TransactionPoolOfCFRD.props("transactionpool"), "transactionpool"))
    if (pe.getRepChainContext.getConsensusNodeConfig.getVoteListOfConfig.contains(pe.getSysTag)) {
      pe.register(ModuleActorType.ActorType.transactioncollectioner, context.actorOf(TransactionOfCollectioner.props("transactioncollectioner"), "transactioncollectioner"))
    }
    pe.register(ModuleActorType.ActorType.storager,context.actorOf(StoragerOfCFRD.props("storager"), "storager"))
    pe.register(CFRDActorType.ActorType.blocker,context.actorOf(BlockerOfCFRD.props("blocker"), "blocker"))
    pe.register(CFRDActorType.ActorType.confirmerofblock,context.actorOf(ConfirmOfBlock.props("confirmerofblock"), "confirmerofblock"))
    pe.register(CFRDActorType.ActorType.endorsementcollectioner,context.actorOf(EndorseCollector.props("endorsementcollectioner"), "endorsementcollectioner"))
    pe.register(CFRDActorType.ActorType.dispatchofRecvendorsement,context.actorOf(DispatchOfRecvEndorsement.props("dispatchofRecvendorsement"), "dispatchofRecvendorsement"))
    pe.register(CFRDActorType.ActorType.voter,context.actorOf(VoterOfCFRD.props("voter"), "voter"))

    if(pe.getRepChainContext.getConfig.getConsensusSynchType == "CFRD"){
      pe.register(CFRDActorType.ActorType.synchrequester,context.actorOf(SynchRequesterOfCFRD.props("synchrequester"), "synchrequester"))
    }else if(pe.getRepChainContext.getConfig.getConsensusSynchType == "RAFT"){
      pe.register(CFRDActorType.ActorType.synchrequester,context.actorOf(SynchRequesterOfRAFT.props("synchrequester"), "synchrequester"))
    }

    //pe.register(CFRDActorType.ActorType.synchrequester,context.actorOf(SynchRequesterOfCFRD.props("synchrequester"), "synchrequester"))
    pe.register(CFRDActorType.ActorType.synchresponser,context.actorOf(SynchronizeResponser.props("synchresponser"), "synchresponser"))
    RepLogger.info(RepLogger.System_Logger,  "CFRD共识模块装载完成...")
  }



}
