package rep.accumulator

import rep.app.system.RepChainSystemContext
import rep.proto.rc2.{ChaincodeId, Transaction}
import rep.utils.IdTool

object Accumulator_test extends App {
  val ctx = new RepChainSystemContext("121000005l35120456.node1")
  val keyFileSuffix = ctx.getCryptoMgr.getKeyFileSuffix
  ctx.getSignTool.loadPrivateKey("121000005l35120456.node1", "123", s"${keyFileSuffix.substring(1)}/${ctx.getConfig.getChainNetworkId}/" + "121000005l35120456.node1" + s"${keyFileSuffix}")

  val transactionContent = getTransactionContent
  val chaincode = new ChaincodeId("ContractAssetsTPL",1)
  var acc : Accumulator = new Accumulator(null,null,null,ctx.getHashTool)


  //functionalTesting
  performanceTesting

  private def performanceTesting:Unit={
    val count = 100
    for (i <- 0 to count) {
      var start = System.currentTimeMillis()
      val t = getTransaction
      val tb = t.toByteArray
      var end = System.currentTimeMillis()
      var t_time = end - start
      acc = acc.add(tb)
      var add_end = System.currentTimeMillis()
      var a_time = add_end - end
      var wit = acc.membershipWitness(tb)
      var m_end = System.currentTimeMillis()
      var m_time = m_end - add_end
      acc.verifyMembershipWitness(wit, tb)
      var v_end = System.currentTimeMillis()
      var v_time = v_end - m_end
      System.out.println(s"loop times=${i}，create transaction time=${t_time}ms,add to accumulator time=${a_time}ms," +
        s"member promise time=${m_time}ms,verify member promise time=${v_time}ms,aggregate length=${acc.acc_aggregate_value.bitLength()/8+1}Byte")
    }

  }

  private def functionalTesting:Unit={
    for (i <- 0 to 9) {
      val t = getTransaction
      val tb = t.toByteArray
      val acc1 = acc.add(tb)
      val acc2 = acc.add1(tb)
      if (acc1.getAccVaule.compareTo(acc2.getAccVaule) == 0) {
        System.out.println(s"equal:acc1.value=${acc1.getAccVaule},acc2.value=${acc2.getAccVaule}")
      } else {
        System.out.println(s"not equal:acc1.value=${acc1.getAccVaule},acc2.value=${acc2.getAccVaule}")
      }
      System.out.println(s"agg value:agg1.value=${acc1.acc_aggregate_value},agg2.value=${acc2.acc_aggregate_value}")

      val wit = acc1.membershipWitness(tb)
      if (acc1.verifyMembershipWitness(wit, tb)) {
        System.out.println("verify ok")
      } else {
        System.out.println("verify failed")
      }
      acc = acc1
    }
  }

  private def getTransaction:Transaction={
    ctx.getTransactionBuilder.createTransaction4Invoke(ctx.getConfig.getChainNetworkId + IdTool.DIDPrefixSeparator + ctx.getSystemName,
      chaincode,"transfer", Seq(transactionContent))
  }

  private def getTransactionContent:String={
    var fpath = "api_req/json/transfer_" + ctx.getSystemName + ".json"
    if (ctx.getConfig.isUseGM) {
      fpath = "api_req/json/gm/transfer_" + ctx.getSystemName + ".json"
    }
    val si2 = scala.io.Source.fromFile(fpath, "UTF-8")
    try si2.mkString finally si2.close()
  }
}
