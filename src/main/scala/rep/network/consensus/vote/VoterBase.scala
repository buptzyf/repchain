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

package rep.network.consensus.vote




/**
  * 特质:全局节点控制
  * Created by shidianyue on 2017/5/15.
  * @update 2018-05 jiangbuyun
  */
trait VoterBase {

  
  
  
  /**
    * 获取出块人（竞争胜出者）
    * @param nodes
    * @tparam T
    * @return
    */
  //def blocker[T](nodes:Set[T], position:Int):Option[T]
  def blocker(nodes:Array[String], position:Int):String

  /**
    * 获取候选人节点
    * @param nodes
    * @tparam T
    * @param seed 随机种子(这里写的不太好，应该改一下）
    * @return
    */
  //def candidators[T](nodes:Set[T], seed:Array[Byte]):Set[T]
  def candidators(nodes:Set[String], seed:Array[Byte]):Array[String]
}
