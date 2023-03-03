import org.json4s._
import org.json4s.jackson.JsonMethods._
import rep.proto.rc2.ActionResult
import rep.sc.scalax.IContract
import rep.sc.scalax.ContractContext
import rep.sc.scalax.ContractException
import rep.sc.tpl.did.DidTplPrefix.signerPrefix
import rep.utils.IdTool
import rep.zk.AltBn128
import rep.zk.G1Point
import rep.zk.G2Point
import rep.zk.Pairing

/**
  * 资产管理合约
  */

class SC_zksnark_1 extends IContract{

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
