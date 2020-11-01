package rep.network.module

/**
 * Created by jiangbuyun on 2020/03/15.
 * 模块管理的接口
 */
trait IModule {
  def startupConsensus: Unit
  def loadConsensusModule:Unit
}
