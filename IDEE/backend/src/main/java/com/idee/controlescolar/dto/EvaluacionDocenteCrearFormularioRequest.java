package com.idee.controlescolar.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class EvaluacionDocenteCrearFormularioRequest {
    private String titulo;
    private String descripcion;
    private Boolean activo;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;

    /** POR_ALUMNOS | POR_SECRETARIA_ACADEMICA | AUTOEVALUACION */
    private String tipo;

    /**
     * Editor por bloques. Si viene null, se usa {@link #preguntas} como bloque único (compatibilidad).
     */
    private List<Bloque> bloques;

    /** Compatibilidad: texto de cada pregunta (LIKERT_5). */
    private List<String> preguntas;

    @Data
    public static class Bloque {
        private String titulo;
        private Integer orden;
        private List<Pregunta> preguntas;
    }

    @Data
    public static class Pregunta {
        private String tipo;   // ABIERTA | LIKERT_5 | OPCION_MULTIPLE
        private String texto;
        private Integer orden;
        private List<String> opciones; // solo OPCION_MULTIPLE
    }
}
