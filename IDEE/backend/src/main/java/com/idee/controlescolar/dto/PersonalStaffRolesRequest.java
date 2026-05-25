package com.idee.controlescolar.dto;

import lombok.Data;

import java.util.List;

@Data
public class PersonalStaffRolesRequest {
    private List<String> roles;
    /** Obligatorio al asignar ESTUDIANTE si el usuario aún no tiene expediente de alumno. */
    private DatosComplementoAlumnoRol datosAlumno;
    /**
     * Obligatorio al asignar COORDINADOR_ACADEMICO: programa educativo que coordinará.
     * Se guarda en usuario_programa_asignado (programasAsignados del Usuario).
     */
    private Long programaCoordinadoId;
}
