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

import rep.storage.leveldb.AbstractLevelDB

import scala.collection.mutable
import rep.protos.peer._
import java.io.File

import org.fusesource.leveldbjni.JniDBFactory
import org.iq80.leveldb.DB
import org.iq80.leveldb.DBIterator
import org.iq80.leveldb.Options
import org.iq80.leveldb.WriteBatch
import rep.storage.cfg.StoreConfig4Scala
import rep.storage.util.pathUtil

import scala.collection.mutable
import rep.storage.util.StoreUtil
import com.google.protobuf.ByteString
import rep.crypto._
import rep.log.RepLogger
import rep.storage.block.BlockFileMgr

/**
 * @author jiangbuyun
 * @version	0.7
 * @since	2017-09-28
 * @category	该类实现LevelDB数据库的操作，并且添加外部操作定义。
 */
abstract class IDataAccess(val SystemName: String) extends AbstractLevelDB(SystemName: String) {
  private var DBDataPath: String = ""
  private var opts: Options = null
  private var leveldbfactory: JniDBFactory = JniDBFactory.factory
  private var db: DB = null
  private var synchObject: Object = new Object()
  private var IsTrans: Boolean = false
  private var batch: WriteBatch = null

  /*//区块存储时采用缓存策略，
  private var cache :java.util.concurrent.ConcurrentHashMap[String,Array[Byte]] = new java.util.concurrent.ConcurrentHashMap[String,Array[Byte]]
  private var filemgr: BlockFileMgr = new BlockFileMgr(this.SystemName)*/


  if (SystemName == null || SystemName.equalsIgnoreCase("")) {
    RepLogger.info(RepLogger.Storager_Logger,  SystemName+"~"+"start")
  }

  //val sc: StoreConfig = StoreConfig.getStoreConfig()
  DBDataPath = StoreConfig4Scala.getDbPath(SystemName)
  val b = pathUtil.MkdirAll(this.DBDataPath)
  if (!b) {
    RepLogger.error(RepLogger.Storager_Logger,  "IDataAccess_" + SystemName + "_" + "DBOP Create error,db store dir is null!")
    throw new Exception("db store dir is null! " + DBDataPath)
  }

  opts = new Options().createIfMissing(true)
  opts.cacheSize(128 * 1048576); //初始化leveldb系统的缓存，默认为50M,修改默认为128
  //opts.blockSize(32 * 1048576)
  db = leveldbfactory.open(new File(this.DBDataPath), opts)


  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	获取当前系统的名称
   * @param
   * @return	返回当前系统的名称 String
   */
  override def getSystemName: String = {
    SystemName
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	获取当前实例的名称
   * @param
   * @return	返回当前实例的名称 String
   */
  override def getInstanceName: String = {
    "single level db op"
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	判断是否开启事务
   * @param
   * @return	返回开启事务或者关闭事务 Boolean true=开启事务，false=关闭事务
   */
  def IsBeginTrans: Boolean = {
    IsTrans
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	开启事务
   * @param
   * @return	无
   */
  def BeginTrans = {
    synchObject.synchronized {
      try {
        if (this.IsTrans  && this.batch != null) {
            try {
              this.batch.close()
            } catch {
              case e: Exception => {
                RepLogger.error(RepLogger.Storager_Logger,
                  "IDataAccess_" + SystemName + "_" + "DBOP BeginTrans failed, error info= " + e.getMessage)
                throw e
              }
            } finally {
              this.batch = null
            }
        }
        this.IsTrans = true
        this.batch = db.createWriteBatch()
      } catch {
        case e: Exception => {
          this.IsTrans = false
          this.batch = null
          RepLogger.error(RepLogger.Storager_Logger,  
            "IDataAccess_" + SystemName + "_" + "DBOP BeginTrans failed, error info= " + e.getMessage)
          throw e
        }
      }
    }
  }

  

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	提交事务
   * @param
   * @return	无
   */
  def CommitTrans = {
    synchObject.synchronized {
      try {
        if (this.IsTrans && this.batch != null) {
          this.db.write(batch)
        }
      } catch {
        case e: Exception => {
          RepLogger.error(RepLogger.Storager_Logger,  
            "IDataAccess_" + SystemName + "_" + "DBOP CommitTrans failed, error info= " + e.getMessage)
          throw e
        }
      } finally {
        this.IsTrans = false
        try {
          if (this.batch != null) {
            this.batch.close()
          }
        } catch {
          case e: Exception => {
            RepLogger.error(RepLogger.Storager_Logger,  
              "IDataAccess_" + SystemName + "_" + "DBOP CommitTrans failed, error info= " + e.getMessage)
          }
        } finally {
          this.batch = null
        }
      }
    }
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	事务回滚
   * @param
   * @return	无
   */
  def RollbackTrans = {
    synchObject.synchronized {
      try {
        if (this.batch != null) {
          this.batch.close()
        }
      } catch {
        case e: Exception => {
          RepLogger.error(RepLogger.Storager_Logger,  
            "IDataAccess_" + SystemName + "_" + "DBOP RollbackTrans failed, error info= " + e.getMessage)
          throw e
        }
      } finally {
        this.IsTrans = false
        this.batch = null
      }
    }
  }

  protected def Option2ArrayByte(bb: Option[Array[Byte]]): Array[Byte] = {
    var v: Array[Byte] = null
    bb match {
      case None => v = None.toArray
      case _    => v = bb.get
    }
    v
  }
  
  protected def ArrayByte2Option(bb : Array[Byte]):Option[Array[Byte]] = {
    var v :Option[Array[Byte]] = None
    if(bb != null && bb.length > 0){
      v = Some(bb)
    }
    v
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	获取指定的键值
   * @param	key String 指定的键
   * @return	返回对应键的值 Array[Byte]
   */
  override def Get(key: String): Array[Byte] = {
    var rb: Array[Byte] = null
    try {
      rb = this.db.get(key.getBytes())
    } catch {
      case e: Exception => {
        rb = null
        RepLogger.error(RepLogger.Storager_Logger,  
          "IDataAccess_" + SystemName + "_" + "DBOP Get failed, error info= " + e.getMessage)
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
   */
  override def Put(key: String, bb: Array[Byte]): Boolean = {
    var b: Boolean = true
    //synchObject.synchronized {
      try {
        var v: Array[Byte] = bb
        
        if(bb == null){
          v = None.toArray
        }
        
        if (this.IsTrans) {
          this.batch.put(key.getBytes(), v)
          
        } else {
          this.db.put(key.getBytes(), v)
        }

      } catch {
        case e: Exception => {
          b = false
          RepLogger.error(RepLogger.Storager_Logger,  
            "IDataAccess_" + SystemName + "_" + "DBOP Put failed, error info= " + e.getMessage)
          throw e
        }
      }
    //}
    b
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2019-05-09
   * @category	删除指定的键值
   * @param	key String 指定的键
   * @return	返回成功或者失败 Boolean
   * 该类暂时没有实现，因为RepChain不能够删除已有的WorldState
   * 开启这个方法的实现，因为要实现区块的回滚恢复。
   */
  override def Delete(key: String): Boolean = {
    var b: Boolean = true;
    synchObject.synchronized{
			try{
				if(this.IsTrans){
					this.batch.delete(key.getBytes());
				}else{
					this.db.delete(key.getBytes());
				}
			}catch{
				case e:Exception =>{
			    b = false
			    RepLogger.error(RepLogger.Storager_Logger,  
            "IDataAccess_" + SystemName + "_" + "DBOP Delete failed, error info= " + e.getMessage)
  				throw e
			  }
			}
		}
    b;
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	实现键值的模糊查询
   * @param	searchkey String 待查询的字符串，searchmode  Int 1 前端一致，2 包含，3后端一致
   * @return	返回查询结果，mutable.HashMap[String,Array[Byte]] 如果没有找到Map的Size=0
   */
  def FindByLike(searchkey: String, searchmode: Int): mutable.HashMap[String, Array[Byte]] = {
    var hm: mutable.HashMap[String, Array[Byte]] = new mutable.HashMap[String, Array[Byte]]()
    synchObject.synchronized {
      var iterator: DBIterator = null

      try {
        iterator = db.iterator()
        iterator.seekToFirst()

        while (iterator.hasNext()) {
          val nextvalue = iterator.next()
          val key = new String(nextvalue.getKey)
          var isskip = true
          searchmode match {
            case 1 =>
              if (key.startsWith(searchkey)) {
                isskip = false
              }

            case 2 =>
              if (key.contains(searchkey)) {
                isskip = false
              }

            case 3 =>
              if (key.endsWith(searchkey)) {
                isskip = false
              }
          }
          if (!isskip) {
            hm += key -> nextvalue.getValue
          }
        }
      } catch {
        case e: Exception => {
          RepLogger.error(RepLogger.Storager_Logger,  
            "IDataAccess_" + SystemName + "_" + "DBOP FindByLike failed, error info= " + e.getMessage)
          throw e
        }
      } finally {
        if (iterator != null) {
          try {
            iterator.close()
          } catch {
            case e: Exception => {
              RepLogger.error(RepLogger.Storager_Logger,  
                "IDataAccess_" + SystemName + "_" + "DBOP FindByLike failed, error info= " + e.getMessage)
            }
          }
        }
      }
    }
    return hm
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	实现键的模糊查询
   * @param	searchkey String 待查询的字符串，searchmode  Int 1 前端一致，2 包含，3后端一致
   * @return	返回查询结果，Array[String] 如果没有找到，返回结构为null
   */
  def FindKeyByLike(searchkey: String, searchmode: Int): Array[String] = {
    synchObject.synchronized {
      val tmphm = FindByLike(searchkey, searchmode)
      val ks = tmphm.keys.toArray
      ks
    }
  }

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	实现键值的模糊查询
   * @param	searchkey String 待查询的字符串 默认采用前端一致查找
   * @return	返回查询结果，mutable.HashMap[String,Array[Byte]] 如果没有找到Map的Size=0
   */
  def FindByLike(searchkey: String): mutable.HashMap[String, Array[Byte]] = {
    val hms = FindByLike(searchkey, 1)
    hms
  }

  ////////////////////////////////worldstate merkle 继承实现///////////////////////////////////////////////////////////////////////
  /*override def   GetComputeMerkle:Array[Byte]={
	  val b = this.merkleop.getMerkleHash
	  b
	}

  override def   GetComputeMerkle4String:String={
    val c = this.merkleop.getMerkelHash4String
    c
  }*/
  ///////////////////////////////////////////////////////////////////////////////////////////////////////

  /////////////////////////////////abstract method////////////////////////////////////////////////
  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	根据block的base64 hash值获取block
   * @param	base64 String block的hash64值
   * @return	返回block的字节数组，如果没有找到，返回null
   */
  def getBlockByBase64Hash(base64: String): Array[Byte]

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	根据block的hash值获取block
   * @param	bh String block的hash值
   * @return	返回block的字节数组，如果没有找到，返回null
   */
  def getBlockByHash(bh: String): Array[Byte]

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	根据block的hash值获取当前block的高度
   * @param	bh String block的hash值
   * @return	返回block的高度，如果没有找到，返回-1
   */
  def getBlockHeightByHash(bh: String): Long

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	根据交易Id值获取block数据
   * @param	bh String 交易的Id值
   * @return	返回block的字节数组，如果没有找到，返回null
   */
  def getBlockByTxId(bh: String): Array[Byte]
  
  /**
   * @author jiangbuyun
   * @version	1.0
   * @since	2017-09-28
   * @category	根据交易Id值获取block
   * @param	bh String 交易的id值
   * @return	返回block对象，如果没有找到，返回null
   */
  def getBlock4ObjectByTxId(bh:String):Block
  
  /**
   * @author jiangbuyun
   * @version	1.0
   * @since	2019-04-28
   * @category	根据chaincodeid获取指定交易
   * @param	cid String 链码id
   * @return	返回Transaction对象，如果没有找到，返回None
   */
  def getTransOfContractFromChaincodeId(cid:String): Option[Transaction]
  
  /**
   * @author jiangbuyun
   * @version	1.0
   * @since	2019-04-28
   * @category	根据交易Id值获取指定交易
   * @param	txid String 交易的Id值
   * @return	返回Transaction对象，如果没有找到，返回None
   */
  def getTransDataByTxId(txid:String):Option[Transaction]

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	根据高度获取Block的index
   * @param	h Long block的高度值
   * @return	返回blockindex，如果没有找到，返回null
   */
  def getBlockByHeight(h: Long): Array[Byte]

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	从某个高度开始（包含这个高度）的所有块取出来，h：开始的高度，h 必须大于0，高度是从1开始
   * limits 返回块的数量，默认值为0，如果大于0，返回指定数量的块，如果块数小于该值返回实际数量
   * 默认最多1000条
   * @param	h Int block的高度值，limits 返回记录的条数
   * @return	返回指定长度的Block数组，如果没有找到，返回长度=0的数组
   */
  def getBlocksFromHeight(h: Int, limits: Int): Array[Array[Byte]]

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	从某个高度开始（包含这个高度）的所有块取出来，h：开始的高度，h 必须大于0，高度是从1开始
   * limits 返回块的数量，默认值为0，如果大于0，返回指定数量的块，如果块数小于该值返回实际数量
   * 默认最多1000条
   * @param	h Int block的高度值
   * @return	返回最多1000条的Block数组，如果没有找到，返回长度=0的数组
   */
  def getBlocksFromHeight(h: Int): Array[Array[Byte]]

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	从某个高度开始（包含这个高度）的所有块取出来，h：开始的高度，h 必须大于0，高度是从1开始
   * limits 返回块的数量，默认值为0，如果大于0，返回指定数量的块，如果块数小于该值返回实际数量
   * 默认最多1000条
   * @param	h Int block的高度值，limits 返回记录的条数
   * @return	返回指定长度的Block数组，如果没有找到，返回长度=0的数组
   */
  def getBlocks4ObjectFromHeight(h: Int, limits: Int): Array[Block]
  
  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2019-09-11
   * @category	根据交易ID获取交易入块时间
   * @param	txid 交易id
   * @return	返回出块时间
   */
  def getBlockTimeOfTxid(txid : String): String
  
   /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2019-09-11
   * @category	根据块的高度获取交易入块时间
   * @param	h 块高度
   * @return	返回出块时间
   */
  def getBlockTimeOfHeight(h : Long): String
  
  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	从某个高度开始（包含这个高度）的所有块取出来，h：开始的高度，h 必须大于0，高度是从1开始
   * limits 返回块的数量，默认值为0，如果大于0，返回指定数量的块，如果块数小于该值返回实际数量
   * 默认最多1000条
   * @param	h Int block的高度值
   * @return	返回最多1000条的Block数组，如果没有找到，返回长度=0的数组
   */
  def getBlocks4ObjectFromHeight(h: Int): Array[Block]

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	根据链的高度来获取等于这个高度值的block
   * @param	h Long block的高度值
   * @return	返回Block字节数组，如果没有找到，返回null
   */
  def getBlock4ObjectByHeight(h: Long): Block

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	返回当前区块链的chaininfo
   * @param
   * @return	返回链码信息 BlockchainInfo
   */
  def getBlockChainInfo(): BlockchainInfo

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	同时写入多个区块到系统
   * @param	blocks Array[Block] 待写入系统的区块数组
   * @return	返回成功写入的区块的数量
   */
  def restoreBlocks(blocks: Array[Block]): Int

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	写入单个区块到系统
   * @param	block  待写入系统的区块
   * @return	如果成功返回true，否则返回false
   */
  def restoreBlock(block: Block):  (Boolean,Long,Long,String,String,String)

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2019-05-09
   * @category	回滚块到某个高度
   * @param
   * @return	如果成功返回true，否则返回false
   */
  def rollbackToheight(toHeight: Long):  Boolean
  
  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	获取链码的高度
   * @param
   * @return	成功返回当前区块链的高度 Long
   */
  def getBlockHeight(): Long

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	获取系统交易的数量
   * @param
   * @return	返回系统当前的交易数量
   */
  def getBlockAllTxNumber(): Long

  /**
   * @author jiangbuyun
   * @version	0.7
   * @since	2017-09-28
   * @category	获取当前存储区块字节的文件编号
   * @param
   * @return	返回文件编号
   */
  def getMaxFileNo(): Int
  //////////////////////////////////////////////////////////////////////////////////////////////
}