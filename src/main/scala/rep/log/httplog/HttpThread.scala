package rep.log.httplog


import java.text.SimpleDateFormat
import java.util
import java.util.Date
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils
import org.apache.http.entity.StringEntity
import org.json4s.JsonAST.{JField, JNull, JObject}
import rep.log.RepLogger


/**
 * @author jiangbuyun
 * @version 1.1
 * @since 2021-07-09
 * @category http请求线程，负责提交日志信息到prisma，由prisma写日志数据到数据库
 *           建立该任务类需要输入url和json日志信息
 **/


//STORAGE
//NETWORK
//CONSENSUS
//CONTRACT
//API
//SYSTEM

/*
1
Critical 紧急
2
Major 重要
3
Minor 次要
4
Warning 提示
5
Indeterminate 不确定
6
Cleared 清除
*/
case class AlertInfo(category: String, level: Int, desc: String)

class HttpThread(url: String, info: AlertInfo) extends Runnable {
  private val paramOfJSon = this.ConvertAlertInfoToJSon(info)

  override def run(): Unit = {
    work
  }

  /**
   * @author jiangbuyun
   * @version 1.1
   * @since 2021-07-09
   * @category 执行post提交
   **/
  private def work: Unit = {
    val httpClient = HttpClientBuilder.create.build
    var response: CloseableHttpResponse = null
    try {
      val httpPost = new HttpPost(this.url)
      val requestConfig = RequestConfig.custom.setSocketTimeout(10000).setConnectTimeout(10000).build
      httpPost.setConfig(requestConfig)
      val entity = new StringEntity(this.paramOfJSon, "utf-8")
      httpPost.setEntity(entity)
      httpPost.setHeader("Content-Type", "application/json")
      // 发送Post请求
      response = httpClient.execute(httpPost)
      RepLogger.System_Logger.debug(s"HttpThread,Response status:${response.getStatusLine}")
      // 获取响应
      val responseEntity = response.getEntity
      if (responseEntity != null) {
        RepLogger.System_Logger.debug(s"HttpThread,Response length:${responseEntity.getContentLength}")
        RepLogger.System_Logger.debug(s"HttpThread,Response content:${EntityUtils.toString(responseEntity)}")
      }
    } catch {
      case e: Exception =>
        RepLogger.System_Logger.error(s"Post Log Except,info:url=${this.url},param=${this.paramOfJSon},msg=${e.getMessage}")
    } finally { // 释放资源
      if (httpClient != null) {
        try {
          httpClient.close()
        } catch {
          case e: Exception =>
            RepLogger.System_Logger.error(s"close httpClient Except,info:url=${this.url},param=${this.paramOfJSon},msg=${e.getMessage}")
        }
      }

      if (response != null) {
        try {
          response.close()
        } catch {
          case e: Exception =>
            RepLogger.System_Logger.error(s"close response Except,info:url=${this.url},param=${this.paramOfJSon},msg=${e.getMessage}")
        }
      }
    }
  }

  private def ConvertAlertInfoToJSon(info: AlertInfo): String = {

    var Str = new StringBuffer()

    Str.append("{\"query\":\"mutation {\\n  createAlert(data: {")
      .append("category: ").append(info.category).append(",")
      .append("level: ").append(info.level).append(",")
      .append("alertDesc: \\\"").append(info.desc).append("\\\",")
      .append("alertTime: \\\"").append(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'+08:00'").format(new Date())).append("\\\"")
      .append("})")
      .append("{\\n    id\\n  }\\n}\\n\"}")

    Str.toString
  }


}
