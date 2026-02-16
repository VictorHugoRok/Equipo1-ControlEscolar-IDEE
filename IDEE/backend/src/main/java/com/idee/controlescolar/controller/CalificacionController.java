package com.idee.controlescolar.controller;

import com.idee.controlescolar.model.Calificacion;
import com.idee.controlescolar.repository.CalificacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/calificaciones")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class CalificacionController {

    private final CalificacionRepository calificacionRepository;

    @GetMapping
    public ResponseEntity<List<Calificacion>> listarTodas() {
        List<Calificacion> lista = calificacionRepository.findAll();
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable Long id) {
        Optional<Calificacion> opt = calificacionRepository.findById(id);
        return opt.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody Calificacion payload) {
        Optional<Calificacion> opt = calificacionRepository.findById(id);
        if (!opt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        Calificacion existente = opt.get();

        if (payload.getCalificacionFinal() != null) existente.setCalificacionFinal(payload.getCalificacionFinal());
        if (payload.getAsistenciaPorcentaje() != null) existente.setAsistenciaPorcentaje(payload.getAsistenciaPorcentaje());
        if (payload.getObservaciones() != null) existente.setObservaciones(payload.getObservaciones());
        if (payload.getConfirmada() != null) existente.setConfirmada(payload.getConfirmada());
        if (payload.getEstadoAprobacion() != null) existente.setEstadoAprobacion(payload.getEstadoAprobacion());

        Calificacion guardada = calificacionRepository.save(existente);
        return ResponseEntity.ok(guardada);
    }

    @PostMapping("/{id}/confirmar")
    public ResponseEntity<?> confirmar(@PathVariable Long id) {
        Optional<Calificacion> opt = calificacionRepository.findById(id);
        if (!opt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        Calificacion c = opt.get();
        c.setConfirmada(true);
        c.setEstadoAprobacion(Calificacion.EstadoAprobacion.CONFIRMADA);
        Calificacion guardada = calificacionRepository.save(c);
        return ResponseEntity.ok(guardada);
    }
}
