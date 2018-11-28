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

package rep.storage

import rep.protos._
import rep.log.trace.RepLogHelp
import rep.log.trace.LogType
import org.slf4j.LoggerFactory


/** 
 *  @author c4w
 */
class FakeStorage() extends Storage[String,Array[Byte]]  { 
  import FakeStorage._
  protected def log = LoggerFactory.getLogger(this.getClass)
  
  private val m = scala.collection.mutable.Map[Key,Value]()
  
  override def set(key:Key, value:Value): Unit = {
    val me = this
    log.info(s"set state:$key $value $me")
    m.put(key,value)
  }
  override def get(key:Key): Option[Value] = {
    m.get(key)
  }  
  override def commit(): Unit={    
  }
  override def merkle():Array[Byte]={
    null
  }
}

object FakeStorage {
 type Key = String  
 type Value = Array[Byte]
}
