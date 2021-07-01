package rep.utils

import java.net.NetworkInterface
import org.apache.http.conn.util.InetAddressUtils
import scala.util.control.Breaks.{break, breakable}

object NetworkTool {
  def getIpAddress:String={
    var ipv4 : String = ""
    try {
      val ipList =   NetworkInterface.getNetworkInterfaces()
      if(ipList != null){
        breakable(while(ipList.hasMoreElements){
          val tmp = ipList.nextElement()
          if(tmp != null){
            val ips = tmp.getInetAddresses
            if(ips != null){
              breakable(while(ips.hasMoreElements){
                val ip = ips.nextElement()
                //System.out.println(ip.getHostAddress)
                if(!ip.isLoopbackAddress && InetAddressUtils.isIPv4Address(ip.getHostAddress)){
                  ipv4 = ip.getHostAddress
                  break
                  //System.out.println(ip.getHostAddress)
                }
              })
              if(ipv4 != ""){
                break
              }
            }
          }
        })
      }
    }catch {
      case e:Exception =>
        e.printStackTrace()
    }
    ipv4
  }
}
