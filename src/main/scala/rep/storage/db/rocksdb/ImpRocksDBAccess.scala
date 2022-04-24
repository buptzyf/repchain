package rep.storage.db.rocksdb

import java.util.concurrent.ConcurrentHashMap

import rep.storage.db.common.IDBAccess
import org.rocksdb.{Options, RocksDB, WriteBatch, WriteOptions}
import rep.log.RepLogger
import rep.storage.filesystem.FileOperate

import scala.collection.mutable

/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-07
 * @category	RocksDB数据库访问实现
 * */
class ImpRocksDBAccess private extends IDBAccess{
  private var DBPath : String = ""
  private var cacheSize:Long = 128 * 1048576//初始化RocksDB系统的缓存，默认为50M,修改默认为128
  private var db: RocksDB = null
  private var opts: Options = null
  private var batch: WriteBatch = null
  private var writeOpts:WriteOptions = null
  private var isEncrypt:Boolean = false

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-04-07
   * @category 构造RocksDB实现
   * @param DBPath 数据库存放路径,cacheSize 设置数据库的缓存,isEncrypt 是否加密数据u
   * @return
   **/
  def this(DBPath:String,cacheSize:Long,isEncrypt:Boolean=false){
    this()
    this.isEncrypt = isEncrypt
    this.DBPath = DBPath
    this.cacheSize = cacheSize
    InitDB
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-04-07
   * @category 初始化RocksDB
   * @return
   **/
  private def InitDB:Unit={
    try{
      val b = FileOperate.MkdirAll(this.DBPath)
      if (!b) {
        RepLogger.error(RepLogger.Storager_Logger,  "ImpRocksDBAccess error:db path create failed," +
          s"dbpath=${this.DBPath}")
      }
    }catch {
      case e:Exception =>
        RepLogger.error(RepLogger.Storager_Logger,  "ImpRocksDBAccess error:db path create Exception," +
          s"dbpath=${this.DBPath},msg=${e.getCause}")
    }
    try{
      RocksDB.loadLibrary()
      this.opts = new Options();
      this.opts.setCreateIfMissing(true);
      this.opts.setDbWriteBufferSize(this.cacheSize)
      this.db = RocksDB.open(opts, this.DBPath);
      this.writeOpts = new WriteOptions()
    }catch{
      case e:Exception=>{
        RepLogger.error(RepLogger.Storager_Logger,  "ImpRocksDBAccess error:DB Create error," +
          s"dbpath=${this.DBPath},msg=${e.getCause}")
      }
    }
  }


  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-04-07
   * @category 获取指定的键值
   * @param key String 指定的键
   * @return 返回对应键的值 Array[Byte]
   **/
  override def getBytes(key: String): Array[Byte] = {
    var r: Array[Byte] = null
    try {
      if(key != null){
        var b = this.db.get(key.getBytes())
        if(b != null){
          r = if(this.isEncrypt) cipherTool.decrypt(b) else b
        }
      }else{
        throw new Exception(s"input key is null")
      }
    } catch {
      case e: Exception => {
        RepLogger.error(RepLogger.Storager_Logger,  "ImpRocksDBAccess error:getBytes error," +
          s"dbpath=${this.DBPath},msg=${e.getCause}")
        throw e
      }
    }
    r
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-04-07
   * @category 存储指定的键和值到数据库
   * @param 	key String 指定的键，bb Array[Byte] 要存储的值
   * @return 返回成功或者失败 Boolean
   **/
  override def putBytes(key: String, bb: Array[Byte]): Boolean = {
    var r: Boolean = false
    try {
      if(key != null){
        if(bb != null){
          val b = if(this.isEncrypt) cipherTool.encrypt(bb) else bb
          if(this.isTransaction){
            this.batch.put(key.getBytes(), b);
          }else{
            this.db.put(key.getBytes(), b)
          }
          r = true
        }else{
          throw new Exception(s"input value is null,key=${key}")
        }
      }else{
        throw new Exception(s"input key is null")
      }
    } catch {
      case e: Exception => {
        RepLogger.error(RepLogger.Storager_Logger,  "ImpRocksDBAccess error:putBytes error," +
          s"dbpath=${this.DBPath},msg=${e.getCause}")
        throw e
      }
    }
    r
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-04-07
   * @category 删除指定的键值
   * @param 	key String 指定的键
   * @return 返回成功或者失败 Boolean
   **/
  override def delete(key: String): Boolean = {
    var r: Boolean = false;

    try{
      if(key != null){
        if(this.isTransaction){
          this.batch.delete(key.getBytes());
        }else{
          this.db.delete(key.getBytes());
        }
        r = true
      }else{
        throw new Exception(s"input key is null")
      }
    }catch{
      case e:Exception =>{
        RepLogger.error(RepLogger.Storager_Logger,  "ImpRocksDBAccess error:Delete error," +
          s"dbpath=${this.DBPath},msg=${e.getCause}")
        throw e
      }
    }
    r
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	开始事务
   * @return
   * */
  override protected def beginTransaction = {
    try {
      if (this.isTransaction  && this.batch != null) {
        try {
          this.batch.close()
        } catch {
          case e: Exception => {
            RepLogger.error(RepLogger.Storager_Logger,  "ImpRocksDBAccess error:beginTransaction close error," +
              s"dbpath=${this.DBPath},msg=${e.getCause}")
          }
        } finally {
          this.batch = null
        }
      }
      this.batch = new WriteBatch()
    } catch {
      case e: Exception => {
        RepLogger.error(RepLogger.Storager_Logger,  "ImpRocksDBAccess error:beginTransaction open error," +
          s"dbpath=${this.DBPath},msg=${e.getCause}")
        throw e
      }
    }
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	提交事务
   * @return
   * */
  override protected def commit = {
    try {
      if (this.isTransaction && this.batch != null) {
        this.db.write(this.writeOpts,batch)
      }
    } catch {
      case e: Exception => {
        RepLogger.error(RepLogger.Storager_Logger,  "ImpRocksDBAccess error:commit write error," +
          s"dbpath=${this.DBPath},msg=${e.getCause}")
        throw e
      }
    } finally {
      try {
        if (this.batch != null) {
          this.batch.close()
        }
      } catch {
        case e: Exception => {
          RepLogger.error(RepLogger.Storager_Logger,  "ImpRocksDBAccess error:commit close error," +
            s"dbpath=${this.DBPath},msg=${e.getCause}")
        }
      } finally {
        this.batch = null
      }
    }
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	回滚事务
   * @return
   * */
  override protected def rollback = {
    try {
      if (this.batch != null) {
        this.batch.close()
      }
    } catch {
      case e: Exception => {
        RepLogger.error(RepLogger.Storager_Logger,  "ImpRocksDBAccess error:rollback close error," +
          s"dbpath=${this.DBPath},msg=${e.getCause}")
      }
    } finally {
      this.batch = null
    }
  }
}

/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-07
 * @category	ImpRocksDBAccess类的伴生对象，用于单实例的生成。
 */
object ImpRocksDBAccess{
  private var RocksDBInstances = new ConcurrentHashMap[String, ImpRocksDBAccess]()
  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	根据数据库路径建立RocksDB数据库访问实例
   * @param	DBPath String 数据库路径;cacheSize 缓存大小,isEncrypt 是否需要加密
   * @return	如果成功返回ImpRocksDBAccess实例，否则为null
   */
  def getDBAccess(DBPath: String,cacheSize:Long,isEncrypt:Boolean=false): ImpRocksDBAccess = {
    var instance: ImpRocksDBAccess = null

    if (RocksDBInstances.containsKey(DBPath)) {
      instance = RocksDBInstances.get(DBPath)
    } else {
      instance = new ImpRocksDBAccess(DBPath,cacheSize,isEncrypt)
      val old = RocksDBInstances.putIfAbsent(DBPath,instance)
      if(old != null){
        instance = old
      }
    }
    instance

  }
}