package com.idee.controlescolar.controller;

import com.idee.controlescolar.model.ResponsableFirma;
import com.idee.controlescolar.repository.ResponsableFirmaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Controlador REST para responsables de firma.
 */
@RestController
@RequestMapping("/api/responsables-firma")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class ResponsableFirmaController {

    private final ResponsableFirmaRepository responsableRepository;

    @GetMapping
    public ResponseEntity<List<ResponsableFirma>> listarResponsables() {
        return ResponseEntity.ok(responsableRepository.findByActivoTrueOrderByOrdenFirmaAsc());
    }

    @PostMapping
    public ResponseEntity<ResponsableFirma> crearResponsable(@RequestBody ResponsableFirma responsable) {
        // Verificar si existe un registro con el mismo CURP (activo o inactivo)
        return responsableRepository.findByCurp(responsable.getCurp())
                .map(existing -> {
                    // Si existe, actualizar sus datos y reactivarlo
                    log.info("Reactivando responsable existente con CURP: {}", responsable.getCurp());
                    existing.setNombre(responsable.getNombre());
                    existing.setPrimerApellido(responsable.getPrimerApellido());
                    existing.setSegundoApellido(responsable.getSegundoApellido());
                    existing.setIdCargo(responsable.getIdCargo());
                    existing.setCargo(responsable.getCargo());
                    existing.setAbrTitulo(responsable.getAbrTitulo());
                    existing.setOrdenFirma(responsable.getOrdenFirma());
                    existing.setActivo(true);
                    return ResponseEntity.ok(responsableRepository.save(existing));
                })
                .orElseGet(() -> {
                    // Si no existe, crear uno nuevo
                    log.info("Creando nuevo responsable con CURP: {}", responsable.getCurp());
                    responsable.setActivo(true);
                    return ResponseEntity.ok(responsableRepository.save(responsable));
                });
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarResponsable(
            @PathVariable Long id,
            @RequestBody ResponsableFirma responsable) {
        Optional<ResponsableFirma> existingOpt = responsableRepository.findById(id);
        if (!existingOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        ResponsableFirma existing = existingOpt.get();
        responsable.setId(id);
        return ResponseEntity.ok(responsableRepository.save(responsable));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminarResponsable(@PathVariable Long id) {
        Optional<ResponsableFirma> responsableOpt = responsableRepository.findById(id);
        if (!responsableOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        ResponsableFirma responsable = responsableOpt.get();
        responsable.setActivo(false);
        responsableRepository.save(responsable);
        return ResponseEntity.ok().build();
    }
}
