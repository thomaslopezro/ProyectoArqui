package com.pgdr.reconstruccion.adaptadores.salida.persistencia;

import com.pgdr.reconstruccion.aplicacion.puertos.Reloj;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class RelojSistema implements Reloj {
    @Override
    public Instant ahora() {
        return Instant.now();
    }
}
