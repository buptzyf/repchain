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
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

class BlockFileReader(val SystemName:String) {
  private val FileName = "Repchain_BlockFile_"
  private val BlockDataPath = StoreConfig4Scala.getBlockPath(SystemName)
  private var rf: RandomAccessFile = null;
  private var channel: FileChannel = null; 
  private var fileindex:Long = 0

  
  private def openChannel(fileno:Long)={
      if(rf == null || (rf != null && fileindex != fileno)){
        createChannel(fileno)
      }
  }
  
  private def createChannel(fileno:Long)={
    synchronized {
      this.FreeResouce
      val fn4path = this.BlockDataPath + File.separator + FileName + fileno;
      try {
        var f = new File(fn4path)
        if (f.exists()) {
          rf = new RandomAccessFile(fn4path, "r");
          channel = rf.getChannel();
          fileindex = fileno
        }
      } catch {
        case e: Exception => throw e
      }
    }
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
    var rb: Array[Byte] = null
   
    synchronized {
      
      try {
        openChannel(fileno)
        if(channel != null){
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
      }
    }

    rb
  }
  
  def FreeResouce = {
    if (channel != null) {
      try {
        channel.close();
        channel = null
      } catch {
        case e: Exception =>
          channel = null
          e.printStackTrace()
      }
    }

    if (rf != null) {
      try {
        rf.close();
        rf = null
      } catch {
        case e: Exception =>
          rf = null
          e.printStackTrace();
      }
    }
    this.fileindex = 0
  }
}