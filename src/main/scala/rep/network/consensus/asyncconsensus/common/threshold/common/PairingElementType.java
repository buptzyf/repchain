package rep.network.consensus.asyncconsensus.common.threshold.common;

/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-04-17
 * @category	该类定义配对元素的类型。
 */

public enum PairingElementType {
    ZR,G1,G2,GT,None;

    public static PairingElementType getType(String name) {
        PairingElementType type = None;
        int index = Integer.parseInt(name);
        switch (index){
            case 1:
                type = G1;
                break;
            case 2:
                type = G2;
                break;
            case 3:
                type = GT;
                break;
            case 0:
                type = ZR;
                break;
            default:
                break;
        }
        return type;
    }

    public static String getName(PairingElementType type) {
        String name = "5";
        switch (type){
            case G1:
                name = "1";
                break;
            case G2:
                name = "2";
                break;
            case GT:
                name = "3";
                break;
            case ZR:
                name = "0";
                break;
            default:
                break;
        }
        return name;
    }

}

