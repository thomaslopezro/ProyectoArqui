package co.edu.javeriana.emergencias.infraestructura.mensajeria;

import co.edu.javeriana.emergencias.aplicacion.servicio.DesastreServicio;
import co.edu.javeriana.emergencias.dominio.desastres.Desastre;
import co.edu.javeriana.emergencias.infraestructura.persistencia.Json;
import jakarta.ejb.*;
import java.util.logging.Logger;

@Singleton
@Startup
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ProcesadorMensajes {
    @EJB RabbitMensajes broker;
    @EJB DesastreServicio servicio;
    private static final Logger LOG = Logger.getLogger(ProcesadorMensajes.class.getName());

    @Schedule(hour="*", minute="*", second="*/1", persistent=false)
    public void procesar() {
        broker.consumir(RabbitMensajes.REGISTRO, texto -> {
            Desastre desastre = Json.leer(texto, Desastre.class);
            if (desastre == null) throw new IllegalArgumentException("Mensaje vacio");
            desastre.validar();
            servicio.registrar(desastre);
        });
        try { servicio.despacharPendientes(); }
        catch (Exception error) { LOG.warning("Notificaciones pendientes; se reintentaran en el siguiente ciclo"); }
        broker.consumir(RabbitMensajes.CORREO, texto -> {
            Desastre desastre = Json.leer(texto, Desastre.class);
            if (desastre == null) throw new IllegalArgumentException("Mensaje vacio");
            desastre.validar();
            servicio.enviarCorreos(desastre.id);
        });
    }
}
