package com.idee.controlescolar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa una clase (grupo + materia) que el maestro imparte según su horario.
 * Ortodoncia I en Grupo A y Ortodoncia I en Grupo B son 2 clases distintas.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaseMaestroDTO {
    private Long grupoId;
    private Long asignaturaId;
    private String grupoNombre;
    private String asignaturaNombre;
    private String periodo;
    private Long periodoAcademicoId;

    /** Docente del horario (solo se llena en listados administrativos). */
    private Long maestroId;
    private String maestroNombre;
}
