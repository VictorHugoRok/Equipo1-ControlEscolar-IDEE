package com.idee.controlescolar.model;

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

/**
 * Periodo académico institucional (ej. 2025-1,2025-2) dentro de un {@link CicloEscolar}.
 * Catálogo para periodo de ingreso, calificaciones, grupos y horarios.
 */
@Entity
@Table(name = "periodo_academico", uniqueConstraints = {
        @UniqueConstraint(name = "uq_periodo_ciclo_tipo_codigo", columnNames = {"ciclo_id", "tipo_periodo", "codigo"}),
        @UniqueConstraint(name = "uq_periodo_ciclo_tipo_numero", columnNames = {"ciclo_id", "tipo_periodo", "numero"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PeriodoAcademico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "ciclo_id", nullable = false)
    @JsonIgnoreProperties({"periodosAcademicos", "hibernateLazyInitializer", "handler"})
    private CicloEscolar ciclo;

    @Column(nullable = false, length = 20)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false)
    private Integer anio;

    @Column(nullable = false)
    private Integer numero;

    @Column(nullable = false)
    private LocalDate fechaInicio;

    @Column(nullable = false)
    private LocalDate fechaFin;

    /**
     * @deprecated Sincronizado con {@link #estadoGestion} == ACTIVO; conservado por compatibilidad con consultas JPA existentes.
     */
    @Column(nullable = false)
    private Boolean activo = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_periodo", length = 50, nullable = false)
    private ProgramaEducativo.TipoPeriodo tipoPeriodo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_gestion", nullable = false, length = 20)
    private EstadoGestionPeriodoAcademico estadoGestion = EstadoGestionPeriodoAcademico.INACTIVO;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @LastModifiedDate
    private LocalDateTime fechaActualizacion;

    @PrePersist
    @PreUpdate
    public void sincronizarFlagActivoLegacy() {
        this.activo = (estadoGestion == EstadoGestionPeriodoAcademico.ACTIVO);
    }

    public boolean esActivo() {
        LocalDate hoy = LocalDate.now();
        return (fechaInicio == null || !fechaInicio.isAfter(hoy))
                && (fechaFin == null || !fechaFin.isBefore(hoy));
    }

    public boolean esPasado() {
        return fechaFin != null && fechaFin.isBefore(LocalDate.now());
    }

    public boolean esFuturo() {
        return fechaInicio != null && fechaInicio.isAfter(LocalDate.now());
    }

    public boolean estaEnPlaneacion() {
        return estadoGestion == null || estadoGestion == EstadoGestionPeriodoAcademico.INACTIVO;
    }

    public boolean estaEnOperacionAcademica() {
        return estadoGestion == EstadoGestionPeriodoAcademico.ACTIVO;
    }

    public boolean estaEnHistorico() {
        return estadoGestion == EstadoGestionPeriodoAcademico.CERRADO;
    }
}
