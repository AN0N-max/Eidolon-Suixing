package com.eidolon.sc.mobile;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class Vault {
    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        String alias = "eidolon-api-v1";
        if (!store.containsAlias(alias)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (SecretKey) store.getKey(alias, null);
    }
    static void save(Context context, String value) throws Exception {
        save(context,"key",value);
    }
    static void save(Context context, String slot, String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        String encoded = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(value.getBytes("UTF-8")), Base64.NO_WRAP);
        if (!context.getSharedPreferences("vault", 0).edit().putString(slot, encoded).putBoolean("configured/"+slot,!value.isEmpty()).commit()) throw new Exception("无法保存密钥");
    }
    static boolean configured(Context context,String slot){
        android.content.SharedPreferences prefs=context.getSharedPreferences("vault",0);if(prefs.contains("configured/"+slot))return prefs.getBoolean("configured/"+slot,false);
        // AES-GCM ciphertext of a legacy cleared key contains only its 16-byte tag.
        String[] parts=prefs.getString(slot,"").split(":");try{return parts.length==2&&Base64.decode(parts[1],Base64.NO_WRAP).length>16;}catch(IllegalArgumentException e){return false;}
    }
    static String read(Context context) throws Exception {
        return read(context,"key");
    }
    static String read(Context context, String slot) throws Exception {
        String encoded = context.getSharedPreferences("vault", 0).getString(slot, "");
        if (encoded.isEmpty()) return "";
        String[] parts = encoded.split(":");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), "UTF-8");
    }
}
