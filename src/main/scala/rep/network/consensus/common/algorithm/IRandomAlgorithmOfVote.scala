package rep.network.consensus.common.algorithm

import rep.log.RepLogger
import rep.storage.util.pathUtil
import scala.math.pow

/**
 * Created by jiangbuyun on 2020/03/17.
 * 实现随机抽签的算法
 */

class IRandomAlgorithmOfVote extends IAlgorithmOfVote {
  case class randomNumber(var number:Long,var generateSerial:Int)

  override def blocker(nodes: Array[String], position:Int): String = {
    if(nodes.nonEmpty){
      var pos = position
      if(position >= nodes.size){
        pos = position % nodes.size
      }
      nodes(pos)
    }else{
      null
    }
  }

  private def getRandomList(seed:Long,candidatorTotal:Int):Array[randomNumber]={
    val m = pow(2,20).toLong
    val a = 2045
    val b = 1
    var randomArray = new Array[randomNumber](candidatorTotal)
    var hashSeed = seed.abs
    for(i<-0 until (candidatorTotal) ){
      var tmpSeed = (a * hashSeed + b) % m
      tmpSeed = tmpSeed.abs
      if(tmpSeed == hashSeed) tmpSeed = tmpSeed + 1
      hashSeed = tmpSeed
      var randomobj = new randomNumber(hashSeed,i)
      randomArray(i) = randomobj
    }

    randomArray = randomArray.sortWith(
      (randomNumber_left,randomNumber_right)=> randomNumber_left.number < randomNumber_right.number)

    randomArray
  }

  override def candidators(Systemname:String,hash:String,nodes: Set[String], seed: Array[Byte]): Array[String] = {
    var nodesSeq = nodes.toSeq.sortBy(f=>(f))
    var len = nodes.size / 2 + 1
    val min_len = 4
    len = if(len<min_len){
      if(nodes.size < min_len) nodes.size
      else min_len
    }
    else len
    if(len<4){
      null
    }
    else{
      var candidate = new Array[String](len)
      var hashSeed:Long = pathUtil.bytesToInt(seed)
      var randomList = getRandomList(hashSeed,nodes.size)
      //println(randomList(0).generateSerial)
      RepLogger.trace(RepLogger.Consensus_Logger, s"sysname=${Systemname},randomList=${randomList.mkString(",")}")
      for(j<-0 until len){
        var e = randomList(j)
        candidate(j) = nodesSeq(e.generateSerial)
      }
      RepLogger.debug(RepLogger.Vote_Logger, s"sysname=${Systemname},hash=${hash},hashvalue=${hashSeed},randomList=${randomList.mkString("|")}")
      RepLogger.debug(RepLogger.Vote_Logger, s"sysname=${Systemname},candidates=${candidate.mkString("|")}")

      candidate
    }
  }
}
