package com.idee.controlescolar.dto;

/**
 * Inscripción: sin nivel 1 completo. Reinscripción: al menos un nivel completamente aprobado.
 */
public enum TipoInscripcion {
    /**
     * Modo unificado: el sistema determina si es nuevo ingreso o reingreso según el avance del alumno.
     */
    AUTO,
    INSCRIPCION,
    REINSCRIPCION
}
