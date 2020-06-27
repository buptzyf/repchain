package rep.storage

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

import rep.protos.peer.{Authorize, Certificate, Operate, Signer}
import rep.sc.tpl.did.DidTplPrefix
import rep.utils.SerializeUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.collection.JavaConverters._
import scala.concurrent.Future

class PermissionOfPreload() {
  private  var caches_sig = new ConcurrentHashMap[String, Signer] asScala
  private  var caches_op = new ConcurrentHashMap[String, Operate] asScala
  private  var caches_auth = new ConcurrentHashMap[String, Authorize] asScala
  private  var  openops:AtomicReference[String] = new AtomicReference[String]("")

  def permissionFilter(key:String,value:Array[Byte])={
    val f = asyncParseKey(key:String,value:Array[Byte])
    f.onComplete(f=>{
      if(f != None){
        val tmp = f.get
        if(tmp.isInstanceOf[Signer]){
          this.caches_sig.put(key,tmp.asInstanceOf[Signer])
        }else if(tmp.isInstanceOf[Operate]){
          this.caches_op.put(key,tmp.asInstanceOf[Operate])
        }else if(tmp.isInstanceOf[Authorize]){
          this.caches_auth.put(key,tmp.asInstanceOf[Authorize])
        }else if(tmp.isInstanceOf[List[String]]){
          this.openops.set(tmp.toString)
        }
      }
    })
  }

  private def asyncParseKey(key:String,value:Array[Byte]): Future[Option[Any]] = Future {
    var t : Option[Any] = None

    if(value != null){
      if(key.indexOf("_"+DidTplPrefix.signerPrefix)>0){
        //did更新或者注册
        val s = SerializeUtils.deserialise(value).asInstanceOf[Signer]
        t = Some(s)
      }else if(key.indexOf("_"+DidTplPrefix.operPrefix)>0){
        //operate更新或者注册
        val s = SerializeUtils.deserialise(value).asInstanceOf[Operate]
        t = Some(s)
      }else if(key.indexOf("_"+DidTplPrefix.authPrefix)>0){
        //auth更新或者注册
        val s = SerializeUtils.deserialise(value).asInstanceOf[Authorize]
        t = Some(s)
      }else if(key.indexOf("_"+"open_ops")>0){
        //open操作更新或者新建
        val s = SerializeUtils.deserialise(value).asInstanceOf[List[String]]
        t = Some(s.mkString(","))
      }else if(key.indexOf("_"+DidTplPrefix.certPrefix)>0){
        //暂时为使用 todo
      }else if(key.indexOf("_"+DidTplPrefix.hashPrefix)>0){
        //暂时未使用 todo
      }else if(key.indexOf("_"+DidTplPrefix.bindPrefix)>0){
        //暂时未使用 todo
      }
    }

    t
  }
}
