/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Fintech Research Center of ISCAS.
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
 */

package rep.network.consensus.vote

import rep.crypto.Sha256
import scala.collection.mutable

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

  override def blocker(nodes: Set[String], position:Int): Option[String] = {
    //if (nodes.nonEmpty&&position<nodes.size) Option(nodes.head) else None
    if(nodes.nonEmpty){
      var pos = position
      if(position >= nodes.size){
        pos = position % nodes.size
      }
      val a = nodes.toList
      Option(a(pos)) 
    }else{
      None
    }
  }

  override def candidators(nodes: Set[String], seed: Array[Byte]): Set[String] = {
    var nodesSeq = nodes.toSeq.sortBy(f=>(f.toString()))//nodes.toSeq
    var len = nodes.size / 2 + 1
    val min_len = 4
    len = if(len<min_len){
      if(nodes.size < min_len) nodes.size
      else min_len
    }
    else len
    if(len<4){
      Set.empty
    }
    else{
      var candidate = mutable.Seq.empty[String]
      var index = 0
      var hashSeed = seed

      while (candidate.size < len) {
        if (index >= hashSeed.size) {
          hashSeed = Sha256.hash(hashSeed)
          index = 0
        }
        //应该按位来计算
        if ((hashSeed(index) & 1) == 1) {
          candidate = (candidate :+ nodesSeq(index % (nodesSeq.size)))
          nodesSeq = (nodesSeq.toSet - nodesSeq(index % (nodesSeq.size))).toSeq
        }
        index += 1
      }
      candidate.toSet
    }
  }
  
}
