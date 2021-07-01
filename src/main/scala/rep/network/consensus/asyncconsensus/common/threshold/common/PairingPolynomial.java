package rep.network.consensus.asyncconsensus.common.threshold.common;

import it.unisa.dia.gas.jpbc.Element;

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-04-17
 * @category	该类实现多项式计算。
 */

public class PairingPolynomial {
    /// Polynomial evaluation
    public static Element PolynomialEvaluation(Element[] a,int x,Element ZERO,Element ONE){
        Element y = ZERO.duplicate();
        Element xx = ONE.duplicate();

        for(int i = 0; i < a.length; i++){
            Element coeff = a[i];
            y = y.add(coeff.mul(xx).getImmutable()).getImmutable();
            xx = xx.mul(x).getImmutable();
        }
        return y;
    }

    public static Element[] PolynomialCoefficients(PairingRandom random,int threshold){
        Element[] a = new Element[threshold];
        for(int i = 0; i < threshold; i++){
            a[i] = random.Random(PairingElementType.ZR).getImmutable();
        }
        return a;
    }
}
