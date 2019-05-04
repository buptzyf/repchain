/*
 * Copyright  2019 Blockchain Technology and Application Joint Lab, Linkel Technology Co., Ltd, Beijing, Fintech Research Center of ISCAS.
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
package rep.log

import org.slf4j.LoggerFactory
import org.slf4j.Logger;


/**
 * RepChain统一日志输出工具，外部输出日志统一调用对象
 * @author jiangbuyun
 * @version	1.0
 */

object RepLogger {
   def Business_Logger = LoggerFactory.getLogger("Business_Logger")
   def System_Logger = LoggerFactory.getLogger("System_Logger")
   def Consensus_Logger = LoggerFactory.getLogger("Consensus_Logger")
   def BlockSyncher_Logger = LoggerFactory.getLogger("BlockSyncher_Logger")
   def Storager_Logger = LoggerFactory.getLogger("Storager_Logger")
   def StatisTime_Logger = LoggerFactory.getLogger("StatisTime_Logger")
   def Sandbox_Logger = LoggerFactory.getLogger("Sandbox_Logger")
   def Vote_Logger = LoggerFactory.getLogger("Vote_Logger")
   
   def trace(logger:Logger,msg:String)={
     logger.trace(msg)
   }
   
   def debug(logger:Logger,msg:String)={
     logger.debug(msg)
   }
   
   def info(logger:Logger,msg:String)={
     logger.info(msg)
   }
   
   def error(logger:Logger,msg:String)={
     logger.error(msg)
   }
   
   def except(logger:Logger,msg:String,e:Exception)={
     logger.error(msg,e)
   }
   
   def except4Throwable(logger:Logger,msg:String,e:Throwable)={
     logger.error(msg,e)
   }
}
