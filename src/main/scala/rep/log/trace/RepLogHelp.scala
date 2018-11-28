/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BA SIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

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