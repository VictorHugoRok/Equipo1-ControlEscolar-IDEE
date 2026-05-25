package com.idee.controlescolar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "calificaciones")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Calificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Puede ser null cuando estadoAprobacion es PENDIENTE (maestro aún no capturó). */
    @Column
    private Double calificacionFinal;

    private Double asistenciaPorcentaje;

    @Enumerated(EnumType.STRING)
    private TipoEvaluacion tipoEvaluacion = TipoEvaluacion.ORDINARIO;

    /** APROBADO/REPROBADO se calcula al confirmar por secretaría. Null antes de confirmar. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private EstatusCalificacion estatus;

    @Enumerated(EnumType.STRING)
    private EstadoAprobacion estadoAprobacion = EstadoAprobacion.PENDIENTE;

    @ManyToOne
    @JoinColumn(name = "periodo_academico_id")
    @JsonIgnoreProperties({"fechaCreacion", "fechaActualizacion", "hibernateLazyInitializer", "handler"})
    private PeriodoAcademico periodoAcademico;

    @Column(length = 20)
    private String periodo;  // Legacy; se sincroniza desde periodoAcademico

    @com.fasterxml.jackson.annotation.JsonProperty("periodo")
    public String getPeriodoDisplay() {
        return periodoAcademico != null ? periodoAcademico.getCodigo() : periodo;
    }

    /**
     * ID de observación del catálogo predefinido (70-78, 100-107).
     * Por defecto 100 (ORDINARIO). Reemplaza texto libre en observaciones.
     */
    @Column(name = "id_observaciones")
    private Integer idObservaciones;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Column(nullable = false)
    private Boolean confirmada = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @LastModifiedDate
    private LocalDateTime fechaActualizacion;

    // Relaciones
    @ManyToOne
    @JoinColumn(name = "alumno_id", nullable = false)
    @JsonIgnoreProperties({
            "calificaciones", "observaciones_list", "documentos", "solicitudes", "usuario",
            "programasAsignados", "cohortes", "hibernateLazyInitializer", "handler"
    })
    private Alumno alumno;

    @ManyToOne
    @JoinColumn(name = "asignatura_id", nullable = false)
    @JsonIgnoreProperties({"grupos", "maestros", "periodo", "programa", "hibernateLazyInitializer", "handler"})
    private Asignatura asignatura;

    @ManyToOne
    @JoinColumn(name = "grupo_id")
    @JsonIgnoreProperties({
            "calificaciones", "alumnos", "programa", "asignatura", "maestro",
            "periodoAcademico", "cicloEscolar", "fechaCreacion", "fechaActualizacion",
            "hibernateLazyInitializer", "handler"
    })
    private Grupo grupo;

    /** Desglose por criterios (puntos por criterio). */
    @OneToMany(mappedBy = "calificacion", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnoreProperties({"calificacion"})
    private List<CalificacionCriterioItem> criterios = new ArrayList<>();

    // Enums
    public enum TipoEvaluacion {
        ORDINARIO,
        EXTRAORDINARIO,
        REGULARIZACION,
        TITULO_SUFICIENCIA
    }

    public enum EstatusCalificacion {
        APROBADO,
        REPROBADO
    }

    /** Flujo: PENDIENTE → CAPTURADA → EN_REVISION → CONFIRMADA (secretaría puede editar manteniendo CONFIRMADA). */
    public enum EstadoAprobacion {
        PENDIENTE,    // Maestro no ha capturado nada; disponible para capturar
        CAPTURADA,   // Maestro escribió y guardó; puede seguir editando
        EN_REVISION, // Maestro envió a revisión; visible para secretaría
        CONFIRMADA   // Secretaría confirmó; calificación final (editable por secretaría sin cambiar estado)
    }

    /**
     * Único callback PrePersist/PreUpdate permitido por JPA.
     * - idObservaciones: valor por defecto siempre.
     * - confirmada: true cuando CONFIRMADA.
     * - estatus (APROBADO/REPROBADO): solo cuando está confirmada por secretaría (CONFIRMADA).
     */
    @PrePersist
    @PreUpdate
    private void antesDePersistirOActualizar() {
        if (idObservaciones == null) {
            idObservaciones = ObservacionCalificacion.ID_DEFAULT;
        }
        if (periodoAcademico != null) {
            periodo = periodoAcademico.getCodigo();
        }
        boolean confirmadaPorSecretaria = estadoAprobacion == EstadoAprobacion.CONFIRMADA;
        confirmada = confirmadaPorSecretaria;
        if (confirmadaPorSecretaria && calificacionFinal != null) {
            boolean cumpleAsistencia = asistenciaPorcentaje == null || asistenciaPorcentaje >= 80.0;
            if (cumpleAsistencia && calificacionFinal >= 7.0) {
                estatus = EstatusCalificacion.APROBADO;
            } else {
                estatus = EstatusCalificacion.REPROBADO;
            }
        }
    }
}
