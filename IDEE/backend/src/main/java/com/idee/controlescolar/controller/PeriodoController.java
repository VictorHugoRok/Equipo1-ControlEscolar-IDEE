package com.idee.controlescolar.controller;

import com.idee.controlescolar.model.Periodo;
import com.idee.controlescolar.model.ProgramaEducativo;
import com.idee.controlescolar.repository.ProgramaEducativoRepository;
import com.idee.controlescolar.repository.PeriodoRepository;
import com.idee.controlescolar.service.PeriodoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controlador REST para gestionar periodos del plan de estudios.
 * Los periodos se crean automáticamente al crear/actualizar un programa.
 */
@RestController
@RequestMapping("/periodos")
@CrossOrigin(origins = "*")
public class PeriodoController {

    @Autowired
    private PeriodoRepository periodoRepository;

    @Autowired
    private ProgramaEducativoRepository programaEducativoRepository;

    @Autowired
    private PeriodoService periodoService;

    /**
     * Obtener periodos de un programa (ordenados por número).
     * Si el programa no tiene periodos pero tiene duracionPeriodos definida, se crean automáticamente.
     */
    @GetMapping
    public ResponseEntity<?> obtenerPorPrograma(@RequestParam(required = false) Long programaId) {
        if (programaId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "El parámetro programaId es obligatorio."));
        }

        List<Periodo> periodos = periodoRepository.findByProgramaIdOrderByNumeroAsc(programaId);

        if (periodos.isEmpty()) {
            Optional<ProgramaEducativo> programaOpt = programaEducativoRepository.findById(programaId);
            if (programaOpt.isPresent() && programaOpt.get().getDuracionPeriodos() != null
                    && programaOpt.get().getDuracionPeriodos() > 0) {
                periodoService.asegurarPeriodosParaPrograma(programaOpt.get());
                periodos = periodoRepository.findByProgramaIdOrderByNumeroAsc(programaId);
            }
        }

        return ResponseEntity.ok(periodos);
    }

    /**
     * Obtener un periodo por ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable Long id) {
        Optional<Periodo> periodoOpt = periodoRepository.findById(id);
        if (periodoOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(periodoOpt.get());
    }

    /**
     * Actualizar nombre de un periodo (el número no debe cambiar).
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody Periodo actualizado) {
        Optional<Periodo> periodoOpt = periodoRepository.findById(id);
        if (periodoOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Periodo p = periodoOpt.get();
        if (actualizado.getNombre() != null) {
            p.setNombre(actualizado.getNombre());
        }
        return ResponseEntity.ok(periodoRepository.save(p));
    }
}
