package rep.accumulator

import java.math.BigInteger
import scala.collection.mutable.HashMap


class Accumulator1 {
  private val RSA_KEY_SIZE = 3072
  private val RSA_PRIME_SIZE = RSA_KEY_SIZE / 2
  private val ACCUMULATED_PRIME_SIZE = 128

  private var N: BigInteger = null
  private var A: BigInteger = null
  private var A0: BigInteger = null
  private var random: EnhancedRandom = null
  private var data: HashMap[BigInteger, BigInteger] = new HashMap[BigInteger, BigInteger]()

  Init

  def Init = {
    val bigIntegerPair = PrimeUtil.generateTwoLargeDistinctPrimes(RSA_PRIME_SIZE)
    val p = bigIntegerPair._1
    val q = bigIntegerPair._2
    N = p.multiply(q)
    random = new EnhancedRandom
    A0 = random.nextBigInteger(BigInteger.ZERO, N)
    A = A0
  }

  def getN: BigInteger = N

  def getNonce(x: BigInteger): BigInteger = data.get(x).get

  def add(x: BigInteger): BigInteger = {
    if (data.contains(x)) {
      A
    } else {
      val bigIntegerPair = PrimeUtil.hashToPrime(x, ACCUMULATED_PRIME_SIZE)
      val hashPrime = bigIntegerPair._1
      val nonce = bigIntegerPair._2
      A = A.modPow(hashPrime, N)
      data += x -> nonce
      A
    }
  }

  def proveMembership(x: BigInteger): BigInteger = {
    if (!data.contains(x)) {
      null
    } else {
      val product = iterateAndGetProduct(x)
      A0.modPow(product, N)
    }
  }

  private def iterateAndGetProduct(x: BigInteger) = {
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
  }


  def delete(x: BigInteger): BigInteger = {
    if (!data.contains(x)) {
      A
    }
    else {
      data.remove(x)
      val product = iterateAndGetProduct(x)
      this.A = A0.modPow(product, N)
      A
    }
  }


  private def doVerifyMembership(A: BigInteger, x: BigInteger, proof: BigInteger, n: BigInteger) = {
    proof.modPow(x, n).compareTo(A) == 0
  }


  def verifyMembership(A: BigInteger, x: BigInteger, nonce: BigInteger, proof: BigInteger, n: BigInteger): Boolean =
    doVerifyMembership(A, PrimeUtil.hashToPrime(x, ACCUMULATED_PRIME_SIZE, nonce)._1, proof, n)
}

