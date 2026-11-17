package com.pgdr.application;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class IdentidadTest {
    @Test void permisosPorRol(){
      UUID uid=UUID.randomUUID(),org=UUID.randomUUID();
      Identidad coordinador=new Identidad(uid,org,"c","COORDINADOR");
      Identidad evaluador=new Identidad(uid,org,"e","EVALUADOR");
      Identidad consulta=new Identidad(uid,org,"v","CONSULTA");
      assertTrue(coordinador.puedeRegistrarDesastre());assertTrue(coordinador.puedeRegistrarAfectacion());
      assertFalse(evaluador.puedeRegistrarDesastre());assertTrue(evaluador.puedeRegistrarAfectacion());
      assertFalse(consulta.puedeRegistrarDesastre());assertFalse(consulta.puedeRegistrarAfectacion());
    }
}
