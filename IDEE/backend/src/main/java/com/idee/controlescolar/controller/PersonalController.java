package com.idee.controlescolar.controller;

import com.idee.controlescolar.model.Personal;
import com.idee.controlescolar.repository.PersonalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controlador REST para la gestión de Personal Administrativo
 */
@RestController
@RequestMapping("/api/personal")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class PersonalController {

    private final PersonalRepository personalRepository;

    /**
     * Obtener todo el personal
     */
    @GetMapping
    public ResponseEntity<List<Personal>> obtenerTodos() {
        try {
            List<Personal> personal = personalRepository.findAll();
            log.info("Se obtuvieron {} registros de personal", personal.size());
            return ResponseEntity.ok(personal);
        } catch (Exception e) {
            log.error("Error al obtener personal", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Obtener un personal por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Personal> obtenerPorId(@PathVariable Long id) {
        try {
            return personalRepository.findById(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error al obtener personal por ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Obtener personal por CURP
     */
    @GetMapping("/curp/{curp}")
    public ResponseEntity<Personal> obtenerPorCurp(@PathVariable String curp) {
        try {
            return personalRepository.findByCurp(curp)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error al obtener personal por CURP: {}", curp, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Obtener personal por correo institucional
     */
    @GetMapping("/correo/{correo}")
    public ResponseEntity<Personal> obtenerPorCorreo(@PathVariable String correo) {
        try {
            return personalRepository.findByCorreoInstitucional(correo)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error al obtener personal por correo: {}", correo, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Obtener personal activo
     */
    @GetMapping("/activo/true")
    public ResponseEntity<List<Personal>> obtenerPersonalActivo() {
        try {
            List<Personal> personal = personalRepository.findByActivoTrue();
            return ResponseEntity.ok(personal);
        } catch (Exception e) {
            log.error("Error al obtener personal activo", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Crear nuevo personal
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> crear(@RequestBody Personal personal) {
        try {
            // Validar campos requeridos
            if (personal.getNombre() == null || personal.getNombre().trim().isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "El nombre es requerido");
                return ResponseEntity.badRequest().body(error);
            }

            if (personal.getApellidoPaterno() == null || personal.getApellidoPaterno().trim().isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "El apellido paterno es requerido");
                return ResponseEntity.badRequest().body(error);
            }

            if (personal.getCorreoInstitucional() == null || personal.getCorreoInstitucional().trim().isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "El correo institucional es requerido");
                return ResponseEntity.badRequest().body(error);
            }

            if (personal.getPuesto() == null || personal.getPuesto().trim().isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "El puesto es requerido");
                return ResponseEntity.badRequest().body(error);
            }

            // Validar CURP único
            if (personal.getCurp() != null && !personal.getCurp().trim().isEmpty()) {
                if (personalRepository.existsByCurp(personal.getCurp())) {
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", "Ya existe personal con este CURP");
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
                }
            }

            // Validar correo único
            if (personalRepository.findByCorreoInstitucional(personal.getCorreoInstitucional()).isPresent()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Ya existe personal con este correo institucional");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
            }

            // Si no viene el campo activo, por defecto es true
            if (personal.getActivo() == null) {
                personal.setActivo(true);
            }

            Personal personalGuardado = personalRepository.save(personal);
            log.info("Personal creado exitosamente: {} {}", personal.getNombre(), personal.getApellidoPaterno());

            return ResponseEntity.status(HttpStatus.CREATED).body(personalGuardado);
        } catch (Exception e) {
            log.error("Error al crear personal", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error al crear personal: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Actualizar personal
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody Personal personalActualizado) {
        try {
            Optional<Personal> personalOpt = personalRepository.findById(id);
            if (!personalOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }
            
            Personal personal = personalOpt.get();
            {
                        try {
                            // Validar CURP único
                            if (personalActualizado.getCurp() != null 
                                    && !personalActualizado.getCurp().trim().isEmpty()
                                    && personalRepository.existsByCurpAndIdNot(personalActualizado.getCurp(), id)) {
                                Map<String, Object> error = new HashMap<>();
                                error.put("error", "Ya existe otro personal con este CURP");
                                return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
                            }

                            // Actualizar campos
                            if (personalActualizado.getCurp() != null) {
                                personal.setCurp(personalActualizado.getCurp());
                            }
                            if (personalActualizado.getNombre() != null) {
                                personal.setNombre(personalActualizado.getNombre());
                            }
                            if (personalActualizado.getApellidoPaterno() != null) {
                                personal.setApellidoPaterno(personalActualizado.getApellidoPaterno());
                            }
                            if (personalActualizado.getApellidoMaterno() != null) {
                                personal.setApellidoMaterno(personalActualizado.getApellidoMaterno());
                            }
                            if (personalActualizado.getEtiqueta() != null) {
                                personal.setEtiqueta(personalActualizado.getEtiqueta());
                            }
                            if (personalActualizado.getCorreoInstitucional() != null) {
                                personal.setCorreoInstitucional(personalActualizado.getCorreoInstitucional());
                            }
                            if (personalActualizado.getCorreoPersonal() != null) {
                                personal.setCorreoPersonal(personalActualizado.getCorreoPersonal());
                            }
                            if (personalActualizado.getTelefono() != null) {
                                personal.setTelefono(personalActualizado.getTelefono());
                            }
                            if (personalActualizado.getCodigoPostal() != null) {
                                personal.setCodigoPostal(personalActualizado.getCodigoPostal());
                            }
                            if (personalActualizado.getGradoAcademico() != null) {
                                personal.setGradoAcademico(personalActualizado.getGradoAcademico());
                            }
                            if (personalActualizado.getCedulaProfesional() != null) {
                                personal.setCedulaProfesional(personalActualizado.getCedulaProfesional());
                            }
                            if (personalActualizado.getPuesto() != null) {
                                personal.setPuesto(personalActualizado.getPuesto());
                            }
                            if (personalActualizado.getDepartamento() != null) {
                                personal.setDepartamento(personalActualizado.getDepartamento());
                            }
                            if (personalActualizado.getRfc() != null) {
                                personal.setRfc(personalActualizado.getRfc());
                            }
                            if (personalActualizado.getRegimenFiscal() != null) {
                                personal.setRegimenFiscal(personalActualizado.getRegimenFiscal());
                            }
                            if (personalActualizado.getFechaAlta() != null) {
                                personal.setFechaAlta(personalActualizado.getFechaAlta());
                            }
                            if (personalActualizado.getActivo() != null) {
                                personal.setActivo(personalActualizado.getActivo());
                            }
                            if (personalActualizado.getObservaciones() != null) {
                                personal.setObservaciones(personalActualizado.getObservaciones());
                            }
                            if (personalActualizado.getNombreContactoEmergencia() != null) {
                                personal.setNombreContactoEmergencia(personalActualizado.getNombreContactoEmergencia());
                            }
                            if (personalActualizado.getTelefonoContactoEmergencia() != null) {
                                personal.setTelefonoContactoEmergencia(personalActualizado.getTelefonoContactoEmergencia());
                            }

                            Personal personalGuardado = personalRepository.save(personal);
                            log.info("Personal actualizado exitosamente: ID {}", id);
                            return ResponseEntity.ok(personalGuardado);
                        } catch (Exception e) {
                            log.error("Error al actualizar personal: {}", id, e);
                            Map<String, Object> error = new HashMap<>();
                            error.put("error", "Error al actualizar personal: " + e.getMessage());
                            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
                        }
            }
        } catch (Exception e) {
            log.error("Error en actualizar personal", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Eliminar personal
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        try {
            Optional<Personal> personalOpt = personalRepository.findById(id);
            if (!personalOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }
            
            personalRepository.delete(personalOpt.get());
            log.info("Personal eliminado exitosamente: ID {}", id);
            Map<String, Object> respuesta = new HashMap<>();
            respuesta.put("mensaje", "Personal eliminado exitosamente");
            return ResponseEntity.ok(respuesta);
        } catch (Exception e) {
            log.error("Error al eliminar personal: {}", id, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error al eliminar personal: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Buscar personal por nombre o apellido
     */
    @GetMapping("/buscar/{termino}")
    public ResponseEntity<List<Personal>> buscar(@PathVariable String termino) {
        try {
            List<Personal> resultados = personalRepository
                    .findByNombreContainingIgnoreCaseOrApellidoPaternoContainingIgnoreCaseOrApellidoMaternoContainingIgnoreCase(
                            termino, termino, termino);
            return ResponseEntity.ok(resultados);
        } catch (Exception e) {
            log.error("Error al buscar personal por termino: {}", termino, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
