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

package rep.api

import java.util.ArrayList

import com.github.swagger.akka.SwaggerHttpService
import com.github.swagger.akka.SwaggerHttpService.prependSlashIfNecessary
import com.github.swagger.akka.model.{Info, License}
import rep.api.rest._
//import io.swagger.models.{ExternalDocs, Scheme, Swagger}
//import io.swagger.models.auth.BasicAuthDefinition
import io.swagger.v3.oas.models.ExternalDocumentation
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.tags.Tag
import rep.app.conf.SystemProfile

/**集成Swagger到AKKA HTTP
 * @author c4w
 * @constructor 创建提供Swagger文档服务的实例
 * @author zyf
 * @since 1.0
 *
 */
object SwaggerDocService extends SwaggerHttpService {
  override val apiClasses: Set[Class[_]] = Set(
    classOf[ChainService],
    classOf[BlockService],
    classOf[TransactionService],
    classOf[DidService]
  )
  override val info = Info(
    description = "RepChian API Doc",
    version = "1.1.0",
    title = "RepChain",
    license = Some(License("Apache 2.0","http://www.apache.org/licenses/LICENSE-2.0.html"))
  )
  //override val host = s"localhost:${SystemProfile.getHttpServicePort()}"
  /**
   * 重写swaggerConfig，加上tag描述信息
   * @author zyf
   */
//  val tagList: java.util.List[Tag] = new ArrayList[Tag]()
//  tagList.add(new Tag().name("logmgr").description("日志信息管理"))
//  tagList.add(new Tag().name("chaininfo").description("获得当前区块链信息"))
//  tagList.add(new Tag().name("block").description("获得区块数据"))
//  tagList.add(new Tag().name("transaction").description("获得交易数据或提交交易"))
  override val externalDocs = Some(new ExternalDocumentation().description("Developers Guide").url("https://repchaindoc.readthedocs.io/zh/latest/index.html"))
//  override val securitySchemes = Map("basicAuth" -> new SecurityScheme().`type`(SecurityScheme.Type.HTTP))
//  override val swaggerConfig = super.swaggerConfig.tags(tagList)
//  override val unwantedDefinitions = Seq("Function1", "Function1RequestContextFutureRouteResult")
  //    new Swagger().basePath(prependSlashIfNecessary(basePath)).info(info)
  //      .scheme(Scheme.HTTP).tags(tagList).securityDefinition("basicAuth", new BasicAuthDefinition())
  //      .externalDocs(new ExternalDocs("Developers Guide", "https://repchaindoc.readthedocs.io/zh/latest/index.html"))
}