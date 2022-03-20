package rep.network.consensus.asyncconsensus.common.erasurecode.hadoop

import java.math.BigInteger
import java.util.Random

import rep.accumulator.{Accumulator1, Accumulator4RepChain, PrimeUtil}
import rep.crypto.Sha256

object testAccumulator extends App {

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

  val start = System.currentTimeMillis
  val bigInteger = PrimeUtil.generateLargePrime(3752 / 2)
  val end = System.currentTimeMillis
  System.out.println("generate prime,spent time =" + (end - start) + "(ms),prime=" + bigInteger.toString)

  val len = 10000 //1000000;
  val hashValue = new Array[BigInteger](len)
  for (i <- 0 until len) {
    val str = generateBigData(256, false)
    val b = new BigInteger(Sha256.hash(str.getBytes()))
    hashValue(i) = b
  }

  System.out.println("---generate transaction finish")

  val accu1 = new Accumulator4RepChain
  var total = BigInteger.ZERO
  val start1 = System.currentTimeMillis
  for (i <- 0 until len) {
    total = accu1.add(hashValue(i))
    if(i % 1000 == 0){
      System.out.println("Add transaction hash count="+i+"")
    }
  }
  val end1 = System.currentTimeMillis
  System.out.println("accumulator add " + len + " times,spent time=" + (end1 - start1) + "ms")
  System.out.println("accumulator,total len=" + total.bitLength + ", total value" + total.toString + "")
  System.out.println("accumulator,power len=" + accu1.getMultiplyOfPower.bitLength + ", pow value" + accu1.getMultiplyOfPower.toString + "")

  val start2 = System.currentTimeMillis
  for (i <- 0 until 10) {
    val str = generateBigData(256, false)
    val b = new BigInteger(Sha256.hash(str.getBytes()))
    total = accu1.add(hashValue(i))
  }
  val end2 = System.currentTimeMillis
  System.out.println("accumulator base  " + len + ",add 10,spent time=" + (end2 - start2) + "ms")

  val r = new Random
  for (i <- 0 until 10) {
    val start3 = System.currentTimeMillis
    val rs = r.nextInt(len)
    val b = hashValue(rs)
    val witness1 = accu1.proveMembership(b)
    val end3 = System.currentTimeMillis
    System.out.println("accumulator get prove, " + "spent time=" + (end3 - start3) + "ms")
    val start4 = System.currentTimeMillis
    val nonce1 = accu1.getNonce(b)
    val n1 = accu1.getN
    val vr = accu1.verifyMembership(total, b, nonce1, witness1, n1)
    val end4 = System.currentTimeMillis
    System.out.println("accumulator verify prove,verify result=" + vr.toString + ",spent time=" + (end4 - start4) + "ms")
  }

  val start5 = System.currentTimeMillis
  val rs = r.nextInt(len)
  val b = hashValue(rs)
  total = accu1.delete(b)
  val end5 = System.currentTimeMillis
  System.out.println("accumulator delete spent time=" + (end5 - start5) + "ms")


}
