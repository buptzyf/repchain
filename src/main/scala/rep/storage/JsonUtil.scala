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

import scala.collection.mutable;
import scala.util.parsing.json._


/**
 * @author jiangbuyun
 * @version	0.7
 * @since	2017-09-28
 * */
object JsonUtil {
   
  def map2Json(map : Map[String,Any]) : String = {
    val json = JSONObject(map)
    val jsonString = json.toString()
    jsonString
  }
 
  def json2Map(jsonstr : String) : Map[String,Any] = {
    val json:Option[Any] = JSON.parseFull(jsonstr)
    val map:Map[String,Any] = json.get.asInstanceOf[Map[String, Any]]
    map
  }
  
  def hashmap2Json(map : mutable.HashMap[String,Any]) : String = {
    val jsonString = map2Json(map.toMap)
    jsonString
  }
 
  def json2HashMap(jsonstr : String) : mutable.HashMap[String,Any] = {
    var map2 = new mutable.HashMap[String, Any];
    val json:Option[Any] = JSON.parseFull(jsonstr)
    if(json != null){
      val map:Map[String,Any] = json.get.asInstanceOf[Map[String, Any]]
      map.foreach(f=>{map2.put(f._1, f._2)})
    }
    map2
  }
}