package com.pgdr.reconstruccion.aplicacion.puertos;

import com.pgdr.reconstruccion.dominio.Desastre;
import com.pgdr.reconstruccion.dominio.EstadoDesastre;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Acceso al módulo de Gestión de desastres, dueño del estado del desastre. */
public interface GestionDesastres {
    Optional<Desastre> buscar(UUID id);

    List<Desastre> listar();

    void cambiarEstado(UUID id, EstadoDesastre nuevo);
}
