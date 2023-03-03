package rep.accumulator

import rep.crypto.Sha256
import java.math.BigInteger


object Poe {
  def prove(base:BigInteger, exp: BigInteger, result:BigInteger,bitLength:Int,hash:Sha256):BigInteger={
    val l = PrimeTool.hash2Prime((base.toString()+exp.toString+result.toString).getBytes(),bitLength,hash)
    val q = Rsa2048.div(exp,l)
    Rsa2048.exp(base,q)
  }

  def verify(base:BigInteger, exp:BigInteger, result: BigInteger, proof:BigInteger,bitLength:Int,hash:Sha256):Boolean={
    val l = PrimeTool.hash2Prime((base.toString() + exp.toString + result.toString).getBytes(), bitLength, hash)
    val r = Rsa2048.mod(exp,l)
    val w = Rsa2048.op(Rsa2048.exp(proof,l),Rsa2048.exp(base,r))
    w.compareTo(result) == 0
  }

}
