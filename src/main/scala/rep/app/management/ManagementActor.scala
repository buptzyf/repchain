package rep.app.management

import akka.actor.{Actor, Props}
import org.json4s.{JValue, string2JsonInput}
import org.json4s.jackson.JsonMethods


object ManagementActor{

  case class SystemStart(nodeName: String)
  case class SystemStop(nodeName:String)
  case class SystemStatusQuery(nodeName:String)

  case class QueryResult(result: Option[JValue])
}

class ManagementActor extends Actor{
  import rep.app.management.ManagementActor.{QueryResult, SystemStart, SystemStatusQuery, SystemStop}
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
  }
}
