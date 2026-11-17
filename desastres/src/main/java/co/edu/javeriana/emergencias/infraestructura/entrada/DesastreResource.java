package co.edu.javeriana.emergencias.infraestructura.entrada;

import co.edu.javeriana.emergencias.aplicacion.servicio.DesastreServicio;
import co.edu.javeriana.emergencias.dominio.desastres.*;
import co.edu.javeriana.emergencias.infraestructura.mensajeria.RabbitMensajes;
import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.net.URI;
import java.util.*;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DesastreResource {
    @EJB DesastreServicio servicio;
    @EJB RabbitMensajes broker;
    @EJB co.edu.javeriana.emergencias.infraestructura.persistencia.RepositorioArchivo archivo;
    @POST @Path("migrar-organizacion")
    public Response migrar(Map<String,String> body,@HeaderParam("X-Integracion-Key") String key){
      if(key==null || !key.equals(System.getenv("INTEGRACION_KEY")))return Response.status(401).build();
      String org=body.get("organizacionId");java.util.UUID.fromString(org);archivo.migrarOrganizacion(org);return Response.noContent().build();
    }

    @GET @Path("salud")
    public Response salud() {
        broker.preparar();
        servicio.listar();
        return Response.ok(Map.of("estado", "LISTO")).build();
    }
    @POST @Path("desastres")
    public Response recibir(Desastre desastre, @Context UriInfo uri) {
        if (desastre == null) throw new BadRequestException("Debe enviar el desastre");
        desastre.validar();
        broker.recibir(desastre);
        // El 202 confirma almacenamiento en el broker; el registro de negocio ocurre después.
        return Response.accepted(Map.of("id", desastre.id, "estado", "RECIBIDO_EN_COLA"))
                .location(uri.getBaseUriBuilder().path("desastres").path(desastre.id).build()).build();
    }
    @GET @Path("desastres") public List<Registro> listar() { return servicio.listar(); }
    @GET @Path("desastres/{id}") public Registro buscar(@PathParam("id") String id) {
        Registro resultado = servicio.buscar(id);
        if (resultado == null) throw new NotFoundException("El desastre aun no esta registrado");
        return resultado;
    }
    @POST @Path("sistemas") public Response registrarSistema(Sistema sistema, @Context UriInfo uri) {
        if (sistema == null) throw new BadRequestException("Debe enviar el sistema");
        sistema.validar();
        // Detectar conflictos antes de llamar al EJB facilita una respuesta HTTP explícita.
        for (Sistema actual : servicio.sistemas()) {
            if (actual.id.equals(sistema.id) && (!Objects.equals(actual.organizacionId,sistema.organizacionId) || !actual.nombre.equals(sistema.nombre) || !actual.correo.equals(sistema.correo)))
                throw new ClientErrorException("El ID del sistema ya esta registrado con otros datos", 409);
        }
        servicio.registrarSistema(sistema);
        return Response.created(uri.getBaseUriBuilder().path("sistemas").path(sistema.id).build())
                .entity(Map.of("sistema", sistema, "cola", sistema.cola(), "exchange", RabbitMensajes.SALIDA)).build();
    }
    @GET @Path("sistemas") public List<Sistema> sistemas() { return servicio.sistemas(); }
}
