package rep.storage.db.factory

import rep.app.conf.RepChainConfig
import rep.log.RepLogger
import rep.storage.db.common.IDBAccess
import rep.storage.db.leveldb.ImpLevelDBAccess
import rep.storage.db.rocksdb.ImpRocksDBAccess
import rep.storage.filesystem.FileOperate


/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-08
 * @category	数据库访问实例建立工厂
 * */
object DBFactory {
  def getDBAccess(config:RepChainConfig):IDBAccess={
      val cacheSize : Long = config.getStorageDBCacheSize * 1024 * 1024
      val dbPath = FileOperate.mergeFilePath(Array[String](config.getStorageDBPath,config.getStorageDBName))
      config.getStorageDBType match{
        case "LevelDB"=>
          RepLogger.trace(RepLogger.Storager_Logger,s"system=${config.getSystemConf},dbType=LevelDB,dbPath=${dbPath}")
          ImpLevelDBAccess.getDBAccess(dbPath,cacheSize)
        case "RocksDB"=>
          RepLogger.trace(RepLogger.Storager_Logger,s"system=${config.getSystemConf},dbType=LevelDB,dbPath=${dbPath}")
          ImpRocksDBAccess.getDBAccess(dbPath,cacheSize)
        case _ =>
          RepLogger.trace(RepLogger.Storager_Logger,s"system=${config.getSystemConf},dbType=LevelDB,dbPath=${dbPath}")
          ImpLevelDBAccess.getDBAccess(dbPath,cacheSize)
      }
  }
}
