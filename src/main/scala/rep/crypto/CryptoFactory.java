package rep.crypto;

import cn.com.tsg.jce.provider.TsgJceProvider;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import rep.app.conf.SystemProfile;

import java.security.*;

/**
 * 实例工厂
 * @author created by zyf
 * @date 2018-10-17
 */
public class CryptoFactory {

    static {
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
        Security.addProvider(new TsgJceProvider());
    }

    //TODO 算法为配置项
    private static String signatureAlgorithm = SystemProfile.getSignatureAlgorithm();  // 默认为开源选项算法
    private static String digestAlgorithm = SystemProfile.getDigestAlogorithm();  // 默认为开源选项算法
//    private static String defaultAlgorithm = "SHA1withECDSA";  // 默认为开源选项算法
//    private static String defaultDigest = "SHA-256";  // 默认为开源选项算法

    /**
     * 获取签名实例
     * return the Signature Instance
     * @return
     * @throws NoSuchAlgorithmException
     */
    public static Signature getSignatureInstance() throws NoSuchAlgorithmException {
        return Signature.getInstance(signatureAlgorithm);
    }

    /**
     * 获取散列杂凑实例
     * return the Digest Instance
     * @return
     * @throws NoSuchAlgorithmException
     */
    public static MessageDigest getDigestInstance() throws NoSuchAlgorithmException {
        return MessageDigest.getInstance(digestAlgorithm);
    }


    /**
     * return the signature algorithm
     * @return
     */
    public static String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    /**
     * set the signature algorithm
     * @param signatureAlgorithm
     */
    public static void setSignatureAlgorithm(String signatureAlgorithm) {
        CryptoFactory.signatureAlgorithm = signatureAlgorithm;
    }

    /**
     * get the digest algorithm
     * @return
     */
    public static String getDigestAlgorithm() {
        return digestAlgorithm;
    }

    /**
     * set the digest algorithm
     * @param digestAlgorithm
     */
    public static void setDigestAlgorithm(String digestAlgorithm) {
        CryptoFactory.digestAlgorithm = digestAlgorithm;
    }

}
