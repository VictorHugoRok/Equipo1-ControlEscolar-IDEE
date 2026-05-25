package com.idee.controlescolar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "evaluacion_docente_pregunta")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluacionDocentePregunta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "formulario_id")
    @JsonIgnoreProperties({"preguntas"})
    private EvaluacionDocenteFormulario formulario;

    @Column(nullable = false, length = 1000)
    private String texto;

    /** Sección/bloque al que pertenece (ej. "Bloque 1"). */
    @Column(length = 200)
    private String bloque;

    public enum TipoPregunta {
        ABIERTA,
        LIKERT_5,
        OPCION_MULTIPLE
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoPregunta tipo = TipoPregunta.LIKERT_5;

    /**
     * Opciones para OPCION_MULTIPLE (una por línea). Null en otros tipos.
     * Se guarda como texto para simplificar edición/Excel.
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String opciones;

    @Column(nullable = false)
    private Integer orden = 0;
}
