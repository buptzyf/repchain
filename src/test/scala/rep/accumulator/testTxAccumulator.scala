package rep.accumulator


import rep.accumulator.Accumulator.Witness
import rep.accumulator.CreateTestTransactionService.tx_data
import rep.crypto.Sha256

import java.math.BigInteger

object testTxAccumulator extends App {
  val tx_service = new CreateTestTransactionService
  val hash_tool = new Sha256(tx_service.ctx.getCryptoMgr.getInstance)
  val numOfBlock = 10
  val block1 = getTransaction(numOfBlock)
  val block2 = getTransaction(numOfBlock)
  val block3 = getTransaction(numOfBlock)

  //testAccumulatorTransaction
  //test_add
  //test_delete
  //test_delete_bad_witness
  test_update_membership_witness

  def test_update_membership_witness:Unit={
    var root_acc = new Accumulator(null, null, hash_tool)
    val a = block1(0).prime
    val b = block1(1).prime
    val c = block1(2).prime
    val d = block1(3).prime
    val acc1 = root_acc.addOfBatch(Array(a,b,c))
    val acc2 = root_acc.addOfBatch(Array(c,d))
    val acc2_wit = Witness(acc2.getAccVaule)

    val wit_new = acc1.updateMembershipWitness(Array(a),acc2_wit,Array(b),
                        Array(d))
    val acc_new = new Accumulator(root_acc.getAccBase,wit_new.witness,hash_tool)

    if(acc_new.add(a).getAccVaule.compareTo(acc1.getAccVaule) == 0) {
      System.out.println("update witness success,ok")
    }
  }

  def test_delete_bad_witness:Unit= {
    var root_acc = new Accumulator(null, null, hash_tool)
    val root1 = root_acc.add(block1(0).prime)
    val root2 = root1.addAndProof(block1(1).prime)

    val root1_ = root_acc.add(block1(0).prime)
    val root2_ = root_acc.addAndProof(block1(2).prime)

    val acc = root2.acc.deleteWithWitness(block1(2).prime,root2_.proof.witness)
    if(acc == null){
      System.out.println("delete failed,ok")
    }
  }

  def test_delete:Unit={
    var root_acc = new Accumulator(null, null, hash_tool)
    val root1 = root_acc.add(block1(0).prime)
    val root2 = root1.add(block1(1).prime)

    val root_1 = root_acc.add(block1(0).tx)
    val root_2 = root_1.add(block1(1).tx)

    val r_proof = root2.addAndProof(block1(2).prime)

    val r_proof_ = root_2.addAndProof(block1(2).tx)

    val del1 = r_proof.acc.deleteWithWitness(block1(2).prime,r_proof.proof.witness)
    val del_1 = r_proof_.acc.deleteWithWitness(block1(2).tx,r_proof_.proof.witness)

    if(del1.getAccVaule.compareTo(root2.getAccVaule)== 0){
      System.out.println("BigInteger delete,result equal")
    }

    if (del_1.getAccVaule.compareTo(root_2.getAccVaule) == 0) {
      System.out.println("Array[Byte] delete,result equal")
    }

    if(!root2.verifyMembershipProof(block1(2).prime,r_proof.proof)){
      System.out.println("BigInteger delete,verify ok")
    }

    if (!root_2.verifyMembershipProof(block1(2).tx, r_proof_.proof)) {
      System.out.println("Array[Byte] delete,verify ok")
    }
  }

  def test_add:Unit= {
    var root_acc = new Accumulator(null, null, hash_tool)
    val root1 = root_acc.add(block1(0).prime)
    val root2 = root1.add(block1(1).prime)

    val root_1 = root_acc.add(block1(0).tx)
    val root_2 = root_1.add(block1(1).tx)

    val trace = new Array[BigInteger](2)
    trace(0) = block1(2).prime
    trace(1) = block1(3).prime

    val trace_ = new Array[Array[Byte]](2)
    trace_(0) = block1(2).tx
    trace_(1) = block1(3).tx

    val r_proof = root2.addAndProof(trace)
    val root3 = r_proof.acc
    val proof = r_proof.proof

    val r_proof_ = root_2.addAndProof(trace_)
    val root3_ = r_proof_.acc
    val proof_ = r_proof_.proof

    val primes = new Array[BigInteger](4)
    primes(0) = block1(0).prime
    primes(1) = block1(1).prime
    primes(2) = block1(2).prime
    primes(3) = block1(3).prime
    val exp = root3.product(primes)

    val primes_ = new Array[Array[Byte]](4)
    primes_(0) = block1(0).tx
    primes_(1) = block1(1).tx
    primes_(2) = block1(2).tx
    primes_(3) = block1(3).tx
    val exp_ = root3_.product(primes)

    if(root3.getAccVaule.compareTo(Rsa2048.exp(root3.getAccBase,exp))==0){
      System.out.println("BigInteger,result equal")
    }
    if(root3.verifyMembershipProofOfBatch(trace,proof)){
      System.out.println("BigInteger,verify ok")
    }

    if (root3_.getAccVaule.compareTo(Rsa2048.exp(root3_.getAccBase, exp_)) == 0) {
      System.out.println("Array[Byte],result equal")
    }
    if (root3_.verifyMembershipProofOfBatch(trace_, proof_)) {
      System.out.println("Array[Byte],verify ok")
    }

    if(r_proof.acc.getAccVaule.compareTo(r_proof_.acc.getAccVaule) == 0){
      System.out.println("Array[Byte];Biginteger,verify ok")
    }
  }

  def testAccumulatorTransaction:Unit={

    var root_acc = new Accumulator(null, null, hash_tool)

    val start = System.currentTimeMillis()
    val blk_root1 = getBlockAcc(block1, root_acc)
    root_acc = blk_root1

    val blk_root2 = getBlockAcc(block2, root_acc)
    root_acc = blk_root2

    val blk_root3 = getBlockAcc(block3, root_acc)
    root_acc = blk_root3
    System.out.println( s"create 3 blocks,time=${System.currentTimeMillis()-start}ms")
    System.out.println("acc1:" + root_acc.getAccVaule + s",length=${blk_root1.getAccVaule.bitLength()}")
    System.out.println("acc2:" + root_acc.getAccVaule + s",length=${blk_root2.getAccVaule.bitLength()}")
    System.out.println("acc3:" + root_acc.getAccVaule + s",length=${blk_root3.getAccVaule.bitLength()}")

  }

  def getTransaction(count:Int):Array[tx_data]={
    val r = new Array[tx_data](count)
    for(i<-0 to count-1){
      r(i) = tx_service.readTx
    }
    r
  }

  def getBlockAcc(txs:Array[tx_data],acc:Accumulator):Accumulator={
    val t : Array[BigInteger] = new Array[BigInteger](txs.length)
    for(i<-0 to t.length-1){
      t(i) = txs(i).prime
    }
    acc.addOfBatch(t)
  }

}
