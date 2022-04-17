package rep.storage.db.factory

import rep.app.conf.RepChainConfig
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
  def getDBAccess(systemName:String):IDBAccess={
    synchronized{
      val config =  RepChainConfig.getSystemConfig(systemName)
      val cacheSize : Long = config.getStorageDBCacheSize * 1024 * 1024
      val dbpath = FileOperate.mergeFilePath(Array[String](config.getStorageDBPath,config.getStorageDBName))
      config.getStorageDBType match{
        case "LevelDB"=>
          ImpLevelDBAccess.getDBAccess(dbpath,cacheSize)
        case "RocksDB"=>
          ImpRocksDBAccess.getDBAccess(dbpath,cacheSize)
        case - =>
          ImpLevelDBAccess.getDBAccess(dbpath,cacheSize)
      }
    }
  }
}
