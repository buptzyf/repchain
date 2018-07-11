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

package rep.network.consensus

/**
  * Created by shidianyue on 2017/9/23.
  */
trait BaseConsenter {
  /**
    * 共识出初始化
    */
  def init()

  /**
    * 初始化完成，开始同步
    */
  def initFinished()


  def start()

  /**
    * 下一次共识
    */
  def nextConsensus()
}
