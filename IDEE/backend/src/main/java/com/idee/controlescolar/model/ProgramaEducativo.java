package com.idee.controlescolar.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

@Entity
@Table(name = "programas_educativos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ProgramaEducativo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_programa", unique = true)
    private String idPrograma;

    @Column(nullable = false, unique = true)
    private String clave;

    @Column(name = "clave_dgp")
    private String claveDgp;

    /**
     * Solo para JSON: nombre del plantel en catálogo institucional (resuelto por {@link #claveDgp}).
     */
    @Transient
    private String nombrePlantel;

    @Column(nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoPrograma tipoPrograma;

    /**
     * Cantidad de periodos/niveles del plan (ej. 6 semestres, 8 cuatrimestres). Debe coincidir con los
     * periodos del plan y con las asignaturas asignadas a cada número de periodo.
     */
    private Integer duracionPeriodos;

    /** Tipo de cada periodo del plan: semestre, cuatrimestre, etc. */
    @Enumerated(EnumType.STRING)
    private TipoPeriodo tipoPeriodo;

    @Enumerated(EnumType.STRING)
    private Modalidad modalidad;

    private Integer creditosTotales;

    @Column(name = "plan_estudio")
    private String planEstudio;

    private String rvoe;

    private LocalDate fechaRvoe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstatusPrograma estatus = EstatusPrograma.ACTIVO;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @LastModifiedDate
    private LocalDateTime fechaActualizacion;

    // Relaciones (no se serializan en JSON: el frontend carga periodos/asignaturas/alumnos vía endpoints dedicados)
    @OneToMany(mappedBy = "programa", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Periodo> periodos = new ArrayList<>();

    @OneToMany(mappedBy = "programa")
    @JsonIgnore
    private List<Asignatura> asignaturas = new ArrayList<>();

    // Alumnos se relacionan a programas vía AlumnoPrograma (alumno_programa).

    /**
     * Tipos de programa / nivel de estudios con IDs oficiales del catálogo SEP (inalterables).
     * Licenciatura=81, Maestría=82, Profesional Asociado=83, Técnico Superior=84, Especialidad=85, Doctorado=95.
     */
    public enum TipoPrograma {
        LICENCIATURA("81"),
        MAESTRIA("82"),
        PROFESIONAL_ASOCIADO("83"),
        TECNICO_SUPERIOR("84"),
        ESPECIALIDAD("85"),
        DOCTORADO("95"),
        EXTRACURRICULAR("81");

        private final String idOficial;

        TipoPrograma(String idOficial) {
            this.idOficial = idOficial;
        }

        public String getIdOficial() {
            return idOficial;
        }
    }

    /**
     * Tipos de periodo con IDs oficiales del catálogo SEP (inalterables).
     * Semestre=91, Cuatrimestre=93, Tetramestre=94, Trimestre=260.
     */
    public enum TipoPeriodo {
        /**
         * Plan con cadencia semanal (en este sistema se maneja como 2 periodos por año: AÑO-1 y AÑO-2).
         */
        SEMANAL("0"),
        SEMESTRE("91"),
        CUATRIMESTRE("93"),
        TETRAMESTRE("94"),
        TRIMESTRE("260");

        private final String idOficial;

        TipoPeriodo(String idOficial) {
            this.idOficial = idOficial;
        }

        public String getIdOficial() {
            return idOficial;
        }
    }

    public enum Modalidad {
        ESCOLARIZADO,
        MIXTO,
        EN_LINEA
    }

    public enum EstatusPrograma {
        ACTIVO,
        INACTIVO
    }
}
