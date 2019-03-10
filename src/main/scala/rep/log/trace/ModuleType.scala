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

/**
 * 定义输出日志的模块
 * @author jiangbuyun
 * @version	1.0
 */

object ModuleType extends Enumeration{
    type ModuleType = Value  
    val blocker = Value("blocker")
    val endorser = Value("endorser")
    val timeTracer = Value("timeTracer")
    val storager = Value("storager")
    val others = Value("others")
    
    def checkExists(t:String) = this.values.exists(_.toString==t) 
  }