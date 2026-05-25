package com.idee.controlescolar.controller;

import com.idee.controlescolar.dto.HorarioBloqueDTO;
import com.idee.controlescolar.model.Alumno;
import com.idee.controlescolar.model.AlumnoPrograma;
import com.idee.controlescolar.model.AlumnoProgramaId;
import com.idee.controlescolar.model.Grupo;
import com.idee.controlescolar.model.DocumentoAlumno;
import com.idee.controlescolar.dto.AlumnoDocumentoMeta;
import com.idee.controlescolar.model.Usuario;
import com.idee.controlescolar.model.ProgramaEducativo;
import com.idee.controlescolar.model.PeriodoAcademico;
import org.springframework.security.core.Authentication;
import com.idee.controlescolar.model.Calificacion;
import com.idee.controlescolar.model.HorarioBloque;
import com.idee.controlescolar.repository.AlumnoRepository;
import com.idee.controlescolar.repository.CalificacionRepository;
import com.idee.controlescolar.repository.GrupoRepository;
import com.idee.controlescolar.repository.HorarioBloqueRepository;
import com.idee.controlescolar.repository.ProgramaEducativoRepository;
import com.idee.controlescolar.repository.UsuarioRepository;
import com.idee.controlescolar.service.AlumnoCargaMasivaService;
import com.idee.controlescolar.service.EmailService;
import com.idee.controlescolar.service.PeriodoAcademicoService;
import com.idee.controlescolar.service.ProgresoAcademicoNivelService;
import com.idee.controlescolar.service.FileStorageService;
import com.idee.controlescolar.service.ProgramaAccesoService;
import com.idee.controlescolar.repository.TituloElectronicoRepository;
import com.idee.controlescolar.security.RequierePermiso;
import com.idee.controlescolar.security.PermisosValidator;
import com.idee.controlescolar.service.EvaluacionDocenteService;
import com.idee.controlescolar.service.DocumentoAlumnoExpedienteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.time.LocalDate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Controlador REST para la gestión de alumnos.
 */
@RestController
@RequestMapping("/alumnos")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AlumnoController {

    private final AlumnoRepository alumnoRepository;
    private final CalificacionRepository calificacionRepository;
    private final ProgramaEducativoRepository programaRepository;
    private final UsuarioRepository usuarioRepository;
    private final GrupoRepository grupoRepository;
    private final HorarioBloqueRepository horarioBloqueRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final TituloElectronicoRepository tituloRepository;
    private final AlumnoCargaMasivaService cargaMasivaService;
    private final PeriodoAcademicoService periodoAcademicoService;
    private final ProgresoAcademicoNivelService progresoAcademicoNivelService;
    private final ProgramaAccesoService programaAccesoService;
    private final PermisosValidator permisosValidator;
    private final EvaluacionDocenteService evaluacionDocenteService;
    private final DocumentoAlumnoExpedienteService documentoAlumnoExpedienteService;

    private static final String PASSWORD_ALUMNO = "idee1234";

    /** Tipos que el alumno puede subir desde su portal (coherente con expediente administrativo). */
    private static final Set<DocumentoAlumno.TipoDocumento> PORTAL_DOCUMENTOS_PERMITIDOS = Set.of(
            DocumentoAlumno.TipoDocumento.CURP,
            DocumentoAlumno.TipoDocumento.INE,
            DocumentoAlumno.TipoDocumento.CONSTANCIA_SITUACION_FISCAL,
            DocumentoAlumno.TipoDocumento.ACTA_NACIMIENTO,
            DocumentoAlumno.TipoDocumento.CONSTANCIA_ESTUDIOS,
            DocumentoAlumno.TipoDocumento.TITULO_CEDULA
    );

    private void resolverYAsignarPeriodoIngreso(Alumno destino, String codigo) {
        if (destino.getPeriodoAcademicoId() != null) {
            PeriodoAcademico pa = periodoAcademicoService.findById(destino.getPeriodoAcademicoId())
                    .orElseThrow(() -> new IllegalArgumentException("Periodo académico no encontrado."));
            ProgramaEducativo prog = destino.getPrograma();
            if (prog != null && prog.getTipoPeriodo() != null && pa.getTipoPeriodo() != null
                    && !PeriodoAcademicoService.tipoCatalogo(prog.getTipoPeriodo())
                            .equals(PeriodoAcademicoService.tipoCatalogo(pa.getTipoPeriodo()))) {
                throw new IllegalArgumentException(
                        "El periodo de ingreso no corresponde al tipo de periodo del programa.");
            }
            destino.setPeriodoAcademico(pa);
            return;
        }
        if (codigo == null || codigo.isBlank()) {
            if (destino.getId() == null) {
                destino.setPeriodoAcademico(null);
            }
            return;
        }
        ProgramaEducativo.TipoPeriodo tipo = null;
        try {
            if (destino.getPrograma() != null) {
                tipo = destino.getPrograma().getTipoPeriodo();
            }
        } catch (Exception ignored) {}
        destino.setPeriodoAcademico(periodoAcademicoService.asegurarPeriodo(codigo.trim(), tipo));
    }

    /** Nivel inicial del plan (1) por programa si la matrícula en ese programa está activa y no se envió nivel. */
    private void asegurarPeriodoCursandoInicial(Alumno alumno) {
        if (alumno.getProgramasAsignados() == null || alumno.getProgramasAsignados().isEmpty()) {
            return;
        }
        for (AlumnoPrograma ap : alumno.getProgramasAsignados()) {
            if (ap == null) continue;
            if (ap.getEstatusMatricula() != null && ap.getEstatusMatricula() != AlumnoPrograma.EstatusMatriculaPrograma.ACTIVA) {
                continue;
            }
            if (ap.getPeriodoCursando() == null) {
                ap.setPeriodoCursando(1);
            }
        }
    }

    /**
     * Resuelve referencias de programa, enlaza la entidad alumno y aplica valores por defecto por inscripción
     * (estatus ACTIVA, periodo cursando 1) para soportar varios programas en un solo alta.
     */
    private void prepararInscripcionesPrograma(Alumno alumno) {
        if (alumno.getPrograma() != null && alumno.getPrograma().getId() != null) {
            programaRepository.findById(alumno.getPrograma().getId()).ifPresent(alumno::setPrograma);
        }
        if (alumno.getProgramasAsignados() == null || alumno.getProgramasAsignados().isEmpty()) {
            return;
        }
        for (AlumnoPrograma ap : alumno.getProgramasAsignados()) {
            if (ap == null) continue;
            if (ap.getPrograma() == null || ap.getPrograma().getId() == null) {
                throw new IllegalArgumentException("Cada inscripción a programa debe incluir programa.id.");
            }
            ProgramaEducativo p = programaRepository.findById(ap.getPrograma().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Programa educativo no encontrado: " + ap.getPrograma().getId()));
            ap.setPrograma(p);
            ap.setAlumno(alumno);
            if (ap.getEstatusMatricula() == null) {
                ap.setEstatusMatricula(AlumnoPrograma.EstatusMatriculaPrograma.ACTIVA);
            }
            if (ap.getPeriodoCursando() == null) {
                ap.setPeriodoCursando(1);
            }
        }
    }

    /**
     * Sincroniza el set {@link Alumno#getProgramasAsignados()} evitando duplicados y asegurando IDs estables
     * (importante porque {@link AlumnoPrograma#equals(Object)} / hashCode dependen de {@link AlumnoProgramaId}).
     *
     * Regla: el request trae la "lista final deseada" de programas. Lo que no venga, se elimina.
     */
    private void sincronizarInscripcionesPrograma(Alumno existente, Alumno alumnoReq) {
        if (existente == null || existente.getId() == null) return;
        if (alumnoReq == null) return;
        if (alumnoReq.getProgramasAsignados() == null) return;

        if (existente.getProgramasAsignados() == null) {
            existente.setProgramasAsignados(new LinkedHashSet<>());
        }

        // Indexar existentes por programaId
        Map<Long, AlumnoPrograma> existentesPorPid = new HashMap<>();
        for (AlumnoPrograma ap : existente.getProgramasAsignados()) {
            if (ap == null || ap.getPrograma() == null || ap.getPrograma().getId() == null) continue;
            existentesPorPid.put(ap.getPrograma().getId(), ap);
        }

        // De-duplicar request por programaId preservando orden de llegada
        Map<Long, AlumnoPrograma> reqPorPid = new LinkedHashMap<>();
        for (AlumnoPrograma apReq : alumnoReq.getProgramasAsignados()) {
            if (apReq == null || apReq.getPrograma() == null || apReq.getPrograma().getId() == null) continue;
            reqPorPid.put(apReq.getPrograma().getId(), apReq);
        }

        LinkedHashSet<AlumnoPrograma> nuevoSet = new LinkedHashSet<>();
        for (Map.Entry<Long, AlumnoPrograma> e : reqPorPid.entrySet()) {
            Long pid = e.getKey();
            AlumnoPrograma apReq = e.getValue();
            ProgramaEducativo p = programaRepository.findById(pid)
                    .orElseThrow(() -> new IllegalArgumentException("Programa educativo no encontrado: " + pid));

            AlumnoPrograma ap = existentesPorPid.get(pid);
            if (ap == null) {
                ap = new AlumnoPrograma();
            }
            ap.setAlumno(existente);
            ap.setPrograma(p);
            // IMPORTANTÍSIMO: fijar ID antes de meterlo al Set (evita corrupción del Set por hashCode mutable)
            ap.setId(new AlumnoProgramaId(existente.getId(), pid));

            if (apReq.getEstatusMatricula() != null) {
                ap.setEstatusMatricula(apReq.getEstatusMatricula());
            } else if (ap.getEstatusMatricula() == null) {
                ap.setEstatusMatricula(AlumnoPrograma.EstatusMatriculaPrograma.ACTIVA);
            }
            if (apReq.getPeriodoCursando() != null) {
                ap.setPeriodoCursando(apReq.getPeriodoCursando());
            } else if (ap.getPeriodoCursando() == null) {
                ap.setPeriodoCursando(1);
            }
            if (apReq.getPeriodoIngreso() != null) {
                ap.setPeriodoIngreso(apReq.getPeriodoIngreso());
            }
            if (apReq.getPeriodoAcademicoActual() != null) {
                ap.setPeriodoAcademicoActual(apReq.getPeriodoAcademicoActual());
            }

            nuevoSet.add(ap);
        }

        existente.getProgramasAsignados().clear();
        existente.getProgramasAsignados().addAll(nuevoSet);
    }

    /**
     * Carga masiva de alumnos desde archivo Excel.
     * El formato debe coincidir con el Excel descargado (Matrícula, Nombre, Apellidos, CURP, etc.)
     */
    @PostMapping(value = "/carga-masiva", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequierePermiso("ACTUALIZAR_ALUMNOS")
    public ResponseEntity<?> cargaMasiva(@RequestParam("archivo") MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Debe seleccionar un archivo Excel"));
        }
        String nombre = archivo.getOriginalFilename();
        if (nombre == null || (!nombre.toLowerCase().endsWith(".xlsx") && !nombre.toLowerCase().endsWith(".xls"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "El archivo debe ser Excel (.xlsx o .xls)"));
        }
        try {
            Map<String, Object> resultado = cargaMasivaService.procesarCargaMasiva(archivo);
            if (resultado.containsKey("error")) {
                return ResponseEntity.badRequest().body(resultado);
            }
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            log.error("Error en carga masiva: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Error al procesar el archivo: " + e.getMessage()));
        }
    }

    /**
     * Obtener todos los alumnos
     */
    @GetMapping
    @RequierePermiso("VER_ALUMNOS")
    public ResponseEntity<List<Alumno>> obtenerTodos(
            @RequestParam(required = false) Long programaId,
            Authentication authentication
    ) {
        Usuario u = (authentication != null && authentication.getPrincipal() instanceof Usuario)
                ? (Usuario) authentication.getPrincipal()
                : null;
        if (programaAccesoService.esCoordinadorAcademico(u)) {
            var permitidos = programaAccesoService.programaIdsPermitidos(u);
            if (permitidos.isEmpty()) return ResponseEntity.ok(List.of());
            if (programaId != null && !permitidos.contains(programaId)) {
                return ResponseEntity.status(403).body(List.of());
            }
            if (programaId != null) {
                return ResponseEntity.ok(alumnoRepository.findByProgramaIdOrderByApellidoPaternoAsc(programaId));
            }
            return ResponseEntity.ok(
                    alumnoRepository.findAll().stream()
                            .filter(a -> a.getPrograma() != null && permitidos.contains(a.getPrograma().getId()))
                            .toList()
            );
        }
        if (programaId != null) {
            return ResponseEntity.ok(alumnoRepository.findByProgramaIdOrderByApellidoPaternoAsc(programaId));
        }
        List<Alumno> alumnos = alumnoRepository.findAll();
        return ResponseEntity.ok(alumnos);
    }

    /**
     * Resumen aplanado (una fila por inscripción Alumno↔Programa).
     * Pensado para pantallas administrativas (kardex/certificados) donde un alumno puede tener múltiples programas.
     *
     * Campos principales:
     * - alumnoId, nombre, apellidos, matricula, curp
     * - programaId, programaNombre, programaClave, programaClaveDgp
     * - estatusMatricula (por inscripción si existe; si no, el estatus legacy del alumno)
     */
    @GetMapping("/resumen-programas")
    @RequierePermiso("VER_ALUMNOS")
    public ResponseEntity<?> resumenProgramas(Authentication authentication) {
        Usuario u = (authentication != null && authentication.getPrincipal() instanceof Usuario)
                ? (Usuario) authentication.getPrincipal()
                : null;

        // Respeta alcance de coordinador (si aplica)
        java.util.Set<Long> permitidos = null;
        if (programaAccesoService.esCoordinadorAcademico(u)) {
            permitidos = programaAccesoService.programaIdsPermitidos(u);
            if (permitidos.isEmpty()) return ResponseEntity.ok(java.util.List.of());
        }

        List<Alumno> alumnos = alumnoRepository.findAllConProgramasInscripcion();
        List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();

        for (Alumno a : alumnos) {
            if (a == null) continue;
            var ins = a.getProgramasAsignados() != null ? a.getProgramasAsignados() : java.util.Set.<AlumnoPrograma>of();
            boolean any = false;
            for (AlumnoPrograma ap : ins) {
                if (ap == null || ap.getPrograma() == null || ap.getPrograma().getId() == null) continue;
                if (permitidos != null && !permitidos.contains(ap.getPrograma().getId())) continue;
                any = true;
                java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("alumnoId", a.getId());
                row.put("nombre", a.getNombre());
                row.put("apellidoPaterno", a.getApellidoPaterno());
                row.put("apellidoMaterno", a.getApellidoMaterno());
                row.put("matricula", a.getMatricula());
                row.put("curp", a.getCurp());
                row.put("programaId", ap.getPrograma().getId());
                row.put("programaNombre", ap.getPrograma().getNombre());
                row.put("programaClave", ap.getPrograma().getClave());
                row.put("programaClaveDgp", ap.getPrograma().getClaveDgp());
                row.put("estatusMatricula", ap.getEstatusMatricula() != null ? ap.getEstatusMatricula().name()
                        : (a.getEstatusMatricula() != null ? a.getEstatusMatricula().name() : ""));
                out.add(row);
            }

            // Compatibilidad legacy: si no hay inscripciones pero existe alumno.programa
            if (!any && a.getPrograma() != null && a.getPrograma().getId() != null) {
                if (permitidos != null && !permitidos.contains(a.getPrograma().getId())) continue;
                java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("alumnoId", a.getId());
                row.put("nombre", a.getNombre());
                row.put("apellidoPaterno", a.getApellidoPaterno());
                row.put("apellidoMaterno", a.getApellidoMaterno());
                row.put("matricula", a.getMatricula());
                row.put("curp", a.getCurp());
                row.put("programaId", a.getPrograma().getId());
                row.put("programaNombre", a.getPrograma().getNombre());
                row.put("programaClave", a.getPrograma().getClave());
                row.put("programaClaveDgp", a.getPrograma().getClaveDgp());
                row.put("estatusMatricula", a.getEstatusMatricula() != null ? a.getEstatusMatricula().name() : "");
                out.add(row);
            }
        }
        return ResponseEntity.ok(out);
    }

    /**
     * Mapeo alumnoId -> periodo de ingreso (primer periodo con calificaciones).
     * Usado para filtros por periodo de ingreso sin depender de ciclo escolar.
     */
    @GetMapping("/periodos-ingreso")
    @RequierePermiso("VER_ALUMNOS")
    public ResponseEntity<Map<String, String>> obtenerPeriodosIngreso() {
        List<Object[]> rows = calificacionRepository.findAlumnoIdToPeriodoIngreso();
        Map<String, String> map = new java.util.HashMap<>();
        for (Object[] row : rows) {
            if (row.length >= 2 && row[0] != null && row[1] != null) {
                String id = String.valueOf(row[0]);
                String periodo = String.valueOf(row[1]);
                map.put(id, periodo);
            }
        }
        return ResponseEntity.ok(map);
    }

    /**
     * Obtener el alumno del usuario autenticado.
     */
    @GetMapping("/me")
    public ResponseEntity<?> obtenerYo(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        Usuario usuario = (Usuario) authentication.getPrincipal();
        if (!usuario.tieneRol(Usuario.TipoUsuario.ALUMNO)) {
            return ResponseEntity.status(403).build();
        }
        Optional<Alumno> optAl = alumnoRepository.findPortalByUsuarioId(usuario.getId());
        if (optAl.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Alumno a = optAl.get();
        if (a.getProgramasAsignados() != null) {
            for (AlumnoPrograma ap : a.getProgramasAsignados()) {
                if (ap == null) {
                    continue;
                }
                String cod = progresoAcademicoNivelService.resolverCodigoPeriodoEscolarParaInscripcion(ap)
                        .orElse(null);
                ap.setPeriodoEscolarCursandoCodigo(cod);
            }
        }
        return ResponseEntity.ok(a);
    }

    /**
     * Actualizar solo foto y/o documentos del alumno autenticado (no permite borrar la foto).
     */
    @PutMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> actualizarMisArchivos(
            Authentication authentication,
            @RequestPart(value = "foto", required = false) MultipartFile foto,
            @RequestPart(value = "documentos", required = false) List<MultipartFile> documentos,
            @RequestPart(value = "documentosTipos", required = false) List<String> documentosTipos) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        Usuario usuario = (Usuario) authentication.getPrincipal();
        if (!usuario.tieneRol(Usuario.TipoUsuario.ALUMNO)) {
            return ResponseEntity.status(403).build();
        }
        Optional<Alumno> opt = alumnoRepository.findByUsuarioId(usuario.getId());
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Alumno existente = opt.get();
        try {
            procesarArchivosAlumno(existente, foto, documentos, documentosTipos, true,
                    DocumentoAlumno.OrigenExpediente.PORTAL_ALUMNO, usuario.getId());
        } catch (Exception e) {
            log.warn("Error al procesar archivos del alumno (me): {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "No se pudieron guardar los archivos: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
        Alumno guardado = alumnoRepository.save(existente);
        progresoAcademicoNivelService.sincronizarPeriodoAcademicoEscolarDesdeNivel(guardado.getId());
        return ResponseEntity.ok(guardado);
    }

    /**
     * Subir documentos del expediente del alumno autenticado.
     *
     * Nota: algunos navegadores/proxies envían {@code PUT + FormData} con un Content-Type inesperado
     * (p. ej. {@code application/octet-stream}). Para máxima compatibilidad, el portal del alumno
     * usa este endpoint {@code POST} dedicado para documentos.
     */
    @PostMapping(value = "/me/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> subirMisDocumentos(
            Authentication authentication,
            @RequestPart(value = "documentos", required = false) List<MultipartFile> documentos,
            @RequestPart(value = "documentosTipos", required = false) List<String> documentosTipos) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        Usuario usuario = (Usuario) authentication.getPrincipal();
        if (!usuario.tieneRol(Usuario.TipoUsuario.ALUMNO)) {
            return ResponseEntity.status(403).build();
        }
        Optional<Alumno> opt = alumnoRepository.findByUsuarioId(usuario.getId());
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Alumno existente = opt.get();
        try {
            procesarArchivosAlumno(existente, null, documentos, documentosTipos, true,
                    DocumentoAlumno.OrigenExpediente.PORTAL_ALUMNO, usuario.getId());
        } catch (Exception e) {
            log.warn("Error al procesar documentos del alumno (me/documentos): {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "No se pudieron guardar los documentos: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
        Alumno guardado = alumnoRepository.save(existente);
        progresoAcademicoNivelService.sincronizarPeriodoAcademicoEscolarDesdeNivel(guardado.getId());
        return ResponseEntity.ok(guardado);
    }

    /**
     * Fallback ultra-compatible: acepta el PDF como body directo (application/pdf u octet-stream).
     * Útil si el navegador/proxy no envía multipart/form-data correctamente.
     */
    @PostMapping(value = "/me/documentos/raw", consumes = {MediaType.APPLICATION_OCTET_STREAM_VALUE, "application/pdf"})
    public ResponseEntity<?> subirMiDocumentoRaw(
            Authentication authentication,
            @RequestParam String tipo,
            @RequestParam(required = false) Integer slot,
            @RequestHeader(value = "X-Filename", required = false) String filename,
            @RequestHeader(value = "X-Etiqueta-Documento", required = false) String etiquetaDocumento,
            @RequestHeader(value = "X-Numero-Cedula", required = false) String numeroCedula,
            @RequestBody byte[] body) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        Usuario usuario = (Usuario) authentication.getPrincipal();
        if (!usuario.tieneRol(Usuario.TipoUsuario.ALUMNO)) {
            return ResponseEntity.status(403).build();
        }
        Alumno alumno = alumnoRepository.findByUsuarioId(usuario.getId()).orElse(null);
        if (alumno == null) {
            return ResponseEntity.notFound().build();
        }
        DocumentoAlumno.TipoDocumento tipoDocumento;
        try {
            tipoDocumento = DocumentoAlumno.TipoDocumento.valueOf(String.valueOf(tipo).trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tipo de documento inválido"));
        }
        if (!PORTAL_DOCUMENTOS_PERMITIDOS.contains(tipoDocumento)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tipo de documento no permitido desde el portal."));
        }
        if (body == null || body.length < 4 || body[0] != 0x25 || body[1] != 0x50 || body[2] != 0x44 || body[3] != 0x46) {
            return ResponseEntity.badRequest().body(Map.of("error", "Solo se permite subir documentos en formato PDF."));
        }
        try {
            if (tipoDocumento == DocumentoAlumno.TipoDocumento.TITULO_CEDULA) {
                if (slot == null || slot < 1 || slot > DocumentoAlumnoExpedienteService.TITULO_CEDULA_MAX_SLOTS) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Indica la posición del título/cédula (slot 1 a 4)."));
                }
                DocumentoAlumno existenteTitulo = documentoAlumnoExpedienteService.buscarTituloCedulaSlot(alumno.getId(), slot).orElse(null);
                if (existenteTitulo != null
                        && Boolean.TRUE.equals(existenteTitulo.getEntregado())
                        && existenteTitulo.getArchivoUrl() != null
                        && !existenteTitulo.getArchivoUrl().isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Este documento ya consta en el expediente. Un administrador o coordinador puede retirarlo para que puedas subir otro."));
                }
                DocumentoAlumno documento = documentoAlumnoExpedienteService.obtenerOCrearTituloCedulaEnSlot(alumno, slot);
                String etiquetaLimpia = headerText(etiquetaDocumento);
                if (etiquetaLimpia != null && !etiquetaLimpia.isBlank()) {
                    documento.setEtiquetaDocumento(etiquetaLimpia);
                }
                String numeroLimpio = headerText(numeroCedula);
                if (numeroLimpio != null && !numeroLimpio.isBlank()) {
                    documento.setNumeroCedula(numeroLimpio);
                }
                String safeFn = (filename != null && !filename.isBlank())
                        ? filename
                        : ("titulo_cedula_" + slot + ".pdf");
                if (!safeFn.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    safeFn = safeFn + ".pdf";
                }
                String archivoUrl = fileStorageService.storeAlumnoBytes(
                        alumno.getId(), body, "titulo_cedula_s" + slot, safeFn);
                documento.setArchivoUrl(archivoUrl);
                documento.setEntregado(true);
                documento.setFechaRecepcion(LocalDate.now());
                marcarTrazabilidadExpediente(documento, DocumentoAlumno.OrigenExpediente.PORTAL_ALUMNO, usuario.getId());
            } else {
                DocumentoAlumno existentePortal = documentoAlumnoExpedienteService.buscar(alumno.getId(), tipoDocumento).orElse(null);
                if (existentePortal != null
                        && Boolean.TRUE.equals(existentePortal.getEntregado())
                        && existentePortal.getArchivoUrl() != null
                        && !existentePortal.getArchivoUrl().isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Este documento ya consta en el expediente. Un administrador o coordinador puede retirarlo para que puedas subir otro."));
                }
                String safeFn = (filename != null && !filename.isBlank())
                        ? filename
                        : (tipoDocumento.name().toLowerCase(Locale.ROOT) + ".pdf");
                if (!safeFn.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    safeFn = safeFn + ".pdf";
                }
                String archivoUrl = fileStorageService.storeAlumnoBytes(
                        alumno.getId(), body, tipoDocumento.name().toLowerCase(Locale.ROOT), safeFn);
                DocumentoAlumno documento = documentoAlumnoExpedienteService.obtenerOCrearYVincular(alumno, tipoDocumento);
                documento.setArchivoUrl(archivoUrl);
                documento.setEntregado(true);
                documento.setFechaRecepcion(LocalDate.now());
                marcarTrazabilidadExpediente(documento, DocumentoAlumno.OrigenExpediente.PORTAL_ALUMNO, usuario.getId());
            }
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.warn("Error al almacenar documento raw: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "No se pudo guardar el documento: " + e.getMessage()));
        }
        Alumno guardado = alumnoRepository.save(alumno);
        progresoAcademicoNivelService.sincronizarPeriodoAcademicoEscolarDesdeNivel(guardado.getId());
        return ResponseEntity.ok(guardado);
    }

    /**
     * Subir un documento del expediente de un alumno específico (administración).
     * Fallback RAW para entornos que mandan application/octet-stream.
     */
    @PostMapping(value = "/{id}/documentos/raw", consumes = {MediaType.APPLICATION_OCTET_STREAM_VALUE, "application/pdf"})
    @RequierePermiso("ACTUALIZAR_ALUMNOS")
    public ResponseEntity<?> subirDocumentoAlumnoRaw(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam String tipo,
            @RequestParam(required = false) Integer slot,
            @RequestHeader(value = "X-Filename", required = false) String filename,
            @RequestHeader(value = "X-Etiqueta-Documento", required = false) String etiquetaDocumento,
            @RequestHeader(value = "X-Numero-Cedula", required = false) String numeroCedula,
            @RequestBody byte[] body) {
        Optional<Alumno> alumnoOpt = alumnoRepository.findById(id);
        if (alumnoOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Alumno alumno = alumnoOpt.get();
        Long operadorId = null;
        if (authentication != null && authentication.getPrincipal() instanceof Usuario uOp) {
            operadorId = uOp.getId();
        }
        DocumentoAlumno.TipoDocumento tipoDocumento;
        try {
            tipoDocumento = DocumentoAlumno.TipoDocumento.valueOf(String.valueOf(tipo).trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tipo de documento inválido"));
        }
        if (tipoDocumento == DocumentoAlumno.TipoDocumento.OTRO) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tipo de documento inválido"));
        }
        if (body == null || body.length < 4 || body[0] != 0x25 || body[1] != 0x50 || body[2] != 0x44 || body[3] != 0x46) {
            return ResponseEntity.badRequest().body(Map.of("error", "Solo se permite subir documentos en formato PDF."));
        }
        try {
            if (tipoDocumento == DocumentoAlumno.TipoDocumento.TITULO_PROFESIONAL) {
                tipoDocumento = DocumentoAlumno.TipoDocumento.TITULO_CEDULA;
                if (slot == null) {
                    slot = 1;
                }
            } else if (tipoDocumento == DocumentoAlumno.TipoDocumento.CEDULA_PROFESIONAL) {
                tipoDocumento = DocumentoAlumno.TipoDocumento.TITULO_CEDULA;
                if (slot == null) {
                    slot = 2;
                }
            }
            if (tipoDocumento == DocumentoAlumno.TipoDocumento.TITULO_CEDULA) {
                if (slot == null || slot < 1 || slot > DocumentoAlumnoExpedienteService.TITULO_CEDULA_MAX_SLOTS) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Indica slot 1 a 4 para título/cédula."));
                }
                DocumentoAlumno documento = documentoAlumnoExpedienteService.obtenerOCrearTituloCedulaEnSlot(alumno, slot);
                String etiquetaLimpia = headerText(etiquetaDocumento);
                if (etiquetaLimpia != null && !etiquetaLimpia.isBlank()) {
                    documento.setEtiquetaDocumento(etiquetaLimpia);
                }
                String numeroLimpio = headerText(numeroCedula);
                if (numeroLimpio != null && !numeroLimpio.isBlank()) {
                    documento.setNumeroCedula(numeroLimpio);
                }
                String safeFn = (filename != null && !filename.isBlank())
                        ? filename
                        : ("titulo_cedula_" + slot + ".pdf");
                if (!safeFn.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    safeFn = safeFn + ".pdf";
                }
                String archivoUrl = fileStorageService.storeAlumnoBytes(
                        alumno.getId(), body, "titulo_cedula_s" + slot, safeFn);
                documento.setArchivoUrl(archivoUrl);
                documento.setEntregado(true);
                documento.setFechaRecepcion(LocalDate.now());
                marcarTrazabilidadExpediente(documento, DocumentoAlumno.OrigenExpediente.EXPEDIENTE_STAFF, operadorId);
            } else {
                String safeFn = (filename != null && !filename.isBlank())
                        ? filename
                        : (tipoDocumento.name().toLowerCase(Locale.ROOT) + ".pdf");
                if (!safeFn.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    safeFn = safeFn + ".pdf";
                }
                String archivoUrl = fileStorageService.storeAlumnoBytes(
                        alumno.getId(), body, tipoDocumento.name().toLowerCase(Locale.ROOT), safeFn);
                DocumentoAlumno documento = documentoAlumnoExpedienteService.obtenerOCrearYVincular(alumno, tipoDocumento);
                documento.setArchivoUrl(archivoUrl);
                documento.setEntregado(true);
                documento.setFechaRecepcion(LocalDate.now());
                marcarTrazabilidadExpediente(documento, DocumentoAlumno.OrigenExpediente.EXPEDIENTE_STAFF, operadorId);
            }
            Alumno guardado = alumnoRepository.save(alumno);
            progresoAcademicoNivelService.sincronizarPeriodoAcademicoEscolarDesdeNivel(guardado.getId());
            return ResponseEntity.ok(Map.of("mensaje", "Documento actualizado"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error al guardar documento raw del alumno: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "No se pudo guardar el documento: " + e.getMessage()));
        }
    }

    /**
     * Foto del alumno autenticado (para mostrar en el portal con sesión).
     */
    @GetMapping("/me/foto")
    public ResponseEntity<?> obtenerMiFoto(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        Usuario usuario = (Usuario) authentication.getPrincipal();
        if (!usuario.tieneRol(Usuario.TipoUsuario.ALUMNO)) {
            return ResponseEntity.status(403).build();
        }
        Optional<Alumno> opt = alumnoRepository.findByUsuarioId(usuario.getId());
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Alumno alumno = opt.get();
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
            log.error("Error al obtener foto del alumno (me): {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Listado de documentos del expediente del alumno autenticado.
     */
    @GetMapping("/me/documentos")
    public ResponseEntity<?> listarMisDocumentos(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        Usuario usuario = (Usuario) authentication.getPrincipal();
        if (!usuario.tieneRol(Usuario.TipoUsuario.ALUMNO)) {
            return ResponseEntity.status(403).build();
        }
        return alumnoRepository.findByUsuarioId(usuario.getId())
                .map(a -> ResponseEntity.ok(armarListaMetadocumentosAlumno(a)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Actualizar etiqueta y número de cédula de un documento título/cédula propio (sin re-subir PDF).
     */
    @PatchMapping("/me/documentos/doc/{docId}")
    public ResponseEntity<?> parchearMiDocumentoTituloCedula(
            Authentication authentication,
            @PathVariable Long docId,
            @RequestBody Map<String, Object> body) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        Usuario usuario = (Usuario) authentication.getPrincipal();
        if (!usuario.tieneRol(Usuario.TipoUsuario.ALUMNO)) {
            return ResponseEntity.status(403).build();
        }
        Alumno alumno = alumnoRepository.findByUsuarioId(usuario.getId()).orElse(null);
        if (alumno == null) {
            return ResponseEntity.notFound().build();
        }
        var docOpt = documentoAlumnoExpedienteService.buscarPorIdEnAlumno(alumno.getId(), docId);
        if (docOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (docOpt.get().getTipoDocumento() != DocumentoAlumno.TipoDocumento.TITULO_CEDULA) {
            return ResponseEntity.badRequest().body(Map.of("error", "Solo aplica a documentos título/cédula."));
        }
        String etiqueta = body != null && body.get("etiquetaDocumento") != null
                ? String.valueOf(body.get("etiquetaDocumento")) : null;
        String numero = body != null && body.get("numeroCedula") != null
                ? String.valueOf(body.get("numeroCedula")) : null;
        documentoAlumnoExpedienteService.aplicarMetadatosTituloCedula(alumno.getId(), docId, etiqueta, numero);
        return ResponseEntity.ok(Map.of("mensaje", "Metadatos actualizados"));
    }

    /**
     * Ver/descargar un documento del propio expediente por id de fila (recomendado para título/cédula).
     */
    @GetMapping("/me/documentos/descarga")
    public ResponseEntity<?> descargarMiDocumentoPorId(Authentication authentication, @RequestParam Long docId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        Usuario usuario = (Usuario) authentication.getPrincipal();
        if (!usuario.tieneRol(Usuario.TipoUsuario.ALUMNO)) {
            return ResponseEntity.status(403).build();
        }
        Alumno alumno = alumnoRepository.findByUsuarioId(usuario.getId()).orElse(null);
        if (alumno == null) {
            return ResponseEntity.notFound().build();
        }
        return entregarDocumentoAlumnoPorDocId(alumno, docId);
    }

    /**
     * Ver/descargar un documento del propio expediente.
     */
    @GetMapping("/me/documentos/{tipo}/archivo")
    public ResponseEntity<?> descargarMiDocumento(Authentication authentication, @PathVariable String tipo) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        Usuario usuario = (Usuario) authentication.getPrincipal();
        if (!usuario.tieneRol(Usuario.TipoUsuario.ALUMNO)) {
            return ResponseEntity.status(403).build();
        }
        Alumno alumno = alumnoRepository.findByUsuarioId(usuario.getId()).orElse(null);
        if (alumno == null) {
            return ResponseEntity.notFound().build();
        }
        return entregarDocumentoAlumnoPorTipo(alumno, tipo);
    }

    /**
     * Obtener los bloques de horario del alumno autenticado.
     * Basado en los grupos en los que está inscrito y los horarios asignados a esos grupos.
     */
    @GetMapping("/me/horarios")
    public ResponseEntity<?> obtenerMisHorarios(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        Usuario usuario = (Usuario) authentication.getPrincipal();
        if (!usuario.tieneRol(Usuario.TipoUsuario.ALUMNO)) {
            return ResponseEntity.status(403).build();
        }
        return alumnoRepository.findByUsuarioId(usuario.getId())
                .map(alumno -> {
                    var grupos = grupoRepository.findByAlumnos_Id(alumno.getId());
                    var grupoIds = grupos.stream()
                            .map(Grupo::getId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList();
                    if (grupoIds.isEmpty()) {
                        return ResponseEntity.ok(List.<HorarioBloqueDTO>of());
                    }
                    var bloques = horarioBloqueRepository.findByGrupoEntity_IdInAndEstatusOrderByDiaAscHoraInicioAsc(
                            grupoIds, HorarioBloque.EstatusHorario.ACTIVO);
                    var filtrados = bloques.stream()
                            .filter(b -> grupos.stream().anyMatch(g -> {
                                if (b.getGrupoEntity() == null || !g.getId().equals(b.getGrupoEntity().getId())) return false;
                                if (g.getAsignatura() != null && b.getAsignatura() != null) {
                                    return g.getAsignatura().getId().equals(b.getAsignatura().getId());
                                }
                                if (g.getPrograma() != null && b.getPrograma() != null) {
                                    return g.getPrograma().getId().equals(b.getPrograma().getId());
                                }
                                return false;
                            }))
                            .map(HorarioBloqueDTO::from)
                            .toList();
                    return ResponseEntity.ok(filtrados);
                })
                .orElse(ResponseEntity.ok(List.of()));
    }

    /**
     * Obtener las calificaciones del alumno autenticado.
     */
    @GetMapping("/me/calificaciones")
    public ResponseEntity<?> obtenerMisCalificaciones(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        Usuario usuario = (Usuario) authentication.getPrincipal();
        if (!usuario.tieneRol(Usuario.TipoUsuario.ALUMNO)) {
            return ResponseEntity.status(403).build();
        }
        return alumnoRepository.findByUsuarioId(usuario.getId())
                .map(alumno -> {
                    // El alumno puede ver:
                    // - EN_REVISION: preliminar (capturada y enviada por el docente; aún no oficial)
                    // - CONFIRMADA: oficial
                    List<Calificacion> todas = calificacionRepository.findByAlumnoId(alumno.getId());
                    List<Calificacion> visibles = todas.stream()
                            .filter(c -> c.getEstadoAprobacion() == Calificacion.EstadoAprobacion.EN_REVISION
                                    || c.getEstadoAprobacion() == Calificacion.EstadoAprobacion.CONFIRMADA)
                            .collect(Collectors.toList());
                    List<Map<String, Object>> resp = visibles.stream().map(c -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", c.getId());
                        m.put("periodo", c.getPeriodoDisplay());
                        m.put("periodoAcademico", c.getPeriodoAcademico());
                        m.put("asignatura", c.getAsignatura());
                        m.put("grupo", c.getGrupo());
                        m.put("asistenciaPorcentaje", c.getAsistenciaPorcentaje());
                        m.put("tipoEvaluacion", c.getTipoEvaluacion());
                        m.put("estatus", c.getEstatus());
                        m.put("estadoAprobacion", c.getEstadoAprobacion());
                        m.put("idObservaciones", c.getIdObservaciones());
                        m.put("observaciones", c.getObservaciones());
                        m.put("calificacionFinal", c.getCalificacionFinal());
                        if (evaluacionDocenteService.debeOcultarCalificacionPorEvaluacion(alumno, c)) {
                            m.put("calificacionFinal", null);
                            m.put("bloqueadaPorEvaluacion", true);
                            m.put("mensajeBloqueo",
                                    "Responde la evaluación docente de este módulo/asignatura para ver tu calificación.");
                        }
                        if (c.getEstadoAprobacion() == Calificacion.EstadoAprobacion.EN_REVISION) {
                            m.put("esPreliminar", true);
                            m.put("mensajePreliminar", "Calificación preliminar: está en revisión por Secretaría Académica.");
                        }
                        return m;
                    }).toList();
                    return ResponseEntity.ok(resp);
                })
                .orElse(ResponseEntity.ok(List.of()));
    }

    /**
     * Buscar alumno por matrícula (ruta más específica antes que /{id})
     */
    @GetMapping("/matricula/{matricula}")
    @RequierePermiso("VER_ALUMNOS")
    public ResponseEntity<?> obtenerPorMatricula(@PathVariable String matricula) {
        Optional<Alumno> alumnoOpt = alumnoRepository.findByMatricula(matricula.trim());
        if (!alumnoOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(alumnoOpt.get());
    }

    /**
     * Buscar alumno por CURP
     */
    @GetMapping("/curp/{curp}")
    @RequierePermiso("VER_ALUMNOS")
    public ResponseEntity<?> obtenerPorCurp(@PathVariable String curp) {
        Optional<Alumno> alumnoOpt = alumnoRepository.findByCurp(curp.trim());
        if (!alumnoOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(alumnoOpt.get());
    }

    /**
     * Obtener un alumno por ID
     */
    @GetMapping("/{id}")
    @RequierePermiso("VER_ALUMNOS")
    public ResponseEntity<?> obtenerPorId(@PathVariable Long id) {
        Optional<Alumno> alumnoOpt = alumnoRepository.findByIdConProgramasInscripcion(id);
        if (!alumnoOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(alumnoOpt.get());
    }

    /**
     * Crear un nuevo alumno (JSON)
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequierePermiso("ACTUALIZAR_ALUMNOS")
    public ResponseEntity<Object> crear(@RequestBody Alumno alumno) {
        try {
            ResponseEntity<Object> validacion = validarMatricula(alumno.getMatricula());
            if (validacion != null) return validacion;
            ResponseEntity<Object> validacionCorreo = validarCorreoInstitucionalAlumno(alumno.getCorreoInstitucional(), null);
            if (validacionCorreo != null) return validacionCorreo;

            if (alumnoRepository.existsByMatricula(alumno.getMatricula())) {
                return ResponseEntity.badRequest().body(Map.<String, Object>of("error", "La matrícula ya existe"));
            }
            if (alumno.getCurp() != null && alumnoRepository.findByCurp(alumno.getCurp()).isPresent()) {
                return ResponseEntity.badRequest().body(Map.<String, Object>of("error", "El CURP ya está registrado"));
            }
            if (alumno.getSexo() == null) {
                return ResponseEntity.badRequest().body(Map.<String, Object>of("error", "El género/sexo es obligatorio para cada alumno."));
            }

            prepararInscripcionesPrograma(alumno);
            resolverYAsignarPeriodoIngreso(alumno, alumno.getPeriodoIngresoInput() != null ? alumno.getPeriodoIngresoInput() : alumno.getPeriodoIngreso());

            normalizarNombresAlumno(alumno);
            Usuario usuario = crearUsuarioAlumno(alumno.getCorreoInstitucional());
            alumno.setUsuario(usuario);
            asegurarPeriodoCursandoInicial(alumno);

            Alumno guardado = alumnoRepository.save(alumno);
            progresoAcademicoNivelService.sincronizarPeriodoAcademicoEscolarDesdeNivel(guardado.getId());
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
    @RequierePermiso("ACTUALIZAR_ALUMNOS")
    public ResponseEntity<Object> crearConArchivos(
            Authentication authentication,
            @RequestPart("alumno") String alumnoJson,
            @RequestPart(value = "foto", required = false) MultipartFile foto,
            @RequestPart(value = "documentos", required = false) List<MultipartFile> documentos,
            @RequestPart(value = "documentosTipos", required = false) List<String> documentosTipos) {
        try {
            Long operadorId = null;
            if (authentication != null && authentication.getPrincipal() instanceof Usuario uOp) {
                operadorId = uOp.getId();
            }
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

            ResponseEntity<Object> validacionCorreo = validarCorreoInstitucionalAlumno(alumno.getCorreoInstitucional(), null);
            if (validacionCorreo != null) return validacionCorreo;

            if (alumno.getSexo() == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "El género/sexo es obligatorio para cada alumno.");
                return ResponseEntity.badRequest().body(error);
            }

            prepararInscripcionesPrograma(alumno);
            resolverYAsignarPeriodoIngreso(alumno, alumno.getPeriodoIngresoInput() != null ? alumno.getPeriodoIngresoInput() : alumno.getPeriodoIngreso());

            normalizarNombresAlumno(alumno);
            Usuario usuario = crearUsuarioAlumno(alumno.getCorreoInstitucional());
            alumno.setUsuario(usuario);
            asegurarPeriodoCursandoInicial(alumno);

            Alumno guardado = alumnoRepository.save(alumno);

            try {
                procesarArchivosAlumno(guardado, foto, documentos, documentosTipos, false,
                        DocumentoAlumno.OrigenExpediente.REGISTRO_ALTA_ALUMNO, operadorId);
            } catch (Exception e) {
                log.warn("Advertencia al procesar archivos del alumno: {}", e.getMessage());
            }
            
            Alumno actualizado = alumnoRepository.save(guardado);
            progresoAcademicoNivelService.sincronizarPeriodoAcademicoEscolarDesdeNivel(actualizado.getId());

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
    @RequierePermiso("ACTUALIZAR_ALUMNOS")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody Alumno alumno) {
        try {
            Optional<Alumno> existenteOpt = alumnoRepository.findByIdConProgramasInscripcion(id);
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
            if (alumno.getSexo() == null) {
                return ResponseEntity.badRequest().body(Map.<String, Object>of("error", "El género/sexo es obligatorio para cada alumno."));
            }

            normalizarNombresAlumno(alumno);
            if (alumno.getCorreoInstitucional() != null && !alumno.getCorreoInstitucional().isBlank()) {
                ResponseEntity<Object> v = validarCorreoInstitucionalAlumno(alumno.getCorreoInstitucional(), id);
                if (v != null) return v;
                actualizarUsuarioAlumno(existente, alumno.getCorreoInstitucional());
            }

            existente.setMatricula(alumno.getMatricula());
            existente.setNombre(alumno.getNombre());
            existente.setApellidoPaterno(alumno.getApellidoPaterno());
            existente.setApellidoMaterno(alumno.getApellidoMaterno());
            existente.setCurp(alumno.getCurp());
            existente.setCorreoInstitucional(alumno.getCorreoInstitucional());
            existente.setCorreoPersonal(alumno.getCorreoPersonal());
            existente.setTelefono(alumno.getTelefono());
            existente.setEstado(alumno.getEstado());
            existente.setCodigoPostal(alumno.getCodigoPostal());
            existente.setSexo(alumno.getSexo());
            existente.setFechaNacimiento(alumno.getFechaNacimiento());
            existente.setNombreContactoEmergencia(alumno.getNombreContactoEmergencia());
            existente.setTelefonoContactoEmergencia(alumno.getTelefonoContactoEmergencia());
            boolean variosProgramas = alumno.getProgramasAsignados() != null && !alumno.getProgramasAsignados().isEmpty();
            if (variosProgramas) {
                sincronizarInscripcionesPrograma(existente, alumno);
            } else {
                existente.setPrograma(alumno.getPrograma());
            }
            prepararInscripcionesPrograma(existente);
            existente.setPeriodoAcademicoId(alumno.getPeriodoAcademicoId());
            resolverYAsignarPeriodoIngreso(existente, alumno.getPeriodoIngresoInput() != null ? alumno.getPeriodoIngresoInput() : alumno.getPeriodoIngreso());
            if (!variosProgramas) {
                existente.setPeriodoCursando(alumno.getPeriodoCursando());
                existente.setEstatusMatricula(alumno.getEstatusMatricula());
            }
            existente.setTurno(alumno.getTurno());
            existente.setObservaciones(alumno.getObservaciones());

            Alumno actualizado = alumnoRepository.save(existente);
            progresoAcademicoNivelService.sincronizarPeriodoAcademicoEscolarDesdeNivel(actualizado.getId());
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
    @RequierePermiso("ACTUALIZAR_ALUMNOS")
    public ResponseEntity<?> actualizarConArchivos(
            Authentication authentication,
            @PathVariable Long id,
            @RequestPart("alumno") String alumnoJson,
            @RequestPart(value = "foto", required = false) MultipartFile foto,
            @RequestPart(value = "documentos", required = false) List<MultipartFile> documentos,
            @RequestPart(value = "documentosTipos", required = false) List<String> documentosTipos) {
        try {
            Long operadorId = null;
            if (authentication != null && authentication.getPrincipal() instanceof Usuario uOp) {
                operadorId = uOp.getId();
            }
            Optional<Alumno> existenteOpt = alumnoRepository.findByIdConProgramasInscripcion(id);
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

                if (alumnoActualizado.getSexo() == null) {
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", "El género/sexo es obligatorio para cada alumno.");
                    return ResponseEntity.badRequest().body(error);
                }

                alumnoActualizado.setId(id);
                normalizarNombresAlumno(alumnoActualizado);

                existente.setMatricula(alumnoActualizado.getMatricula());
                existente.setNombre(alumnoActualizado.getNombre());
                existente.setApellidoPaterno(alumnoActualizado.getApellidoPaterno());
                existente.setApellidoMaterno(alumnoActualizado.getApellidoMaterno());
                existente.setCurp(alumnoActualizado.getCurp());
                if (alumnoActualizado.getCorreoInstitucional() != null && !alumnoActualizado.getCorreoInstitucional().isBlank()) {
                    actualizarUsuarioAlumno(existente, alumnoActualizado.getCorreoInstitucional());
                }
                existente.setCorreoInstitucional(alumnoActualizado.getCorreoInstitucional());
                existente.setCorreoPersonal(alumnoActualizado.getCorreoPersonal());
                existente.setTelefono(alumnoActualizado.getTelefono());
                existente.setEstado(alumnoActualizado.getEstado());
                existente.setCodigoPostal(alumnoActualizado.getCodigoPostal());
                existente.setSexo(alumnoActualizado.getSexo());
                if (alumnoActualizado.getFechaNacimiento() != null) {
                    existente.setFechaNacimiento(alumnoActualizado.getFechaNacimiento());
                }
                existente.setNombreContactoEmergencia(alumnoActualizado.getNombreContactoEmergencia());
                existente.setTelefonoContactoEmergencia(alumnoActualizado.getTelefonoContactoEmergencia());
                boolean variosProgramas = alumnoActualizado.getProgramasAsignados() != null && !alumnoActualizado.getProgramasAsignados().isEmpty();
                if (variosProgramas) {
                    sincronizarInscripcionesPrograma(existente, alumnoActualizado);
                } else {
                    existente.setPrograma(alumnoActualizado.getPrograma());
                }
                prepararInscripcionesPrograma(existente);
                existente.setPeriodoAcademicoId(alumnoActualizado.getPeriodoAcademicoId());
                resolverYAsignarPeriodoIngreso(existente, alumnoActualizado.getPeriodoIngresoInput() != null ? alumnoActualizado.getPeriodoIngresoInput() : alumnoActualizado.getPeriodoIngreso());
                if (!variosProgramas) {
                    existente.setPeriodoCursando(alumnoActualizado.getPeriodoCursando());
                    existente.setEstatusMatricula(alumnoActualizado.getEstatusMatricula());
                }
                existente.setTurno(alumnoActualizado.getTurno());
                existente.setObservaciones(alumnoActualizado.getObservaciones());

                try {
                    procesarArchivosAlumno(existente, foto, documentos, documentosTipos, false,
                            DocumentoAlumno.OrigenExpediente.CARGA_ADMINISTRATIVA, operadorId);
                } catch (Exception e) {
                    log.warn("Advertencia al procesar archivos del alumno: {}", e.getMessage());
                    // No lanzar error si falla el procesamiento de archivos, solo advertir
                }

                Alumno guardado = alumnoRepository.save(existente);
                progresoAcademicoNivelService.sincronizarPeriodoAcademicoEscolarDesdeNivel(guardado.getId());
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

    private void eliminarAlumnoYUsuario(Alumno alumno) {
        if (alumno == null || alumno.getId() == null) {
            return;
        }
        Long alumnoId = alumno.getId();

        // 1) Desinscribir al alumno de todos sus grupos (borra filas en tabla puente grupo_alumno)
        try {
            List<Grupo> grupos = grupoRepository.findByAlumnos_Id(alumnoId);
            if (grupos != null && !grupos.isEmpty()) {
                for (Grupo g : grupos) {
                    if (g == null || g.getAlumnos() == null) continue;
                    g.getAlumnos().removeIf(a -> a != null && a.getId() != null && a.getId().equals(alumnoId));
                }
                grupoRepository.saveAll(grupos);
            }
        } catch (Exception e) {
            // Si falla aquí, el delete del alumno fallará por FK; devolver el error más claro arriba
            throw e;
        }

        // 2) Borrar calificaciones explícitamente (evita conflictos por FK alumno_id NOT NULL)
        try {
            List<Calificacion> califs = calificacionRepository.findByAlumnoId(alumnoId);
            if (califs != null && !califs.isEmpty()) {
                calificacionRepository.deleteAll(califs);
            }
        } catch (Exception e) {
            throw e;
        }

        Usuario u = alumno.getUsuario();
        alumno.setUsuario(null);
        alumnoRepository.save(alumno);
        alumnoRepository.delete(alumno);
        if (u != null) {
            usuarioRepository.delete(u);
        }
    }

    /**
     * Convierte un valor JSON (Integer, Long, Double, String) a Long para ids.
     * Jackson suele deserializar números en JSON como Integer, no como Long.
     */
    private static Long toLongId(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Long) {
            return (Long) o;
        }
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        if (o instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) {
                return null;
            }
            try {
                return Long.parseLong(t);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Normaliza la lista {@code ids} del cuerpo JSON a {@link Long} sin ClassCastException.
     */
    @SuppressWarnings("unchecked")
    private static LinkedHashSet<Long> extraerIdsUnicosDesdeBody(Map<String, Object> body) {
        LinkedHashSet<Long> unicos = new LinkedHashSet<>();
        if (body == null) {
            return unicos;
        }
        Object raw = body.get("ids");
        if (!(raw instanceof List)) {
            return unicos;
        }
        for (Object o : (List<Object>) raw) {
            Long id = toLongId(o);
            if (id != null) {
                unicos.add(id);
            }
        }
        return unicos;
    }

    /**
     * Eliminar varios alumnos en una sola operación.
     * Cada id se valida por separado (no encontrado o con títulos electrónicos → se reporta en {@code errores}).
     */
    @PostMapping("/eliminar-lote")
    @RequierePermiso("ACTUALIZAR_ALUMNOS")
    @Transactional
    public ResponseEntity<?> eliminarLote(@RequestBody Map<String, Object> body) {
        LinkedHashSet<Long> unicos = extraerIdsUnicosDesdeBody(body);
        if (unicos.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Envía ids: lista numérica de alumnos a eliminar."));
        }

        int eliminados = 0;
        List<Map<String, Object>> errores = new ArrayList<>();

        for (Long id : unicos) {
            try {
                Optional<Alumno> alumnoOpt = alumnoRepository.findById(id);
                if (alumnoOpt.isEmpty()) {
                    errores.add(Map.of("id", id, "mensaje", "Alumno no encontrado"));
                    continue;
                }
                if (tituloRepository.existsByAlumnoId(id)) {
                    errores.add(Map.of("id", id, "mensaje", "Tiene títulos electrónicos asociados; elimine los títulos primero."));
                    continue;
                }
                Alumno alumno = alumnoOpt.get();
                eliminarAlumnoYUsuario(alumno);
                eliminados++;
                log.info("Alumno eliminado (lote): {} - {}", alumno.getMatricula(), alumno.getNombreCompleto());
            } catch (Exception e) {
                log.warn("No se pudo eliminar alumno id {}: {}", id, e.getMessage());
                errores.add(Map.of("id", id, "mensaje", e.getMessage() != null ? e.getMessage() : "Error al eliminar"));
            }
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("eliminados", eliminados);
        resp.put("errores", errores);
        return ResponseEntity.ok(resp);
    }

    /**
     * Eliminar un alumno
     */
    @DeleteMapping("/{id}")
    @RequierePermiso("ACTUALIZAR_ALUMNOS")
    @Transactional
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        try {
            Optional<Alumno> alumnoOpt = alumnoRepository.findById(id);
            if (alumnoOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            // Evitar eliminación si el alumno tiene títulos electrónicos asociados
            if (tituloRepository.existsByAlumnoId(id)) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "No es posible eliminar el alumno: existen títulos electrónicos asociados. Elimine los títulos primero.");
                return ResponseEntity.badRequest().body(error);
            }

            Alumno alumno = alumnoOpt.get();
            eliminarAlumnoYUsuario(alumno);
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

    /**
     * Lista metadatos de documentos del expediente del alumno (sin exponer datos binarios).
     * El propio alumno o personal con acceso a expedientes.
     */
    @GetMapping("/{id}/documentos")
    public ResponseEntity<?> listarDocumentosAlumno(Authentication authentication, @PathVariable Long id) {
        Optional<Alumno> alumnoOpt = alumnoRepository.findById(id);
        if (alumnoOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Alumno alumno = alumnoOpt.get();
        if (!puedeAccederExpedienteAlumno(authentication, alumno)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No autorizado"));
        }
        return ResponseEntity.ok(armarListaMetadocumentosAlumno(alumno));
    }

    /**
     * Descarga/visualiza un documento del alumno por tipo.
     */
    @GetMapping("/{id}/documentos/descarga")
    public ResponseEntity<?> descargarDocumentoAlumnoPorId(
            Authentication authentication, @PathVariable Long id, @RequestParam Long docId) {
        Optional<Alumno> alumnoOpt = alumnoRepository.findById(id);
        if (alumnoOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Alumno alumno = alumnoOpt.get();
        if (!puedeAccederExpedienteAlumno(authentication, alumno)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No autorizado"));
        }
        return entregarDocumentoAlumnoPorDocId(alumno, docId);
    }

    @GetMapping("/{id}/documentos/{tipo}/archivo")
    public ResponseEntity<?> descargarDocumentoAlumno(Authentication authentication, @PathVariable Long id, @PathVariable String tipo) {
        Optional<Alumno> alumnoOpt = alumnoRepository.findById(id);
        if (alumnoOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Alumno alumno = alumnoOpt.get();
        if (!puedeAccederExpedienteAlumno(authentication, alumno)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No autorizado"));
        }
        return entregarDocumentoAlumnoPorTipo(alumno, tipo);
    }

    @PatchMapping("/{id}/documentos/doc/{docId}")
    @RequierePermiso("ACTUALIZAR_ALUMNOS")
    public ResponseEntity<?> parchearDocumentoTituloCedulaAdmin(
            Authentication authentication,
            @PathVariable Long id,
            @PathVariable Long docId,
            @RequestBody Map<String, Object> body) {
        Optional<Alumno> alumnoOpt = alumnoRepository.findById(id);
        if (alumnoOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!puedeGestionarExpedienteComoPersonal(authentication, alumnoOpt.get())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No autorizado"));
        }
        Alumno alumno = alumnoOpt.get();
        var docOpt = documentoAlumnoExpedienteService.buscarPorIdEnAlumno(alumno.getId(), docId);
        if (docOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (docOpt.get().getTipoDocumento() != DocumentoAlumno.TipoDocumento.TITULO_CEDULA) {
            return ResponseEntity.badRequest().body(Map.of("error", "Solo aplica a documentos título/cédula."));
        }
        String etiqueta = body != null && body.get("etiquetaDocumento") != null
                ? String.valueOf(body.get("etiquetaDocumento")) : null;
        String numero = body != null && body.get("numeroCedula") != null
                ? String.valueOf(body.get("numeroCedula")) : null;
        documentoAlumnoExpedienteService.aplicarMetadatosTituloCedula(alumno.getId(), docId, etiqueta, numero);
        return ResponseEntity.ok(Map.of("mensaje", "Metadatos actualizados"));
    }

    /**
     * Elimina un documento del expediente. Solo personal con permiso; el alumno no puede borrar
     * (al retirarlo aquí, el alumno vuelve a poder subir otro).
     */
    @DeleteMapping("/{id}/documentos/doc/{docId}")
    @RequierePermiso("ACTUALIZAR_ALUMNOS")
    @Transactional
    public ResponseEntity<?> eliminarDocumentoAlumnoPorDocId(
            Authentication authentication, @PathVariable Long id, @PathVariable Long docId) {
        Optional<Alumno> alumnoOpt = alumnoRepository.findById(id);
        if (alumnoOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!puedeGestionarExpedienteComoPersonal(authentication, alumnoOpt.get())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No autorizado"));
        }
        Alumno alumno = alumnoOpt.get();
        if (documentoAlumnoExpedienteService.buscarPorIdEnAlumno(alumno.getId(), docId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        documentoAlumnoExpedienteService.limpiarArchivoPorDocId(alumno.getId(), docId);
        return ResponseEntity.ok(Map.of("mensaje", "Documento eliminado"));
    }

    @DeleteMapping("/{id}/documentos/{tipo}")
    @RequierePermiso("ACTUALIZAR_ALUMNOS")
    @Transactional
    public ResponseEntity<?> eliminarDocumentoAlumno(Authentication authentication, @PathVariable Long id, @PathVariable String tipo) {
        Optional<Alumno> alumnoOpt = alumnoRepository.findById(id);
        if (alumnoOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!puedeGestionarExpedienteComoPersonal(authentication, alumnoOpt.get())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No autorizado"));
        }
        Alumno alumno = alumnoOpt.get();
        DocumentoAlumno.TipoDocumento t;
        try {
            t = DocumentoAlumno.TipoDocumento.valueOf(tipo.toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tipo de documento inválido"));
        }
        if (t == DocumentoAlumno.TipoDocumento.TITULO_CEDULA) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Para título/cédula use DELETE /alumnos/{id}/documentos/doc/{docId} con el id de la fila."));
        }
        if (documentoAlumnoExpedienteService.buscar(alumno.getId(), t).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        documentoAlumnoExpedienteService.limpiarArchivo(alumno.getId(), t);
        return ResponseEntity.ok(Map.of("mensaje", "Documento eliminado"));
    }

    private List<AlumnoDocumentoMeta> armarListaMetadocumentosAlumno(Alumno alumno) {
        return documentoAlumnoExpedienteService.listarMetadatos(alumno.getId());
    }

    private ResponseEntity<?> entregarDocumentoAlumnoPorDocId(Alumno alumno, Long docId) {
        if (docId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "docId requerido"));
        }
        DocumentoAlumno doc = documentoAlumnoExpedienteService.buscarPorIdEnAlumno(alumno.getId(), docId).orElse(null);
        if (doc == null || doc.getArchivoUrl() == null || doc.getArchivoUrl().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        return entregarDocumentoAlumnoRecurso(doc);
    }

    private ResponseEntity<?> entregarDocumentoAlumnoPorTipo(Alumno alumno, String tipo) {
        DocumentoAlumno.TipoDocumento t;
        try {
            t = DocumentoAlumno.TipoDocumento.valueOf(tipo.toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tipo de documento inválido"));
        }
        if (t == DocumentoAlumno.TipoDocumento.TITULO_CEDULA) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Use GET .../documentos/descarga?docId= con el id devuelto en el listado de documentos."));
        }
        DocumentoAlumno doc = documentoAlumnoExpedienteService.buscar(alumno.getId(), t).orElse(null);
        if (doc == null || doc.getArchivoUrl() == null || doc.getArchivoUrl().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        return entregarDocumentoAlumnoRecurso(doc);
    }

    private ResponseEntity<?> entregarDocumentoAlumnoRecurso(DocumentoAlumno doc) {
        DocumentoAlumno.TipoDocumento t = doc.getTipoDocumento();
        try {
            Path path = Paths.get(doc.getArchivoUrl());
            if (!Files.exists(path)) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new UrlResource(path.toUri());
            String contentType = Files.probeContentType(path);
            String fn = extraerFilename(doc.getArchivoUrl());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + (fn != null ? fn.replace("\"", "") : (t.name().toLowerCase() + ".bin")) + "\"")
                    .contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (Exception e) {
            log.error("Error al descargar documento del alumno: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Alumno: solo su registro. Personal: lectura/escritura según permisos; coordinador restringido por programa.
     */
    private boolean puedeAccederExpedienteAlumno(Authentication authentication, Alumno alumno) {
        if (authentication == null || !authentication.isAuthenticated() || alumno == null) {
            return false;
        }
        if (!(authentication.getPrincipal() instanceof Usuario u)) {
            return false;
        }
        if (u.getTipoUsuario() == Usuario.TipoUsuario.ALUMNO) {
            return alumnoRepository.findByUsuarioId(u.getId())
                    .map(a -> a.getId().equals(alumno.getId()))
                    .orElse(false);
        }
        if (!permisosValidator.tieneAlgunoPermiso(u, "VER_ALUMNOS", "ACTUALIZAR_ALUMNOS")) {
            return false;
        }
        if (programaAccesoService.esCoordinadorAcademico(u)) {
            Long pid = alumno.getPrograma() != null ? alumno.getPrograma().getId() : null;
            return programaAccesoService.puedeAccederPrograma(u, pid);
        }
        return true;
    }

    /** Solo personal con ACTUALIZAR (no el alumno). */
    private boolean puedeGestionarExpedienteComoPersonal(Authentication authentication, Alumno alumno) {
        if (authentication == null || !authentication.isAuthenticated() || alumno == null) {
            return false;
        }
        if (!(authentication.getPrincipal() instanceof Usuario u)) {
            return false;
        }
        if (u.getTipoUsuario() == Usuario.TipoUsuario.ALUMNO) {
            return false;
        }
        if (!permisosValidator.tienePermiso(u, "ACTUALIZAR_ALUMNOS")) {
            return false;
        }
        if (programaAccesoService.esCoordinadorAcademico(u)) {
            Long pid = alumno.getPrograma() != null ? alumno.getPrograma().getId() : null;
            return programaAccesoService.puedeAccederPrograma(u, pid);
        }
        return true;
    }

    private static String extraerFilename(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) return null;
        String s = pathOrUrl.trim();
        int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        String fn = slash >= 0 ? s.substring(slash + 1) : s;
        return fn.isBlank() ? null : fn;
    }

    private static String headerText(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.contains("%")) {
            try {
                value = URLDecoder.decode(value, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ignored) {
                // Si el header no venía URL-encoded válido, usarlo tal cual.
            }
        }
        return value.trim();
    }

    private static String aMayusculas(String s) {
        return (s != null && !s.isBlank()) ? s.trim().toUpperCase(Locale.forLanguageTag("es")) : s;
    }

    private void normalizarNombresAlumno(Alumno alumno) {
        if (alumno != null) {
            alumno.setNombre(aMayusculas(alumno.getNombre()));
            alumno.setApellidoPaterno(aMayusculas(alumno.getApellidoPaterno()));
            alumno.setApellidoMaterno(aMayusculas(alumno.getApellidoMaterno()));
            if (alumno.getCurp() != null && !alumno.getCurp().isBlank()) {
                alumno.setCurp(alumno.getCurp().trim().toUpperCase(Locale.forLanguageTag("es")));
            }
        }
    }

    private static void marcarTrazabilidadExpediente(DocumentoAlumno documento,
                                                     DocumentoAlumno.OrigenExpediente origen,
                                                     Long cargadoPorUsuarioId) {
        if (documento == null || origen == null) {
            return;
        }
        documento.setOrigenUltimaCarga(origen);
        documento.setCargadoPorUsuarioId(cargadoPorUsuarioId);
    }

    private void procesarArchivosAlumno(Alumno alumno,
                                        MultipartFile foto,
                                        List<MultipartFile> documentos,
                                        List<String> documentosTipos,
                                        boolean bloqueaReemplazoExpedienteEnPortal,
                                        DocumentoAlumno.OrigenExpediente origenDocumentos,
                                        Long cargadoPorUsuarioId) throws Exception {
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
            DocumentoAlumno.TipoDocumento tipoRes = tipoDocumento;
            int slotTitulo = 0;
            if (tipoDocumento == DocumentoAlumno.TipoDocumento.TITULO_PROFESIONAL) {
                tipoRes = DocumentoAlumno.TipoDocumento.TITULO_CEDULA;
                slotTitulo = 1;
            } else if (tipoDocumento == DocumentoAlumno.TipoDocumento.CEDULA_PROFESIONAL) {
                tipoRes = DocumentoAlumno.TipoDocumento.TITULO_CEDULA;
                slotTitulo = 2;
            } else if (tipoDocumento == DocumentoAlumno.TipoDocumento.TITULO_CEDULA) {
                slotTitulo = 1;
            }
            // Portal del alumno: no sustituir ningún documento ya entregado (aunque lo haya subido o personal); solo personal con expediente quita y vuelve a habilitar la carga
            if (bloqueaReemplazoExpedienteEnPortal) {
                if (tipoDocumento == DocumentoAlumno.TipoDocumento.OTRO) {
                    throw new IllegalStateException("Tipo de documento no permitido desde el portal.");
                }
                if (!PORTAL_DOCUMENTOS_PERMITIDOS.contains(tipoRes)) {
                    throw new IllegalStateException("Tipo de documento no permitido desde el portal.");
                }
                String fn = archivo.getOriginalFilename() != null ? archivo.getOriginalFilename().trim() : "";
                String ct = archivo.getContentType() != null ? archivo.getContentType().trim() : "";
                boolean extPdf = fn.toLowerCase(Locale.ROOT).endsWith(".pdf");
                boolean ctPdf = ct.equalsIgnoreCase("application/pdf");
                if (!extPdf && !ctPdf) {
                    throw new IllegalStateException("Solo se permite subir documentos en formato PDF.");
                }
                DocumentoAlumno d0;
                if (tipoRes == DocumentoAlumno.TipoDocumento.TITULO_CEDULA) {
                    d0 = documentoAlumnoExpedienteService.buscarTituloCedulaSlot(alumno.getId(), slotTitulo).orElse(null);
                } else {
                    d0 = documentoAlumnoExpedienteService.buscar(alumno.getId(), tipoRes).orElse(null);
                }
                if (d0 != null
                        && Boolean.TRUE.equals(d0.getEntregado())
                        && d0.getArchivoUrl() != null
                        && !d0.getArchivoUrl().isBlank()) {
                    throw new IllegalStateException("Este documento ya consta en el expediente. Un administrador o coordinador puede retirarlo para que puedas subir otro.");
                }
            }

            String prefijoStorage;
            DocumentoAlumno documento;
            if (tipoRes == DocumentoAlumno.TipoDocumento.TITULO_CEDULA) {
                documento = documentoAlumnoExpedienteService.obtenerOCrearTituloCedulaEnSlot(alumno, slotTitulo);
                prefijoStorage = "titulo_cedula_s" + slotTitulo;
            } else {
                documento = documentoAlumnoExpedienteService.obtenerOCrearYVincular(alumno, tipoRes);
                prefijoStorage = tipoRes.name().toLowerCase(Locale.ROOT);
            }
            String archivoUrl = fileStorageService.storeAlumnoFile(alumno.getId(), archivo, prefijoStorage);
            documento.setArchivoUrl(archivoUrl);
            documento.setEntregado(true);
            documento.setFechaRecepcion(LocalDate.now());
            marcarTrazabilidadExpediente(documento, origenDocumentos, cargadoPorUsuarioId);
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
        if (matricula == null || matricula.isBlank()) {
            return ResponseEntity.badRequest().body(Map.<String, Object>of("error", "La matrícula es requerida"));
        }
        if (matricula.length() < 5 || matricula.length() > 30 || !matricula.matches("^[A-Za-z0-9_-]+$")) {
            return ResponseEntity.badRequest().body(Map.<String, Object>of("error", "La matrícula debe tener entre 5 y 30 caracteres alfanuméricos"));
        }
        return null;
    }

    private ResponseEntity<Object> validarCorreoInstitucionalAlumno(String correo, Long alumnoIdExcluir) {
        if (correo == null || correo.isBlank()) {
            return ResponseEntity.badRequest().body(Map.<String, Object>of("error", "El correo institucional es requerido. Será el acceso al sistema del alumno (contraseña por defecto: idee1234)"));
        }
        if (usuarioRepository.existsByEmail(correo.trim())) {
            if (alumnoIdExcluir != null) {
                var alumnoOpt = alumnoRepository.findById(alumnoIdExcluir);
                if (alumnoOpt.isPresent() && alumnoOpt.get().getUsuario() != null
                        && correo.trim().equalsIgnoreCase(alumnoOpt.get().getUsuario().getEmail())) {
                    return null; // mismo alumno, mismo correo
                }
            }
            return ResponseEntity.badRequest().body(Map.<String, Object>of("error", "El correo institucional ya está registrado como acceso de otro usuario"));
        }
        return null;
    }

    private Usuario crearUsuarioAlumno(String correoInstitucional) {
        if (correoInstitucional == null || correoInstitucional.isBlank()) {
            throw new IllegalArgumentException("Correo institucional requerido para crear acceso del alumno");
        }
        Usuario u = new Usuario();
        u.setEmail(correoInstitucional.trim().toLowerCase());
        u.setPassword(passwordEncoder.encode(PASSWORD_ALUMNO));
        u.setTipoUsuario(Usuario.TipoUsuario.ALUMNO);
        u.setActivo(true);
        return usuarioRepository.save(u);
    }

    private void actualizarUsuarioAlumno(Alumno existente, String nuevoCorreo) {
        String correo = nuevoCorreo != null ? nuevoCorreo.trim().toLowerCase() : "";
        if (correo.isBlank()) return;
        Usuario u = existente.getUsuario();
        if (u == null) {
            if (usuarioRepository.existsByEmail(correo)) return;
            u = crearUsuarioAlumno(correo);
            existente.setUsuario(u);
        } else if (!u.getEmail().equalsIgnoreCase(correo)) {
            if (usuarioRepository.existsByEmail(correo)) return;
            u.setEmail(correo);
            usuarioRepository.save(u);
        }
    }
}
