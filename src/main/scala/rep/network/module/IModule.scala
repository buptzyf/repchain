package rep.network.module

trait IModule {
  def startupConsensus: Unit
  def loadConsensusModule:Unit
}
