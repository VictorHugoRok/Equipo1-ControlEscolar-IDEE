package com.idee.controlescolar.dto;

import lombok.Data;

@Data
public class CambiarEstadoGestionRequest {
    /** INACTIVO, ACTIVO, CERRADO */
    private String estado;
}
