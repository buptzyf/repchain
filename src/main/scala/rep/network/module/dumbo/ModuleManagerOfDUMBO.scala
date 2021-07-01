package rep.network.module.dumbo

import akka.actor.Props
import rep.app.conf.SystemProfile
import rep.log.RepLogger
import rep.network.cache.TransactionOfCollectioner
import rep.network.confirmblock.common.ConfirmOfBlock
import rep.network.consensus.asyncconsensus.AsyncBlocker
import rep.network.consensus.cfrd.block.{BlockerOfCFRD, EndorseCollector}
import rep.network.consensus.cfrd.endorse.DispatchOfRecvEndorsement
import rep.network.consensus.cfrd.vote.VoterOfCFRD
import rep.network.consensus.asyncconsensus.protocol.commoncoin.CommonCoinInActor
import rep.network.module.{IModuleManager, ModuleActorType}
import rep.network.module.cfrd.CFRDActorType
import rep.network.consensus.asyncconsensus.AsyncBlocker
import rep.network.consensus.asyncconsensus.AsyncBlocker.startup
import rep.network.persistence.cfrd.StoragerOfCFRD
import rep.network.persistence.dumbo.StoragerOfDumbo
import rep.network.sync.request.cfrd.SynchRequesterOfCFRD
import rep.network.sync.response.SynchronizeResponser

object ModuleManagerOfDUMBO{
def props(name: String, sysTag: String, enableStatistic: Boolean, enableWebSocket: Boolean, isStartup: Boolean): Props = Props(classOf[ModuleManagerOfDUMBO], name, sysTag, enableStatistic: Boolean, enableWebSocket: Boolean, isStartup: Boolean)

}

class ModuleManagerOfDUMBO (moduleName: String, sysTag: String, enableStatistic: Boolean, enableWebSocket: Boolean, isStartup: Boolean) extends IModuleManager(moduleName,sysTag, enableStatistic, enableWebSocket, isStartup){

  override def preStart(): Unit = {
    RepLogger.info(RepLogger.System_Logger, this.getLogMsgPrefix( "ModuleManagerOfCFRD Start"))
  }

  //启动共识模块，启动CFRD共识
  override def startupConsensus: Unit = {
    //pe.getActorRef(CFRDActorType.ActorType.voter) ! VoteOfBlocker
    pe.getActorRef(CFRDActorType.ActorType.blocker) ! startup
  }

  override def loadConsensusModule = {

    pe.register(ModuleActorType.ActorType.transactioncollectioner, context.actorOf(TransactionOfCollectioner.props("transactioncollectioner"), "transactioncollectioner"))
    pe.register(ModuleActorType.ActorType.storager,context.actorOf(StoragerOfDumbo.props("storager"), "storager"))
    //pe.register(CFRDActorType.ActorType.blocker,context.actorOf(BlockerOfCFRD.props("blocker"), "blocker"))
    pe.register(CFRDActorType.ActorType.confirmerofblock,context.actorOf(ConfirmOfBlock.props("confirmerofblock"), "confirmerofblock"))
    //pe.register(CFRDActorType.ActorType.endorsementcollectioner,context.actorOf(EndorseCollector.props("endorsementcollectioner"), "endorsementcollectioner"))
    //pe.register(CFRDActorType.ActorType.dispatchofRecvendorsement,context.actorOf(DispatchOfRecvEndorsement.props("dispatchofRecvendorsement"), "dispatchofRecvendorsement"))
   //pe.register(CFRDActorType.ActorType.voter,context.actorOf(VoterOfCFRD.props("voter"), "voter"))


    pe.register(CFRDActorType.ActorType.synchrequester,context.actorOf(SynchRequesterOfCFRD.props("synchrequester"), "synchrequester"))
    pe.register(CFRDActorType.ActorType.synchresponser,context.actorOf(SynchronizeResponser.props("synchresponser"), "synchresponser"))

    pe.register(CFRDActorType.ActorType.blocker,context.actorOf(AsyncBlocker.props(pe.getSysTag+"-asyncblocker", pe.getSysTag,"/user/modulemanager","{nodeName}"+"-asyncblocker",SystemProfile.getVoteNodeMin,SystemProfile.getFaultCount,self), pe.getSysTag+"-asyncblocker"))


  }



}
