package rep.accumulator

import java.math.BigInteger

object Rsa2048 {
  private val RSA2048_MODULUS_STR = "251959084756578934940271832400483985714292821262040320277771378360436620207075955562640185258807"+
                                "8440691829064124951508218929855914917618450280848912007284499268739280728777673597141834727026189"+
                                "6375014971824691165077613379859095700097330459748808428401797429100642458691817195118746121515172"+
                                "6546322822168699875491824224336372590851418654620435767984233871847744479207399342365848238242811"+
                                "9816381501067481045166037730605620161967625613384414360383390441495263443219011465754445417842402"+
                                "0924616515723350778707749817125772467962926386356373289912154831438167899885040445364023527381951"+
                                "378636564391212010397122822120720357"
  private val RSA2048_MODULUS = new BigInteger(RSA2048_MODULUS_STR,10)
  private val Half_MODULUS = RSA2048_MODULUS.divide(new BigInteger("2"))

  def getRSAModule:BigInteger={
    RSA2048_MODULUS
  }

  def getHalfModulus:BigInteger={
    Half_MODULUS
  }
  def exp(x:BigInteger,n:BigInteger):BigInteger={
    x.modPow(n,RSA2048_MODULUS)
  }

  def inv(x:BigInteger):BigInteger={
    x.modInverse(RSA2048_MODULUS)
  }

  def op(a: BigInteger, b: BigInteger): BigInteger = {
    a.multiply(b).mod(RSA2048_MODULUS)
  }

  def mul(a:BigInteger,b:BigInteger):BigInteger={
    a.multiply(b)
  }

  def div(a:BigInteger,b:BigInteger):BigInteger={
    a.divide(b)
  }

  def mod(a:BigInteger,b:BigInteger):BigInteger={
    a.mod(b)
  }

  def divideAndRemainder(a:BigInteger,b:BigInteger):(BigInteger,BigInteger)={
    val r = a.divideAndRemainder(b)
    (r(0),r(1))
  }
  def main(args:Array[String]):Unit={
    testOp
    testExp
    testInv
  }

  private def testOp={
    val a = op(new BigInteger("2"),new BigInteger("3"))
    assert(a == BigInt(6))
    System.out.println("test op(2*3) passed")
    val b = op(new BigInteger("-2"),new BigInteger("-3"))
    assert(a == BigInt(6))
    System.out.println("test op(-2*-3) passed")
  }

  private def testExp={
    val a = exp(new BigInteger("2"),new BigInteger("3"))
    assert(a == BigInt("8"))
    System.out.println("test exp(2^3) passed")
    val b = exp(new BigInteger("2"),new BigInteger("4096"))
    assert(b == BigInt("21720738995539542858936915878186921869751915989840152165899303861582487" +
                        "240810878492659751749672737203717627738047648700009977053044057502917091" +
                        "973287111671693426065546612150833232954361536709981055037121764270784874720971933716065" +
                        "574032615073613728454497477072129686538873330572773963696018637078230885896090312654536801" +
                        "52037285312247125429494632830592984498231941638420413405655184014591668587095150788789512935641" +
                        "470442274871421711388048970393414761255193808250175305529680182970301726073143987111021561898850" +
                        "9545129088484396848644805730347466581515692959313583208325725034506693916571047785061884094866050395109710")
    )
    System.out.println("test exp(2^4096) passed")
  }

  private def testInv={
    val x = new BigInteger("2")
    val inv1 = inv(new BigInteger("2"))
    assert(op(x,inv1) == new BigInteger("1"))
    System.out.println("test inv(2^-1 mod module) passed")
  }
}
