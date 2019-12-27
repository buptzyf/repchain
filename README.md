# RepChain
[RepChain文档](https://gitee.com/BTAJL/repchain/attach_files)   [单机多节点部署](https://iscas1-my.sharepoint.cn/:v:/g/personal/zhengls_iscas1_partner_onmschina_cn/EaghaEdYxndOm1f7H01RNVoBRqWm7v5kCFXUZ4QwVVP7Wg?e=fIa58e)   [多机多节点部署](https://iscas1-my.sharepoint.cn/:v:/g/personal/zhengls_iscas1_partner_onmschina_cn/Ebk4-kDPg25KjE-9oSBKTuEBwD9pTJeQAgal_AYquLPHzg?e=D9tQNF)

## 参考阅读
- [akka](https://akka.io/) ——系统内部模块采用akka actor实现
- [akka remoting security](http://doc.akka.io/docs/akka/current/scala/remoting.html) ——节点之间安全通信采用akka Remote支持的TLS
- [akka serialization](http://doc.akka.io/docs/akka/current/scala/serialization.html)——节点之间消息交互采用protobuf序列化
- [scalaPB](https://scalapb.github.io/)——从proto定义生成Scala类的工具
- [protobufjs](https://github.com/dcodeIO/ProtoBuf.js/)——在web端根据proto定义，反序列化protobuf字节流
- [swagger-scala](https://github.com/swagger-api/swagger-scala-module)——API支持Swagger UI
- [json4s](https://github.com/json4s/json4s)——在API层提供输入对象的json反序列化，返回结果的json序列化
- [d3.js-force layout](https://github.com/d3/d3-3.x-api-reference/blob/master/Force-Layout.md)——入／离网节点的自动布局
- [leveldb for java](https://github.com/dain/leveldb)——存取Blocks、Transactions索引
- [java security](http://docs.oracle.com/javase/8/docs/technotes/guides/security/index.html)——hash、签名、密钥对及证书管理均采用jdk内置方法

## 安装
- install [zuluJdk8](https://www.azul.com/downloads/zulu-community/?&architecture=x86-64-bit&package=jdk)
- install [Python](http://www.python.org/downloads/)
- install [Scala](https://www.scala-lang.org/download/)
- install [SBT](http://www.scala-sbt.org/release/docs/Setup.html)
- install [Idea IDE](https://www.jetbrains.com/idea/download/#section=windows)，安装scala插件
- install [keystore-explorer](http://keystore-explorer.org/) ——用于生成密钥对的工具,非必须
- install [protobuf editor](https://github.com/Enide/polyglot-maven-editors)——编辑protobuf定义工具，非必须

## 运行
- ` 下载项目到本地`
  - git clone https://gitee.com/BTAJL/repchain.git
- `导入` 
  - 打开Idea IDE，File->New->Project or Project from VersionControl
  - 使用Idea的sbt插件导入
- ` 启用OpenJSSE `
  - 增加OpenJSSE provider：jre/lib/security/java.security 配置文件中加security.provider.11=org.openjsse.net.ssl.OpenJSSE <br>
  - 使用OpenJSSE 替换sunJSSE：替换com.sun.net.ssl.internal.ssl.Provider 为 org.openjsse.net.ssl.OpenJSSE
- 右键单击 rep.app.Repchain.scala，Run 'RepChain'(单机组网4个节点)
- 运行配置VM参数 -Dlogback.configurationFile=conf/logback.xml (使logback配置生效)，-XX:+UseOpenJSSE
- 查看实时图 http://localhost:8081/web/g1.html 
- 查看API  http://localhost:8081/swagger/index.html

## 修改配置
- 生成RepChain节点密钥对及信任证书列表（见[《RepChain开发者指南》](https://gitee.com/BTAJL/repchain/attach_files/235993/download) 2.1.5）
- 制作创世区块（见[《RepChain开发者指南》](https://gitee.com/BTAJL/repchain/attach_files/235993/download) 2.1.6）
- 调整系统配置参数（见[《RepChain开发者指南》](https://gitee.com/BTAJL/repchain/attach_files/235993/download) 2.1.7）

## 打包
- assembly 
  - 打包成jar包，进行分布式部署

## 论坛社区
- http://bbs.repchain.net.cn/ 
