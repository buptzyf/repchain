package rep.accumulator

import java.math.BigInteger

import rep.crypto.Sha256

import scala.collection.mutable.{ArrayBuffer, HashMap}


class Accumulator4RepChain {


  private var N: BigInteger = null
  private var A: BigInteger = null
  private var multiplyOfPower: BigInteger = null
  private var A0: BigInteger = null
  private var random: EnhancedRandom = null
  private var data: HashMap[BigInteger, BigInteger] = new HashMap[BigInteger, BigInteger]()

  Init

  def Init = {
    val bigIntegerPair = PrimeUtil.generateTwoLargeDistinctPrimes(AccumulatorCommonAlgorithms.RSA_PRIME_SIZE)
    val p = bigIntegerPair._1
    val q = bigIntegerPair._2
    N = p.multiply(q)
    random = new EnhancedRandom
    A0 = random.nextBigInteger(BigInteger.ZERO, N)
    A = A0
    multiplyOfPower = BigInteger.ONE
  }

  def getN: BigInteger = N

  def getNonce(x: BigInteger): BigInteger = data.get(x).get

  def getMultiplyOfPower: BigInteger = this.multiplyOfPower

  def add(x: BigInteger): BigInteger = {
    if (data.contains(x)) {
      A
    } else {
      val bigIntegerPair = PrimeUtil.hashToPrime(x, AccumulatorCommonAlgorithms.ACCUMULATED_PRIME_SIZE)
      val hashPrime = bigIntegerPair._1
      val nonce = bigIntegerPair._2
      A = A.modPow(hashPrime, N)
      data += x -> nonce
      this.multiplyOfPower = this.multiplyOfPower.multiply(hashPrime)
      A
    }
  }

  def proveMembership(x: BigInteger): BigInteger = {
    if (!data.contains(x)) {
      null
    } else {
      val nonce = data.get(x).get
      val product = this.multiplyOfPower.divide(PrimeUtil.hashToPrime(x, AccumulatorCommonAlgorithms.ACCUMULATED_PRIME_SIZE, nonce)._1)
      A0.modPow(product, N)
    }
  }


  /*def proveMembership(x: BigInteger): BigInteger = {
    if (!data.contains(x)) {
      null
    } else {
      val product = iterateAndGetProduct(x)
      A0.modPow(product, N)
    }
  }*/

  /*private def iterateAndGetProduct(x: BigInteger) = {
    var product = BigInteger.ONE
    data.keySet.foreach(k => {
      if (k.compareTo(x) != 0) {
        val nonce = data.get(k).get
        product = product.multiply(
          PrimeUtil.hashToPrime(k, ACCUMULATED_PRIME_SIZE, nonce)._1
        )
      }
    })

    product
  }*/


  def delete(x: BigInteger): BigInteger = {
    if (!data.contains(x)) {
      A
    }
    else {
      val nonce = data.get(x).get
      data.remove(x)
      this.multiplyOfPower = this.multiplyOfPower.divide(PrimeUtil.hashToPrime(x, AccumulatorCommonAlgorithms.ACCUMULATED_PRIME_SIZE, nonce)._1)
      this.A = A0.modPow(this.multiplyOfPower, N)
      A
    }
  }


  private def doVerifyMembership(A: BigInteger, x: BigInteger, proof: BigInteger, n: BigInteger) = {
    proof.modPow(x, n).compareTo(A) == 0
  }


  def verifyMembership(A: BigInteger, x: BigInteger, nonce: BigInteger, proof: BigInteger, n: BigInteger): Boolean =
    doVerifyMembership(A, PrimeUtil.hashToPrime(x, AccumulatorCommonAlgorithms.ACCUMULATED_PRIME_SIZE, nonce)._1, proof, n)
}