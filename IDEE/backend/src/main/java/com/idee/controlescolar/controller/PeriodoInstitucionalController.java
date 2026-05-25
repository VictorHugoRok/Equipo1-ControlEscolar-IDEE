package com.idee.controlescolar.controller;

import com.idee.controlescolar.dto.PeriodoInstitucionalDTO;
import com.idee.controlescolar.model.ProgramaEducativo;
import com.idee.controlescolar.service.PeriodoInstitucionalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Year;
import java.util.List;

/**
 * API para periodos institucionales (generados automáticamente).
 * No se persisten; se calculan según año y tipo de periodo.
 */
@RestController
@RequestMapping("/periodos-institucionales")
@CrossOrigin(origins = "*")
public class PeriodoInstitucionalController {

    private final PeriodoInstitucionalService periodoInstitucionalService;

    public PeriodoInstitucionalController(PeriodoInstitucionalService periodoInstitucionalService) {
        this.periodoInstitucionalService = periodoInstitucionalService;
    }

    /**
     * Obtiene periodos institucionales para un año.
     * GET /api/periodos-institucionales?year=2026&tipoPeriodo=SEMESTRE
     */
    @GetMapping
    public ResponseEntity<List<PeriodoInstitucionalDTO>> listar(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(required = false) ProgramaEducativo.TipoPeriodo tipoPeriodo) {
        int año = year > 0 ? year : Year.now().getValue();
        List<PeriodoInstitucionalDTO> periodos = periodoInstitucionalService.generarParaAño(año, tipoPeriodo);
        return ResponseEntity.ok(periodos);
    }

    /**
     * Obtiene periodos para un rango de años (desde añoActual-1 hasta añoActual+1 por defecto).
     * GET /api/periodos-institucionales/rango?yearDesde=2025&yearHasta=2027&tipoPeriodo=SEMESTRE
     */
    @GetMapping("/rango")
    public ResponseEntity<List<PeriodoInstitucionalDTO>> listarRango(
            @RequestParam(defaultValue = "0") int yearDesde,
            @RequestParam(defaultValue = "0") int yearHasta,
            @RequestParam(required = false) ProgramaEducativo.TipoPeriodo tipoPeriodo) {
        int añoActual = Year.now().getValue();
        int desde = yearDesde > 0 ? yearDesde : añoActual - 1;
        int hasta = yearHasta > 0 ? yearHasta : añoActual + 1;
        if (desde > hasta) {
            int t = desde;
            desde = hasta;
            hasta = t;
        }
        List<PeriodoInstitucionalDTO> periodos = periodoInstitucionalService.generarParaRango(desde, hasta, tipoPeriodo);
        return ResponseEntity.ok(periodos);
    }

    /**
     * Obtiene el periodo institucional activo para un tipo (si existe).
     */
    @GetMapping("/activo")
    public ResponseEntity<PeriodoInstitucionalDTO> obtenerActivo(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(required = false) ProgramaEducativo.TipoPeriodo tipoPeriodo) {
        int año = year > 0 ? year : Year.now().getValue();
        PeriodoInstitucionalDTO activo = periodoInstitucionalService.obtenerPeriodoActivo(año, tipoPeriodo);
        return activo != null ? ResponseEntity.ok(activo) : ResponseEntity.noContent().build();
    }
}
