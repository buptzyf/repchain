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

package rep.network.base

import akka.actor.Actor


/**
  * 系统基础Actor封装
  *
  * @author shidianyue
  * @version 1.0
  **/
trait BaseActor extends Actor {
  val selfAddr = akka.serialization.Serialization.serializedActorPath(self)
  

  var schedulerLink: akka.actor.Cancellable = null

  def scheduler = context.system.scheduler

  /**
    * 清除定时器
    *
    * @return
    */
  def clearSched() = {
    if (schedulerLink != null) schedulerLink.cancel()
    null
  }
}
