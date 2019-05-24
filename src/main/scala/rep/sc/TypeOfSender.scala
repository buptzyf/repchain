package rep.sc

/**
 * 定义交易调用者的类型，目前是三种以后可以扩展
 *  1.FromAPI说明交易的请求来源于系统的API。
 * 2.FromPreloader 说明交易来源于系统的预出块环节。
 * 3.ContractInNone 说明交易来源于系统的背书环节。
 */
object TypeOfSender extends Enumeration {
  type TypeOfSender = Value

  val FromAPI = Value(1, "FromAPI")
  val FromPreloader = Value(2, "FromPreloader")
  val FromEndorser = Value(3, "FromEndorser")
}