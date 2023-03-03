package rep.zk;

import java.math.BigInteger;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

/**
 * Java wrapper for alt-bn128 curve implemented here: https://github.com/paritytech/bn
 *
 * todo: the three functions have different failure mode. fix for production.
 * todo: move more of the point validation logic into the JNI wrapper. fix for production.
 */
public class AltBn128 {

    static {
      //if (!loadEmbeddedLibrary()) {
        StringBuilder url = new StringBuilder();
        url.append(System.getProperty("user.dir"));
        String sep = System.getProperty("file.separator");
        url.append(sep).append("src").append(sep).append("main").append(sep).append("scala").append(sep).append("rep").append(sep).append("zk").append(sep).append("native").append(sep).append("bn_jni.dll");
//
        System.load("D:\\Idea\\repchain-dev_jdk13_2.0.0.0\\src\\main\\scala\\rep\\zk\\native\\bn_jni.dll");
            //System.loadLibrary("bn_jni");
        //}
    }

    private static final class Holder {
        protected static final AltBn128Jni INSTANCE = new AltBn128Jni();
    }

    // non-instantiable class
    private AltBn128() { }

    private static int WORD_SIZE = 32;
    // points in G1 are encoded like so: [p.x || p.y]. Each coordinate is 32-byte aligned.
    private static int G1_POINT_SIZE = 2 * WORD_SIZE;
    // points in G2, encoded like so: [p1[0].x || p1[0].y || p1[1].x || p2[1].y || p2[0].x]. Each coordinate is 32-byte aligned.
    private static int G2_POINT_SIZE = 4 * WORD_SIZE;

    // Runtime-facing implementation
    /**
     * Computes EC addition in G1
     *
     * We do buffer size validation here (not done in JNI wrapper).
     *
     * Failure Mode: Any illegal points yield a '0' as result.
     *
     * @param point1 point in G1, encoded like so: [p.x || p.y]. Each coordinate is 32-byte aligned.
     * @param point2 point in G1, encoded like so: [p.x || p.y]. Each coordinate is 32-byte aligned.
     *
     */
    public static byte[] g1EcAdd(byte[] point1, byte[] point2) throws Exception {
        // assert valid data.
        // todo: convert assert to runtime assertion in AVM
        assert (point1 != null && point2 != null &&
                point1.length == G1_POINT_SIZE && point2.length == G1_POINT_SIZE);

        // call jni
        return Holder.INSTANCE.g1EcAdd(point1, point2);
    }

    /**
     * Computes scalar multiplication in G1
     *
     * We do buffer size validation here (not done in JNI wrapper).
     *
     * Failure Mode: Any illegal points as input yields an Exception with message "NotOnCurve".
     *
     * @param point point in G1, encoded like so: [p.x || p.y]. Each coordinate is 32-byte aligned.
     * @param scalar natural number (> 0), byte aligned to 32 bytes.
     */
    public static byte[] g1EcMul(byte[] point, BigInteger scalar) throws Exception {
        // assert valid data.
        // todo: convert assert to runtime assertion in AVM
        assert (point != null && scalar != null &&
                point.length == G1_POINT_SIZE && scalar.signum() != -1); // scalar can't be negative (it can be zero or positive)

        byte[] sdata = scalar.toByteArray();
        assert (sdata.length <= WORD_SIZE);

        byte[] sdata_aligned = new byte[WORD_SIZE];
        System.arraycopy(sdata, 0, sdata_aligned, WORD_SIZE - sdata.length, sdata.length);

        // call jni
        return Holder.INSTANCE.g1EcMul(point, sdata_aligned);
    }

    /**
     * The Pairing itself is a transformation of the form G1 x G2 -> Gt, <br/>
     * where Gt is a subgroup of roots of unity in Fp12 field<br/>
     * <br/>
     *
     * Pairing Check input is a sequence of point pairs, the result is either success (true) or failure (false) <br/>
     * <br/>
     *
     * We do buffer size validation here (not done in JNI wrapper).
     *
     * Failure Mode: Any illegal points as input yield a result 'false'.
     *
     * @param g1_point_list list of points in G1, encoded like so: [p1.x || p1.y || p2.x || p2.y || ...].
     *                      Each coordinate is byte aligned to 32 bytes.
     * @param g2_point_list list of points in G2, encoded like so: [p1[0].x || p1[0].y || p1[1].x || p2[1].y || p2[0].x || ...].
     *                      Each coordinate is byte aligned to 32 bytes.
     *
     */
    public static boolean ecPair(byte[] g1_point_list, byte[] g2_point_list) throws Exception {
        // assert valid data.
        // todo: convert assert to runtime assertion in AVM
        assert (g1_point_list != null && g2_point_list != null &&
                g1_point_list.length % G1_POINT_SIZE == 0 && g2_point_list.length % G2_POINT_SIZE == 0); // data is well-aligned
        int g1_list_size = g1_point_list.length / G1_POINT_SIZE;
        int g2_list_size = g2_point_list.length / G2_POINT_SIZE;
        assert (g1_list_size == g2_list_size);

        // call jni
        return Holder.INSTANCE.ecPair(g1_point_list, g2_point_list);
    }

    public static int ping() {
        return Holder.INSTANCE.ping();
    }
//	private static boolean loadEmbeddedLibrary() {
//        boolean usingEmbedded = false;
//        String libsFromProps = System.getProperty("bn_jni-native");
//        String[] libs;
//        if (libsFromProps == null) {
//            libs = new String[]{"libbn_jni.so", "libbn_jni.dylib", "bn_jni.dll"};
//        } else {
//            libs = libsFromProps.split(",");
//        }
//
//
//        StringBuilder url = new StringBuilder();
//        url.append(System.getProperty("user.dir"));
//        String sep = System.getProperty("file.separator");
//        url.append(sep).append("src").append(sep).append("main").append(sep).append("scala").append(sep).append("rep").append(sep).append("zk").append(sep).append("native").append(sep);
//        URL nativeLibraryUrl = null;
//        String[] var5 = libs;
//        int var6 = libs.length;
//
//        for(int var7 = 0; var7 < var6; ++var7) {
//            String lib = var5[var7];
//            nativeLibraryUrl = AltBn128.class.getResource(url.toString() + lib);
//            if (nativeLibraryUrl != null) {
//                break;
//            }
//        }
//
//        if (nativeLibraryUrl != null) {
//            try {
//                File libfile = File.createTempFile("bn_jni", ".lib");
//                libfile.deleteOnExit();
//                InputStream in = nativeLibraryUrl.openStream();
//                OutputStream out = new BufferedOutputStream(new FileOutputStream(libfile));
//                //int len = false;
//                byte[] buffer = new byte[8192];
//
//                int len;
//                while((len = in.read(buffer)) > -1) {
//                    out.write(buffer, 0, len);
//                }
//
//                out.close();
//                in.close();
//                System.load(libfile.getAbsolutePath());
//                usingEmbedded = true;
//            } catch (IOException var10) {
//            }
//        }
//
//        return usingEmbedded;
//    }
}