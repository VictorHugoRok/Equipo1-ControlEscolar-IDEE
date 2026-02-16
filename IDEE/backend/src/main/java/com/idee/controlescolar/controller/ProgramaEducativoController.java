package com.idee.controlescolar.controller;

import com.idee.controlescolar.model.ProgramaEducativo;
import com.idee.controlescolar.repository.ProgramaEducativoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Controlador REST para gestionar programas educativos
 */
@RestController
@RequestMapping("/api/programas-educativos")
@CrossOrigin(origins = "*")
public class ProgramaEducativoController {

    @Autowired
    private ProgramaEducativoRepository programaEducativoRepository;

    /**
     * Obtener todos los programas educativos
     */
    @GetMapping
    public ResponseEntity<List<ProgramaEducativo>> obtenerTodos() {
        List<ProgramaEducativo> programas = programaEducativoRepository.findAll();
        return ResponseEntity.ok(programas);
    }

    /**
     * Obtener un programa educativo por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable Long id) {
        Optional<ProgramaEducativo> programaOpt = programaEducativoRepository.findById(id);
        if (!programaOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(programaOpt.get());
    }

    /**
     * Obtener un programa educativo por clave
     */
    @GetMapping("/clave/{clave}")
    public ResponseEntity<?> obtenerPorClave(@PathVariable String clave) {
        Optional<ProgramaEducativo> programaOpt = programaEducativoRepository.findByClave(clave);
        if (!programaOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(programaOpt.get());
    }

    /**
     * Crear un nuevo programa educativo
     */
    @PostMapping
    public ResponseEntity<?> crear(@RequestBody ProgramaEducativo programa) {
        try {
            // Validar que no exista la clave
            if (programaEducativoRepository.existsByClave(programa.getClave())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Ya existe un programa con la clave: " + programa.getClave());
            }

            ProgramaEducativo programaGuardado = programaEducativoRepository.save(programa);
            return ResponseEntity.status(HttpStatus.CREATED).body(programaGuardado);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al crear el programa: " + e.getMessage());
        }
    }

    /**
     * Actualizar un programa educativo existente
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody ProgramaEducativo programaActualizado) {
        Optional<ProgramaEducativo> programaOpt = programaEducativoRepository.findById(id);
        if (!programaOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        ProgramaEducativo programa = programaOpt.get();
        programa.setClave(programaActualizado.getClave());
        programa.setClaveDgp(programaActualizado.getClaveDgp());
        programa.setNombre(programaActualizado.getNombre());
        programa.setTipoPrograma(programaActualizado.getTipoPrograma());
        programa.setDuracionPeriodos(programaActualizado.getDuracionPeriodos());
        programa.setTipoPeriodo(programaActualizado.getTipoPeriodo());
        programa.setModalidad(programaActualizado.getModalidad());
        programa.setCreditosTotales(programaActualizado.getCreditosTotales());
        programa.setRvoe(programaActualizado.getRvoe());
        programa.setFechaRvoe(programaActualizado.getFechaRvoe());
        programa.setEstatus(programaActualizado.getEstatus());
        programa.setDescripcion(programaActualizado.getDescripcion());
        ProgramaEducativo programaGuardado = programaEducativoRepository.save(programa);
        return ResponseEntity.ok(programaGuardado);
    }

    /**
     * Eliminar un programa educativo
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        Optional<ProgramaEducativo> programaOpt = programaEducativoRepository.findById(id);
        if (!programaOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        programaEducativoRepository.delete(programaOpt.get());
        return ResponseEntity.ok().build();
    }
}
