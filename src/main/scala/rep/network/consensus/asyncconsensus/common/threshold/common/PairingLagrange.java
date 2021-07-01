package rep.network.consensus.asyncconsensus.common.threshold.common;

import it.unisa.dia.gas.jpbc.Element;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-04-17
 * @category	该类实现拉格朗日计算。
 */

public class PairingLagrange {
    public static Element Lagrange(Element ONE, List<Integer> S, int j){
        Element re = null;
        List<Integer> num1 = new ArrayList<Integer>();
        List<Integer> num2 = new ArrayList<Integer>();

        for(int jj = 0; jj < S.size(); jj++){
            Integer k = S.get(jj);
            if(k != j){
                num1.add(0 - k - 1);
            }
        }

        for(int jj = 0; jj < S.size(); jj++){
            Integer k = S.get(jj);
            if(k != j){
                num2.add(j - k);
            }
        }

        Element num = ONE.duplicate();
        for(int k = 0; k < num1.size(); k++){
            num = num.mul(num1.get(k)).getImmutable();
        }

        Element den = ONE.duplicate();
        for(int k = 0; k < num2.size(); k++){
            den = den.mul(num2.get(k)).getImmutable();
        }
        re = num.div(den).getImmutable();
        return re;
    }

    public static Element[] computeLagrangePow(Element ONE,HashMap<Integer,Element> sigs, Integer[] S){
        Element[] res = new Element[S.length];
        for(int i = 0; i < S.length; i++){
            int j = S[i];
            Element sig = sigs.get(j).getImmutable();
            Element lag = PairingLagrange.Lagrange(ONE, Arrays.asList(S),j);
            res[i] = sig.powZn(lag).getImmutable();
        }
        return res;
    }


}
