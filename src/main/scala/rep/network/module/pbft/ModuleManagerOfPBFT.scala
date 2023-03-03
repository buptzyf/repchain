//zhj

package rep.network.module.pbft

import akka.actor.Props
import rep.log.RepLogger
import rep.network.cache.pbft.TransactionPoolOfPBFT
import rep.network.confirmblock.pbft.ConfirmOfBlockOfPBFT
import rep.network.consensus.pbft.block.{BlockerOfPBFT, EndorseCollector}
import rep.network.module.{IModuleManager, ModuleActorType}
import rep.network.sync.response.SynchronizeResponser
import rep.network.consensus.pbft.endorse.{PbftCommit, PbftPrePrepare, PbftPrepare}
import rep.network.consensus.pbft.MsgOfPBFT.VoteOfBlocker
import rep.network.consensus.pbft.endorse.DispatchOfRecvEndorsement
import rep.network.persistence.StoragerOfPBFT
import rep.network.consensus.pbft.vote.VoterOfPBFT
import rep.network.sync.request.pbft.SynchRequesterOfPBFT

/**
 * Created by zhaohuanjun on 2020/03/30.
 * PBFT的模块管理类，继承公共的模块管理类，实现PBFT的自己的模块
 */
object ModuleManagerOfPBFT{
  def props(name: String,  isStartup: Boolean): Props = Props(classOf[ModuleManagerOfPBFT], name,  isStartup: Boolean)

}

class ModuleManagerOfPBFT(moduleName: String,  isStartup: Boolean) extends IModuleManager(moduleName,isStartup){

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix( "ModuleManagerOfPBFT Start"))
  }

  override def startupConsensus: Unit = {
    pe.getActorRef(PBFTActorType.ActorType.voter) ! VoteOfBlocker("startup")
  }

  override def loadConsensusModule = {
    if (pe.getRepChainContext.getConsensusNodeConfig.getVoteListOfConfig.contains(pe.getSysTag)) {
      pe.register(ModuleActorType.ActorType.transactionpool, context.actorOf(TransactionPoolOfPBFT.props("transactionpool"), "transactionpool")) //zhj
    }
    //pe.register(ModuleActorType.ActorType.transactioncollectioner, context.actorOf(TransactionOfCollectioner.props("transactioncollectioner"), "transactioncollectioner"))
    pe.register(ModuleActorType.ActorType.storager,context.actorOf(StoragerOfPBFT.props("storager"), "storager"))
    pe.register(PBFTActorType.ActorType.blocker,context.actorOf(BlockerOfPBFT.props("blocker"), "blocker"))
    pe.register(PBFTActorType.ActorType.confirmerofblock,context.actorOf(ConfirmOfBlockOfPBFT.props("confirmerofblock"), "confirmerofblock"))
    pe.register(PBFTActorType.ActorType.endorsementcollectioner,context.actorOf(EndorseCollector.props("endorsementcollectioner"), "endorsementcollectioner"))
    pe.register(PBFTActorType.ActorType.dispatchofRecvendorsement,context.actorOf(DispatchOfRecvEndorsement.props("dispatchofRecvendorsement"), "dispatchofRecvendorsement"))
    pe.register(PBFTActorType.ActorType.voter,context.actorOf(VoterOfPBFT.props("voter"), "voter"))


    pe.register(PBFTActorType.ActorType.synchrequester,context.actorOf(SynchRequesterOfPBFT.props("synchrequester"), "synchrequester"))
    pe.register(PBFTActorType.ActorType.synchresponser,context.actorOf(SynchronizeResponser.props("synchresponser"), "synchresponser"))

    pe.register(PBFTActorType.ActorType.pbftpreprepare, context.actorOf(PbftPrePrepare.props("pbftpreprepare"), "pbftpreprepare"))
    pe.register(PBFTActorType.ActorType.pbftprepare, context.actorOf(PbftPrepare.props("pbftprepare"), "pbftprepare"))
    pe.register(PBFTActorType.ActorType.pbftcommit, context.actorOf(PbftCommit.props("pbftcommit"), "pbftcommit"))
    RepLogger.info(RepLogger.System_Logger,  "PBFT共识模块装载完成...")
  }

}
