package rep.accumulator.verkle.util;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigInteger;
import java.util.LinkedHashMap;

public class proofStruct {
    @JsonSerialize(using = ToStringSerializer.class)
    public BigInteger z;
    @JsonSerialize(using = ToStringSerializer.class)
    public BigInteger Q;
    @JsonSerialize(using = ToStringSerializer.class)
    public BigInteger r;
    public proofStruct(BigInteger z,BigInteger Q,BigInteger r){
        this.z = z;
        this.Q = Q;
        this.r = r;
    }

    public static proofStruct Deserial(LinkedHashMap<String,Object> ps){
        BigInteger z1 = new BigInteger(ps.get("z").toString());
        BigInteger q1 = new BigInteger(ps.get("Q").toString());
        BigInteger r1 = new BigInteger(ps.get("r").toString());
        return new proofStruct(z1,q1,r1);
    }

}
