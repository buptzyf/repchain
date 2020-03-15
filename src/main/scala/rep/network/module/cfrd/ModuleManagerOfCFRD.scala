package rep.network.module.cfrd

import akka.actor.{ Props}
import rep.network.consensus.cfrd.block.{Blocker, ConfirmOfBlock, EndorseCollector, GenesisBlocker}
import rep.network.consensus.cfrd.endorse.DispatchOfRecvEndorsement
import rep.network.consensus.cfrd.vote.Voter
import rep.network.module.{IModuleManager, ModuleActorType}
import rep.network.sync.{SynchronizeRequester4Future, SynchronizeResponser}

object ModuleManagerOfCFRD{
  def props(name: String, sysTag: String, enableStatistic: Boolean, enableWebSocket: Boolean, isStartup: Boolean): Props = Props(classOf[ModuleManagerOfCFRD], name, sysTag, enableStatistic: Boolean, enableWebSocket: Boolean, isStartup: Boolean)

}

class ModuleManagerOfCFRD(moduleName: String, sysTag: String, enableStatistic: Boolean, enableWebSocket: Boolean, isStartup: Boolean) extends IModuleManager(moduleName,sysTag, enableStatistic, enableWebSocket, isStartup){

  //启动共识模块，启动CFRD共识
  override def startupConsensus: Unit = {
    pe.getActorRef(CFRDActorType.ActorType.voter) ! Voter.VoteOfBlocker
  }

  override def loadConsensusModule = {
    pe.register(CFRDActorType.ActorType.blocker,context.actorOf(Blocker.props("blocker"), "blocker"))
    pe.register(CFRDActorType.ActorType.endorsementcollectioner,context.actorOf(EndorseCollector.props("endorsementcollectioner"), "endorsementcollectioner"))
    pe.register(CFRDActorType.ActorType.dispatchofRecvendorsement,context.actorOf(DispatchOfRecvEndorsement.props("dispatchofRecvendorsement"), "dispatchofRecvendorsement"))
    pe.register(CFRDActorType.ActorType.voter,context.actorOf(Voter.props("voter"), "voter"))
    pe.register(CFRDActorType.ActorType.gensisblock,context.actorOf(GenesisBlocker.props("gensisblock"), "gensisblock"))
    pe.register(CFRDActorType.ActorType.confirmerofblock,context.actorOf(ConfirmOfBlock.props("confirmerofblock"), "confirmerofblock"))

    pe.register(CFRDActorType.ActorType.synchrequester,context.actorOf(SynchronizeRequester4Future.props("synchrequester"), "synchrequester"))
    pe.register(CFRDActorType.ActorType.synchresponser,context.actorOf(SynchronizeResponser.props("synchresponser"), "synchresponser"))
  }



}
