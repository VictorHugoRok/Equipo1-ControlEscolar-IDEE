package com.idee.controlescolar.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CicloEscolarRequest {
    private String nombre;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    /** ACTIVO o INACTIVO */
    private String estado;
}
