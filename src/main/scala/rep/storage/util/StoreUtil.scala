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

package rep.storage.util

import rep.crypto.Sha256

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable
import scala.collection.immutable
import rep.utils._


/**
 * @author jiangbuyun
 * @version	0.7
 * @since	2017-09-28
 * */
object StoreUtil {
  
  def SplitKey(key:String):Array[String]={
    var rel : Array[String] = null
    if(key != null){
      rel = key.split("_", 3)
      if(rel.length != 3){
        rel = null
      }
    }
    rel
  }
  

}