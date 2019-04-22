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

package rep.utils

import rep.log.RepLogger

/**
  * 时间相关工具
  * Created by shidianyue on 2017/5/17.
  */
object TimeUtils {

  def getCurrentTime():Long ={
    var time = System.currentTimeMillis()
    //中国时区+8
    time += 8*3600*1000
    time
  }

  def getNextTargetTimeDur(targetTime:Long): Long ={
    RepLogger.trace(RepLogger.System_Logger,"Time is : " + targetTime)
    val time = getCurrentTime()
    val result = targetTime - time%targetTime
    println("Time is : " + result)
    result
  }

  def getNextTargetTimeDurMore(targetTime:Long): Long ={
    val time = getCurrentTime()
    val result = targetTime - time%targetTime
    RepLogger.trace(RepLogger.System_Logger,"Time is : " + (result+targetTime))
    result+targetTime
  }
}
