package rep.authority.cache.opcache

import java.util.concurrent.ConcurrentHashMap

import rep.authority.check.PermissionKeyPrefix
import rep.log.RepLogger
import rep.utils.SerializeUtils

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
 * Created by jiangbuyun on 2020/06/24.
 * 实现操作缓存
 */


object IOperateCache{
  case class opData(opId:String,opValid:Boolean,isOpen:Boolean,register:String)
}

abstract class IOperateCache(sysTag:String) {
  import rep.authority.cache.opcache.IOperateCache.opData
  protected implicit var op_map_cache = new ConcurrentHashMap[String, Option[opData]] asScala
  protected implicit var op_read_map_cache = new ConcurrentHashMap[String, Future[Option[opData]]] asScala

  protected def getDataFromStorage(key:String):Array[Byte]

  def ChangeValue(key:String)={
    val idx = key.lastIndexOf("_")
    if(idx > 0){
      val id = key.substring(idx+1)
      if(this.op_map_cache.contains(id)){
        this.op_map_cache.remove(id)
      }
    }
  }

  private def AsyncHandle(opid:String):Future[Option[opData]]=Future{
    var r : Option[opData] = None

    try {
      val bb = getDataFromStorage(PermissionKeyPrefix.opPrefix+opid)
      if(bb != null){
        val op = ValueToOperate(bb)
        if(op != null){
          RepLogger.Permission_Logger.trace(s"IOperateCache.AsynHandle get op object,key=${opid}")
          val od = operateToOpData(op)
          if(od != null) r = Some(od)
        }
      }
    }catch {
      case e : Exception =>
        RepLogger.Permission_Logger.error(s"IOperateCache.AsynHandle get op error" +
          s", info=${e.getMessage},key=${opid}")
    }
    r
  }

  private def ValueToOperate(value:Array[Byte]):Operate={
    var op : Operate = null

    try{
      if(value != null)
        op = SerializeUtils.deserialise(value).asInstanceOf[Operate]
    }catch {
      case e: Exception => {
        //todo add exception to log file
        RepLogger.Permission_Logger.error(s"IOperateCache.ValueToOperate error,info=${e.getMessage}")
      }
    }
    op
  }

  private def operateToOpData(op:Operate):opData = {
    var od : opData = null
    if(op != null) {
      if (op.operateType == OperateType.OPERATE_CONTRACT) {
        od = new opData(op.opId, op.opValid, op.isPublish,op.register)
      } else if (op.operateType == OperateType.OPERATE_SERVICE) {
        if (!op.operateServiceName.isEmpty) {
          od = new opData(op.opId,  op.opValid, op.isPublish,op.register)
        }
      }
    }
    od
  }

  def FindOp4OpName(opid:String):opData={
    var od : opData = null

    //检查数据缓存是否包含了opid，如果包含直接返回给调用者
    if(this.op_map_cache.contains(opid)){
      od = this.op_map_cache.get(opid).get.getOrElse(null)
    }else{
      //数据缓存没有包含opid，说明数据未加载到缓存，需要异步计算
      var tmpOperater : Future[Option[opData]] = this.op_read_map_cache.get(opid).getOrElse(null)
      if(tmpOperater == null){
        tmpOperater = AsyncHandle(opid)
        this.op_read_map_cache.putIfAbsent(opid,tmpOperater)
      }
      val result = Await.result(tmpOperater, 30.seconds).asInstanceOf[Option[opData]]
      //将结果放到数据缓存，结果有可能是None
      od = result.getOrElse(null)
      this.op_map_cache.put(opid,result)
      this.op_read_map_cache.remove(opid)
    }

    od
  }

}
