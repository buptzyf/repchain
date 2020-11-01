package rep.network.consensus.common.algorithm

/**
 * Created by jiangbuyun on 2020/03/17.
 * 定义抽签算法的接口
 */
trait IAlgorithmOfVote {
  /**
   * 获取出块人（竞争胜出者）
   * @param nodes
   * @tparam T
   * @return
   */
  def blocker(nodes:Array[String], position:Int):String

  /**
   * 获取候选人节点
   * @param nodes
   * @tparam T
   * @param seed 随机种子
   * @return
   */
  def candidators(Systemname:String,hash:String,nodes:Set[String], seed:Array[Byte]):Array[String]
}
