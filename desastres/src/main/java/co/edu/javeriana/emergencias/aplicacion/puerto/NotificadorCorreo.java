package co.edu.javeriana.emergencias.aplicacion.puerto;

import co.edu.javeriana.emergencias.dominio.desastres.*;

public interface NotificadorCorreo {
    void enviar(Desastre desastre, Sistema destinatario);
}
