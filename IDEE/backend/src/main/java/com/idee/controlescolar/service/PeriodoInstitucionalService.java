package com.idee.controlescolar.service;

import com.idee.controlescolar.dto.PeriodoInstitucionalDTO;
import com.idee.controlescolar.model.ProgramaEducativo;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Genera periodos institucionales automáticamente según año y tipo de periodo.
 * No se persisten; se calculan on-the-fly.
 * <p>
 * Formatos por tipo:
 * - SEMESTRE: FEB-JUL-YY-1, AGO-ENE-YY-2
 * - CUATRIMESTRE/TETRAMESTRE: ENE-ABR-YY-1, MAY-AGO-YY-2, SEP-DIC-YY-3
 * - TRIMESTRE: FEB-ABR-YY-1, MAY-JUL-YY-2, AGO-OCT-YY-3, NOV-ENE-YY-4
 */
@Service
public class PeriodoInstitucionalService {

    /**
     * Genera los periodos institucionales para un año dado y tipo de periodo.
     */
    public List<PeriodoInstitucionalDTO> generarParaAño(int año, ProgramaEducativo.TipoPeriodo tipoPeriodo) {
        if (tipoPeriodo == null) {
            return generarParaAñoSemestral(año);
        }
        return switch (tipoPeriodo) {
            case SEMESTRE -> generarParaAñoSemestral(año);
            case CUATRIMESTRE, TETRAMESTRE -> generarParaAñoCuatrimestral(año);
            case TRIMESTRE -> generarParaAñoTrimestral(año);
            default -> generarParaAñoSemestral(año);
        };
    }

    /**
     * Genera periodos para un rango de años (ej. añoActual-1 a añoActual+1).
     */
    public List<PeriodoInstitucionalDTO> generarParaRango(int añoDesde, int añoHasta, ProgramaEducativo.TipoPeriodo tipoPeriodo) {
        List<PeriodoInstitucionalDTO> todos = new ArrayList<>();
        for (int y = añoDesde; y <= añoHasta; y++) {
            todos.addAll(generarParaAño(y, tipoPeriodo));
        }
        return todos;
    }

    private List<PeriodoInstitucionalDTO> generarParaAñoSemestral(int año) {
        int yy = año % 100;
        String yyStr = yy < 10 ? "0" + yy : String.valueOf(yy);

        return List.of(
                PeriodoInstitucionalDTO.builder()
                        .codigo("FEB-JUL-" + yyStr + "-1")
                        .nombre("Febrero-Julio " + año + " (1)")
                        .año(año)
                        .numero(1)
                        .fechaInicio(LocalDate.of(año, 2, 1))
                        .fechaFin(LocalDate.of(año, 7, 31))
                        .build(),
                PeriodoInstitucionalDTO.builder()
                        .codigo("AGO-ENE-" + yyStr + "-2")
                        .nombre("Agosto-Enero " + año + "/" + (año + 1) + " (2)")
                        .año(año)
                        .numero(2)
                        .fechaInicio(LocalDate.of(año, 8, 1))
                        .fechaFin(LocalDate.of(año + 1, 1, 31))
                        .build()
        );
    }

    /**
     * CUATRIMESTRE/TETRAMESTRE: ENE-ABR-YY-1, MAY-AGO-YY-2, SEP-DIC-YY-3
     */
    private List<PeriodoInstitucionalDTO> generarParaAñoCuatrimestral(int año) {
        int yy = año % 100;
        String yyStr = yy < 10 ? "0" + yy : String.valueOf(yy);

        return List.of(
                PeriodoInstitucionalDTO.builder()
                        .codigo("ENE-ABR-" + yyStr + "-1")
                        .nombre("Enero-Abril " + año + " (1)")
                        .año(año)
                        .numero(1)
                        .fechaInicio(LocalDate.of(año, 1, 1))
                        .fechaFin(LocalDate.of(año, 4, 30))
                        .build(),
                PeriodoInstitucionalDTO.builder()
                        .codigo("MAY-AGO-" + yyStr + "-2")
                        .nombre("Mayo-Agosto " + año + " (2)")
                        .año(año)
                        .numero(2)
                        .fechaInicio(LocalDate.of(año, 5, 1))
                        .fechaFin(LocalDate.of(año, 8, 31))
                        .build(),
                PeriodoInstitucionalDTO.builder()
                        .codigo("SEP-DIC-" + yyStr + "-3")
                        .nombre("Septiembre-Diciembre " + año + " (3)")
                        .año(año)
                        .numero(3)
                        .fechaInicio(LocalDate.of(año, 9, 1))
                        .fechaFin(LocalDate.of(año, 12, 31))
                        .build()
        );
    }

    private List<PeriodoInstitucionalDTO> generarParaAñoTrimestral(int año) {
        int yy = año % 100;
        String yyStr = yy < 10 ? "0" + yy : String.valueOf(yy);

        return List.of(
                PeriodoInstitucionalDTO.builder()
                        .codigo("FEB-ABR-" + yyStr + "-1")
                        .nombre("Febrero-Abril " + año + " (1)")
                        .año(año)
                        .numero(1)
                        .fechaInicio(LocalDate.of(año, 2, 1))
                        .fechaFin(LocalDate.of(año, 4, 30))
                        .build(),
                PeriodoInstitucionalDTO.builder()
                        .codigo("MAY-JUL-" + yyStr + "-2")
                        .nombre("Mayo-Julio " + año + " (2)")
                        .año(año)
                        .numero(2)
                        .fechaInicio(LocalDate.of(año, 5, 1))
                        .fechaFin(LocalDate.of(año, 7, 31))
                        .build(),
                PeriodoInstitucionalDTO.builder()
                        .codigo("AGO-OCT-" + yyStr + "-3")
                        .nombre("Agosto-Octubre " + año + " (3)")
                        .año(año)
                        .numero(3)
                        .fechaInicio(LocalDate.of(año, 8, 1))
                        .fechaFin(LocalDate.of(año, 10, 31))
                        .build(),
                PeriodoInstitucionalDTO.builder()
                        .codigo("NOV-ENE-" + yyStr + "-4")
                        .nombre("Noviembre-Enero " + año + "/" + (año + 1) + " (4)")
                        .año(año)
                        .numero(4)
                        .fechaInicio(LocalDate.of(año, 11, 1))
                        .fechaFin(LocalDate.of(año + 1, 1, 31))
                        .build()
        );
    }

    /**
     * Obtiene el periodo institucional activo para un tipo dado (si existe).
     */
    public PeriodoInstitucionalDTO obtenerPeriodoActivo(int año, ProgramaEducativo.TipoPeriodo tipoPeriodo) {
        return generarParaAño(año, tipoPeriodo).stream()
                .filter(PeriodoInstitucionalDTO::esActivo)
                .findFirst()
                .orElse(null);
    }
}
