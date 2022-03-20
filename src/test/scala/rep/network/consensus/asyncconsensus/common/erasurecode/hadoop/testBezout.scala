package rep.network.consensus.asyncconsensus.common.erasurecode.hadoop

import java.math.BigInteger
import java.util.Random

import rep.accumulator.{Accumulator4RepChain, AccumulatorCommonAlgorithms}
import rep.crypto.Sha256

object testBezout extends App {
  private def generateBigData(num: Int, isUseDefault: Boolean) = {
    var str = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    if (!isUseDefault) {
      val random = new Random
      val sb = new StringBuffer
      for (i <- 0 until num) {
        val number = random.nextInt(62)
        sb.append(str.charAt(number))
      }
      str = sb.toString
    }
    str
  }

  for (i <- 0 until 10) {
    val start = System.currentTimeMillis()
    val str1 = generateBigData(256, false)
    val b1 = new BigInteger(Sha256.hash(str1.getBytes())).abs()
    //val b1 = new BigInteger("5")

    val str2 = generateBigData(256, false)
    val b2 = new BigInteger(Sha256.hash(str2.getBytes())).abs()
    //val b2 = new BigInteger("177")

    //val result = Accumulator2.Bezout(b1,b2)
    val result = AccumulatorCommonAlgorithms.BezoutInExtendedEuclidean(b1, b2)
    val end = System.currentTimeMillis()
    var ax: BigInteger = result.coefficient_a
    var by: BigInteger = result.coefficient_b
    if (result.sign_a) {
      ax = ax.negate()
    }
    if (result.sign_b) {
      by = by.negate()
    }
    System.out.println(s"ii=${i},gcd=${b1.gcd(b2)},gcd1=${result.gcd};;;;a(${b1})*x(${ax})+b(${b2})*y(${by})=gcd(${result.gcd}),sign_a=${result.sign_a},sign_b=${result.sign_b}")
    if(result.gcd.compareTo(BigInteger.ONE) == 0){
      System.out.println(s"ax+by=${b1.multiply(ax).add(b2.multiply(by)).toString()}")
    }else{
      System.err.println(s"the i=${i} times,gcd=${result.gcd}")
    }

    System.out.println(s"the i=${i} times,spent time=${(end-start)}ms")
  }
}
