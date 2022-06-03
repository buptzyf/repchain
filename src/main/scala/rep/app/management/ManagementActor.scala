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
      //s"recv start ${nodeName}"//
      val result = RepChainMgr.Startup4Single(nodeName,ReasonOfStartup.Manual)
      val rs = "{\"status\":\""+result+"\"}"
      val r = rs match {
        case null => QueryResult(None)
        case _ =>
          QueryResult(Option(JsonMethods.parse(string2JsonInput(rs))))
      }
      sender ! rs

    case  SystemStatusQuery(nodeName:String) =>
      //s"recv status ${nodeName}"//
      val result = RepChainMgr.systemStatus(nodeName)
      val rs = "{\"status\":\""+result+"\"}"
      val r = rs match {
        case null => QueryResult(None)
        case _ =>
          QueryResult(Option(JsonMethods.parse(string2JsonInput(rs))))
      }
      sender ! rs

    case SystemStop(nodeName) =>
      //s"recv stop ${nodeName}"
      val result = RepChainMgr.shutdown(nodeName,ReasonOfStop.Manual)
      val rs = "{\"status\":\""+result+"\"}"
      val r = rs match {
        case null => QueryResult(None)
        case _ =>
          QueryResult(Option(JsonMethods.parse(string2JsonInput(rs))))
      }
      sender ! rs
  }
}
