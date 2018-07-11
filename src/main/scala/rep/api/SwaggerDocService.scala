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

package rep.api

import scala.reflect.runtime.{universe=>ru}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.github.swagger.akka._
import com.github.swagger.akka.model.`package`.Info
import rep.api.rest._
import io.swagger.models.ExternalDocs
import io.swagger.models.auth.BasicAuthDefinition
import rep.app.conf.SystemProfile

/**集成Swagger到AKKA HTTP
 * @author c4w
 * @constructor 创建提供Swagger文档服务的实例
 * @param system 传入的AKKA系统实例 
 * 
 */
class SwaggerDocService(system: ActorSystem) extends SwaggerHttpService with HasActorSystem {
  override implicit val actorSystem: ActorSystem = system
  override implicit val materializer: ActorMaterializer = ActorMaterializer()
  override val apiTypes = Seq(
    ru.typeOf[ChainService],
    ru.typeOf[BlockService],ru.typeOf[TransactionService],
    ru.typeOf[CertService], ru.typeOf[HashVerifyService])
  override val info = Info(version = "0.7")
  override val externalDocs = Some(new ExternalDocs("Developers Guide", "https://repchaindoc.readthedocs.io/zh/latest/index.html"))
  override val securitySchemeDefinitions = Map("basicAuth" -> new BasicAuthDefinition())
}