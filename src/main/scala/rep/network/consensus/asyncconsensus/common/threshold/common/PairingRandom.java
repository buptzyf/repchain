package rep.network.consensus.asyncconsensus.common.threshold.common;

import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Pairing;


/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-04-17
 * @category	该类实现配对元素生成的随机计算。
 */

public class PairingRandom {
    protected Pairing group = null;

    public PairingRandom(Pairing group){
        this.group = group;
    }

    public Element Random(PairingElementType eType){
        Element re = null;
        switch (eType){
            case G1:
                re = this.group.getG1().newRandomElement();
                break;
            case G2:
                re = this.group.getG2().newRandomElement();
                break;
            case ZR:
                re = this.group.getZr().newRandomElement();
                break;
            case GT:
                re = RandomForGT();
                break;
            default:
                break;
        }
        return re;
    }

    public Element RandomForGT(){
        Element re = null;
        Element p = this.group.pairing(this.Random(PairingElementType.G1),this.Random(PairingElementType.G2));
        Element z = this.Random(PairingElementType.ZR);
        re = p.powZn(z);
        return re;
    }
}
