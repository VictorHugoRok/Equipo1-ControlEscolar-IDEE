package com.idee.controlescolar.controller;

import com.idee.controlescolar.model.Plantel;
import com.idee.controlescolar.repository.PlantelRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Catálogo de Clave DGP y Nombre del plantel.
 * Mismos roles que pueden gestionar programas educativos pueden gestionar este catálogo.
 */
@RestController
@RequestMapping("/planteles")
@CrossOrigin(origins = "*")
public class PlantelController {

    private final PlantelRepository plantelRepository;

    private static final Pattern CCT_PATTERN = Pattern.compile("^[A-Za-z0-9]{10}$");
    private static final Pattern DGAIR_PATTERN = Pattern.compile("^[A-Za-z0-9]{1,20}$");

    public PlantelController(PlantelRepository plantelRepository) {
        this.plantelRepository = plantelRepository;
    }

    @GetMapping
    public ResponseEntity<List<Plantel>> listarTodos() {
        List<Plantel> lista = plantelRepository.findAll();
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable Long id) {
        Optional<Plantel> opt = plantelRepository.findById(id);
        return opt.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/clave/{claveDgp}")
    public ResponseEntity<?> obtenerPorClaveDgp(@PathVariable String claveDgp) {
        Optional<Plantel> opt = plantelRepository.findByClaveDgp(claveDgp);
        return opt.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Plantel plantel) {
        if (plantel.getClaveDgp() == null || plantel.getClaveDgp().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("La clave DGP es obligatoria.");
        }
        if (plantel.getNombrePlantel() == null || plantel.getNombrePlantel().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("El nombre del plantel es obligatorio.");
        }
        if (plantel.getNombreCorto() == null || plantel.getNombreCorto().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("El nombre corto es obligatorio.");
        }
        if (plantel.getIdEntidadFederativa() == null || plantel.getIdEntidadFederativa().trim().isEmpty()
                || plantel.getEntidadFederativa() == null || plantel.getEntidadFederativa().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("La entidad federativa del plantel es obligatoria.");
        }
        if (plantel.getClaveCct() == null || plantel.getClaveCct().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("La clave CCT es obligatoria.");
        }
        if (!CCT_PATTERN.matcher(plantel.getClaveCct().trim()).matches()) {
            return ResponseEntity.badRequest().body("La clave CCT debe tener exactamente 10 caracteres alfanuméricos sin espacios.");
        }
        if (plantel.getClaveDgair() == null || plantel.getClaveDgair().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("La clave DGAIR es obligatoria.");
        }
        if (!DGAIR_PATTERN.matcher(plantel.getClaveDgair().trim()).matches()) {
            return ResponseEntity.badRequest().body("La clave DGAIR debe tener hasta 20 caracteres alfanuméricos sin espacios.");
        }
        if (plantel.getCampus() == null || plantel.getCampus().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("El campus es obligatorio.");
        }

        String clave = plantel.getClaveDgp().trim();
        if (plantelRepository.existsByClaveDgp(clave)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Ya existe un plantel con la clave DGP: " + clave);
        }
        plantel.setClaveDgp(clave);
        plantel.setClaveCct(plantel.getClaveCct().trim());
        plantel.setClaveDgair(plantel.getClaveDgair().trim());
        plantel.setNombrePlantel(plantel.getNombrePlantel().trim());
        plantel.setCampus(plantel.getCampus().trim());
        plantel.setNombreCorto(plantel.getNombreCorto().trim().toUpperCase());
        plantel.setIdPlantel(plantel.getIdPlantel() != null ? plantel.getIdPlantel().trim() : null);
        plantel.setIdEntidadFederativa(plantel.getIdEntidadFederativa().trim());
        plantel.setEntidadFederativa(plantel.getEntidadFederativa().trim());
        Plantel guardado = plantelRepository.save(plantel);
        return ResponseEntity.status(HttpStatus.CREATED).body(guardado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody Plantel plantel) {
        Optional<Plantel> opt = plantelRepository.findById(id);
        if (!opt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        if (plantel.getClaveDgp() == null || plantel.getClaveDgp().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("La clave DGP es obligatoria.");
        }
        if (plantel.getNombrePlantel() == null || plantel.getNombrePlantel().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("El nombre del plantel es obligatorio.");
        }
        if (plantel.getNombreCorto() == null || plantel.getNombreCorto().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("El nombre corto es obligatorio.");
        }
        if (plantel.getIdEntidadFederativa() == null || plantel.getIdEntidadFederativa().trim().isEmpty()
                || plantel.getEntidadFederativa() == null || plantel.getEntidadFederativa().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("La entidad federativa del plantel es obligatoria.");
        }
        if (plantel.getClaveCct() == null || plantel.getClaveCct().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("La clave CCT es obligatoria.");
        }
        if (!CCT_PATTERN.matcher(plantel.getClaveCct().trim()).matches()) {
            return ResponseEntity.badRequest().body("La clave CCT debe tener exactamente 10 caracteres alfanuméricos sin espacios.");
        }
        if (plantel.getClaveDgair() == null || plantel.getClaveDgair().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("La clave DGAIR es obligatoria.");
        }
        if (!DGAIR_PATTERN.matcher(plantel.getClaveDgair().trim()).matches()) {
            return ResponseEntity.badRequest().body("La clave DGAIR debe tener hasta 20 caracteres alfanuméricos sin espacios.");
        }
        if (plantel.getCampus() == null || plantel.getCampus().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("El campus es obligatorio.");
        }

        Plantel existente = opt.get();
        String nuevaClave = plantel.getClaveDgp().trim();
        if (!nuevaClave.equals(existente.getClaveDgp()) && plantelRepository.existsByClaveDgp(nuevaClave)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Ya existe otro plantel con la clave DGP: " + nuevaClave);
        }
        existente.setClaveDgp(nuevaClave);
        existente.setClaveCct(plantel.getClaveCct().trim());
        existente.setClaveDgair(plantel.getClaveDgair().trim());
        existente.setNombrePlantel(plantel.getNombrePlantel().trim());
        existente.setCampus(plantel.getCampus().trim());
        existente.setNombreCorto(plantel.getNombreCorto().trim().toUpperCase());
        existente.setIdPlantel(plantel.getIdPlantel() != null ? plantel.getIdPlantel().trim() : null);
        existente.setIdEntidadFederativa(plantel.getIdEntidadFederativa().trim());
        existente.setEntidadFederativa(plantel.getEntidadFederativa().trim());
        Plantel guardado = plantelRepository.save(existente);
        return ResponseEntity.ok(guardado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        Optional<Plantel> opt = plantelRepository.findById(id);
        if (!opt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        plantelRepository.delete(opt.get());
        return ResponseEntity.ok().build();
    }
}
