package rep.app.conf

import com.typesafe.config.Config

class TimePolicy(config:Config) {

  def getVoteWaitingDelay:Long ={
    config.getLong("system.time.block.waiting_delay")
  }

  def getTimeOutBlock:Int = {
    config.getInt("system.time.timeout.block")
  }

  def getTimeoutEndorse:Int={
    config.getInt("system.time.timeout.endorse")
  }

  def getTimeoutPreload:Int ={
    config.getInt("system.time.timeout.transaction_preload")
  }

  def getTimeoutSync:Int={
    config.getInt("system.time.timeout.sync_chain")
  }

  def getVoteRetryDelay:Long={
    config.getLong("system.time.block.vote_retry_delay")
  }

//  def getTranscationWaiting:Int={
//    config.getInt("system.time.timeout.transaction_waiting")
//  }

  def getSysNodeStableDelay:Long={
    config.getLong("system.cluster.node_stable_delay")
  }

  def getStableTimeDur:Int = {
    config.getInt("system.time.stable_time_dur")
  }
}
