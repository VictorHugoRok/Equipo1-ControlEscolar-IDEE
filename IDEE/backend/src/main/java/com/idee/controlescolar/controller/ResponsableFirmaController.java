package com.idee.controlescolar.controller;

import com.idee.controlescolar.model.ResponsableFirma;
import com.idee.controlescolar.repository.ResponsableFirmaRepository;
import jakarta.validation.Valid;
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
@RequestMapping("/responsables-firma")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class ResponsableFirmaController {

    private final ResponsableFirmaRepository responsableRepository;

    @GetMapping
    public ResponseEntity<List<ResponsableFirma>> listarResponsables() {
        return ResponseEntity.ok(responsableRepository.findByActivoTrueOrderByOrdenFirmaAsc());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponsableFirma> obtenerResponsable(@PathVariable Long id) {
        return responsableRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ResponsableFirma> crearResponsable(@Valid @RequestBody ResponsableFirma responsable) {
        normalizarIdCargoYCargo(responsable);
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
            @Valid @RequestBody ResponsableFirma responsable) {
        Optional<ResponsableFirma> existingOpt = responsableRepository.findById(id);
        if (!existingOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        normalizarIdCargoYCargo(responsable);
        responsable.setId(id);
        return ResponseEntity.ok(responsableRepository.save(responsable));
    }

    /**
     * idCargo = solo el número (ej. "1"); cargo = solo el texto (ej. "DIRECTOR").
     * Si viene "1|DIRECTOR" en idCargo, se separa correctamente.
     */
    private void normalizarIdCargoYCargo(ResponsableFirma r) {
        String idRaw = r.getIdCargo();
        String cargoRaw = r.getCargo();
        if (idRaw != null && idRaw.contains("|")) {
            String[] parts = idRaw.split("\\|", 2);
            r.setIdCargo(parts[0].replaceAll("[^0-9]", ""));
            if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                r.setCargo(toTitleCaseEs(parts[1].trim()));
            }
        } else if (idRaw != null) {
            r.setIdCargo(idRaw.replaceAll("[^0-9]", ""));
        }
        if (cargoRaw != null && cargoRaw.contains("|")) {
            String[] parts = cargoRaw.split("\\|", 2);
            r.setCargo(toTitleCaseEs(parts.length > 1 ? parts[1].trim() : parts[0].trim()));
        } else if (cargoRaw != null && !cargoRaw.isBlank()) {
            r.setCargo(toTitleCaseEs(cargoRaw.trim()));
        }
    }

    private static String toTitleCaseEs(String s) {
        if (s == null) return null;
        String raw = s.trim();
        if (raw.isEmpty()) return "";
        String lower = raw.toLowerCase();
        String[] parts = lower.split("\\s+");
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < parts.length; i++) {
            String w = parts[i];
            if (w.isEmpty()) continue;
            if (i > 0) out.append(' ');
            out.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) out.append(w.substring(1));
        }
        return out.toString();
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
