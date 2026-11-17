package co.edu.javeriana.emergencias.aplicacion.puerto;

import co.edu.javeriana.emergencias.dominio.desastres.*;

public interface NotificadorMensajes {
    void publicar(Registro registro);
    void suscribir(Sistema sistema);
}
