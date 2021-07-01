package rep.network.consensus.asyncconsensus.common.threshold.common;

import it.unisa.dia.gas.jpbc.Element;
import it.unisa.dia.gas.jpbc.Pairing;
import it.unisa.dia.gas.plaf.jpbc.field.curve.CurveElement;
import java.util.Base64;

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-04-17
 * @category	该类实现配对元素的序列化操作。
 */

public class PairingSerializer {
    protected Pairing  group = null;

    public PairingSerializer(Pairing group){
        this.group = group;
    }

    public Element Deserialize(String base64Data){
        return Deserialize(base64Data,true);
    }

    public Element Deserialize(String base64Data,boolean isCompressed){
        if(base64Data == null) return null;
        if(base64Data.indexOf(":") != 1) return null;
        String data = base64Data.substring(2,base64Data.length());
        PairingElementType type = PairingElementType.getType(base64Data.substring(0,1));
        if(type == PairingElementType.None) return null;
        return Deserialize(data,type,isCompressed);
    }

    //反序列化，根据已有数据建立Element
    private Element Deserialize(String base64Data,PairingElementType type,boolean isCompressed){
        Element e = null;
        if(base64Data == null) {
            return e;
        }
        byte[] data = Base64.getDecoder().decode(base64Data);
        switch (type){
            case G1:
                e = this.group.getG1().newElement();
                e = setDataFromByte(e,data,isCompressed);
                break;
            case G2:
                e = this.group.getG2().newElement();
                e = setDataFromByte(e,data,isCompressed);
                break;
            case GT:
                e = this.group.getGT().newElementFromBytes(data).getImmutable();
                break;
            case ZR:
                e = this.group.getZr().newElementFromBytes(data).getImmutable();
                break;
            default:
                break;
        }
        return e;
    }

    private Element setDataFromByte(Element e,byte[] data,boolean isCompressed){
        Element re = null;
        if(isCompressed){
            if(e instanceof CurveElement){
                CurveElement ce = (CurveElement)e;
                ce.setFromBytesCompressed(data);
                re = ce.duplicate().getImmutable();
            }
        }else{
            e.setFromBytes(data);
            re = e.duplicate().getImmutable();
        }
        return re;
    }

    //序列化，输出base64字符串
    public String Serialize(Element e,PairingElementType type,boolean isCompressed){
        String str = "";

        if(e == null) {
            return str;
        }

        String base64 = SerializeForData(e,type,isCompressed);
        String typeStr = PairingElementType.getName(type);
        str = typeStr + ":" + base64;
        return str;
    }

    public String SerializeForData(Element e,PairingElementType type,boolean isCompressed){
        String str = "";

        if(e == null) {
            return str;
        }

        byte[] rb = SerializeForByte(e,type,isCompressed);
        if(rb != null){
            str = Base64.getEncoder().encodeToString(rb);
        }

        return str;
    }

    public byte[] SerializeForByte(Element e,PairingElementType type,boolean isCompressed){
        byte[] rb = null;

        switch (type){
            case G1:case G2:
                rb = toByte(e,isCompressed);
                break;
            case GT:case ZR:
                rb = e.toBytes();
                break;
            default:
                break;
        }
        return rb;
    }

    public String Serialize(Element e,PairingElementType type){
        return Serialize(e,type,true);
    }

    private byte[] toByte(Element e,boolean isCompressed){
        byte[] rb = null;
        if(isCompressed) {
            if (e instanceof CurveElement) {
                CurveElement ce = (CurveElement) e;
                rb = ce.toBytesCompressed();
            }
        }else{
            rb = e.toBytes();
        }
        return rb;
    }

}
