package rep.storage.block

import rep.storage.cfg.StoreConfig4Scala
import java.io.File
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

class BlockFileReader(val SystemName:String) {
  private val FileName = "Repchain_BlockFile_"

  /**
   * @author jiangbuyun
   * @version	1.0
   * @since	2019-05-11
   * @category	内部函数，读区块文件中的指定区块信息
   * @param	fileno Long 文件编号,startpos Long 区块信息存储的起始位置,length Int 读取数据的长度
   * @return	返回读取的区块字节数组
   */
  def readBlock(fileno: Long, startpos: Long, length: Int): Array[Byte] = {
    var rb: Array[Byte] = null
    val np = StoreConfig4Scala.getBlockPath(SystemName) + File.separator + FileName + fileno
    synchronized {
      var rf: RandomAccessFile = null
      var channel: FileChannel = null
      try {
        var f = new File(np)
        if (!f.exists()) {
          rb
        } else {
          rf = new RandomAccessFile(np, "r")
          channel = rf.getChannel()
          channel.position(startpos)
          var buf: ByteBuffer = ByteBuffer.allocate(length)
          channel.read(buf)
          buf.flip()
          rb = buf.array()
        }
      } catch {
        case e: Exception =>
          e.printStackTrace()
          throw e
      } finally {
        if (channel != null) {
          try {
            channel.close();
          } catch {
            case e: Exception =>
              e.printStackTrace()
          }
        }

        if (rf != null) {
          try {
            rf.close();
          } catch {
            case e: Exception =>
              e.printStackTrace()
          }
        }
      }
    }

    rb
  }
}