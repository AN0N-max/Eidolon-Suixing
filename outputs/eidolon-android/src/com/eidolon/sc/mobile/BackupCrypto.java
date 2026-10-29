package com.eidolon.sc.mobile;

import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

final class BackupCrypto {
    static final byte[] HEADER={'E','I','D','O','B','K',1};
    static final int MAX_BYTES=16*1024*1024;
    private static SecretKeySpec derive(char[] password,byte[] salt)throws Exception {
        PBEKeySpec spec=new PBEKeySpec(password,salt,210000,256);
        try{return new SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(),"AES");}
        finally{spec.clearPassword();}
    }
    static byte[] encrypt(byte[] content,char[] password)throws Exception {
        if(password.length<8)throw new Exception("密码至少需要 8 个字符");
        if(content.length>MAX_BYTES-51)throw new Exception("备份文件过大");
        byte[] salt=new byte[16],iv=new byte[12];SecureRandom random=new SecureRandom();random.nextBytes(salt);random.nextBytes(iv);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,derive(password,salt),new GCMParameterSpec(128,iv));cipher.updateAAD(HEADER);
        byte[] encrypted=cipher.doFinal(content),result=new byte[35+encrypted.length];
        System.arraycopy(HEADER,0,result,0,7);System.arraycopy(salt,0,result,7,16);System.arraycopy(iv,0,result,23,12);System.arraycopy(encrypted,0,result,35,encrypted.length);return result;
    }
    static byte[] decrypt(byte[] content,char[] password)throws Exception {
        if(content.length<51 || content.length>MAX_BYTES || !Arrays.equals(HEADER,Arrays.copyOf(content,7)))throw new Exception("不是受支持的加密聊天备份");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,derive(password,Arrays.copyOfRange(content,7,23)),new GCMParameterSpec(128,Arrays.copyOfRange(content,23,35)));cipher.updateAAD(HEADER);
        return cipher.doFinal(content,35,content.length-35);
    }
}
