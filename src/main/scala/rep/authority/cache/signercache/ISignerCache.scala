package rep.authority.cache.signercache

import java.util.concurrent.ConcurrentHashMap
import rep.authority.cache.authcache.ImpAuthorizeCache
import rep.authority.check.PermissionKeyPrefix
import rep.log.RepLogger
import rep.protos.peer.Signer
import rep.storage.ImpDataPreload
import rep.utils.SerializeUtils
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConverters._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

/**
 * Created by jiangbuyun on 2020/06/24.
 * 实现实体账户缓存
 */

object ISignerCache{
  case class signerData(did:String,signer_valid:Boolean,opids:ConcurrentHashMap[String,ArrayBuffer[String]])
}

abstract class ISignerCache(sysTag:String) {
  import ISignerCache.signerData
  protected implicit var signer_map_cache = new ConcurrentHashMap[String, Option[signerData]] asScala
  protected implicit var signer_read_map_cache = new ConcurrentHashMap[String, Future[Option[signerData]]] asScala

  protected def getDataFromStorage(key:String):Array[Byte]

  def ChangeValue(key:String)={
    val idx = key.lastIndexOf("_")
    if(idx > 0){
      val id = key.substring(idx+1)
      if(this.signer_map_cache.contains(id)){
        this.signer_map_cache.remove(id)
      }
    }
  }

  private def AsyncHandle(did:String,pd:ImpDataPreload):Future[Option[signerData]]=Future{
    var r : Option[signerData] = None

    try {
      val bb = getDataFromStorage(PermissionKeyPrefix.sigPrefix+did)
      if(bb != null){
        val s = ValueToSigner(bb)
        if(s != null){
          RepLogger.Permission_Logger.trace(s"ISignerCache.AsynHandle get signer object,key=${did}")
          val sd = signerToSignerData(s,pd)
          if(sd != None) r = sd
        }
      }
    }catch {
      case e : Exception =>
        RepLogger.Permission_Logger.error(s"ISignerCache.AsynHandle get signer error" +
          s", info=${e.getMessage},key=${did}")
    }
    r
  }

  def FindSigner(did:String,pd:ImpDataPreload):signerData={
    var sd : signerData = null
    RepLogger.Permission_Logger.error(s"system=${sysTag},ISignerCache.FindSigner get signer did=${did}" +
      s"")
    //检查数据缓存是否包含了opid，如果包含直接返回给调用者
    if(this.signer_map_cache.contains(did)){
      sd = this.signer_map_cache.get(did).get.getOrElse(null)
      RepLogger.Permission_Logger.error(s"system=${sysTag},ISignerCache.FindSigner cache hit did=${did}" +
        s"")
    }else{
      //数据缓存没有包含opid，说明数据未加载到缓存，需要异步计算
      var tmpOperater : Future[Option[signerData]] = this.signer_read_map_cache.get(did).getOrElse(null)
      if(tmpOperater == null){
        RepLogger.Permission_Logger.error(s"system=${sysTag},ISignerCache.FindSigner Future not hit did=${did}" +
          s"")
        tmpOperater = AsyncHandle(did,pd)
        this.signer_read_map_cache.putIfAbsent(did,tmpOperater)
      }
      val result = Await.result(tmpOperater, 30.seconds).asInstanceOf[Option[signerData]]
      //将结果放到数据缓存，结果有可能是None
      RepLogger.Permission_Logger.error(s"system=${sysTag},ISignerCache.FindSigner future result did=${did}" +
        s"")
      sd = result.getOrElse(null)
      this.signer_map_cache.put(did,result)
      this.signer_read_map_cache.remove(did)
    }
    sd
  }


  protected def ValueToSigner(value:Array[Byte]):Signer={
    var s : Signer = null

    try{
      if(value != null)
        s = SerializeUtils.deserialise(value).asInstanceOf[Signer]
    }catch {
      case e: Exception => {
        //todo add exception to log file
        RepLogger.Permission_Logger.error(s"ISignerCache.ValueToSigner error,info=${e.getMessage}")
      }
    }
    s
  }

  protected def signerToSignerData(signer:Signer,pr:ImpDataPreload):Option[signerData] = {
    var sd : Option[signerData] = None
    var opids:ConcurrentHashMap[String,ArrayBuffer[String]] = new ConcurrentHashMap[String,ArrayBuffer[String]]()
    RepLogger.Permission_Logger.trace(s"ISignerCache.signerToSignerData ,key=${signer.creditCode}")
    if(signer != null) {
      if(!signer.authorizeIds.isEmpty) {
        RepLogger.Permission_Logger.trace(s"ISignerCache.signerToSignerData find Signer`s auth ,key=${signer.creditCode}")
        val authlist = signer.authorizeIds.toList
        authlist.foreach(f=>{
          val oplist = getOpidInAuthid(f,pr)
          if(!oplist.isEmpty){
            oplist.foreach(k=>{
              var ab : ArrayBuffer[String] = new ArrayBuffer[String]()
              if(opids.contains(k)){
                ab = opids.get(k)
                if(ab.indexOf(f) < 0){
                  ab += f
                  opids.put(k,ab)
                }
              }else{
                ab += f
                opids.put(k,ab)
              }
            })
          }
        })

      }
      sd = Some(new signerData(signer.creditCode,signer.signerValid,opids))
    }

    sd
  }

  protected def getOpidInAuthid(authid:String,pr:ImpDataPreload):List[String]={
    RepLogger.Permission_Logger.trace(s"ISignerCache.getOpidInAuthid ,key=${authid}")
    val adcache = ImpAuthorizeCache.GetAuthorizeCache(this.sysTag)
    val ad = adcache.getAuthorizeData(authid,pr)
    if(ad == null){
      List[String]()
    }else{
      ad.ops
    }
  }
}
