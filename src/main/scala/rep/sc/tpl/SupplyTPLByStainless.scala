package rep.sc.tpl

import stainless.lang._
import stainless.proof._
import stainless.annotation._
import stainless.collection._

object SupplyTPLByStainless {
    // var kv:Map[String, MutableMap[String, Real]] = Map.empty[String, MutableMap[String, Real]];
    // var kv2:Map[String, String] = Map.empty[String, String];
    // var kv3:Map[String, BigInt] = Map.empty[String, BigInt];

    // def signFixed(data : MutableMap[String, Double]) : Unit = {
    //     val sid = ACCOUNT_SALE + "_" + PRODUCT_ID
    //     val pid = sid + "_PM"
    //     kv.update(sid, data)
    //     kv2.update(pid, "TPL.Fixed")
    // }

    // def split(amount : Int) : Unit = {
    //     val sid = ACCOUNT_SALE + "_" + PRODUCT_ID
    //     val pid = sid + "_PM"
    //     val tm = kv2.apply(pid)
    //     val mr = tm match {
    //         case "TPL.Fixed" =>
    //             val sp = kv.apply(sid)
    //             splitFixedRatio(amount, kv.apply(sid))
    //     }
    //     addToAccount(mr)
    // }

    // def addToAccount(mr : MutableMap[String,Int]) : Unit = {
    //     for ((k, v) <- mr.theMap) {
    //         val sk = kv3.apply(k)
    //         var dk = if(sk==None[Int]) 0 else sk       //
    //         kv3.update(k, dk+v)
    //     }
    // }

    // @pure
    // def splitFixedRatio(sr : Int, mr : MutableMap[String, Double]) : MutableMap[String ,Int] = {
    //     require(mr.theMap.values.reduceLeft(_ + _) < 1)
    //     var rm : MutableMap[String, Int] = MutableMap[String, Int](scala.collection.mutable.Map[String,Int](), () => 0)
    //     var remain = sr
    //     for ((k, v) <- mr.theMap) {
    //         val rv = (sr * v ).toInt
    //         rm.update(k, rv)
    //         remain -= rv
    //     }
    //     rm.update("ACCOUNT_REMAIN", remain)
    //     rm
    // } ensuring {(res: MutableMap[String, Int]) => res.theMap.values.reduceLeft(_ + _) <= sr}

    // def splitFixedRatio2(sr : BigInt, mr : List[BigInt]) : Boolean = {
    //     require(mr.forall(i => i > 0) && mr.foldLeft(0)((i:BigInt, j:BigInt) => i + j) < 100 && sr > 0)
    //     check(mr.foldLeft(0)((i:BigInt, j:BigInt) => i + j) < 100) &&
    //     check(mr.foldLeft(0)((i:BigInt, j:BigInt) => i + j) * sr < 100 * sr) &&
    //     check(mr.map((k:BigInt) => sr * k).foldLeft(0)((i:BigInt, j:BigInt) => i + j) == mr.foldLeft(0)((i:BigInt, j:BigInt) => i + j) * sr)
    // }.holds

    def splitShare(sr: BigInt, rule: Map[String, Map[String, BigInt]]): Boolean = {
        var xsMap = rule.values
        checkAllMap(sr, xsMap)
    }

    def checkAllMap(sr: BigInt, xs: List[Map[String,BigInt]]): Boolean = xs match {
        case Nil() => true
        case Cons(xsMap, xsMaps) => checkMapValues(sr, xsMap) && checkAllMap(sr, xsMaps)
    }

    def splitFixedRatio(sr: BigInt, xsMap: Map[String, BigInt]): Boolean = {
        checkMapValues(sr, xsMap)
    }

    def checkMapValues(sr: BigInt, xsMap: Map[String, BigInt]): Boolean = {
        var xs = xsMap.values
        checkListSum(sr, xs)
    }

    def sum(xs: List[BigInt]): BigInt = xs match {
        case Nil() => 0
        case Cons(y, ys) => y + sum(ys)
    }

    def foldMap(xs: List[BigInt], sr: BigInt): BigInt = xs match {
        case Nil() => 0
        case Cons(y, ys) => y * sr + foldMap(ys, sr)
    }

    def checkListSum(sr: BigInt, xs: List[BigInt]): Boolean = {
        foldMap(xs, sr) == sum(xs) * sr because {
            xs match {
                case Nil() => check ( 0 == 0 * sr )
                case Cons(y, ys) => check ( y * sr + foldMap(ys, sr) == (y + sum(ys)) * sr because
                                            y * sr + foldMap(ys, sr) == y * sr + sum(ys) * sr because
                                            checkListSum(sr, ys) )
            }
        }
    }.holds

    def transferAccount(account1: BigInt, account2: BigInt, amount: BigInt): Boolean = {
        val sum = account1 + account2
        val newAccount1 = account1 - amount
        val newAccount2 = account2 + amount
        newAccount1 + newAccount2 == sum
    }.holds

    def test2(xsMap: Map[String, BigInt], sr: BigInt): Boolean = {
        var xs = xsMap.values
        foldMap(xs, sr) == sum(xs) * sr because {
            xs match {
                case Nil() => check ( 0 == 0 * sr )
                case Cons(y, ys) => check ( y * sr + foldMap(ys, sr) == (y + sum(ys)) * sr because
                                            y * sr + foldMap(ys, sr) == y * sr + sum(ys) * sr because
                                            test2(ys, sr) )
            }
        }
    }.holds

    def test3(xs: List[BigInt], sr: BigInt): Boolean = {
        decreases(xs)
        foldMap(xs, sr) == xs.foldLeft(BigInt(0))((i:BigInt, j:BigInt) => i + j) * sr because {//foldLeft(0)(_ + _)
            xs match {
                case Nil() => check ( 0 == 0 * sr )
                case Cons(y, ys) => check ( y * sr + foldMap(ys, sr) == (y :: ys).foldLeft(BigInt(0))((i:BigInt, j:BigInt) => i + j) * sr because
                                            y * sr + foldMap(ys, sr) == ys.SupplyTPLSupplyTPLSupplyTPLSupplyTPL(BigInt(0) + y)((i:BigInt, j:BigInt) => i + j) * sr because
                                            y * sr + foldMap(ys, sr) == y * sr + ys.foldLeft(BigInt(0))((i:BigInt, j:BigInt) => i + j) * sr because
                                            test3(ys, sr) )
            }
        }
    }.holds

    def prop (wrapper: ContractContext)= {
        require(!wrapper.contains("1"))
        assert(!wrapper.contains("1"))
        wrapper.setVal("1", "test")
        assert(wrapper.contains("1"))
        assert(getVal("1") == "test")
    }
}