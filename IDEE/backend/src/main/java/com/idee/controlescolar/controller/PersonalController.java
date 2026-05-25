package com.idee.controlescolar.controller;

import com.idee.controlescolar.dto.PersonalStaffRequest;
import com.idee.controlescolar.dto.PersonalStaffRolesRequest;
import com.idee.controlescolar.dto.StaffListItemDto;
import com.idee.controlescolar.dto.StaffSaveResponse;
import com.idee.controlescolar.model.DocumentoAlumno;
import com.idee.controlescolar.model.Maestro;
import com.idee.controlescolar.model.Personal;
import com.idee.controlescolar.model.PersonalCedulaProfesional;
import com.idee.controlescolar.model.PersonalDocumento;
import com.idee.controlescolar.repository.AlumnoRepository;
import com.idee.controlescolar.repository.MaestroRepository;
import com.idee.controlescolar.model.Usuario;
import com.idee.controlescolar.repository.PersonalRepository;
import com.idee.controlescolar.security.RequierePermiso;
import com.idee.controlescolar.service.StaffPersonalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controlador REST para la gestión de Personal Administrativo
 */
@RestController
@RequestMapping("/personal")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class PersonalController {

    private final PersonalRepository personalRepository;
    private final StaffPersonalService staffPersonalService;
    private final AlumnoRepository alumnoRepository;
    private final MaestroRepository maestroRepository;

    private static boolean puedeDarAltaStaffUnificado(Usuario u) {
        return u != null && (u.tieneRol(Usuario.TipoUsuario.ADMIN)
                || u.tieneRol(Usuario.TipoUsuario.SECRETARIA_ACADEMICA)
                || u.tieneRol(Usuario.TipoUsuario.SECRETARIA_ADMINISTRATIVA));
    }

    /**
     * Obtener todo el personal
     */
    @GetMapping
    @RequierePermiso("VER_DOCENTES")
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
    @RequierePermiso("VER_DOCENTES")
    public ResponseEntity<Personal> obtenerPorId(@PathVariable Long id) {
        try {
            return personalRepository.findByIdWithCedulasProfesionales(id)
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
    @RequierePermiso("ACTUALIZAR_DOCENTES")
    public ResponseEntity<Object> crear(@RequestBody Personal personal, Authentication authentication) {
        try {
            // Restricción extra: solo ADMIN y SECRETARIA_ACADEMICA pueden dar de alta personal administrativo
            Usuario u = (authentication != null && authentication.getPrincipal() instanceof Usuario)
                    ? (Usuario) authentication.getPrincipal()
                    : null;
            if (!puedeDarAltaStaffUnificado(u)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No tienes permisos para dar de alta personal administrativo."));
            }

            // Normalización: nombres en formato Título, RFC en mayúsculas
            if (personal.getNombre() != null) personal.setNombre(normalizarNombreTitulo(personal.getNombre()));
            if (personal.getApellidoPaterno() != null) personal.setApellidoPaterno(normalizarNombreTitulo(personal.getApellidoPaterno()));
            if (personal.getApellidoMaterno() != null) personal.setApellidoMaterno(normalizarNombreTitulo(personal.getApellidoMaterno()));
            if (personal.getNombreContactoEmergencia() != null) personal.setNombreContactoEmergencia(normalizarNombreTitulo(personal.getNombreContactoEmergencia()));
            if (personal.getRfc() != null && !personal.getRfc().isBlank()) personal.setRfc(personal.getRfc().trim().toUpperCase());

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
    @RequierePermiso("ACTUALIZAR_DOCENTES")
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
                                personal.setNombre(normalizarNombreTitulo(personalActualizado.getNombre()));
                            }
                            if (personalActualizado.getApellidoPaterno() != null) {
                                personal.setApellidoPaterno(normalizarNombreTitulo(personalActualizado.getApellidoPaterno()));
                            }
                            if (personalActualizado.getApellidoMaterno() != null) {
                                personal.setApellidoMaterno(normalizarNombreTitulo(personalActualizado.getApellidoMaterno()));
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
                                personal.setRfc(personalActualizado.getRfc().trim().toUpperCase());
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
                                personal.setNombreContactoEmergencia(normalizarNombreTitulo(personalActualizado.getNombreContactoEmergencia()));
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
    @RequierePermiso("ACTUALIZAR_DOCENTES")
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

    /**
     * Lista unificada: fichas de personal + docentes solo en tabla maestros (legado).
     */
    @GetMapping("/staff")
    @RequierePermiso("VER_DOCENTES")
    public ResponseEntity<List<StaffListItemDto>> listarStaffUnificado() {
        return ResponseEntity.ok(staffPersonalService.listarStaff());
    }

    @PostMapping(value = "/staff", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequierePermiso("ACTUALIZAR_DOCENTES")
    public ResponseEntity<?> crearStaffUnificado(@RequestBody PersonalStaffRequest req, Authentication authentication) {
        Usuario u = (authentication != null && authentication.getPrincipal() instanceof Usuario)
                ? (Usuario) authentication.getPrincipal() : null;
        if (!puedeDarAltaStaffUnificado(u)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No tienes permisos para dar de alta personal."));
        }
        try {
            Personal creado = staffPersonalService.crearStaff(req);
            StaffSaveResponse resp = new StaffSaveResponse(
                    creado.getId(),
                    (creado.getUsuario() != null ? creado.getUsuario().getId() : null),
                    "Usuario registrado correctamente.",
                    staffPersonalService.listarDocumentosBasicosPersonal(creado.getId())
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (ResponseStatusException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getReason() != null ? e.getReason() : e.getStatusCode().toString());
            return ResponseEntity.status(e.getStatusCode().value()).body(err);
        }
    }

    /**
     * Alta con archivos opcionales (copia de CURP e INE) en la misma petición.
     */
    @PostMapping(value = "/staff", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequierePermiso("ACTUALIZAR_DOCENTES")
    public ResponseEntity<?> crearStaffUnificadoMultipart(
            @RequestPart("staff") PersonalStaffRequest req,
            @RequestPart(value = "docCurp", required = false) MultipartFile docCurp,
            @RequestPart(value = "docIne", required = false) MultipartFile docIne,
            @RequestPart(value = "docCsf", required = false) MultipartFile docCsf,
            @RequestPart(value = "fotoPerfil", required = false) MultipartFile fotoPerfil,
            Authentication authentication) {
        Usuario u = (authentication != null && authentication.getPrincipal() instanceof Usuario)
                ? (Usuario) authentication.getPrincipal() : null;
        if (!puedeDarAltaStaffUnificado(u)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No tienes permisos para dar de alta personal."));
        }
        try {
            Personal creado = staffPersonalService.crearStaff(req, docCurp, docIne, docCsf, fotoPerfil);
            StaffSaveResponse resp = new StaffSaveResponse(
                    creado.getId(),
                    (creado.getUsuario() != null ? creado.getUsuario().getId() : null),
                    "Usuario registrado correctamente.",
                    staffPersonalService.listarDocumentosBasicosPersonal(creado.getId())
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (ResponseStatusException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getReason() != null ? e.getReason() : e.getStatusCode().toString());
            return ResponseEntity.status(e.getStatusCode().value()).body(err);
        }
    }

    @PutMapping(value = "/staff/{personalId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequierePermiso("ACTUALIZAR_DOCENTES")
    public ResponseEntity<?> actualizarStaffUnificado(@PathVariable Long personalId,
            @RequestBody PersonalStaffRequest req, Authentication authentication) {
        Usuario u = (authentication != null && authentication.getPrincipal() instanceof Usuario)
                ? (Usuario) authentication.getPrincipal() : null;
        if (!puedeDarAltaStaffUnificado(u)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No tienes permisos para actualizar personal."));
        }
        try {
            Personal actualizado = staffPersonalService.actualizarStaff(personalId, req);
            StaffSaveResponse resp = new StaffSaveResponse(
                    actualizado.getId(),
                    (actualizado.getUsuario() != null ? actualizado.getUsuario().getId() : null),
                    "Registro actualizado correctamente.",
                    staffPersonalService.listarDocumentosBasicosPersonal(actualizado.getId())
            );
            return ResponseEntity.ok(resp);
        } catch (ResponseStatusException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getReason() != null ? e.getReason() : e.getStatusCode().toString());
            return ResponseEntity.status(e.getStatusCode().value()).body(err);
        }
    }

    /**
     * Actualización con archivos (CURP, INE y cédulas profesionales de filas nuevas).
     */
    @PutMapping(value = "/staff/{personalId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequierePermiso("ACTUALIZAR_DOCENTES")
    public ResponseEntity<?> actualizarStaffUnificadoMultipart(
            @PathVariable Long personalId,
            @RequestPart("staff") PersonalStaffRequest req,
            @RequestPart(value = "docCurp", required = false) MultipartFile docCurp,
            @RequestPart(value = "docIne", required = false) MultipartFile docIne,
            @RequestPart(value = "docCsf", required = false) MultipartFile docCsf,
            @RequestPart(value = "fotoPerfil", required = false) MultipartFile fotoPerfil,
            @RequestPart(value = "cedulaProfesionalArchivo", required = false) List<MultipartFile> cedulaProfesionalArchivo,
            Authentication authentication) {
        Usuario u = (authentication != null && authentication.getPrincipal() instanceof Usuario)
                ? (Usuario) authentication.getPrincipal() : null;
        if (!puedeDarAltaStaffUnificado(u)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No tienes permisos para actualizar personal."));
        }
        try {
            Personal actualizado = staffPersonalService.actualizarStaffConArchivos(personalId, req, docCurp, docIne, docCsf, fotoPerfil, cedulaProfesionalArchivo);
            StaffSaveResponse resp = new StaffSaveResponse(
                    actualizado.getId(),
                    (actualizado.getUsuario() != null ? actualizado.getUsuario().getId() : null),
                    "Registro actualizado correctamente.",
                    staffPersonalService.listarDocumentosBasicosPersonal(actualizado.getId())
            );
            return ResponseEntity.ok(resp);
        } catch (ResponseStatusException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getReason() != null ? e.getReason() : e.getStatusCode().toString());
            return ResponseEntity.status(e.getStatusCode().value()).body(err);
        }
    }

    /**
     * Documentos básicos del expediente (CURP/INE) para la ficha unificada.
     */
    @GetMapping("/staff/{personalId}/documentos")
    @RequierePermiso("VER_DOCENTES")
    public ResponseEntity<?> listarDocumentosBasicos(@PathVariable Long personalId) {
        try {
            return ResponseEntity.ok(staffPersonalService.listarDocumentosBasicosPersonal(personalId));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode().value()).body(Map.of("error", e.getReason()));
        }
    }

    /**
     * Sube/actualiza documentos del expediente del alumno vinculado al usuario (desde Staff / Roles).
     * No depende del módulo de alumnos y usa permisos de staff.
     */
    @PutMapping(value = "/staff/{personalId}/alumno/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequierePermiso("ACTUALIZAR_DOCENTES")
    public ResponseEntity<?> actualizarDocumentosAlumnoDesdeStaff(
            @PathVariable Long personalId,
            @RequestPart(value = "deleteTipos", required = false) List<String> deleteTipos,
            @RequestPart(value = "documentos", required = false) List<MultipartFile> documentos,
            @RequestPart(value = "documentosTipos", required = false) List<String> documentosTipos,
            Authentication authentication) {
        Usuario u = (authentication != null && authentication.getPrincipal() instanceof Usuario)
                ? (Usuario) authentication.getPrincipal()
                : null;
        if (!puedeDarAltaStaffUnificado(u)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No tienes permisos para actualizar documentos."));
        }
        try {
            staffPersonalService.actualizarDocumentosAlumnoDesdePersonal(personalId, deleteTipos, documentos, documentosTipos);
            return ResponseEntity.ok(Map.of("mensaje", "Documentos del estudiante actualizados"));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode().value()).body(Map.of("error", e.getReason()));
        } catch (Exception e) {
            log.error("Error al actualizar documentos del alumno desde staff: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "No se pudieron actualizar documentos"));
        }
    }

    /**
     * Fallback ultra-compatible: subir un solo documento del expediente del alumno (vía staff)
     * como body directo (application/octet-stream o application/pdf).
     */
    @PostMapping(value = "/staff/{personalId}/alumno/documentos/raw", consumes = {MediaType.APPLICATION_OCTET_STREAM_VALUE, "application/pdf"})
    @RequierePermiso("ACTUALIZAR_DOCENTES")
    public ResponseEntity<?> subirDocumentoAlumnoDesdeStaffRaw(
            @PathVariable Long personalId,
            @RequestParam String tipo,
            @RequestParam(required = false) Integer slot,
            @RequestHeader(value = "X-Filename", required = false) String filename,
            @RequestHeader(value = "X-Etiqueta-Documento", required = false) String etiquetaDocumento,
            @RequestHeader(value = "X-Numero-Cedula", required = false) String numeroCedula,
            Authentication authentication,
            @RequestBody byte[] body) {
        Usuario u = (authentication != null && authentication.getPrincipal() instanceof Usuario)
                ? (Usuario) authentication.getPrincipal()
                : null;
        if (!puedeDarAltaStaffUnificado(u)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No tienes permisos para actualizar documentos."));
        }
        DocumentoAlumno.TipoDocumento t;
        try {
            t = DocumentoAlumno.TipoDocumento.valueOf(String.valueOf(tipo).trim().toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tipo de documento inválido"));
        }
        try {
            staffPersonalService.actualizarDocumentoAlumnoRawDesdePersonal(
                    personalId, t, slot, filename, etiquetaDocumento, numeroCedula, body);
            return ResponseEntity.ok(Map.of("mensaje", "Documento del estudiante actualizado"));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode().value()).body(Map.of("error", e.getReason()));
        } catch (Exception e) {
            log.error("Error al actualizar documento raw del alumno desde staff: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "No se pudo actualizar el documento"));
        }
    }

    @GetMapping("/staff/{personalId}/documentos/{tipo}/archivo")
    @RequierePermiso("VER_DOCENTES")
    public ResponseEntity<?> descargarDocumentoBasico(@PathVariable Long personalId, @PathVariable String tipo) {
        try {
            PersonalDocumento.Tipo t = PersonalDocumento.Tipo.valueOf(tipo);
            PersonalDocumento d = staffPersonalService.obtenerDocumentoBasico(personalId, t);
            byte[] data = d.getData() != null ? d.getData() : new byte[0];
            MediaType mt = MediaType.APPLICATION_OCTET_STREAM;
            if (d.getContentType() != null && !d.getContentType().isBlank()) {
                try { mt = MediaType.parseMediaType(d.getContentType()); } catch (Exception ignored) {}
            }
            String fn = d.getFilename() != null ? d.getFilename() : (t.name().toLowerCase() + ".bin");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fn.replace("\"", "") + "\"")
                    .contentType(mt)
                    .body(data);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tipo de documento inválido"));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode().value()).body(Map.of("error", e.getReason()));
        }
    }

    @DeleteMapping("/staff/{personalId}/documentos/{tipo}")
    @RequierePermiso("ACTUALIZAR_DOCENTES")
    public ResponseEntity<?> eliminarDocumentoBasico(@PathVariable Long personalId, @PathVariable String tipo, Authentication authentication) {
        Usuario u = (authentication != null && authentication.getPrincipal() instanceof Usuario)
                ? (Usuario) authentication.getPrincipal()
                : null;
        if (!puedeDarAltaStaffUnificado(u)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No tienes permisos para eliminar documentos."));
        }
        try {
            PersonalDocumento.Tipo t = PersonalDocumento.Tipo.valueOf(tipo);
            staffPersonalService.eliminarDocumentoBasico(personalId, t);
            return ResponseEntity.ok(Map.of("mensaje", "Documento eliminado"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tipo de documento inválido"));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode().value()).body(Map.of("error", e.getReason()));
        }
    }

    @GetMapping("/{personalId}/cedulas-profesionales/{cedulaId}/archivo")
    @RequierePermiso("VER_DOCENTES")
    public ResponseEntity<byte[]> descargarCedulaProfesionalArchivo(
            @PathVariable Long personalId,
            @PathVariable Long cedulaId) {
        PersonalCedulaProfesional c = staffPersonalService.obtenerCedulaProfesionalArchivo(personalId, cedulaId);
        byte[] data = c.getData() != null ? c.getData() : new byte[0];
        MediaType mt = MediaType.APPLICATION_OCTET_STREAM;
        if (c.getContentType() != null && !c.getContentType().isBlank()) {
            try {
                mt = MediaType.parseMediaType(c.getContentType());
            } catch (Exception ignored) {
                // usar octet-stream
            }
        }
        String fn = c.getFilename() != null ? c.getFilename() : "cedula";
        return ResponseEntity.ok()
                .contentType(mt)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fn.replace("\"", "") + "\"")
                .body(data);
    }

    @DeleteMapping("/{personalId}/cedulas-profesionales/{cedulaId}/archivo")
    @RequierePermiso("ACTUALIZAR_DOCENTES")
    public ResponseEntity<?> eliminarCedulaProfesionalArchivo(
            @PathVariable Long personalId,
            @PathVariable Long cedulaId) {
        try {
            staffPersonalService.eliminarCedulaProfesionalArchivo(personalId, cedulaId);
            return ResponseEntity.ok(Map.of("mensaje", "Documento de cédula eliminado"));
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode().value()).body(Map.of("error", e.getReason()));
        } catch (Exception e) {
            log.error("Error al eliminar documento de cédula: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "No se pudo eliminar el documento"));
        }
    }

    /**
     * Foto de perfil de la ficha de personal.
     */
    @GetMapping("/{personalId}/foto-perfil")
    @RequierePermiso("VER_DOCENTES")
    public ResponseEntity<?> obtenerFotoPerfil(@PathVariable Long personalId) {
        Optional<Personal> pOpt = personalRepository.findById(personalId);
        if (pOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Personal p = pOpt.get();
        try {
            String foto = (p.getFotoUrl() != null && !p.getFotoUrl().isBlank())
                    ? p.getFotoUrl()
                    : null;
            // Fallback: si el expediente de alumno ya tiene foto, usarla para no duplicar capturas.
            if (foto == null && p.getUsuario() != null && p.getUsuario().getId() != null) {
                var oa = alumnoRepository.findByUsuarioId(p.getUsuario().getId());
                if (oa.isPresent()) {
                    var a = oa.get();
                    if (a.getFotoUrl() != null && !a.getFotoUrl().isBlank()) {
                        foto = a.getFotoUrl();
                    }
                }
            }
            if (foto == null && p.getUsuario() != null && p.getUsuario().getId() != null) {
                Optional<Maestro> om = maestroRepository.findByUsuarioId(p.getUsuario().getId());
                if (om.isPresent()) {
                    Maestro m = om.get();
                    if (m.getFotoUrl() != null && !m.getFotoUrl().isBlank()) {
                        foto = m.getFotoUrl();
                    }
                }
            }
            if (foto == null) {
                return ResponseEntity.notFound().build();
            }

            Path path = Paths.get(foto);
            if (!Files.exists(path)) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new UrlResource(path.toUri());
            String contentType = Files.probeContentType(path);
            return ResponseEntity.ok()
                    .contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (Exception e) {
            log.error("Error al obtener foto de perfil: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{personalId}/foto-perfil")
    @RequierePermiso("ACTUALIZAR_DOCENTES")
    public ResponseEntity<?> eliminarFotoPerfil(@PathVariable Long personalId) {
        Optional<Personal> pOpt = personalRepository.findById(personalId);
        if (pOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Personal p = pOpt.get();
        try {
            String foto = (p.getFotoUrl() != null && !p.getFotoUrl().isBlank()) ? p.getFotoUrl() : null;
            p.setFotoUrl(null);
            personalRepository.save(p);

            // Best-effort: si era un archivo local y existe, intentar borrarlo.
            if (foto != null) {
                try {
                    Path path = Paths.get(foto);
                    if (Files.exists(path)) {
                        Files.delete(path);
                    }
                } catch (Exception ignored) {
                    // No bloquear eliminación por un fallo al borrar archivo físico.
                }
            }
            return ResponseEntity.ok(Map.of("mensaje", "Foto de perfil eliminada"));
        } catch (Exception e) {
            log.error("Error al eliminar foto de perfil: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "No se pudo eliminar la foto de perfil"));
        }
    }

    @PatchMapping("/staff/{personalId}/roles")
    @RequierePermiso("ACTUALIZAR_DOCENTES")
    public ResponseEntity<?> actualizarRolesStaffUnificado(@PathVariable Long personalId,
            @RequestBody PersonalStaffRolesRequest req, Authentication authentication) {
        Usuario u = (authentication != null && authentication.getPrincipal() instanceof Usuario)
                ? (Usuario) authentication.getPrincipal() : null;
        if (!puedeDarAltaStaffUnificado(u)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No tienes permisos para actualizar roles."));
        }
        try {
            return ResponseEntity.ok(staffPersonalService.actualizarRolesStaff(personalId, req));
        } catch (ResponseStatusException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getReason() != null ? e.getReason() : e.getStatusCode().toString());
            return ResponseEntity.status(e.getStatusCode().value()).body(err);
        }
    }

    /**
     * Actualiza roles por {@code usuarioId} (lista unificada: incluye expediente solo estudiante sin ficha de personal).
     */
    @PatchMapping("/staff/by-usuario/{usuarioId}/roles")
    @RequierePermiso("ACTUALIZAR_DOCENTES")
    public ResponseEntity<?> actualizarRolesPorUsuario(@PathVariable Long usuarioId,
            @RequestBody PersonalStaffRolesRequest req, Authentication authentication) {
        Usuario u = (authentication != null && authentication.getPrincipal() instanceof Usuario)
                ? (Usuario) authentication.getPrincipal() : null;
        if (!puedeDarAltaStaffUnificado(u)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No tienes permisos para actualizar roles."));
        }
        try {
            return ResponseEntity.ok(staffPersonalService.actualizarRolesPorUsuarioId(usuarioId, req));
        } catch (ResponseStatusException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getReason() != null ? e.getReason() : e.getStatusCode().toString());
            return ResponseEntity.status(e.getStatusCode().value()).body(err);
        }
    }

    @PostMapping("/staff/migrar-maestro/{maestroId}")
    @RequierePermiso("ACTUALIZAR_DOCENTES")
    public ResponseEntity<?> migrarMaestroAStaff(@PathVariable Long maestroId, @RequestBody PersonalStaffRequest req,
            Authentication authentication) {
        Usuario u = (authentication != null && authentication.getPrincipal() instanceof Usuario)
                ? (Usuario) authentication.getPrincipal() : null;
        if (!puedeDarAltaStaffUnificado(u)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No tienes permisos."));
        }
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(staffPersonalService.migrarMaestroAFicha(maestroId, req));
        } catch (ResponseStatusException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getReason() != null ? e.getReason() : e.getStatusCode().toString());
            return ResponseEntity.status(e.getStatusCode().value()).body(err);
        }
    }

    @DeleteMapping("/staff/{personalId}")
    @RequierePermiso("ACTUALIZAR_DOCENTES")
    public ResponseEntity<?> eliminarStaffUnificado(@PathVariable Long personalId, Authentication authentication) {
        Usuario u = (authentication != null && authentication.getPrincipal() instanceof Usuario)
                ? (Usuario) authentication.getPrincipal() : null;
        if (!puedeDarAltaStaffUnificado(u)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No tienes permisos para eliminar personal."));
        }
        try {
            staffPersonalService.eliminarStaff(personalId);
            return ResponseEntity.ok(Map.of("mensaje", "Registro eliminado"));
        } catch (ResponseStatusException e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getReason() != null ? e.getReason() : e.getStatusCode().toString());
            return ResponseEntity.status(e.getStatusCode().value()).body(err);
        }
    }

    private static String normalizarNombreTitulo(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replaceAll("\\s+", " ");
        if (s.isEmpty()) return "";
        String[] parts = s.split(" ");
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            String w = p.trim();
            if (w.isEmpty()) continue;
            String first = w.substring(0, 1).toUpperCase();
            String rest = w.length() > 1 ? w.substring(1).toLowerCase() : "";
            if (out.length() > 0) out.append(' ');
            out.append(first).append(rest);
        }
        return out.toString();
    }
}
