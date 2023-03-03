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

package rep.app.management

import com.github.swagger.akka.SwaggerHttpService
import com.github.swagger.akka.model.{Info, License}
import io.swagger.v3.oas.models.ExternalDocumentation

/** 集成Swagger到AKKA HTTP
  *
  * @author zyf
  * @since 2.0.0
  */
object ManagementSwagger extends SwaggerHttpService {
  override val apiClasses: Set[Class[_]] = Set(
    classOf[ManagementService]
  )
  override val info = Info(
    description = "RepChain Management API Doc",
    version = "2.0.0",
    title = "RepChain Management",
    license = Some(License("Apache 2.0", "http://www.apache.org/licenses/LICENSE-2.0.html"))
  )
  /**
    * 重写swaggerConfig，加上tag描述信息
    *
    * @author zyf
    */
  override val externalDocs = Some(new ExternalDocumentation().description("Developers Guide").url("https://repchaindoc.readthedocs.io/zh/latest/index.html"))
}