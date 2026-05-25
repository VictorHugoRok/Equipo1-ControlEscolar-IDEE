package com.idee.controlescolar.dto;

import lombok.Data;

/**
 * Asignación de un alumno a un programa con atributos por programa.
 */
@Data
public class AlumnoProgramaAsignadoDTO {
    private Long programaId;
    private Long periodoAcademicoIngresoId;
    private String estatusMatricula;
}

