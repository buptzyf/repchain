package rep.storage.test

import rep.app.conf.RepChainConfig
import rep.app.system.RepChainSystemContext
import rep.network.consensus.util.BlockHelp
import rep.proto.rc2.Block
import rep.storage.chain.block.BlockIndex
import rep.storage.filesystem.common.{IFileReader, IFileWriter}
import rep.storage.filesystem.factory.FileFactory
import rep.storage.util.pathUtil

object TestOfFileAccess extends App {
  val systemName = "215159697776981712.node1"
  //val config = new RepChainConfig(systemName)
  val ctx = new RepChainSystemContext(systemName,null)
  var testCount = 0
  var errorCount = 0
  var rightCount = 0

  test

  def createBlock:Array[Block]={
    var bs = new Array[Block](10)
    for(h<-1 to 10){
      var b = BlockHelp.buildBlock("",h,Seq.empty)
      b = BlockHelp.AddBlockHeaderHash(b,ctx.getHashTool)
      bs(h-1)= b
    }

    bs
  }

  def test:Unit={
    val writer = FileFactory.getWriter(ctx.getConfig,0)
    val reader = FileFactory.getReader(ctx.getConfig,0)

    val bs = createBlock
    val bsIndex = new Array[BlockIndex](10)

    for(h<-1 to 10){
      bsIndex(h-1) = writeBlock(bs(h-1),writer)
    }

    for(h<-1 to 10){
      testCount += 1
      val idx = bsIndex(h-1)
      val block = readBlock(idx,reader)
      if(block.getHeader.height == bs(h-1).getHeader.height){
        rightCount += 1
      }else{
        errorCount += 1
      }
    }
    printTestResult
  }

  def printTestResult:Unit={
    System.out.println(s"test result:testCount=${testCount},errorCount=${errorCount},rightCount=${rightCount}")
  }


  def writeBlock(b:Block,writer:IFileWriter):BlockIndex={
    val idx = new BlockIndex(b)
    idx.setFileNo(0)
    idx.setFilePos(writer.getFileLength+8)
    val bb = b.toByteArray
    val bLength = bb.length
    idx.setLength(bb.length)
    writer.writeData(idx.getFilePos - 8, pathUtil.longToByte(bLength) ++ bb)
    idx
  }

  def readBlock(idx:BlockIndex,reader:IFileReader):Block={
    val bData = reader.readData(idx.getFilePos,idx.getLength)
    Block.parseFrom(bData)
  }

}
