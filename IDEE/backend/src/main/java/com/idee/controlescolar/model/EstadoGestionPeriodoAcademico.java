package com.idee.controlescolar.model;

/**
 * Estado operativo del periodo académico dentro del ciclo.
 */
public enum EstadoGestionPeriodoAcademico {
    /** Planeación: aún no cursa la institución en ese periodo. */
    INACTIVO,
    /** Periodo en curso para ese tipo de calendario (solo uno a la vez por tipo). */
    ACTIVO,
    /** Periodo concluido y cierres aprobados. */
    CERRADO
}
