package com.idee.controlescolar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "calificacion_criterio_item",
        uniqueConstraints = @UniqueConstraint(columnNames = {"calificacion_id", "criterio_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalificacionCriterioItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "calificacion_id", nullable = false)
    @JsonIgnoreProperties({"criterios"})
    private Calificacion calificacion;

    @ManyToOne(optional = false)
    @JoinColumn(name = "criterio_id", nullable = false)
    @JsonIgnoreProperties({"maestro", "grupo", "asignatura"})
    private CriterioEvaluacion criterio;

    /** Puntos obtenidos por el alumno (máximo = criterio.porcentaje). */
    @Column(nullable = false)
    private Double puntos;
}

