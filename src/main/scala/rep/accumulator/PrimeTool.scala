package rep.accumulator

import java.math.BigInteger
import java.util.Random

import rep.crypto.Sha256

import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks._

object PrimeTool {
  private val PRIME_CONST = 5

  def getPrimeOfRandom(bitLength: Int): BigInt = {
    val random = new Random
    BigInt.probablePrime(bitLength, random)
  }

  def getPrimeOfRandom(bitLenght:Int,max:BigInt):BigInt = {
    var p = getPrimeOfRandom(bitLenght)
    while(p.compare(max) >= 0 ){
      p = getPrimeOfRandom(bitLenght)
    }
    p
  }

  def hash2Prime(src:Array[Byte],bitLength:Int,hashTool:Sha256):BigInteger={
    var prime = new BigInteger(getPrimeString(src, bitLength, hashTool), 16)
    while (!prime.isProbablePrime(PRIME_CONST)) {
      prime = new BigInteger(getPrimeString(prime.toString(10).getBytes(), bitLength, hashTool), 16)
    }
    prime
  }

  def hash2Primes(src: Array[Array[Byte]], bitLength: Int, hashTool: Sha256): ArrayBuffer[BigInteger] = {
    var result = new ArrayBuffer[BigInteger]()
    src.foreach(sb => {
      result.append(hash2Prime(sb,bitLength,hashTool))
    })
    result
  }

  private def getPrimeString(src:Array[Byte],bitLength:Int,hashTool:Sha256):String={
    var randomHexString = new StringBuilder
    val x = new BigInteger(hashTool.hashstr(src),16)
    val numOfBlocks = Math.ceil(bitLength / 256.00).toInt
    var count = 0
    for (i <- 0 until numOfBlocks) {
      count += 1
      randomHexString.append(hashTool.hashstr(x.add(BigInteger.valueOf(count)).toString(10).getBytes()))
    }
    if (bitLength % 256 > 0) randomHexString = new StringBuilder(randomHexString.substring((bitLength % 256) / 4))
    randomHexString.toString
  }


}
