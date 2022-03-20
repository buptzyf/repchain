package rep.accumulator

import java.math.BigInteger
import rep.crypto.Sha256
import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks.{break, breakable}

case class BezoutStruct(gcd:BigInteger,coefficient_a:BigInteger,coefficient_b:BigInteger,
                        sign_a:Boolean,sign_b:Boolean)
case class NonMemWitnessStruct(coefficient_a:BigInteger,sign_a:Boolean,wit_value:BigInteger)

case class PokeStruct(z:BigInteger,Q:BigInteger,r:BigInteger)

object AccumulatorCommonAlgorithms {
  val RSA_KEY_SIZE = 3072
  val RSA_PRIME_SIZE = RSA_KEY_SIZE / 2
  val ACCUMULATED_PRIME_SIZE = 128

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
  def BezoutInExtendedEuclidean(a: BigInteger, b: BigInteger) : BezoutStruct = {
    var s: BigInteger = BigInteger.ZERO
    var old_s: BigInteger = BigInteger.ONE
    var t: BigInteger = BigInteger.ONE
    var old_t: BigInteger = BigInteger.ZERO
    var r: BigInteger = b
    var old_r: BigInteger = a
    var prev_sign_s: Boolean = false
    var prev_sign_t: Boolean = false
    var sign_s: Boolean = false
    var sign_t: Boolean = false
    while (r.compareTo(BigInteger.ZERO) != 0) {
      val quotient = old_r.divide(r)
      var new_r = old_r.subtract(quotient.multiply(r));
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
  }

  /**
   * @author jiangbuyun
   * @version 0.1
   * @since 2022-02-5
   * @category 计算贝祖定理。
   * @Input a，b 输入两个参数，求a，b的因子和公约数
   * @Output 1  公约数
   *         2  因子x
   *         3  因子y
   *         4  因子x的符号
   *         5  因子y的符号
   */
  def Bezout(a: BigInteger, b: BigInteger):Option[BezoutStruct]={
    var result = BezoutInExtendedEuclidean(a, b);
    //检查a和b是否互为素数
    if(result.gcd.compareTo(BigInteger.ONE) == 0){
      Some(result)
    }else{
      None
    }
  }

  /**
   * @author jiangbuyun
   * @version 0.1
   * @since 2022-02-5
   * @category 已知y = g^x ,且x = x 1 x 2 . . . x n, 求y的xi − th root，若直接计算的话，需要的算法复杂度为 O(n^2)，若采用RootFactor算法，则复杂度降为O(nlog(n))：
   * @Input g，elems x的集合，modules 模数
   * @Output 返回对应的xi的root值
   */
  def RootFactor(g: BigInteger, elems: Array[BigInteger],modules:BigInteger) : ArrayBuffer[BigInteger]= {
    var ret = new ArrayBuffer[BigInteger]()
    if(elems.length == 1){
      ret.append(g)
    }else{
      val n_prime = elems.length/2
      var g_left = g
      for(i <- 0 to n_prime-1){
        g_left = g_left.modPow(elems(i),modules)
      }

      var g_right = g
      for(i <- n_prime to elems.length-1){
        g_right = g_right.modPow(elems(i),modules)
      }

      var left = RootFactor(g_right,elems.slice(0,n_prime),modules)
      var right = RootFactor(g_left,elems.slice(n_prime,elems.length),modules)
      left.appendAll(right)
      ret = left
    }
    ret
  }

  /**
   * @author jiangbuyun
   * @version 0.1
   * @since 2022-02-5
   * @category 求元素的逆模
   * @Input elems 待求的元素，modules 模数
   * @Output 返回elems的-1次方再对modules取模
   */
  def ModInverse(elem: BigInteger,modules:BigInteger) : BigInteger= {
    val bezout = BezoutInExtendedEuclidean(elem,modules)
    if(bezout.sign_a){
      var tmp = modules.subtract(bezout.coefficient_a)
      tmp.mod(modules)
    }else{
      bezout.coefficient_a.mod(modules)
    }
  }

  /**
   * @author jiangbuyun
   * @version 0.1
   * @since 2022-02-5
   * @category 幂模运算
   * @Input base1 底数，exp1 指数，modules 模数
   * @Output 返回base1的exp1次方再对modules取模
   */
  def ModExp(base1: BigInteger, exp1: BigInteger, modulus: BigInteger) : BigInteger= {
    var result = BigInteger.ONE
    var base = base1.mod(modulus)
    var exp = exp1
    breakable {
      while (exp.compareTo(BigInteger.ZERO) > 0) {
        if (exp.mod(BigInteger.TWO).compareTo(BigInteger.ONE) == 0) {
          result = MulMod(result, base, modulus)
        }
        if (exp.compareTo(BigInteger.ONE) == 0) {
          break
        }
        exp = exp.shiftRight(1)
        base = MulMod(base, base, modulus)
      }
    }
    result
  }

  /**
   * @author jiangbuyun
   * @version 0.1
   * @since 2022-02-5
   * @category 乘模运算
   * @Input a 乘数，b 另一个乘数，modules 模数
   * @Output 返回a*b的积再对modules取模
   */
  def MulMod(a: BigInteger, b: BigInteger, modulus: BigInteger) : BigInteger= {
    var result = BigInteger.ZERO
    var A = a.mod(modulus)
    var B = new BigInteger(b.toString())
    while(B.compareTo(BigInteger.ZERO) > 0) {
      if(B.mod(BigInteger.TWO).compareTo(BigInteger.ONE) == 0){
        result = result.add(A).mod(modulus)
      }
      A = A.multiply(BigInteger.TWO).mod(modulus)
      B = B.divide(BigInteger.TWO)
    }
    result.mod(modulus)
  }

  /**
   * @author jiangbuyun
   * @version 0.1
   * @since 2022-02-5
   * @category 求xth_root的b次方再乘以yth_root的a次方的积再取模
   * @Input xth_root, yth_root, x, y，modules 模数
   * @Output 根据贝祖定理得到a，b，再求xth_root的b次方再乘以yth_root的a次方的积再取模
   */
  def ShamirTrick(xth_root: BigInteger, yth_root: BigInteger, x: BigInteger, y: BigInteger,modules:BigInteger) : Option[BigInteger] = {
    var ret:Option[BigInteger] = None
    if(xth_root.modPow(x,modules).compareTo(yth_root.modPow(y,modules)) == 0){
      var x1th_root = new BigInteger(xth_root.toString())
      var y1th_root = new BigInteger(yth_root.toString())
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
   * @since 2022-02-5
   * @category 生成POE协议的证明。
   * @Input u 基数，x 指数，w 幂，modules 模数
   * @Output 返回证明，即大整数
   */
  def ProofOfPOE(u:BigInteger,x:BigInteger,w:BigInteger,modules:BigInteger):BigInteger={
    val combineString = u.toString+"-"+x.toString+"-"+w.toString
    val big1 = new BigInteger(Sha256.hashToBytes(combineString))
    val l = PrimeUtil.hashToPrime(big1)._1
    val q = x.divide(l)
    u.modPow(q,modules)
  }

  /**
   * @author jiangbuyun
   * @version 0.1
   * @since 2022-02-5
   * @category 验证POE协议的证明是否正确。
   * @Input u 基数，x 指数，w 幂，Q 挑战数，modules 模数
   * @Output 返回验证结论，true验证正确，反之验证失败
   */
  def VerifyProofOfPOE(u:BigInteger,x:BigInteger,w:BigInteger,Q:BigInteger,modules:BigInteger):Boolean={
    var result = false
    val combineString = u.toString+"-"+x.toString+"-"+w.toString
    val big1 = new BigInteger(Sha256.hashToBytes(combineString))
    val l = PrimeUtil.hashToPrime(big1)._1
    val r = x.mod(l)
    val t1 = Q.modPow(l,modules)
    val t2 = u.modPow(r,modules)
    val lsh = t1.multiply(t2).mod(modules)
    if(lsh.compareTo(w) == 0) {result = true}
    result
  }

  /**
   * @author jiangbuyun
   * @version 0.1
   * @since 2022-02-5
   * @category 生成POKE协议的证明。
   * @Input u 基数，x 指数，w 幂，modules 模数
   * @Output 返回证明
   *        z 基数
   *        Q 挑战数
   *        r 余数
   */
  def ProofOfPOKE(u:BigInteger,x:BigInteger,w:BigInteger,modules:BigInteger):PokeStruct={
    val z = BigInteger.TWO.modPow(x,modules)
    val combineString = u.toString+"-"+w.toString+"-"+z.toString
    val big1 = new BigInteger(Sha256.hashToBytes(combineString))
    val l:BigInteger = PrimeUtil.hashToPrime(big1)._1

    val combineString1 = u.toString+"-"+w.toString+"-"+z.toString+"-"+l.toString
    val big2 = new BigInteger(Sha256.hashToBytes(combineString1))
    val alpha = PrimeUtil.hashToPrime(big2)._1

    val tmp = x.divideAndRemainder(l)
    val q = tmp(0)
    val r = tmp(1)
    val Q = u.multiply(BigInteger.TWO.modPow(alpha,modules)).mod(modules).modPow(q,modules)
    new PokeStruct(z,Q,r)
  }

  /**
   * @author jiangbuyun
   * @version 0.1
   * @since 2022-02-5
   * @category 生成POKE协议的证明。
   * @Input u 基数，w 幂，z 基数，Q 挑战数，r 余数，modules 模数
   * @Output 返回证明结果，true验证正确，反之验证失败
   */
  def VerifyProofOfPOKE(u:BigInteger,w:BigInteger,
                        pokeStruct:PokeStruct,
                        modules:BigInteger):Boolean={
    var result = false
    val combineString = u.toString+"-"+w.toString+"-"+pokeStruct.z.toString
    val big1:BigInteger = new BigInteger(Sha256.hashToBytes(combineString))
    val l:BigInteger = PrimeUtil.hashToPrime(big1)._1

    val combineString1 = u.toString+"-"+w.toString+"-"+pokeStruct.z.toString+"-"+l.toString
    val big2 = new BigInteger(Sha256.hashToBytes(combineString1))
    val alpha = PrimeUtil.hashToPrime(big2)._1

    val lhs = pokeStruct.Q.modPow(l,modules).multiply(
      u.multiply(BigInteger.TWO.modPow(alpha,modules)).mod(modules).modPow(pokeStruct.r,modules)).mod(modules)
    val rhs = w.multiply(pokeStruct.z.modPow(alpha,modules)).mod(modules)

    if(lhs.compareTo(rhs) == 0){ result = true}

    result
  }

  /**
   * @author jiangbuyun
   * @version 0.1
   * @since 2022-02-5
   * @category 生成成员见证。
   * @Input old_state 旧的累加器的值，aggregated_data 旧的累加器的指数乘积，element 待见证的元素数据，modules 模数
   * @Output 返回见证值，否则返回None
   */
  def MemWitCreate(old_state:BigInteger,aggregated_data:BigInteger,element:BigInteger,modules:BigInteger):Option[BigInteger]={
    if(aggregated_data.mod(element).compareTo(BigInteger.ZERO) != 0){
      None
    }else{
      val quotient = aggregated_data.divide(element)
      Some(old_state.modPow(quotient,modules))
    }
  }

  /**
   * @author jiangbuyun
   * @version 0.1
   * @since 2022-02-5
   * @category 验证成员见证。
   * @Input state 累加器的值，witness 成员见证值，element 待验证的元素数据，modules 模数
   * @Output 返回验证见证结果，true验证正确，否则返回false
   */
  def VerifyMemWit(state:BigInteger,witness:BigInteger,element:BigInteger,modules:BigInteger):Boolean={
    var result = false
    var value = witness.modPow(element,modules)
    if(value.compareTo(state) == 0) {result = true}
    result
  }

  def NonMemWitCreate(old_state: BigInteger, aggregated_data: BigInteger, elem: BigInteger,modules:BigInteger) : Option[NonMemWitnessStruct] = {
    val bezout = BezoutInExtendedEuclidean(aggregated_data,elem)
    if(bezout == None){
      None
    }else{
      var old_state_1 = new BigInteger(old_state.toString())
      if(bezout.sign_b){
        old_state_1 = old_state.modInverse(modules)//ModInverse(old_state,modules)
      }
      val B = old_state_1.modPow(bezout.coefficient_b,modules)
      Some(new NonMemWitnessStruct(bezout.coefficient_a,bezout.sign_a,B))
    }
  }

  /// Verifies a non-membership witness. "state" represents the current state.
  def VerifyNonMemWit(old_state: BigInteger, state: BigInteger, witness: NonMemWitnessStruct, elem: BigInteger,modules:BigInteger) :Boolean= {
    val a = witness.coefficient_a
    val sign_a = witness.sign_a
    val B = witness.wit_value
    var state1 = new BigInteger(state.toString())

    if(sign_a){
      state1 = state.modInverse(modules)//ModInverse(state,modules)
    }

    val exp_1 = state1.modPow(a,modules)
    val exp_2 = B.modPow(elem,modules)
    val mul = exp_1.multiply(exp_2).mod(modules)//MulMod(exp_1,exp_2,modules)
    if(mul.compareTo(old_state) == 0){
      true
    }else{
      false
    }
  }

  /**
   * @author jiangbuyun
   * @version 0.1
   * @since 2022-02-6
   * @category 更新已有的成员见证。
   * @Input new_state 累加器的值，witness 成员见证值，element 待验证的元素数据，
   *       additions 新增值，deletions 删除值，modules 模数
   * @Output 返回更新的见证结果
   */
  def UpdateMemWit(element: BigInteger, witness: BigInteger, new_state: BigInteger,
                   additions: BigInteger, deletions: BigInteger,modules:BigInteger) : Option[BigInteger]= {
    ShamirTrick(witness.modPow(additions, modules), new_state, element, deletions,modules)
  }

  /**
   * @author jiangbuyun
   * @version 0.1
   * @since 2022-02-6
   * @category 聚合多个成员见证。
   * @Input state 累加器的值，witness_x，witness_y 成员见证值，x，y 待聚合的成员，modules 模数
   * @Output 返回聚合的见证结果
   */
  def AggregatedMemWit(state: BigInteger, witness_x: BigInteger, witness_y: BigInteger,
                       x: BigInteger, y: BigInteger,modules:BigInteger) : (BigInteger, BigInteger)= {
    val aggregated = ShamirTrick(witness_x, witness_y, x, y,modules)
    val proof = ProofOfPOE(aggregated.get, x.multiply(y).mod(modules), state,modules);
    new Tuple2[BigInteger,BigInteger](aggregated.get, proof)
  }

  /**
   * @author jiangbuyun
   * @version 0.1
   * @since 2022-02-6
   * @category 验证聚合的多个成员见证。
   * @Input state 累加器的值，witness 成员见证值，agg_elems 聚合的成员，proof 证明，modules 模数
   * @Output 返回验证结果
   */
  def VerifyAggregatedMemWit(state: BigInteger, agg_elems: BigInteger, witness: BigInteger,
                             proof: BigInteger,modules:BigInteger) :Boolean= {
    VerifyProofOfPOE(witness,agg_elems,state,proof,modules)
  }

  /**
   * @author jiangbuyun
   * @version 0.1
   * @since 2022-02-6
   * @category 建立所有成员的见证。
   * @Input old_state 底数，new_elems 成员数组，modules 模数
   * @Output 返回所有的见证
   */
  def CreateAllMemWit(old_state: BigInteger, new_elems: Array[BigInteger],modules:BigInteger) : ArrayBuffer[BigInteger]= {
    RootFactor(old_state,new_elems,modules)
  }

  def MemWitCreateStart(cur_state: BigInteger, old_state: BigInteger,
                        agg: BigInteger, new_elems: Array[BigInteger],modules:BigInteger) : (BigInteger, BigInteger)= {

    var product = BigInteger.ONE
    new_elems.foreach(x=>{
      product = product.multiply(x)
    })
    val witness = MemWitCreate(old_state, agg, product,modules)
    val proof = ProofOfPOE(witness.get, product, cur_state,modules)
    new Tuple2[BigInteger,BigInteger](witness.get,proof)
  }
}
