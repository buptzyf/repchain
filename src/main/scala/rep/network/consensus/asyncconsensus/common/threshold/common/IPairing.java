package rep.network.consensus.asyncconsensus.common.threshold.common;

import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Pairing;
import it.unisa.dia.gas.plaf.jpbc.pairing.PairingFactory;

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-04-17
 * @category	包装TPKE和BLS的公共元素。
 */

public abstract class IPairing {
    protected Pairing group = null;
    protected PairingSerializer serializer = null;
    protected PairingRandom random = null;

    protected Element ZERO = null;
    protected Element ONE = null;
    protected Element g1 = null;
    protected Element g2 = null;

    public IPairing(String CurvesType){
        String path = ParingCurves.getCurvesParams(CurvesType);
        this.group = PairingFactory.getPairing(path);
        this.serializer = new PairingSerializer(this.group);
        this.random = new PairingRandom(this.group);
        this.ZERO = this.random.Random(PairingElementType.ZR).setToZero().getImmutable();
        this.ONE = this.random.Random(PairingElementType.ZR).setToOne().getImmutable();
        this.Init();
    }

    public Pairing getGroup(){return this.group;}

    public PairingSerializer getSerializer(){return this.serializer;}

    public PairingRandom getRandom(){return this.random;}

    public Element getZERO(){return this.ZERO;}

    public Element getONE(){return this.ONE;}

    public Element getG1(){return this.g1;}

    public Element getG2(){return this.g2;}

    protected abstract void Init();
}
