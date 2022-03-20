package rep.network.consensus.asyncconsensus.common.erasurecode.hadoop

import java.math.BigInteger

import rep.accumulator.PrimeUtil.{PRIME_CONST, hashToLength}

import scala.util.control.Breaks.{break, breakable}

object testPrime extends App {
  def hashToPrimeForRabin(x: BigInteger, bitLength: Int, initNonce: BigInteger): (BigInteger, BigInteger) = {
    var rs = Tuple2(BigInteger.ZERO, BigInteger.ZERO)
    var nonce = initNonce
    breakable(
      while (true) {
        val num = hashToLength(x.add(nonce), bitLength)
        if (num.isProbablePrime(5)) {
          rs = Tuple2(num, nonce)
          break
        }
        nonce = nonce.add(BigInteger.ONE)
      }
    )
    rs
  }


}
