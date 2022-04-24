package rep.storage.db.common


import rep.log.RepLogger
import rep.storage.encrypt.EncryptFactory
import rep.utils.SerializeUtils

/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-07
 * @category	抽象类，描述任意数据库（目前主要使用键值数据库，默认实现LevelDB和RocksDB）访问接口。
 * */
abstract class IDBAccess {
  protected var isTransaction = false //是否开启事务
  private val lock : Object = new Object()
  protected val cipherTool = EncryptFactory.getEncrypt
  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	获取指定的键值
   * @param	key String 指定的键
   * @return	返回对应键的值 Array[Byte]
   * */
  def   getBytes(key : String):Array[Byte]

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	获取指定的键值
   * @param	key String 指定的键
   * @return	返回对应键的值 Object
   * */
  def   getObject[T](key : String):Option[T]={
    val b = this.getBytes(key)
    RepLogger.trace(RepLogger.Storager_Logger,s"DB operate getObject, key=${key}")
    if(b == null){
      RepLogger.trace(RepLogger.Storager_Logger,s"DB operate getObject,object==None, key=${key}")
      None
    }else{
      try{
        val o = SerializeUtils.deserialise(b)
        if(o == null){
          RepLogger.trace(RepLogger.Storager_Logger,s"DB operate getObject,serialize==None, key=${key}")
          None
        }else{
          RepLogger.trace(RepLogger.Storager_Logger,s"DB operate getObject,data!=None, key=${key},o=${o}")
          Some(o.asInstanceOf[T])
        }
      }catch {
        case e:Exception=>None
      }
    }
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	存储指定的键和值到数据库
   * @param	key String 指定的键，bb Array[Byte] 要存储的值
   * @return	返回成功或者失败 Boolean
   * */
  def   putBytes (key : String,bb : Array[Byte]):Boolean

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	删除指定的键值
   * @param	key String 指定的键
   * @return	返回成功或者失败 Boolean
   * */
  def   delete (key : String) : Boolean

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	提交事务
   * @return
   * */
  protected def commit

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	回滚事务
   * @return
   * */
  protected def rollback

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	开始事务
   * @return
   * */
  protected def beginTransaction

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	包装执行事务操作
   * @return	返回成功或者失败 Boolean
   * */
  def transactionOperate(callBack: ITransactionCallback):Boolean={
    var r = false
    lock.synchronized(
      if(callBack != null){
        this.isTransaction = true
        try{
          this.beginTransaction
          if(callBack.callback){
            this.commit
            r = true
          }
        }catch {
          case e:Exception=>{
            this.rollback
            RepLogger.error(RepLogger.Storager_Logger,  "IDBAccess error:transactionOperate error," +
              s"msg=${e.getCause}")
          }
        }finally {
          this.isTransaction = false
        }
      }
    )
    r
  }
}