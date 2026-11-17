package com.pgdr.reconstruccion.aplicacion.puertos;

import java.util.function.Supplier;

/** Ejecuta el trabajo en una sola transacción: se confirma todo o no se confirma nada. */
public interface UnidadDeTrabajo {
    <T> T ejecutar(Supplier<T> trabajo);
}
