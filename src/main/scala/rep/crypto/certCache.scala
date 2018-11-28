package rep.crypto


import java.util.concurrent.locks._
import scala.collection.immutable
import java.security.cert.{ Certificate, CertificateFactory }
import rep.storage._
import rep.utils.SerializeUtils

object certCache {
  private val  getCertLock : Lock = new ReentrantLock();
  private var  caches : immutable.HashMap[String,Certificate] = new immutable.HashMap[String,Certificate]()
  
  def getCertForUser(certKey:String,sysTag:String):Certificate={
    var rcert : Certificate = null
    getCertLock.lock()
    try{
      if(caches.contains(certKey)){
        rcert = caches(certKey)
      }else{
        val sr: ImpDataAccess = ImpDataAccess.GetDataAccess(sysTag)
        val cert = Option(sr.Get(certKey))
        if (cert != None){
          if (!(new String(cert.get)).equalsIgnoreCase("null")) {
                val kvcert = SerializeUtils.deserialise(cert.get).asInstanceOf[Certificate]
                if(kvcert != null){
                  caches += certKey -> kvcert
                  rcert = kvcert
                }
            }
        }
      }
    }finally {
      getCertLock.unlock()
    }
    rcert
  }
}