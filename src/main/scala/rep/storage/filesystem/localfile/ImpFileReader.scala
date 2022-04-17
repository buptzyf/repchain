package rep.storage.filesystem.localfile

import java.io.{File, RandomAccessFile}
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import rep.log.RepLogger
import rep.storage.filesystem.common.IFileReader


/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-10
 * @category	实现本地文件系统读
 * */
class ImpFileReader(fileName:String,isEncrypt:Boolean=false) extends IFileReader(fileName:String){
  private var rf: RandomAccessFile = null;
  private var channel: FileChannel = null;
  private var lastAccessTime : Long = System.currentTimeMillis()

  createFileChannel

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-04-10
   * @category 建立文件访问句柄
   **/
  private def createFileChannel:Unit={
    try {
      this.FreeResource
      var f = new File(this.fileName)
      if (f.exists()) {
        this.rf = new RandomAccessFile(this.fileName, "r");
        this.channel = rf.getChannel();
        RepLogger.info(RepLogger.System_Logger,s"ImpFileReader createFileChannel succuss,fileName=${this.fileName}")
      }
    } catch {
      case e: Exception =>
        RepLogger.info(RepLogger.System_Logger,s"ImpFileReader createFileChannel failed," +
          s"fileName=${this.fileName},msg=${e.getCause}")
        throw e
    }
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-04-10
   * @category 根据指定读位置和长度读文件
   * @param startpos 起始位置,length 读取数据长度
   * @return 返回读取字节数组Array[Byte]，否则返回null
   **/
  override def readData(startpos: Long, length: Int): Array[Byte] = {
    var rb: Array[Byte] = null

    synchronized {
      try {
        if(channel != null){
          this.lastAccessTime = System.currentTimeMillis()
          channel.position(startpos)
          val buf: ByteBuffer = ByteBuffer.allocate(length)
          channel.read(buf)
          buf.flip()
          rb = buf.array()
        }
      } catch {
        case e: Exception =>
          RepLogger.info(RepLogger.System_Logger,s"ImpFileReader readData failed," +
            s"fileName=${this.fileName},msg=${e.getCause}")
      }
    }

    rb
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-04-10
   * @category 获取最近访问该的时间
   * @return 返回毫秒时间
   **/
  override def getLastAccessTime: Long = {this.lastAccessTime}

  override def getFileName: String = {
    this.fileName
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-04-10
   * @category 释放文件句柄
   **/
  override def FreeResource: Unit = {
    if (channel != null) {
      try {
        channel.close();
        channel = null
      } catch {
        case e: Exception =>
          channel = null
          RepLogger.info(RepLogger.System_Logger,s"ImpFileReader FreeResouce close channel failed," +
            s"fileName=${this.fileName},msg=${e.getCause}")
      }
    }

    if (rf != null) {
      try {
        rf.close();
        rf = null
      } catch {
        case e: Exception =>
          rf = null
          RepLogger.info(RepLogger.System_Logger,s"ImpFileReader FreeResouce close RandomAccessFile failed," +
            s"fileName=${this.fileName},msg=${e.getCause}")
      }
    }
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-04-10
   * @category 获取文件号
   * @return 返回Int文件号
   **/
  override def getFileNo: Int = {
    this.fileName.substring(this.fileName.lastIndexOf("_")+1).toInt
  }
}
