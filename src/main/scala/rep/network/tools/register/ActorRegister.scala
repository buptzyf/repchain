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

package rep.network.tools.register

import akka.actor.ActorRef

import scala.collection.mutable

/**
  * Single System Actor Reference Register
  * Created by shidianyue on 2017/9/22.
  */
class ActorRegister {
  private val actorList = mutable.HashMap[Int, ActorRef]()

  def register(actorName:Int, actorRef: ActorRef)={
    actorList.put(actorName,actorRef)
  }

  def getActorRef(actorName:Int) = actorList.get(actorName)

  def unregister(actorName:Int) = actorList.remove(actorName)

}
