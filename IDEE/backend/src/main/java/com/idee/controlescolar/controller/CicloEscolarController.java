package com.idee.controlescolar.controller;

import com.idee.controlescolar.dto.CicloEscolarRequest;
import com.idee.controlescolar.model.CicloEscolar;
import com.idee.controlescolar.model.CicloEscolarEstado;
import com.idee.controlescolar.security.RequierePermiso;
import com.idee.controlescolar.service.CicloEscolarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ciclos-escolares")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CicloEscolarController {

    private final CicloEscolarService cicloEscolarService;

    @GetMapping
    @RequierePermiso("VER_ALUMNOS")
    public ResponseEntity<List<CicloEscolar>> listar() {
        return ResponseEntity.ok(cicloEscolarService.listarTodosOrdenados());
    }

    @PostMapping
    @RequierePermiso("GESTIONAR_CICLOS_Y_PERIODOS")
    public ResponseEntity<CicloEscolar> crear(@RequestBody CicloEscolarRequest body) {
        CicloEscolarEstado est = parseCicloEstado(body != null ? body.getEstado() : null);
        CicloEscolar c = cicloEscolarService.crear(
                body.getNombre(),
                body.getFechaInicio(),
                body.getFechaFin(),
                est);
        return ResponseEntity.ok(c);
    }

    @PutMapping("/{id}")
    @RequierePermiso("GESTIONAR_CICLOS_Y_PERIODOS")
    public ResponseEntity<CicloEscolar> actualizar(@PathVariable Long id, @RequestBody CicloEscolarRequest body) {
        CicloEscolarEstado est = body.getEstado() != null ? parseCicloEstado(body.getEstado()) : null;
        return ResponseEntity.ok(cicloEscolarService.actualizar(
                id,
                body.getNombre(),
                body.getFechaInicio(),
                body.getFechaFin(),
                est));
    }

    @DeleteMapping("/{id}")
    @RequierePermiso("GESTIONAR_CICLOS_Y_PERIODOS")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        try {
            cicloEscolarService.eliminar(id);
            return ResponseEntity.ok(Map.of("message", "Ciclo escolar eliminado correctamente."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private static CicloEscolarEstado parseCicloEstado(String s) {
        if (s == null || s.isBlank()) return CicloEscolarEstado.ACTIVO;
        try {
            return CicloEscolarEstado.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado de ciclo inválido. Use ACTIVO o INACTIVO.");
        }
    }
}
