package rep.accumulator

import rep.accumulator.CreateTestTransactionService.tx_data
import rep.accumulator.chain.VectorCommitment
import rep.accumulator.chain.VectorCommitment.TxAndPrime
import rep.crypto.Sha256
import rep.network.consensus.util.BlockHelp
import rep.proto.rc2.{Block, Transaction}

import java.io.PrintWriter
import java.math.BigInteger
import scala.math.abs
import scala.util.Random
import org.apache.commons.codec.binary.{Base32, Base64, BinaryCodec, Hex}

object vectorCommitment_test extends App {
  val tx_service = new CreateTestTransactionService
  val ctx = tx_service.ctx
  val hash_tool = new Sha256(ctx.getCryptoMgr.getInstance)

  //product_test
  //TransactionOfBlockVectorCommitment_test
  //TransactionOfBlockVectorCommitmentProve_test
  toHexString_test



  def toHexString_test:Unit={
    val ht = ctx.getHashTool
    val t = getTransaction(100)
    for(i<-0 to t.length-1){
      val t_hash = ht.hash(t(i).tx)
      val b32 = new Base32(false)
      val a = b32.encode(t_hash)
      val asc = String.valueOf(BinaryCodec.toAsciiChars(t_hash))
      //val hex = ByteToString(t_hash)


      System.out.println(s"i:${i},bytelength:${t_hash.length},hexlength:${asc.length}, hex:${asc}")
    }
  }

  def ByteToChar(bs:Array[Byte]):Array[Char]={
    var r : Array[Char] = null
    if(bs != null && bs.length>0) {
      r = new Array[Char](bs.length)
      for(i<-0 to bs.length-1){
        r(i) = (bs(i).toInt + 128).toChar
        System.out.print(s"int=${bs(i).toInt},int+128=${bs(i).toInt + 128},char=${r(i)};")
      }
      System.out.println("")
    }
    r
  }

  def ByteToString(bs:Array[Byte]):String={
    val cs = ByteToChar(bs:Array[Byte])
    String.valueOf(cs)
  }

  def TransactionOfBlockVectorCommitmentProve_test:Unit={
    val vc: VectorCommitment = new VectorCommitment(ctx)
    var old_acc_value :BigInteger = null
    val isUseBlock = true

    val number = 10
    val block1 = getBlock(number,1)
    val block2 = getBlock(number,2)
    val block3 = getBlock(number,3)
    val vc1 = vc.getTransactionWitnessesWithBlock(block1._1,old_acc_value,isUseBlock)
    val vc2 = vc.getTransactionWitnessesWithBlock(block2._1,vc1.tx_acc_value,true)
    val vc3 = vc.getTransactionWitnessesWithBlock(block3._1,vc2.tx_acc_value,true)

    val acc3 = new Accumulator(ctx.getTxAccBase,vc3.tx_acc_value,ctx.getHashTool)
    val ri = abs(new Random().nextInt(10))
    val wi = vc3.txWitnesses(ri)
    val proof = acc3.getMemberProof4Witness(wi.prime,wi.witnessInBlock)
    if(acc3.verifyMembershipProof(wi.prime,proof.proof)){
      System.out.println("verify member proof,is success,ok")
    }else{
      System.out.println("verify member proof,is failed,ok")
    }
  }

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
        val tx_wits = vc.getTransactionWitnessesWithBlock(block, old_acc_value)
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

  def getBlock(count:Int,height:Long):(Block,Array[String],Array[BigInteger])={
    val block_tx = getTransaction(count: Int): Array[tx_data]
    val txs = for (tx <- block_tx) yield {
      Transaction.parseFrom(tx.tx)
    }
    val tids = for(t <- txs) yield {
      t.id.toString
    }
    val tps = for(t <- block_tx) yield {
      t.prime
    }
    Tuple3(BlockHelp.buildBlock("", height, txs.toSeq),tids,tps)
  }
}
