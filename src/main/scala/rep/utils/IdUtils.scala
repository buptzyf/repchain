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

import java.util.UUID
import com.gilt.timeuuid.TimeUuid

/**
  * 生成ID
  * Created by shidianyue on 2017/6/29.
  */
object IdUtils {
  def getUUID(): String = {
    val uuid = TimeUuid()
    uuid.toString
  }

  def getRandomUUID: String = {
    UUID.randomUUID().toString
  }

  def main(args: Array[String]): Unit = {
    //TODO kami 需要进行并发测试
    println(IdUtils.getUUID())
    println(IdUtils.getUUID())
    println(IdUtils.getUUID())
    println(IdUtils.getUUID())
    println(IdUtils.getUUID())

    println(IdUtils.getRandomUUID)
  }

}
