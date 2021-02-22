package rep.network.module.cfrdinstream

import akka.actor.Props
import rep.log.RepLogger
import rep.network.cache.TransactionOfCollectioner
import rep.network.confirmblock.common.ConfirmOfBlock
import rep.network.consensus.cfrd.MsgOfCFRD.VoteOfBlocker
import rep.network.consensus.cfrd.block.{BlockerOfCFRD, EndorseCollector}
import rep.network.consensus.cfrd.endorse.DispatchOfRecvEndorsement
import rep.network.consensus.cfrd.vote.VoterOfCFRD
import rep.network.consensus.cfrdinstream.block.{BlockerOfCFRDInStream, CollectionerOfBlocker, EndorseCollectorInStream}
import rep.network.consensus.cfrdinstream.endorse.Endorser4FutureInStream
import rep.network.consensus.cfrdinstream.transaction.DispatchOfPreloadInStream
import rep.network.consensus.cfrdinstream.vote.VoteOfCFRDInStream
import rep.network.module.{IModuleManager, ModuleActorType}
import rep.network.module.cfrd.{CFRDActorType, ModuleManagerOfCFRD}
import rep.network.persistence.cfrd.StoragerOfCFRD
import rep.network.sync.request.cfrd.SynchRequesterOfCFRD
import rep.network.sync.request.raft.SynchRequesterOfRAFT
import rep.network.sync.response.SynchronizeResponser
import rep.network.transaction.DispatchOfPreload

object ModuleManagerOfCFRDInStream{
  def props(name: String, sysTag: String, enableStatistic: Boolean, enableWebSocket: Boolean, isStartup: Boolean): Props = Props(classOf[ModuleManagerOfCFRDInStream], name, sysTag, enableStatistic: Boolean, enableWebSocket: Boolean, isStartup: Boolean)
}

class ModuleManagerOfCFRDInStream(moduleName: String, sysTag: String, enableStatistic: Boolean, enableWebSocket: Boolean, isStartup: Boolean) extends IModuleManager(moduleName,sysTag, enableStatistic, enableWebSocket, isStartup){

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix( "ModuleManagerOfCFRD Start"))
  }

  //启动共识模块，启动CFRD共识
  override def startupConsensus: Unit = {
    pe.getActorRef(CFRDActorType.ActorType.voter) ! VoteOfBlocker
  }

  override def loadConsensusModule = {
    //pe.register(ModuleActorType.ActorType.transactionpool, context.actorOf(TransactionPoolOfCFRD.props("transactionpool"), "transactionpool"))
    pe.register(ModuleActorType.ActorType.transactioncollectioner, context.actorOf(TransactionOfCollectioner.props("transactioncollectioner"), "transactioncollectioner"))
    pe.register(ModuleActorType.ActorType.storager,context.actorOf(StoragerOfCFRD.props("storager"), "storager"))
    pe.register(CFRDActorType.ActorType.blocker,context.actorOf(BlockerOfCFRDInStream.props("blocker"), "blocker"))
    pe.register(CFRDActorType.ActorType.confirmerofblock,context.actorOf(ConfirmOfBlock.props("confirmerofblock"), "confirmerofblock"))
    pe.register(CFRDActorType.ActorType.endorsementcollectioner,context.actorOf(EndorseCollectorInStream.props("endorsementcollectioner"), "endorsementcollectioner"))
    pe.register(CFRDActorType.ActorType.endorsementcollectionerinstream,context.actorOf(CollectionerOfBlocker.props("CollectionerOfBlocker"), "CollectionerOfBlocker"))
    //pe.register(CFRDActorType.ActorType.dispatchofRecvendorsement,context.actorOf(Endorser4FutureInStream.props("dispatchofRecvendorsement"), "dispatchofRecvendorsement"))
    pe.register(CFRDActorType.ActorType.dispatchofRecvendorsement,context.actorOf(Endorser4FutureInStream.props("dispatchofRecvendorsement"), "dispatchofRecvendorsement"))
    pe.register(CFRDActorType.ActorType.voter,context.actorOf(VoteOfCFRDInStream.props("voter"), "voter"))
    pe.register(ModuleActorType.ActorType.dispatchofpreloadinstream, context.actorOf(DispatchOfPreloadInStream.props("dispatchofpreloadinstream").withDispatcher("contract-dispatcher"), "dispatchofpreloadinstream"))


    //pe.register(CFRDActorType.ActorType.synchrequester,context.actorOf(SynchRequesterOfCFRD.props("synchrequester"), "synchrequester"))
    //pe.register(CFRDActorType.ActorType.synchresponser,context.actorOf(SynchronizeResponser.props("synchresponser"), "synchresponser"))

    pe.register(CFRDActorType.ActorType.synchrequester,context.actorOf(SynchRequesterOfRAFT.props("synchrequester"), "synchrequester"))
    pe.register(CFRDActorType.ActorType.synchresponser,context.actorOf(SynchronizeResponser.props("synchresponser"), "synchresponser"))
  }



}