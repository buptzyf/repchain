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

package rep.api

import java.util.ArrayList

import com.github.swagger.akka.SwaggerHttpService
import com.github.swagger.akka.SwaggerHttpService.prependSlashIfNecessary
import com.github.swagger.akka.model.{Info, License}
import rep.api.rest._
import io.swagger.models.{ExternalDocs, Scheme, Swagger, Tag}
import io.swagger.models.auth.BasicAuthDefinition

/**集成Swagger到AKKA HTTP
  * @author c4w
  * @constructor 创建提供Swagger文档服务的实例
  * @param system 传入的AKKA系统实例
  * @author zyf
  * @since 1.0
  *
 */
object SwaggerDocService extends SwaggerHttpService {
  override val apiClasses: Set[Class[_]] = Set(
    classOf[ChainService],
    classOf[BlockService],
    classOf[TransactionService],
    classOf[LogMgrService]
  )
  override val info = Info(
    description = "RepChian API Doc",
    version = "1.0.0",
    title = "RepChain",
    license = Some(License("Apache 2.0","http://www.apache.org/licenses/LICENSE-2.0.html")))
  /**
    * 重写swaggerConfig，加上tag描述信息
    * @author zyf
    */
  val tagList = new ArrayList[Tag]()
  tagList.add(new Tag().name("logmgr").description("日志信息管理"))
  tagList.add(new Tag().name("chaininfo").description("获得当前区块链信息"))
  tagList.add(new Tag().name("block").description("获得区块数据"))
  tagList.add(new Tag().name("transaction").description("获得交易数据或提交交易"))
  override val swaggerConfig = new Swagger().basePath(prependSlashIfNecessary(basePath)).info(info)
      .scheme(Scheme.HTTP).tags(tagList).securityDefinition("basicAuth", new BasicAuthDefinition())
      .externalDocs(new ExternalDocs("Developers Guide", "https://repchaindoc.readthedocs.io/zh/latest/index.html"))
//  override val externalDocs = Some(new ExternalDocs("Developers Guide", "https://repchaindoc.readthedocs.io/zh/latest/index.html"))
//  override val securitySchemeDefinitions = Map("basicAuth" -> new BasicAuthDefinition())
}