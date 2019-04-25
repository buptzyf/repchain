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

package rep.storage.util

import scala.collection.mutable.ArrayBuffer
import rep.crypto._
import scala.collection.mutable
import scala.collection.immutable
import rep.utils._


/**
 * @author jiangbuyun
 * @version	0.7
 * @since	2017-09-28
 * */
object StoreUtil {
  
  def SplitKey(key:String):Array[String]={
    var rel : Array[String] = null
    if(key != null){
      rel = key.split("_", 3)
      if(rel.length != 3){
        rel = null
      }
    }
    rel
  }
  
  def RecomputeMerkleValue(LeafHashs:immutable.TreeMap[String,Array[Byte]],MerkleTree:mutable.HashMap[Int,ArrayBuffer[Array[Byte]]],MaxGroup: Integer)={
    var tmp : ArrayBuffer[Array[Byte]] = ArrayBuffer()
    if(LeafHashs != null && LeafHashs.size > 0){
       val tmparray = LeafHashs.values.toArray
       tmp ++= tmparray
       
       var thesecondhash = getNextLevelList4Byte(tmp,MaxGroup)
       MerkleTree += 1 -> thesecondhash
       Redo(MerkleTree,MaxGroup)
    }
  }
  
  private def Redo(MerkleTree:mutable.HashMap[Int,ArrayBuffer[Array[Byte]]],MaxGroup: Integer):Int={
    var rel = 0
    val maxlevel = MerkleTree.size
    var elems = MerkleTree(maxlevel)
    if(elems.length > 1){
      val currentlevel = maxlevel + 1
      MerkleTree += currentlevel -> StoreUtil.getNextLevelList4Byte(elems,MaxGroup)
      Redo(MerkleTree,MaxGroup)
    }
    rel
  }
  
  
  def  getNextLevelList4Byte(src:ArrayBuffer[Array[Byte]],MaxGroup:Integer):ArrayBuffer[Array[Byte]]={
		  var nextlist: ArrayBuffer[Array[Byte]] = ArrayBuffer()
		  var i = 0
		  while(i < src.length){
		    var j = 1
		    var value = src(i)
		    while(j < MaxGroup){
		       i += 1
		       if(i < src.length){
		         value = Array.concat(value , src(i))
		       }
		       j += 1
		    }
		    value = Sha256.hash(value)
		    i+=1
		    nextlist += value
		  }
		  nextlist
	}
  
  
  
  
  def  UpdateNextLevelHash(src:ArrayBuffer[Array[Byte]],UpdateIdx:Integer,clevel:Int,ExistMerkle:mutable.HashMap[Int,ArrayBuffer[Array[Byte]]],MaxGroup:Integer){
    val start = UpdateIdx / MaxGroup * MaxGroup
    val tmpend = UpdateIdx / MaxGroup * MaxGroup + MaxGroup - 1
    var end = tmpend
    if(tmpend > src.size -1){
      end = src.size -1
    }
    val UpdateNextIdx = UpdateIdx / MaxGroup
    
    var cmerkledata : mutable.ArrayBuffer[Array[Byte]] = ExistMerkle(clevel)
    
    var i = start
    
    while(i < (end + 1)){
      var j = 1
		    var value = src(i)
		    while(j < MaxGroup){
		       i += 1;
		      
		       if(i < (end + 1)){
		         value = Array.concat(value , src(i))
		       }
		       j += 1
		    }
      value = Sha256.hash(value)
      i+=1
      cmerkledata.update(UpdateNextIdx, value)
    }
    
    if(cmerkledata.size > 1){
      UpdateNextLevelHash(cmerkledata,UpdateNextIdx,clevel+1,ExistMerkle,MaxGroup)
    }
  }
  
  def  AddOrInsertNextLevelHash(src:ArrayBuffer[Array[Byte]],StartPos:Integer,clevel:Int,
                                ExistMerkle:mutable.HashMap[Int,ArrayBuffer[Array[Byte]]],MaxGroup:Integer){
      val start = StartPos / MaxGroup * MaxGroup
      val end = src.size -1
      
      val UpdateNextIdx = StartPos / MaxGroup
    
      var cmerkledata : mutable.ArrayBuffer[Array[Byte]] = null
      if(ExistMerkle.contains(clevel)){
        cmerkledata = ExistMerkle(clevel)
      }else{
        cmerkledata  = new mutable.ArrayBuffer[Array[Byte]]()
        ExistMerkle += clevel -> cmerkledata
      }
      
      val dellen = cmerkledata.length - UpdateNextIdx
      if(dellen > 0){
        cmerkledata.trimEnd(dellen)
      }
      
		  var i = start
		  while(i <= end ){
		    var j = 1
		    var value = src(i)
		    while(j < MaxGroup){
		       i += 1;
		       if(i <= end){
		         value = Array.concat(value , src(i))
		       }
		       j += 1
		    }
		    value = Sha256.hash(value)
		    cmerkledata += value
		    i+=1
		  }
		  
      if(cmerkledata.length > 1){
        AddOrInsertNextLevelHash(cmerkledata,UpdateNextIdx,clevel+1,ExistMerkle,MaxGroup)
      }else{
        val len = ExistMerkle.size
        var i = len
        while(i > clevel){
          if(ExistMerkle.contains(i)){
            ExistMerkle -= (i)
          }
          i -= 1
        }
      }
		}
  
  def  getUpdateNextLevelHash(src:ArrayBuffer[Array[Byte]],StartIdx:Integer,EndIdx:Integer,clevel:Int,
                                ExistMerkle:mutable.HashMap[Int,ArrayBuffer[Array[Byte]]],MaxGroup:Integer,prevLength:Int){
      val cStartIdx = StartIdx/MaxGroup/MaxGroup * MaxGroup
      val cEndIdx = EndIdx/MaxGroup/MaxGroup * MaxGroup + MaxGroup -1 
      
      var cmerkledata : mutable.ArrayBuffer[Array[Byte]] = null
      if(ExistMerkle.contains(clevel)){
        cmerkledata = ExistMerkle(clevel)
      }else{
        cmerkledata  = new mutable.ArrayBuffer[Array[Byte]]()
        ExistMerkle += clevel -> cmerkledata
      }
      
      val currentLength = (prevLength -1)/MaxGroup + 1
      if(cmerkledata.length > currentLength){
        cmerkledata.remove(cmerkledata.length-1)
      }
      
		  var i = 0
		  var loop = StartIdx
		  while((i < src.size) && (loop <= EndIdx)){
		    var j = 1
		    var value = src(i)
		    while(j < MaxGroup){
		       i += 1;
		       loop += 1
		       if((i < src.length) && (loop <= EndIdx)){
		         value = Array.concat(value , src(i))
		       }
		       j += 1
		    }
		    value = Sha256.hash(value)
		    var tmpidx = loop/MaxGroup
		    if(tmpidx > cmerkledata.size-1){
		      cmerkledata += value
		    }else{
		      cmerkledata.update(tmpidx, value)
		    }
		    
		    i+=1
		    loop += 1
		  }
		  
      if(cmerkledata.length > 1){
        if(cEndIdx > (cmerkledata.length - 1)){
          val vs = cmerkledata.view(cStartIdx, cmerkledata.length)
          val vsa = vs.toBuffer
          getUpdateNextLevelHash(vsa.asInstanceOf[ArrayBuffer[Array[Byte]]],cStartIdx,cmerkledata.length - 1,clevel+1,ExistMerkle,MaxGroup,cmerkledata.length)
        }else{
          val vs = cmerkledata.view(cStartIdx, cEndIdx+1)
          val vsa = vs.toBuffer
          getUpdateNextLevelHash(vsa.asInstanceOf[ArrayBuffer[Array[Byte]]],cStartIdx,cEndIdx,clevel+1,ExistMerkle,MaxGroup,cmerkledata.length)
        }
      }else{
        val len = ExistMerkle.size
        var i = len
        while(i > clevel){
          if(ExistMerkle.contains(i)){
            ExistMerkle -= (i)
          }
          i -= 1
        }
      }
		}
  
  def ConvertLeafHashs2Bytes(vobj:immutable.TreeMap[String,Array[Byte]]):Array[Byte]={
      val theLeafHashByByte = SerializeUtils.serialise(vobj)
      theLeafHashByByte
  }
  
  def ConvertMerkleTree2Bytes(vobj:mutable.HashMap[Int,ArrayBuffer[Array[Byte]]]):Array[Byte]={
      val theMerkleTreeByByte = SerializeUtils.serialise(vobj)
      theMerkleTreeByByte
  }
}