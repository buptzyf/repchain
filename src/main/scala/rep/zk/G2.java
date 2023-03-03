package rep.zk;

import java.math.BigInteger;

/**
 * A collection of Elliptic Curve operations on G2 for alt_bn128.
 * <p>
 * Curve definition: y^2 = x^3 + b
 * <p>
 * Ported from https://github.com/musalbas/solidity-BN256G2/blob/master/BN256G2.sol
 */
public class G2 {

    public static final Fp2 TWIST_B = new Fp2(
            new BigInteger("2b149d40ceb8aaae81be18991be06ac3b5b4c5e559dbefa33267e6dc24a138e5", 16),
            new BigInteger("9713b03af0fed4cd2cafadeed8fdf4a74fa084e52d1852e4a2bd0685c315d2", 16)
    );

    /**
     * Adds two points.
     *
     * @param p1 the first point
     * @param p2 the second point
     * @return p1 + p2
     */
    public static G2Point ECTwistAdd(G2Point p1, G2Point p2) {
        if (p1.isZero()) {
            if (!p2.isZero()) {
                assert isOnCurve(p2);
            }
            return p2;
        } else if (p2.isZero()) {
            assert isOnCurve(p1);
            return p1;
        }

        assert isOnCurve(p1);
        assert isOnCurve(p2);

        G2Point p3 = ECTwistAdd(toJacobian(p1), toJacobian(p2)).toAffine();

        return p3;
    }

    /**
     * Multiplies a point by a scalar.
     *
     * @param p the point
     * @param s the multiplier
     * @return s * p
     */
    public static G2Point ECTwistMul(G2Point p, BigInteger s) {
        if (!p.isZero()) {
            assert isOnCurve(p);
        }

        G2Point p2 = ECTwistMul(toJacobian(p), s).toAffine();

        return p2;
    }

    protected static boolean isOnCurve(G2Point p) {
        Fp2 y2 = p.y.multiply(p.y); // y^2
        Fp2 x3 = p.x.multiply(p.x).multiply(p.x); // x^3
        Fp2 diff = y2.subtract(x3).subtract(TWIST_B); // y^2 - x^3 - B

        return diff.isZero();
    }

    protected static JacobianPoint ECTwistAdd(JacobianPoint p1, JacobianPoint p2) {
        if (p1.z.isZero()) {
            return p2;
        } else if (p2.z.isZero()) {
            return p1;
        }

        Fp2 U1 = p2.y.multiply(p1.z); // U1 = p2.y * p1.z
        Fp2 U2 = p1.y.multiply(p2.z); // U2 = p1.y * p2.z
        Fp2 V1 = p2.x.multiply(p1.z); // V1 = p2.x * p1.z
        Fp2 V2 = p1.x.multiply(p2.z); // V2 = p1.x * p2.z

        if (p2.x.equals(V2)) {
            if (p2.y.equals(U2)) {
                return ECTwistDouble(p1);
            }

            return new JacobianPoint(Fp2.one(), Fp2.one(), Fp2.zero());
        }

        Fp2 W = p1.z.multiply(p2.z); // W = p1.z * p2.z
        Fp2 V = V1.subtract(V2); // V = V1 - V2
        Fp2 V_2 = V.multiply(V);
        Fp2 V_3 = V_2.multiply(V);
        // z = V^3 * W
        Fp2 z = V_3.multiply(W);

        Fp2 U = U1.subtract(U2); // U = U1 - U2
        Fp2 U_2 = U.multiply(U);
        Fp2 A = U_2.multiply(W).subtract(V_3).subtract(V_2.multiply(V2).multiply(BigInteger.TWO));
        // x = V * (U^2 * W - V^3 - 2 * V^2 * V2)
        Fp2 x = V.multiply(A);

        // y = U * (v^2 * V2 - A) - V^3 * U2
        Fp2 y = U.multiply(V_2.multiply(V2).subtract(A)).subtract(V_3.multiply(U2));

        return new JacobianPoint(x, y, z);
    }

    protected static JacobianPoint ECTwistMul(JacobianPoint p, BigInteger s) {
        JacobianPoint p2 = new JacobianPoint(Fp2.zero(), Fp2.zero(), Fp2.zero());

        while (!s.equals(BigInteger.ZERO)) {
            if (s.testBit(0)) {
                p2 = ECTwistAdd(p2, p);
            }

            p = ECTwistDouble(p);

            s = s.divide(BigInteger.TWO);
        }

        return p2;
    }

    protected static JacobianPoint ECTwistDouble(JacobianPoint p) {
        Fp2 W = p.x.multiply(p.x).multiply(BigInteger.valueOf(3)); // W = 3 * x * x
        Fp2 S = p.y.multiply(p.z); // S = y * z
        Fp2 B = p.x.multiply(p.y).multiply(S); // B = x * y * S
        Fp2 H = W.multiply(W).subtract(B.multiply(BigInteger.valueOf(8))); // H = W * W - 8 * B
        Fp2 S_2 = S.multiply(S); // S^2
        Fp2 S_3 = S_2.multiply(S); // S^3

        // y = W * (4 * B - H) - 8 * y * y * S^2
        Fp2 y = W.multiply(B.multiply(BigInteger.valueOf(4)).subtract(H))
                .subtract(p.y.multiply(p.y).multiply(BigInteger.valueOf(8)).multiply(S_2));
        // x = 2 * H * S
        Fp2 x = H.multiply(S).multiply(BigInteger.TWO);
        // z = 8 * S^3
        Fp2 z = S_3.multiply(BigInteger.valueOf(8));

        return new JacobianPoint(x, y, z);
    }

    protected static JacobianPoint toJacobian(G2Point p) {
        return p.isZero() ? new JacobianPoint(Fp2.one(), Fp2.one(), Fp2.zero()) : new G2.JacobianPoint(p.x, p.y, Fp2.one());
    }

    public static class JacobianPoint {
        public final Fp2 x;
        public final Fp2 y;
        public final Fp2 z;

        public JacobianPoint(Fp2 x, Fp2 y, Fp2 z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public G2Point toAffine() {
            if (z.isZero()) {
                return new G2Point(Fp2.zero(), Fp2.zero());
            } else {
                Fp2 inv = z.inverse();
                return new G2Point(x.multiply(inv), y.multiply(inv));
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            JacobianPoint that = (JacobianPoint) o;
            return this.x.equals(that.x) && this.y.equals(that.y) && this.z.equals(that.z);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + this.x.hashCode();
            result = 31 * result + this.y.hashCode();
            result = 31 * result + this.z.hashCode();

            return result;
        }

        @Override
        public String toString() {
            return "((" + x.a + ", " + x.b + "), (" + y.a + ", " + y.b + "), (" + z.a + ", " + z.b + "))";
        }
    }
}