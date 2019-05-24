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

package rep.utils

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import java.security.cert.Certificate
import scala.util.{Try, Success, Failure}

import com.twitter.chill.KryoInjection
import com.twitter.bijection._

import org.json4s._
import org.json4s.native.JsonMethods
import org.json4s.native.JsonMethods._

import org.json4s.native.Serialization.write
import org.json4s.native.Serialization.read
/**
  * Created by shidianyue on 2017/6/9.
  * updated by c4w 2019/3
  */
object SerializeUtils {
  implicit val formats = DefaultFormats

  /**
    * Java 序列化方法
    * @param value
    * @return
    */
  def serialise(value: Any): Array[Byte] = {
    KryoInjection(value)
  }

  /**
    * Java 反序列化方法
    * @param bytes
    * @return
    */
  def deserialise(bytes: Array[Byte]): Any = {
    if(bytes == null)
      return null;
    KryoInjection.invert(bytes) match {
      case Success(any) =>
        any
      case Failure(e) =>
        null
    }
  }

def compactJson(src: Any): String = {
    import org.json4s.native.Serialization
    import org.json4s.native.JsonMethods._

    implicit val formats = Serialization.formats(NoTypeHints)
    compact(render(Extraction.decompose(src)))
  }  

  /**
    * Json对象序列化
    * @param value
    * @return
    */
  def toJson(v: AnyRef): String = {
    write(v)
  }

  /**
    * Json对象反序列化
    * @param bytes
    * @return
    */
  def fromJson[A: Manifest](s: String): A = {
    read[A](s)
  }
}
