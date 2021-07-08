package rep.storage

import rep.log.RepLogger


/**
 * @author jiangbuyun
 * @version	1.1
 * @since	2021-07-20
 * @category	按照交易操作worldState，可以方便进行基于交易的事务操作
 * */
class TransactionOfDataPreload(txid:String,dbop:ImpDataPreload) {
  private var update :java.util.concurrent.ConcurrentHashMap[String,Array[Byte]] = new java.util.concurrent.ConcurrentHashMap[String,Array[Byte]]
  //private var isStartWrite:Boolean = false

  /**
   * @author jiangbuyun
   * @version	1.1
   * @since	2021-07-08
   * @category	获取指定的键值
   * @param	key String 指定的键
   * @return	返回对应键的值 Array[Byte]
   * */
  def Get(key : String):Array[Byte]={
    var rb : Array[Byte] = null
    try{
      //if(this.isStartWrite) throw new Exception("Can't read after starting to write ")
      if(this.update.contains(key)){
        rb = this.update.get(key)
      }else{
        rb = this.dbop.Get(key)
      }
      this.dbop.setUseTime
    }catch{
      case e:Exception =>{
        rb = null
        RepLogger.error(RepLogger.Storager_Logger,
          "TransactionOfDataPreload_" + this.txid + "_" + " Get failed, error info= "+e.getMessage)
        throw e
      }
    }
    rb
  }

  /**
   * @author jiangbuyun
   * @version	1.1
   * @since	2021-07-20
   * @category	存储指定的键和值到数据库
   * @param	key String 指定的键，bb Array[Byte] 要存储的值
   * @return	返回成功或者失败 Boolean
   * */
  def Put (key : String,bb : Array[Byte]):Boolean={
    var b : Boolean = true
    try{
      //if(!this.isStartWrite) this.isStartWrite = true
      if(key == null){
        RepLogger.trace(RepLogger.Storager_Logger,
          "TransactionOfDataPreload_" + this.dbop.getSystemName + "_" + "Put failed, error info= key is null")
      }
      var v :Array[Byte] = bb
      if(bb == null){
        v = None.toArray
        RepLogger.trace(RepLogger.Storager_Logger,
          "TransactionOfDataPreload_" + this.dbop.getSystemName + "_" + "ImpDataPreload Put failed, error info= value is null")
      }
      if(key != null ){
        this.update.put(key, v)
      }
      this.dbop.setUseTime
    }catch{
      case e:Exception =>{
        b = false
        RepLogger.error(RepLogger.Storager_Logger,
          "TransactionOfDataPreload_" + this.dbop.getSystemName + "_" + "Put failed, error info= "+e.getMessage)
        throw e
      }
    }
    b
  }

  /**
   * @author jiangbuyun
   * @version	1.1
   * @since	2021-07-20
   * @category	将写入的数据提交到块数据缓存
   * */
  def commit:Unit={
    try{
      this.update.keySet().forEach(key=>{
        this.dbop.Put(key,this.update.get(key))
      })
    }catch {
      case e:Exception=>
        RepLogger.error(RepLogger.Storager_Logger,
          "TransactionOfDataPreload_" + this.dbop.getSystemName + "_" + "commit failed, error info= "+e.getMessage)
        throw e
    }
  }

  /**
   * @author jiangbuyun
   * @version	1.1
   * @since	2021-07-20
   * @category	将写入到数据清除，不再提交到块缓存
   * */
  def roolback:Unit={
    try{
      this.update.clear()
    }catch{
      case e:Exception=>
        RepLogger.error(RepLogger.Storager_Logger,
          "TransactionOfDataPreload_" + this.dbop.getSystemName + "_" + "roolback failed, error info= "+e.getMessage)
        throw e
    }
  }
}
