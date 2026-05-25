package com.idee.controlescolar.dto;

import com.idee.controlescolar.model.Calificacion;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalificacionRequest {
    private Long alumnoId;
    private Long asignaturaId;
    private Long grupoId;
    private Double calificacionFinal;
    private Double asistenciaPorcentaje;
    private String periodo;
    private Integer idObservaciones;
    private String observaciones;
    private String tipoEvaluacion; // ORDINARIO, EXTRAORDINARIO, etc.
    /** Captura por criterios (puntos por criterio; max = porcentaje del criterio). */
    private java.util.List<CriterioPunto> criterios;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CriterioPunto {
        private Long criterioId;
        private Double puntos;
    }
}
