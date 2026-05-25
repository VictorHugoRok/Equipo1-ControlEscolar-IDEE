package com.idee.controlescolar.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CrearPeriodoAcademicoGestionRequest {
    private Long cicloId;
    /** SEMESTRE, CUATRIMESTRE, TRIMESTRE, TETRAMESTRE, … */
    private String tipoPeriodo;
    private Integer numero;
    private String nombre;
    /** Opcional; si se omite se infiere año-numero desde fechaInicio */
    private String codigo;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
}
