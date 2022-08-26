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

  for(i<-0 to 9){
    val t = getTransaction
    val tb = t.toByteArray
    val acc1 = acc.add(tb)
    val acc2 = acc.add1(tb)
    if(acc1.getAccVaule.compareTo(acc2.getAccVaule) == 0){
      System.out.println(s"equal:acc1.value=${acc1.getAccVaule},acc2.value=${acc2.getAccVaule}")
    }else{
      System.out.println(s"not equal:acc1.value=${acc1.getAccVaule},acc2.value=${acc2.getAccVaule}")
    }
    System.out.println(s"agg value:agg1.value=${acc1.acc_aggregate_value},agg2.value=${acc2.acc_aggregate_value}")
    acc = acc1
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
