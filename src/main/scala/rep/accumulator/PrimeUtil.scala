package rep.accumulator

import java.math.BigInteger
import java.util.Random

import rep.crypto.Sha256

import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks._

class PrimeUtil(hashTool:Sha256) {
  private val PRIME_CONST = 5

  def generateLargePrime(bitLength: Int): BigInteger = {
    val random = new Random
    BigInteger.probablePrime(bitLength, random)
  }

  def generateTwoLargeDistinctPrimes(bitLength: Int): (BigInteger, BigInteger) = {
    var rs = Tuple2(BigInteger.ZERO, BigInteger.ZERO)
    val first = generateLargePrime(bitLength)
    breakable(
      while (true) {
        val second = generateLargePrime(bitLength)
        if (first.compareTo(second) != 0) {
          rs = Tuple2(first, second)
          break
        }
      }
    )
    rs
  }

  def hashToPrime(x: BigInteger, bitLength: Int): (BigInteger, BigInteger) = hashToPrime(x, bitLength, BigInteger.ZERO)

  def hashToPrime(x: BigInteger): (BigInteger, BigInteger) = hashToPrime(x, 120, BigInteger.ZERO)

  def hashToPrimes(xs: Array[BigInteger]): ArrayBuffer[BigInteger]={
    var result = new ArrayBuffer[BigInteger]()
    xs.foreach(x=>{
      val temp = hashToPrime(x)
      result.append(temp._1)
    })
    result
  }

  def hashToPrime(x: BigInteger, bitLength: Int, initNonce: BigInteger): (BigInteger, BigInteger) = {
    var rs = Tuple2(BigInteger.ZERO, BigInteger.ZERO)
    var nonce = initNonce
    breakable(
      while (true) {
        val num = hashToLength(x.add(nonce), bitLength)
        if (num.isProbablePrime(PRIME_CONST)) {
          rs = Tuple2(num, nonce)
          break
        }
        nonce = nonce.add(BigInteger.ONE)
      }
    )
    rs
  }


  def hashToLength(x: BigInteger, bitLength: Int): BigInteger = {
    var randomHexString = new StringBuilder
    val numOfBlocks = Math.ceil(bitLength / 256.00).toInt
    for (i <- 0 until numOfBlocks) {
      val bigIntI = new BigInteger(Integer.toString(i))
      randomHexString.append(hashTool.hashstr(x.add(bigIntI).toString(10).getBytes()))
    }
    if (bitLength % 256 > 0) randomHexString = new StringBuilder(randomHexString.substring((bitLength % 256) / 4))
    new BigInteger(randomHexString.toString, 16)
  }


}
