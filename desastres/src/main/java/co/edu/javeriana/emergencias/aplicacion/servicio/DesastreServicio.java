package co.edu.javeriana.emergencias.aplicacion.servicio;

import co.edu.javeriana.emergencias.aplicacion.puerto.*;
import co.edu.javeriana.emergencias.dominio.desastres.*;
import jakarta.ejb.*;
import jakarta.inject.Inject;
import java.util.List;

@Stateless
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class DesastreServicio {
    @Inject RegistroRepositorio repositorio;
    @Inject NotificadorMensajes mensajes;
    @Inject NotificadorCorreo correo;

    public DesastreServicio() { }
    public DesastreServicio(RegistroRepositorio repositorio, NotificadorMensajes mensajes, NotificadorCorreo correo) {
        this.repositorio = repositorio; this.mensajes = mensajes; this.correo = correo;
    }
    public boolean registrar(Desastre desastre) {
        if (desastre == null) throw new IllegalArgumentException("El desastre es obligatorio");
        desastre.validar();
        return repositorio.registrar(desastre);
    }
    public void registrarSistema(Sistema sistema) {
        sistema.validar();
        mensajes.suscribir(sistema);
        repositorio.registrarSistema(sistema);
    }
    public List<Sistema> sistemas() { return repositorio.sistemas(); }
    public List<Registro> listar() { return repositorio.listar(); }
    public Registro buscar(String id) { return repositorio.buscar(id).orElse(null); }
    public void despacharPendientes() {
        // La marca se persiste después del confirm. Un fallo intermedio puede repetir el ID, nunca omitirlo.
        for (Registro registro : repositorio.pendientes()) {
            mensajes.publicar(registro);
            repositorio.confirmarPublicacion(registro.desastre.id);
        }
    }
    public void enviarCorreos(String id) {
        Registro registro = repositorio.buscar(id).orElseThrow(() -> new IllegalArgumentException("Desastre no registrado"));
        for (Sistema sistema : registro.destinatarios) {
            if (!registro.correosEnviados.contains(sistema.id)) {
                correo.enviar(registro.desastre, sistema);
                repositorio.confirmarCorreo(id, sistema.id);
            }
        }
    }
}
