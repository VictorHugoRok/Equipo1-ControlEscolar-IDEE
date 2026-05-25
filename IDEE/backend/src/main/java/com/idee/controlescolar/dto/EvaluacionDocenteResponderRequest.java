package com.idee.controlescolar.dto;

import lombok.Data;

import java.util.List;

@Data
public class EvaluacionDocenteResponderRequest {
    private Long formularioId;
    /**
     * Vigencia modular: bloque de horario que identifica la asignatura/grupo/módulo (alumno y autoevaluación).
     */
    private Long horarioBloqueId;
    /**
     * Por cada maestro, una calificación por pregunta.
     */
    private List<EvaluacionDocenteMaestroRespuesta> porMaestro;

    @Data
    public static class EvaluacionDocenteMaestroRespuesta {
        private Long maestroId;
        /** Evaluación Académica: clase concreta (mismo criterio que alumnos / horario). */
        private Long horarioBloqueId;
        /**
         * Ignorado en Evaluación Académica (secretaría): la fecha/hora de visita se asigna en servidor al enviar.
         */
        private java.time.LocalDateTime fechaVisita;
        /** Solo Evaluación Académica: observaciones/anotaciones por bloque. */
        private List<ObservacionBloque> observacionesBloque;
        private List<EvaluacionDocenteParPreguntaValor> valores;
    }

    @Data
    public static class ObservacionBloque {
        private String bloque;
        private String texto;
    }

    @Data
    public static class EvaluacionDocenteParPreguntaValor {
        private Long preguntaId;
        /** 1–5 (LIKERT_5) */
        private Integer valor;
        /** Texto (ABIERTA) u opción elegida (OPCION_MULTIPLE). */
        private String valorTexto;
    }
}
