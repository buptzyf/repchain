package rep.accumulator.verkle.util;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigInteger;
import java.util.LinkedHashMap;

public class NonWitness {
    @JsonSerialize(using = ToStringSerializer.class)
    public BigInteger d_coefficient;
    @JsonSerialize(using = ToStringSerializer.class)
    public BigInteger v_coefficient;
    @JsonSerialize(using = ToStringSerializer.class)
    public BigInteger gv_inv;

    public NonWitness(BigInteger d_coefficient, BigInteger v_coefficient, BigInteger gv_inv){
        this.d_coefficient = d_coefficient;
        this.v_coefficient = v_coefficient;
        this.gv_inv = gv_inv;
    }

    public static NonWitness Deserial(LinkedHashMap<String,Object> ps){
        BigInteger d = new BigInteger(ps.get("d_coefficient").toString());
        BigInteger v = new BigInteger(ps.get("v_coefficient").toString());
        BigInteger gv = new BigInteger(ps.get("gv_inv").toString());
        return new NonWitness(d,v,gv);
    }
}
