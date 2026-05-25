package com.idee.controlescolar.controller;

import com.idee.controlescolar.model.Asignatura;
import com.idee.controlescolar.model.Grupo;
import com.idee.controlescolar.model.Periodo;
import com.idee.controlescolar.model.ProgramaEducativo;
import com.idee.controlescolar.model.Usuario;
import com.idee.controlescolar.repository.AsignaturaRepository;
import com.idee.controlescolar.repository.GrupoRepository;
import com.idee.controlescolar.security.RequierePermiso;
import com.idee.controlescolar.repository.PeriodoRepository;
import com.idee.controlescolar.repository.ProgramaEducativoRepository;
import com.idee.controlescolar.service.ProgramaAccesoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controlador REST para gestionar asignaturas
 */
@RestController
@RequestMapping("/asignaturas")
@CrossOrigin(origins = "*")
public class AsignaturaController {

    @Autowired
    private AsignaturaRepository asignaturaRepository;

    @Autowired
    private ProgramaEducativoRepository programaEducativoRepository;

    @Autowired
    private PeriodoRepository periodoRepository;

    @Autowired
    private GrupoRepository grupoRepository;

    @Autowired
    private ProgramaAccesoService programaAccesoService;

    /**
     * Obtener asignaturas (opcionalmente filtradas por programa).
     */
    @GetMapping
    @RequierePermiso("VER_PROGRAMAS")
    public ResponseEntity<List<Asignatura>> obtener(@RequestParam(required = false) Long programaId,
                                                    Authentication authentication,
                                                    @RequestParam(required = false) Long periodoId) {
        Usuario u = (authentication != null && authentication.getPrincipal() instanceof Usuario)
                ? (Usuario) authentication.getPrincipal()
                : null;
        if (programaAccesoService.esCoordinadorAcademico(u)) {
            var permitidos = programaAccesoService.programaIdsPermitidos(u);
            if (permitidos.isEmpty()) return ResponseEntity.ok(List.of());
            if (programaId != null && !permitidos.contains(programaId)) {
                return ResponseEntity.status(403).body(List.of());
            }
        }
        if (periodoId != null) {
            List<Asignatura> porPeriodo = asignaturaRepository.findByPeriodoIdWithPeriodoYPrograma(periodoId);
            Asignatura.ordenarListaPorIdAsignatura(porPeriodo);
            return ResponseEntity.ok(porPeriodo);
        }
        if (programaId != null) {
            List<Asignatura> porPrograma = asignaturaRepository.findByProgramaIdWithPeriodoYPrograma(programaId);
            Asignatura.ordenarListaPorIdAsignatura(porPrograma);
            return ResponseEntity.ok(porPrograma);
        }
        List<Asignatura> all = new ArrayList<>(asignaturaRepository.findAllWithPeriodoYPrograma());
        if (programaAccesoService.esCoordinadorAcademico(u)) {
            var permitidos = programaAccesoService.programaIdsPermitidos(u);
            all = new ArrayList<>(all.stream()
                    .filter(a -> a.getPrograma() != null && permitidos.contains(a.getPrograma().getId()))
                    .toList());
        }
        Asignatura.ordenarListaPorIdAsignatura(all);
        return ResponseEntity.ok(all);
    }

    /**
     * Obtener asignatura por ID
     */
    @GetMapping("/{id}")
    @RequierePermiso("VER_PROGRAMAS")
    public ResponseEntity<?> obtenerPorId(@PathVariable Long id) {
        Optional<Asignatura> asignaturaOpt = asignaturaRepository.findById(id);
        if (!asignaturaOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(asignaturaOpt.get());
    }

    /**
     * Crear una nueva asignatura
     */
    @PostMapping
    @RequierePermiso("ACTUALIZAR_PROGRAMAS")
    public ResponseEntity<?> crear(@RequestBody Asignatura asignatura) {
        try {
            if (asignatura.getClave() == null || asignatura.getClave().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "La clave de la asignatura es obligatoria."));
            }
            if (asignatura.getNombre() == null || asignatura.getNombre().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El nombre de la asignatura es obligatorio."));
            }
            if (asignatura.getPeriodo() == null || asignatura.getPeriodo().getId() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "El periodo del plan de estudios es obligatorio."));
            }

            Periodo periodo = periodoRepository.findById(asignatura.getPeriodo().getId()).orElse(null);
            if (periodo == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Periodo no encontrado."));
            }

            asignatura.setPeriodo(periodo);
            asignatura.setPrograma(periodo.getPrograma());
            Asignatura asignaturaGuardada = asignaturaRepository.save(asignatura);
            return ResponseEntity.status(HttpStatus.CREATED).body(asignaturaGuardada);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al crear la asignatura: " + e.getMessage()));
        }
    }

    /**
     * Actualizar una asignatura existente
     */
    @PutMapping("/{id}")
    @RequierePermiso("ACTUALIZAR_PROGRAMAS")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody Asignatura asignaturaActualizada) {
        Optional<Asignatura> asignaturaOpt = asignaturaRepository.findById(id);
        if (asignaturaOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Asignatura asignatura = asignaturaOpt.get();

        if (asignaturaActualizada.getClave() == null || asignaturaActualizada.getClave().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "La clave de la asignatura es obligatoria."));
        }
        if (asignaturaActualizada.getNombre() == null || asignaturaActualizada.getNombre().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "El nombre de la asignatura es obligatorio."));
        }

        asignatura.setIdAsignatura(asignaturaActualizada.getIdAsignatura());
        asignatura.setClave(asignaturaActualizada.getClave());
        asignatura.setNombre(asignaturaActualizada.getNombre());
        asignatura.setTipo(asignaturaActualizada.getTipo());
        asignatura.setCreditos(asignaturaActualizada.getCreditos());
        asignatura.setHorasAula(asignaturaActualizada.getHorasAula());
        asignatura.setHorasPractica(asignaturaActualizada.getHorasPractica());
        asignatura.setHorasIndependientes(asignaturaActualizada.getHorasIndependientes());
        asignatura.setEstatus(asignaturaActualizada.getEstatus());

        if (asignaturaActualizada.getPeriodo() != null
                && asignaturaActualizada.getPeriodo().getId() != null) {
            Periodo periodo = periodoRepository.findById(asignaturaActualizada.getPeriodo().getId()).orElse(null);
            if (periodo == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Periodo no encontrado."));
            }
            if (asignatura.getPrograma() != null && !periodo.getPrograma().getId().equals(asignatura.getPrograma().getId())) {
                return ResponseEntity.badRequest().body(Map.of("error", "El periodo seleccionado no pertenece al programa de esta asignatura."));
            }
            asignatura.setPeriodo(periodo);
            asignatura.setPrograma(periodo.getPrograma());
        }

        Asignatura asignaturaGuardada = asignaturaRepository.save(asignatura);
        return ResponseEntity.ok(asignaturaGuardada);
    }

    /**
     * Eliminar una asignatura.
     * Si hay grupos que la referencian, se eliminan primero (junto con sus calificaciones).
     */
    @DeleteMapping("/{id}")
    @RequierePermiso("ACTUALIZAR_PROGRAMAS")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        Optional<Asignatura> asignaturaOpt = asignaturaRepository.findById(id);
        if (asignaturaOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<Grupo> grupos = grupoRepository.findByAsignaturaId(id);
        if (!grupos.isEmpty()) {
            grupoRepository.deleteAll(grupos);
        }

        asignaturaRepository.delete(asignaturaOpt.get());
        return ResponseEntity.ok().build();
    }
}
