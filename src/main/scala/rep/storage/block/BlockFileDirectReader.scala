package rep.storage.block

import java.io.{File, RandomAccessFile}
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import rep.protos.peer.Block

class BlockFileDirectReader(filePath:String) {
  private var rf: RandomAccessFile = null;
  private var channel: FileChannel = null;


  private def createChannel={
    synchronized {
      this.FreeResouce
      try {
        var f = new File(this.filePath)
        if (f.exists()) {
          rf = new RandomAccessFile(this.filePath, "r");
          channel = rf.getChannel();
        }
      } catch {
        case e: Exception => throw e
      }
    }
  }

  def IterationBlock(startPosition:Long=0):(Block,Long)={
    var currentPos = startPosition
    try {
        val bb = this.readBlock(startPosition)
        if (bb != null) {
          currentPos += 8 + bb.length
          val tmpBlock = Block.parseFrom(bb)
          if (tmpBlock != null) {
            (tmpBlock,currentPos)
          } else {
            (null,currentPos)
          }
        }else{
          (null,currentPos)
        }
    } catch {
      case e: Exception =>
        e.printStackTrace()
        throw e
    }
  }

  def readBlockWithHeight(height:Long): Block = {
    var block: Block = null

      try {
        var loopFlag = true
        var start = 0l
        while(loopFlag) {
          val bb = this.readBlock(start)
          if (bb != null) {
            start += 8 + bb.length
            val tmpBlock = Block.parseFrom(bb)
            if (tmpBlock != null) {
              if (tmpBlock.height == height) {
                block = tmpBlock
                loopFlag = false
              }
              /*else{
                System.out.println("read block,block height="+tmpBlock.height)
              }*/
            } else {
              loopFlag = false
            }
          }else{
            loopFlag = false
          }
        }
      } catch {
        case e: Exception =>
          e.printStackTrace()
          throw e
      }finally {
        this.FreeResouce
      }
    block
  }

  private def readBlock(startpos: Long): Array[Byte] = {
    var rb: Array[Byte] = null

      try {
        if(this.channel == null){
          this.createChannel
        }
        if((startpos+8) < channel.size()){
          channel.position(startpos)
          val length = getBlockLength
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
    rb
  }

  private def getBlockLength:Int={
    val buffer = ByteBuffer.allocate(8)
    this.channel.read(buffer)
    buffer.flip()
    buffer.getLong().toInt
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
  }

}
