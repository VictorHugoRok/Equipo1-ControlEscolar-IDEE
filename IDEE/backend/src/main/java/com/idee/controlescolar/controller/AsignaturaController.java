package com.idee.controlescolar.controller;

import com.idee.controlescolar.model.Asignatura;
import com.idee.controlescolar.model.ProgramaEducativo;
import com.idee.controlescolar.repository.AsignaturaRepository;
import com.idee.controlescolar.repository.ProgramaEducativoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Controlador REST para gestionar asignaturas
 */
@RestController
@RequestMapping("/api/asignaturas")
@CrossOrigin(origins = "*")
public class AsignaturaController {

    @Autowired
    private AsignaturaRepository asignaturaRepository;

    @Autowired
    private ProgramaEducativoRepository programaEducativoRepository;

    /**
     * Obtener asignaturas (opcionalmente filtradas por programa)
     */
    @GetMapping
    public ResponseEntity<List<Asignatura>> obtener(@RequestParam(required = false) Long programaId) {
        if (programaId != null) {
            return ResponseEntity.ok(asignaturaRepository.findByProgramaId(programaId));
        }
        return ResponseEntity.ok(asignaturaRepository.findAll());
    }

    /**
     * Obtener asignatura por ID
     */
    @GetMapping("/{id}")
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
    public ResponseEntity<?> crear(@RequestBody Asignatura asignatura) {
        try {
            if (asignatura.getPrograma() == null || asignatura.getPrograma().getId() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("El programa educativo es requerido.");
            }

            ProgramaEducativo programa = programaEducativoRepository.findById(asignatura.getPrograma().getId())
                    .orElse(null);
            if (programa == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Programa educativo no encontrado.");
            }

            asignatura.setPrograma(programa);
            Asignatura asignaturaGuardada = asignaturaRepository.save(asignatura);
            return ResponseEntity.status(HttpStatus.CREATED).body(asignaturaGuardada);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al crear la asignatura: " + e.getMessage());
        }
    }

    /**
     * Actualizar una asignatura existente
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody Asignatura asignaturaActualizada) {
        Optional<Asignatura> asignaturaOpt = asignaturaRepository.findById(id);
        if (!asignaturaOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        Asignatura asignatura = asignaturaOpt.get();
        asignatura.setClave(asignaturaActualizada.getClave());
        asignatura.setNombre(asignaturaActualizada.getNombre());
        asignatura.setTipo(asignaturaActualizada.getTipo());
        asignatura.setPeriodo(asignaturaActualizada.getPeriodo());
        asignatura.setCreditos(asignaturaActualizada.getCreditos());
        asignatura.setHorasAula(asignaturaActualizada.getHorasAula());
        asignatura.setHorasPractica(asignaturaActualizada.getHorasPractica());
        asignatura.setHorasIndependientes(asignaturaActualizada.getHorasIndependientes());
        asignatura.setEstatus(asignaturaActualizada.getEstatus());

        if (asignaturaActualizada.getPrograma() != null
                && asignaturaActualizada.getPrograma().getId() != null) {
            ProgramaEducativo programa = programaEducativoRepository
                    .findById(asignaturaActualizada.getPrograma().getId())
                    .orElse(null);
            if (programa != null) {
                asignatura.setPrograma(programa);
            }
        }

        Asignatura asignaturaGuardada = asignaturaRepository.save(asignatura);
        return ResponseEntity.ok(asignaturaGuardada);
    }

    /**
     * Eliminar una asignatura
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        Optional<Asignatura> asignaturaOpt = asignaturaRepository.findById(id);
        if (!asignaturaOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        asignaturaRepository.delete(asignaturaOpt.get());
        return ResponseEntity.ok().build();
    }
}
