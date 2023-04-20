package rep.storage.chain.block

import com.fasterxml.jackson.core.Base64Variants
import com.google.protobuf.ByteString
import rep.api.rest.RestActor.BlockTime
import rep.app.system.RepChainSystemContext
import rep.log.RepLogger
import rep.proto.rc2.{Block, BlockchainInfo, Transaction}
import rep.storage.chain.KeyPrefixManager
import rep.storage.db.common.IDBAccess
import rep.storage.db.factory.DBFactory
import rep.storage.encrypt.{EncryptFactory, IEncrypt}
import rep.storage.filesystem.factory.FileFactory
import rep.utils.SerializeUtils

import scala.collection.mutable.HashMap
import scala.util.control.Breaks.{break, breakable}

/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-13
 * @category	区块查询器，定义所有可以查询方法。
 * */
class BlockSearcher(ctx:RepChainSystemContext) {
  protected val db : IDBAccess = DBFactory.getDBAccess(ctx.getConfig)
  val isEncrypt:Boolean = ctx.getConfig.isEncryptedStorage
  private val cipherTool: IEncrypt = if(isEncrypt)EncryptFactory.getEncrypt(ctx.getConfig.isUseGM,
    ctx.getConfig.getEncryptedKey, ctx.getConfig.getKeyServer) else null
  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	提供自定义的key的数据查询，按照对象方式获取，预执行类可以重载该方法，不允许外部直接访问
   * @param	key String 键
   * @return 返回Option[Any]任意对象
   * */
  protected def getObjectForClass[T](key:String):Option[T]={
    this.db.getObject[T](key)
  }

  protected def getBytes(key:String):Array[Byte]={
    this.db.getBytes(key)
  }

  //获取建立该查询器的系统名称
  def getSystemName:String={
    this.ctx.getSystemName
  }

  //获取该查询器是否需要加解密
  def getIsEncrypt:Boolean={
    this.isEncrypt
  }
  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-12
   * @category	根据交易id检查交易是否存在
   * @param	tid String 交易的id
   * @return	返回true表示存在；如果没有找到，返回false
   */
  def isExistTransactionByTxId(tid:String):Boolean={
    this.getBlockHeightByTxId(tid) match {
      case None => false
      case _ => true
    }
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	获取指定高度的区块包含交易的数量
   * @param	h Long 区块高度
   * @return 返回区块内的交易数量，不存在该区块返回0
   * */
  def getNumberOfTransInBlockByHeight(h: Long): Int = {
    val idx = this.getBlockIndexByHeight(Some(h))
    idx match {
      case None => 0
      case _ => idx.get.getTransactionSize
    }
  }

  /////获取链信息///////
  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	获取链信息
   * @param
   * @return 返回链信息
   * */
  def getChainInfo:BlockchainInfo={
    val lastChainInfo = this.getLastChainInfo
    BlockchainInfo(lastChainInfo.height,lastChainInfo.txCount,ByteString.copyFromUtf8(lastChainInfo.bHash),
      ByteString.copyFromUtf8(lastChainInfo.previousHash),ByteString.EMPTY)
  }

  def getLastChainInfo:KeyPrefixManager.ChainInfo={
    val obj = this.getObjectForClass[KeyPrefixManager.ChainInfo](KeyPrefixManager.getBlockInfoKey(ctx.getConfig))
    obj match {
      case None => KeyPrefixManager.ChainInfo(0,"","",0,0,0,0)
      case _ => obj.get.asInstanceOf[KeyPrefixManager.ChainInfo]
    }
  }
  //////////////////////////////////////////////////////

  /////通过区块高度、区块hash、交易获取区块对象，///////
  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据高度获取区块数据
   * @param h:Long 区块高度
   * @return 返回Option[Block]区块数据，否则返回None
   * */
  def getBlockByHeight(h:Long) : Option[Block] = {
    this.readBlockData(this.getBlockIndexByHeight(Some(h)))
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据Hash获取区块数据
   * @param bh: String 区块Hash
   * @return 返回Option[Block]区块数据，否则返回None
   * */
  def getBlockByHash(bh: String): Option[Block] = {
    val h = this.getBlockHeightByHash(bh)
    if(h == None) None else this.getBlockByHeight(h.get)
  }


  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据交易id获取区块数据
   * @param tid:String 交易id
   * @return 返回Option[Block]区块数据，否则返回None
   * */
  def getBlockByTxid(tid:String):Option[Block] = {
    val h = this.getBlockHeightByTxId(tid)
    if(h == None) None else this.getBlockByHeight(h.get)
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-12
   * @category	根据block的base64 hash值获取block
   * @param	base64 String block的hash64值
   * @return	返回block对象，如果没有找到，返回None
   */
  def getBlockByBase64Hash(base64: String): Option[Block] = {
    base64 match {
      case null => None
      case _ => this.getBlockByHash(new String(Base64Variants.getDefaultVariant.decode(base64)))
    }
  }
    //////////////////////////////////////////////////////

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-12
   * @category	从某个高度开始（包含这个高度）的所有块取出来，h：开始的高度，h 必须大于0，高度是从1开始
   * limits 返回块的数量，默认值为0，如果大于0，返回指定数量的块，如果块数小于该值返回实际数量
   * 默认最多1000条
   * @param	h Int block的高度值，limits 返回记录的条数
   * @return	返回指定长度的Block数组，如果没有找到，返回长度=0的数组
   */
  def getBlocksFromHeight(h: Int, limits: Int = 0): Array[Option[Block]] = {
    var rb = new scala.collection.mutable.ArrayBuffer[Option[Block]]()
    val limited = if(limits == 0) 1000 else limits
    val lastHeight = this.getChainInfo.height.toInt
    if(h > 0 && h <= lastHeight){
      var count = 1
      breakable(
        for(lh <- h to lastHeight){
          if(count <= limited){
            rb += this.getBlockByHeight(lh)
            count += 1
          }else{
            break
          }
        }
      )
    }
    rb.toArray
  }
  //////////////////////////////////////////////////////

  //////////通过区块hash、交易id获取区块高度
  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据区块Hash获取区块高度
   * @param bh:String 区块Hash
   * @return 返回Option[Long]区块高度，否则返回None
  * */
  def getBlockHeightByHash(bh:String):Option[Long]={
    this.getObjectForClass[Long](KeyPrefixManager.getBlockHeightKey4Hash(ctx.getConfig,bh))
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据交易id获取区块高度
   * @param tid:String 交易ID
   * @return 返回Option[Long]区块高度，否则返回None
   * */
  def getBlockHeightByTxId(tid:String):Option[Long]={
    this.getObjectForClass[Long](KeyPrefixManager.getBlockHeightKey4TxId(ctx.getConfig,tid))
  }
  //////////////////////////////////////////////////////

  //////通过区块hash、区块高度、交易id获取区块建立时间
  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据区块Hash获取区块建立时间
   * @param bh:String 区块Hash
   * @return 返回Option[BlockTime]区块建立时间，否则返回None
   * */
  def getBlockCreateTimeByHash(bh:String):Option[BlockTime]={
    this.convertBlockTimeFromBlockIndex(this.getBlockIndexByHash(bh))
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据区块高度获取区块建立时间
   * @param h:Long 区块高度
   * @return 返回Option[BlockTime]区块建立时间，否则返回None
   * */
  def getBlockCreateTimeByHeight(h:Long):Option[BlockTime]={
    this.convertBlockTimeFromBlockIndex(this.getBlockIndexByHeight(Some(h)))
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据交易id获取区块建立时间
   * @param tid:String 交易id
   * @return 返回Option[BlockTime]区块建立时间，否则返回None
   * */
  def getBlockCreateTimeByTxId(tid:String):Option[BlockTime]={
    this.convertBlockTimeFromBlockIndex(this.getBlockIndexByTxId(tid))
  }


  private def convertBlockTimeFromBlockIndex(bIndex:Option[BlockIndex]):Option[BlockTime]={
    bIndex match {
      case None => None
      case _ =>
        val formatStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        formatStr.setTimeZone(java.util.TimeZone.getTimeZone("ETC/GMT-8"))
        val datestr = formatStr.format(new java.util.Date(bIndex.get.getCreateTime.get.seconds * 1000))
        val millis = bIndex.get.getCreateTime.get.nanos / 1000000
        Some(BlockTime(datestr + "." + millis, String.valueOf((bIndex.get.getCreateTime.get.seconds * 1000 + millis) - 8 * 3600 * 1000)))
    }
  }
  //////////////////////////////////////////////////////

  //////通过高度获取区块hash
  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据区块高度获取区块Hash
   * @param h:Long 区块高度
   * @return 返回String ，否则返回空字符串
   * */
  def getBlockHashByHeight(h:Long):String={
    val idx = this.getBlockIndexByHeight(Some(h))
    if(idx == None) "" else idx.get.getHash
  }
  //////////////////////////////////////////////////////

  //////通过交易id获取交易对象
  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据交易id获取交易数据
   * @param tid:String 交易id
   * @return 返回Option[Transaction]，否则返回None
   * */
  def getTransactionByTxId(tid:String):Option[Transaction]={
    val block = this.getBlockByTxid(tid)
    if(block == None) None else block.get.transactions.find(_.id == tid)
  }
  //////////////////////////////////////////////////////

  //////通过文件编号获取该文件第一个区块的高度
  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据区块文件的第一个区块的高度
   * @param fileNo:Int 文件号
   * @return 返回Option[Long]，否则返回None
   * */
  def getBlockHeightInFileFirstBlockByFileNo(fileNo:Int):Option[Long]={
    this.getObjectForClass[Long](KeyPrefixManager.getBlockFileFirstHeightKey(ctx.getConfig,fileNo))
  }
  //////////////////////////////////////////////////////

  //////通过区块hash、区块高度、交易id获取区块索引对象
  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据区块Hash获取区块索引
   * @param bh:String
   * @return 返回Option[BlockIndex]，否则返回None
   * */
  def getBlockIndexByHash(bh:String):Option[BlockIndex]={
    getBlockIndexByHeight(this.getBlockHeightByHash(bh))
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据交易id获取区块索引
   * @param tid:String 交易id
   * @return 返回Option[BlockIndex]，否则返回None
   * */
  def getBlockIndexByTxId(tid:String):Option[BlockIndex]={
    this.getBlockIndexByHeight(this.getBlockHeightByTxId(tid))
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据区块高度获取区块索引
   * @param h:Option[Long] 区块高度
   * @return 返回Option[BlockIndex]，否则返回None
   * */
  def getBlockIndexByHeight(h:Option[Long]):Option[BlockIndex]={
    h match {
      case None => None
      case _ =>
        val idx = this.getObjectForClass[BlockIndex](KeyPrefixManager.getBlockIndexKey4Height(ctx.getConfig,h.get))
        idx match {
          case None => None
          case _ => idx.asInstanceOf[Option[BlockIndex]]
        }
    }
  }
  //////////////////////////////////////////////////////
  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据区块索引读区块文件
   * @param bIndex: Option[BlockIndex]  区块索引
   * @return 返回Option[Block]，否则返回None
   * */
  private def readBlockData(bIndex: Option[BlockIndex]):Option[Block]={
    if(bIndex == None)
      None
    else{
      val reader = FileFactory.getReader(ctx.getConfig,bIndex.get.getFileNo)
      val bData = reader.readData(bIndex.get.getFilePos,bIndex.get.getLength)
      val data = if(isEncrypt) cipherTool.decrypt(bData) else bData
      Some(Block.parseFrom(data))
    }
  }
}