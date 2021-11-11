/*
 * Copyright  2019 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BA SIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package rep.storage.block

import rep.storage.cfg.StoreConfig4Scala
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.{ConcurrentLinkedQueue, ExecutorService, Executors};

class BlockFileWriter(val SystemName: String, val fileIndex: Long, val isPreAllocate: Boolean = false) {
  private val FileName = "Repchain_BlockFile_"
  private val filemaxlength = StoreConfig4Scala.getFileMax
  private val FlushLimitLength = 30 * 1024 * 1024
  private var lastFlushPos: Long = 0
  private val BlockDataPath = StoreConfig4Scala.getBlockPath(SystemName)
  private var rf: RandomAccessFile = null;
  private var channel: FileChannel = null;
  private var wt = new WriteBlockThread
  new Thread(wt).start()

  synchronized {
    val fn4path = this.BlockDataPath + File.separator + FileName + fileIndex;
    try {
      rf = new RandomAccessFile(fn4path, "rw");
      channel = rf.getChannel();
      this.lastFlushPos = channel.size()
    } catch {
      case e: Exception => throw e
    }
  }

  /**
   * @author jiangbuyun
   * @version	1.0
   * @since	2019-05-11
   * @category	获取当前文件的长度
   * @param
   * @return	返回当前编号的文件长度 long
   */
  def getFileLength: Long = {
    var l: Long = 0;
    try {
      l = this.channel.size()
    } catch {
      case e: Exception => throw e
    }
    l
  }

  /**
   * @author jiangbuyun
   * @version	1.0
   * @since	2019-05-11
   * @category	判断是否需要增加新的区块文件
   * @param
   * @return	如果需要新增区块文件返回true，否则false
   */
  def isAddFile(blength: Int): Boolean = {
    (this.getFileLength + blength) > this.filemaxlength
  }

  class WriteBlockThread extends Runnable{
    private var blocks : ConcurrentLinkedQueue[(FileChannel,Long,Array[Byte])] = new ConcurrentLinkedQueue[(FileChannel,Long,Array[Byte])]

    def addBlockToQueue(fileChannel:FileChannel,startpos: Long, bb: Array[Byte]): Unit ={
      this.blocks.add(fileChannel,startpos,bb)
    }

    private def writeBlock(fileChannel:FileChannel,startpos: Long, bb: Array[Byte]): Unit = {
        try {
          fileChannel.position(startpos);
          val buf: ByteBuffer = ByteBuffer.wrap(bb);
          fileChannel.write(buf);

        } catch {
          case e: Exception =>
            e.printStackTrace()
            throw e
        }
    }

    override def run(){
      while(true){
        try{
          if(!this.blocks.isEmpty){
            val e = this.blocks.poll()
            if(e != null){
              this.writeBlock(e._1,e._2,e._3)
            }
          }else{
            Thread.sleep(100)
          }
        }catch{
          case e:Exception=>e.printStackTrace()
        }
      }
    }
  }

  /**
   * @author jiangbuyun
   * @version	1.0
   * @since	2019-05-11
   * @category	内部函数，写区块字节数组到指定文件的指定位置
   * @param	,startpos Long 区块信息存储的起始位置,bb byte[] 区块字节数组
   * @return	如果写入成功返回true，否则false
   */
  /*def writeBlock(startpos: Long, bb: Array[Byte]): Boolean = {
    var b = false
    synchronized {
      try {
        channel.position(startpos);
        var buf: ByteBuffer = ByteBuffer.wrap(bb);
        channel.write(buf);
        /*if (channel.size() - this.lastFlushPos > this.FlushLimitLength) {
          channel.force(true)
          this.lastFlushPos = channel.size()
        }*/
        b = true
      } catch {
        case e: Exception =>
          e.printStackTrace()
          throw e
      }
    }
    b
  }*/

  def writeBlock(startpos: Long, bb: Array[Byte]): Boolean = {
    var b = false
    synchronized {
      try {
        this.wt.addBlockToQueue(this.channel,startpos,bb)
        b = true
      } catch {
        case e: Exception =>
          e.printStackTrace()
          throw e
      }
    }
    b
  }

  /**
   * @author jiangbuyun
   * @version	1.0
   * @since	2017-09-28
   * @category	从文件尾部删除指定长度的字节
   * @param
   * @return	如果写入成功返回true，否则false
   */
  def deleteBlockBytesFromFileTail(delLength: Long): Boolean = {
    var b = false;
    synchronized {
      try {
        val len = channel.size() - delLength;
        channel.truncate(len);
        this.lastFlushPos = channel.size()
        b = true;
      } catch {
        case e: Exception =>
          e.printStackTrace()
          throw e
      }
    }

    b
  }

  def FreeResouce = {
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
          e.printStackTrace();
      }
    }
  }

}
