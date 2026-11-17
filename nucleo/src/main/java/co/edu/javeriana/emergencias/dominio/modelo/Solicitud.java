
package co.edu.javeriana.emergencias.dominio.modelo;



public class Solicitud {

    private final Long id;
    private final String descripcionDesastre;
    private final String tipoRecurso;
    private final int cantidad;
    private final Prioridad prioridad;
    private final EstadoSolicitud estado;

    public Solicitud(
            Long id,
            String descripcionDesastre,
            String tipoRecurso,
            int cantidad,
            Prioridad prioridad,
            EstadoSolicitud estado) {

        this.id = id;
        this.descripcionDesastre = descripcionDesastre;
        this.tipoRecurso = tipoRecurso;
        this.cantidad = cantidad;
        this.prioridad = prioridad;
        this.estado = estado;
    }

    public Long getId() {
        return id;
    }

    public String getDescripcionDesastre() {
        return descripcionDesastre;
    }

    public String getTipoRecurso() {
        return tipoRecurso;
    }

    public int getCantidad() {
        return cantidad;
    }

    public Prioridad getPrioridad() {
        return prioridad;
    }

    public EstadoSolicitud getEstado() {
        return estado;
    }

    public Solicitud conId(Long nuevoId) {
        return new Solicitud(
                nuevoId,
                descripcionDesastre,
                tipoRecurso,
                cantidad,
                prioridad,
                estado
        );
    }

    public Solicitud conEstado(EstadoSolicitud nuevoEstado) {
        return new Solicitud(
                id,
                descripcionDesastre,
                tipoRecurso,
                cantidad,
                prioridad,
                nuevoEstado
        );
    }
}
