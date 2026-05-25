package com.idee.controlescolar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "evaluacion_docente_formulario")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluacionDocenteFormulario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    public enum TipoEvaluacion {
        POR_ALUMNOS,
        POR_SECRETARIA_ACADEMICA,
        AUTOEVALUACION
    }

    /** Por ahora implementamos flujo POR_ALUMNOS; los otros tipos quedan listados para UI. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TipoEvaluacion tipo = TipoEvaluacion.POR_ALUMNOS;

    @Column(nullable = false)
    private Boolean activo = true;

    private LocalDate fechaInicio;
    private LocalDate fechaFin;

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    @OneToMany(mappedBy = "formulario", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC, id ASC")
    @JsonIgnoreProperties({"formulario"})
    private List<EvaluacionDocentePregunta> preguntas = new ArrayList<>();
}
