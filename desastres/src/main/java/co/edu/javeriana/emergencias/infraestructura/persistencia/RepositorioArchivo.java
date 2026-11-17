package co.edu.javeriana.emergencias.infraestructura.persistencia;

import co.edu.javeriana.emergencias.aplicacion.puerto.RegistroRepositorio;
import co.edu.javeriana.emergencias.dominio.desastres.*;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/** Un único escritor. Registro y salida pendiente se guardan juntos antes de confirmar al broker. */
@Singleton
@LocalBean
@Local(RegistroRepositorio.class)
@Startup
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class RepositorioArchivo implements RegistroRepositorio {
    public static class Estado {
        public List<Registro> registros = new ArrayList<>();
        public List<Sistema> sistemas = new ArrayList<>();
    }
    private Estado estado;
    private Path archivo;

    public RepositorioArchivo() { }
    public RepositorioArchivo(Path archivo) { this.archivo = archivo; cargar(); }

    @PostConstruct
    public void iniciar() {
        if (archivo == null) archivo = Path.of(System.getenv().getOrDefault("REGISTRO_ARCHIVO", "/opt/payara/registro/estado.json"));
        cargar();
    }
    private void cargar() {
        try {
            Files.createDirectories(archivo.getParent());
            estado = Files.exists(archivo) ? Json.leer(Files.readString(archivo), Estado.class) : new Estado();
            if (estado == null || estado.registros == null || estado.sistemas == null)
                throw new IllegalStateException("El registro persistido no tiene una estructura valida");
        } catch (Exception e) { throw new IllegalStateException("No se pudo abrir el registro de desastres", e); }
    }
    private Estado copia() { return Json.leer(Json.escribir(estado), Estado.class); }
    private void guardar(Consumer<Estado> cambio) {
        Estado siguiente = copia();
        cambio.accept(siguiente);
        Path temporal = archivo.resolveSibling(archivo.getFileName() + ".tmp");
        try {
            Files.writeString(temporal, Json.escribir(siguiente));
            try (var canal = FileChannel.open(temporal, StandardOpenOption.WRITE)) { canal.force(true); }
            Files.move(temporal, archivo, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            estado = siguiente;
        } catch (Exception e) { throw new IllegalStateException("No se pudo persistir el registro", e); }
    }
    @Override
    public boolean registrar(Desastre desastre) {
        if (estado.registros.stream().anyMatch(r -> r.desastre.id.equals(desastre.id))) return false;
        guardar(s -> {
            Registro r = new Registro();
            r.desastre = desastre;
            r.organizacionId = desastre.organizacionId;
            r.registradoEn = Instant.now().toString();
            r.destinatarios = new ArrayList<>(s.sistemas.stream().filter(x->Objects.equals(x.organizacionId,desastre.organizacionId)).toList());
            s.registros.add(r);
        });
        return true;
    }
    public void migrarOrganizacion(String org) {
        guardar(s->{
            for(Sistema x:s.sistemas)if(x.organizacionId==null)x.organizacionId=org;
            for(Registro x:s.registros)if(x.organizacionId==null){x.organizacionId=org;x.desastre.organizacionId=org;
                for(Sistema d:x.destinatarios)if(d.organizacionId==null)d.organizacionId=org;}
        });
    }
    @Override public List<Registro> listar() { return copia().registros; }
    @Override public Optional<Registro> buscar(String id) { return listar().stream().filter(r -> r.desastre.id.equals(id)).findFirst(); }
    @Override public List<Registro> pendientes() { return listar().stream().filter(r -> !r.notificacionPublicada).toList(); }
    @Override public List<Sistema> sistemas() { return copia().sistemas; }
    @Override public void confirmarPublicacion(String id) {
        guardar(s -> s.registros.stream().filter(r -> r.desastre.id.equals(id)).findFirst().orElseThrow().notificacionPublicada = true);
    }
    @Override public void confirmarCorreo(String id, String sistemaId) {
        guardar(s -> {
            Registro r = s.registros.stream().filter(x -> x.desastre.id.equals(id)).findFirst().orElseThrow();
            if (!r.correosEnviados.contains(sistemaId)) r.correosEnviados.add(sistemaId);
        });
    }
    @Override public void registrarSistema(Sistema sistema) {
        guardar(s -> {
            Optional<Sistema> anterior = s.sistemas.stream().filter(x -> x.id.equals(sistema.id)).findFirst();
            if (anterior.isPresent()) {
                if (!Objects.equals(anterior.get().organizacionId,sistema.organizacionId) || !anterior.get().nombre.equals(sistema.nombre) || !anterior.get().correo.equals(sistema.correo))
                    throw new IllegalArgumentException("El ID del sistema ya esta registrado con otros datos");
            } else s.sistemas.add(sistema);
        });
    }
}
