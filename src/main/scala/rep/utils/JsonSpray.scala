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

import spray.json._


/** 
 *  @author c4w
 */
object JsonSpray {
  implicit object AnyJsonFormat extends JsonFormat[Any] {
      def write(x: Any) = x match {
        case null => JsNull
        case None => JsNull
        case n: Int => JsNumber(n)
        case s: String => JsString(s)
        case b: Boolean if b == true => JsTrue
        case b: Boolean if b == false => JsFalse
      }
      def read(value: JsValue) = value match {
        case JsNumber(n) => n.intValue()
        case JsString(s) => s
        case JsTrue => true
        case JsFalse => false
      }
  }
}