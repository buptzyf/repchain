package rep.accumulator


import java.math.BigInteger
import scala.util.control.Breaks.{break, breakable}
import rep.crypto.Sha256



case class membershipProof(witness:Accumulator,proof:BigInt)
case class accumulationAndCommitment(acc:Accumulator,commitment:membershipProof)
case class accumulationAndPrimeProduct(acc:Accumulator,primeProduct:BigInt)
case class waitingForDeletion(element:String,witness:Accumulator)
case class BezoutStruct(gcd:BigInt,coefficient_a:BigInt,coefficient_b:BigInt,
                        sign_a:Boolean,sign_b:Boolean)


class Accumulator(acc_value:BigInt) {
  private val acc : BigInt = acc_value
  def this()={
    this(BigInt("2"))
  }

  def value:BigInt={
    this.acc
  }

  def add(elements:Array[String]):accumulationAndPrimeProduct={
    val x = Accumulator.hashToPrimeProduct(elements)
    new accumulationAndPrimeProduct(new Accumulator(Rsa2048.exp(this.value,x)),x)
  }

  def addAndProof(elements:Array[String]):accumulationAndCommitment={
    val accAndProduct = this.add(elements)
    val proof = Accumulator.ProofOfPOE(this.value,accAndProduct.primeProduct,accAndProduct.acc.value)
    new accumulationAndCommitment(accAndProduct.acc,new membershipProof(this,proof))
  }

  def delete(wds:Array[waitingForDeletion]):accumulationAndPrimeProduct={
    var prime_witnesses : Array[Tuple2[BigInt,BigInt]] = new Array[Tuple2[BigInt,BigInt]](wds.length)
    var count = 0
    wds.foreach(e=>{
      prime_witnesses(count) = new Tuple2(Accumulator.hashToPrime(e.element),e.witness.value)
    })


    prime_witnesses.foreach(pw=>{
      if(!Rsa2048.exp(pw._2,pw._1).equals(this.value)){
        throw new Exception(Accumulator.BadWitness)
      }
    })



  }



  /*
fn delete_(self, elem_witnesses: &[(T, Witness<G, T>)]) -> Result<(Self, Integer), AccError> {
    let prime_witnesses = elem_witnesses
      .iter()
      .map(|(elem, witness)| (hash_to_prime(elem), witness.0.value.clone()))
    .collect::<Vec<_>>();

    for (p, witness_elem) in &prime_witnesses {
      if G::exp(&witness_elem, &p) != self.value {
        return Err(AccError::BadWitness);
      }
    }

    let (prime_product, acc_elem) = divide_and_conquer(
      |(p1, v1), (p2, v2)| Ok((int(p1 * p2), shamir_trick::<G>(&v1, &v2, p1, p2).unwrap())),
      (int(1), self.value),
      &prime_witnesses[..],
    )?;

    Ok((
      Self {
        phantom: PhantomData,
        value: acc_elem.clone(),
      },
      prime_product,
    ))
  }
  * */

}

object Accumulator{
  private val PRIME_CONST = 5

  private val BadWitness = "错误的见证值"
  private val BadWitnessUpdate = "更新时出现错误的见证值"
  private val DivisionByZero = "除数为零错误"
  private val InexactDivision = "不精确的除法"
  private val InputsNotCoprime = "输入的参数不是互质"


def hashToPrimeProduct(elements: Array[String]): BigInt={
    var result = BigInt("1")
    elements.foreach(x=>{
      val temp = hashToPrime(x)
      result = Rsa2048.mul(result,temp)
    })
    result
  }

  def hashToPrime(element:String): BigInt = {
    var x:BigInt = BigInt(Sha256.hash(element.getBytes()))
    var rs : BigInt = x.clone().asInstanceOf[BigInt]
    var nonce = BigInt("0")
    breakable(
      while (true) {
        val num = hashToLength(x + nonce, 256)
        if (num.isProbablePrime(Accumulator.PRIME_CONST)) {
          rs = num
          break
        }
        nonce = nonce + 1
      }
    )
    rs
  }

  private def hashToLength(x: BigInt, bitLength: Int): BigInt = {
    var randomHexString = new StringBuilder
    val numOfBlocks = Math.ceil(bitLength / 256.00).toInt
    for (i <- 0 until numOfBlocks) {
      val bi = BigInt(i)
      randomHexString.append(Sha256.hashstr((x + bi).toString(10).getBytes()))
    }
    if (bitLength % 256 > 0) randomHexString = new StringBuilder(randomHexString.substring((bitLength % 256) / 4))
    BigInt(randomHexString.toString, 16)
  }

  /**
   * @author jiangbuyun
   * @version 0.1
   * @since 2022-02-5
   * @category 生成POE协议的证明。
   * @Input u 基数，x 指数，w 幂
   * @Output 返回证明，即大整数
   */
  def ProofOfPOE(u:BigInt,x:BigInt,w:BigInt):BigInt={
    val combineString = u.toString+"-"+x.toString+"-"+w.toString
    val l = hashToPrime(combineString)
    val q = Rsa2048.div(x,l)
    Rsa2048.exp(u,q)
  }

  /**
   * @author jiangbuyun
   * @version 0.1
   * @since 2022-02-5
   * @category 验证POE协议的证明是否正确。
   * @Input u 基数，x 指数，w 幂，proof 挑战数
   * @Output 返回验证结论，true验证正确，反之验证失败
   */
  def VerifyProofOfPOE(u:BigInt,x:BigInt,w:BigInt,proof:BigInt):Boolean={
    var result = false
    val combineString = u.toString+"-"+x.toString+"-"+w.toString
    val l = hashToPrime(combineString)
    val r = Rsa2048.mod(x,l)
    val lsh = Rsa2048.op(Rsa2048.exp(proof,l),Rsa2048.exp(u,r))
    if(lsh.compareTo(w) == 0) {result = true}
    result
  }
/*
  /**
   * @author jiangbuyun
   * @version 0.1
   * @since 2022-02-5
   * @category 求xth_root的b次方再乘以yth_root的a次方的积再取模
   * @Input xth_root, yth_root, x, y
   * @Output 根据贝祖定理得到a，b，再求xth_root的b次方再乘以yth_root的a次方的积再取模
   */
  def ShamirTrick(xth_root: BigInt, yth_root: BigInt, x: BigInt, y: BigInt) : Option[BigInt] = {
    var ret:Option[BigInt] = None
    if(Rsa2048.exp(xth_root,x).equals(Rsa2048.exp(yth_root,y))){
      var x1th_root = BigInt(xth_root.toString())
      var y1th_root = BigInt(yth_root.toString())
      val bezout = BezoutInExtendedEuclidean(x,y)
      if(bezout.gcd.compareTo(BigInteger.ONE) == 0){
        //x,y 互为素数
        if(bezout.sign_b){
          x1th_root = xth_root.modInverse(modules)//ModInverse(xth_root,modules)
        }
        if(bezout.sign_a){
          y1th_root = yth_root.modInverse(modules)//ModInverse(yth_root,modules)
        }
        val mod_x = x1th_root.modPow(bezout.coefficient_b,modules)
        val mod_y = y1th_root.modPow(bezout.coefficient_a,modules)
        ret = Some(mod_x.multiply(mod_y).mod(modules))
      }
    }
    ret
  }

  /**
   * @author jiangbuyun
   * @version 0.1
   * @since 2022-01-20
   * @category 该方法采用了扩展欧几里德算法计算贝祖恒等式的因子以及他们的公约数。
   * @Input a，b 输入两个参数，求a，b的因子和公约数
   * @Output 1  公约数
   *         2  因子x
   *         3  因子y
   *         4  因子x的符号
   *         5  因子y的符号
   */
  def BezoutInExtendedEuclidean(a: BigInt, b: BigInt) : BezoutStruct = {
    var s: BigInt = BigInteger.ZERO
    var old_s: BigInt = BigInteger.ONE
    var t: BigInt = BigInteger.ONE
    var old_t: BigInt = BigInteger.ZERO
    var r: BigInt = b
    var old_r: BigInt = a
    var prev_sign_s: Boolean = false
    var prev_sign_t: Boolean = false
    var sign_s: Boolean = false
    var sign_t: Boolean = false
    while (!r.equals(BigInteger.ZERO)) {
      val quotient = Rsa2048.div(old_r,r)
      var new_r = old_r.subtract( quotient.multiply(r));
      old_r = r;
      r = new_r;
      var new_s = quotient.multiply(s)
      if (prev_sign_s == sign_s && new_s.compareTo(old_s) == 1) {
        new_s = new_s.subtract(old_s)
        if (!sign_s) {
          sign_s = true
        } else {
          sign_s = false
        }
      } else if (prev_sign_s != sign_s) {
        new_s = old_s.add(new_s)
        prev_sign_s = sign_s;
        sign_s = !sign_s;
      } else {
        new_s = old_s.subtract(new_s)
      }
      old_s = s;
      s = new_s;

      var new_t = quotient.multiply(t);
      if (prev_sign_t == sign_t && new_t.compareTo(old_t) == 1) {
        new_t = new_t.subtract(old_t);
        if (!sign_t) {
          sign_t = true;
        }
        else {
          sign_t = false;
        }
      } else if (prev_sign_t != sign_t) {
        new_t = old_t.add(new_t)
        prev_sign_t = sign_t;
        sign_t = !sign_t;
      } else {
        new_t = old_t.subtract(new_t)
      }
      old_t = t;
      t = new_t;
    }
    new BezoutStruct(old_r, old_s, old_t, prev_sign_s, prev_sign_t)
  }*/

  def main(args : Array[String])={
    val a1 = new Accumulator
    val a2 = new Accumulator(BigInt("3"))
    System.out.println(s"a1=${a1.value},a2=${a2.value}")
  }
}
