package cn.kongj.blackstone;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Password stays in private app storage, encrypted by a non-exportable device key. */
final class LoginCredentials {
    private static final String ALIAS="blackstone-login-password";
    static boolean saved(Context c){return c.getSharedPreferences("login",0).contains("ciphertext");}
    static boolean configured(Context c){android.content.SharedPreferences prefs=c.getSharedPreferences("login",0);return prefs.contains("ciphertext")&&prefs.contains("phoneCiphertext");}
    static void saveAccount(Context c,String phone,String password)throws Exception {
        if(!phone.matches("1[0-9]{10}"))throw new IllegalArgumentException("请输入11位手机号");
        if(password.isEmpty())throw new IllegalArgumentException("请输入登录密码");
        Cipher secretCipher=Cipher.getInstance("AES/GCM/NoPadding"),phoneCipher=Cipher.getInstance("AES/GCM/NoPadding");
        SecretKey key=key();secretCipher.init(Cipher.ENCRYPT_MODE,key);phoneCipher.init(Cipher.ENCRYPT_MODE,key);
        byte[] secretBytes=secretCipher.doFinal(password.getBytes("UTF-8")),phoneBytes=phoneCipher.doFinal(phone.getBytes("UTF-8"));
        c.getSharedPreferences("login",0).edit()
            .putString("iv",Base64.encodeToString(secretCipher.getIV(),Base64.NO_WRAP)).putString("ciphertext",Base64.encodeToString(secretBytes,Base64.NO_WRAP))
            .putString("phoneIv",Base64.encodeToString(phoneCipher.getIV(),Base64.NO_WRAP)).putString("phoneCiphertext",Base64.encodeToString(phoneBytes,Base64.NO_WRAP)).apply();
    }
    private static SecretKey key()throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    static void save(Context c,String password)throws Exception {
        if(password.isEmpty())throw new IllegalArgumentException("请输入登录密码");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encoded=cipher.doFinal(password.getBytes("UTF-8"));
        c.getSharedPreferences("login",0).edit().putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
            .putString("ciphertext",Base64.encodeToString(encoded,Base64.NO_WRAP)).apply();
    }
    static String load(Context c)throws Exception {
        android.content.SharedPreferences prefs=c.getSharedPreferences("login",0);
        if(!saved(c))throw new Exception("请先在助手中保存登录密码");
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(prefs.getString("iv",""),Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(prefs.getString("ciphertext",""),Base64.NO_WRAP)),"UTF-8");
        }catch(Exception e){throw new Exception("本机登录密码无法读取，请重新保存密码");}
    }
    static void clear(Context c){c.getSharedPreferences("login",0).edit().clear().apply();}
    static void savePhone(Context c,String phone)throws Exception {
        if(!phone.matches("1[0-9]{10}"))throw new IllegalArgumentException("请输入11位手机号");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encoded=cipher.doFinal(phone.getBytes("UTF-8"));
        c.getSharedPreferences("login",0).edit().putString("phoneIv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
            .putString("phoneCiphertext",Base64.encodeToString(encoded,Base64.NO_WRAP)).apply();
    }
    static String phone(Context c)throws Exception {
        android.content.SharedPreferences prefs=c.getSharedPreferences("login",0);
        if(!prefs.contains("phoneCiphertext"))return "";
        try{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(prefs.getString("phoneIv",""),Base64.NO_WRAP)));return new String(cipher.doFinal(Base64.decode(prefs.getString("phoneCiphertext",""),Base64.NO_WRAP)),"UTF-8");}
        catch(Exception e){throw new Exception("本机账号无法读取，请重新保存手机号");}
    }
}
