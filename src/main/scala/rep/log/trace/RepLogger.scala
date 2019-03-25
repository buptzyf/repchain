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
import rep.log.trace.ModuleType.ModuleType
import rep.log.trace.LogType.LogType
import java.lang.Throwable

/**
 * RepChain统一日志输出工具，外部输出日志统一调用对象
 * @author jiangbuyun
 * @version	1.0
 */

object RepLogger {
  protected def log = LoggerFactory.getLogger(this.getClass)
  
  def logWarn(nodeName:String,mtype:ModuleType,message:String) = {
    if(LogOption.isPrintLog(nodeName, mtype)){
      logMsg(LogType.WARN,getMessage(nodeName,mtype,message) )
    }
  }
  
  def logWarn4Exception(nodeName:String,mtype:ModuleType,message:String,t:Throwable) = {
     if(LogOption.isPrintLog(nodeName, mtype)){
        logMsg4Exception(LogType.WARN,getMessage(nodeName,mtype,message),t )
      }
   }
  
  def logDebug(nodeName:String,mtype:ModuleType,message:String) = {
    if(LogOption.isPrintLog(nodeName, mtype)){
      logMsg(LogType.DEBUG,getMessage(nodeName,mtype,message) )
    }
  }
  
  def logDebug4Exception(nodeName:String,mtype:ModuleType,message:String,t:Throwable) = {
     if(LogOption.isPrintLog(nodeName, mtype)){
        logMsg4Exception(LogType.DEBUG,getMessage(nodeName,mtype,message),t )
      }
   }
  
  def logInfo(nodeName:String,mtype:ModuleType,message:String) = {
    if(LogOption.isPrintLog(nodeName, mtype)){
      logMsg(LogType.INFO,getMessage(nodeName,mtype,message) )
    }
  }
  
  def logInfo4Exception(nodeName:String,mtype:ModuleType,message:String,t:Throwable) = {
     if(LogOption.isPrintLog(nodeName, mtype)){
        logMsg4Exception(LogType.INFO,getMessage(nodeName,mtype,message),t )
      }
   }
  
  def logError(nodeName:String,mtype:ModuleType,message:String) = {
    if(LogOption.isPrintLog(nodeName, mtype)){
      logMsg(LogType.ERROR,getMessage(nodeName,mtype,message))
    }
  }
  
  def logError4Exception(nodeName:String,mtype:ModuleType,message:String,t:Throwable) = {
     if(LogOption.isPrintLog(nodeName, mtype)){
        logMsg4Exception(LogType.ERROR,getMessage(nodeName,mtype,message),t )
      }
   }
  
  private def getMessage(nodeName:String,mtype:ModuleType,message:String):String={
    nodeName + " ~ " +mtype.toString() +  " ~ " + message
  }
  
  private def logMsg(logtype: LogType.LogType,  msg:String) = {
    logtype match {
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
  
  private def logMsg4Exception(logtype: LogType.LogType,  msg:String,t:Throwable) = {
    logtype match {
      case LogType.INFO =>
        log.info(msg,t)
      case LogType.DEBUG =>
        log.debug(msg,t)
      case LogType.WARN =>
        log.warn(msg,t)
      case LogType.ERROR =>
        log.error(msg,t)
    }
  }  
}
