package rep.zk;

/**
 * Represents a point on the elliptic curve.
 */
public class G2Point {
    public final Fp2 x;
    public final Fp2 y;

    public G2Point(Fp2 x, Fp2 y) {
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
        G2Point that = (G2Point) o;
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
