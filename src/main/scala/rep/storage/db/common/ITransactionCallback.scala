package rep.storage.db.common

/**
 * @author jiangbuyun
 * @version	2.0
 * @since	2022-04-07
 * @category	接口类，数据库事务的外部接口，在此接口实现事务相关的操作，数据库事务函数调用回调接口，执行事务。
 * */
trait ITransactionCallback {
  def callback:Boolean
}
