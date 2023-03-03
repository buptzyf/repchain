package rep.app.management

import akka.actor.Actor
import org.json4s.JValue
import rep.app.conf.RepChainConfig


object ManagementActor{

  case class SystemStart(nodeName: String)
  case class SystemStop(nodeName:String)
  case class SystemStatusQuery(nodeName:String)
  case class SystemNetworkQuery(nodeName:String)

}

class ManagementActor extends Actor{
  import rep.app.management.ManagementActor.{ SystemStart, SystemStatusQuery, SystemStop,SystemNetworkQuery}
  override def receive: Receive = {
    case SystemStart(nodeName) =>
      val rsBuf = new StringBuffer()
      rsBuf.append("{")
      if(nodeName.indexOf(",")>0){
        val nodes = nodeName.split(",")
        for(i<-0 to nodes.length-1){
          val result = RepChainMgr.Startup4Single(nodes(i).trim,ReasonOfStartup.Manual)
          if(i > 0){
            rsBuf.append(",")
          }
          rsBuf.append("\""+nodes(i).trim+"\":\""+result+"\"")
        }
      }else{
        val result = RepChainMgr.Startup4Single(nodeName.trim,ReasonOfStartup.Manual)
        rsBuf.append("\""+nodeName.trim+"\":\""+result+"\"")
      }
      rsBuf.append("}")
      sender ! rsBuf.toString

    case  SystemStatusQuery(nodeName:String) =>
      val rsBuf = new StringBuffer()
      rsBuf.append("{")
      if(nodeName.indexOf(",")>0){
        val nodes = nodeName.split(",")
        for(i<-0 to nodes.length-1){
          val result = RepChainMgr.systemStatus(nodes(i).trim)
          if(i > 0){
            rsBuf.append(",")
          }
          rsBuf.append("\""+nodes(i).trim+"\":\""+result+"\"")
        }
      }else{
        val result = RepChainMgr.systemStatus(nodeName.trim)
        rsBuf.append("\""+nodeName.trim+"\":\""+result+"\"")
      }
      rsBuf.append("}")
      sender ! rsBuf.toString

    case SystemStop(nodeName) =>
      val rsBuf = new StringBuffer()
      rsBuf.append("{")
      if(nodeName.indexOf(",")>0){
        val nodes = nodeName.split(",")
        for(i<-0 to nodes.length-1){
          val result = RepChainMgr.shutdown(nodes(i).trim,ReasonOfStartup.Manual)
          if(i > 0){
            rsBuf.append(",")
          }
          rsBuf.append("\""+nodes(i).trim+"\":\""+result+"\"")
        }
      }else{
        val result = RepChainMgr.shutdown(nodeName.trim,ReasonOfStartup.Manual)
        rsBuf.append("\""+nodeName.trim+"\":\""+result+"\"")
      }
      rsBuf.append("}")
      sender ! rsBuf.toString

      case SystemNetworkQuery(nodeName) =>
        val rsBuf = new StringBuffer()
        rsBuf.append("{")
        if(nodeName.indexOf(",")>0){
          val nodes = nodeName.split(",")
          for(i<-0 to nodes.length-1){
            if(RepChainMgr.isExistSystem(nodes(i).trim)){
              val config = new RepChainConfig(nodes(i).trim)
              val result = config.getChainNetworkId
              if(i > 0){
                rsBuf.append(",")
              }
              rsBuf.append("\""+nodes(i).trim+"\":\""+result+"\"")
            }else{
              if(i > 0){
                rsBuf.append(",")
              }
              rsBuf.append("\""+nodes(i).trim+"\":\"节点不存在\"")
            }
          }
        }else{
          if(RepChainMgr.isExistSystem(nodeName.trim)) {
            val config = new RepChainConfig(nodeName.trim)
            val result = config.getChainNetworkId
            rsBuf.append("\"" + nodeName.trim + "\":\"" + result + "\"")
          }else{
            rsBuf.append("\""+nodeName.trim+"\":\"节点不存在\"")
          }
        }
        rsBuf.append("}")
        sender ! rsBuf.toString
  }
}
