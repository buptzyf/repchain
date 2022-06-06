package rep.app.management

import java.nio.file.{Path, Paths}
import java.security.cert.X509Certificate
import akka.http.javadsl.model.headers.TlsSessionInfo
import rep.storage.util.pathUtil
import scala.concurrent.duration._

object RepChainConfigFilePathMgr {

  def getCert(session:TlsSessionInfo):X509Certificate={
    val sslSession = session.getSession()
    val client_cert = sslSession.getPeerCertificates
    client_cert(0).asInstanceOf[X509Certificate]
  }

  def getSavePath(network_name:String,node_name:String,file_type:String,file_name:String):Path={
    file_type match {
      case "pfx_key" =>
        Paths.get("pfx",file_name)
      case "jks_key" =>
        Paths.get("jks",file_name)
      case "pfx_cert" =>
        Paths.get("pfx",file_name)
      case "jks_cert" =>
        Paths.get("jks",file_name)
      case "config" =>
        val path = Paths.get("conf",node_name)
        checkPath(path)
        Paths.get("conf",node_name,file_name)
      case "pfx_trust" =>
        Paths.get("pfx",file_name)
      case "jks_trust" =>
        Paths.get("jks",file_name)
      case "genesis" =>
        val path = Paths.get("json",network_name)
        checkPath(path)
        Paths.get("json",network_name,file_name)
    }
  }

  private def checkPath(path: Path):Unit={
    val file = path.toFile
    if(!file.exists()){
      pathUtil.MkdirAll(file.getPath)
    }
  }

}
