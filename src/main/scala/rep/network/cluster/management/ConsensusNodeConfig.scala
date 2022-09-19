package rep.network.cluster.management

import java.util.concurrent.atomic.AtomicBoolean
import rep.app.system.RepChainSystemContext
import rep.log.RepLogger
import akka.actor.{Actor, ActorRef}

class ConsensusNodeConfig(ctx:RepChainSystemContext) {
  private var voteListOfConfig : Array[String] = ConsensusNodeUtil.loadVoteList(ctx)
  private val isChangeVoteListOfConfig : AtomicBoolean = new AtomicBoolean(false)
  private var tmpVoteListOfConfig : Array[String] = null
  private val lock : Object = new Object

  def getVoteListOfConfig:Array[String] = {
    if(this.isChangeVoteListOfConfig.get()){
      this.lock.synchronized({
        if(this.tmpVoteListOfConfig != null){
          this.voteListOfConfig = this.tmpVoteListOfConfig
        }
        this.isChangeVoteListOfConfig.set(false)
        RepLogger.trace(RepLogger.System_Logger, "ConsensusMgr 更新共识节点名称列表装载="+this.voteListOfConfig.mkString(","))
        this.voteListOfConfig
      })
    }else{
      this.voteListOfConfig
    }
  }


  ///////////////////////共识节点配置改变代码  开始/////////////////////////////////////////////////////////////////////
  def notifyVoteListChange(data:Array[String]): Unit = {
    try{
      val thread = new Thread(new UpdateVoteListThread(data))
      thread.start()
    }catch {
      case ex:Exception=>
        RepLogger.trace(RepLogger.System_Logger, "ConsensusMgr 通知更新共识节点名称列表异常，msg="+ex.getMessage)
    }
  }

  class UpdateVoteListThread(updateInfo: Array[String]) extends Runnable {
    override def run(): Unit = {
      try {
        updateVoteList(updateInfo)
        val ml: ActorRef = ctx.getMemberList
        if (ml != null) {
          ml ! getVoteListOfConfig
        }
      } catch {
        case ex: Exception =>
          RepLogger.trace(RepLogger.System_Logger, "ConsensusMgr 接收更新共识节点名称列表线程异常，msg=" + ex.getMessage)
      }
    }
  }

  private def updateVoteList(update:Array[String]):Unit={
    if(update != null && update.length >= ctx.getConfig.getMinVoteNumber){
      this.lock.synchronized({
        this.tmpVoteListOfConfig = update
        this.isChangeVoteListOfConfig.set(true)
      })
      RepLogger.trace(RepLogger.System_Logger, "ConsensusMgr 更新共识节点名称列表通知接收="+this.tmpVoteListOfConfig.mkString(","))
    }
  }
  ///////////////////////共识节点配置改变代码 结束/////////////////////////////////////////////////////////////////////






}
