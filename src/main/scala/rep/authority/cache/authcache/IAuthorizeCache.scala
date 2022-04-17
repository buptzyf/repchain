package rep.authority.cache.authcache

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
 * 实现权限缓存
 */

object IAuthorizeCache{
  case class authData(authid:String,authorizeValid:Boolean,ops:List[String])
}

abstract class IAuthorizeCache(sysTag:String) {
  import rep.authority.cache.authcache.IAuthorizeCache.authData
  protected implicit var auth_map_cache = new ConcurrentHashMap[String, Option[authData]] asScala
  protected implicit var auth_read_map_cache = new ConcurrentHashMap[String, Future[Option[authData]]] asScala

  protected def getDataFromStorage(key:String):Array[Byte]

  def ChangeValue(key:String)={
    val idx = key.lastIndexOf("_")
    if(idx > 0){
      val id = key.substring(idx+1)
      if(this.auth_map_cache.contains(id)){
        this.auth_map_cache.remove(id)
      }
    }
  }

  private def AsyncHandle(authid:String):Future[Option[authData]]=Future{
    var r : Option[authData] = None

    try {
      val bb = getDataFromStorage(PermissionKeyPrefix.authPrefix+authid)
      if(bb != null){
        val auth = ValueToAuthorize(bb)
        if(auth != null){
          RepLogger.Permission_Logger.trace(s"IAuthorizeCache.AsynHandle get auth object,key=${authid}")
          val ad = authToAuthData(auth)
          if(ad != None) r = ad
        }
      }
    }catch {
      case e : Exception =>
        RepLogger.Permission_Logger.error(s"IAuthorizeCache.AsynHandle get op error" +
          s", info=${e.getMessage},key=${authid}")
    }
    r
  }

  def FindAuthorize(authid:String):authData={
    var od : authData = null

    //检查数据缓存是否包含了authid，如果包含直接返回给调用者
    if(this.auth_map_cache.contains(authid)){
      od = this.auth_map_cache.get(authid).get.getOrElse(null)
    }else{
      //数据缓存没有包含authid，说明数据未加载到缓存，需要异步计算
      var tmpOperater : Future[Option[authData]] = this.auth_read_map_cache.get(authid).getOrElse(null)
      if(tmpOperater == null){
        tmpOperater = AsyncHandle(authid)
        this.auth_read_map_cache.putIfAbsent(authid,tmpOperater)
      }
      val result = Await.result(tmpOperater, 30.seconds).asInstanceOf[Option[authData]]
      //将结果放到数据缓存，结果有可能是None
      od = result.getOrElse(null)
      this.auth_map_cache.put(authid,result)
      this.auth_read_map_cache.remove(authid)
    }

    od
  }

  protected def ValueToAuthorize(value:Array[Byte]):Authorize={
    var auth : Authorize = null

    try{
      if(value != null)
        auth = SerializeUtils.deserialise(value).asInstanceOf[Authorize]
    }catch {
      case e: Exception => {
        //todo add exception to log file
        RepLogger.Permission_Logger.error(s"IAuthorizeCache.ValueToAuthorize deserialise error,info=${e.getMessage}")
      }
    }
    auth
  }

  protected def authToAuthData(auth:Authorize):Option[authData] = {
    var ad : Option[authData] = None
    if(auth != null) {
      ad = Some(new authData(auth.id,auth.authorizeValid,auth.opId.toList))
    }
    ad
  }
}
