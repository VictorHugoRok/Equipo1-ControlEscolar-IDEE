package com.idee.controlescolar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO para periodo institucional (generado automáticamente según año y tipo de periodo).
 * No se persiste; se calcula on-the-fly.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeriodoInstitucionalDTO {

    private String codigo;       // Ej: FEB-JUL-26-1, AGO-ENE-26-2
    private String nombre;      // Ej: Febrero-Julio 2026 (1)
    private int año;
    private int numero;         // 1, 2, 3 o 4 según tipo
    private LocalDate fechaInicio;
    private LocalDate fechaFin;

    /**
     * Indica si el periodo está activo (hoy está entre fechaInicio y fechaFin).
     */
    public boolean esActivo() {
        LocalDate hoy = LocalDate.now();
        return (fechaInicio == null || !fechaInicio.isAfter(hoy))
                && (fechaFin == null || !fechaFin.isBefore(hoy));
    }

    /**
     * Indica si el periodo ya pasó.
     */
    public boolean esPasado() {
        return fechaFin != null && fechaFin.isBefore(LocalDate.now());
    }

    /**
     * Indica si el periodo es futuro.
     */
    public boolean esFuturo() {
        return fechaInicio != null && fechaInicio.isAfter(LocalDate.now());
    }
}
