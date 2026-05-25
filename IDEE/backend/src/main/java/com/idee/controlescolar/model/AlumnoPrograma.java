package com.idee.controlescolar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "alumno_programa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AlumnoPrograma {

    @EmbeddedId
    @EqualsAndHashCode.Include
    @ToString.Include
    private AlumnoProgramaId id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @MapsId("alumnoId")
    @JoinColumn(name = "alumno_id", nullable = false)
    @JsonIgnoreProperties({"programasAsignados", "cohortes", "usuario", "calificaciones", "observaciones_list", "documentos", "solicitudes"})
    private Alumno alumno;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @MapsId("programaId")
    @JoinColumn(name = "programa_id", nullable = false)
    @JsonIgnoreProperties({"periodos", "asignaturas", "alumnos"})
    private ProgramaEducativo programa;

    /**
     * Periodo de ingreso (catálogo) para ESTE programa.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "periodo_ingreso_id")
    @JsonIgnoreProperties({"ciclo", "fechaCreacion", "fechaActualizacion", "hibernateLazyInitializer", "handler"})
    private PeriodoAcademico periodoIngreso;

    /**
     * Periodo académico actual derivado del nivel/progreso en ESTE programa.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "periodo_academico_actual_id")
    @JsonIgnoreProperties({"ciclo", "fechaCreacion", "fechaActualizacion", "hibernateLazyInitializer", "handler"})
    private PeriodoAcademico periodoAcademicoActual;

    /**
     * Nivel del plan (1..N) que cursa en ESTE programa.
     */
    @Column(name = "periodo_cursando")
    private Integer periodoCursando;

    /**
     * Solo respuesta JSON (portal alumno): código de periodo escolar que cursa (ej. 2026-1), derivado de ingreso + nivel o de {@link #periodoAcademicoActual}.
     */
    @Transient
    private String periodoEscolarCursandoCodigo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estatus_matricula", nullable = false, length = 30)
    private EstatusMatriculaPrograma estatusMatricula = EstatusMatriculaPrograma.ACTIVA;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @LastModifiedDate
    private LocalDateTime fechaActualizacion;

    public enum EstatusMatriculaPrograma {
        ACTIVA, BAJA_TEMPORAL, BAJA_DEFINITIVA, EGRESADO
    }

    @PrePersist
    @PreUpdate
    private void asegurarId() {
        if (id == null) {
            Long aid = (alumno != null ? alumno.getId() : null);
            Long pid = (programa != null ? programa.getId() : null);
            if (aid != null && pid != null) {
                id = new AlumnoProgramaId(aid, pid);
            }
        }
    }
}

