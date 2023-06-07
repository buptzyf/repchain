package rep.accumulator.verkle.util;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigInteger;
import java.util.LinkedHashMap;

public class MembershipProof {
    @JsonSerialize(using = ToStringSerializer.class)
    public BigInteger proof;
    public Witness witness;

    public MembershipProof(BigInteger proof,Witness witness){
        this.proof = proof;
        this.witness = witness;
    }

    public static MembershipProof Deserial(LinkedHashMap<String,Object> ps){
        BigInteger p = new BigInteger(ps.get("proof").toString());
        LinkedHashMap<String,Object>  tmp = (LinkedHashMap<String,Object>)ps.get("witness");
        return new MembershipProof(p,Witness.Deserial(tmp));
    }
}
