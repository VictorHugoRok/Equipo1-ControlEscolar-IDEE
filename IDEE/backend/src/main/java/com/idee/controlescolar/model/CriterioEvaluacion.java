package com.idee.controlescolar.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "criterios_evaluacion")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CriterioEvaluacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre; // Ej: "Tareas", "Exámenes parciales", "Proyecto final"

    @Column(nullable = false)
    private Integer porcentaje; // Porcentaje que representa del total (debe sumar 100%)

    @Column(nullable = false)
    private Boolean bloqueado = false; // Una vez guardado, no se puede modificar

    private String periodo; // Periodo académico al que aplica

    @Lob
    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    // Relaciones
    @ManyToOne
    @JoinColumn(name = "asignatura_id", nullable = false)
    private Asignatura asignatura;

    @ManyToOne
    @JoinColumn(name = "maestro_id", nullable = false)
    private Maestro maestro;

    @ManyToOne
    @JoinColumn(name = "grupo_id")
    private Grupo grupo;

    // Validación
    @PrePersist
    @PreUpdate
    private void validarPorcentaje() {
        if (porcentaje < 1 || porcentaje > 100) {
            throw new IllegalArgumentException("El porcentaje debe estar entre 1 y 100");
        }
    }
}
