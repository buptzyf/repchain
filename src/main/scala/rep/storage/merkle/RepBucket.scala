/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
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

package rep.storage.merkle

import rep.storage.leveldb.ILevelDB
import scala.collection.immutable
import scala.collection.mutable
import rep.storage.IdxPrefix
import rep.utils._
import rep.crypto._
import rep.storage.util.StoreUtil
import rep.log.trace.LogType
import org.slf4j.LoggerFactory

/**
 * @author jiangbuyun
 * @version	0.7
 * @since	2017-09-28
 * */
class RepBucket(val leveldb : ILevelDB){
  protected def log = LoggerFactory.getLogger(this.getClass)
  private var ccids : mutable.ArrayBuffer[String] = new mutable.ArrayBuffer[String]()
  private var MerkleHash : Array[Byte] = null
  private var ccbucket : mutable.HashMap[String,ChainCodeBucket] = new mutable.HashMap[String,ChainCodeBucket]()
  
  load
    
  private def load={
    getMerkleHashFromDB
    getAllChainCode
    this.ccids.foreach(f=>{
      if(!this.ccbucket.contains(f)){
        var tmp = new ChainCodeBucket(this.leveldb,f)
        this.ccbucket += f -> tmp
      }
    })
  }
  
  def Put( key:String, value:Array[Byte]){
      synchronized{
        if(key.startsWith(IdxPrefix.WorldStateKeyPreFix)){
          val keys = StoreUtil.SplitKey(key)
          if(keys != null && keys.length == 3){
            val ccid = keys(1)
            if(this.ccbucket.contains(ccid)){
              var tmp = this.ccbucket(ccid)
              if(tmp != null){
                tmp.Put(key, value)
                computeMerkel
              }
            }else{
              this.ccids += ccid
              var tmp1 = new ChainCodeBucket(this.leveldb,ccid)
              this.ccbucket += ccid -> tmp1
              tmp1.Put(key, value)
              computeMerkel
            }
          }
        }
      }
    }
    
    private def computeMerkel={
      synchronized{
        val start = System.currentTimeMillis()
        var value : Array[Byte] = null
        this.ccids.foreach(f=>{
          var tmp = this.ccbucket(f)
          if(tmp != null){
            val tv = tmp.getCCMerkle
            if(tv != null){
              if(value == null){
                value = tv
              }else{
                value = Array.concat(value , tv)
              }
            }
          }
        })
        if(value != null)  this.MerkleHash = Sha256.hash(value)
        val end = System.currentTimeMillis()
        log.info("Merkle compute time="+(end - start)+"ms")
      }
    }
  
    def getMerkleHash:Array[Byte]={
      this.MerkleHash
    }
    
    def getMerkelHash4String:String={
       var rel:String = "" 
       val bb = getMerkleHash
       if(bb != null){
         rel =  BytesHex.bytes2hex(bb)
       }
       
       rel
    }
    
    def save={
      val keyhash = IdxPrefix.WorldStateForInternetPrefix + "merkletreehash"
      this.leveldb.Put(keyhash, this.MerkleHash)
      val key = IdxPrefix.WorldStateForInternetPrefix + IdxPrefix.ChainCodeList
      val cckb = SerializeUtils.serialise(this.ccids)
      this.leveldb.Put(key, cckb)
      this.ccids.foreach(f=>{
        var tmp = this.ccbucket(f)
          if(tmp != null){
            tmp.save
          }
      })
    }
    
  private def getMerkleHashFromDB={
      val key = IdxPrefix.WorldStateForInternetPrefix + "merkletreehash"
      val v = leveldb.Get(key)
      if(v != null){
        this.MerkleHash = v
      }
    }
  
  private def getAllChainCode={
    val key = IdxPrefix.WorldStateForInternetPrefix + IdxPrefix.ChainCodeList
      val v = leveldb.Get(key)
      if(v != null){
        val tmp = SerializeUtils.deserialise(v)
        if(tmp.isInstanceOf[mutable.ArrayBuffer[String]] ){
            var ta = tmp.asInstanceOf[mutable.ArrayBuffer[String]]
            this.ccids = ta
          }
      }
  }
}