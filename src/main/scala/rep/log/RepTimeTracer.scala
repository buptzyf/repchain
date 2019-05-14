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

import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._
import java.util.concurrent.atomic._

/**
 * RepChain系统运行时间跟踪工具，需要跟踪运行时间的程序统一调用该对象
 * @author jiangbuyun
 * @version	1.0
 */

object RepTimeTracer {
  private implicit var times = new ConcurrentHashMap[String, Long] asScala
  private var isOpenTrace: AtomicBoolean = new AtomicBoolean(false)

  def openTimeTrace = {
    this.isOpenTrace.set(true)
  }

  def closeTimeTrace = {
    this.isOpenTrace.set(false)
  }

  def setStartTime(nodeName: String, flag: String, t: Long,bheight:Long,trannum:Int) = {
    if (this.isOpenTrace.get){
      val key = nodeName + "-" + flag
      this.times.put(key, t);
      RepLogger.trace(RepLogger.OutputTime_Logger,  s"${key}_bheight=${bheight}_start_time=${t},transcount=${trannum}")
    }
  }

  def setEndTime(nodeName: String, flag: String, t: Long,bheight:Long,trannum:Int) = {
    if (this.isOpenTrace.get) {
      val key = nodeName + "-" + flag;
      if (this.times.contains(key)) {
        val tl = t - this.times(key);
        RepLogger.trace(RepLogger.StatisTime_Logger,  s"${key}_bheight=${bheight}_spent_time=${tl},transcount=${trannum}")
      }
      RepLogger.trace(RepLogger.OutputTime_Logger,  s"${key}_bheight=${bheight}_end_time=${t},transcount=${trannum}")
    }
  }

}