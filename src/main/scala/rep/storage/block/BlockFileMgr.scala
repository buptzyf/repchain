package rep.storage.block

import java.nio.ByteBuffer
import rep.storage.cfg.StoreConfig4Scala
import rep.storage.util.pathUtil

class BlockFileMgr(val SystemName: String) {
  private var bw: BlockFileWriter = null
  private var br: BlockFileReader = new BlockFileReader(this.SystemName)

  private def checkFileWriter(fileno: Long) = {
    synchronized {
      val tmpblockDataPath = StoreConfig4Scala.getBlockPath(SystemName)
      val b = pathUtil.MkdirAll(tmpblockDataPath)
      if (this.bw == null) {
        this.bw = new BlockFileWriter(SystemName, fileno, false)
      } else {
        if (this.bw.fileIndex != fileno) {
          this.bw.FreeResouce
          this.bw = null
          this.bw = new BlockFileWriter(SystemName, fileno, false)
        }
      }
    }
  }

  /**
   * @author jiangbuyun
   * @version	1.0
   * @since	2019-05-11
   * @category	获取当前文件的长度
   * @param   fileno 文件编号
   * @return	返回当前编号的文件长度 long
   */
  def getFileLength(fileno: Long): Long = {
    this.checkFileWriter(fileno)
    this.bw.getFileLength
  }

  /**
   * @author jiangbuyun
   * @version	1.0
   * @since	2019-05-11
   * @category	判断是否需要增加新的区块文件
   * @param   fileno 文件编号，int blength 当前要写入数据的长度
   * @return	如果需要新增区块文件返回true，否则false
   */
  def isAddFile(fileno: Long, blength: Int): Boolean = {
    this.checkFileWriter(fileno)
    this.bw.isAddFile(blength)
  }

  /**
   * @author jiangbuyun
   * @version	1.0
   * @since	2019-05-11
   * @category	内部函数，写区块字节数组到指定文件的指定位置
   * @param	fileno Long 文件编号,startpos Long 区块信息存储的起始位置,bb byte[] 区块字节数组
   * @return	如果写入成功返回true，否则false
   */
  def writeBlock(fileno: Long, startpos: Long, bb: Array[Byte]): Boolean = {
    this.checkFileWriter(fileno)
    this.bw.writeBlock(startpos, bb)
  }

  /**
   * @author jiangbuyun
   * @version	1.0
   * @since	2017-09-28
   * @category	从文件尾部删除指定长度的字节
   * @param	fileno Long 文件编号,startpos Long 区块信息存储的起始位置,bb byte[] 区块字节数组
   * @return	如果写入成功返回true，否则false
   */
  def deleteBlockBytesFromFileTail(fileno: Long, delLength: Long): Boolean = {
    this.checkFileWriter(fileno)
    this.bw.deleteBlockBytesFromFileTail(delLength)
  }

  /**
   * @author jiangbuyun
   * @version	1.0
   * @since	2019-05-11
   * @category	内部函数，读区块文件中的指定区块信息
   * @param	fileno Long 文件编号,startpos Long 区块信息存储的起始位置,length Int 读取数据的长度
   * @return	返回读取的区块字节数组
   */
  def readBlock(fileno: Long, startpos: Long, length: Int): Array[Byte] = {
    //var reader = new BlockFileReader(this.SystemName)
    br.readBlock(fileno, startpos, length)
  }
  
  def longToByte(number:Long):Array[Byte]={
    var buffer = ByteBuffer.allocate(8)
    buffer.putLong(0, number)
    buffer.array()
  }
  
  def byteToLong(b:Array[Byte]):Long={
    var buffer = ByteBuffer.allocate(8)
    buffer.put(b, 0, b.length) 
    buffer.flip()
    buffer.getLong()
  }
  
}
