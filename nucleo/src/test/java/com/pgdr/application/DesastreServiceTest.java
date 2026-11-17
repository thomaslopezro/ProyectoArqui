package com.pgdr.application;

import com.pgdr.domain.Desastre;
import com.pgdr.domain.AuditoriaDesastre;
import com.pgdr.infrastructure.DesastreRepository;
import com.pgdr.infrastructure.AuditoriaRepository;
import com.pgdr.infrastructure.OutboxRepository;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DesastreServiceTest {
    @Test void coordinadorPuedeRegistrar(){
      Identidad i=new Identidad(UUID.randomUUID(),UUID.randomUUID(),"coord","COORDINADOR");
      assertTrue(i.puedeRegistrarDesastre());
    }
    @Test void evaluadorNoPuedeRegistrarDesastre(){
      Identidad i=new Identidad(UUID.randomUUID(),UUID.randomUUID(),"eval","EVALUADOR");
      assertFalse(i.puedeRegistrarDesastre());
    }
    @Test void huellaMismaSolicitud(){
      assertEquals(Huellas.desastre(" Sismo ","observacion",3),Huellas.desastre("Sismo","observacion",3));
    }
    @Test void huellaDistintaSiNivelDifiere(){
      assertNotEquals(Huellas.desastre("Sismo",null,2),Huellas.desastre("Sismo",null,3));
    }
}
