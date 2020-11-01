package rep.network.consensus.common.algorithm
//zhj
import rep.log.RepLogger
import rep.storage.util.pathUtil

import scala.math.pow

/**
 * Created by jiangbuyun on 2020/03/17.
 * 实现随机抽签的算法
 */

class ISequencialAlgorithmOfVote extends IAlgorithmOfVote {
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

  override def candidators(Systemname:String,hash:String,nodes: Set[String], seed: Array[Byte]): Array[String] = {
    var nodesSeq = nodes.toSeq.sortBy(f => (f))
    var len = nodes.size
    var candidate = new Array[String](len)
    for (j <- 0 until len) {
      candidate(j) = nodesSeq(j)
    }
    candidate
  }
}
