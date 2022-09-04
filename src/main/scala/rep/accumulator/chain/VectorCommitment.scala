package rep.accumulator.chain

import rep.accumulator.{Accumulator, PrimeTool}
import rep.accumulator.Accumulator.Witness
import rep.accumulator.chain.VectorCommitment.{BlockVectorCommitment, TxAndPrime, TxWitness, TxWitnessesOfBlock}
import rep.app.system.RepChainSystemContext
import rep.proto.rc2.{Block, Transaction}

import java.math.BigInteger

object VectorCommitment{
  case class TxAndPrime(tid:String,prime:BigInteger)
  case class TxWitness(tid:String,prime:BigInteger,witnessInBlock: Witness)
  case class TxWitnessesOfBlock(h:Long,tx_acc_value:BigInteger,txWitnesses: Array[TxWitness])
  case class TxVectorCommitment()
  case class StateVectorCommitment()
  case class BlockVectorCommitment(h:Long,acc_value:BigInteger,tx_wt:Array[TxWitness])
}

class VectorCommitment(ctx:RepChainSystemContext) {

  def getTransactionWitnesses(block:Block,tx_acc_value:BigInteger,isCreateWitness:Boolean=false):TxWitnessesOfBlock={
    var r : TxWitnessesOfBlock = null
    if(block != null){
      val txs = block.transactions.toArray
      val tps = new Array[TxAndPrime](txs.length)
      for(i<-0 to tps.length-1){
        tps(i) = TxAndPrime(txs(i).id,PrimeTool.hash2Prime(txs(i).toByteArray,Accumulator.bitLength,ctx.getHashTool))
      }
      r = getTransactionWitnesses(tps,block.getHeader.height,tx_acc_value)
    }
    r
  }

  def getTransactionWitnesses(txs:Array[TxAndPrime],height:Long,tx_acc_value:BigInteger,isCreateWitness:Boolean=false):TxWitnessesOfBlock={
    var r : TxWitnessesOfBlock = null
    if(txs != null && txs.length > 0){
      val old_acc = new Accumulator(ctx.getTxAccBase, tx_acc_value, ctx.getHashTool)
      val tx_primes = for(tx<-txs) yield {
        tx.prime
      }
      val new_acc = old_acc.addOfBatch(tx_primes)
      val t_wts = if(isCreateWitness) new Array[TxWitness](tx_primes.length) else null
      if(isCreateWitness){
        val wts = old_acc.compute_individual_witnesses(tx_primes)
        for (i <- 0 to wts.length - 1) {
          t_wts(i) = TxWitness(txs(i).tid, wts(i)._1, wts(i)._2)
        }
      }
      r = TxWitnessesOfBlock(height, new_acc.getAccVaule, t_wts)
    }
    r
  }


}
