package com.pgdr.application;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class HuellasTest {
    @Test void separadoresNoOcultanCambios(){
      assertNotEquals(Huellas.desastre("Parte A\nParte B","Parte C",2),Huellas.desastre("Parte A","Parte B\nParte C",2));
    }
    @Test void hashDifiereSiCambiaContenido(){
      assertNotEquals(Huellas.desastre("Sismo","A",2),Huellas.desastre("Sismo","A",3));
    }
    @Test void coordenadasConEscalasEquivalentesCompartenHuella(){
      String a=Huellas.afectacion("x",new BigDecimal("4.600"),new BigDecimal("-74.100"),3,null);
      String b=Huellas.afectacion("x",new BigDecimal("4.6"),new BigDecimal("-74.1"),3,null);
      assertEquals(a,b);
    }
}
