package rep.accumulator


import java.math.BigInteger

object Util {
  case class BezoutStruct(gcd: BigInteger, coefficient_a: BigInteger, coefficient_b: BigInteger,
                          sign_a: Boolean, sign_b: Boolean)

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-08-28
   * @category 求xth_root的b次方再乘以yth_root的a次方的积再取模
   * @Input xth_root, yth_root, x, y，modules 模数
   * @Output 根据贝祖定理得到a，b，再求xth_root的b次方再乘以yth_root的a次方的积再取模
   */
  def ShamirTrick(xth_root: BigInteger, yth_root: BigInteger, x: BigInteger, y: BigInteger): Option[BigInteger] = {
    var ret: Option[BigInteger] = None
    if (Rsa2048.exp(xth_root,x).compareTo(Rsa2048.exp(yth_root,y)) == 0) {
      var x1th_root = new BigInteger(xth_root.toString())
      var y1th_root = new BigInteger(yth_root.toString())
      val bezout = BezoutInExtendedEuclidean(x, y)
      //x,y的最大公约数是否为1，即x，y互为素数
      if (bezout.gcd.compareTo(BigInteger.ONE) == 0) {
        //x,y 互为素数
        if (bezout.sign_b) {
          x1th_root =  Rsa2048.inv(xth_root)
        }
        if (bezout.sign_a) {
          y1th_root = Rsa2048.inv(yth_root)
        }
        val mod_x = Rsa2048.exp(x1th_root,bezout.coefficient_b)
        val mod_y = Rsa2048.exp(y1th_root,bezout.coefficient_a)
        ret = Some(Rsa2048.op(mod_x,mod_y))
      }
    }
    ret
  }

  /**
   * @author jiangbuyun
   * @version 2.0
   * @since 2022-08-28
   * @category 该方法采用了扩展欧几里德算法计算贝祖恒等式的因子以及他们的公约数。
   * @Input a，b 输入两个参数，求a，b的因子和公约数
   * @Output 1  公约数
   *         2  因子x
   *         3  因子y
   *         4  因子x的符号
   *         5  因子y的符号
   */
  private def BezoutInExtendedEuclidean(a: BigInteger, b: BigInteger) : BezoutStruct = {
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
      val new_r = old_r.subtract(quotient.multiply(r));
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
    BezoutStruct(old_r, old_s, old_t, prev_sign_s, prev_sign_t)
  }

  /**
   * @author jiangbuyun
   * @version 0.1
   * @since 2022-08-28
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



}
