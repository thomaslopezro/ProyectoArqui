package com.pgdr.reconstruccion.aplicacion.puertos;

import com.pgdr.reconstruccion.dominio.RegistroAuditoria;
import java.util.List;
import java.util.UUID;

/** Solo permite agregar y leer: la auditoría no se modifica ni se borra (RN-17). */
public interface RegistroDeAuditoria {
    void registrar(RegistroAuditoria registro);

    List<RegistroAuditoria> porEntidad(UUID entidadId);

    List<RegistroAuditoria> porDesastre(UUID desastreId, String organizacion);
}
