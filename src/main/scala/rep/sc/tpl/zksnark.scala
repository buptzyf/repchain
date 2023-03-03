package rep.sc.tpl

import org.json4s.jackson.JsonMethods.parse
import rep.proto.rc2.ActionResult
import rep.sc.scalax.{ContractContext, IContract}
import rep.zk.Pairing

class zksnark extends IContract{

  def init(ctx: ContractContext) {
    println("init success")
  }
  def test(ctx: ContractContext): ActionResult = {
    val p1 = Pairing.P1()
    val p2 = Pairing.P2()
    Pairing.pairingProd1(p1, p2)
    null
  }
  /**
   * 根据action,找到对应的method，并将传入的json字符串parse为method需要的传入参数
   */
  def onAction(ctx: ContractContext, action: String, sdata: String): ActionResult = {
    val json = parse(sdata)
    action match {
      case "test" =>
        test(ctx)
    }
  }

}
