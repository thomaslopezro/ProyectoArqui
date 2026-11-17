package com.pgdr.application;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CriptografiaTest {
    @Test void hashPasswordYComparacion(){
      String salt=Criptografia.sal(),clave="UnaClaveLarga2026!";
      String hash=Criptografia.hashPassword(clave.toCharArray(),salt);
      assertTrue(Criptografia.comprobar(clave.toCharArray(),salt,hash));
      assertFalse(Criptografia.comprobar("otra clave".toCharArray(),salt,hash));
    }
    @Test void tokenAleatorioNoSeRepite(){assertNotEquals(Criptografia.token(),Criptografia.token());}
    @Test void hashEstable(){assertEquals(Huellas.desastre("Test","A",2),Huellas.desastre(" Test ","A",2));}
}
