package rep.zk;

/**
 * We don't need a particular type of checked exception. The jni "driver" just sets the message.
 */
public class AltBn128Jni {
    public native byte[] g1EcAdd(byte[] point1, byte[] point2) throws Exception;
    public native byte[] g1EcMul(byte[] point, byte[] scalar) throws Exception;
    public native boolean ecPair(byte[] g1_point_list, byte[] g2_point_list) throws Exception;
    public native int ping();
}
