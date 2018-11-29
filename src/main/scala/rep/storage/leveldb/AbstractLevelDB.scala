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

package rep.storage.leveldb

import scala.collection.immutable
import scala.collection.mutable
import rep.utils._
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import rep.storage.IdxPrefix
import rep.storage.util.StoreUtil
import com.google.protobuf.ByteString
import scala.math._ 
import rep.crypto._
import rep.log.trace.RepLogHelp
import rep.log.trace.LogType
import org.slf4j.LoggerFactory


/**
 * @author jiangbuyun
 * @version	0.7
 * @since	2017-09-28
 * @category	该类实现公共方法。
 * */
abstract class AbstractLevelDB extends ILevelDB  {
  protected def log = LoggerFactory.getLogger(this.getClass)
  protected var IncrementWorldState : immutable.TreeMap[String,Array[Byte]] = new immutable.TreeMap[String,Array[Byte]]() 
  protected var GlobalMerkle : Array[Byte] = null
  
  /**
	 * @author jiangbuyun
	 * @version	0.7
	 * @since	2017-09-28
	 * @category	初始化Merkle值
	 * @param	无
	 * @return	无
	 * */
  protected  def  ReloadMerkle={
    this.GlobalMerkle = null
    this.IncrementWorldState = new immutable.TreeMap[String,Array[Byte]]()
    val key = IdxPrefix.WorldStateForInternetPrefix + IdxPrefix.GlobalWorldStateValue
    val v = this.Get(key)
    if(v != null){
        this.GlobalMerkle = v
    }
  }
  
  /**
	 * @author jiangbuyun
	 * @version	0.7
	 * @since	2017-09-28
	 * @category	系统WorldState改变时需要调用该函数完成WorldState的Merkle重新计算
	 * @param	key 指定的键，value Array[Byte] 修改的键值
	 * @return	无
	 * */
  protected  def PutWorldStateToMerkle(key:String,value:Array[Byte]){
    val prefix = IdxPrefix.WorldStateKeyPreFix
    if(key.startsWith(prefix)){
      val sv = ShaDigest.hash(value)
      this.IncrementWorldState += key -> sv
    }
  }
  
   /**
	 * @author jiangbuyun
	 * @version	0.7
	 * @since	2017-09-28
	 * @category	计算当前WorldState的Merkle的值
	 * @param	无
	 * @return	返回WorldState的Merkle值 Array[Byte]
	 * */
  override  def   GetComputeMerkle:Array[Byte]={
    var rel :Array[Byte] = null
    val source = this.IncrementWorldState.values.toArray
    if(source.size > 0){
       var value : Array[Byte] = null
       var i = 1
       if(this.GlobalMerkle != null){
         i = 0
         value = this.GlobalMerkle
       }else{
         value = source(0)
         i = 1
       }
      
       while(i < source.size){
         value = Array.concat(value , source(i))
         i += 1
       }
       rel = ShaDigest.hash(value)
    }else{
      rel = this.GlobalMerkle
    }
    if(rel != null){
      //println("=========################getmerkle value="+BytesHex.bytes2hex(rel)+"\tsource size="+source.size)
    }else{
      //println("=========################getmerkle value=null"+"\tsource size="+source.size)
    }
    rel
  }
  
  /**
	 * @author jiangbuyun
	 * @version	0.7
	 * @since	2017-09-28
	 * @category	计算当前WorldState的Merkle的值
	 * @param	无
	 * @return	返回WorldState的Merkle值 String
	 * */
  override  def   GetComputeMerkle4String:String={
     var rel:String = "" 
     val bb = GetComputeMerkle
     if(bb != null){
       rel =  BytesHex.bytes2hex(bb)
     }
       
     rel
  }
  
  /**
	 * @author jiangbuyun
	 * @version	0.7
	 * @since	2017-09-28
	 * @category	把字节数组转成字符串
	 * @param	b Array[Byte] 待转换字节数组
	 * @return	返回转换结果，String 如果为null 返回空字符串
	 * */
  def toString(b : Array[Byte]):String={
		var str : String = ""
		if(b != null){
		  str = new String(b)
		}
		str
	}
	
   /**
	 * @author jiangbuyun
	 * @version	0.7
	 * @since	2017-09-28
	 * @category	把字节数组转成长整型
	 * @param	b Array[Byte] 待转换字节数组
	 * @return	返回转换结果，Long  如果为null 返回-1
	 * */
	def toLong(b : Array[Byte]):Long={
		var l : Long = -1
		if(b != null){
		  val str = toString(b)
  		try{
  			l = str.toLong
  		}catch{
  			case e:Exception =>{
  				    log.error( s"DBOP toLong failed, error info= "+e.getMessage)
			  }
  		}
		}
		l
	}
	
	/**
	 * @author jiangbuyun
	 * @version	0.7
	 * @since	2017-09-28
	 * @category	把字节数组转成整型
	 * @param	b Array[Byte] 待转换字节数组
	 * @return	返回转换结果，Int  如果为null 返回-1
	 * */
	def toInt(b : Array[Byte]):Int={
		var l : Int = -1
		if(b != null){
		  val str = toString(b)
  		try{
  			l = str.toInt
  		}catch{
  			case e:Exception =>{
  				    log.error( s"DBOP toInt failed, error info= "+e.getMessage)
			  }
  		}
		}
		l
	}
	
	 /**
	 * @author jiangbuyun
	 * @version	0.7
	 * @since	2017-09-28
	 * @category	把字节数组转成字符串
	 * @param	a Array[Byte] 待转换的字节数组
	 * @return	返回转换结果，String 如果为null 返回空字符串
	 * */
	def byteToString(a:Array[Byte]):String={
	    var s = "" 
	    if(a != null){
	      s = new String(a) 
	    }
	    s
	  }
	
	 /**
	 * @author jiangbuyun
	 * @version	0.7
	 * @since	2017-09-28
	 * @category	打印Map中的键值对
	 * @param	map 需要打印的map
	 * @return	无
	 * */
	def printlnHashMap(map : mutable.HashMap[String,Array[Byte]])={
	  if(map != null){
	    map.foreach(f=>{
	      log.warn("\tkey="+f._1 + "\tvalue=" +toString(f._2))
	    })
	  }
	}
}