package rep.network.persistence

import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._
import java.util.concurrent.atomic._
import rep.network.persistence.Storager.{ BlockRestore}


class BlockCache {
  private implicit var caches  = new ConcurrentHashMap[Long, BlockRestore] asScala
  
  def addToCache(block:BlockRestore)={
    this.caches += block.blk.height -> block
  }
  
  def removeFromCache(height:Long)={
    this.caches -= height
  }
  
  def getBlockFromCache(height:Long):BlockRestore={
    this.caches(height)
  }
  
  def exist(height:Long):Boolean={
    if(this.caches.contains(height)){
      true
    }else{
      false
    }
  }
  
  def isEmpty:Boolean={
    this.caches.isEmpty
  }
  
  def getKeyArray4Sort:Array[Long]={
    this.caches.keys.toArray.sortWith(_ < _) 
  }
}