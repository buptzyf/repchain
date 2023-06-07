package rep.accumulator.verkle.util;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigInteger;
import java.util.LinkedHashMap;

public class NonMembershipProof {
    public NonWitness nonWitness;
    @JsonSerialize(using = ToStringSerializer.class)
    public BigInteger proof_poe;
    public proofStruct proof_poke2;

    public NonMembershipProof(NonWitness nonWitness, BigInteger proof_poe, proofStruct proof_poke2){
        this.nonWitness = nonWitness;
        this.proof_poe = proof_poe;
        this.proof_poke2 = proof_poke2;
    }

    public static NonMembershipProof Deserial(LinkedHashMap<String,Object> ps){
        LinkedHashMap<String,Object>  tmp = (LinkedHashMap<String,Object>)ps.get("nonWitness");
        LinkedHashMap<String,Object>  tmp1 = (LinkedHashMap<String,Object>)ps.get("proof_poke2");
        BigInteger p = new BigInteger(ps.get("proof_poe").toString());
        return new NonMembershipProof(NonWitness.Deserial(tmp),p,proofStruct.Deserial(tmp1));
    }
}
