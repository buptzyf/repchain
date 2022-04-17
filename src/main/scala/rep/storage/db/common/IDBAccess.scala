package rep.storage.db.common



import java.util.Date
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
  private val synchLock : Object = new Object()
  protected val cipherTool = EncryptFactory.getEncrypt
  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	获取指定的键值
   * @param	key String 指定的键
   * @return	返回对应键的值 Array[Byte]
   * */
  def   getBytes(key : String):Option[Array[Byte]]

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	获取指定的键值
   * @param	key String 指定的键
   * @return	返回对应键的值 String
   * */
  def   getString(key : String):Option[String]={
    val d = this.getObject(key)
    if(d == None){
      None
    }else{
      if(d.get.isInstanceOf[String] ){
        Some(d.get.asInstanceOf[String])
      }else{
        None
      }
    }
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	获取指定的键值
   * @param	key String 指定的键
   * @return	返回对应键的值 Int
   * */
  def   getInt(key : String):Option[Int]={
    val d = this.getObject(key)
    if(d == None){
      None
    }else{
      if(d.get.isInstanceOf[Int] ){
        Some(d.get.asInstanceOf[Int])
      }else{
        None
      }
    }
  }


  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	获取指定的键值
   * @param	key String 指定的键
   * @return	返回对应键的值 Long
   * */
  def   getLong(key : String):Option[Long]={
    val d = this.getObject(key)
    if(d == None){
      None
    }else{
      if(d.get.isInstanceOf[Long] ){
        Some(d.get.asInstanceOf[Long])
      }else{
        None
      }
    }
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	获取指定的键值
   * @param	key String 指定的键
   * @return	返回对应键的值 Date
   * */
  def   getDate(key : String):Option[Date]={
    val d = this.getObject(key)
    if(d == None){
      None
    }else{
      if(d.get.isInstanceOf[Date] ){
        Some(d.get.asInstanceOf[Date])
      }else{
        None
      }
    }
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	获取指定的键值
   * @param	key String 指定的键
   * @return	返回对应键的值 Boolean
   * */
  def   getBoolean(key : String):Option[Boolean]={
    val d = this.getObject(key)
    if(d == None){
      None
    }else{
      if(d.get.isInstanceOf[Boolean] ){
        Some(d.get.asInstanceOf[Boolean])
      }else{
        None
      }
    }
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	获取指定的键值
   * @param	key String 指定的键
   * @return	返回对应键的值 Object
   * */
  def   getObject(key : String):Option[Any]={
    val b = this.getBytes(key)
    if(b == None){
      None
    }else{
      val o = SerializeUtils.deserialise(b.get)
      if(o == null){
        None
      }else{
        Some(o)
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
   * @category	存储指定的键和值到数据库
   * @param	key String 指定的键，o Any 要存储的对象
   * @return	返回成功或者失败 Boolean
   * */
  def   putObject (key : String,o : Any):Boolean={
    var b = false
    if(o != null){
      val bb = SerializeUtils.serialise(o)
      b = putBytes(key,bb)
    }
    b
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	存储指定的键和值到数据库
   * @param	key String 指定的键，str String 要存储的字符串
   * @return	返回成功或者失败 Boolean
   * */
  def   putString (key : String,str : String):Boolean={
    this.putObject(key,str)
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	存储指定的键和值到数据库
   * @param	key String 指定的键，l Long 要存储的长整型
   * @return	返回成功或者失败 Boolean
   * */
  def   putLong (key : String,l : Long):Boolean={
    this.putObject(key,l)
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	存储指定的键和值到数据库
   * @param	key String 指定的键，i Int 要存储的整型
   * @return	返回成功或者失败 Boolean
   * */
  def   putLong (key : String,i : Int):Boolean={
    this.putObject(key,i)
  }

  /**
   * @author jiangbuyun
   * @version	2.0
   * @since	2022-04-07
   * @category	存储指定的键和值到数据库
   * @param	key String 指定的键，i Int 要存储的整型
   * @return	返回成功或者失败 Boolean
   * */
  def   putDate (key : String,d : Date):Boolean={
    this.putObject(key,d)
  }

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
    synchLock.synchronized(
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