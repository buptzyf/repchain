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

package rep.network.persistence

import java.util.concurrent.ConcurrentHashMap
//import scala.jdk.CollectionConverters._
import scala.collection.JavaConverters._
import rep.network.consensus.common.MsgOfConsensus.BlockRestore

/**
 * Created by jiangbuyun on 2020/03/19.
 * 缓存经过确认后的块
 */

class BlockCache {
  private implicit var caches  = new ConcurrentHashMap[Long, BlockRestore] asScala
  
  def addToCache(block:BlockRestore)={
    this.caches += block.blk.header.get.height -> block
  }
  
  def removeFromCache(height:Long)={
    this.caches -= height
  }
  
  def getBlockFromCache(height:Long):BlockRestore={
    this.caches(height)
  }
  
  def exist(height:Long):Boolean={
    this.caches.contains(height)
  }
  
  def isEmpty:Boolean={
    this.caches.isEmpty
  }
  
  def getKeyArray4Sort:Array[Long]={
    this.caches.keys.toArray.sortWith(_ < _) 
  }
}