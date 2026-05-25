package com.idee.controlescolar.controller;

import com.idee.controlescolar.model.ObservacionCalificacion;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API del catálogo de observaciones predefinidas para calificaciones.
 */
@RestController
@RequestMapping("/observaciones-calificacion")
@CrossOrigin(origins = "*")
public class ObservacionCalificacionController {

    @GetMapping
    public ResponseEntity<List<ObservacionCalificacion>> listar() {
        return ResponseEntity.ok(ObservacionCalificacion.getCatalogo());
    }
}
