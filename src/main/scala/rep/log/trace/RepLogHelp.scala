package rep.log.trace

import org.slf4j.LoggerFactory
import org.slf4j.Logger;

object RepLogHelp {
  
  case object LOG_TYPE{
    val INFO = 1
    val DEBUG =2
    val WARN = 3
    val ERROR = 4
  } 
  
  
 def logMsg(log: Logger,lOG_TYPE: LogType,  msg:String, nodeName:String) = {
    lOG_TYPE match {
      case LogType.INFO =>
        log.info(msg,Array(nodeName))
      case LogType.DEBUG =>
        log.debug(msg,Array(nodeName))
      case LogType.WARN =>
        log.warn(msg,Array(nodeName))
      case LogType.ERROR =>
        log.error(msg,Array(nodeName))
    }
  }  
 
 def logMsg(log: Logger,lOG_TYPE: LogType,  msg:String) = {
    lOG_TYPE match {
      case LogType.INFO =>
        log.info(msg)
      case LogType.DEBUG =>
        log.debug(msg)
      case LogType.WARN =>
        log.warn(msg)
      case LogType.ERROR =>
        log.error(msg)
    }
  }  
    
}