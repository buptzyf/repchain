package rep.network.consensus.byzantium

import java.util.concurrent.atomic.AtomicInteger
import rep.app.conf.SystemProfile


/**
 * Created by jiangbuyun on 2020/06/30.
 * 实现共识一致性计算条件，是大于1/2通过，还是大于2/3通过。
 * todo:自动部署可以更新
 */

object ConsensusCondition {
  private var ConsensusNodes : AtomicInteger = new AtomicInteger(SystemProfile.getVoteNodeList.size())
  private var ConsensusScale : AtomicInteger = new AtomicInteger(SystemProfile.getNumberOfEndorsement)
  private var LeastNodeNumber: AtomicInteger = new AtomicInteger(SystemProfile.getVoteNodeMin)
  private def Check(input:Int):Boolean={
    var scaledata = this.ConsensusScale.get()
    if(this.ConsensusScale == 1) {
      scaledata = 2
    }

    if(scaledata == 2) {
      //采用大于1/2方法计算
      input >= (Math.floor(((this.ConsensusNodes.get()) * 1.0) / scaledata + 1))
    }else if(scaledata == 3) {
      //采用大于2/3方法计算
      input >= (Math.floor((((this.ConsensusNodes.get()) * 1.0) / scaledata) * 2) + 1)
    }else{
      false
    }
  }

  //提供一致性判断方法
  def ConsensusConditionChecked(inputNumber: Int): Boolean = {
    if (SystemProfile.getTypeOfConsensus == "PBFT") { //zhj
      true
    } else {
      this.Check(inputNumber)
    }
  }

  //系统是否可以正常工作
  def CheckWorkConditionOfSystem(nodeNumber:Int):Boolean = {
    nodeNumber >= this.LeastNodeNumber.get() && this.Check(nodeNumber)
  }

  def ChangeConsensusNodes(nodeNumber:Int): Unit ={
    this.ConsensusNodes.set(nodeNumber)
  }

  def ChangeConsensusScale(scale:Int)={
    this.ConsensusScale.set(scale)
  }

  def ChangeLeastNodeNumber(limit:Int) ={
    this.LeastNodeNumber.set(limit)
  }

}
