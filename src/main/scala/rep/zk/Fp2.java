package rep.zk;

import java.math.BigInteger;

/**
 * Represents an element of the field F_p^2 (F_p[i]/(i^2 + 1)).
 *
 * F_q2(a + bi, a is real coeff, b is imaginary)
 */
public class Fp2 {
    public static final BigInteger FIELD_MODULUS = new BigInteger("30644e72e131a029b85045b68181585d97816a916871ca8d3c208c16d87cfd47", 16);

    public final BigInteger a;
    public final BigInteger b;

    // (a + bi, a is real coeff, b is imaginary)
    public Fp2(BigInteger a, BigInteger b) {
        this.a = a;
        this.b = b;
    }

    public static Fp2 zero() {
        return new Fp2(BigInteger.ZERO, BigInteger.ZERO);
    }

    public static Fp2 one() {
        return new Fp2(BigInteger.ONE, BigInteger.ZERO);
    }

    public boolean isZero() {
        return a.equals(BigInteger.ZERO) && b.equals(BigInteger.ZERO);
    }

    public Fp2 add(Fp2 that) {
        return new Fp2(
                addmod(this.a, that.a, FIELD_MODULUS),
                addmod(this.b, that.b, FIELD_MODULUS)
        );
    }

    public Fp2 subtract(Fp2 that) {
        return new Fp2(
                submod(this.a, that.a, FIELD_MODULUS),
                submod(this.b, that.b, FIELD_MODULUS)
        );
    }

    public Fp2 multiply(Fp2 that) {
        /*
         * (a + bx) * (c + dx) // 1 + x^2
         * = (ac - bd) + (ad + bc)x
         */
        return new Fp2(
                submod(mulmod(this.a, that.a, FIELD_MODULUS), mulmod(this.b, that.b, FIELD_MODULUS), FIELD_MODULUS),
                addmod(mulmod(this.a, that.b, FIELD_MODULUS), mulmod(this.b, that.a, FIELD_MODULUS), FIELD_MODULUS)
        );
    }

    public Fp2 multiply(BigInteger s) {
        return new Fp2(
                mulmod(this.a, s, FIELD_MODULUS),
                mulmod(this.b, s, FIELD_MODULUS)
        );
    }

    public Fp2 divide(Fp2 other) {
        return multiply(other.inverse());
    }

    public Fp2 inverse() {
        /*
         * Assume this = a + bx and inverse = c + dx, then
         * (ac - bd) + (ad + bc)x = 1, then
         * ac - bd = 1
         * ad + bc = 0.
         * Solving the above linear equations, we get
         * c = a * (a^2 + b^2)^-1
         * d = -b * (a^2 + b^2)^-1
         */
        BigInteger inv = addmod(
                mulmod(this.b, this.b, FIELD_MODULUS),
                mulmod(this.a, this.a, FIELD_MODULUS),
                FIELD_MODULUS
        ).modInverse(FIELD_MODULUS);

        return new Fp2(
                mulmod(this.a, inv, FIELD_MODULUS),
                FIELD_MODULUS.subtract(mulmod(this.b, inv, FIELD_MODULUS))
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Fp2 that = (Fp2) o;
        return this.a.equals(that.a) && this.b.equals(that.b);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + this.a.hashCode();
        result = 31 * result + this.b.hashCode();

        return result;
    }

    private static BigInteger addmod(BigInteger a, BigInteger b, BigInteger c) {
        return a.add(b).mod(c);
    }

    private static BigInteger submod(BigInteger a, BigInteger b, BigInteger c) {
        return a.subtract(b).mod(c);
    }

    private static BigInteger mulmod(BigInteger a, BigInteger b, BigInteger c) {
        return a.multiply(b).mod(c);
    }

    @Override
    public String toString() {
        return "(" + Util.bytesToHex(a.toByteArray()) + ", " + Util.bytesToHex(b.toByteArray()) + ")";
    }
}
