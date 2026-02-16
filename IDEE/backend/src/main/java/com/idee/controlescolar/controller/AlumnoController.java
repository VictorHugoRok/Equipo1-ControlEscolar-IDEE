package com.idee.controlescolar.controller;

import com.idee.controlescolar.model.Alumno;
import com.idee.controlescolar.model.DocumentoAlumno;
import com.idee.controlescolar.repository.AlumnoRepository;
import com.idee.controlescolar.repository.ProgramaEducativoRepository;
import com.idee.controlescolar.service.EmailService;
import com.idee.controlescolar.service.FileStorageService;
import com.idee.controlescolar.repository.TituloElectronicoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.time.LocalDate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controlador REST para la gestión de alumnos.
 */
@RestController
@RequestMapping("/api/alumnos")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AlumnoController {

    private final AlumnoRepository alumnoRepository;
    private final ProgramaEducativoRepository programaRepository;
    private final FileStorageService fileStorageService;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final TituloElectronicoRepository tituloRepository;

    /**
     * Obtener todos los alumnos
     */
    @GetMapping
    public ResponseEntity<List<Alumno>> obtenerTodos() {
        List<Alumno> alumnos = alumnoRepository.findAll();
        return ResponseEntity.ok(alumnos);
    }

    /**
     * Obtener un alumno por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable Long id) {
        Optional<Alumno> alumnoOpt = alumnoRepository.findById(id);
        if (!alumnoOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(alumnoOpt.get());
    }

    /**
     * Buscar alumno por matrícula
     */
    @GetMapping("/matricula/{matricula}")
    public ResponseEntity<?> obtenerPorMatricula(@PathVariable String matricula) {
        Optional<Alumno> alumnoOpt = alumnoRepository.findByMatricula(matricula);
        if (!alumnoOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(alumnoOpt.get());
    }

    /**
     * Buscar alumno por CURP
     */
    @GetMapping("/curp/{curp}")
    public ResponseEntity<?> obtenerPorCurp(@PathVariable String curp) {
        Optional<Alumno> alumnoOpt = alumnoRepository.findByCurp(curp);
        if (!alumnoOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(alumnoOpt.get());
    }

    /**
     * Crear un nuevo alumno (JSON)
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> crear(@RequestBody Alumno alumno) {
        try {
            ResponseEntity<Object> validacion = validarMatricula(alumno.getMatricula());
            if (validacion != null) {
                return validacion;
            }
            // Validar que no exista la matrícula
            if (alumnoRepository.existsByMatricula(alumno.getMatricula())) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "La matrícula ya existe");
                return ResponseEntity.badRequest().body(error);
            }

            // Validar que no exista el CURP
            if (alumno.getCurp() != null && alumnoRepository.findByCurp(alumno.getCurp()).isPresent()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "El CURP ya está registrado");
                return ResponseEntity.badRequest().body(error);
            }

            // Establecer programa si viene el ID
            if (alumno.getPrograma() != null && alumno.getPrograma().getId() != null) {
                programaRepository.findById(alumno.getPrograma().getId())
                        .ifPresent(alumno::setPrograma);
            }

            Alumno guardado = alumnoRepository.save(alumno);
            enviarCorreoInscripcion(guardado);
            log.info("Alumno creado: {} - {}", guardado.getMatricula(), guardado.getNombreCompleto());
            return ResponseEntity.ok(guardado);

        } catch (Exception e) {
            log.error("Error al crear alumno: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error al crear alumno: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Crear un nuevo alumno (multipart)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> crearConArchivos(
            @RequestPart("alumno") String alumnoJson,
            @RequestPart(value = "foto", required = false) MultipartFile foto,
            @RequestPart(value = "documentos", required = false) List<MultipartFile> documentos,
            @RequestPart(value = "documentosTipos", required = false) List<String> documentosTipos) {
        try {
            Alumno alumno = parseAlumnoPart(alumnoJson);
            ResponseEntity<Object> validacion = validarMatricula(alumno.getMatricula());
            if (validacion != null) {
                return validacion;
            }
            // Validar que no exista la matrícula
            if (alumnoRepository.existsByMatricula(alumno.getMatricula())) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "La matrícula ya existe");
                return ResponseEntity.badRequest().body(error);
            }

            // Validar CURP único
            if (alumno.getCurp() != null && alumnoRepository.findByCurp(alumno.getCurp()).isPresent()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "El CURP ya está registrado");
                return ResponseEntity.badRequest().body(error);
            }

            aplicarPrograma(alumno);
            Alumno guardado = alumnoRepository.save(alumno);

            try {
                procesarArchivosAlumno(guardado, foto, documentos, documentosTipos);
            } catch (Exception e) {
                log.warn("Advertencia al procesar archivos del alumno: {}", e.getMessage());
                // No lanzar error si falla el procesamiento de archivos, solo advertir
            }
            
            Alumno actualizado = alumnoRepository.save(guardado);

            enviarCorreoInscripcion(actualizado);
            log.info("Alumno creado: {} - {}", actualizado.getMatricula(), actualizado.getNombreCompleto());
            return ResponseEntity.ok(actualizado);

        } catch (Exception e) {
            log.error("Error al crear alumno: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error al crear alumno: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Actualizar un alumno existente (JSON)
     */
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody Alumno alumno) {
        try {
            Optional<Alumno> existenteOpt = alumnoRepository.findById(id);
            if (!existenteOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }
            
            Alumno existente = existenteOpt.get();
            ResponseEntity<Object> validacion = validarMatricula(alumno.getMatricula());
            if (validacion != null) {
                return validacion;
            }
            // Validar matrícula única (excepto si es la misma)
            if (!existente.getMatricula().equals(alumno.getMatricula()) &&
                    alumnoRepository.existsByMatricula(alumno.getMatricula())) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "La matrícula ya existe");
                return ResponseEntity.badRequest().body(error);
            }

            // Validar CURP único (excepto si es el mismo)
            if (alumno.getCurp() != null &&
                    !existente.getCurp().equals(alumno.getCurp()) &&
                    alumnoRepository.findByCurp(alumno.getCurp()).isPresent()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "El CURP ya está registrado");
                return ResponseEntity.badRequest().body(error);
            }

            // Actualizar campos
            alumno.setId(id);

            // Establecer programa si viene el ID
            aplicarPrograma(alumno);

            Alumno actualizado = alumnoRepository.save(alumno);
            log.info("Alumno actualizado: {} - {}", actualizado.getMatricula(), actualizado.getNombreCompleto());
            return ResponseEntity.ok(actualizado);

        } catch (Exception e) {
            log.error("Error al actualizar alumno: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error al actualizar alumno: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Actualizar un alumno existente (multipart)
     */
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> actualizarConArchivos(
            @PathVariable Long id,
            @RequestPart("alumno") String alumnoJson,
            @RequestPart(value = "foto", required = false) MultipartFile foto,
            @RequestPart(value = "documentos", required = false) List<MultipartFile> documentos,
            @RequestPart(value = "documentosTipos", required = false) List<String> documentosTipos) {
        try {
            Optional<Alumno> existenteOpt = alumnoRepository.findById(id);
            if (!existenteOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }
            
            Alumno existente = existenteOpt.get();
            try {
                Alumno alumnoActualizado = parseAlumnoPart(alumnoJson);
                ResponseEntity<Object> validacion = validarMatricula(alumnoActualizado.getMatricula());
                if (validacion != null) {
                    return validacion;
                }
                // Validar matrícula única
                if (!existente.getMatricula().equals(alumnoActualizado.getMatricula()) &&
                        alumnoRepository.existsByMatricula(alumnoActualizado.getMatricula())) {
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", "La matrícula ya existe");
                    return ResponseEntity.badRequest().body(error);
                }

                // Validar CURP único
                if (alumnoActualizado.getCurp() != null &&
                        !existente.getCurp().equals(alumnoActualizado.getCurp()) &&
                        alumnoRepository.findByCurp(alumnoActualizado.getCurp()).isPresent()) {
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", "El CURP ya está registrado");
                    return ResponseEntity.badRequest().body(error);
                }

                alumnoActualizado.setId(id);
                aplicarPrograma(alumnoActualizado);

                existente.setMatricula(alumnoActualizado.getMatricula());
                existente.setNombre(alumnoActualizado.getNombre());
                existente.setApellidoPaterno(alumnoActualizado.getApellidoPaterno());
                existente.setApellidoMaterno(alumnoActualizado.getApellidoMaterno());
                existente.setCurp(alumnoActualizado.getCurp());
                existente.setCorreoInstitucional(alumnoActualizado.getCorreoInstitucional());
                existente.setCorreoPersonal(alumnoActualizado.getCorreoPersonal());
                existente.setTelefono(alumnoActualizado.getTelefono());
                existente.setCodigoPostal(alumnoActualizado.getCodigoPostal());
                if (alumnoActualizado.getSexo() != null) {
                    existente.setSexo(alumnoActualizado.getSexo());
                }
                if (alumnoActualizado.getFechaNacimiento() != null) {
                    existente.setFechaNacimiento(alumnoActualizado.getFechaNacimiento());
                }
                existente.setNombreContactoEmergencia(alumnoActualizado.getNombreContactoEmergencia());
                existente.setTelefonoContactoEmergencia(alumnoActualizado.getTelefonoContactoEmergencia());
                existente.setPrograma(alumnoActualizado.getPrograma());
                existente.setCicloEscolar(alumnoActualizado.getCicloEscolar());
                existente.setTurno(alumnoActualizado.getTurno());
                existente.setEstatusMatricula(alumnoActualizado.getEstatusMatricula());
                existente.setObservaciones(alumnoActualizado.getObservaciones());

                try {
                    procesarArchivosAlumno(existente, foto, documentos, documentosTipos);
                } catch (Exception e) {
                    log.warn("Advertencia al procesar archivos del alumno: {}", e.getMessage());
                    // No lanzar error si falla el procesamiento de archivos, solo advertir
                }

                Alumno guardado = alumnoRepository.save(existente);
                log.info("Alumno actualizado: {} - {}", guardado.getMatricula(), guardado.getNombreCompleto());
                return ResponseEntity.ok(guardado);
            } catch (Exception e) {
                log.error("Error al actualizar alumno: {}", e.getMessage(), e);
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Error al actualizar alumno: " + e.getMessage());
                return ResponseEntity.internalServerError().body(error);
            }

        } catch (Exception e) {
            log.error("Error al actualizar alumno: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error al actualizar alumno: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Eliminar un alumno
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        try {
            Optional<Alumno> alumnoOpt = alumnoRepository.findById(id);
            if (!alumnoOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }
            // Evitar eliminación si el alumno tiene títulos electrónicos asociados
            if (tituloRepository.existsByAlumnoId(id)) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "No es posible eliminar el alumno: existen títulos electrónicos asociados. Elimine los títulos primero.");
                return ResponseEntity.badRequest().body(error);
            }

            Alumno alumno = alumnoOpt.get();
            alumnoRepository.delete(alumno);
            log.info("Alumno eliminado: {} - {}", alumno.getMatricula(), alumno.getNombreCompleto());

            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            log.error("Error al eliminar alumno: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error al eliminar alumno: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Obtener la foto del alumno
     */
    @GetMapping("/{id}/foto")
    public ResponseEntity<?> obtenerFoto(@PathVariable Long id) {
        Optional<Alumno> alumnoOpt = alumnoRepository.findById(id);
        if (!alumnoOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        Alumno alumno = alumnoOpt.get();
        try {
            if (alumno.getFotoUrl() == null || alumno.getFotoUrl().isBlank()) {
                return ResponseEntity.notFound().build();
            }
            Path path = Paths.get(alumno.getFotoUrl());
            if (!Files.exists(path)) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new UrlResource(path.toUri());
            String contentType = Files.probeContentType(path);
            return ResponseEntity.ok()
                    .contentType(contentType != null ? org.springframework.http.MediaType.parseMediaType(contentType)
                            : org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (Exception e) {
            log.error("Error al obtener foto del alumno: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private void aplicarPrograma(Alumno alumno) {
        if (alumno.getPrograma() != null && alumno.getPrograma().getId() != null) {
            programaRepository.findById(alumno.getPrograma().getId())
                    .ifPresent(alumno::setPrograma);
        }
    }

    private void procesarArchivosAlumno(Alumno alumno,
                                        MultipartFile foto,
                                        List<MultipartFile> documentos,
                                        List<String> documentosTipos) throws Exception {
        if (foto != null && !foto.isEmpty()) {
            String fotoUrl = fileStorageService.storeAlumnoFile(alumno.getId(), foto, "foto");
            alumno.setFotoUrl(fotoUrl);
        }

        if (documentos == null || documentos.isEmpty()) {
            return;
        }

        for (int i = 0; i < documentos.size(); i++) {
            MultipartFile archivo = documentos.get(i);
            if (archivo == null || archivo.isEmpty()) {
                continue;
            }

            String tipoTexto = documentosTipos != null && documentosTipos.size() > i ? documentosTipos.get(i) : null;
            DocumentoAlumno.TipoDocumento tipoDocumento = DocumentoAlumno.TipoDocumento.OTRO;
            if (tipoTexto != null) {
                try {
                    tipoDocumento = DocumentoAlumno.TipoDocumento.valueOf(tipoTexto.toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    tipoDocumento = DocumentoAlumno.TipoDocumento.OTRO;
                }
            }

            String archivoUrl = fileStorageService.storeAlumnoFile(alumno.getId(), archivo, tipoDocumento.name().toLowerCase());
            DocumentoAlumno.TipoDocumento tipoFinal = tipoDocumento;
            DocumentoAlumno documento = alumno.getDocumentos().stream()
                    .filter(item -> item.getTipoDocumento() == tipoFinal)
                    .findFirst()
                    .orElseGet(() -> {
                        DocumentoAlumno nuevo = new DocumentoAlumno();
                        nuevo.setAlumno(alumno);
                        nuevo.setTipoDocumento(tipoFinal);
                        alumno.getDocumentos().add(nuevo);
                        return nuevo;
                    });

            documento.setArchivoUrl(archivoUrl);
            documento.setEntregado(true);
            documento.setFechaRecepcion(LocalDate.now());
        }
    }

    private void enviarCorreoInscripcion(Alumno alumno) {
        try {
            emailService.enviarCorreoInscripcion(alumno.getCorreoPersonal(), alumno.getNombreCompleto());
        } catch (Exception e) {
            log.warn("No se pudo enviar correo de inscripcion: {}", e.getMessage());
        }
    }

    private Alumno parseAlumnoPart(String alumnoJson) {
        if (alumnoJson == null || alumnoJson.isBlank()) {
            throw new IllegalArgumentException("Parte 'alumno' vacía o ausente");
        }
        try {
            return objectMapper.readValue(alumnoJson, Alumno.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("JSON de alumno inválido: " + e.getMessage(), e);
        }
    }

    private ResponseEntity<Object> validarMatricula(String matricula) {
        if (matricula == null || !matricula.matches("^[A-Za-z0-9]{20}$")) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "La matrícula debe tener exactamente 20 caracteres alfanuméricos");
            return ResponseEntity.badRequest().body(error);
        }
        return null;
    }
}
