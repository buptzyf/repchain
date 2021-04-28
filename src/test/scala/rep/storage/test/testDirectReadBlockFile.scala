package rep.storage.test

import rep.storage.block.BlockFileDirectReader
import rep.utils.MessageToJson

object testDirectReadBlockFile extends App{
  val reader = new BlockFileDirectReader("/Users/jiangbuyun/Repchain_BlockFile_50.log")
  val block = reader.readBlockWithHeight(10192214)
  try{
    MessageToJson.toJson(block)
  }catch {
    case e:Exception=>
      e.printStackTrace()
  }
  System.out.println(block)

  //readBlockFile

  def readBlockFile()={
    var result = reader.IterationBlock(0)
    while(result._1 != null){
      System.out.println("read block data,block height="+result._1.height)
      result = reader.IterationBlock(result._2)
    }
    reader.FreeResouce
  }
}
