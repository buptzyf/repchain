package rep.network.consensus.asyncconsensus.common.erasurecode.hadoop

import java.math.BigInteger

import rep.accumulator.{Accumulator4RepChain, AccumulatorCommonAlgorithms, BezoutStruct, NonMemWitnessStruct, PokeStruct}

object testAccumulatorCommonAlgorithms extends App {

  var accumulator = new Accumulator4RepChain()
  val modules = accumulator.getN
  test_mul_mod()
  test_mod_exp()
  test_extended_gcd()
  test_bezout()
  test_shamir_trick()
  test_mod_inverse()
  test_root_factor()
  test_mem_wit_create()
  test_non_mem_wit()
  test_poe()
  test_poke()
  test_agg_mem_wit()
  test_verify_agg_mem_wit()
  test_update_mem_wit()
  test_create_all_mem_wit()


  def test_mul_mod() {
    Assert("mul_mod1",AccumulatorCommonAlgorithms.MulMod(new BigInteger("121"),
      new BigInteger("12314"), modules),
      //new BigInteger("12"))
      new BigInteger("1489994").mod(modules))
    Assert("mul_mod2",AccumulatorCommonAlgorithms.MulMod(new BigInteger("128"),
      new BigInteger("23"), new BigInteger("75")),
      new BigInteger("19"))

    Assert("mul_mod3",AccumulatorCommonAlgorithms.MulMod(new BigInteger("121"),
      new BigInteger("12314"), modules),
      //new BigInteger("12"))
      new BigInteger("121").multiply(new BigInteger("12314")).mod(modules))
    Assert("mul_mod4",AccumulatorCommonAlgorithms.MulMod(new BigInteger("128"),
      new BigInteger("23"), new BigInteger("75")),
      new BigInteger("128").multiply(new BigInteger("23")).mod(new BigInteger("75"))
    )
  }


  def test_mod_exp() {
    Assert("mod_exp1",AccumulatorCommonAlgorithms.ModExp(new BigInteger("2"),
      new BigInteger("7"), new BigInteger("75")), new BigInteger("2").modPow(new BigInteger("7"), new BigInteger("75")))
    Assert("mod_exp2",AccumulatorCommonAlgorithms.ModExp(new BigInteger("7"),
      new BigInteger("15"), new BigInteger("75")), new BigInteger("7").modPow(new BigInteger("15"), new BigInteger("75")))

    Assert("mod_exp3",AccumulatorCommonAlgorithms.ModExp(new BigInteger("2"),
      new BigInteger("7"), modules), new BigInteger("2").modPow(new BigInteger("7"),modules))
    Assert("mod_exp4",AccumulatorCommonAlgorithms.ModExp(new BigInteger("7"),
      new BigInteger("15"), modules), new BigInteger("7").modPow(new BigInteger("15"), modules))
  }


  def test_extended_gcd() {
    Assert("extended_gcd1",AccumulatorCommonAlgorithms.BezoutInExtendedEuclidean(
      new BigInteger("180"), new BigInteger("150")),
      new BezoutStruct(new BigInteger("30"),
      new BigInteger("1"), new BigInteger("1"), false, true));
    Assert("extended_gcd2",AccumulatorCommonAlgorithms.BezoutInExtendedEuclidean(
      new BigInteger("13"), new BigInteger("17")),
      new BezoutStruct(new BigInteger("1"),
        new BigInteger("4"), new BigInteger("3"), false, true));
  }


  def test_bezout() {
    Assert("bezout1",AccumulatorCommonAlgorithms.Bezout(new BigInteger("4"),
      new BigInteger("10")), None);
    val temp = AccumulatorCommonAlgorithms.Bezout(new BigInteger("3434"), new BigInteger("2423")).get
    val t4 = new Tuple4[BigInteger,BigInteger,Boolean,Boolean](temp.coefficient_a,temp.coefficient_b,temp.sign_a,temp.sign_b)
    Assert("bezout2",t4,
      new Tuple4[BigInteger,BigInteger,Boolean,Boolean](new BigInteger("997"),
        new BigInteger("1413"),true,false)
      );
  }


  def test_shamir_trick() {
    Assert("shamir_trick1",AccumulatorCommonAlgorithms.ShamirTrick(
      new BigInteger("11"), new BigInteger("6"), new BigInteger("7"), new BigInteger("5"),new BigInteger("13")),
      Some(new BigInteger("7")))
    Assert("shamir_trick2",AccumulatorCommonAlgorithms.ShamirTrick(
      new BigInteger("11"), new BigInteger("7"), new BigInteger("7"), new BigInteger("11"),new BigInteger("13")),
      Some(new BigInteger("6")));
    Assert("shamir_trick3",AccumulatorCommonAlgorithms.ShamirTrick(
      new BigInteger("6"), new BigInteger("7"), new BigInteger("5"), new BigInteger("11"),new BigInteger("13")),
      Some(new BigInteger("11")));
    Assert("shamir_trick4",AccumulatorCommonAlgorithms.ShamirTrick(
      new BigInteger("12"), new BigInteger("7"), new BigInteger("7"), new BigInteger("11"),new BigInteger("13")),
      None);

  }


  def test_mod_inverse() {
    Assert("mod_inverse1",AccumulatorCommonAlgorithms.ModInverse(new BigInteger("9"),new BigInteger("7")),
      new BigInteger("9").modInverse(new BigInteger("7")))
    Assert("mod_inverse2",AccumulatorCommonAlgorithms.ModInverse(new BigInteger("6"),new BigInteger("7")),
      new BigInteger("6").modInverse(new BigInteger("7")))

    Assert("mod_inverse3",AccumulatorCommonAlgorithms.ModInverse(new BigInteger("9"),modules),
      new BigInteger("9").modInverse(modules))
    Assert("mod_inverse4",AccumulatorCommonAlgorithms.ModInverse(new BigInteger("6"),modules),
      new BigInteger("6").modInverse(modules))
  }


  def test_root_factor() {
    System.out.println("root factor::::"+AccumulatorCommonAlgorithms.RootFactor(new BigInteger("2"),
      Array(new BigInteger("3"), new BigInteger("5"),
        new BigInteger("7"), new BigInteger("11")),new BigInteger("13")).mkString(","))
    Assert("root_factor",AccumulatorCommonAlgorithms.RootFactor(new BigInteger("2"),
      Array(new BigInteger("3"), new BigInteger("5"),
    new BigInteger("7"), new BigInteger("11")),new BigInteger("13")).mkString(","),
      Array(new BigInteger("2"), new BigInteger("8"), new BigInteger("5"), new BigInteger("5")).mkString(","))
  }


  def test_mem_wit_create() {
    val old_state:BigInteger = new BigInteger("2")
    val aggregated_data:BigInteger = new BigInteger("1155")
    val element1:BigInteger = new BigInteger("3")
    val element2:BigInteger = new BigInteger("5")
    val element3:BigInteger = new BigInteger("7")
    val element4:BigInteger = new BigInteger("11")
    val element5:BigInteger = new BigInteger("4")
    val modules:BigInteger = new BigInteger("13")
    var proof1:Option[BigInteger] = None
    var proof2:Option[BigInteger] = None
    var proof3:Option[BigInteger] = None
    var proof4:Option[BigInteger] = None
    var proof5:Option[BigInteger] = None
    proof1 = AccumulatorCommonAlgorithms.MemWitCreate(old_state,aggregated_data,element1,modules)
    proof2 = AccumulatorCommonAlgorithms.MemWitCreate(old_state,aggregated_data,element2,modules)
    proof3 = AccumulatorCommonAlgorithms.MemWitCreate(old_state,aggregated_data,element3,modules)
    proof4 = AccumulatorCommonAlgorithms.MemWitCreate(old_state,aggregated_data,element4,modules)
    proof5 = AccumulatorCommonAlgorithms.MemWitCreate(old_state,aggregated_data,element5,modules)

    Assert("mem_wit_create_1",proof1.get,new BigInteger("2"))
    Assert("mem_wit_create_2",proof2.get,new BigInteger("8"))
    Assert("mem_wit_create_3",proof3.get,new BigInteger("5"))
    Assert("mem_wit_create_4",proof4.get,new BigInteger("5"))
    Assert("mem_wit_create_5",proof5 == None,true)

    val state = old_state.modPow(aggregated_data,modules)
    Assert("mem_wit_verify_1",
      AccumulatorCommonAlgorithms.VerifyMemWit(state,proof1.get,element1,modules),true)
    Assert("mem_wit_verify_2",
      AccumulatorCommonAlgorithms.VerifyMemWit(state,proof2.get,element2,modules),true)
    Assert("mem_wit_verify_3",
      AccumulatorCommonAlgorithms.VerifyMemWit(state,proof3.get,element3,modules),true)
    Assert("mem_wit_verify_4",
      AccumulatorCommonAlgorithms.VerifyMemWit(state,proof4.get,element4,modules),true)
  }

  def test_non_mem_wit() {
    val old_state:BigInteger = new BigInteger("2")
    val aggregated_data:BigInteger = new BigInteger("105")
    val modules:BigInteger = new BigInteger("13")
    val nonMemElement : BigInteger = new BigInteger("11")
    val nonProof = AccumulatorCommonAlgorithms.NonMemWitCreate(old_state,aggregated_data,nonMemElement,modules)
    val state = old_state.modPow(aggregated_data,modules)

    Assert("non_mem_wit_verify_1",
      AccumulatorCommonAlgorithms.VerifyNonMemWit(old_state,new BigInteger("5"),nonProof.get,nonMemElement,modules),true)

    Assert("non_mem_wit_verify_2",
      AccumulatorCommonAlgorithms.VerifyNonMemWit(old_state,new BigInteger("6"),nonProof.get,nonMemElement,modules),false)

    Assert("non_mem_wit_verify_3",
      AccumulatorCommonAlgorithms.VerifyNonMemWit(old_state,new BigInteger("5"),nonProof.get,new BigInteger("5"),modules),false)
  }

  def test_poe() {
    val modules = new BigInteger("13")
    val proof1 = AccumulatorCommonAlgorithms.ProofOfPOE(
      new BigInteger("2"), new BigInteger("6"), new BigInteger("12"),modules)
    Assert("verify_poe_1",
      AccumulatorCommonAlgorithms.VerifyProofOfPOE(
        new BigInteger("2"), new BigInteger("6"), new BigInteger("12"),proof1,modules),true)

    val proof2 = AccumulatorCommonAlgorithms.ProofOfPOE(
      new BigInteger("121314"), new BigInteger("14123"), new BigInteger("6"),modules)
    Assert("verify_poe_2",
      AccumulatorCommonAlgorithms.VerifyProofOfPOE(
        new BigInteger("121314"), new BigInteger("14123"), new BigInteger("6"),proof2,modules),true)


    Assert("verify_poe_3",
      AccumulatorCommonAlgorithms.VerifyProofOfPOE(
        new BigInteger("2"), new BigInteger("6"), new BigInteger("12"),new BigInteger("3"),modules),false)

    Assert("verify_poe_4",
      AccumulatorCommonAlgorithms.VerifyProofOfPOE(
        new BigInteger("4"), new BigInteger("12"), new BigInteger("7"),new BigInteger("1"),modules),false)
  }


  def test_poke() {
    val modules = new BigInteger("13")

    val proof1 =  AccumulatorCommonAlgorithms.ProofOfPOKE(
      new BigInteger("2"), new BigInteger("6"), new BigInteger("12"),modules)
    Assert("verify_poke_1",
      AccumulatorCommonAlgorithms.VerifyProofOfPOKE(
        new BigInteger("2"), new BigInteger("12"),proof1,modules),true)

    val proof2 =  AccumulatorCommonAlgorithms.ProofOfPOKE(
      new BigInteger("121314"), new BigInteger("14123"), new BigInteger("6"),modules)
    Assert("verify_poke_2",
      AccumulatorCommonAlgorithms.VerifyProofOfPOKE(
        new BigInteger("121314"), new BigInteger("6"),proof2,modules),true)

    Assert("verify_poke_3",
      AccumulatorCommonAlgorithms.VerifyProofOfPOKE(
        new BigInteger("121314"), new BigInteger("7"),proof2,modules),false)

    Assert("verify_poke_4",
      AccumulatorCommonAlgorithms.VerifyProofOfPOKE(
        new BigInteger("2"), new BigInteger("12"),
        new PokeStruct(new BigInteger("4"), new BigInteger("1"), new BigInteger("2")),modules),
      false)
  }

  def test_agg_mem_wit() {
    val wits = AccumulatorCommonAlgorithms.AggregatedMemWit(new BigInteger("8"), new BigInteger("6"),
      new BigInteger("8"),new BigInteger("3"), new BigInteger("5"),new BigInteger("13"))

    Assert("agg_mem_wit_1",wits._1,new BigInteger("2"))
    Assert("verify_agg_mem_wit_1",AccumulatorCommonAlgorithms.VerifyAggregatedMemWit(
      new BigInteger("8"), new BigInteger("15"),wits._1,wits._2,new BigInteger("13")),true)
  }


  def test_verify_agg_mem_wit() {
    val proof = AccumulatorCommonAlgorithms.ProofOfPOE(new BigInteger("2"),
      new BigInteger("12123"), new BigInteger("8"),new BigInteger("13"))
    Assert("verify_agg_mem_wit_2",AccumulatorCommonAlgorithms.VerifyAggregatedMemWit(
      new BigInteger("8"), new BigInteger("12123"),new BigInteger("2"),proof,new BigInteger("13")),true)
    Assert("verify_agg_mem_wit_3",AccumulatorCommonAlgorithms.VerifyAggregatedMemWit(
      new BigInteger("7"), new BigInteger("12123"),new BigInteger("2"),proof,new BigInteger("13")),false)
  }


  def test_update_mem_wit() {
    val deletions = new BigInteger("15")
    val additions = new BigInteger("77")

    val elem = new BigInteger("12131")
    val witness = new BigInteger("8")
    val new_state = new BigInteger("11")

    Assert("update_mem_wit_1",AccumulatorCommonAlgorithms.UpdateMemWit(
      elem, witness, new_state, additions, deletions,new BigInteger("13")).get,new BigInteger("6"))
  }


  def test_create_all_mem_wit() {
    val all = AccumulatorCommonAlgorithms.CreateAllMemWit(new BigInteger("2"),
      Array(new BigInteger("3"), new BigInteger("5"), new BigInteger("7"), new BigInteger("11")),
      new BigInteger("13")
    )
    Assert("create_all_mem_wit_1",all.mkString(","),
      Array(new BigInteger("2"), new BigInteger("8"), new BigInteger("5"), new BigInteger("5")).mkString(","))
  }

  def Assert(fName:String,value:Any,expect:Any):Unit={
    if(value.toString.equalsIgnoreCase(expect.toString)){
      System.out.println(s"${fName}'s result is equal")
    }else{
      System.err.println(s"${fName}'s result is not equal'")
    }
  }
}
