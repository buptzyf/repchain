package rep.storage.db.leveldb

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import org.fusesource.leveldbjni.JniDBFactory
import org.iq80.leveldb.{DB, Options, WriteBatch}
import rep.log.RepLogger
import rep.storage.db.common.IDBAccess
import rep.storage.filesystem.FileOperate

/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-07
 * @category	LevelDB数据库访问实现
 * */
class ImpLevelDBAccess private extends IDBAccess{
  private var DBPath : String = ""
  private var cacheSize:Long = 128 * 1048576//初始化leveldb系统的缓存，默认为50M,修改默认为128
  private var opts: Options = null
  private val levelDBFactory: JniDBFactory = JniDBFactory.factory
  private var db: DB = null
  private var batch: WriteBatch = null
  private var isEncrypt : Boolean = false

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-04-07
   * @category 构造LevelDB实现
   * @param DBPath 数据库存放路径,cacheSize 设置数据库的缓存,isEncrypt 是否加密
   * @return
   **/
  private def this(DBPath:String,cacheSize:Long,isEncrypt:Boolean=false){
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
   * @category 初始化LevelDB
   * @return
   **/
  private def InitDB:Unit={
    try{
      val b = FileOperate.MkdirAll(this.DBPath)
      if (!b) {
        RepLogger.error(RepLogger.Storager_Logger,  "ImpLevelDBAccess error:db path create failed," +
          s"dbpath=${this.DBPath}")
      }else{
        RepLogger.trace(RepLogger.Storager_Logger,s"DB's Dir Exist, dbType=LevelDB,dbPath=${DBPath}")
      }
    }catch {
      case e:Exception =>
        RepLogger.error(RepLogger.Storager_Logger,  "ImpLevelDBAccess error:db path create Exception," +
          s"dbpath=${this.DBPath},msg=${e.getCause}")
    }
    try{
      this.opts = new Options().createIfMissing(true)
      this.opts.cacheSize(this.cacheSize);
      this.opts.maxOpenFiles(this.maxOpenFiles)
      this.db = this.levelDBFactory.open(new File(this.DBPath), opts)
      RepLogger.trace(RepLogger.Storager_Logger,s"DB create success, dbType=LevelDB,dbPath=${DBPath}")
    }catch{
      case e:Exception=>{
        RepLogger.error(RepLogger.Storager_Logger,  "ImpLevelDBAccess error:DB Create error," +
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
    var isRepeat : Boolean = false
    var count : Int = 0
    do{
      isRepeat = false
      count += 1
      try {
        if (key != null) {
          RepLogger.trace(RepLogger.Storager_Logger, s"DB operate getBytes, key=${key},dbName=${this.getDBName}")
          val b = this.db.get(key.getBytes())
          if (b != null) {
            r = if (this.isEncrypt) this.cipherTool.decrypt(b) else b
          } else {
            RepLogger.trace(RepLogger.Storager_Logger, s"DB operate getBytes, data is null key=${key},dbName=${this.getDBName}")
          }
        } else {
          throw new Exception(s"input key is null")
        }
      } catch {
        case e: Exception =>
          if(e.getMessage.indexOf("Could not create random access file") > 0){
            isRepeat = true
          }else{
            RepLogger.error(RepLogger.Storager_Logger, "ImpLevelDBAccess error:getBytes error," +
              s"dbpath=${this.DBPath},msg=${e.getCause}")
            throw e
          }
      }
    }while(isRepeat && count <= this.repeatTimes)
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
    var isRepeat: Boolean = false
    var count: Int = 0
    do {
      isRepeat = false
      count += 1
      try {
        if (key != null) {
          if (bb != null) {
            val b = if (this.isEncrypt) this.cipherTool.encrypt(bb) else bb
            RepLogger.trace(RepLogger.Storager_Logger, s"DB operate putBytes, key=${key}")
            if (this.isTransaction) {
              this.batch.put(key.getBytes(), b);
            } else {
              this.db.put(key.getBytes(), b)
            }
            r = true
          } else {
            throw new Exception(s"input value is null,key=${key},dbName=${this.getDBName}")
          }
        } else {
          throw new Exception(s"input key is null")
        }
      } catch {
        case e: Exception =>
          if (e.getMessage.indexOf("Could not create random access file") > 0) {
            isRepeat = true
          } else {
            RepLogger.error(RepLogger.Storager_Logger, "ImpLevelDBAccess error:putBytes error," +
              s"dbpath=${this.DBPath},msg=${e.getCause}")
            throw e
          }
      }
    }while(isRepeat && count <= this.repeatTimes)
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
    var isRepeat: Boolean = false
    var count: Int = 0
    do {
      isRepeat = false
      count += 1
      try {
        if (key != null) {
          if (this.isTransaction) {
            this.batch.delete(key.getBytes());
          } else {
            this.db.delete(key.getBytes());
          }
          r = true
        } else {
          throw new Exception(s"input key is null")
        }
      } catch {
        case e: Exception =>
          if (e.getMessage.indexOf("Could not create random access file") > 0) {
            isRepeat = true
          } else {
            RepLogger.error(RepLogger.Storager_Logger, "ImpLevelDBAccess error:Delete error," +
              s"dbpath=${this.DBPath},msg=${e.getCause}")
            throw e
          }
      }
    }while(isRepeat && count <= this.repeatTimes)
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
            RepLogger.error(RepLogger.Storager_Logger,  "ImpLevelDBAccess error:beginTransaction close error," +
              s"dbpath=${this.DBPath},msg=${e.getCause}")
          }
        } finally {
          this.batch = null
        }
      }
      this.batch = db.createWriteBatch()
    } catch {
      case e: Exception => {
        RepLogger.error(RepLogger.Storager_Logger,  "ImpLevelDBAccess error:beginTransaction open error," +
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
        this.db.write(batch)
      }
    } catch {
      case e: Exception => {
        RepLogger.error(RepLogger.Storager_Logger,  "ImpLevelDBAccess error:commit write error," +
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
          RepLogger.error(RepLogger.Storager_Logger,  "ImpLevelDBAccess error:commit close error," +
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
        RepLogger.error(RepLogger.Storager_Logger,  "ImpLevelDBAccess error:rollback close error," +
          s"dbpath=${this.DBPath},msg=${e.getCause}")
      }
    } finally {
      this.batch = null
    }
  }

  override def getDBName: String = {
    this.DBPath
  }
}

/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-07
 * @category	ImpLevelDBAccess类的伴生对象，用于单实例的生成。
 */
object ImpLevelDBAccess {
  private val LevelDBInstances = new ConcurrentHashMap[String, ImpLevelDBAccess]()

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	根据数据库路径建立LevelDB数据库访问实例
   * @param	DBPath String 数据库路径;cacheSize 缓存大小，isEncrypt 是否需要加密
   * @return	如果成功返回ImpLevelDBAccess实例，否则为null
   */
  def getDBAccess(DBPath: String,cacheSize:Long,isEncrypt:Boolean=false): ImpLevelDBAccess = {
    var instance: ImpLevelDBAccess = null

    if (LevelDBInstances.containsKey(DBPath)) {
      RepLogger.trace(RepLogger.Storager_Logger,s"DBInstance exist, dbType=LevelDB,dbPath=${DBPath}")
      instance = LevelDBInstances.get(DBPath)
    } else {
      RepLogger.trace(RepLogger.Storager_Logger,s"DBInstance not exist,create new Instance, dbType=LevelDB,dbPath=${DBPath}")
      instance = new ImpLevelDBAccess(DBPath,cacheSize,isEncrypt)
      val old = LevelDBInstances.putIfAbsent(DBPath,instance)
      if(old != null){
        instance = old
      }
    }
    instance

  }
}
