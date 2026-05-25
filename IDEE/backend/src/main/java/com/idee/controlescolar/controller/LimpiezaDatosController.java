package com.idee.controlescolar.controller;

import com.idee.controlescolar.service.LimpiezaDatosPruebaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.idee.controlescolar.security.RequierePermiso;

import java.util.Map;

/**
 * Endpoint para limpiar todos los datos de prueba antes de pasar a uso productivo.
 * Conserva: Secretaria Académica, configuración institucional, responsable de firma.
 */
@RestController
@RequestMapping("/limpieza-datos")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class LimpiezaDatosController {

    private final LimpiezaDatosPruebaService limpiezaService;

    /**
     * POST /api/limpieza-datos/ejecutar
     * Elimina programas, asignaturas, alumnos, maestros, calificaciones, grupos, horarios,
     * certificados y títulos electrónicos. Requiere autenticación.
     */
    @PostMapping("/ejecutar")
    @RequierePermiso("EJECUTAR_LIMPIEZA_TOTAL")
    public ResponseEntity<?> ejecutarLimpieza() {
        try {
            LimpiezaDatosPruebaService.ResumenLimpieza resumen = limpiezaService.ejecutarLimpieza();
            return ResponseEntity.ok(Map.of(
                    "mensaje", "Limpieza de datos de prueba completada. Se conservó Secretaria Académica y configuración institucional.",
                    "resumen", resumen
            ));
        } catch (Exception e) {
            log.error("Error al ejecutar limpieza: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error al ejecutar limpieza: " + e.getMessage()));
        }
    }

    /**
     * POST /api/limpieza-datos/reset-total
     * Resetea por completo la BD (todas las tablas de public) con TRUNCATE ... CASCADE.
     * Nota: conserva la estructura (migraciones), pero borra todos los datos y reinicia IDs.
     */
    @PostMapping("/reset-total")
    @RequierePermiso("EJECUTAR_LIMPIEZA_TOTAL")
    public ResponseEntity<?> resetTotal() {
        try {
            Map<String, Object> resp = limpiezaService.resetTotal();
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("Error al ejecutar reset total: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error al ejecutar reset total: " + e.getMessage()));
        }
    }
}
