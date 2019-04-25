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

package rep.utils


/**
  *  @author c4w
  */
object Json4s {
  import org.json4s._
  def encodeJson(src: Any): JValue = {
    import org.json4s.JsonDSL.WithDouble._
    import org.json4s.jackson.Serialization
    implicit val formats = Serialization.formats(NoTypeHints)
    Extraction.decompose(src)
  }
  def compactJson(src: Any): String = {
    import org.json4s.jackson.Serialization
    import org.json4s.JsonDSL._
    import org.json4s.jackson.JsonMethods._

    implicit val formats = Serialization.formats(NoTypeHints)
    compact(render(Extraction.decompose(src)))
  }
  def parseJson[T: Manifest](src: String):T  = {
    import org.json4s.jackson.JsonMethods._
    implicit val formats = DefaultFormats

    val json = parse(src)
    json.extract[T]
  }
  def parseAny(src: String):Any  = {
    import org.json4s.jackson.JsonMethods._
    implicit val formats = DefaultFormats
    try{
      val json = parse(src)
      json.extract[Any]
    }catch{
      case e => src
    }
  }

}