package rep.storage.chain.preload


import java.util.concurrent.ConcurrentHashMap
import rep.log.RepLogger

/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-13
 * @category	交易预执行。
 * */
class TransactionPreload(txId:String,blockPreload: BlockPreload) {
  private val update :ConcurrentHashMap[String,Array[Byte]] = new ConcurrentHashMap[String,Array[Byte]]

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	获取区块预执行
   * @param
   * @return 返回BlockPreload区块预执行实例
   * */
  def getBlockPreload:BlockPreload={
    this.blockPreload
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	获取指定的键值
   * @param	key String 指定的键
   * @return	返回对应键的值 Option[Any]
   * */
  def get(key : String):Array[Byte]={
    var ro : Array[Byte] = null
    try{
      if(this.update.contains(key)){
        ro = this.update.get(key)
      }else{
        ro = this.blockPreload.get(key)
      }
    }catch{
      case e:Exception =>{
        RepLogger.error(RepLogger.Storager_Logger,
          s"TransactionPreload get failed,txid=${this.txId},systemName=${this.blockPreload.getSystemName}, msg=${e.getCause} ")
        throw e
      }
    }
    ro
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	存储指定的键和值到数据库
   * @param	key String 指定的键，any Any 要存储的值
   * @return	返回成功或者失败 Boolean
   * */
  def put (key : String,value : Array[Byte]):Boolean={
    var b : Boolean = false
    try{
      key match{
        case null =>
          RepLogger.error(RepLogger.Storager_Logger,
            s"TransactionPreload put data Except, txid=${this.txId},systemName=${this.blockPreload.getSystemName},msg=key is null")
        case _ =>
          if(value == null) throw new Exception("value is null")
          this.update.put(key,value)
          b = true
      }
    }catch{
      case e:Exception =>{
        RepLogger.error(RepLogger.Storager_Logger,
          s"TransactionPreload put data Except, txid=${this.txId},systemName=${this.blockPreload.getSystemName},msg=${e.getCause}")
        throw e
      }
    }
    b
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	将写入的数据提交到区块预执行缓存
   * */
  def commit:Unit={
    try{
      this.update.keySet().forEach(key=>{
        this.blockPreload.put(key,this.update.get(key))
      })
    }catch {
      case e:Exception=>
        RepLogger.error(RepLogger.Storager_Logger,
          s"TransactionPreload commit failed except,txid=${this.txId},systemName=${this.blockPreload.getSystemName},msg=${e.getCause} ")
        throw e
    }
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-13
   * @category	将写入的数据清除，不再提交到区块预执行缓存
   * */
  def rollback:Unit={
    try{
      this.update.clear()
    }catch{
      case e:Exception=>
        RepLogger.error(RepLogger.Storager_Logger,
          s"TransactionPreload rollback failed except,txid=${this.txId},systemName=${this.blockPreload.getSystemName},msg=${e.getCause}")
        throw e
    }
  }

}
