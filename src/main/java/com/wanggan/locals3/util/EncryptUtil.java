package com.wanggan.locals3.util;

import com.wanggan.locals3.constant.S3Constant;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class EncryptUtil {
    public static String encryptByMD5(String str) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        //生成md5加密算法
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        md5.update(str.getBytes(S3Constant.UTF_8));
        byte b[] = md5.digest();
        int i;
        StringBuffer buf = new StringBuffer("");
        for (int j = 0; j < b.length; j++) {
            i = b[j];
            if (i < 0)
                i += 256;
            if (i < 16)
                buf.append("0");
            buf.append(Integer.toHexString(i));
        }
        String md5_32 = buf.toString();
        return md5_32;
    }

    private final static String DES = "DES";
    private final static String ENCODE = S3Constant.UTF_8;
    private final static String defaultKey = "LocalS3X";


    public static String encryptByDES(String data) throws Exception {
        byte[] bt = encrypt(data.getBytes(ENCODE), defaultKey.getBytes(ENCODE));
        String strs = Base64.getEncoder().encodeToString(bt);
        return strs;
    }

    public static String decryptByDES(String data) throws IOException, Exception {
        if (data == null)
            return null;
        byte[] buf = Base64.getDecoder().decode(data);;
        byte[] bt = decrypt(buf, defaultKey.getBytes(ENCODE));
        return new String(bt, ENCODE);
    }

    private static byte[] encrypt(byte[] data, byte[] key) throws Exception {
        SecureRandom sr = new SecureRandom();
        DESKeySpec dks = new DESKeySpec(key);
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(DES);
        SecretKey securekey = keyFactory.generateSecret(dks);
        Cipher cipher = Cipher.getInstance(DES);
        cipher.init(Cipher.ENCRYPT_MODE, securekey, sr);
        return cipher.doFinal(data);
    }

    private static byte[] decrypt(byte[] data, byte[] key) throws Exception {
        SecureRandom sr = new SecureRandom();
        DESKeySpec dks = new DESKeySpec(key);
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(DES);
        SecretKey secretkey = keyFactory.generateSecret(dks);
        Cipher cipher = Cipher.getInstance(DES);
        cipher.init(Cipher.DECRYPT_MODE, secretkey, sr);
        return cipher.doFinal(data);
    }
}
