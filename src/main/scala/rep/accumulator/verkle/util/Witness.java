package rep.accumulator.verkle.util;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigInteger;
import java.util.LinkedHashMap;

public class Witness {
    @JsonSerialize(using = ToStringSerializer.class)
    public BigInteger witness;

    public Witness(BigInteger witness){
        this.witness = witness;
    }

    public static Witness Deserial(LinkedHashMap<String,Object> ps){
        BigInteger w = new BigInteger(ps.get("witness").toString());
        return new Witness(w);
    }
}
