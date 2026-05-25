package com.idee.controlescolar.controller;

import com.idee.controlescolar.model.CriterioEvaluacion;
import com.idee.controlescolar.repository.CriterioEvaluacionRepository;
import com.idee.controlescolar.security.RequierePermiso;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/criterios-evaluacion")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CriterioEvaluacionController {

    private final CriterioEvaluacionRepository criterioEvaluacionRepository;

    @GetMapping
    @RequierePermiso({"VER_CALIFICACIONES", "REGISTRAR_CALIFICACIONES", "EDITAR_CALIFICACIONES"})
    public ResponseEntity<List<CriterioEvaluacion>> listar(
            @RequestParam Long grupoId,
            @RequestParam Long asignaturaId
    ) {
        if (grupoId == null || asignaturaId == null) {
            return ResponseEntity.badRequest().body(List.of());
        }
        return ResponseEntity.ok(criterioEvaluacionRepository.findByGrupo_IdAndAsignatura_Id(grupoId, asignaturaId));
    }
}

