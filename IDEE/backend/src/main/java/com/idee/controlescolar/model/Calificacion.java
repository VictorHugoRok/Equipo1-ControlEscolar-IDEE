package com.idee.controlescolar.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "calificaciones")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Calificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Double calificacionFinal;

    private Double asistenciaPorcentaje;

    @Enumerated(EnumType.STRING)
    private TipoEvaluacion tipoEvaluacion = TipoEvaluacion.ORDINARIO;

    @Enumerated(EnumType.STRING)
    private EstatusCalificacion estatus;

    @Enumerated(EnumType.STRING)
    private EstadoAprobacion estadoAprobacion = EstadoAprobacion.PENDIENTE;

    private String periodo; // Ejemplo: "2025-1"

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
    private Alumno alumno;

    @ManyToOne
    @JoinColumn(name = "asignatura_id", nullable = false)
    private Asignatura asignatura;

    @ManyToOne
    @JoinColumn(name = "grupo_id")
    private Grupo grupo;

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

    public enum EstadoAprobacion {
        PENDIENTE,
        EN_REVISION,
        CONFIRMADA
    }

    // Helper methods
    @PrePersist
    @PreUpdate
    private void calcularEstatus() {
        if (calificacionFinal != null) {
            // Validar asistencia mínima (80%)
            boolean cumpleAsistencia = asistenciaPorcentaje == null || asistenciaPorcentaje >= 80.0;

            if (cumpleAsistencia && calificacionFinal >= 70.0) {
                estatus = EstatusCalificacion.APROBADO;
            } else {
                estatus = EstatusCalificacion.REPROBADO;
            }
        }
    }
}
