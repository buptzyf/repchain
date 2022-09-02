package rep.accumulator


import rep.crypto.Sha256
import java.math.BigInteger


object Poke2 {
  case class proofStruct(z:BigInteger,Q:BigInteger,r:BigInteger)

  def prove(base:BigInteger, exp:BigInteger, result:BigInteger,bitLength:Int,hash:Sha256):proofStruct={
    val g = PrimeTool.getPrimeOfRandom(bitLength)
    val z = Rsa2048.exp(g,exp)
    val l = PrimeTool.hash2Prime((base.toString()+result.toString+z.toString()).getBytes(),bitLength,hash)
    val alpha = new BigInteger(hash.hash((base.toString()+result.toString+z.toString()+l.toString()).getBytes()))
    val div = Rsa2048.divideAndRemainder(exp,l)
    val q = div._1
    val r = div._2
    val Q = Rsa2048.exp(Rsa2048.op(base,Rsa2048.exp(g,alpha)),q)
    proofStruct(z,Q,r)
  }

  def verify(base:BigInteger, result:BigInteger, proof:proofStruct,bitLength:Int,hash:Sha256):Boolean={
    val g = PrimeTool.getPrimeOfRandom(bitLength)
    val l = PrimeTool.hash2Prime((base.toString() + result.toString + proof.z.toString()).getBytes(), bitLength, hash)
    val alpha = new BigInteger(hash.hash((base.toString() + result.toString + proof.z.toString() + l.toString()).getBytes()))
    val lhs = Rsa2048.op(Rsa2048.exp(proof.Q,l),Rsa2048.exp(Rsa2048.op(base,Rsa2048.exp(g,alpha)),proof.r))
    val rhs = Rsa2048.op(result,Rsa2048.exp(proof.z,alpha))
    lhs.compareTo(rhs)==0
  }
}
