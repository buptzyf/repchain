package rep.storage.filesystem.factory

import com.googlecode.concurrentlinkedhashmap.{ConcurrentLinkedHashMap, EvictionListener, Weighers}
import rep.app.conf.RepChainConfig
import rep.log.RepLogger
import rep.storage.filesystem.FileOperate
import rep.storage.filesystem.common.{IFile, IFileReader, IFileWriter}
import rep.storage.filesystem.localfile.{ImpFileReader, ImpFileWriter}

/**
 * @author jiangbuyun
 * @version 2.0
 * @since 2022-04-10
 * @category 构造文件访问实例工厂
 **/
object FileFactory {
  private val cacheSize = 30 //缓存文件读写器，默认30个

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-04-13
   * @category 建立文件读取实例集合的监听器，监听缓存释放
   * @param
   * @return
   **/
  private val listenerOfReader : EvictionListener[String, IFileReader] = new EvictionListener[String, IFileReader]() {
    override def onEviction(key:String, value:IFileReader) {
      RepLogger.info(RepLogger.Storager_Logger,s"ReadCache remove reader,key=${key}")
    }
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-04-13
   * @category 建立文件写实例集合的监听器，监听缓存释放
   * @param
   * @return
   **/
  private val listenerOfWriter : EvictionListener[String, IFileWriter] = new EvictionListener[String, IFileWriter]() {
    override def onEviction(key:String, value:IFileWriter) {
      RepLogger.info(RepLogger.Storager_Logger,s"WriteCache remove writer,key=${key}")
    }
  }

  private implicit val reads = new ConcurrentLinkedHashMap.Builder[String, IFileReader]().maximumWeightedCapacity(cacheSize).listener(listenerOfReader).weigher(Weighers.singleton[IFileReader]).build
  private implicit val writers = new ConcurrentLinkedHashMap.Builder[String, IFileWriter]().maximumWeightedCapacity(cacheSize).listener(listenerOfWriter).weigher(Weighers.singleton[IFileWriter]).build


  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-04-13
   * @category 根据系统名称和文件号建立文件读取实例
   * @param systemName: String 系统名称, fileNo: Int 文件号
   * @return 返回文件读取实例IFileReader
   **/
  def getReader(systemName: String, fileNo: Int): IFileReader = {
    var instance: IFileReader = null
    synchronized {
      val config = RepChainConfig.getSystemConfig(systemName)
      val fileName = FileOperate.mergeFilePath(Array[String](
        config.getStorageBlockFilePath, config.getStorageBlockFileName, fileNo.toString))
      if (reads.containsKey(fileName)) {
        instance = reads.get(fileName)
      } else {
        val fileType = config.getStorageBlockFileType
        instance = getFileReader(fileType, fileName)
        if (instance != null) {
          reads.put(fileName, instance)
          RepLogger.info(RepLogger.System_Logger,s"FileFactory getReader ,fileName=${fileName},put ReadCache")
        }
      }

      instance
    }
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-04-13
   * @category 根据系统名称和文件号建立文件写实例
   * @param systemName: String 系统名称, fileNo: Int 文件号
   * @return 返回文件写实例IFileWriter
   **/
  def getWriter(systemName: String, fileNo: Int): IFileWriter = {
    var instance: IFileWriter = null
    synchronized {
      val config = RepChainConfig.getSystemConfig(systemName)
      val fileName = FileOperate.mergeFilePath(Array[String](
        config.getStorageBlockFilePath, config.getStorageBlockFileName, fileNo.toString))
      if (writers.containsKey(fileName)) {
        instance = writers.get(fileName)
      } else {
        val fileType = config.getStorageBlockFileType
        instance = getFileWriter(fileType, fileName)
        if (instance != null) {
          writers.put(fileName, instance)
          RepLogger.info(RepLogger.System_Logger,s"FileFactory getWriter ,fileName=${fileName},put WriteCache")
        }
      }
      instance
    }
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-04-13
   * @category 根据文件类型和文件名建立文件读实例
   * @param fileType: String 文件类型, fileName: String 文件名（完整路径）
   * @return 返回文件读实例IFileReader
   **/
  private def getFileReader(fileType: String, fileName: String): IFileReader = {
    try {
      fileType match {
        case "localFileSystem" =>
          new ImpFileReader(fileName)
        case - =>
          new ImpFileReader(fileName)
      }
    } catch {
      case e: Exception =>
        null
    }
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-04-13
   * @category 根据文件类型和文件名建立文件写实例
   * @param fileType: String 文件类型, fileName: String 文件名（完整路径）
   * @return 返回文件写实例IFileWriter
   **/
  private def getFileWriter(fileType: String, fileName: String): IFileWriter = {
    try {
      fileType match {
        case "localFileSystem" =>
          new ImpFileWriter(fileName)
        case - =>
          new ImpFileWriter(fileName)
      }
    } catch {
      case e: Exception =>
        null
    }
  }
}
