package rep.network.consensus.asyncconsensus.common.crypto;

import org.apache.commons.lang3.RandomStringUtils;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.Security;
import java.io.UnsupportedEncodingException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;


/**
 * @author jiangbuyun
 * @version	0.1
 * @since	2021-04-17
 * @category	实现AES对称加密。
 */
public class AesCrypto {
    private static String IV = "Ry6ykCKhCBoQO5CC";//RandomStringUtils.randomAlphanumeric(16);
    private static String charset = "UTF-8";

    //确认采用AES256bit
    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public static byte[] generatePasswordIn32Bit(){
        byte[] password = null;
        String key = RandomStringUtils.randomAlphanumeric(32);
        try {
            password = key.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return password;
    }

    public static byte[] encryptInAES256(byte[] key,byte[] data){
        return compute(key,data,Cipher.ENCRYPT_MODE);
    }

    public static byte[] encryptInAES256(String key,String data){
        byte[] result = null;
        try {
            byte[] keyB = key.getBytes(charset);
            byte[] dataB = data.getBytes(charset);
            result = encryptInAES256(keyB,dataB);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public static byte[] decryptInAES256(byte[] key,byte[] data){
        return compute(key,data,Cipher.DECRYPT_MODE);
    }

    public static byte[] decryptInAES256(String key,String data){
        byte[] result = null;
        try {
            byte[] keyB = key.getBytes(charset);
            byte[] dataB = data.getBytes(charset);
            result = decryptInAES256(keyB,dataB);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private static byte[] compute(byte[] key,byte[] data,int mode){
        byte[] result = null;
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");//PKCS7Padding,PKCS5Padding
            byte[] param = IV.getBytes(charset);
            IvParameterSpec ivSpec = new IvParameterSpec(param);
            cipher.init(mode, keySpec, ivSpec);
            result = cipher.doFinal(data);
        }catch(Exception e){
            e.printStackTrace();
        }
        return result;
    }

    public static  void main(String[] args){
        //System.out.println(RandomStringUtils.randomAlphanumeric(16));
        byte[] key = generatePasswordIn32Bit();
        byte[] data = null;
        try {
            data = "sfsdfjklsfjllsdjfklwer9u23234你好！！###".getBytes(charset);
        } catch (Exception e) {
            e.printStackTrace();
        }
        byte[] endata = encryptInAES256(key,data);
        byte[] dedata = decryptInAES256(key,endata);
        try {
            System.out.println("source="+new String(data,charset));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        //System.out.println("endata="+endata);
        try {
            System.out.println("dedata="+new String(dedata,charset));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

}
