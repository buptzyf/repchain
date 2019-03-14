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

import rep.log.trace.ModuleType.ModuleType

/**
 * 日志模块的测试代码
 * @author jiangbuyun
 * @version	1.0
 */

object test extends Object{
  def main(args: Array[String]): Unit = {
    var i = 0
    while(i < 100){
      RepLogger.logInfo("1", ModuleType.endorser, s"test{$i}")
      if(i == 10){
        LogOption.closeNodeLog("1")
      }
      if(i == 20){
        LogOption.openNodeLog("1")
        LogOption.setModuleLogOption("1", "endorser", false)
      }
      if(i == 30){
        LogOption.setModuleLogOption("1", "endorser", true)
      }
      if(i == 35){
        RepTimeTracer.setStartTime("1", "test", System.currentTimeMillis())
      }
      if(i == 37){
        RepTimeTracer.setEndTime("1", "test", System.currentTimeMillis())
      }
      
      if(i == 40){
        RepTimeTracer.openTimeTrace
      }
      
      if(i == 45){
        RepTimeTracer.setStartTime("1", "test", System.currentTimeMillis())
      }
      if(i == 48){
        RepTimeTracer.setEndTime("1", "test", System.currentTimeMillis())
      }
      
      i = i+1
    }
    
  }
}