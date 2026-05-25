package com.idee.controlescolar.dto;

import lombok.Data;

@Data
public class CriterioEvaluacionRequest {
    private String nombre;
    private Integer porcentaje;
    private String descripcion;
}
