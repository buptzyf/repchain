package rep.network.consensus.asyncconsensus.common.erasurecode.hadoop

import java.math.BigInteger
import java.util.Random
import rep.accumulator.{Accumulator4RepChain, AccumulatorCommonAlgorithms, PrimeUtil}
import rep.crypto.Sha256
import rep.network.consensus.asyncconsensus.common.erasurecode.hadoop.testBezout.generateBigData

object testProof extends App {
  val modules = PrimeUtil.generateLargePrime(AccumulatorCommonAlgorithms.RSA_PRIME_SIZE)
  val proof = AccumulatorCommonAlgorithms.ProofOfPOE(new BigInteger("2"),new BigInteger("6"),new BigInteger("64"),modules)
  val result = AccumulatorCommonAlgorithms.VerifyProofOfPOE(new BigInteger("2"),new BigInteger("6"),new BigInteger("64"),proof,modules)
  if(result){
    System.out.println(s"poe first is fine!")
  }else{
    System.out.println(s"poe first is bad")
  }


  /*val u = new BigInteger("2")
  val x = new BigInteger("8")
  val w = u.modPow(x,modules)*/

  val u = PrimeUtil.generateLargePrime(128)
  val x = PrimeUtil.generateLargePrime(128)
  val w = u.modPow(x,modules)

  val proof1 = AccumulatorCommonAlgorithms.ProofOfPOE(
    u,x,
    w,modules)
  val result1 = AccumulatorCommonAlgorithms.VerifyProofOfPOE(
    u,x,
    w,proof1,modules)
  if(result1){
    System.out.println(s"poe second is fine!")
  }else{
    System.out.println(s"poe second is bad")
  }


  val proof5 = AccumulatorCommonAlgorithms.ProofOfPOKE(
    u,x,
    w,modules)
  val result5 = AccumulatorCommonAlgorithms.VerifyProofOfPOKE(
    u,
    w,proof5,modules)
  if(result1){
    System.out.println(s"poke second is fine!")
  }else{
    System.out.println(s"poke second is bad")
  }


  val result3 = AccumulatorCommonAlgorithms.VerifyProofOfPOE(new BigInteger("2"),new BigInteger("6"),new BigInteger("12"),new BigInteger("3"),modules)
  if(result3){
    System.out.println(s"third is not fine!")
  }else{
    System.out.println(s"third is bad")
  }

  val result4 = AccumulatorCommonAlgorithms.VerifyProofOfPOE(new BigInteger("4"),new BigInteger("12"),new BigInteger("7"),new BigInteger("1"),modules)
  if(result4){
    System.out.println(s"fouth is not fine!")
  }else{
    System.out.println(s"fouth is bad")
  }

}
