package rep.zk;

import java.math.BigInteger;

/**
 * A collection of Elliptic Curve operations on G1 for alt_bn128. This implementation is
 * heavily based on the EC API exposed by the AVM.
 *
 * <p>
 * Curve definition: y^2 = x^3 + b
 * <p>
 */
public class G1 {

    // The prime q in the base field F_q for G1
    private static final BigInteger q = new BigInteger("21888242871839275222246405745257275088696311157297823662689037894645226208583");

    public static G1Point negate(G1Point p) {
        if (p.isZero()) {
            return new G1Point(Fp.zero(), Fp.zero());
        }
        return new G1Point(p.x, new Fp(q.subtract(p.y.c0.mod(q))));
    }

    public static G1Point add(G1Point p1, G1Point p2) throws Exception {
        byte[] p1data = Util.serializeG1(p1);
        byte[] p2data = Util.serializeG1(p2);
        byte[] resultData = AltBn128.g1EcAdd(p1data, p2data);
        G1Point result = Util.deserializeG1(resultData);
        return result;
    }

    public static G1Point mul(G1Point p, BigInteger s) throws Exception {
        byte[] pdata = Util.serializeG1(p);
        byte[] resultData = AltBn128.g1EcMul(pdata, s);
        G1Point result = Util.deserializeG1(resultData);
        return result;
    }
}
