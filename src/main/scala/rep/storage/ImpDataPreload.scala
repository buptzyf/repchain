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

package rep.storage

import rep.utils._
import scala.collection.mutable
import rep.storage.leveldb._
import scala.collection.mutable.ArrayBuffer
import rep.protos.peer._;
import scala.util.control.Breaks
import rep.log.RepLogger
import rep.utils.SerializeUtils.deserialise

/**内存数据库的访问类，属于多实例。
 * @constructor	根据SystemName和InstanceName建立实例
 * @param	SystemName	系统名
 * @param	InstanceName	实例名
 * @author jiangbuyun
 * @version	0.7
 * @since	2017-09-28
 * @category	内存数据库的访问类，属于多实例。
 */
class ImpDataPreload (SystemName:String,InstanceName:String) extends AbstractLevelDB(SystemName:String) {
    private var update :java.util.concurrent.ConcurrentHashMap[String,Array[Byte]] = new java.util.concurrent.ConcurrentHashMap[String,Array[Byte]]
    private var cache  :java.util.concurrent.ConcurrentHashMap[String,Array[Byte]] = new java.util.concurrent.ConcurrentHashMap[String,Array[Byte]]
    private var dbop = ImpDataAccess.GetDataAccess(SystemName) 
   
   
    /**
	 * @author jiangbuyun
	 * @version	0.7
	 * @since	2017-09-28
	 * @category	获取当前系统的名称
	 * @param
	 * @return	返回当前系统的名称 String
	 * */
    override def   getSystemName:String={
	    SystemName
	  }
	
    /**
	 * @author jiangbuyun
	 * @version	0.7
	 * @since	2017-09-28
	 * @category	获取当前实例的名称
	 * @param
	 * @return	返回当前实例的名称 String
	 * */
    override def getInstanceName:String={
      InstanceName
    }
   
    /**
	 * @author jiangbuyun
	 * @version	0.7
	 * @since	2017-09-28
	 * @category	获取指定的键值
	 * @param	key String 指定的键
	 * @return	返回对应键的值 Array[Byte]
	 * */
  	override def Get(key : String):Array[Byte]={
  		var rb : Array[Byte] = null
			try{
  					if(this.update.containsKey(key)){
  					  //RepLogger.trace(RepLogger.Business_Logger,  
  			      //s"nodename=${getSystemName},dbname=${getInstanceName},key=${key},in cache=${deserialise(this.update.get(key))}")
  						rb = this.update.get(key)
							/*if(this.cache.containsKey(key)){
								this.cache.remove(key)
							}*/
  					}else if(this.cache.containsKey(key)){
							rb = this.cache.get(key)
						}else {
  					 // RepLogger.trace(RepLogger.Business_Logger,  
  			      //s"nodename=${getSystemName},dbname=${getInstanceName},key=${key},in db=${deserialise(this.dbop.Get(key))}")
  						rb = this.dbop.Get(key)
							if(rb != null)
								this.cache.put(key,rb)
						}
  					setUseTime
			}catch{
				case e:Exception =>{
				  rb = null
				  RepLogger.error(RepLogger.Storager_Logger,  
  			      "ImpDataPreload_" + SystemName + "_" + "ImpDataPreload Get failed, error info= "+e.getMessage)
  			  throw e
  			}
			}
  		rb
  	}
  	
  	/**
	 * @author jiangbuyun
	 * @version	0.7
	 * @since	2017-09-28
	 * @category	存储指定的键和值到数据库
	 * @param	key String 指定的键，bb Array[Byte] 要存储的值
	 * @return	返回成功或者失败 Boolean
	 * */
  	override def Put (key : String,bb : Array[Byte]):Boolean={
  		var b : Boolean = true
			try{
				  if(key == null){
				    RepLogger.trace(RepLogger.Storager_Logger,  
  			      "ImpDataPreload_" + SystemName + "_" + "ImpDataPreload Put failed, error info= key is null")
				  }
				  var v :Array[Byte] = bb
				  if(bb == null){
				    v = None.toArray
				    RepLogger.trace(RepLogger.Storager_Logger,  
  			      "ImpDataPreload_" + SystemName + "_" + "ImpDataPreload Put failed, error info= value is null")
				  }
				  if(key != null ){
				    this.update.put(key, v)
						if(this.cache.containsKey(key)){
							this.cache.remove(key)
						}
				  }
				  setUseTime
			}catch{
			  case e:Exception =>{
			    b = false
			    RepLogger.error(RepLogger.Storager_Logger,  
  			      "ImpDataPreload_" + SystemName + "_" + "ImpDataPreload Put failed, error info= "+e.getMessage)
  				throw e
			  }
			}
  		b
  	}
  	
  	/**
	 * @author jiangbuyun
	 * @version	0.7
	 * @since	2017-09-28
	 * @category	删除指定的键值
	 * @param	key String 指定的键
	 * @return	返回成功或者失败 Boolean
	 * 该类暂时没有实现，因为RepChain不能够删除已有的WorldState
	 * */
  	override def Delete (key : String) : Boolean={
		  var b : Boolean = true;
			//todo 目前没有实现，原则上不允许删除任何状态
		  setUseTime
		  b;
	  }
  	
  	private var useTime:Long = System.currentTimeMillis();
  	
  	/**
	 * @author jiangbuyun
	 * @version	0.7
	 * @since	2017-09-28
	 * @category	内部使用，更新实例的访问时间，每次访问的时候都会调用该方法
	 * @param
	 * @return	无
	 * */
  	def setUseTime{
  	  this.useTime = System.currentTimeMillis();
  	}
  	
  	/**
	 * @author jiangbuyun
	 * @version	0.7
	 * @since	2017-09-28
	 * @category	内部使用，获取该实例最后一次使用时间
	 * @param
	 * @return	长整型，最后一次的使用时间
	 * */
  	def getUseTime:Long={
  	  this.useTime
  	}
  	
  	
  	//////////////////////////////Endorsement 背书验证/////////////////////////////////////////////////////////////////////////////
  	/**
	 * @author jiangbuyun
	 * @version	0.7
	 * @since	2017-09-28
	 * @category	根据交易id获取chaincode id 
	 * @param	block Block 待写入的区块,txid String 待查询的交易id
	 * @return	如果成功返回chaincode id，否则返回空字符串
	 * */
  	private def getTxidFormBlock(block:Block,txid:String):String={
		  var rel = ""
		  if(block != null){
		    var trans = block.transactions
		    if(trans.length > 0){
		       val loopbreak = new Breaks
           loopbreak.breakable(
      		      trans.foreach(f=>{
      		        if(f.id.equals(txid)){
      		          rel = IdTool.getCid(f.cid.get)
      		          //val chainspec = f.payload.get
      		          //rel = chainspec.chaincodeID.get.name
      		          loopbreak.break
      		        }
      		      })
		      )
		    }
		  }
		  rel
		}
  	
  	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  	
  ////////////////////////////////worldstate merkle 继承实现///////////////////////////////////////////////////////////////////////
	/*override def   GetComputeMerkle:Array[Byte]={
	  setUseTime
	  val b = this.merkleop.getMerkleHash
	  b
	}
	
  override def   GetComputeMerkle4String:String={
    setUseTime
    val c = this.merkleop.getMerkelHash4String
    c
  }*/
	///////////////////////////////////////////////////////////////////////////////////////////////////////
}

/**
 * @author jiangbuyun
 * @version	0.7
 * @since	2017-09-28
 * @category	某个系统的多实例管理类，管理某个系统所有的多实例，包含自动释放超时的实例。
 * */
private class  MultiDBMgr (val SystemName:String) {
    import org.slf4j.LoggerFactory
    private val  checktime = 60*1000//如果某个实例超过60s没有使用，就自动清理
    private var  DBOps  =  new scala.collection.mutable.HashMap[String,ImpDataPreload]()
    protected def log = LoggerFactory.getLogger(this.getClass)
    /**
	 * @author jiangbuyun
	 * @version	0.7
	 * @since	2017-09-28
	 * @category	根据实例名称获取预执行的数据访问类
	 * @param	InstanceName String 实例名称
	 * @return	如果成功返回ImpDataPreload实例，否则为null
	 * */
	  def  GetImpDataPreload( InstanceName:String) : ImpDataPreload = {
	     var DBOp : ImpDataPreload = null
	     
  	     synchronized{
  	       try{
  	         clear
    	       if(DBOps.contains(InstanceName)){
    	           DBOp = DBOps(InstanceName)
    	       }else{
    	         DBOp = new ImpDataPreload(SystemName,InstanceName)
    	         DBOps.put(InstanceName, DBOp)
    	       }
    	       if(DBOp != null){
    	         DBOp.setUseTime
    	       }
  	      }catch{
    		    case e:Exception =>{
    		      RepLogger.error(RepLogger.Storager_Logger,  
  			      "MultiDBMgr_" + SystemName + "_" + "ImpDataPreload Create failed, error info= "+e.getMessage)
    		      throw e
    		    }
  		    }
  	      DBOp
  	    }
  }
    
     /**
	 * @author jiangbuyun
	 * @version	0.7
	 * @since	2017-09-28
	 * @category	清理系统中空闲超时的实例
	 * @param
	 * @return	无
	 * */
  def clear{
    var exists : ArrayBuffer[String] = new ArrayBuffer[String]()
    val iterator = DBOps.keysIterator
    while (iterator.hasNext) {
      val key = iterator.next()
      val tmp = DBOps(key)
      if(tmp != null && (System.currentTimeMillis() - tmp.getUseTime) > this.checktime){
          exists += key
      }
    }
    exists.foreach(f => {
      try{
        DBOps -= f
      }catch{
        case e:Exception =>{
          RepLogger.error(RepLogger.Storager_Logger,  
  			      "MultiDBMgr_" + SystemName + "_" + s"ImpDataPreload clear failed, error info= "+e.getMessage)
    		      throw e
    		    }
      }
    })
  }
  
  /**
	 * @author jiangbuyun
	 * @version	0.7
	 * @since	2017-09-28
	 * @category	释放指定的实例
	 * @param	InstanceName String 实例名称
	 * @return	无
	 * */
  def Free(InstanceName:String)={
    try{
      //DBOps.remove(InstanceName)
      DBOps -= InstanceName
     }catch{
        case e:Exception =>{
          RepLogger.error(RepLogger.Storager_Logger,  
  			      "MultiDBMgr_" + SystemName + "_" + "ImpDataPreload Free failed, error info= "+e.getMessage)
    		      throw e
    		    }
      }
  }
}

////////////////以下半生对象用来做存储实例的管理，相当于存储管理的类工厂，每一个系统只能产生一个实例来进行操作///////////////
/**
 * @author jiangbuyun
 * @version	0.7
 * @since	2017-09-28
 * @category	系统管理类，管理每个系统中的多实例管理器。
 * */
object ImpDataPreloadMgr{
    import org.slf4j.LoggerFactory
    protected def log = LoggerFactory.getLogger(this.getClass)
    private var  singleobjs  =  new scala.collection.mutable.HashMap[String,MultiDBMgr]()
    /**
	 * @author jiangbuyun
	 * @version	0.7
	 * @since	2017-09-28
	 * @category	根据系统名称、实例名称获取预执行的数据访问类
	 * @param	SystemName String 系统名称,InstanceName String 实例名称
	 * @return	如果成功返回ImpDataPreload实例，否则为null
	 * */
	   def  GetImpDataPreload( SystemName:String,InstanceName:String) : ImpDataPreload = {
	     var singleobj : MultiDBMgr = null
	     var dbop : ImpDataPreload = null
	     
  	     synchronized{
  	       try{
  	         //clear  //暂时先不每次做全局清除
    	       if(singleobjs.contains(SystemName)){
    	           singleobj = singleobjs(SystemName)
    	       }else{
    	         singleobj = new MultiDBMgr(SystemName)
    	         singleobjs.put(SystemName, singleobj)
    	       }
    	       dbop = singleobj.GetImpDataPreload(InstanceName)
  	      }catch{
    		    case e:Exception =>{
    		      RepLogger.error(RepLogger.Storager_Logger,  
  			      "ImpDataPreloadMgr_" + SystemName + "_" + "ImpDataPreload Create failed, error info= "+e.getMessage)
    		      throw e
    		    }
  		    }
  	      dbop
  	    }
  }
    
    /**
	 * @author jiangbuyun
	 * @version	0.7
	 * @since	2017-09-28
	 * @category	从多实例管理器中清理系统中空闲超时的实例
	 * @param
	 * @return	无
	 * */
 private def clear{
    val iterator = singleobjs.keysIterator
    while (iterator.hasNext) {
      val key = iterator.next()
      val tmp = singleobjs(key)
      if(tmp != null){
        tmp.clear
      }
    }
  }
    
 /**
	 * @author jiangbuyun
	 * @version	0.7
	 * @since	2017-09-28
	 * @category	从多实例管理器中释放指定的实例
	 * @param	SystemName String 系统名称，InstanceName String 实例名称
	 * @return	无
	 * */
  def Free(SystemName:String,InstanceName:String)={
       var singleobj : MultiDBMgr = null
	     
  	     synchronized{
  	       try{
    	       if(singleobjs.contains(SystemName)){
    	           singleobj = singleobjs(SystemName)
    	           if(singleobj != null) singleobj.Free(InstanceName)
    	       }
  	      }catch{
    		    case e:Exception =>{
    		      RepLogger.error(RepLogger.Storager_Logger,  
  			      "ImpDataPreloadMgr_" + SystemName + "_" + "ImpDataPreload Free failed, error info= "+e.getMessage)
    		      throw e
    		    }
  		    }
  	    }
  }
}
////////////////end///////////////