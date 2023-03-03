package rep.zk;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;

/**
 * A library of pairing utilities.
 * <p>
 * Ported from https://github.com/Zokrates/ZoKrates/blob/master/zokrates_core/src/proof_system/bn128/g16.rs
 */
@SuppressWarnings("unused")
public class Pairing {

    /**
     * Returns the generator of G1
     */
    public static G1Point P1() {
        return new G1Point(new Fp(1), new Fp(2));
    }

    /**
     * Returns the generator of G2
     */
    public static G2Point P2() {
        return new G2Point(
                new Fp2(new BigInteger("11559732032986387107991004021392285783925812861821192530917403151452391805634"),
                        new BigInteger("10857046999023057135944570762232829481370756359578518086990519993285655852781")),
                new Fp2(new BigInteger("4082367875863433681332203403145435568316851327593401208105741076214120093531"),
                        new BigInteger("8495653923123431417604973247489272438418190587263600148770280649306958101930")));
    }

    /**
     * Bilinear pairing check.
     *
     * @param p1
     * @param p2
     * @return
     */
    public static boolean pairing(G1Point[] p1, G2Point[] p2) throws Exception {
        if (p1.length != p2.length) {
            throw new IllegalArgumentException("Points are not in pair");
        }

        ByteArrayOutputStream g1ListData = new ByteArrayOutputStream(p1.length * Util.FP_SIZE*2);
        ByteArrayOutputStream g2ListData = new ByteArrayOutputStream(p1.length * Util.FP_SIZE*4);
        for (int i = 0; i < p1.length; i++) {
            g1ListData.write(Util.serializeG1(p1[i]), 0, Util.FP_SIZE*2);
            g2ListData.write(Util.serializeG2(p2[i]), 0, Util.FP_SIZE*4);
        }

        return AltBn128.ecPair(g1ListData.toByteArray(), g2ListData.toByteArray());
    }

    public static boolean pairingProd1(G1Point a1, G2Point a2) throws Exception {
        return pairing(new G1Point[]{a1}, new G2Point[]{a2});
    }

    public static boolean pairingProd2(G1Point a1, G2Point a2, G1Point b1, G2Point b2) throws Exception {
        return pairing(new G1Point[]{a1, b1}, new G2Point[]{a2, b2});
    }

    public static boolean pairingProd3(G1Point a1, G2Point a2, G1Point b1, G2Point b2, G1Point c1, G2Point c2) throws Exception {
        return pairing(new G1Point[]{a1, b1, c1}, new G2Point[]{a2, b2, c2});
    }

    public static boolean pairingProd4(G1Point a1, G2Point a2, G1Point b1, G2Point b2, G1Point c1, G2Point c2, G1Point d1, G2Point d2) throws Exception {
        return pairing(new G1Point[]{a1, b1, c1, d1}, new G2Point[]{a2, b2, c2, d2});
    }
}
