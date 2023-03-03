package rep.network.consensus.byzantium

import java.util.concurrent.atomic.AtomicInteger

import rep.app.conf.RepChainConfig
import rep.app.system.RepChainSystemContext


/**
 * Created by jiangbuyun on 2020/06/30.
 * 实现共识一致性计算条件，是大于1/2通过，还是大于2/3通过。
 * todo:自动部署可以更新
 */

class ConsensusCondition(ctx:RepChainSystemContext){

  private def byzantineExamination(input:Int):Boolean={
      var mode = ctx.getConfig.getEndorsementNumberMode
      if(mode == 1) {
        mode = 2
      }

      if(mode == 2) {
        //采用大于1/2方法计算
        input >= Math.floor((ctx.getConsensusNodeConfig.getVoteListOfConfig.length * 1.0) / mode + 1)
      }else if(mode == 3) {
        //采用大于2/3方法计算
        input >= (Math.floor(((ctx.getConsensusNodeConfig.getVoteListOfConfig.length * 1.0) / mode) * 2) + 1)
      }else{
        false
      }
  }

  //提供一致性判断方法
  def ConsensusConditionChecked(inputNumber: Int): Boolean = {
    if (ctx.getConfig.getConsensustype == "PBFT") { //zhj
      true
    } else {
      this.byzantineExamination(inputNumber)
    }
  }

  //系统是否可以正常工作
  def CheckWorkConditionOfSystem(nodeNumber:Int):Boolean = {
    nodeNumber >= ctx.getConfig.getMinVoteNumber && this.byzantineExamination(nodeNumber)
  }


}
