package rep.storage.filesystem.factory

import java.io.File

import com.googlecode.concurrentlinkedhashmap.{ConcurrentLinkedHashMap, EvictionListener, Weighers}
import rep.app.conf.RepChainConfig
import rep.log.RepLogger
import rep.storage.filesystem.FileOperate
import rep.storage.filesystem.common.{IFile, IFileReader, IFileWriter}
import rep.storage.filesystem.localfile.{ImpFileReader, ImpFileWriter}
import rep.storage.util.pathUtil

/**
 * @author jiangbuyun
 * @version 2.0
 * @since 2022-04-10
 * @category 构造文件访问实例工厂
 **/
object FileFactory {
  private val cacheSize = 100 //缓存文件读写器，默认30个
  private val fileNamePrefix = "Repchain_BlockFile_"

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
      value.FreeResource
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
      value.FreeResource
      RepLogger.info(RepLogger.Storager_Logger,s"WriteCache remove writer,key=${key}")
    }
  }

  private val reads = new ConcurrentLinkedHashMap.Builder[String, IFileReader]().maximumWeightedCapacity(cacheSize).listener(listenerOfReader).weigher(Weighers.singleton[IFileReader]).build
  private val writers = new ConcurrentLinkedHashMap.Builder[String, IFileWriter]().maximumWeightedCapacity(cacheSize).listener(listenerOfWriter).weigher(Weighers.singleton[IFileWriter]).build


  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-04-13
   * @category 根据系统名称和文件号建立文件读取实例
   * @param config: RepChainConfig, fileNo: Int 文件号
   * @return 返回文件读取实例IFileReader
   **/
  def getReader(config: RepChainConfig, fileNo: Int): IFileReader = {
    var instance: IFileReader = null
    synchronized {
      val filePath = FileOperate.mergeFilePath(Array[String](
        config.getStorageBlockFilePath, config.getStorageBlockFileName))
      FileOperate.MkdirAll(filePath)
      val fileName = filePath + fileNamePrefix + fileNo.toString
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

  def checkFreeDiskSpace(config: RepChainConfig):Boolean={
    var r = true
    val fileType = config.getStorageBlockFileType
    if(fileType == "localFileSystem"){
      val filePath = FileOperate.mergeFilePath(Array[String](
        config.getStorageBlockFilePath, config.getStorageBlockFileName))
      try {
        if(pathUtil.FileExists(filePath) == -1){
          pathUtil.MkdirAll(filePath)
        }
      } catch{
        case e:Exception => e.printStackTrace()
      }
      val f = new File(filePath)

      if (config.getMinDiskSpaceAlarm > (f.getFreeSpace() / (1000 * 1000))) {
        r = false
      }
    }
    r
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-04-13
   * @category 根据系统名称和文件号建立文件写实例
   * @param config: RepChainConfig, fileNo: Int 文件号
   * @return 返回文件写实例IFileWriter
   **/
  def getWriter(config: RepChainConfig, fileNo: Int): IFileWriter = {
    var instance: IFileWriter = null
    synchronized {
      val filePath = FileOperate.mergeFilePath(Array[String](
        config.getStorageBlockFilePath, config.getStorageBlockFileName))
      FileOperate.MkdirAll(filePath)
      val fileName = filePath + fileNamePrefix + fileNo.toString
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
          try{
            new ImpFileReader(fileName)
          }catch {
            case e:Exception=> null
          }
        case _ =>
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
        case _ =>
          new ImpFileWriter(fileName)
      }
    } catch {
      case e: Exception =>
        null
    }
  }
}
