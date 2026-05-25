package com.idee.controlescolar.dto;

import lombok.Data;

import java.util.List;

/**
 * Datos académicos mínimos para crear el expediente {@link com.idee.controlescolar.model.Alumno}
 * al asignar el rol ESTUDIANTE desde la ficha unificada de personal.
 */
@Data
public class DatosComplementoAlumnoRol {
    private String matricula;

    /**
     * Nuevo modelo: múltiples programas por alumno, con periodo/estatus por programa.
     * Si viene, se usa en lugar de {@link #programaId}.
     */
    private List<AlumnoProgramaAsignadoDTO> programasAsignados;

    private Long programaId;
    /** Periodo de ingreso (catálogo); opcional. */
    private Long periodoAcademicoIngresoId;
    /** {@link com.idee.controlescolar.model.Alumno.Sexo#name()} */
    private String sexo;
    /** {@link com.idee.controlescolar.model.Alumno.EstatusMatricula#name()} (ACTIVA, BAJA_TEMPORAL, BAJA_DEFINITIVA, EGRESADO); opcional. */
    private String estatusMatricula;
    /** Fecha en formato ISO {@code yyyy-MM-dd}; opcional. */
    private String fechaNacimiento;
}
