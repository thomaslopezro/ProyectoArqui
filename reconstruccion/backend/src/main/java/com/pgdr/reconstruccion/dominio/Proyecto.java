package com.pgdr.reconstruccion.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Proyecto de reparación o reconstrucción. Contiene las reglas RN-07 a RN-16 que dependen
 * solo de su propio estado. No conoce gRPC, la base de datos ni ningún framework.
 */
public class Proyecto {
    private final UUID id;
    private final UUID desastreId;
    private final String organizacion;
    private final String nombre;
    private final TipoProyecto tipo;
    private final List<UUID> evaluaciones;
    private EstadoProyecto estado;
    private BigDecimal presupuesto;
    private String fuenteFinanciacion;
    private String responsable;
    private String contratista;
    private LocalDate inicioPlan;
    private LocalDate finPlan;
    private int avance;
    private BigDecimal costoAcumulado;
    private final List<Seguimiento> seguimiento;
    private long version;

    private Proyecto(UUID id, UUID desastreId, String organizacion, String nombre, TipoProyecto tipo,
                     List<UUID> evaluaciones, EstadoProyecto estado, BigDecimal presupuesto,
                     String fuenteFinanciacion, String responsable, String contratista, LocalDate inicioPlan,
                     LocalDate finPlan, int avance, BigDecimal costoAcumulado, List<Seguimiento> seguimiento,
                     long version) {
        this.id = id;
        this.desastreId = desastreId;
        this.organizacion = organizacion;
        this.nombre = nombre;
        this.tipo = tipo;
        this.evaluaciones = List.copyOf(evaluaciones);
        this.estado = estado;
        this.presupuesto = presupuesto;
        this.fuenteFinanciacion = fuenteFinanciacion;
        this.responsable = responsable;
        this.contratista = contratista;
        this.inicioPlan = inicioPlan;
        this.finPlan = finPlan;
        this.avance = avance;
        this.costoAcumulado = costoAcumulado;
        this.seguimiento = new ArrayList<>(seguimiento);
        this.version = version;
    }

    /** CU-02. RN-07, RN-08 y elegibilidad de RN-02; RN-11 para el estado Propuesto. */
    public static Proyecto proponer(UUID id, UUID desastreId, String organizacion, String nombre,
                                    TipoProyecto tipoSolicitado, List<EvaluacionDano> evaluaciones) {
        if (enBlanco(nombre)) throw new ReglaVioladaException("RN-11", "El proyecto necesita un nombre.");
        if (evaluaciones == null || evaluaciones.isEmpty())
            throw new ReglaVioladaException("RN-07", "El proyecto debe nacer de al menos una evaluación de daño.");
        boolean destruccionTotal = false;
        List<UUID> ids = new ArrayList<>();
        for (EvaluacionDano e : evaluaciones) {
            if (!e.desastreId().equals(desastreId))
                throw new ReglaVioladaException("RN-07", "La evaluación " + e.id() + " pertenece a otro desastre.");
            if (!e.esElegible())
                throw new ReglaVioladaException("RN-02",
                        "La evaluación " + e.id() + " no está validada o su daño no requiere intervención.");
            if (e.nivel() == NivelDano.DESTRUCCION_TOTAL) destruccionTotal = true;
            ids.add(e.id());
        }
        TipoProyecto tipo = tipoSolicitado;
        if (tipo == null) tipo = destruccionTotal ? TipoProyecto.RECONSTRUCCION : TipoProyecto.REPARACION;
        if (destruccionTotal && tipo != TipoProyecto.RECONSTRUCCION)
            throw new ReglaVioladaException("RN-08", "Con destrucción total el proyecto debe ser de Reconstrucción.");
        return new Proyecto(id, desastreId, organizacion, nombre.trim(), tipo, ids, EstadoProyecto.PROPUESTO,
                null, null, null, null, null, null, 0, BigDecimal.ZERO, List.of(), 0);
    }

    /** Reconstruye un proyecto ya guardado. Solo para los adaptadores de persistencia. */
    public static Proyecto rehidratar(UUID id, UUID desastreId, String organizacion, String nombre, TipoProyecto tipo,
                                      List<UUID> evaluaciones, EstadoProyecto estado, BigDecimal presupuesto,
                                      String fuenteFinanciacion, String responsable, String contratista,
                                      LocalDate inicioPlan, LocalDate finPlan, int avance, BigDecimal costoAcumulado,
                                      List<Seguimiento> seguimiento, long version) {
        return new Proyecto(id, desastreId, organizacion, nombre, tipo, evaluaciones, estado, presupuesto,
                fuenteFinanciacion, responsable, contratista, inicioPlan, finPlan, avance, costoAcumulado,
                seguimiento, version);
    }

    /** CU-03. RN-11 (datos de Aprobado) y RN-12 (presupuesto mayor que cero). */
    public void aprobar(BigDecimal presupuesto, String fuenteFinanciacion, String responsable) {
        exigirTransicion(EstadoProyecto.APROBADO);
        if (presupuesto == null || presupuesto.signum() <= 0)
            throw new ReglaVioladaException("RN-12", "El presupuesto debe ser mayor que cero.");
        if (enBlanco(fuenteFinanciacion))
            throw new ReglaVioladaException("RN-11", "Para aprobar se requiere la fuente de financiación.");
        if (enBlanco(responsable))
            throw new ReglaVioladaException("RN-11", "Para aprobar se requiere un responsable.");
        this.presupuesto = presupuesto;
        this.fuenteFinanciacion = fuenteFinanciacion.trim();
        this.responsable = responsable.trim();
        this.estado = EstadoProyecto.APROBADO;
    }

    /** CU-04. RN-11 (datos de En ejecución). */
    public void iniciarEjecucion(String contratista, LocalDate inicio, LocalDate fin) {
        exigirTransicion(EstadoProyecto.EN_EJECUCION);
        if (estado != EstadoProyecto.APROBADO)
            throw new ReglaVioladaException("RN-16", "Un proyecto suspendido se reanuda, no se inicia de nuevo.");
        if (enBlanco(contratista))
            throw new ReglaVioladaException("RN-11", "Para iniciar la ejecución se requiere un contratista.");
        if (inicio == null || fin == null || !inicio.isBefore(fin))
            throw new ReglaVioladaException("RN-11", "El cronograma necesita un inicio anterior al fin.");
        this.contratista = contratista.trim();
        this.inicioPlan = inicio;
        this.finPlan = fin;
        this.estado = EstadoProyecto.EN_EJECUCION;
    }

    /** CU-06. RN-12. Devuelve el presupuesto anterior para la auditoría. */
    public BigDecimal modificarPresupuesto(BigDecimal nuevo, String motivo) {
        if (estado.esTerminal())
            throw new ReglaVioladaException("RN-12", "No se modifica el presupuesto de un proyecto " + estado + ".");
        if (presupuesto == null)
            throw new ReglaVioladaException("RN-12", "El proyecto aún no tiene presupuesto; se asigna al aprobarlo.");
        if (nuevo == null || nuevo.signum() <= 0)
            throw new ReglaVioladaException("RN-12", "El presupuesto debe ser mayor que cero.");
        if (enBlanco(motivo))
            throw new ReglaVioladaException("RN-12", "Todo cambio de presupuesto exige un motivo.");
        BigDecimal anterior = this.presupuesto;
        this.presupuesto = nuevo;
        return anterior;
    }

    /** CU-05. RN-13. Una disminución solo se acepta como corrección con motivo. */
    public int registrarAvance(int porcentaje, String descripcion, String motivoCorreccion, Instant momento,
                               String usuario) {
        if (estado != EstadoProyecto.EN_EJECUCION)
            throw new ReglaVioladaException("RN-13", "El avance solo se registra con el proyecto En ejecución.");
        if (porcentaje < 0 || porcentaje > 100)
            throw new ReglaVioladaException("RN-13", "El avance va de 0 a 100 %.");
        if (porcentaje < avance && enBlanco(motivoCorreccion))
            throw new ReglaVioladaException("RN-13", "El avance no disminuye salvo corrección con motivo.");
        int anterior = this.avance;
        this.avance = porcentaje;
        String texto = porcentaje < anterior ? "Corrección: " + motivoCorreccion.trim() : nulo(descripcion);
        seguimiento.add(new Seguimiento(Seguimiento.Tipo.AVANCE, BigDecimal.valueOf(porcentaje), texto, momento, usuario));
        return anterior;
    }

    /** CU-05. RN-14. Devuelve true si con este costo el proyecto pasa a tener sobrecosto. */
    public boolean registrarCosto(BigDecimal valor, String descripcion, Instant momento, String usuario) {
        if (estado != EstadoProyecto.EN_EJECUCION)
            throw new ReglaVioladaException("RN-14", "Los costos solo se registran con el proyecto En ejecución.");
        if (valor == null || valor.signum() <= 0)
            throw new ReglaVioladaException("RN-14", "El costo debe ser mayor que cero.");
        boolean antes = tieneSobrecosto();
        this.costoAcumulado = this.costoAcumulado.add(valor);
        seguimiento.add(new Seguimiento(Seguimiento.Tipo.COSTO, valor, nulo(descripcion), momento, usuario));
        return !antes && tieneSobrecosto();
    }

    /** CU-05. Documentación y evidencias; no se aceptan en estados terminales. */
    public void adjuntarEvidencia(String descripcion, Instant momento, String usuario) {
        if (estado.esTerminal())
            throw new ReglaVioladaException("RN-16", "Un proyecto " + estado + " no admite más evidencias.");
        if (enBlanco(descripcion))
            throw new ReglaVioladaException("RN-15", "La evidencia necesita una descripción o referencia.");
        seguimiento.add(new Seguimiento(Seguimiento.Tipo.EVIDENCIA, null, descripcion.trim(), momento, usuario));
    }

    /** CU-07. Suspender, reanudar, finalizar o cancelar. RN-15 y RN-16. Devuelve el estado anterior. */
    public EstadoProyecto cambiarEstado(EstadoProyecto destino, String motivo) {
        if (estado.esTerminal())
            throw new ReglaVioladaException("RN-16", "Un proyecto " + estado + " es terminal y no cambia de estado.");
        if (destino == EstadoProyecto.APROBADO)
            throw new ReglaVioladaException("RN-11", "La aprobación exige presupuesto, financiación y responsable.");
        if (destino == EstadoProyecto.EN_EJECUCION && estado != EstadoProyecto.SUSPENDIDO)
            throw new ReglaVioladaException("RN-11", "El inicio de la ejecución exige contratista y cronograma.");
        exigirTransicion(destino);
        if ((destino == EstadoProyecto.SUSPENDIDO || destino == EstadoProyecto.CANCELADO) && enBlanco(motivo))
            throw new ReglaVioladaException("RN-16", "Suspender y cancelar exigen un motivo.");
        if (destino == EstadoProyecto.FINALIZADO) {
            if (avance < 100)
                throw new ReglaVioladaException("RN-15", "Finalizar exige un avance de 100 %.");
            if (seguimiento.stream().noneMatch(s -> s.tipo() == Seguimiento.Tipo.EVIDENCIA))
                throw new ReglaVioladaException("RN-15", "Finalizar exige al menos una evidencia.");
        }
        EstadoProyecto anterior = this.estado;
        this.estado = destino;
        return anterior;
    }

    private void exigirTransicion(EstadoProyecto destino) {
        if (!estado.puedePasarA(destino))
            throw new ReglaVioladaException("RN-16", "Transición no permitida: " + estado + " a " + destino + ".");
    }

    /** RN-14: el costo acumulado supera el presupuesto. */
    public boolean tieneSobrecosto() {
        return presupuesto != null && costoAcumulado.compareTo(presupuesto) > 0;
    }

    /** RN-09: una evaluación queda ocupada por cualquier proyecto que no esté cancelado. */
    public boolean estaActivo() {
        return estado != EstadoProyecto.CANCELADO;
    }

    public void marcarGuardado() {
        this.version++;
    }

    private static boolean enBlanco(String s) {
        return s == null || s.isBlank();
    }

    private static String nulo(String s) {
        return enBlanco(s) ? null : s.trim();
    }

    public UUID id() { return id; }
    public UUID desastreId() { return desastreId; }
    public String organizacion() { return organizacion; }
    public String nombre() { return nombre; }
    public TipoProyecto tipo() { return tipo; }
    public List<UUID> evaluaciones() { return evaluaciones; }
    public EstadoProyecto estado() { return estado; }
    public BigDecimal presupuesto() { return presupuesto; }
    public String fuenteFinanciacion() { return fuenteFinanciacion; }
    public String responsable() { return responsable; }
    public String contratista() { return contratista; }
    public LocalDate inicioPlan() { return inicioPlan; }
    public LocalDate finPlan() { return finPlan; }
    public int avance() { return avance; }
    public BigDecimal costoAcumulado() { return costoAcumulado; }
    public List<Seguimiento> seguimiento() { return Collections.unmodifiableList(seguimiento); }
    public long version() { return version; }
}
