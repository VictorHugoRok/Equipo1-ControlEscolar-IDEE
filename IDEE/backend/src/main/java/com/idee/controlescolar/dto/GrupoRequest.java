package com.idee.controlescolar.dto;

import lombok.Data;

import java.util.List;

@Data
public class GrupoRequest {
    private String nombre;       // ej. "3A"
    /** Número de periodo / nivel del plan (primer semestre=1, etc.); obligatorio sin asignatura */
    private Integer numeroPeriodo;
    private Long periodoAcademicoId;  // Catálogo periodo académico (prioridad)
    private String periodoIngreso;     // Código ej. "2025-1" - se resuelve a PeriodoAcademico
    private Long programaId;      // para modo básico (sin asignatura)
    private Long asignaturaId;   // para modo avanzado
    private Long maestroId;
    private List<Long> alumnoIds;
    private String estatus;      // ACTIVO, FINALIZADO, CANCELADO
}
