package rep.zk;

import java.math.BigInteger;

/**
 * Represents a point on G1.
 */
@SuppressWarnings("unused")
public class G1Point {
    public final Fp x;
    public final Fp y;

    public static final G1Point INF = new G1Point(new Fp(BigInteger.ZERO), new Fp(BigInteger.ZERO));

    public G1Point(Fp x, Fp y) {
        this.x = x;
        this.y = y;
    }

    public boolean isZero() {
        return x.isZero() && y.isZero();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        G1Point that = (G1Point) o;
        return this.x.equals(that.x) && this.y.equals(that.y);
    }

    @Override
    public int hashCode() {
        int result = 1;
        result = 31 * result + this.x.hashCode();
        result = 31 * result + this.y.hashCode();

        return result;
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}
