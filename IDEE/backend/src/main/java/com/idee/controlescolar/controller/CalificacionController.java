package com.idee.controlescolar.controller;

import com.idee.controlescolar.dto.CalificacionRequest;
import com.idee.controlescolar.model.Alumno;
import com.idee.controlescolar.model.Asignatura;
import com.idee.controlescolar.model.Calificacion;
import com.idee.controlescolar.model.CalificacionCriterioItem;
import com.idee.controlescolar.model.Grupo;
import com.idee.controlescolar.model.HorarioBloque;
import com.idee.controlescolar.model.Maestro;
import com.idee.controlescolar.model.ObservacionCalificacion;
import com.idee.controlescolar.model.PeriodoAcademico;
import com.idee.controlescolar.model.ProgramaEducativo;
import com.idee.controlescolar.model.CriterioEvaluacion;
import com.idee.controlescolar.repository.AlumnoRepository;
import com.idee.controlescolar.repository.AsignaturaRepository;
import com.idee.controlescolar.repository.CalificacionRepository;
import com.idee.controlescolar.repository.CalificacionCriterioItemRepository;
import com.idee.controlescolar.repository.CriterioEvaluacionRepository;
import com.idee.controlescolar.repository.GrupoRepository;
import com.idee.controlescolar.repository.HorarioBloqueRepository;
import com.idee.controlescolar.repository.MaestroRepository;
import com.idee.controlescolar.repository.ProgramaEducativoRepository;
import com.idee.controlescolar.repository.UsuarioRepository;
import com.idee.controlescolar.service.PeriodoAcademicoService;
import com.idee.controlescolar.service.ProgresoAcademicoNivelService;
import com.idee.controlescolar.service.GestionAcademicaEstadoService;
import com.idee.controlescolar.security.PermisosValidator;
import com.idee.controlescolar.security.RequierePermiso;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import com.idee.controlescolar.model.Usuario;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Calificaciones: maestros registran, secretaría académica verifica, modifica y confirma.
 * Solo SECRETARIA_ACADEMICA puede modificar y confirmar. El estatus APROBADO/REPROBADO
 * se define al confirmar.
 */
@RestController
@RequestMapping("/calificaciones")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class CalificacionController {

    private final CalificacionRepository calificacionRepository;
    private final AlumnoRepository alumnoRepository;
    private final AsignaturaRepository asignaturaRepository;
    private final GrupoRepository grupoRepository;
    private final MaestroRepository maestroRepository;
    private final HorarioBloqueRepository horarioBloqueRepository;
    private final ProgramaEducativoRepository programaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PeriodoAcademicoService periodoAcademicoService;
    private final ProgresoAcademicoNivelService progresoAcademicoNivelService;
    private final GestionAcademicaEstadoService gestionAcademicaEstadoService;
    private final PermisosValidator permisosValidator;
    private final CalificacionCriterioItemRepository calificacionCriterioItemRepository;
    private final CriterioEvaluacionRepository criterioEvaluacionRepository;
    private final PlatformTransactionManager transactionManager;

    private static Double round2(Double v) {
        if (v == null) return null;
        return Math.round(v * 100.0) / 100.0;
    }

    /**
     * Determina si el usuario está operando como docente.
     * Nota: en este sistema puede existir ficha de Maestro vinculada aunque el enum principal no sea MAESTRO.
     */
    private boolean esDocente(Usuario u) {
        if (u == null) return false;
        try {
            if (u.getTipoUsuario() == Usuario.TipoUsuario.MAESTRO) return true;
            if (u.tieneRol(Usuario.TipoUsuario.MAESTRO)) return true;
        } catch (Exception ignored) {}
        try {
            return maestroRepository.findByUsuarioId(u.getId()).isPresent();
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Admin / secretarías / coordinador pueden ver y operar calificaciones de cualquier clase del alcance del sistema,
     * aunque tengan también ficha de {@link Maestro} vinculada (si no, {@link #esDocente} los trataría solo como docente).
     */
    private boolean puedeOperarCalificacionesComoAdministrativo(Usuario u) {
        if (u == null) return false;
        try {
            Usuario.TipoUsuario t = u.getTipoUsuario();
            if (t == Usuario.TipoUsuario.ADMIN
                    || t == Usuario.TipoUsuario.SECRETARIA_ACADEMICA
                    || t == Usuario.TipoUsuario.SECRETARIA_ADMINISTRATIVA
                    || t == Usuario.TipoUsuario.COORDINADOR_ACADEMICO) {
                return true;
            }
            return u.tieneRol(Usuario.TipoUsuario.ADMIN)
                    || u.tieneRol(Usuario.TipoUsuario.SECRETARIA_ACADEMICA)
                    || u.tieneRol(Usuario.TipoUsuario.SECRETARIA_ADMINISTRATIVA)
                    || u.tieneRol(Usuario.TipoUsuario.COORDINADOR_ACADEMICO);
        } catch (Exception e) {
            return false;
        }
    }

    private PeriodoAcademico resolverPeriodoOperacionCalificacion(Grupo grupo, Calificacion calificacion) {
        if (grupo != null && grupo.getPeriodoAcademico() != null) {
            return grupo.getPeriodoAcademico();
        }
        if (calificacion != null && calificacion.getGrupo() != null && calificacion.getGrupo().getPeriodoAcademico() != null) {
            return calificacion.getGrupo().getPeriodoAcademico();
        }
        return calificacion != null ? calificacion.getPeriodoAcademico() : null;
    }

    /** Total puntos 0..100 -> calificación final 0.00..10.00 */
    private static Double calificacionFinalDesdePuntos(double totalPuntos) {
        double t = Math.max(0.0, Math.min(100.0, totalPuntos));
        double final10 = t / 10.0;
        return round2(final10);
    }

    /**
     * Docente sin criterios (o criterios que no suman 100%): la captura es un solo valor.
     * Si el valor es &gt; 10 se interpreta como porcentaje 0–100 y se convierte a escala 0–10;
     * si es ≤ 10 se interpreta ya en escala de 0 a 10 (compatibilidad).
     */
    private static Double normalizarCalificacionDocenteSinCriterios(Double raw) {
        if (raw == null) {
            return null;
        }
        double v = raw;
        if (v > 10.0) {
            double pct = Math.max(0.0, Math.min(100.0, v));
            return round2(pct / 10.0);
        }
        return round2(Math.max(0.0, Math.min(10.0, v)));
    }

    private static String validarCalificacionDirectaAdministrativa(Double value) {
        if (value == null) {
            return null;
        }
        return (value < 5.0 || value > 10.0)
                ? "La calificación final debe quedar entre 5.00 y 10.00."
                : null;
    }

    private List<CriterioEvaluacion> criteriosOperables(Calificacion c, CalificacionRequest req, Usuario u) {
        Long gid = req != null && req.getGrupoId() != null
                ? req.getGrupoId()
                : (c != null && c.getGrupo() != null ? c.getGrupo().getId() : null);
        Long aid = req != null && req.getAsignaturaId() != null
                ? req.getAsignaturaId()
                : (c != null && c.getAsignatura() != null ? c.getAsignatura().getId() : null);
        if (gid == null || aid == null) {
            return List.of();
        }
        if (u != null && esDocente(u) && !puedeOperarCalificacionesComoAdministrativo(u)) {
            Maestro maestro = maestroRepository.findByUsuarioId(u.getId()).orElse(null);
            if (maestro == null) {
                return List.of();
            }
            return criterioEvaluacionRepository.findByMaestro_IdAndAsignatura_IdAndGrupo_Id(
                    maestro.getId(), aid, gid);
        }
        return criterioEvaluacionRepository.findByGrupo_IdAndAsignatura_Id(gid, aid);
    }

    private boolean maestroSinCriteriosEvaluacionValidos(Usuario u, Long grupoId, Long asignaturaId) {
        if (u == null || grupoId == null || asignaturaId == null) {
            return false;
        }
        if (!esDocente(u) || puedeOperarCalificacionesComoAdministrativo(u)) {
            return false;
        }
        Maestro maestro = maestroRepository.findByUsuarioId(u.getId()).orElse(null);
        if (maestro == null) {
            return false;
        }
        List<CriterioEvaluacion> criterios = criterioEvaluacionRepository.findByMaestro_IdAndAsignatura_IdAndGrupo_Id(
                maestro.getId(), asignaturaId, grupoId);
        if (criterios == null || criterios.isEmpty()) {
            return true;
        }
        int sumaMax = criterios.stream()
                .mapToInt(ce -> ce != null && ce.getPorcentaje() != null ? ce.getPorcentaje() : 0)
                .sum();
        return sumaMax != 100;
    }

    private void limpiarDesgloseCriteriosCalificacion(Calificacion c) {
        if (c == null) {
            return;
        }
        if (c.getId() != null) {
            calificacionCriterioItemRepository.deleteByCalificacion_Id(c.getId());
            try {
                calificacionCriterioItemRepository.flush();
            } catch (Exception ignored) {
            }
        }
        if (c.getCriterios() != null) {
            c.getCriterios().clear();
        }
    }

    /**
     * Si la clase tiene criterios que suman 100%, calcula la calificación final desde el desglose.
     * Docente usa sus propios criterios; administrativos usan los criterios de la clase.
     * Si no hay criterios válidos, retorna null y se usa captura simple.
     */
    private Double aplicarCriteriosSiExisten(Calificacion c, CalificacionRequest req, Usuario u) {
        if (req == null) {
            return null;
        }
        List<CriterioEvaluacion> criterios = criteriosOperables(c, req, u);
        if (criterios == null || criterios.isEmpty()) {
            return null;
        }

        // Map criterioId -> criterio
        java.util.Map<Long, CriterioEvaluacion> byId = new java.util.HashMap<>();
        int sumaMax = 0;
        for (CriterioEvaluacion ce : criterios) {
            if (ce != null && ce.getId() != null) {
                byId.put(ce.getId(), ce);
                if (ce.getPorcentaje() != null) sumaMax += ce.getPorcentaje();
            }
        }
        if (sumaMax != 100) {
            return null;
        }

        if (req.getCriterios() == null || req.getCriterios().isEmpty()) {
            throw new IllegalArgumentException(
                    "Esta clase tiene criterios de evaluación (deben sumar 100%). Captura los puntos de cada criterio antes de guardar.");
        }

        // Validar y sumar puntos
        double total = 0.0;
        java.util.Set<Long> vistos = new java.util.HashSet<>();
        for (CalificacionRequest.CriterioPunto cp : req.getCriterios()) {
            if (cp == null || cp.getCriterioId() == null) continue;
            CriterioEvaluacion ce = byId.get(cp.getCriterioId());
            if (ce == null) {
                throw new IllegalArgumentException("Criterio inválido para esta clase: " + cp.getCriterioId());
            }
            if (vistos.contains(cp.getCriterioId())) {
                throw new IllegalArgumentException("Criterio duplicado: " + cp.getCriterioId());
            }
            vistos.add(cp.getCriterioId());
            Double puntos = cp.getPuntos();
            if (puntos == null) {
                throw new IllegalArgumentException("Faltan puntos para el criterio: " + ce.getNombre());
            }
            if (puntos < 0) {
                throw new IllegalArgumentException("Los puntos no pueden ser negativos (" + ce.getNombre() + ").");
            }
            int max = ce.getPorcentaje() != null ? ce.getPorcentaje() : 0;
            if (puntos > max) {
                throw new IllegalArgumentException("Los puntos de \"" + ce.getNombre() + "\" no pueden exceder " + max + ".");
            }
            total += puntos;
        }
        // Requerir que estén todos los criterios
        if (vistos.size() != byId.size()) {
            throw new IllegalArgumentException("Debes capturar todos los criterios antes de guardar.");
        }

        // Persistir desglose (reemplazar)
        if (c.getId() != null) {
            // Al ser un bulk delete, forzamos flush para que la BD aplique el borrado
            // antes de insertar el nuevo desglose (evita violación uq_calif_crit_item).
            calificacionCriterioItemRepository.deleteByCalificacion_Id(c.getId());
            try {
                calificacionCriterioItemRepository.flush();
            } catch (Exception ignored) {}
        }
        c.getCriterios().clear();
        for (CalificacionRequest.CriterioPunto cp : req.getCriterios()) {
            if (cp == null || cp.getCriterioId() == null) continue;
            CalificacionCriterioItem item = new CalificacionCriterioItem();
            item.setCalificacion(c);
            item.setCriterio(byId.get(cp.getCriterioId()));
            item.setPuntos(cp.getPuntos());
            c.getCriterios().add(item);
        }

        // total (0..100) -> final (0..10)
        return calificacionFinalDesdePuntos(total);
    }

    /**
     * El maestro solo puede operar calificaciones de grupos/asignaturas donde imparte clase
     * (titular del grupo o bloque de horario activo grupo+asignatura).
     */
    private boolean maestroTieneClaseEn(Usuario usuario, Long grupoId, Long asignaturaId) {
        // Solo restringir cuando el usuario opera como docente.
        // Roles administrativos pueden operar calificaciones fuera del portal docente.
        if (usuario == null || !esDocente(usuario)) {
            return true;
        }
        if (grupoId == null || asignaturaId == null) {
            return false;
        }
        Optional<Maestro> optM = maestroRepository.findByUsuarioId(usuario.getId());
        if (!optM.isPresent()) {
            return false;
        }
        Long maestroId = optM.get().getId();
        Optional<Grupo> og = grupoRepository.findById(grupoId);
        if (!og.isPresent()) {
            return false;
        }
        Grupo g = og.get();
        if (g.getMaestro() != null && g.getMaestro().getId().equals(maestroId)) {
            return true;
        }
        List<HorarioBloque> bloques = horarioBloqueRepository.findByMaestro_IdAndEstatusOrderByDiaAscHoraInicioAsc(
                maestroId, HorarioBloque.EstatusHorario.ACTIVO);
        return bloques.stream().anyMatch(b ->
                b.getGrupoEntity() != null && b.getGrupoEntity().getId().equals(grupoId)
                        && b.getAsignatura() != null && b.getAsignatura().getId().equals(asignaturaId));
    }

    private List<Calificacion> aplicarFiltrosListaCalificacion(
            List<Calificacion> lista,
            Long grupoId,
            Long asignaturaId,
            String estado,
            boolean ocultarCapturadasParaAdmin,
            Set<Long> permitidosCoord) {
        if (grupoId != null) {
            lista = lista.stream()
                    .filter(c -> c.getGrupo() != null && grupoId.equals(c.getGrupo().getId()))
                    .collect(Collectors.toList());
        }
        if (asignaturaId != null) {
            lista = lista.stream()
                    .filter(c -> c.getAsignatura() != null && asignaturaId.equals(c.getAsignatura().getId()))
                    .collect(Collectors.toList());
        }
        if (estado != null && !estado.isBlank()) {
            String e = estado.trim().toUpperCase();
            lista = lista.stream()
                    .filter(c -> c.getEstadoAprobacion() != null && c.getEstadoAprobacion().name().equalsIgnoreCase(e))
                    .collect(Collectors.toList());
        }
        if (ocultarCapturadasParaAdmin) {
            lista = lista.stream()
                    .filter(c -> c.getEstadoAprobacion() == null
                            || c.getEstadoAprobacion() == Calificacion.EstadoAprobacion.EN_REVISION
                            || c.getEstadoAprobacion() == Calificacion.EstadoAprobacion.CONFIRMADA)
                    .collect(Collectors.toList());
        }
        if (permitidosCoord != null) {
            Set<Long> pids = permitidosCoord;
            lista = lista.stream().filter(c -> {
                if (c.getAlumno() != null && c.getAlumno().getPrograma() != null && pids.contains(c.getAlumno().getPrograma().getId())) {
                    return true;
                }
                return c.getAsignatura() != null && c.getAsignatura().getPrograma() != null && pids.contains(c.getAsignatura().getPrograma().getId());
            }).collect(Collectors.toList());
        }
        return lista;
    }

    /**
     * Lista calificaciones. Si se envía {@code alumnoId}, solo las de ese alumno (opcionalmente filtradas por {@code periodo}).
     * Sin {@code alumnoId}, devuelve el listado global (comportamiento administrativo histórico).
     */
    @GetMapping
    @RequierePermiso("VER_CALIFICACIONES")
    public ResponseEntity<?> listar(
            @RequestParam(required = false) Long alumnoId,
            @RequestParam(required = false) String periodo,
            @RequestParam(required = false) Long grupoId,
            @RequestParam(required = false) Long asignaturaId,
            @RequestParam(required = false) String estado,
            Authentication authentication
    ) {
        Usuario u = (authentication != null && authentication.getPrincipal() instanceof Usuario)
                ? (Usuario) authentication.getPrincipal()
                : null;
        boolean esMaestro = esDocente(u);
        // Para roles administrativos (secretaría/admin/coordinador), las calificaciones CAPTURADAS
        // por el docente aún no deben ser visibles hasta que sean EN_REVISION o CONFIRMADA.
        boolean ocultarCapturadasParaAdmin = !esMaestro;
        Set<Long> permitidos = null;
        try {
            if (u != null && u.tieneRol(Usuario.TipoUsuario.COORDINADOR_ACADEMICO)) {
                permitidos = new java.util.HashSet<>(usuarioRepository.findProgramaIdsAsignados(u.getId()));
                if (permitidos.isEmpty()) {
                    return ResponseEntity.ok(List.of());
                }
            }
        } catch (Exception ignored) {}
        if (alumnoId != null) {
            if (!alumnoRepository.existsById(alumnoId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Alumno no encontrado."));
            }
            List<Calificacion> lista;
            if (periodo != null && !periodo.isBlank()) {
                lista = calificacionRepository.findByAlumnoIdAndPeriodoFlexible(alumnoId, periodo.trim());
            } else {
                lista = calificacionRepository.findByAlumnoId(alumnoId);
            }
            lista = aplicarFiltrosListaCalificacion(lista, grupoId, asignaturaId, estado, ocultarCapturadasParaAdmin, permitidos);
            return ResponseEntity.ok(lista);
        }
        List<Calificacion> lista = calificacionRepository.findAll();
        if (ocultarCapturadasParaAdmin) {
            lista = lista.stream()
                    .filter(c -> c.getEstadoAprobacion() == null
                            || c.getEstadoAprobacion() == Calificacion.EstadoAprobacion.EN_REVISION
                            || c.getEstadoAprobacion() == Calificacion.EstadoAprobacion.CONFIRMADA)
                    .collect(Collectors.toList());
        }
        if (permitidos != null) {
            Set<Long> pids = permitidos;
            lista = lista.stream().filter(c -> {
                if (c.getAlumno() != null && c.getAlumno().getPrograma() != null && pids.contains(c.getAlumno().getPrograma().getId())) return true;
                return c.getAsignatura() != null && c.getAsignatura().getPrograma() != null && pids.contains(c.getAsignatura().getPrograma().getId());
            }).collect(Collectors.toList());
        }
        return ResponseEntity.ok(lista);
    }

    /**
     * Calificaciones por grupo + asignatura (para construir la tabla completa con alumnos del grupo).
     * Maestro: solo si imparte esa clase (mismo criterio que /maestros/me/grupos/{id}).
     */
    @GetMapping("/por-grupo-asignatura")
    @RequierePermiso({"VER_CALIFICACIONES", "REGISTRAR_CALIFICACIONES"})
    public ResponseEntity<?> listarPorGrupoYAsignatura(@RequestParam Long grupoId, @RequestParam Long asignaturaId,
                                                       Authentication authentication) {
        if (grupoId == null || asignaturaId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Se requieren grupoId y asignaturaId."));
        }
        Usuario u = (authentication != null && authentication.getPrincipal() instanceof Usuario)
                ? (Usuario) authentication.getPrincipal()
                : null;
        if (u != null && esDocente(u) && !puedeOperarCalificacionesComoAdministrativo(u)
                && !maestroTieneClaseEn(u, grupoId, asignaturaId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "No tienes permisos para ver calificaciones de este grupo y asignatura."));
        }
        List<Calificacion> lista = calificacionRepository.findByGrupo_IdAndAsignatura_Id(grupoId, asignaturaId);
        try {
            if (u != null && u.tieneRol(Usuario.TipoUsuario.COORDINADOR_ACADEMICO)) {
                Set<Long> permitidos = new java.util.HashSet<>(usuarioRepository.findProgramaIdsAsignados(u.getId()));
                if (permitidos.isEmpty()) {
                    return ResponseEntity.ok(List.of());
                }
                lista = lista.stream().filter(c -> {
                    if (c.getAlumno() != null && c.getAlumno().getPrograma() != null
                            && permitidos.contains(c.getAlumno().getPrograma().getId())) {
                        return true;
                    }
                    return c.getAsignatura() != null && c.getAsignatura().getPrograma() != null
                            && permitidos.contains(c.getAsignatura().getPrograma().getId());
                }).collect(Collectors.toList());
            }
        } catch (Exception ignored) {}
        return ResponseEntity.ok(lista);
    }

    /** Maestros registran calificaciones; secretaría académica puede crear y confirmar directo si es necesario. */
    @PostMapping
    @RequierePermiso({"REGISTRAR_CALIFICACIONES", "EDITAR_CALIFICACIONES"})
    @Transactional
    public ResponseEntity<?> crear(
            @RequestBody CalificacionRequest req,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Captura-Como", required = false) String capturaComo,
            Authentication authentication
    ) {
        if (req.getAlumnoId() == null || req.getAsignaturaId() == null) {
            return ResponseEntity.badRequest().body("Faltan alumnoId o asignaturaId.");
        }
        Optional<Alumno> optAlumno = alumnoRepository.findById(req.getAlumnoId());
        Optional<Asignatura> optAsignatura = asignaturaRepository.findById(req.getAsignaturaId());
        if (!optAlumno.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Alumno no encontrado.");
        }
        if (!optAsignatura.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Asignatura no encontrada.");
        }
        Grupo grupoOperacion = null;
        if (req.getGrupoId() != null) {
            grupoOperacion = grupoRepository.findById(req.getGrupoId()).orElse(null);
            if (grupoOperacion == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Grupo no encontrado.");
            }
            String mensajePeriodo = gestionAcademicaEstadoService.validarCapturaCalificacion(
                    resolverPeriodoOperacionCalificacion(grupoOperacion, null));
            if (mensajePeriodo != null) {
                return ResponseEntity.badRequest().body(mensajePeriodo);
            }
        }
        Usuario u = (authentication != null && authentication.getPrincipal() instanceof Usuario)
                ? (Usuario) authentication.getPrincipal()
                : null;
        boolean esDocente = esDocente(u);

        // Upsert: un alumno no debe tener más de una calificación por asignatura dentro de la misma clase (grupo+asignatura).
        // Si ya existe, actualizarla en vez de insertar una nueva.
        Calificacion c = null;
        try {
            List<Calificacion> existentes = calificacionRepository.findByAlumnoIdAndAsignaturaId(req.getAlumnoId(), req.getAsignaturaId());
            if (existentes != null && !existentes.isEmpty()) {
                Long gidReq = req.getGrupoId();
                if (gidReq != null) {
                    c = existentes.stream()
                            .filter(x -> x.getGrupo() != null && gidReq.equals(x.getGrupo().getId()))
                            .findFirst().orElse(null);
                } else {
                    // Si no se especifica grupo, tomar la primera (compatibilidad)
                    c = existentes.get(0);
                }
            }
        } catch (Exception ignored) {}
        boolean wasNew = false;
        if (c == null) {
            c = new Calificacion();
            c.setAlumno(optAlumno.get());
            c.setAsignatura(optAsignatura.get());
            wasNew = true;
        }

        Double califDesdeCriterios = null;
        try {
            califDesdeCriterios = aplicarCriteriosSiExisten(c, req, u);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
        Long gidNorm = req.getGrupoId() != null ? req.getGrupoId() : (c.getGrupo() != null ? c.getGrupo().getId() : null);
        Double califFinal;
        if (califDesdeCriterios != null) {
            califFinal = califDesdeCriterios;
        } else if (esDocente && !puedeOperarCalificacionesComoAdministrativo(u)
                && gidNorm != null && maestroSinCriteriosEvaluacionValidos(u, gidNorm, req.getAsignaturaId())) {
            limpiarDesgloseCriteriosCalificacion(c);
            califFinal = normalizarCalificacionDocenteSinCriterios(req.getCalificacionFinal());
        } else {
            califFinal = req.getCalificacionFinal();
        }
        c.setCalificacionFinal(califFinal);
        if (u != null && puedeOperarCalificacionesComoAdministrativo(u) && califDesdeCriterios == null) {
            String mensajeCalificacion = validarCalificacionDirectaAdministrativa(c.getCalificacionFinal());
            if (mensajeCalificacion != null) {
                return ResponseEntity.badRequest().body(mensajeCalificacion);
            }
        }
        // Validación de rango para captura docente (0..10 en persistencia)
        if (esDocente && c.getCalificacionFinal() != null) {
            double v = c.getCalificacionFinal();
            if (v < 0.0 || v > 10.0) {
                return ResponseEntity.badRequest().body("La calificación debe quedar entre 0.00 y 10.00 (captura 0–100% si no usas criterios).");
            }
        }
        // Estado:
        // - Maestro: CAPTURADA (hasta que envía a revisión)
        // - Administrativos (secretaría/admin/coordinador): al asignar/verificar, guardar como EN_REVISION.
        //   La calificación solo pasa a CONFIRMADA mediante el endpoint /confirmar.
        Calificacion.EstadoAprobacion estado;
        if (c.getCalificacionFinal() == null) {
            estado = Calificacion.EstadoAprobacion.PENDIENTE;
        } else {
            boolean forzarDocente = capturaComo != null && capturaComo.trim().equalsIgnoreCase("DOCENTE");
            boolean capturaComoDocente = esDocente && (forzarDocente || !puedeOperarCalificacionesComoAdministrativo(u));
            estado = capturaComoDocente ? Calificacion.EstadoAprobacion.CAPTURADA : Calificacion.EstadoAprobacion.EN_REVISION;
        }
        c.setEstadoAprobacion(estado);
        c.setAsistenciaPorcentaje(req.getAsistenciaPorcentaje());
        if (req.getPeriodo() != null && !req.getPeriodo().isBlank()) {
            ProgramaEducativo.TipoPeriodo tipo = null;
            try {
                if (c.getAlumno() != null && c.getAlumno().getPrograma() != null) {
                    tipo = c.getAlumno().getPrograma().getTipoPeriodo();
                }
            } catch (Exception ignored) {}
            c.setPeriodoAcademico(periodoAcademicoService.asegurarPeriodo(req.getPeriodo().trim(), tipo));
        }
        c.setIdObservaciones(req.getIdObservaciones() != null ? req.getIdObservaciones() : ObservacionCalificacion.ID_DEFAULT);
        c.setObservaciones(req.getObservaciones());
        if (req.getTipoEvaluacion() != null) {
            try {
                c.setTipoEvaluacion(Calificacion.TipoEvaluacion.valueOf(req.getTipoEvaluacion()));
            } catch (IllegalArgumentException ignored) {}
        }
        if (req.getGrupoId() != null) {
            c.setGrupo(grupoOperacion);
        }
        if (u != null) {
            if (esDocente && !puedeOperarCalificacionesComoAdministrativo(u)) {
                Long gid = req.getGrupoId();
                if (gid == null || !maestroTieneClaseEn(u, gid, req.getAsignaturaId())) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body("No tienes permisos para registrar calificaciones en este grupo y asignatura.");
                }
            }
        }
        // Estatus (APROBADO/REPROBADO) se calcula en @PrePersist del modelo
        Calificacion guardada = calificacionRepository.save(c);
        // Si fue actualización (upsert), responder 200; si fue creación nueva, 201.
        return ResponseEntity.status(wasNew ? HttpStatus.CREATED : HttpStatus.OK).body(guardada);
    }

    @GetMapping("/{id:\\d+}")
    @RequierePermiso("VER_CALIFICACIONES")
    public ResponseEntity<?> obtenerPorId(@PathVariable Long id) {
        Optional<Calificacion> opt = calificacionRepository.findById(id);
        return opt.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Maestros asignan (REGISTRAR) la calificación inicial; secretaría académica modifica (EDITAR).
     * Maestro: solo edita en PENDIENTE o CAPTURADA. Al guardar valor pasa a CAPTURADA.
     * Secretaría: puede editar en EN_REVISION (antes de confirmar) o en CONFIRMADA (modificar sin cambiar estado).
     */
    @PutMapping("/{id:\\d+}")
    @RequierePermiso({"REGISTRAR_CALIFICACIONES", "EDITAR_CALIFICACIONES"})
    @Transactional
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody CalificacionRequest payload, Authentication authentication) {
        Optional<Calificacion> opt = calificacionRepository.findById(id);
        if (!opt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        Calificacion existente = opt.get();
        String mensajePeriodo = gestionAcademicaEstadoService.validarCapturaCalificacion(
                resolverPeriodoOperacionCalificacion(null, existente));
        if (mensajePeriodo != null) {
            return ResponseEntity.badRequest().body(mensajePeriodo);
        }
        Usuario u = (authentication != null && authentication.getPrincipal() instanceof Usuario)
                ? (Usuario) authentication.getPrincipal()
                : null;
        boolean esDocente = esDocente(u);
        if (authentication != null && authentication.getPrincipal() instanceof Usuario) {
            if (esDocente && !puedeOperarCalificacionesComoAdministrativo(u)) {
                Long gid = existente.getGrupo() != null ? existente.getGrupo().getId() : null;
                Long aid = existente.getAsignatura() != null ? existente.getAsignatura().getId() : null;
                if (gid == null || aid == null || !maestroTieneClaseEn(u, gid, aid)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body("No tienes permisos para modificar esta calificación.");
                }
            }
        }
        Calificacion.EstadoAprobacion estado = existente.getEstadoAprobacion();

        // CONFIRMADA: solo vía PUT /modificar
        if (estado == Calificacion.EstadoAprobacion.CONFIRMADA) {
            return ResponseEntity.badRequest().body("La calificación está confirmada. Use el endpoint /modificar para cambios.");
        }
        // EN_REVISION: maestro no puede editar; secretaría usa PUT /editar-revision
        if (estado == Calificacion.EstadoAprobacion.EN_REVISION) {
            return ResponseEntity.badRequest().body("La calificación está en revisión. La secretaría puede editarla antes de confirmar.");
        }

        // Si viene captura por criterios (docente), calcular y guardar desglose
        Double califDesdeCriterios = null;
        try {
            califDesdeCriterios = aplicarCriteriosSiExisten(existente, payload, u);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
        Long gidEx = existente.getGrupo() != null ? existente.getGrupo().getId() : null;
        Long aidEx = existente.getAsignatura() != null ? existente.getAsignatura().getId() : null;
        if (califDesdeCriterios != null) {
            existente.setCalificacionFinal(califDesdeCriterios);
        } else if (esDocente && !puedeOperarCalificacionesComoAdministrativo(u)
                && gidEx != null && aidEx != null && maestroSinCriteriosEvaluacionValidos(u, gidEx, aidEx)) {
            limpiarDesgloseCriteriosCalificacion(existente);
            existente.setCalificacionFinal(normalizarCalificacionDocenteSinCriterios(payload.getCalificacionFinal()));
        } else if (payload.getCalificacionFinal() != null) {
            existente.setCalificacionFinal(payload.getCalificacionFinal());
        }
        if (u != null && puedeOperarCalificacionesComoAdministrativo(u) && califDesdeCriterios == null) {
            String mensajeCalificacion = validarCalificacionDirectaAdministrativa(existente.getCalificacionFinal());
            if (mensajeCalificacion != null) {
                return ResponseEntity.badRequest().body(mensajeCalificacion);
            }
        }
        // Validación de rango para captura docente (0..10)
        if (esDocente && existente.getCalificacionFinal() != null) {
            double v = existente.getCalificacionFinal();
            if (v < 0.0 || v > 10.0) {
                return ResponseEntity.badRequest().body("La calificación debe quedar entre 0.00 y 10.00 (captura 0–100% si no usas criterios).");
            }
        }
        if (payload.getAsistenciaPorcentaje() != null) existente.setAsistenciaPorcentaje(payload.getAsistenciaPorcentaje());
        if (payload.getIdObservaciones() != null) existente.setIdObservaciones(payload.getIdObservaciones());
        if (payload.getObservaciones() != null) existente.setObservaciones(payload.getObservaciones());
        // No permitir que el cliente cambie confirmada ni estadoAprobacion directamente
        if (existente.getCalificacionFinal() != null) {
            boolean capturaComoDocente = esDocente && !puedeOperarCalificacionesComoAdministrativo(u);
            existente.setEstadoAprobacion(capturaComoDocente
                    ? Calificacion.EstadoAprobacion.CAPTURADA
                    : Calificacion.EstadoAprobacion.EN_REVISION);
        } else {
            existente.setEstadoAprobacion(Calificacion.EstadoAprobacion.PENDIENTE);
        }

        Calificacion guardada = calificacionRepository.save(existente);
        return ResponseEntity.ok(guardada);
    }

    /**
     * Maestro envía a revisión. Solo desde CAPTURADA.
     */
    @PostMapping("/{id:\\d+}/enviar-revision")
    @RequierePermiso("REGISTRAR_CALIFICACIONES")
    public ResponseEntity<?> enviarRevision(@PathVariable Long id, Authentication authentication) {
        Optional<Calificacion> opt = calificacionRepository.findById(id);
        if (!opt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        Calificacion c = opt.get();
        String mensajePeriodo = gestionAcademicaEstadoService.validarCapturaCalificacion(
                resolverPeriodoOperacionCalificacion(null, c));
        if (mensajePeriodo != null) {
            return ResponseEntity.badRequest().body(mensajePeriodo);
        }
        if (authentication != null && authentication.getPrincipal() instanceof Usuario) {
            Usuario u = (Usuario) authentication.getPrincipal();
            if (esDocente(u) && !puedeOperarCalificacionesComoAdministrativo(u)) {
                Long gid = c.getGrupo() != null ? c.getGrupo().getId() : null;
                Long aid = c.getAsignatura() != null ? c.getAsignatura().getId() : null;
                if (gid == null || aid == null || !maestroTieneClaseEn(u, gid, aid)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body("No tienes permisos para enviar esta calificación a revisión.");
                }
            }
        }
        // Idempotente: reenvío no debe fallar (evita 400 si el cliente repite la petición o hay duplicados en el lote).
        if (c.getEstadoAprobacion() == Calificacion.EstadoAprobacion.EN_REVISION) {
            return ResponseEntity.ok(c);
        }
        if (c.getEstadoAprobacion() != Calificacion.EstadoAprobacion.CAPTURADA &&
                c.getEstadoAprobacion() != Calificacion.EstadoAprobacion.PENDIENTE) {
            return ResponseEntity.badRequest().body("Solo se puede enviar a revisión una calificación en estado Capturada o Pendiente (con valor).");
        }
        if (c.getCalificacionFinal() == null) {
            return ResponseEntity.badRequest().body("Debe capturar la calificación antes de enviar a revisión.");
        }
        c.setEstadoAprobacion(Calificacion.EstadoAprobacion.EN_REVISION);
        Calificacion guardada = calificacionRepository.save(c);
        return ResponseEntity.ok(guardada);
    }

    /**
     * Aplica una observación a todas las calificaciones de un alumno en un periodo.
     * Solo secretaría académica.
     */
    @PostMapping("/aplicar-observacion-periodo")
    @RequierePermiso("EDITAR_CALIFICACIONES")
    public ResponseEntity<?> aplicarObservacionAPeriodo(@RequestBody java.util.Map<String, Object> body) {
        Object alumnoIdObj = body.get("alumnoId");
        Object periodoObj = body.get("periodo");
        Object idObsObj = body.get("idObservaciones");
        if (alumnoIdObj == null || periodoObj == null || idObsObj == null) {
            return ResponseEntity.badRequest().body("Se requieren alumnoId, periodo e idObservaciones.");
        }
        Long alumnoId = alumnoIdObj instanceof Number ? ((Number) alumnoIdObj).longValue() : Long.parseLong(alumnoIdObj.toString());
        String periodo = periodoObj.toString().trim();
        Integer idObservaciones = idObsObj instanceof Number ? ((Number) idObsObj).intValue() : Integer.parseInt(idObsObj.toString());
        if (ObservacionCalificacion.getPorId(idObservaciones) == null) {
            return ResponseEntity.badRequest().body("idObservaciones no válido. Use un ID del catálogo.");
        }
        List<Calificacion> calificaciones = calificacionRepository.findByAlumnoIdAndPeriodo(alumnoId, periodo);
        for (Calificacion c : calificaciones) {
            if (!c.getConfirmada()) {
                c.setIdObservaciones(idObservaciones);
                calificacionRepository.save(c);
            }
        }
        return ResponseEntity.ok(java.util.Map.of(
                "mensaje", "Observación aplicada",
                "actualizadas", calificaciones.stream().filter(c -> !c.getConfirmada()).count(),
                "total", calificaciones.size()
        ));
    }

    /**
     * Secretaría académica edita una calificación en EN_REVISION (antes de confirmar).
     */
    @PutMapping("/{id:\\d+}/editar-revision")
    @RequierePermiso("EDITAR_CALIFICACIONES")
    public ResponseEntity<?> editarEnRevision(@PathVariable Long id,
                                              @RequestBody CalificacionRequest payload,
                                              Authentication authentication) {
        Optional<Calificacion> opt = calificacionRepository.findById(id);
        if (!opt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        Calificacion c = opt.get();
        String mensajePeriodo = gestionAcademicaEstadoService.validarCapturaCalificacion(
                resolverPeriodoOperacionCalificacion(null, c));
        if (mensajePeriodo != null) {
            return ResponseEntity.badRequest().body(mensajePeriodo);
        }
        if (c.getEstadoAprobacion() != Calificacion.EstadoAprobacion.EN_REVISION) {
            return ResponseEntity.badRequest().body("Solo se puede editar una calificación en estado En revisión.");
        }
        Usuario u = (authentication != null && authentication.getPrincipal() instanceof Usuario)
                ? (Usuario) authentication.getPrincipal()
                : null;
        Double califDesdeCriterios;
        try {
            califDesdeCriterios = aplicarCriteriosSiExisten(c, payload, u);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
        if (califDesdeCriterios != null) {
            c.setCalificacionFinal(califDesdeCriterios);
        } else if (payload.getCalificacionFinal() != null) {
            limpiarDesgloseCriteriosCalificacion(c);
            c.setCalificacionFinal(round2(payload.getCalificacionFinal()));
        }
        if (u != null && puedeOperarCalificacionesComoAdministrativo(u) && califDesdeCriterios == null) {
            String mensajeCalificacion = validarCalificacionDirectaAdministrativa(c.getCalificacionFinal());
            if (mensajeCalificacion != null) {
                return ResponseEntity.badRequest().body(mensajeCalificacion);
            }
        }
        if (payload.getAsistenciaPorcentaje() != null) c.setAsistenciaPorcentaje(payload.getAsistenciaPorcentaje());
        if (payload.getIdObservaciones() != null) c.setIdObservaciones(payload.getIdObservaciones());
        if (payload.getObservaciones() != null) c.setObservaciones(payload.getObservaciones());
        if (payload.getPeriodo() != null && !payload.getPeriodo().isBlank()) {
            ProgramaEducativo.TipoPeriodo tipo = null;
            try {
                if (c.getAsignatura() != null && c.getAsignatura().getPrograma() != null) {
                    tipo = c.getAsignatura().getPrograma().getTipoPeriodo();
                } else if (c.getAlumno() != null && c.getAlumno().getPrograma() != null) {
                    tipo = c.getAlumno().getPrograma().getTipoPeriodo();
                }
            } catch (Exception ignored) {}
            c.setPeriodoAcademico(periodoAcademicoService.asegurarPeriodo(payload.getPeriodo().trim(), tipo));
        }
        Calificacion guardada = calificacionRepository.save(c);
        return ResponseEntity.ok(guardada);
    }

    /** Solo secretaría académica confirma; al confirmar se define estatus APROBADO/REPROBADO. Solo desde EN_REVISION. */
    @PostMapping("/{id:\\d+}/confirmar")
    @RequierePermiso("CONFIRMAR_CALIFICACIONES")
    public ResponseEntity<?> confirmar(@PathVariable Long id) {
        Optional<Calificacion> opt = calificacionRepository.findById(id);
        if (!opt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        Calificacion c = opt.get();
        String mensajePeriodo = gestionAcademicaEstadoService.validarCapturaCalificacion(
                resolverPeriodoOperacionCalificacion(null, c));
        if (mensajePeriodo != null) {
            return ResponseEntity.badRequest().body(mensajePeriodo);
        }
        // Secretaría académica puede confirmar calificaciones enviadas (EN_REVISION) o capturadas directamente (CAPTURADA)
        if (c.getEstadoAprobacion() != Calificacion.EstadoAprobacion.EN_REVISION &&
                c.getEstadoAprobacion() != Calificacion.EstadoAprobacion.CAPTURADA) {
            return ResponseEntity.badRequest().body("Solo se puede confirmar una calificación en estado En revisión o Capturada.");
        }
        c.setEstadoAprobacion(Calificacion.EstadoAprobacion.CONFIRMADA);
        Calificacion guardada = calificacionRepository.save(c);
        if (guardada.getAlumno() != null) {
            progresoAcademicoNivelService.sincronizarPeriodoCursandoDesdeNiveles(guardada.getAlumno().getId());
        }
        return ResponseEntity.ok(guardada);
    }

    /**
     * Secretaría académica modifica una calificación ya confirmada (mantiene estado CONFIRMADA).
     */
    @PutMapping("/{id:\\d+}/modificar")
    @RequierePermiso({"EDITAR_CALIFICACIONES", "CONFIRMAR_CALIFICACIONES"})
    public ResponseEntity<?> modificarPorSecretaria(@PathVariable Long id, @RequestBody Calificacion payload) {
        Optional<Calificacion> opt = calificacionRepository.findById(id);
        if (!opt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        Calificacion c = opt.get();
        String mensajePeriodo = gestionAcademicaEstadoService.validarCorreccionCalificacion(
                resolverPeriodoOperacionCalificacion(null, c));
        if (mensajePeriodo != null) {
            return ResponseEntity.badRequest().body(mensajePeriodo);
        }
        if (c.getEstadoAprobacion() != Calificacion.EstadoAprobacion.CONFIRMADA) {
            return ResponseEntity.badRequest().body("Solo se puede modificar una calificación ya confirmada.");
        }
        if (payload.getCalificacionFinal() != null) {
            String mensajeCalificacion = validarCalificacionDirectaAdministrativa(payload.getCalificacionFinal());
            if (mensajeCalificacion != null) {
                return ResponseEntity.badRequest().body(mensajeCalificacion);
            }
            c.setCalificacionFinal(payload.getCalificacionFinal());
        }
        if (payload.getAsistenciaPorcentaje() != null) c.setAsistenciaPorcentaje(payload.getAsistenciaPorcentaje());
        if (payload.getIdObservaciones() != null) c.setIdObservaciones(payload.getIdObservaciones());
        if (payload.getObservaciones() != null) c.setObservaciones(payload.getObservaciones());
        if (payload.getPeriodo() != null && !payload.getPeriodo().isBlank()) {
            ProgramaEducativo.TipoPeriodo tipo = null;
            try {
                if (c.getAsignatura() != null && c.getAsignatura().getPrograma() != null) {
                    tipo = c.getAsignatura().getPrograma().getTipoPeriodo();
                } else if (c.getAlumno() != null && c.getAlumno().getPrograma() != null) {
                    tipo = c.getAlumno().getPrograma().getTipoPeriodo();
                }
            } catch (Exception ignored) {}
            c.setPeriodoAcademico(periodoAcademicoService.asegurarPeriodo(payload.getPeriodo().trim(), tipo));
        }
        // Mantener CONFIRMADA; fechaActualizacion queda como auditoría de última modificación
        Calificacion guardada = calificacionRepository.save(c);
        if (guardada.getAlumno() != null) {
            progresoAcademicoNivelService.sincronizarPeriodoCursandoDesdeNiveles(guardada.getAlumno().getId());
        }
        return ResponseEntity.ok(guardada);
    }

    /**
     * Captura masiva para certificados (secretaría/admin): upsert + confirmar en una sola operación.
     * Se usa para alumnos EGRESADOS en un programa específico (certificado TOTAL).
     * Cada elemento de {@code items} puede incluir {@code periodo} o {@code periodoCursado} (ciclo YYYY-N);
     * si no va en la fila, se usa el {@code periodo} raíz del JSON.
     */
    @PostMapping("/bulk-confirmar-programa")
    @RequierePermiso({"EDITAR_CALIFICACIONES", "CONFIRMAR_CALIFICACIONES"})
    @Transactional
    public ResponseEntity<?> bulkConfirmarPrograma(@RequestBody java.util.Map<String, Object> payload) {
        Long alumnoId = payload.get("alumnoId") != null ? Long.valueOf(String.valueOf(payload.get("alumnoId"))) : null;
        Long programaId = payload.get("programaId") != null ? Long.valueOf(String.valueOf(payload.get("programaId"))) : null;
        String periodo = payload.get("periodo") != null ? String.valueOf(payload.get("periodo")).trim() : "";
        Object itemsObj = payload.get("items");

        if (alumnoId == null || programaId == null) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "Faltan alumnoId o programaId"));
        }
        if (!(itemsObj instanceof java.util.List<?> items)) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "Falta items (lista)"));
        }

        Alumno alumno = alumnoRepository.findById(alumnoId)
                .orElseThrow(() -> new RuntimeException("Alumno no encontrado"));

        int ok = 0;
        int total = 0;
        java.util.List<java.util.Map<String, Object>> detalles = new java.util.ArrayList<>();

        ProgramaEducativo.TipoPeriodo tipo = null;

        for (Object raw : items) {
            total++;
            if (!(raw instanceof java.util.Map<?, ?> m)) continue;
            Long asignaturaId = m.get("asignaturaId") != null ? Long.valueOf(String.valueOf(m.get("asignaturaId"))) : null;
            Double cal = null;
            try {
                if (m.get("calificacionFinal") != null && String.valueOf(m.get("calificacionFinal")).trim().length() > 0) {
                    cal = Double.valueOf(String.valueOf(m.get("calificacionFinal")));
                }
            } catch (Exception ignored) {}

            if (asignaturaId == null || cal == null) continue;

            String periodoFila = "";
            if (m.get("periodo") != null && !String.valueOf(m.get("periodo")).isBlank()) {
                periodoFila = String.valueOf(m.get("periodo")).trim();
            } else if (m.get("periodoCursado") != null && !String.valueOf(m.get("periodoCursado")).isBlank()) {
                periodoFila = String.valueOf(m.get("periodoCursado")).trim();
            }
            String periodoEfectivo = !periodoFila.isBlank() ? periodoFila : periodo;

            Asignatura asig = asignaturaRepository.findById(asignaturaId)
                    .orElseThrow(() -> new RuntimeException("Asignatura no encontrada: " + asignaturaId));
            if (asig.getPrograma() == null || asig.getPrograma().getId() == null || !programaId.equals(asig.getPrograma().getId())) {
                detalles.add(java.util.Map.of(
                        "asignaturaId", asignaturaId,
                        "ok", false,
                        "mensaje", "La asignatura no pertenece al programa seleccionado"
                ));
                continue;
            }
            if (tipo == null && asig.getPrograma() != null) {
                try { tipo = asig.getPrograma().getTipoPeriodo(); } catch (Exception ignored) {}
            }

            // Upsert por alumno+asignatura (misma regla del endpoint crear)
            Calificacion c = calificacionRepository.findTop1ByAlumno_IdAndAsignatura_IdOrderByFechaCreacionDesc(alumnoId, asignaturaId)
                    .orElseGet(Calificacion::new);
            c.setAlumno(alumno);
            c.setAsignatura(asig);
            c.setCalificacionFinal(round2(cal));

            if (!periodoEfectivo.isBlank()) {
                c.setPeriodoAcademico(periodoAcademicoService.asegurarPeriodo(periodoEfectivo, tipo));
            }

            // Confirmar de una vez para que cuente en certificados
            c.setEstadoAprobacion(Calificacion.EstadoAprobacion.CONFIRMADA);
            calificacionRepository.save(c);
            ok++;
            detalles.add(java.util.Map.of(
                    "asignaturaId", asignaturaId,
                    "ok", true
            ));
        }

        if (alumno != null) {
            progresoAcademicoNivelService.sincronizarPeriodoCursandoDesdeNiveles(alumno.getId());
        }
        return ResponseEntity.ok(java.util.Map.of(
                "ok", ok,
                "total", total,
                "detalles", detalles
        ));
    }

    /**
     * Descarga plantilla Excel para capturar calificaciones (una fila por asignatura del programa).
     * Columnas:
     * - matricula (del alumno; no editar)
     * - programaId
     * - asignaturaId (no editar)
     * - clave, nombre
     * - periodo (ciclo escolar en que cursó la materia, ej. 2024-2, 2025-1)
     * - calificacionFinal (captura: 0..10)
     */
    @GetMapping("/excel/plantilla-certificados")
    @RequierePermiso({"EDITAR_CALIFICACIONES", "CONFIRMAR_CALIFICACIONES"})
    public ResponseEntity<byte[]> descargarPlantillaCertificados(@RequestParam String matricula, @RequestParam Long programaId) {
        String m = matricula != null ? matricula.trim() : "";
        if (m.isBlank()) {
            throw new RuntimeException("Indica la matrícula del alumno");
        }
        Alumno alumno = alumnoRepository.findByMatricula(m)
                .orElseThrow(() -> new RuntimeException("Alumno no encontrado para la matrícula indicada"));
        ProgramaEducativo programa = programaRepository.findById(programaId).orElseThrow(() -> new RuntimeException("Programa no encontrado"));

        List<Asignatura> plan = asignaturaRepository.findByProgramaId(programaId);
        Asignatura.ordenarListaPorIdAsignatura(plan);
        plan = plan.stream().filter(Asignatura::cuentaEnPlanAcademico).collect(Collectors.toList());

        byte[] bytes;
        String matSegura = alumno.getMatricula() != null ? alumno.getMatricula() : m;
        try (org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sh = wb.createSheet("Calificaciones");
            int r = 0;
            org.apache.poi.ss.usermodel.Row h = sh.createRow(r++);
            h.createCell(0).setCellValue("matricula");
            h.createCell(1).setCellValue("programaId");
            h.createCell(2).setCellValue("asignaturaId");
            h.createCell(3).setCellValue("clave");
            h.createCell(4).setCellValue("nombre");
            h.createCell(5).setCellValue("periodo");
            h.createCell(6).setCellValue("calificacionFinal");

            for (Asignatura a : plan) {
                org.apache.poi.ss.usermodel.Row row = sh.createRow(r++);
                row.createCell(0).setCellValue(matSegura);
                row.createCell(1).setCellValue(programa.getId());
                row.createCell(2).setCellValue(a.getId());
                row.createCell(3).setCellValue(a.getClave() != null ? a.getClave() : "");
                row.createCell(4).setCellValue(a.getNombre() != null ? a.getNombre() : "");
                row.createCell(5).setCellValue(""); // ciclo escolar ej. 2024-2
                row.createCell(6).setCellValue(""); // calificación 0..10
            }
            sh.createFreezePane(0, 1);
            for (int i = 0; i <= 6; i++) sh.autoSizeColumn(i);

            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(32_768);
            wb.write(bos);
            bytes = bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo generar plantilla Excel: " + e.getMessage());
        }

        String fn = ("plantilla_calificaciones_" + matSegura + "_prog_" + programaId)
                .replaceAll("[\\\\/:*?\"<>|]+", "").trim();
        if (fn.isBlank()) fn = "plantilla_calificaciones";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fn + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    private static String excelCellString(org.apache.poi.ss.usermodel.Row row, int colIdx, org.apache.poi.ss.usermodel.DataFormatter fmt) {
        if (row == null || colIdx < 0 || fmt == null) {
            return "";
        }
        org.apache.poi.ss.usermodel.Cell cell = row.getCell(colIdx);
        if (cell == null) {
            return "";
        }
        return fmt.formatCellValue(cell).trim();
    }

    /**
     * Importa una plantilla Excel previamente descargada y confirma calificaciones.
     * Columnas requeridas: matricula, programaId, asignaturaId, calificacionFinal.
     * Opcional por fila: periodo (o periodoCursado) — ciclo escolar YYYY-N; si falta, se usa el query param {@code periodo} del POST.
     * Compatibilidad: plantillas sin columna periodo siguen funcionando; si el archivo trae {@code alumnoId} en lugar de {@code matricula}, también se acepta.
     */
    @PostMapping(value = "/excel/importar-certificados", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequierePermiso({"EDITAR_CALIFICACIONES", "CONFIRMAR_CALIFICACIONES"})
    public ResponseEntity<?> importarExcelCertificados(@RequestPart("archivo") MultipartFile archivo,
                                                      @RequestParam(required = false) String periodo) {
        if (archivo == null || archivo.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Debe seleccionar un archivo Excel"));
        }
        String nombre = archivo.getOriginalFilename();
        if (nombre == null || (!nombre.toLowerCase().endsWith(".xlsx") && !nombre.toLowerCase().endsWith(".xls"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "El archivo debe ser Excel (.xlsx o .xls)"));
        }

        try (org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(archivo.getInputStream())) {
            org.apache.poi.ss.usermodel.Sheet sh = wb.getSheet("Calificaciones");
            if (sh == null) sh = wb.getSheetAt(0);
            if (sh == null || sh.getPhysicalNumberOfRows() < 2) {
                return ResponseEntity.badRequest().body(Map.of("error", "El Excel no contiene filas de datos"));
            }

            org.apache.poi.ss.usermodel.Row header = sh.getRow(0);
            java.util.Map<String, Integer> idx = new java.util.HashMap<>();
            if (header != null) {
                for (int c = 0; c <= header.getLastCellNum(); c++) {
                    org.apache.poi.ss.usermodel.Cell cell = header.getCell(c);
                    String v = cell != null ? String.valueOf(cell).trim() : "";
                    if (!v.isBlank()) {
                        idx.put(v, c);
                    }
                }
            }
            java.util.function.Function<String, Integer> col = (k) -> idx.getOrDefault(k, -1);
            int cMat = col.apply("matricula");
            int cAid = col.apply("alumnoId");
            int cProg = col.apply("programaId");
            int cAsig = col.apply("asignaturaId");
            int cCal = col.apply("calificacionFinal");
            int cPer = col.apply("periodo");
            if (cPer < 0) {
                cPer = col.apply("periodoCursado");
            }
            boolean porMatricula = cMat >= 0;
            if (!porMatricula && cAid < 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Faltan columnas requeridas: matricula (o alumnoId en plantillas antiguas), programaId, asignaturaId, calificacionFinal"));
            }
            if (cProg < 0 || cAsig < 0 || cCal < 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Faltan columnas requeridas: programaId, asignaturaId, calificacionFinal"));
            }

            org.apache.poi.ss.usermodel.DataFormatter fmt = new org.apache.poi.ss.usermodel.DataFormatter();
            Long alumnoId = null;
            Long programaId = null;
            String matriculaFija = null;
            java.util.List<java.util.Map<String, Object>> items = new java.util.ArrayList<>();

            for (int r = 1; r <= sh.getLastRowNum(); r++) {
                org.apache.poi.ss.usermodel.Row row = sh.getRow(r);
                if (row == null) continue;

                String pIdS = excelCellString(row, cProg, fmt);
                String asigIdS = excelCellString(row, cAsig, fmt);
                String calS = excelCellString(row, cCal, fmt);
                String perS = cPer >= 0 ? excelCellString(row, cPer, fmt) : "";
                if (asigIdS.isBlank()) continue;
                if (calS.isBlank()) continue;

                Long aId;
                if (porMatricula) {
                    String matS = excelCellString(row, cMat, fmt);
                    if (matS.isBlank()) continue;
                    if (matriculaFija == null) {
                        matriculaFija = matS;
                    } else if (!matriculaFija.equals(matS)) {
                        return ResponseEntity.badRequest().body(Map.of("error", "El Excel mezcla varias matrículas. Use un archivo por alumno y programa."));
                    }
                    aId = alumnoRepository.findByMatricula(matS)
                            .map(Alumno::getId)
                            .orElseThrow(() -> new IllegalArgumentException("No hay alumno con matrícula: " + matS));
                } else {
                    String aIdS = excelCellString(row, cAid, fmt);
                    if (aIdS.isBlank()) continue;
                    aId = Long.valueOf(aIdS.replaceAll("\\.0$", ""));
                }

                Long pId = Long.valueOf(pIdS.replaceAll("\\.0$", ""));
                Long asigId = Long.valueOf(asigIdS.replaceAll("\\.0$", ""));
                Double cal = Double.valueOf(calS.replace(",", "."));

                if (alumnoId == null) {
                    alumnoId = aId;
                }
                if (programaId == null) {
                    programaId = pId;
                }
                if (!aId.equals(alumnoId) || !pId.equals(programaId)) {
                    return ResponseEntity.badRequest().body(Map.of("error", "El Excel contiene múltiples alumno/programa. Use un archivo por alumno y programa."));
                }
                java.util.Map<String, Object> itemRow = new java.util.LinkedHashMap<>();
                itemRow.put("asignaturaId", asigId);
                itemRow.put("calificacionFinal", cal);
                if (perS != null && !perS.isBlank()) {
                    itemRow.put("periodo", perS);
                }
                items.add(itemRow);
            }

            if (alumnoId == null || programaId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "No se pudo determinar alumno y programa desde el Excel"));
            }
            if (items.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No hay calificaciones capturadas en el Excel"));
            }

            // Transacción aislada: si bulkConfirmarPrograma falla, no dejar una transacción externa
            // marcada rollback-only (evita UnexpectedRollbackException al devolver JSON de error).
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("alumnoId", alumnoId);
            payload.put("programaId", programaId);
            payload.put("periodo", periodo != null ? periodo : "");
            payload.put("items", items);
            TransactionTemplate tpl = new TransactionTemplate(transactionManager);
            return tpl.execute(status -> bulkConfirmarPrograma(payload));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Datos inválidos en el Excel"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "No se pudo importar el Excel", "mensaje", e.getMessage()));
        }
    }
}
