package rep.storage.chain


import java.util.concurrent.ConcurrentHashMap
import rep.app.conf.RepChainConfig
import scala.collection.JavaConverters._

/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-13
 * @category	定义区块索引的关键字前缀，以及生成关键字方法
 * */
object KeyPrefixManager {
  //区块存储全局前缀定义
  case class ChainInfo(height:Long,bHash:String,previousHash:String,txCount:Long,maxFileNo:Int,filePos:Long,bLength:Int)
  private val repChainBlockInfoPrefix:String = "rbi" //repchain全局信息key，每个组网都有定义和存储
  private val blockFileFirstHeightPrefix:String = "rbfh"  //每个区块文件的第一个区块的高度前缀，每个组网都有定义和存储
  //每个区块索引内容包含高度、摘要、存储位置等，这个key对应value（index content），相当于通过高度获得区块等索引信息
  private val BlockIndexKeyByHeightPrefix:String = "ih"
  //通过Hash获取区块高度前缀
  private val BlockHeightKeyByHashPrefix:String = "hh"
  //通过交易id获取区块高度前缀
  private val BlockHeightKeyByTxIdPrefix:String = "ht"
  //通过cid获取部署交易前缀
  private val ContractDeployKeyByCIdPrefix:String = "c"

  private implicit var chainIds = new ConcurrentHashMap[String,String]() asScala

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据系统名获取链id
   * @param config:RepChainConfig
   * @return 返回String链id
   * */
  private def getChainId(config:RepChainConfig):String={
    config.getChainNetworkId
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据系统名称生成链相关信息关键字
   * @param config:RepChainConfig
   * @return 返回String链相关信息关键字
   * */
  def getBlockInfoKey(config:RepChainConfig):String={
    getChainId(config)+"_"+repChainBlockInfoPrefix
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据系统名称和区块文件号生成区块文件第一个块的关键字
   * @param config:RepChainConfig，fileNo:Int 文件号
   * @return 返回String区块文件第一个块的关键字
   * */
  def getBlockFileFirstHeightKey(config:RepChainConfig,fileNo:Int):String={
    getChainId(config)+"_"+blockFileFirstHeightPrefix+"_"+fileNo
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据系统名称和区块高度生成区块索引的关键字
   * @param config:RepChainConfig，height:Long 区块高度
   * @return 返回String区块索引的关键字
   * */
  def getBlockIndexKey4Height(config:RepChainConfig,height:Long):String={
    getChainId(config)+"_"+BlockIndexKeyByHeightPrefix+"_"+height
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据系统名称和区块Hash生成区块Hash与区块高度索引的关键字
   * @param config:RepChainConfig，hash:String 区块Hash
   * @return 返回String区块Hash与区块高度索引的关键字
   * */
  def getBlockHeightKey4Hash(config:RepChainConfig,hash:String):String={
    getChainId(config)+"_"+BlockHeightKeyByHashPrefix+"_"+hash
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据系统名称和交易id生成区块交易id与区块高度索引的关键字
   * @param config:RepChainConfig，txId:String 交易id
   * @return 返回String区块交易id与区块高度索引的关键字
   * */
  def getBlockHeightKey4TxId(config:RepChainConfig,txId:String):String={
    getChainId(config)+"_"+BlockHeightKeyByTxIdPrefix+"_"+txId
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	根据系统名称、worldstate、合约id、实例id一起生成worldstate完整关键字
   * @param config:RepChainConfig,key:String worldstate,contractId:String 合约id,oid:String="_" 实例id
   * @return 返回String生成worldstate完整关键字
   * */
  def getWorldStateKey(config:RepChainConfig,key:String,contractId:String,oid:String="_"):String={
    if(oid==null ||  oid.equalsIgnoreCase(""))
      getChainId(config)+"_"+contractId+"_"+"_"+"_"+key
    else
      getChainId(config)+"_"+contractId+"_"+oid+"_"+key
  }

  def getWorldStateKeyPrefix(config:RepChainConfig,contractId:String,oid:String="_"):String={
    if(oid==null ||  oid.equalsIgnoreCase(""))
      getChainId(config)+"_"+contractId+"_"+"_"
    else
      getChainId(config)+"_"+contractId+"_"+oid
  }

  /*def getContractStateKey(systemName:String,key:String):String={
    getChainId(systemName)+"_"+this.ContractDeployKeyByCIdPrefix+"_"+key
  }*/
}
