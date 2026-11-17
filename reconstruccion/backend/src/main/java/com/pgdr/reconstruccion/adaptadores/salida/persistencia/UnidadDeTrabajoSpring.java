package com.pgdr.reconstruccion.adaptadores.salida.persistencia;

import com.pgdr.reconstruccion.aplicacion.puertos.UnidadDeTrabajo;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Una transacción de base de datos por caso de uso: proyecto, auditoría y evento se confirman juntos. */
@Component
public class UnidadDeTrabajoSpring implements UnidadDeTrabajo {
    private final TransactionTemplate transaccion;

    public UnidadDeTrabajoSpring(PlatformTransactionManager gestor) {
        this.transaccion = new TransactionTemplate(gestor);
    }

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        return transaccion.execute(estado -> trabajo.get());
    }
}
