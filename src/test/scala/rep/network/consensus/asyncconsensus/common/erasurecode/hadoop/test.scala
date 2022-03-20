package rep.network.consensus.asyncconsensus.common.erasurecode.hadoop

import java.math.BigInteger
import java.util.Random

import rep.accumulator.{AccumulatorCommonAlgorithms, EnhancedRandom, GFG, PrimeUtil}
import rep.crypto.Sha256
import rep.network.consensus.asyncconsensus.common.erasurecode.hadoop.testAccumulator.generateBigData
import rep.network.consensus.asyncconsensus.common.erasurecode.hadoop.testAccumulatorCommonAlgorithms.Assert

object test extends App {
    test_mem_wit_simple()

    def test_mem_wit_simple() {
        val g = new BigInteger("2")

        val p = PrimeUtil.generateLargePrime(2048)
        val q = PrimeUtil.generateLargePrime(2048)
        val N = p.multiply(q) //new BigInteger("13")//

        val element1:BigInteger = new BigInteger("3")
        var root = g.modPow(element1,N)
        var aggregated_data = BigInteger.ONE.multiply(element1)

        val element2:BigInteger = new BigInteger("5")
        val element3:BigInteger = new BigInteger("7")
        val element4:BigInteger = new BigInteger("11")

        var dd = element3.modInverse(N)

        root = root.modPow(element2,N)
        aggregated_data = aggregated_data.multiply(element2)
        var tmp = g.modPow(aggregated_data,N)
        Assert("check root^element2 mod N == g^element2 mod N",root.compareTo(tmp)==0,true)

        root = root.modPow(element3,N)
        aggregated_data = aggregated_data.multiply(element3)
        tmp = g.modPow(aggregated_data,N)
        Assert("check root^element3 mod N == g^element3 mod N",root.compareTo(tmp)==0,true)

        root = root.modPow(element4,N)
        aggregated_data = aggregated_data.multiply(element4)
        tmp = g.modPow(aggregated_data,N)
        Assert("check root^element4 mod N == g^element4 mod N",root.compareTo(tmp)==0,true)



        //var wit = root.divide(GFG.pow(g,element3)).mod(N)
        var wit = root.modPow(element3.modInverse(N),N)
        var wit_aggr = g.modPow(aggregated_data.divide(element3),N)
        var tmp_root = wit.modPow(element3,N)
        Assert("check root^(element3^-1 mod N) mod N == g^(aggr/element3) mod N",wit.compareTo(wit_aggr)==0,true)
        Assert("check wit^element3 mod N == root",root.compareTo(tmp_root)==0,true)
        Assert("check wit_aggr^element3 mod N == root",root.compareTo(wit_aggr.modPow(element3,N))==0,true)


        wit = root.divide(g.modPow(element4,N))
        wit_aggr = g.modPow(aggregated_data.divide(element4),N)
        tmp_root = wit.modPow(element4,N)
        Assert("check root/element4  == g^(aggr/element4) mod N",wit.compareTo(wit_aggr)==0,true)
        Assert("check wit^element4 mod N == root",root.compareTo(tmp_root)==0,true)
        Assert("check wit_aggr^element4 mod N == root",root.compareTo(wit_aggr.modPow(element4,N))==0,true)
    }

    def Assert(fName:String,value:Any,expect:Any):Unit={
        if(value.toString.equalsIgnoreCase(expect.toString)){
            System.out.println(s"${fName}'s result is equal")
        }else{
            System.err.println(s"${fName}'s result is not equal'")
        }
    }




}
