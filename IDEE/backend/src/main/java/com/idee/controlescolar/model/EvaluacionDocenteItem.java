package com.idee.controlescolar.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "evaluacion_docente_item")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluacionDocenteItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "respuesta_id")
    @JsonIgnoreProperties({"items", "alumno"})
    private EvaluacionDocenteRespuesta respuesta;

    @ManyToOne(optional = false)
    @JoinColumn(name = "maestro_id")
    @JsonIgnoreProperties({"asignaturas", "grupos", "horariosImpartidos", "usuario", "documentos"})
    private Maestro maestro;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pregunta_id")
    @JsonIgnoreProperties({"formulario"})
    private EvaluacionDocentePregunta pregunta;

    /** Respuesta numérica (LIKERT_5). */
    private Integer valor;

    /** Respuesta texto (ABIERTA) u opción elegida (OPCION_MULTIPLE). */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String valorTexto;
}
