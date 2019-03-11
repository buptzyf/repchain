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

import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._
import rep.log.trace.ModuleType.ModuleType;

object LogOption {
  private implicit var options  = new ConcurrentHashMap[String, Boolean] asScala
  
  def isPrintLog(nodeName:String,mt:ModuleType):Boolean={
    var b = true
    if(options.contains(nodeName)){
      if(options(nodeName)){
        val k = nodeName+"_"+mt.toString()
        if(options.contains(k)){
          b = options(k)
        }
      }else{
        b = false
      }
    }
    b
	}
  
  def openNodeLog(nodeName:String)={
    options.put(nodeName, true)
  }
  
  def closeNodeLog(nodeName:String)={
    options.put(nodeName, false)
  }
	
	def setModuleLogOption(nodeName:String,option:String,isOpen:Boolean)={
	  options.put(nodeName+"_"+option, isOpen)
	}
}
