package rep.storage.filesystem.localfile

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import rep.log.RepLogger
import rep.storage.filesystem.common.IFileWriter


/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-10
 * @category	实现本地文件系统写
 * */
class ImpFileWriter(fileName:String,isEncrypt:Boolean=false) extends IFileWriter(fileName:String) {
  private var rf: RandomAccessFile = null;
  private var channel: FileChannel = null;
  private var lastAccessTime : Long = System.currentTimeMillis()

  createFileChannel

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-04-10
   * @category 建立文件写的句柄
   **/
  private def createFileChannel:Unit={
    try {
      this.FreeResource
      rf = new RandomAccessFile(this.fileName, "rw");
      channel = rf.getChannel();
      RepLogger.info(RepLogger.System_Logger,s"ImpFileWriter createFileChannel succuss,fileName=${this.fileName}")
    } catch {
      case e: Exception =>
        RepLogger.info(RepLogger.System_Logger,s"ImpFileWriter createFileChannel failed," +
          s"fileName=${this.fileName},msg=${e.getCause}")
        throw e
    }
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-04-07
   * @category 在指定位置写入数据
   * @param startpos 起始位置,bb 待写入的字节数组
   * @return 返回写入数据的长度
   **/
  override def writeData(startpos: Long, bb: Array[Byte]): Boolean = {
    var r : Boolean= false
    synchronized {
      try {
        this.lastAccessTime = System.currentTimeMillis()
        channel.position(startpos);
        val buf: ByteBuffer = ByteBuffer.wrap(bb);
        channel.write(buf);
        r = true
      } catch {
        case e: Exception =>
          RepLogger.info(RepLogger.System_Logger,s"ImpFileWriter writeBlock failed," +
            s"fileName=${this.fileName},msg=${e.getCause}")
          throw e
      }
    }
    r
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-04-10
   * @category 从文件尾删除指定长度的数据
   * @param delLength 要删除的数据长度
   * @return 成功返回ture，否则false
   **/
  override def deleteBytesFromFileTail(delLength: Long): Boolean = {
    var b = false;
    synchronized {
      try {
        val len = channel.size() - delLength;
        channel.truncate(len);
        b = true;
      } catch {
        case e: Exception =>
          RepLogger.info(RepLogger.System_Logger,s"ImpFileWriter deleteBytesFromFileTail failed," +
            s"fileName=${this.fileName},msg=${e.getCause}")
          throw e
      }
    }

    b
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-04-10
   * @category 获取最后写入数据的时间
   * @return 返回毫秒时间
   **/
  override def getLastAccessTime: Long = {this.lastAccessTime}

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-04-10
   * @category 获取文件名
   * @return 返回String文件名
   **/
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
      } catch {
        case e: Exception =>
          RepLogger.info(RepLogger.System_Logger,s"ImpFileWriter FreeResouce close channel failed," +
            s"fileName=${this.fileName},msg=${e.getCause}")
      }
    }

    if (rf != null) {
      try {
        rf.close();
      } catch {
        case e: Exception =>
          RepLogger.info(RepLogger.System_Logger,s"ImpFileWriter FreeResouce close RandomAccessFile failed," +
            s"fileName=${this.fileName},msg=${e.getCause}")
      }
    }
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-04-10
   * @category 获取文件长度
   * @return 返回Long文件长度
   **/
  override def getFileLength: Long = {
    this.channel.size()
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
