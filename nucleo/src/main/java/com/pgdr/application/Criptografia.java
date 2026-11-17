package com.pgdr.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Criptografia {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERACIONES = 600_000;
    private Criptografia() {}
    public static String sal(){byte[] b=new byte[16];RANDOM.nextBytes(b);return Base64.getEncoder().encodeToString(b);}
    public static String token(){byte[] b=new byte[32];RANDOM.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    public static String sha256(String s){
      try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}
      catch(Exception e){throw new IllegalStateException(e);}
    }
    public static String hashPassword(char[] password,String salB64){
      if(password==null||password.length<1||password.length>256) throw new IllegalArgumentException("Clave invalida");
      PBEKeySpec spec=new PBEKeySpec(password,Base64.getDecoder().decode(salB64),ITERACIONES,256);
      try {return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());}
      catch(Exception e){throw new IllegalStateException(e);}
      finally{spec.clearPassword();}
    }
    public static boolean comprobar(char[] password,String salt,String expected){
      String actual=hashPassword(password,salt);
      return MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),expected.getBytes(StandardCharsets.US_ASCII));
    }
}
