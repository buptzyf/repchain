package rep.accumulator.verkle.util;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigInteger;
import java.util.LinkedHashMap;

public class VerkleProofOfMembership {
    public String nodeId;
    @JsonSerialize(using = ToStringSerializer.class)
    public BigInteger acc_value;
    public MembershipProof proof;

    public VerkleProofOfMembership(String nodeId, BigInteger acc_value, MembershipProof proof){
        this.nodeId = nodeId;
        this.acc_value = acc_value;
        this.proof = proof;
    }

    public static VerkleProofOfMembership Deserial(LinkedHashMap<String,Object> ps){
        String id = ps.get("nodeId").toString();
        BigInteger v = new BigInteger(ps.get("acc_value").toString());
        LinkedHashMap<String,Object>  tmp = (LinkedHashMap<String,Object>)ps.get("proof");
        return new VerkleProofOfMembership(id,v,MembershipProof.Deserial(tmp));
    }
}
