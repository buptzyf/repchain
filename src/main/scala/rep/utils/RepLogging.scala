/*
 * Copyright  2018 Blockchain Technology and Application Joint Lab, Fintech Research Center of ISCAS.
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
 */

package rep.utils

import org.slf4j.LoggerFactory
import rep.network.cluster.ClusterActor


/** 
 *  @author c4w
 */
object RepLogging{
  case class LogTime(module:String, msg:String, time:Long, cluster_addr:String)
}

trait RepLogging {

  case object LOG_TYPE{
    val INFO = 1
    val DEBUG =2
    val WARN = 3
    val ERROR = 4
  }

  protected def log = LoggerFactory.getLogger(this.getClass)

  /**
    * 记录操作时间相关的日志
    * @param sysname
    * @param log_prefix
    * @param tag
    */
  @deprecated
  def logTime(sysname:String, log_prefix:String, tag:String):Unit = {
    log.info(log_prefix + " : " + sysname + " " + tag + " ~ " + TimeUtils.getCurrentTime())
  }

  /**
    * 记录当前操作时间（鼓励使用）
    * @param module
    * @param msg
    * @param time
    * @param cluster_addr
    */
  def logTime(module:String, msg:String, time:Long, cluster_addr:String) = {
    log.info(module + " ~ Opt Time ~ " + msg + " ~  " + time + " ~ " + cluster_addr)
  }

  def logMsg(lOG_TYPE: Int, module:String, msg:String, cluster_addr:String) = {

    lOG_TYPE match {
      case LOG_TYPE.INFO =>
        log.info(module + " ~ " + msg + " ~ " + cluster_addr)
      case LOG_TYPE.DEBUG =>
        log.debug(module + " ~ " + msg + " ~ " + cluster_addr)
      case LOG_TYPE.WARN =>
        log.warn(module + " ~ " + msg + " ~ " + cluster_addr)
      case LOG_TYPE.ERROR =>
        log.error(module + " ~ " + msg + " ~ " + cluster_addr)
    }
  }

}