package rep.accumulator

import rep.accumulator.CreateTestTransactionService.tx_data
import rep.accumulator.chain.VectorCommitment
import rep.accumulator.chain.VectorCommitment.TxAndPrime
import rep.crypto.Sha256
import rep.network.consensus.util.BlockHelp
import rep.proto.rc2.Transaction

import java.io.PrintWriter
import java.math.BigInteger

object vectorCommitment_test extends App {
  val tx_service = new CreateTestTransactionService
  val ctx = tx_service.ctx
  val hash_tool = new Sha256(ctx.getCryptoMgr.getInstance)

  //product_test
  TransactionOfBlockVectorCommitment_test

  def TransactionOfBlockVectorCommitment_test:Unit={
    val vc : VectorCommitment = new VectorCommitment(ctx)
    val isUseBlock = false
    var old_acc_value :BigInteger = null
    for(i<-0 to 3){
      val number = 1000
      val block_tx = getTransaction(number)
      val txs = for(tx <- block_tx) yield {
        Transaction.parseFrom(tx.tx)
      }
      val block = BlockHelp.buildBlock("",i,txs.toSeq)
      val tps = for (tx <- block_tx) yield {
        val t = Transaction.parseFrom(tx.tx)
        TxAndPrime(t.id,tx.prime)
      }
      val start = System.currentTimeMillis()

      if(isUseBlock){
        val tx_wits = vc.getTransactionWitnesses(block, old_acc_value)
        if (tx_wits != null) {
          old_acc_value = tx_wits.tx_acc_value
        }
      }else{
        val tx_wits = vc.getTransactionWitnesses(tps, i, old_acc_value)
        if (tx_wits != null) {
          old_acc_value = tx_wits.tx_acc_value
        }
      }

      val end = System.currentTimeMillis()
      System.out.println(s"block's transaction(1000) vc,time=${end-start}ms,commitment length=${old_acc_value.bitLength()/8}Byte")
    }
  }

  def product_test:Unit={
    val number = 1000000
    val block_tx_100w = getTransaction(number)

    val start = System.currentTimeMillis()
    var p = BigInteger.ONE
    var i = 0
    var start_per = System.currentTimeMillis()
    val startNo = 0
    for (j <- startNo to block_tx_100w.length - 1) {
      val tx = block_tx_100w(j)
      p = Rsa2048.mul(p, tx.prime)
      i += 1
      if (i % 10000 == 0) {
        System.out.println(s"product 1w,i=${i},time=${(System.currentTimeMillis() - start_per) / 1000}s," +
          s"reulst length=${(p.bitLength() / 8 + 1) / 1000}KB")
        val pw = new PrintWriter(s"repchaindata/product_${i}.txt", "UTF-8")
        pw.write(p.toString())
        pw.flush()
        pw.close()
        start_per = System.currentTimeMillis()
      }
    }
    val end = System.currentTimeMillis()
    System.out.println(s"100w primes product,time=${(end - start) / 1000}s")
  }

  def getTransaction(count: Int): Array[tx_data] = {
    val r = new Array[tx_data](count)
    for (i <- 0 to count - 1) {
      r(i) = tx_service.readTx
      /*if(i % 10000 == 0){
        System.out.println(s"read tx,j=${i}")
      }*/
    }
    r
  }
}
