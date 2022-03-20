package rep.accumulator;

import java.math.BigInteger;

public class GFG {

    // Java code to check if number is prime. This
    // program demonstrates concept behind AKS
    // algorithm and doesn't implement the actual
    // algorithm (This works only till n = 64)

    // array used to store coefficients .
    static long c[] = new long[100];

    // function to calculate the coefficients
    // of (x - 1)^n - (x^n - 1) with the help
    // of Pascal's triangle .
    static void coef(int n)
    {
        c[0] = 1;
        for (int i = 0; i < n; c[0] = -c[0], i++) {
            c[1 + i] = 1;

            for (int j = i; j > 0; j--)
                c[j] = c[j - 1] - c[j];
        }
    }

    // function to check whether
    // the number is prime or not
    static boolean isPrime(int n)
    {
        // Calculating all the coefficients by
        // the function coef and storing all
        // the coefficients in c array .
        coef(n);

        // subtracting c[n] and adding c[0] by 1
        // as ( x - 1 )^n - ( x^n - 1), here we
        // are subtracting c[n] by 1 and adding
        // 1 in expression.
        c[0]++;
        c[n]--;

        // checking all the coefficients whether
        // they are divisible by n or not.
        // if n is not prime, then loop breaks
        // and (i > 0).
        int i = n;
        while ((i--) > 0 && c[i] % n == 0)
            ;

        // Return true if all coefficients are
        // divisible by n.
        return i < 0;
    }

    public static BigInteger bigIntegerEuler(BigInteger n) {
        BigInteger ret = new BigInteger(n.toString());

        BigInteger i = new BigInteger("2");
        BigInteger one = new BigInteger("1");
        BigInteger zero = new BigInteger("0");
        for (; i.multiply(i).compareTo(n) <= 0; i.add(one)) {
            if (n.mod(i).compareTo(zero) == 0) {
                ret = ret.subtract(n.divide(i));
                do {
                    n = n.divide(i);
                }while (n.mod(i).compareTo(zero) == 0);
            }
        }

        if (n.compareTo(one) > 0) {
            ret = ret.subtract(ret.divide(n));
        }

        return ret;
    }

    public static BigInteger pow(BigInteger base, BigInteger exponent)  {
        BigInteger result  = BigInteger.ONE;

        while (exponent.signum() > 0 ) {
            if (exponent.testBit(0)) result = result.multiply(base);
            base = base.pow(2);
            exponent = exponent.shiftRight(1);
        }
        return result;
    }

    // Driver code
    public static void main(String[] args)
    {
        //int n = 37;
        int n = 945820934;
        if (isPrime(n))
            System.out.println("Prime");
        else
            System.out.println("Not Prime");
    }
}