package com.idee.controlescolar.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "alumnos")
@NamedEntityGraph(
        name = "Alumno.programasInscripcion",
        attributeNodes = {
                @NamedAttributeNode(value = "programasAsignados", subgraph = "subAlumnoPrograma"),
        },
        subgraphs = {
                @NamedSubgraph(
                        name = "subAlumnoPrograma",
                        attributeNodes = {
                                @NamedAttributeNode("programa"),
                                @NamedAttributeNode("periodoIngreso"),
                                @NamedAttributeNode("periodoAcademicoActual")
                        }
                )
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Alumno {

    /** Nombre del {@link NamedEntityGraph} para cargar inscripciones ({@code alumno_programa}) con programa y periodos. */
    public static final String GRAPH_PROGRAMAS_INSCRIPCION = "Alumno.programasInscripcion";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String matricula;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String apellidoPaterno;

    @Column(nullable = false)
    private String apellidoMaterno;

    @Column(nullable = false, unique = true)
    private String curp;

    private String correoInstitucional;

    private String correoPersonal;

    private String telefono;

    private String estado;

    private String codigoPostal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Sexo sexo;

    private LocalDate fechaNacimiento;

    // Contacto de emergencia
    private String nombreContactoEmergencia;

    private String telefonoContactoEmergencia;

    // Información académica
    /**
     * Programas a los que este alumno está inscrito/asignado (uso interno del instituto).
     * Cada asignación conserva su propio periodo de ingreso, estatus y progreso.
     */
    @OneToMany(mappedBy = "alumno", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties({"alumno"})
    private Set<AlumnoPrograma> programasAsignados = new LinkedHashSet<>();

    /**
     * Compatibilidad: algunas pantallas/DTOs antiguos enviaban un solo programa y un solo periodo de ingreso.
     * Estos campos se aplican como DEFAULT a la primera asignación (si existe) o crean una nueva asignación.
     */
    @Transient
    private String periodoIngresoInput;

    @Transient
    @JsonProperty(value = "periodoAcademicoId", access = Access.WRITE_ONLY)
    private Long periodoAcademicoId;

    /**
     * Cohortes internas del instituto. Un alumno puede pertenecer a múltiples cohortes.
     * (Uso administrativo; no agrupación oficial.)
     */
    @ManyToMany
    @JoinTable(
            name = "alumno_cohorte",
            joinColumns = @JoinColumn(name = "alumno_id"),
            inverseJoinColumns = @JoinColumn(name = "cohorte_id")
    )
    @JsonIgnoreProperties({"miembros", "hibernateLazyInitializer", "handler"})
    private Set<Cohorte> cohortes = new LinkedHashSet<>();

    @Enumerated(EnumType.STRING)
    private Turno turno;

    private String fotoUrl;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @LastModifiedDate
    private LocalDateTime fechaActualizacion;

    // Relaciones
    @OneToOne
    @JoinColumn(name = "usuario_id")
    @JsonIgnoreProperties({"alumno", "maestro", "personal"})
    @JsonIgnore
    private Usuario usuario;

    @OneToMany(mappedBy = "alumno", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Calificacion> calificaciones = new ArrayList<>();

    @OneToMany(mappedBy = "alumno", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Observacion> observaciones_list = new ArrayList<>();

    @OneToMany(mappedBy = "alumno", cascade = CascadeType.ALL)
    @JsonIgnoreProperties({"alumno"})
    private List<DocumentoAlumno> documentos = new ArrayList<>();

    @OneToMany(mappedBy = "alumno", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<SolicitudConstancia> solicitudes = new ArrayList<>();

    /**
     * Género con IDs oficiales del catálogo SEP (inalterables).
     * Mujer/Femenino=250, Hombre/Masculino=251.
     */
    public enum Sexo {
        MASCULINO("251"),
        FEMENINO("250");

        private final String idOficial;

        Sexo(String idOficial) {
            this.idOficial = idOficial;
        }

        public String getIdOficial() {
            return idOficial;
        }
    }

    public enum Turno {
        MATUTINO, VESPERTINO, MIXTO
    }

    /**
     * Enum histórico para reutilizar valores en UI/validaciones.
     * El estatus efectivo se gestiona por programa en {@link AlumnoPrograma}.
     */
    public enum EstatusMatricula {
        ACTIVA, BAJA_TEMPORAL, BAJA_DEFINITIVA, EGRESADO
    }

    // ---------------------------
    // Compatibilidad legacy (1 programa)
    // ---------------------------

    @Deprecated
    @JsonIgnore
    public ProgramaEducativo getPrograma() {
        return programasAsignados.stream()
                .map(AlumnoPrograma::getPrograma)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @Deprecated
    @JsonProperty(value = "programa", access = Access.WRITE_ONLY)
    public void setPrograma(ProgramaEducativo programa) {
        if (programa == null || programa.getId() == null) return;
        AlumnoPrograma ap = programasAsignados.stream()
                .filter(x -> x != null && x.getPrograma() != null && programa.getId().equals(x.getPrograma().getId()))
                .findFirst()
                .orElse(null);
        if (ap == null) {
            ap = new AlumnoPrograma();
            ap.setAlumno(this);
            ap.setPrograma(programa);
            ap.setId(new AlumnoProgramaId(this.id, programa.getId()));
            programasAsignados.add(ap);
        } else {
            ap.setPrograma(programa);
        }
    }

    @Deprecated
    @JsonIgnore
    public PeriodoAcademico getPeriodoAcademico() {
        return programasAsignados.stream()
                .map(AlumnoPrograma::getPeriodoIngreso)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @Deprecated
    public void setPeriodoAcademico(PeriodoAcademico pa) {
        AlumnoPrograma ap = programasAsignados.stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if (ap != null) {
            ap.setPeriodoIngreso(pa);
        }
    }

    @Deprecated
    @JsonIgnore
    public PeriodoAcademico getPeriodoAcademicoActual() {
        return programasAsignados.stream()
                .map(AlumnoPrograma::getPeriodoAcademicoActual)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @Deprecated
    public void setPeriodoAcademicoActual(PeriodoAcademico pa) {
        AlumnoPrograma ap = programasAsignados.stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if (ap != null) {
            ap.setPeriodoAcademicoActual(pa);
        }
    }

    @Deprecated
    @JsonIgnore
    public Integer getPeriodoCursando() {
        return programasAsignados.stream()
                .map(AlumnoPrograma::getPeriodoCursando)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @Deprecated
    @JsonProperty(value = "periodoCursando", access = Access.WRITE_ONLY)
    public void setPeriodoCursando(Integer v) {
        AlumnoPrograma ap = programasAsignados.stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if (ap != null) {
            ap.setPeriodoCursando(v);
        }
    }

    @Deprecated
    @JsonIgnore
    public EstatusMatricula getEstatusMatricula() {
        AlumnoPrograma ap = programasAsignados.stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if (ap == null || ap.getEstatusMatricula() == null) return null;
        try {
            return EstatusMatricula.valueOf(ap.getEstatusMatricula().name());
        } catch (Exception e) {
            return null;
        }
    }

    @Deprecated
    @JsonProperty(value = "estatusMatricula", access = Access.WRITE_ONLY)
    public void setEstatusMatricula(EstatusMatricula estatus) {
        AlumnoPrograma ap = programasAsignados.stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if (ap == null || estatus == null) return;
        try {
            ap.setEstatusMatricula(AlumnoPrograma.EstatusMatriculaPrograma.valueOf(estatus.name()));
        } catch (Exception ignored) {}
    }

    @JsonProperty("periodoIngreso")
    @Deprecated
    public String getPeriodoIngreso() {
        PeriodoAcademico pa = getPeriodoAcademico();
        return pa != null ? pa.getCodigo() : periodoIngresoInput;
    }

    @JsonProperty("periodoIngreso")
    @JsonAlias("cicloEscolar")
    @Deprecated
    public void setPeriodoIngreso(String codigo) {
        this.periodoIngresoInput = codigo;
    }

    public String getPeriodoIngresoInput() {
        return periodoIngresoInput;
    }

    // Helper methods
    public String getNombreCompleto() {
        return nombre + " " + apellidoPaterno + " " + apellidoMaterno;
    }

    // Progreso/periodos se gestiona por programa en AlumnoPrograma.
}
