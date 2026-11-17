package co.edu.javeriana.emergencias.aplicacion.puerto;

import java.util.List;
import java.util.Optional;
import co.edu.javeriana.emergencias.dominio.desastres.*;

public interface RegistroRepositorio {
    boolean registrar(Desastre desastre);
    List<Registro> listar();
    Optional<Registro> buscar(String id);
    List<Registro> pendientes();
    void confirmarPublicacion(String id);
    void confirmarCorreo(String id, String sistemaId);
    void registrarSistema(Sistema sistema);
    List<Sistema> sistemas();
}
