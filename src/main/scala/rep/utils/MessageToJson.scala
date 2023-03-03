package rep.utils

import com.google.protobuf.timestamp.Timestamp
import scalapb.json4s.{JsonFormat, Printer, Timestamps}
import org.json4s.JsonAST._
import scalapb.GeneratedMessage

/**
 * Utils to format the json object when convert protobuf message
 * to json object.
 * Created by JayTsang on 2021/2/2.
 */
object MessageToJson {

  /**
   * 标明时间字符串中的时间所处的正确时区
   * @param t
   * @return 符合ISO8601标准的时间字符串
   */
  private def myWriteTimestamp(t: Timestamp)= {
    val res = Timestamps.writeTimestamp(t)
    res.replace("Z", "+08:00")
  }

  /**
   * 将protobuf message转为格式化后的Json对象
   * @param m protobuf message对象
   * @return 结果Json对象
   */
  def toJson[A <: GeneratedMessage ](m: A): JValue =  {

    val myJsonFormatRegistry = JsonFormat.DefaultRegistry.registerWriter((t: Timestamp) => JString(myWriteTimestamp(t)), jv => jv match {
      case JString(str) => Timestamps.parseTimestamp(str)
      case _ => throw new Exception("Expected a string.")
    })
    val myPrinter = new Printer().withFormatRegistry(myJsonFormatRegistry)
    val r = myPrinter.toJson(m)
    r
  }
}
