package rep.storage.chain.block

import rep.proto.rc2.{Block}
import scala.collection.mutable.ArrayBuffer

/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-13
 * @category	区块索引，存放在键值数据库中。
 * */
class BlockIndex {
  private var height:Long = 0
  private var hash : String = ""
  private var preHash: String = ""

  private var txIds : Array[String] = null

  private var createTime: Option[com.google.protobuf.timestamp.Timestamp] = None

  private var fileNo: Int = 0
  private var filePos: Long = 0
  private var length: Int = 0

  def this(block:Block){
    this()
    initIndex(block)
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据区块数据建立实例
   * @param	block Block 指定的区块
   * @return
   * */
  private def initIndex(block:Block): Unit ={
    val header = block.getHeader
    this.height = header.height
    this.hash = header.hashPresent.toStringUtf8
    this.preHash = header.hashPrevious.toStringUtf8

    this.txIds = {
      val trs = block.transactions
      val txs = if(trs.isEmpty) null else new ArrayBuffer[String]
      if(txs != null){
        trs.foreach(t=>{
          txs.append(t.id)
        })
      }
      txs.toArray
    }

    this.createTime = if (header != null && header.endorsements.length >= 1) {
          header.endorsements(0).tmLocal
      }else{
        None
      }
  }

  //获取区块高度
  def getHeight:Long={
    this.height
  }

  //获取区块Hash
  def getHash:String={
    this.hash
  }

  //获取上一个区块Hash
  def getPreHash:String={
    this.preHash
  }

  //获取当前区块所有交易的id数组
  def getTxIds:Array[String]={
    this.txIds
  }

  //获取区块的建立时间
  def getCreateTime:Option[com.google.protobuf.timestamp.Timestamp] = {
    this.createTime
  }

  //获取当前区块存储的文件的文件序号
  def getFileNo:Int={
    this.fileNo
  }

  //获取当前区块在文件中的开始位置
  def getFilePos:Long={
    this.filePos
  }

  //获取当前区块转为字节数组之后的长度
  def getLength:Int={
    this.length
  }

  //设置当前区块存储的文件的序号
  def setFileNo(value:Int):Unit={
    this.fileNo = value
  }

  //设置当前区块在文件中存放的位置
  def setFilePos(value:Long):Unit={
    this.filePos = value
  }

  //设置当前区块转为字节数组的长度
  def setLength(value:Int):Unit={
    this.length = value
  }

  //获取当前区块所有交易的数量
  def getTransactionSize:Int={
    this.txIds.length
  }
}
