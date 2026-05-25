package com.idee.controlescolar.controller;

import com.idee.controlescolar.dto.ClaseMaestroDTO;
import com.idee.controlescolar.dto.CriterioEvaluacionRequest;
import com.idee.controlescolar.dto.HorarioBloqueDTO;
import com.idee.controlescolar.model.HorarioBloque;
import com.idee.controlescolar.model.Maestro;
import com.idee.controlescolar.model.DocumentoAlumno;
import com.idee.controlescolar.model.MaestroDocumento;
import com.idee.controlescolar.model.Usuario;
import com.idee.controlescolar.repository.MaestroRepository;
import com.idee.controlescolar.service.DocenteExpedienteSyncService;
import com.idee.controlescolar.service.FileStorageService;
import com.idee.controlescolar.service.MaestroDocumentoExpedienteService;
import com.idee.controlescolar.repository.UsuarioRepository;
import com.idee.controlescolar.model.Calificacion;
import com.idee.controlescolar.model.CriterioEvaluacion;
import com.idee.controlescolar.model.Grupo;
import com.idee.controlescolar.repository.AsignaturaRepository;
import com.idee.controlescolar.repository.CalificacionRepository;
import com.idee.controlescolar.repository.CriterioEvaluacionRepository;
import com.idee.controlescolar.repository.GrupoRepository;
import com.idee.controlescolar.repository.HorarioBloqueRepository;
import com.idee.controlescolar.security.RequierePermiso;
import org.springframework.transaction.annotation.Transactional;
import com.idee.controlescolar.util.ValidacionRfc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

/**
 * Controlador REST para gestionar maestros (docentes).
 * Al crear/actualizar maestro: correo institucional = acceso al sistema (contraseña por defecto: idee1234).
 */
@RestController
@RequestMapping("/maestros")
@CrossOrigin(origins = "*")
public class MaestroController {

    private static final String PASSWORD_MAESTRO = "idee1234";

    @Autowired
    private MaestroRepository maestroRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private GrupoRepository grupoRepository;

    @Autowired
    private CalificacionRepository calificacionRepository;

    @Autowired
    private CriterioEvaluacionRepository criterioEvaluacionRepository;

    @Autowired
    private AsignaturaRepository asignaturaRepository;

    @Autowired
    private HorarioBloqueRepository horarioBloqueRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private MaestroDocumentoExpedienteService maestroDocumentoExpedienteService;

    @Autowired
    private DocenteExpedienteSyncService docenteExpedienteSyncService;

    /**
     * Portal docente ({@code /me}): rol {@code MAESTRO} en la cuenta o ficha {@link Maestro} vinculada.
     * Así se evita 403 cuando existe registro en {@code maestros} pero el enum no quedó alineado con roles adicionales.
     */
    private boolean puedeAccederPortalMaestro(Usuario usuario) {
        if (usuario == null) {
            return false;
        }
        if (usuario.tieneRol(Usuario.TipoUsuario.MAESTRO)) {
            return true;
        }
        return maestroRepository.findByUsuarioId(usuario.getId()).isPresent();
    }

    /**
     * Obtener el maestro del usuario autenticado.
     */
    @GetMapping("/me")
    public ResponseEntity<?> obtenerYo(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        Usuario usuario = (Usuario) authentication.getPrincipal();
        if (!puedeAccederPortalMaestro(usuario)) {
            return ResponseEntity.status(403).build();
        }
        return maestroRepository.findByUsuarioId(usuario.getId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/me/foto")
    public ResponseEntity<?> obtenerMiFotoMaestro(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        Usuario usuario = (Usuario) authentication.getPrincipal();
        if (!puedeAccederPortalMaestro(usuario)) {
            return ResponseEntity.status(403).build();
        }
        Optional<Maestro> opt = maestroRepository.findByUsuarioId(usuario.getId());
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Maestro maestro = opt.get();
        try {
            if (maestro.getFotoUrl() == null || maestro.getFotoUrl().isBlank()) {
                return ResponseEntity.notFound().build();
            }
            Path path = Paths.get(maestro.getFotoUrl());
            if (!Files.exists(path)) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new UrlResource(path.toUri());
            String contentType = Files.probeContentType(path);
            return ResponseEntity.ok()
                    .contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Actualiza la foto de perfil del docente (mismo criterio que el portal del estudiante).
     */
    @PutMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> actualizarMiFotoMaestro(
            Authentication authentication,
            @RequestPart(value = "foto", required = false) MultipartFile foto) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        Usuario usuario = (Usuario) authentication.getPrincipal();
        if (!puedeAccederPortalMaestro(usuario)) {
            return ResponseEntity.status(403).build();
        }
        Optional<Maestro> opt = maestroRepository.findByUsuarioId(usuario.getId());
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Maestro maestro = opt.get();
        if (foto == null || foto.isEmpty()) {
            return ResponseEntity.badRequest().body("Se requiere la parte 'foto'.");
        }
        try {
            String url = fileStorageService.storeMaestroFile(maestro.getId(), foto, "foto");
            maestro.setFotoUrl(url);
            Maestro guardado = maestroRepository.save(maestro);
            docenteExpedienteSyncService.propagarMaestroHaciaPersonal(guardado.getId());
            return ResponseEntity.ok(maestroRepository.findById(guardado.getId()).orElse(guardado));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("No se pudo guardar la foto: " + e.getMessage());
        }
    }

    @GetMapping("/me/documentos")
    public ResponseEntity<?> listarMisDocumentosMaestro(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        Usuario usuario = (Usuario) authentication.getPrincipal();
        if (!puedeAccederPortalMaestro(usuario)) {
            return ResponseEntity.status(403).build();
        }
        return maestroRepository.findByUsuarioId(usuario.getId())
                .map(m -> ResponseEntity.ok(maestroDocumentoExpedienteService.listarMetadatos(m.getId())))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/me/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> subirMisDocumentosMaestro(
            Authentication authentication,
            @RequestPart(value = "documentos", required = false) List<MultipartFile> documentos,
            @RequestPart(value = "documentosTipos", required = false) List<String> documentosTipos) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        Usuario usuario = (Usuario) authentication.getPrincipal();
        if (!puedeAccederPortalMaestro(usuario)) {
            return ResponseEntity.status(403).build();
        }
        Optional<Maestro> opt = maestroRepository.findByUsuarioId(usuario.getId());
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Maestro maestro = opt.get();
        try {
            procesarDocumentosPortalMaestro(maestro, documentos, documentosTipos);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "No se pudieron guardar los documentos: " + e.getMessage()));
        }
        Maestro guardado = maestroRepository.save(maestro);
        docenteExpedienteSyncService.propagarMaestroHaciaPersonal(guardado.getId());
        return ResponseEntity.ok(maestroRepository.findById(guardado.getId()).orElse(guardado));
    }

    @PostMapping(value = "/me/documentos/raw", consumes = {MediaType.APPLICATION_OCTET_STREAM_VALUE, "application/pdf"})
    public ResponseEntity<?> subirMiDocumentoRawMaestro(
            Authentication authentication,
            @RequestParam String tipo,
            @RequestHeader(value = "X-Filename", required = false) String filename,
            @RequestBody byte[] body) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        Usuario usuario = (Usuario) authentication.getPrincipal();
        if (!puedeAccederPortalMaestro(usuario)) {
            return ResponseEntity.status(403).build();
        }
        Maestro maestro = maestroRepository.findByUsuarioId(usuario.getId()).orElse(null);
        if (maestro == null) {
            return ResponseEntity.notFound().build();
        }
        DocumentoAlumno.TipoDocumento tipoDocumento;
        try {
            tipoDocumento = DocumentoAlumno.TipoDocumento.valueOf(String.valueOf(tipo).trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "Tipo de documento inválido"));
        }
        if (tipoDocumento == DocumentoAlumno.TipoDocumento.OTRO) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "Tipo de documento no permitido desde el portal."));
        }
        if (body == null || body.length < 4 || body[0] != 0x25 || body[1] != 0x50 || body[2] != 0x44 || body[3] != 0x46) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "Solo se permite subir documentos en formato PDF."));
        }
        String tipoStr = tipoDocumento.name();
        Optional<MaestroDocumento> existente = maestroDocumentoExpedienteService.buscar(maestro.getId(), tipoStr);
        if (existente.isPresent() && MaestroDocumentoExpedienteService.tieneContenido(existente.get())) {
            return ResponseEntity.badRequest().body(Collections.singletonMap(
                    "error", "Este documento ya consta en el expediente. Un administrador puede retirarlo para que puedas subir otro."));
        }
        try {
            String safeFn = (filename != null && !filename.isBlank())
                    ? filename
                    : (tipoDocumento.name().toLowerCase(Locale.ROOT) + ".pdf");
            if (!safeFn.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                safeFn = safeFn + ".pdf";
            }
            MaestroDocumento documento = maestroDocumentoExpedienteService.obtenerOCrearYVincular(maestro, tipoStr);
            documento.setFilename(safeFn);
            documento.setContentType("application/pdf");
            documento.setSizeBytes((long) body.length);
            documento.setData(body);
            Maestro guardado = maestroRepository.save(maestro);
            docenteExpedienteSyncService.propagarMaestroHaciaPersonal(guardado.getId());
            return ResponseEntity.ok(maestroRepository.findById(guardado.getId()).orElse(guardado));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "No se pudo guardar el documento: " + e.getMessage()));
        }
    }

    @GetMapping("/me/documentos/{tipo}/archivo")
    public ResponseEntity<?> descargarMiDocumentoMaestro(Authentication authentication, @PathVariable String tipo) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        Usuario usuario = (Usuario) authentication.getPrincipal();
        if (!puedeAccederPortalMaestro(usuario)) {
            return ResponseEntity.status(403).build();
        }
        Maestro maestro = maestroRepository.findByUsuarioId(usuario.getId()).orElse(null);
        if (maestro == null) {
            return ResponseEntity.notFound().build();
        }
        String tipoNorm = tipo != null ? tipo.trim().toUpperCase(Locale.ROOT) : "";
        if (tipoNorm.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        MaestroDocumento doc = maestroDocumentoExpedienteService.buscar(maestro.getId(), tipoNorm).orElse(null);
        if (doc == null || doc.getData() == null || doc.getData().length == 0) {
            return ResponseEntity.notFound().build();
        }
        String fn = doc.getFilename() != null && !doc.getFilename().isBlank() ? doc.getFilename() : (tipoNorm.toLowerCase(Locale.ROOT) + ".pdf");
        fn = fn.replace("\"", "").replace("\r", "").replace("\n", "");
        MediaType ct = doc.getContentType() != null && !doc.getContentType().isBlank()
                ? MediaType.parseMediaType(doc.getContentType())
                : MediaType.APPLICATION_PDF;
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fn + "\"")
                .contentType(ct)
                .body(new ByteArrayResource(doc.getData()));
    }

    private void procesarDocumentosPortalMaestro(
            Maestro maestro,
            List<MultipartFile> documentos,
            List<String> documentosTipos) throws Exception {
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
                    tipoDocumento = DocumentoAlumno.TipoDocumento.valueOf(tipoTexto.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    tipoDocumento = DocumentoAlumno.TipoDocumento.OTRO;
                }
            }
            if (tipoDocumento == DocumentoAlumno.TipoDocumento.OTRO) {
                throw new IllegalStateException("Tipo de documento no permitido desde el portal.");
            }
            String fn = archivo.getOriginalFilename() != null ? archivo.getOriginalFilename().trim() : "";
            String ct = archivo.getContentType() != null ? archivo.getContentType().trim() : "";
            boolean extPdf = fn.toLowerCase(Locale.ROOT).endsWith(".pdf");
            boolean ctPdf = ct.equalsIgnoreCase("application/pdf");
            if (!extPdf && !ctPdf) {
                throw new IllegalStateException("Solo se permite subir documentos en formato PDF.");
            }
            String tipoStr = tipoDocumento.name();
            Optional<MaestroDocumento> existente = maestroDocumentoExpedienteService.buscar(maestro.getId(), tipoStr);
            if (existente.isPresent() && MaestroDocumentoExpedienteService.tieneContenido(existente.get())) {
                throw new IllegalStateException(
                        "Este documento ya consta en el expediente. Un administrador puede retirarlo para que puedas subir otro.");
            }
            byte[] data = archivo.getBytes();
            MaestroDocumento documento = maestroDocumentoExpedienteService.obtenerOCrearYVincular(maestro, tipoStr);
            documento.setFilename(fn.isEmpty() ? (tipoStr.toLowerCase(Locale.ROOT) + ".pdf") : Paths.get(fn).getFileName().toString());
            documento.setContentType(ct.isEmpty() ? "application/pdf" : ct);
            documento.setSizeBytes(archivo.getSize());
            documento.setData(data);
        }
    }

    /**
     * Obtener las clases (grupo + materia) que el maestro imparte según su horario.
     * Cada combinación grupo+materia es una clase distinta (ej: Ortodoncia I en Grupo A y en Grupo B = 2 clases).
     */
    @GetMapping("/me/clases")
    public ResponseEntity<?> obtenerMisClases(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        Usuario usuario = (Usuario) authentication.getPrincipal();
        if (!puedeAccederPortalMaestro(usuario)) {
            return ResponseEntity.status(403).build();
        }
        return maestroRepository.findByUsuarioId(usuario.getId())
                .map(maestro -> {
                    var bloques = horarioBloqueRepository.findByMaestro_IdAndEstatusOrderByDiaAscHoraInicioAsc(
                            maestro.getId(), HorarioBloque.EstatusHorario.ACTIVO);
                    var vistos = new java.util.HashSet<String>();
                    var clases = new java.util.ArrayList<ClaseMaestroDTO>();
                    for (var b : bloques) {
                        if (b.getGrupoEntity() == null || b.getAsignatura() == null) continue;
                        String key = b.getGrupoEntity().getId() + "_" + b.getAsignatura().getId();
                        if (vistos.contains(key)) continue;
                        vistos.add(key);
                        clases.add(ClaseMaestroDTO.builder()
                                .grupoId(b.getGrupoEntity().getId())
                                .asignaturaId(b.getAsignatura().getId())
                                .grupoNombre(b.getGrupoEntity().getNombre())
                                .asignaturaNombre(b.getAsignatura().getNombre())
                                .periodo(b.getPeriodoAcademico() != null && b.getPeriodoAcademico().getCodigo() != null
                                        ? b.getPeriodoAcademico().getCodigo()
                                        : b.getCicloEscolar())
                                .periodoAcademicoId(b.getPeriodoAcademico() != null ? b.getPeriodoAcademico().getId() : null)
                                .build());
                    }
                    return ResponseEntity.<List<ClaseMaestroDTO>>ok(clases);
                })
                .orElse(ResponseEntity.ok(Collections.<ClaseMaestroDTO>emptyList()));
    }

    /**
     * Obtener un grupo con alumnos si el maestro imparte clase en ese grupo.
     */
    @GetMapping("/me/grupos/{grupoId}")
    public ResponseEntity<?> obtenerGrupoConAlumnos(Authentication authentication, @PathVariable Long grupoId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        Usuario usuario = (Usuario) authentication.getPrincipal();
        if (!puedeAccederPortalMaestro(usuario)) {
            return ResponseEntity.status(403).build();
        }
        return maestroRepository.findByUsuarioId(usuario.getId())
                .flatMap(maestro -> grupoRepository.findById(grupoId)
                        .filter(grupo -> {
                            if (grupo.getMaestro() != null && grupo.getMaestro().getId().equals(maestro.getId()))
                                return true;
                            return horarioBloqueRepository.findByMaestro_IdAndEstatusOrderByDiaAscHoraInicioAsc(maestro.getId(), HorarioBloque.EstatusHorario.ACTIVO)
                                    .stream().anyMatch(b -> b.getGrupoEntity() != null && b.getGrupoEntity().getId().equals(grupoId));
                        })
                        .map(ResponseEntity::ok))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Obtener los grupos del maestro autenticado (Grupo.maestro).
     */
    @GetMapping("/me/grupos")
    public ResponseEntity<List<Grupo>> obtenerMisGrupos(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        Usuario usuario = (Usuario) authentication.getPrincipal();
        if (!puedeAccederPortalMaestro(usuario)) {
            return ResponseEntity.status(403).build();
        }
        return maestroRepository.findByUsuarioId(usuario.getId())
                .map(maestro -> ResponseEntity.ok(grupoRepository.findByMaestroId(maestro.getId())))
                .orElse(ResponseEntity.ok(Collections.emptyList()));
    }

    /**
     * Obtener los bloques de horario del maestro autenticado.
     */
    @GetMapping("/me/horarios")
    public ResponseEntity<?> obtenerMisHorarios(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        Usuario usuario = (Usuario) authentication.getPrincipal();
        if (!puedeAccederPortalMaestro(usuario)) {
            return ResponseEntity.status(403).build();
        }
        return maestroRepository.findByUsuarioId(usuario.getId())
                .map(maestro -> {
                    var bloques = horarioBloqueRepository.findByMaestro_IdAndEstatusOrderByDiaAscHoraInicioAsc(
                            maestro.getId(), HorarioBloque.EstatusHorario.ACTIVO);
                    return ResponseEntity.ok(bloques.stream().map(HorarioBloqueDTO::from).toList());
                })
                .orElse(ResponseEntity.ok(Collections.emptyList()));
    }

    /**
     * Obtener criterios de evaluación del maestro para un grupo y asignatura.
     */
    @GetMapping("/me/criterios")
    public ResponseEntity<?> obtenerMisCriterios(Authentication authentication,
            @RequestParam Long grupoId, @RequestParam Long asignaturaId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        Usuario usuario = (Usuario) authentication.getPrincipal();
        if (!puedeAccederPortalMaestro(usuario)) {
            return ResponseEntity.status(403).build();
        }
        return maestroRepository.findByUsuarioId(usuario.getId())
                .map(maestro -> {
                    var lista = criterioEvaluacionRepository.findByMaestro_IdAndAsignatura_IdAndGrupo_Id(
                            maestro.getId(), asignaturaId, grupoId);
                    return ResponseEntity.ok(lista);
                })
                .orElse(ResponseEntity.ok(Collections.emptyList()));
    }

    /**
     * Guardar criterios de evaluación. Reemplaza los existentes para grupo+asignatura.
     */
    @PutMapping("/me/criterios")
    public ResponseEntity<?> guardarMisCriterios(Authentication authentication,
            @RequestParam Long grupoId, @RequestParam Long asignaturaId,
            @RequestBody java.util.List<CriterioEvaluacionRequest> criterios) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        Usuario usuario = (Usuario) authentication.getPrincipal();
        if (!puedeAccederPortalMaestro(usuario)) {
            return ResponseEntity.status(403).build();
        }
        Optional<Maestro> optMaestro = maestroRepository.findByUsuarioId(usuario.getId());
        if (!optMaestro.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        var optGrupo = grupoRepository.findById(grupoId);
        var optAsignatura = asignaturaRepository.findById(asignaturaId);
        if (!optGrupo.isPresent() || !optAsignatura.isPresent()) {
            return ResponseEntity.badRequest().body("Grupo o asignatura no encontrados.");
        }
        Grupo grupo = optGrupo.get();
        Long maestroId = optMaestro.get().getId();
        boolean tienePermiso = (grupo.getMaestro() != null && grupo.getMaestro().getId().equals(maestroId));
        if (!tienePermiso) {
            var bloques = horarioBloqueRepository.findByMaestro_IdAndEstatusOrderByDiaAscHoraInicioAsc(maestroId, HorarioBloque.EstatusHorario.ACTIVO);
            tienePermiso = bloques.stream().anyMatch(b ->
                    b.getGrupoEntity() != null && b.getGrupoEntity().getId().equals(grupoId)
                            && b.getAsignatura() != null && b.getAsignatura().getId().equals(asignaturaId));
        }
        if (!tienePermiso) {
            return ResponseEntity.status(403).body("No tienes permisos sobre este grupo y materia.");
        }
        var existentes = criterioEvaluacionRepository.findByMaestro_IdAndAsignatura_IdAndGrupo_Id(
                optMaestro.get().getId(), asignaturaId, grupoId);
        criterioEvaluacionRepository.deleteAll(existentes);
        int total = 0;
        for (var req : criterios) {
            if (req == null || req.getNombre() == null || req.getNombre().isBlank()) continue;
            int pct = req.getPorcentaje() != null ? req.getPorcentaje() : 0;
            if (pct < 1 || pct > 100) continue;
            CriterioEvaluacion c = new CriterioEvaluacion();
            c.setNombre(req.getNombre().trim());
            c.setPorcentaje(pct);
            c.setDescripcion(req.getDescripcion());
            c.setAsignatura(optAsignatura.get());
            c.setMaestro(optMaestro.get());
            c.setGrupo(grupo);
            c.setPeriodo(grupo.getPeriodoAcademico() != null ? grupo.getPeriodoAcademico().getCodigo() : null);
            c.setBloqueado(false);
            criterioEvaluacionRepository.save(c);
            total++;
        }
        var guardados = criterioEvaluacionRepository.findByMaestro_IdAndAsignatura_IdAndGrupo_Id(
                optMaestro.get().getId(), asignaturaId, grupoId);
        return ResponseEntity.ok(guardados);
    }

    /**
     * Obtener las calificaciones de los grupos/clases del maestro autenticado.
     * Incluye: grupo.maestro = maestro Y clases del horario (grupo+asignatura).
     */
    @GetMapping("/me/calificaciones")
    public ResponseEntity<List<Calificacion>> obtenerMisCalificaciones(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        Usuario usuario = (Usuario) authentication.getPrincipal();
        if (!puedeAccederPortalMaestro(usuario)) {
            return ResponseEntity.status(403).build();
        }
        return maestroRepository.findByUsuarioId(usuario.getId())
                .map(maestro -> {
                    var porGrupo = calificacionRepository.findByGrupo_Maestro_Id(maestro.getId());
                    var bloques = horarioBloqueRepository.findByMaestro_IdAndEstatusOrderByDiaAscHoraInicioAsc(
                            maestro.getId(), HorarioBloque.EstatusHorario.ACTIVO);
                    var vistos = new java.util.HashSet<Long>();
                    var todas = new java.util.ArrayList<>(porGrupo);
                    porGrupo.forEach(c -> vistos.add(c.getId()));
                    for (var b : bloques) {
                        if (b.getGrupoEntity() != null && b.getAsignatura() != null) {
                            var list = calificacionRepository.findByGrupo_IdAndAsignatura_Id(
                                    b.getGrupoEntity().getId(), b.getAsignatura().getId());
                            for (var c : list) {
                                if (!vistos.contains(c.getId())) {
                                    vistos.add(c.getId());
                                    todas.add(c);
                                }
                            }
                        }
                    }
                    return ResponseEntity.<List<Calificacion>>ok(todas);
                })
                .orElse(ResponseEntity.ok(Collections.<Calificacion>emptyList()));
    }

    @GetMapping
    @RequierePermiso("VER_DOCENTES")
    public ResponseEntity<List<Maestro>> obtenerTodos() {
        return ResponseEntity.ok(maestroRepository.findAll());
    }

    /**
     * Elimina el archivo de un documento del maestro (staff). No borra la fila; permite nueva carga desde expediente o portal.
     */
    @DeleteMapping("/{id}/documentos/{tipo}")
    @RequierePermiso("ACTUALIZAR_DOCENTES")
    @Transactional
    public ResponseEntity<?> eliminarDocumentoMaestro(@PathVariable Long id, @PathVariable String tipo) {
        Optional<Maestro> maestroOpt = maestroRepository.findById(id);
        if (maestroOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String tipoNorm = tipo != null ? tipo.trim().toUpperCase(Locale.ROOT) : "";
        if (tipoNorm.isEmpty()) {
            return ResponseEntity.badRequest().body("Tipo inválido");
        }
        if (maestroDocumentoExpedienteService.buscar(id, tipoNorm).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        maestroDocumentoExpedienteService.limpiarContenido(id, tipoNorm);
        docenteExpedienteSyncService.limpiarEspejoPersonalPorDocumentoMaestro(id, tipoNorm);
        return ResponseEntity.ok(Collections.singletonMap("mensaje", "Documento retirado del expediente"));
    }

    @GetMapping("/{id}")
    @RequierePermiso("VER_DOCENTES")
    public ResponseEntity<?> obtenerPorId(@PathVariable Long id) {
        Optional<Maestro> maestroOpt = maestroRepository.findById(id);
        if (!maestroOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(maestroOpt.get());
    }

    /**
     * Crear maestro (JSON sin archivos). Evita problemas de multipart.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequierePermiso("ACTUALIZAR_DOCENTES")
    public ResponseEntity<?> crearJson(@RequestBody Maestro maestro) {
        return crearInterno(maestro, null);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequierePermiso("ACTUALIZAR_DOCENTES")
    public ResponseEntity<?> crear(
            @RequestPart("maestro") Maestro maestro,
            @RequestPart(value = "antecedentes", required = false) List<MultipartFile> antecedentes) {
        return crearInterno(maestro, antecedentes);
    }

    private ResponseEntity<?> crearInterno(Maestro maestro, List<MultipartFile> antecedentes) {
        try {
            if (maestro.getNombre() != null) maestro.setNombre(normalizarNombreTitulo(maestro.getNombre()));
            if (maestro.getApellidoPaterno() != null) maestro.setApellidoPaterno(normalizarNombreTitulo(maestro.getApellidoPaterno()));
            if (maestro.getApellidoMaterno() != null) maestro.setApellidoMaterno(normalizarNombreTitulo(maestro.getApellidoMaterno()));
            if (maestro.getNombreContactoEmergencia() != null) maestro.setNombreContactoEmergencia(normalizarNombreTitulo(maestro.getNombreContactoEmergencia()));
            if (maestro.getRfc() != null && !maestro.getRfc().isBlank()) maestro.setRfc(maestro.getRfc().trim().toUpperCase());

            if (maestroRepository.existsByCurp(maestro.getCurp())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Ya existe un maestro con la CURP: " + maestro.getCurp());
            }
            if (maestro.getCorreoInstitucional() == null || maestro.getCorreoInstitucional().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("El correo institucional es requerido. Será el acceso al sistema (contraseña por defecto: idee1234)");
            }
            try {
                ValidacionRfc.validarFormatoOpcional(maestro.getRfc());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
            }
            if (usuarioRepository.existsByEmail(maestro.getCorreoInstitucional().trim())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("El correo institucional ya está registrado como acceso de otro usuario");
            }

            Usuario usuario = crearUsuarioMaestro(maestro.getCorreoInstitucional());
            maestro.setUsuario(usuario);

            Maestro maestroGuardado = maestroRepository.save(maestro);

            if (antecedentes != null && !antecedentes.isEmpty()) {
                int idx = 0;
                for (MultipartFile archivo : antecedentes) {
                    if (archivo == null || archivo.isEmpty()) {
                        continue;
                    }
                    String tipo = "ANT_" + idx++;
                    MaestroDocumento documento = maestroDocumentoExpedienteService.obtenerOCrearYVincular(maestroGuardado, tipo);
                    String fn = archivo.getOriginalFilename();
                    documento.setFilename(fn != null && !fn.isBlank() ? fn : "antecedente.pdf");
                    documento.setContentType(archivo.getContentType());
                    documento.setSizeBytes(archivo.getSize());
                    documento.setData(archivo.getBytes());
                }
                maestroGuardado = maestroRepository.save(maestroGuardado);
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(maestroGuardado);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al crear el maestro: " + e.getMessage());
        }
    }

    /**
     * Actualizar maestro (JSON sin archivos).
     */
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequierePermiso("ACTUALIZAR_DOCENTES")
    public ResponseEntity<?> actualizarJson(@PathVariable Long id, @RequestBody Maestro maestroActualizado) {
        return actualizarInterno(id, maestroActualizado, null);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequierePermiso("ACTUALIZAR_DOCENTES")
    public ResponseEntity<?> actualizar(
            @PathVariable Long id,
            @RequestPart("maestro") Maestro maestroActualizado,
            @RequestPart(value = "antecedentes", required = false) List<MultipartFile> antecedentes) {
        return actualizarInterno(id, maestroActualizado, antecedentes);
    }

    private ResponseEntity<?> actualizarInterno(Long id, Maestro maestroActualizado, List<MultipartFile> antecedentes) {
        Optional<Maestro> maestroOpt = maestroRepository.findById(id);
        if (!maestroOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        Maestro maestro = maestroOpt.get();
        try {
            if (maestroActualizado.getNombre() != null) maestroActualizado.setNombre(normalizarNombreTitulo(maestroActualizado.getNombre()));
            if (maestroActualizado.getApellidoPaterno() != null) maestroActualizado.setApellidoPaterno(normalizarNombreTitulo(maestroActualizado.getApellidoPaterno()));
            if (maestroActualizado.getApellidoMaterno() != null) maestroActualizado.setApellidoMaterno(normalizarNombreTitulo(maestroActualizado.getApellidoMaterno()));
            if (maestroActualizado.getNombreContactoEmergencia() != null) maestroActualizado.setNombreContactoEmergencia(normalizarNombreTitulo(maestroActualizado.getNombreContactoEmergencia()));
            if (maestroActualizado.getRfc() != null && !maestroActualizado.getRfc().isBlank()) maestroActualizado.setRfc(maestroActualizado.getRfc().trim().toUpperCase());

            try {
                ValidacionRfc.validarFormatoOpcional(maestroActualizado.getRfc());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
            }
            if (maestroActualizado.getCurp() != null
                    && maestroRepository.existsByCurpAndIdNot(maestroActualizado.getCurp(), id)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Ya existe un maestro con la CURP: " + maestroActualizado.getCurp());
            }

            maestro.setCurp(maestroActualizado.getCurp());
            maestro.setNombre(maestroActualizado.getNombre());
            maestro.setApellidoPaterno(maestroActualizado.getApellidoPaterno());
            maestro.setApellidoMaterno(maestroActualizado.getApellidoMaterno());
            maestro.setEtiqueta(maestroActualizado.getEtiqueta());
            if (maestroActualizado.getCorreoInstitucional() != null && !maestroActualizado.getCorreoInstitucional().isBlank()) {
                actualizarUsuarioMaestro(maestro, maestroActualizado.getCorreoInstitucional());
            }
            maestro.setCorreoInstitucional(maestroActualizado.getCorreoInstitucional());
            maestro.setCorreoPersonal(maestroActualizado.getCorreoPersonal());
            maestro.setTelefono(maestroActualizado.getTelefono());
            maestro.setCodigoPostal(maestroActualizado.getCodigoPostal());
            maestro.setGradoAcademico(maestroActualizado.getGradoAcademico());
            maestro.setCedulaProfesional(maestroActualizado.getCedulaProfesional());
            maestro.setArea(maestroActualizado.getArea());
            maestro.setRfc(maestroActualizado.getRfc());
            maestro.setRegimenFiscal(maestroActualizado.getRegimenFiscal());
            maestro.setTipoMaestro(maestroActualizado.getTipoMaestro());
            maestro.setFechaAlta(maestroActualizado.getFechaAlta());
            maestro.setActivo(maestroActualizado.getActivo());
            maestro.setObservaciones(maestroActualizado.getObservaciones());
            maestro.setNombreContactoEmergencia(maestroActualizado.getNombreContactoEmergencia());
            maestro.setTelefonoContactoEmergencia(maestroActualizado.getTelefonoContactoEmergencia());

            if (antecedentes != null && !antecedentes.isEmpty()) {
                int idx = 0;
                for (MultipartFile archivo : antecedentes) {
                    if (archivo == null || archivo.isEmpty()) {
                        continue;
                    }
                    String tipo = "ANT_" + idx++;
                    MaestroDocumento documento = maestroDocumentoExpedienteService.obtenerOCrearYVincular(maestro, tipo);
                    String fn = archivo.getOriginalFilename();
                    documento.setFilename(fn != null && !fn.isBlank() ? fn : "antecedente.pdf");
                    documento.setContentType(archivo.getContentType());
                    documento.setSizeBytes(archivo.getSize());
                    documento.setData(archivo.getBytes());
                }
            }

            Maestro maestroGuardado = maestroRepository.save(maestro);
            return ResponseEntity.ok(maestroGuardado);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al actualizar el maestro: " + e.getMessage());
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

    @DeleteMapping("/{id}")
    @RequierePermiso("ACTUALIZAR_DOCENTES")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        Optional<Maestro> maestroOpt = maestroRepository.findById(id);
        if (!maestroOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        Maestro m = maestroOpt.get();
        Usuario u = m.getUsuario();
        m.setUsuario(null);
        maestroRepository.save(m);
        maestroRepository.delete(m);
        if (u != null) usuarioRepository.delete(u);
        return ResponseEntity.ok().build();
    }

    private Usuario crearUsuarioMaestro(String correoInstitucional) {
        Usuario u = new Usuario();
        u.setEmail(correoInstitucional.trim().toLowerCase());
        u.setPassword(passwordEncoder.encode(PASSWORD_MAESTRO));
        u.aplicarRolesMultiples(java.util.Collections.singleton(Usuario.TipoUsuario.MAESTRO));
        u.setActivo(true);
        return usuarioRepository.save(u);
    }

    private void actualizarUsuarioMaestro(Maestro maestro, String nuevoCorreo) {
        String correo = nuevoCorreo != null ? nuevoCorreo.trim().toLowerCase() : "";
        if (correo.isBlank()) return;
        Usuario u = maestro.getUsuario();
        if (u == null) {
            if (usuarioRepository.existsByEmail(correo)) return;
            u = crearUsuarioMaestro(correo);
            maestro.setUsuario(u);
        } else if (!u.getEmail().equalsIgnoreCase(correo)) {
            if (usuarioRepository.existsByEmail(correo)) return;
            u.setEmail(correo);
            usuarioRepository.save(u);
        }
    }
}
