package rep.authority.cache.authbind

import java.util.concurrent.ConcurrentHashMap

import rep.authority.check.PermissionKeyPrefix
import rep.log.RepLogger
import rep.protos.peer.BindCertToAuthorize
import rep.utils.SerializeUtils

import scala.collection.JavaConverters._

/**
 * Created by jiangbuyun on 2020/07/06.
 * 实现权限与证书绑定缓存
 */
abstract class IAuthBindToCert(sysTag:String) {
  protected implicit var bind_map_cache = new ConcurrentHashMap[String, Boolean] asScala

  protected def getDataFromStorage(key:String):Array[Byte]

  def ChangeValue(key:String)={
    val ak = key.split("_")
    if(ak != null && ak.length > 2){
      val id = ak(ak.length-2)+"_"+ak(ak.length-1)
      if(this.bind_map_cache.contains(id)){
        this.bind_map_cache.remove(id)
      }
    }
  }


  protected def ValueToBindCertToAuthorize(value:Array[Byte]):BindCertToAuthorize={
    var abc : BindCertToAuthorize = null

    try{
      if(value != null)
        abc = SerializeUtils.deserialise(value).asInstanceOf[BindCertToAuthorize]
    }catch {
      case e: Exception => {
        //todo add exception to log file
        RepLogger.Permission_Logger.error(s"IAuthBindToCert.ValueToBindCertToAuthorize deserialise error,info=${e.getMessage}")
      }
    }
    abc
  }

  def FindBindCertToAuthorize(authid:String,certid:String):Boolean={
    var r  = false

    val key = authid+"_"+certid
    //检查数据缓存是否包含了authid和certid，如果包含直接返回给调用者
    if(this.bind_map_cache.contains(key)){
      r = this.bind_map_cache.get(key).get
    }else {
      //数据缓存没有包含authid和certid，说明数据未加载到缓存，需要获取
      synchronized {
        val bb = getDataFromStorage(PermissionKeyPrefix.authBindCertPrefix + key)
        if (bb == null) {
          this.bind_map_cache.put(key, false)
        } else {
          val ab = ValueToBindCertToAuthorize(bb)
          if (ab == null) {
            this.bind_map_cache.put(key, false)
          } else {
            r = true
            this.bind_map_cache.put(key, true)
          }
        }
      }
    }
    r
  }

}
