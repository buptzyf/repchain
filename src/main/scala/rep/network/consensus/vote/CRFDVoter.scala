/*
 * Copyright  2019 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BA SIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package rep.network.consensus.vote

import rep.crypto.Sha256
import scala.collection.mutable
import rep.storage.util.pathUtil
import scala.math._
import rep.log.RepLogger

/**
  * 系统默认
  * 候选人竞争实现
  * 出块人竞争实现
  * Created by shidianyue on 2017/5/15.
  * 
  * @update 2018-05 jiangbuyun
  */
//TODO kami 应该在init的时候载入一个实现函数或者类。然后调用方法。写的更通用一些
trait CRFDVoter extends VoterBase {
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
      var randomList = getRandomList(hashSeed,len)//,nodes.size)
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
