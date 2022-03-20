package rep.accumulator

import java.math.BigInteger
import java.util.Random
import scala.util.control.Breaks._

class EnhancedRandom {
  private val random = new Random()

  def nextBigInteger: BigInteger = nextBigInteger(BigInteger.valueOf(2).pow(256))

  def nextBigInteger(until: BigInteger): BigInteger = nextBigInteger(BigInteger.ZERO, until)

  def nextBigInteger(from: BigInteger, until: BigInteger): BigInteger = {
    if (from.compareTo(until) >= 0) throw new IllegalArgumentException("until must be greater than from")
    var randomNumber: BigInteger = null
    var bitLength = 0
    if (from.bitLength == until.bitLength) {
      val fromBitLength = from.bitLength
      bitLength = fromBitLength
    } else {
      val fromBitLength = from.bitLength
      val untilBitLength = until.bitLength
      bitLength = nextInt(fromBitLength, untilBitLength)
    }

    randomNumber = new BigInteger(bitLength, this.random)
    breakable(
      while (true) {
        if ((randomNumber.compareTo(from) < 0) || (randomNumber.compareTo(until) >= 0)) {
          randomNumber = new BigInteger(bitLength, this.random)
        } else {
          break
        }
      }
    )
    randomNumber
  }

  def nextInt(from: Int, until: Int): Int = {
    random.nextInt(until - from) + from
  }
}
