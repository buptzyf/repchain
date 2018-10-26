package rep.trace.time

import java.util.concurrent.locks._
import scala.collection.immutable


object blockTimeMgr {
  private val  getBlockTimeLock : Lock = new ReentrantLock();
  private var  blocktimes : immutable.HashMap[String,blocktime] = new immutable.HashMap[String,blocktime]()
  
  private def getBlockTimeLock(systag:String,prehash:String,height:Long,flag:Int):blocktime={
    var bt : blocktime = null
    getBlockTimeLock.lock()
    try{
      if(this.blocktimes.contains(systag)){
        bt = this.blocktimes(systag)
        if(!bt.getId.equalsIgnoreCase(systag + "_" + prehash+"_"+height)){
          if(flag == timeType.preblock_start){
            bt = new blocktime(systag,prehash,height)
            this.blocktimes += systag -> bt
          }
        }
      }else{
        if(flag == timeType.preblock_start){
          bt = new blocktime(systag,prehash,height)
          this.blocktimes += systag -> bt
        }
      }
    }finally {
      getBlockTimeLock.unlock()
    }
    bt
  }
  
  def writeTime(systag:String,prehash:String,height:Long,flag:Int,value:Long)={
    var bt = getBlockTimeLock(systag,prehash,height,flag)
    if(bt != null){
      bt.writeBlockTime(systag + "_" + prehash+"_"+height, flag, value)
      if(flag == timeType.store_end){
        timeAnalysis.addBlockTime(bt, systag)
      }
    }
  }
  
}