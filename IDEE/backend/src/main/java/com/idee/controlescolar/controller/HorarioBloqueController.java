package com.idee.controlescolar.controller;

import com.idee.controlescolar.dto.ClaseMaestroDTO;
import com.idee.controlescolar.dto.HorarioBloqueRequest;
import com.idee.controlescolar.model.*;
import com.idee.controlescolar.repository.*;
import com.idee.controlescolar.security.RequierePermiso;
import com.idee.controlescolar.service.GestionAcademicaEstadoService;
import com.idee.controlescolar.service.HorarioBloqueService;
import com.idee.controlescolar.service.PeriodoAcademicoService;
import com.idee.controlescolar.service.ProgramaAccesoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Controlador REST para gestionar bloques de horario.
 */
@RestController
@RequestMapping("/horarios")
@CrossOrigin(origins = "*")
public class HorarioBloqueController {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @Autowired
    private HorarioBloqueRepository horarioBloqueRepository;

    @Autowired
    private ProgramaEducativoRepository programaEducativoRepository;

    @Autowired
    private AsignaturaRepository asignaturaRepository;

    @Autowired
    private MaestroRepository maestroRepository;

    @Autowired
    private HorarioBloqueService horarioBloqueService;

    @Autowired
    private PeriodoAcademicoService periodoAcademicoService;

    @Autowired
    private GrupoRepository grupoRepository;

    @Autowired
    private ProgramaAccesoService programaAccesoService;

    @Autowired
    private EvaluacionDocenteRespuestaRepository evaluacionDocenteRespuestaRepository;

    @Autowired
    private GestionAcademicaEstadoService gestionAcademicaEstadoService;

    /**
     * Listar horarios, opcionalmente filtrados por programa, grupo y periodo.
     */
    @GetMapping
    @RequierePermiso("VER_HORARIOS")
    public ResponseEntity<List<HorarioBloque>> listar(
            @RequestParam(required = false) Long programaId,
            @RequestParam(required = false) Long grupoId,
            @RequestParam(required = false) Long periodoAcademicoId,
            @RequestParam(required = false, defaultValue = "false") boolean incluirInactivos,
            Authentication authentication) {
        Usuario u = (authentication != null && authentication.getPrincipal() instanceof Usuario)
                ? (Usuario) authentication.getPrincipal()
                : null;
        List<HorarioBloque> bloques;
        HorarioBloque.EstatusHorario estatusFiltro = incluirInactivos ? null : HorarioBloque.EstatusHorario.ACTIVO;
        if (grupoId != null && periodoAcademicoId != null) {
            bloques = (estatusFiltro == null)
                    ? horarioBloqueRepository.findByGrupoEntity_IdAndPeriodoAcademico_Id(grupoId, periodoAcademicoId)
                    : horarioBloqueRepository.findByGrupoEntity_IdAndPeriodoAcademico_IdAndEstatusOrderByDiaAscHoraInicioAsc(
                        grupoId, periodoAcademicoId, estatusFiltro);
        } else if (grupoId != null) {
            bloques = (estatusFiltro == null)
                    ? horarioBloqueRepository.findByGrupoEntity_Id(grupoId)
                    : horarioBloqueRepository.findByGrupoEntity_IdAndEstatusOrderByDiaAscHoraInicioAsc(grupoId, estatusFiltro);
        } else if (programaId != null) {
            bloques = (estatusFiltro == null)
                    ? horarioBloqueRepository.findByProgramaIdOrderByDiaAscHoraInicioAsc(programaId)
                    : horarioBloqueRepository.findByPrograma_IdAndEstatusOrderByDiaAscHoraInicioAsc(programaId, estatusFiltro);
        } else {
            bloques = (estatusFiltro == null)
                    ? horarioBloqueRepository.findAllByOrderByDiaAscHoraInicioAsc()
                    : horarioBloqueRepository.findAllByEstatusOrderByDiaAscHoraInicioAsc(estatusFiltro);
        }
        if (programaAccesoService.esCoordinadorAcademico(u)) {
            var permitidos = programaAccesoService.programaIdsPermitidos(u);
            if (permitidos.isEmpty()) return ResponseEntity.ok(List.of());
            if (programaId != null && !permitidos.contains(programaId)) return ResponseEntity.status(403).body(List.of());
            bloques = bloques.stream()
                    .filter(b -> b.getPrograma() != null && permitidos.contains(b.getPrograma().getId()))
                    .toList();
        }
        return ResponseEntity.ok(bloques);
    }

    /**
     * Clases (materia + docente del horario) activas para un grupo. Una entrada por par grupo+materia.
     * Usado en revisión de calificaciones del personal administrativo.
     */
    @GetMapping("/clases-por-grupo")
    @RequierePermiso("VER_CALIFICACIONES")
    public ResponseEntity<?> listarClasesActivasPorGrupo(
            @RequestParam Long grupoId,
            Authentication authentication) {
        if (grupoId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Se requiere grupoId."));
        }
        Usuario u = (authentication != null && authentication.getPrincipal() instanceof Usuario)
                ? (Usuario) authentication.getPrincipal()
                : null;
        Optional<Grupo> optG = grupoRepository.findById(grupoId);
        if (optG.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Grupo g = optG.get();
        if (g.getEstatus() != Grupo.EstatusGrupo.ACTIVO) {
            return ResponseEntity.ok(List.of());
        }
        java.util.Set<Long> permitidosCoord = null;
        if (programaAccesoService.esCoordinadorAcademico(u)) {
            permitidosCoord = programaAccesoService.programaIdsPermitidos(u);
            if (permitidosCoord.isEmpty()) {
                return ResponseEntity.ok(List.of());
            }
        }

        List<HorarioBloque> bloques = horarioBloqueRepository.findByGrupoEntity_IdAndEstatus(
                grupoId, HorarioBloque.EstatusHorario.ACTIVO);
        Map<String, ClaseMaestroDTO> porClave = new LinkedHashMap<>();
        for (HorarioBloque h : bloques) {
            if (h.getAsignatura() == null || h.getGrupoEntity() == null) {
                continue;
            }
            if (permitidosCoord != null) {
                Long pid = null;
                if (h.getPrograma() != null && h.getPrograma().getId() != null) {
                    pid = h.getPrograma().getId();
                } else if (h.getAsignatura().getPrograma() != null) {
                    pid = h.getAsignatura().getPrograma().getId();
                }
                if (pid == null || !permitidosCoord.contains(pid)) {
                    continue;
                }
            }
            String key = h.getGrupoEntity().getId() + "_" + h.getAsignatura().getId();
            if (porClave.containsKey(key)) {
                continue;
            }
            Maestro m = h.getMaestro();
            Long mid = m != null ? m.getId() : null;
            String mnom = nombreDocenteHorario(m);
            PeriodoAcademico pa = h.getPeriodoAcademico();
            porClave.put(key, ClaseMaestroDTO.builder()
                    .grupoId(h.getGrupoEntity().getId())
                    .grupoNombre(h.getGrupoEntity().getNombre())
                    .asignaturaId(h.getAsignatura().getId())
                    .asignaturaNombre(h.getAsignatura().getNombre())
                    .periodo(pa != null && pa.getCodigo() != null ? pa.getCodigo() : h.getCicloEscolar())
                    .periodoAcademicoId(pa != null ? pa.getId() : null)
                    .maestroId(mid)
                    .maestroNombre(mnom)
                    .build());
        }
        List<ClaseMaestroDTO> out = new ArrayList<>(porClave.values());
        out.sort(Comparator.comparing(ClaseMaestroDTO::getAsignaturaNombre,
                Comparator.nullsLast(String::compareToIgnoreCase)));
        return ResponseEntity.ok(out);
    }

    private static String nombreDocenteHorario(Maestro m) {
        if (m == null) {
            return "Sin docente asignado";
        }
        String etiqueta = m.getEtiqueta() != null ? m.getEtiqueta().trim() : "";
        String nombre = String.join(" ",
                java.util.stream.Stream.of(m.getNombre(), m.getApellidoPaterno(), m.getApellidoMaterno())
                        .filter(s -> s != null && !s.isBlank())
                        .toList());
        if (nombre.isBlank()) {
            return "Sin docente asignado";
        }
        return etiqueta.isEmpty() ? nombre : etiqueta + " " + nombre;
    }

    /**
     * Obtener un bloque por ID.
     */
    @GetMapping("/{id}")
    @RequierePermiso("VER_HORARIOS")
    public ResponseEntity<?> obtenerPorId(@PathVariable Long id) {
        Optional<HorarioBloque> opt = horarioBloqueRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(opt.get());
    }

    /**
     * Crear un nuevo bloque de horario.
     */
    @PostMapping
    @RequierePermiso("ACTUALIZAR_HORARIOS")
    public ResponseEntity<?> crear(@Valid @RequestBody HorarioBloqueRequest request) {
        try {
            ProgramaEducativo programa = programaEducativoRepository.findById(request.getProgramaId())
                    .orElse(null);
            if (programa == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Programa educativo no encontrado.");
            }

            Asignatura asignatura = resolverAsignatura(request);
            if (asignatura == null) {
                if (request.getAsignaturaId() == null && (request.getIdAsignatura() == null || request.getIdAsignatura().isBlank())) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("mensaje", "Debes indicar la asignatura (asignaturaId o idAsignatura)."));
                }
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("mensaje", "Asignatura no encontrada."));
            }

            Maestro maestro = null;
            if (request.getMaestroId() != null) {
                maestro = maestroRepository.findById(request.getMaestroId()).orElse(null);
                if (maestro == null) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("mensaje", "Maestro no encontrado."));
                }
            }

            PeriodoAcademico periodo = resolverPeriodoAcademico(request);

            if (request.getGrupoId() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("mensaje", "Debes seleccionar un grupo."));
            }

            Optional<Grupo> grupoOpt = grupoRepository.findById(request.getGrupoId());
            if (grupoOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("mensaje", "Grupo no encontrado."));
            }
            Grupo grupo = grupoOpt.get();
            String mensajePeriodo = gestionAcademicaEstadoService.validarEdicionHorario(periodoOperacionHorario(periodo, grupo));
            if (mensajePeriodo != null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("mensaje", mensajePeriodo));
            }
            String errorGrupo = validarGrupoParaHorario(grupo, programa, asignatura, periodo);
            if (errorGrupo != null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("mensaje", errorGrupo));
            }

            String hIni = normalizarHora(request.getHoraInicio());
            String hFin = normalizarHora(request.getHoraFin());
            LocalTime horaInicio = LocalTime.parse(hIni, TIME_FORMAT);
            LocalTime horaFin = LocalTime.parse(hFin, TIME_FORMAT);
            if (!horaFin.isAfter(horaInicio)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("mensaje", "La hora de fin debe ser posterior a la hora de inicio."));
            }

            String nomAsig = nombreAsignaturaLegible(asignatura);
            var cAula = horarioBloqueService.buscarConflictoAulaEnPrograma(
                    programa.getId(),
                    request.getDia(), horaInicio, horaFin, request.getAula(),
                    request.getFechaInicio(), request.getFechaFin(),
                    null);
            if (cAula.isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("mensaje", horarioBloqueService.mensajeConflictoSalon(
                                nomAsig, request.getDia(), horaInicio, horaFin, request.getAula(), cAula.get())));
            }
            if (maestro != null) {
                var cMaestro = horarioBloqueService.buscarConflictoMaestroEnPrograma(
                        programa.getId(),
                        request.getMaestroId(), request.getDia(), horaInicio, horaFin,
                        request.getFechaInicio(), request.getFechaFin(),
                        null);
                if (cMaestro.isPresent()) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of("mensaje", horarioBloqueService.mensajeConflictoDocente(
                                    nomAsig, request.getDia(), horaInicio, horaFin, cMaestro.get())));
                }
            }
            String grupoNombre = resolverGrupoNombre(request);
            if (grupoNombre != null && !grupoNombre.isBlank()) {
                var dup = horarioBloqueService.buscarBloqueMismaAsignaturaGrupoDiaEnPrograma(
                        programa.getId(),
                        asignatura.getId(), grupoNombre, request.getDia(),
                        request.getFechaInicio(), request.getFechaFin(),
                        null);
                if (dup.isPresent()) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of("mensaje", horarioBloqueService.mensajeDuplicadoMismaMateriaGrupoDia(
                                    nomAsig, grupoNombre, request.getDia(), dup.get())));
                }
            }

            HorarioBloque bloque = new HorarioBloque();
            bloque.setPrograma(programa);
            bloque.setAsignatura(asignatura);
            bloque.setMaestro(maestro);
            bloque.setDia(request.getDia());
            bloque.setHoraInicio(horaInicio);
            bloque.setHoraFin(horaFin);
            bloque.setFechaInicio(request.getFechaInicio());
            bloque.setFechaFin(request.getFechaFin());
            asignarGrupo(bloque, request);
            bloque.setPeriodoAcademico(periodo != null ? periodo : grupo.getPeriodoAcademico());
            bloque.setAula(request.getAula());
            bloque.setEstatus(request.getEstatus() != null ? request.getEstatus() : HorarioBloque.EstatusHorario.ACTIVO);

            HorarioBloque guardado = horarioBloqueRepository.save(bloque);
            return ResponseEntity.status(HttpStatus.CREATED).body(guardado);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("mensaje", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al crear el bloque: " + e.getMessage());
        }
    }

    /**
     * Actualizar un bloque de horario.
     */
    @PutMapping("/{id}")
    @RequierePermiso("ACTUALIZAR_HORARIOS")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @Valid @RequestBody HorarioBloqueRequest request) {
        Optional<HorarioBloque> opt = horarioBloqueRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        try {
            HorarioBloque bloque = opt.get();

            ProgramaEducativo programa = programaEducativoRepository.findById(request.getProgramaId())
                    .orElse(null);
            if (programa == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("mensaje", "Programa educativo no encontrado."));
            }

            Asignatura asignatura = resolverAsignatura(request);
            if (asignatura == null) {
                if (request.getAsignaturaId() == null && (request.getIdAsignatura() == null || request.getIdAsignatura().isBlank())) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("mensaje", "Debes indicar la asignatura (asignaturaId o idAsignatura)."));
                }
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("mensaje", "Asignatura no encontrada."));
            }

            Maestro maestro = null;
            if (request.getMaestroId() != null) {
                maestro = maestroRepository.findById(request.getMaestroId()).orElse(null);
                if (maestro == null) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("mensaje", "Maestro no encontrado."));
                }
            }

            PeriodoAcademico periodo = resolverPeriodoAcademico(request);

            if (request.getGrupoId() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("mensaje", "Debes seleccionar un grupo."));
            }

            Optional<Grupo> grupoOpt = grupoRepository.findById(request.getGrupoId());
            if (grupoOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("mensaje", "Grupo no encontrado."));
            }
            Grupo grupo = grupoOpt.get();
            String mensajePeriodo = gestionAcademicaEstadoService.validarEdicionHorario(periodoOperacionHorario(periodo, grupo));
            if (mensajePeriodo != null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("mensaje", mensajePeriodo));
            }
            String errorGrupo = validarGrupoParaHorario(grupo, programa, asignatura, periodo);
            if (errorGrupo != null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("mensaje", errorGrupo));
            }

            String hIni = normalizarHora(request.getHoraInicio());
            String hFin = normalizarHora(request.getHoraFin());
            LocalTime horaInicio = LocalTime.parse(hIni, TIME_FORMAT);
            LocalTime horaFin = LocalTime.parse(hFin, TIME_FORMAT);
            if (!horaFin.isAfter(horaInicio)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("mensaje", "La hora de fin debe ser posterior a la hora de inicio."));
            }

            String nomAsigUp = nombreAsignaturaLegible(asignatura);
            var cAulaUp = horarioBloqueService.buscarConflictoAulaEnPrograma(
                    programa.getId(),
                    request.getDia(), horaInicio, horaFin, request.getAula(),
                    request.getFechaInicio(), request.getFechaFin(),
                    id);
            if (cAulaUp.isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("mensaje", horarioBloqueService.mensajeConflictoSalon(
                                nomAsigUp, request.getDia(), horaInicio, horaFin, request.getAula(), cAulaUp.get())));
            }
            if (maestro != null) {
                var cMaestroUp = horarioBloqueService.buscarConflictoMaestroEnPrograma(
                        programa.getId(),
                        request.getMaestroId(), request.getDia(), horaInicio, horaFin,
                        request.getFechaInicio(), request.getFechaFin(),
                        id);
                if (cMaestroUp.isPresent()) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of("mensaje", horarioBloqueService.mensajeConflictoDocente(
                                    nomAsigUp, request.getDia(), horaInicio, horaFin, cMaestroUp.get())));
                }
            }
            String grupoNombre = resolverGrupoNombre(request);
            if (grupoNombre != null && !grupoNombre.isBlank()) {
                var dupUp = horarioBloqueService.buscarBloqueMismaAsignaturaGrupoDiaEnPrograma(
                        programa.getId(),
                        asignatura.getId(), grupoNombre, request.getDia(),
                        request.getFechaInicio(), request.getFechaFin(),
                        id);
                if (dupUp.isPresent()) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of("mensaje", horarioBloqueService.mensajeDuplicadoMismaMateriaGrupoDia(
                                    nomAsigUp, grupoNombre, request.getDia(), dupUp.get())));
                }
            }

            boolean tieneHistorial = bloqueTieneHistorialEvaluacion(bloque);
            boolean requiereVersionado = tieneHistorial && requiereVersionadoHistorico(
                    bloque, programa, asignatura, maestro, grupo, periodo,
                    request.getDia(), horaInicio, horaFin, request.getFechaInicio(), request.getFechaFin(), request.getAula());
            if (requiereVersionado) {
                bloque.setEstatus(HorarioBloque.EstatusHorario.CANCELADO);
                horarioBloqueRepository.save(bloque);
                HorarioBloque sucesor = new HorarioBloque();
                sucesor.setPrograma(programa);
                sucesor.setAsignatura(asignatura);
                sucesor.setMaestro(maestro);
                sucesor.setDia(request.getDia());
                sucesor.setHoraInicio(horaInicio);
                sucesor.setHoraFin(horaFin);
                sucesor.setFechaInicio(request.getFechaInicio());
                sucesor.setFechaFin(request.getFechaFin());
                sucesor.setGrupoEntity(grupo);
                sucesor.setPeriodoAcademico(periodo != null ? periodo : grupo.getPeriodoAcademico());
                sucesor.setAula(request.getAula());
                sucesor.setEstatus(request.getEstatus() != null ? request.getEstatus() : HorarioBloque.EstatusHorario.ACTIVO);
                HorarioBloque guardado = horarioBloqueRepository.save(sucesor);
                return ResponseEntity.ok(guardado);
            }

            bloque.setPrograma(programa);
            bloque.setAsignatura(asignatura);
            bloque.setMaestro(maestro);
            bloque.setDia(request.getDia());
            bloque.setHoraInicio(horaInicio);
            bloque.setHoraFin(horaFin);
            bloque.setFechaInicio(request.getFechaInicio());
            bloque.setFechaFin(request.getFechaFin());
            asignarGrupo(bloque, request);
            bloque.setPeriodoAcademico(periodo != null ? periodo : grupo.getPeriodoAcademico());
            bloque.setAula(request.getAula());
            bloque.setEstatus(request.getEstatus() != null ? request.getEstatus() : HorarioBloque.EstatusHorario.ACTIVO);

            HorarioBloque guardado = horarioBloqueRepository.save(bloque);
            return ResponseEntity.ok(guardado);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("mensaje", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al actualizar el bloque: " + e.getMessage());
        }
    }

    /**
     * Reemplaza el horario activo de un grupo preservando los bloques que llegan con ID.
     * Esto permite editar solo docente/aula/fechas/horas sin romper referencias históricas
     * como respuestas de evaluación docente.
     */
    @PutMapping("/grupo/{grupoId}/reemplazar")
    @RequierePermiso("ACTUALIZAR_HORARIOS")
    @Transactional
    public ResponseEntity<?> reemplazarHorarioGrupo(
            @PathVariable Long grupoId,
            @Valid @RequestBody List<HorarioBloqueRequest> requests) {
        Optional<Grupo> grupoOpt = grupoRepository.findById(grupoId);
        if (grupoOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("mensaje", "Grupo no encontrado."));
        }
        Grupo grupo = grupoOpt.get();
        if (grupo.getEstatus() != Grupo.EstatusGrupo.ACTIVO) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("mensaje", "Solo se pueden asignar horarios a grupos activos."));
        }
        String mensajePeriodoGrupo = gestionAcademicaEstadoService.validarEdicionHorario(grupo.getPeriodoAcademico());
        if (mensajePeriodoGrupo != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("mensaje", mensajePeriodoGrupo));
        }

        List<HorarioBloqueRequest> entrada = requests != null ? requests : List.of();
        List<HorarioBloque> activosGrupo = horarioBloqueRepository.findByGrupoEntity_IdAndEstatusOrderByDiaAscHoraInicioAsc(
                grupoId, HorarioBloque.EstatusHorario.ACTIVO);
        Map<Long, HorarioBloque> activosPorId = new LinkedHashMap<>();
        for (HorarioBloque h : activosGrupo) {
            if (h.getId() != null) {
                activosPorId.put(h.getId(), h);
            }
        }

        List<PreparedHorario> preparados = new ArrayList<>();
        Set<Long> idsRecibidos = new HashSet<>();
        for (int i = 0; i < entrada.size(); i++) {
            HorarioBloqueRequest req = entrada.get(i);
            int fila = i + 1;
            if (req == null) {
                continue;
            }
            if (req.getGrupoId() != null && !grupoId.equals(req.getGrupoId())) {
                return ResponseEntity.badRequest().body(Map.of("mensaje",
                        "Fila " + fila + ": el grupo del bloque no coincide con el grupo que se está editando."));
            }
            if (req.getProgramaId() == null || req.getDia() == null) {
                return ResponseEntity.badRequest().body(Map.of("mensaje",
                        "Fila " + fila + ": programa y día son obligatorios."));
            }
            if (req.getId() != null) {
                if (!idsRecibidos.add(req.getId())) {
                    return ResponseEntity.badRequest().body(Map.of("mensaje",
                            "Fila " + fila + ": el mismo bloque aparece más de una vez en la solicitud."));
                }
                if (!activosPorId.containsKey(req.getId())) {
                    return ResponseEntity.badRequest().body(Map.of("mensaje",
                            "Fila " + fila + ": el bloque indicado no pertenece al horario activo de este grupo."));
                }
            }

            ProgramaEducativo programa = programaEducativoRepository.findById(req.getProgramaId()).orElse(null);
            if (programa == null) {
                return ResponseEntity.badRequest().body(Map.of("mensaje", "Fila " + fila + ": programa no encontrado."));
            }
            Asignatura asignatura = resolverAsignatura(req);
            if (asignatura == null) {
                return ResponseEntity.badRequest().body(Map.of("mensaje", "Fila " + fila + ": asignatura no encontrada."));
            }
            Maestro maestro = null;
            if (req.getMaestroId() != null) {
                maestro = maestroRepository.findById(req.getMaestroId()).orElse(null);
                if (maestro == null) {
                    return ResponseEntity.badRequest().body(Map.of("mensaje", "Fila " + fila + ": maestro no encontrado."));
                }
            }
            PeriodoAcademico periodo = resolverPeriodoAcademico(req);
            String errGrupo = validarGrupoParaHorario(grupo, programa, asignatura, periodo);
            if (errGrupo != null) {
                return ResponseEntity.badRequest().body(Map.of("mensaje", "Fila " + fila + ": " + errGrupo));
            }
            LocalTime horaInicio;
            LocalTime horaFin;
            try {
                horaInicio = LocalTime.parse(normalizarHora(req.getHoraInicio()), TIME_FORMAT);
                horaFin = LocalTime.parse(normalizarHora(req.getHoraFin()), TIME_FORMAT);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("mensaje", "Fila " + fila + ": hora inválida."));
            }
            if (!horaFin.isAfter(horaInicio)) {
                return ResponseEntity.badRequest().body(Map.of("mensaje",
                        "Fila " + fila + ": la hora de fin debe ser posterior a la de inicio."));
            }

            PreparedHorario p = new PreparedHorario();
            p.id = req.getId();
            p.fila = fila;
            p.programa = programa;
            p.asignatura = asignatura;
            p.maestro = maestro;
            p.periodo = periodo;
            p.dia = req.getDia();
            p.horaInicio = horaInicio;
            p.horaFin = horaFin;
            p.fechaInicio = req.getFechaInicio();
            p.fechaFin = req.getFechaFin();
            p.aula = req.getAula();
            p.estatus = req.getEstatus() != null ? req.getEstatus() : HorarioBloque.EstatusHorario.ACTIVO;
            preparados.add(p);
        }

        for (int i = 0; i < preparados.size(); i++) {
            PreparedHorario actual = preparados.get(i);
            var cAula = horarioBloqueService.buscarConflictoAulaEnProgramaExcluyendoGrupo(
                    actual.programa.getId(), grupoId, actual.dia, actual.horaInicio, actual.horaFin,
                    actual.aula, actual.fechaInicio, actual.fechaFin, actual.id);
            if (cAula.isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("mensaje", horarioBloqueService.mensajeConflictoSalon(
                                nombreAsignaturaLegible(actual.asignatura), actual.dia, actual.horaInicio,
                                actual.horaFin, actual.aula, cAula.get())));
            }
            if (actual.maestro != null) {
                var cMaestro = horarioBloqueService.buscarConflictoMaestroEnProgramaExcluyendoGrupo(
                        actual.programa.getId(), grupoId, actual.maestro.getId(), actual.dia,
                        actual.horaInicio, actual.horaFin, actual.fechaInicio, actual.fechaFin, actual.id);
                if (cMaestro.isPresent()) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of("mensaje", horarioBloqueService.mensajeConflictoDocente(
                                    nombreAsignaturaLegible(actual.asignatura), actual.dia, actual.horaInicio,
                                    actual.horaFin, cMaestro.get())));
                }
            }
            for (int j = 0; j < i; j++) {
                PreparedHorario prev = preparados.get(j);
                if (actual.dia != prev.dia || !fechasSeSolapan(actual.fechaInicio, actual.fechaFin, prev.fechaInicio, prev.fechaFin)) {
                    continue;
                }
                if (horariosSeSolapan(actual.horaInicio, actual.horaFin, prev.horaInicio, prev.horaFin)) {
                    if (actual.maestro != null && prev.maestro != null && actual.maestro.getId().equals(prev.maestro.getId())) {
                        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensaje",
                                "Fila " + actual.fila + ": el mismo docente tiene dos bloques que se cruzan en esta solicitud."));
                    }
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensaje",
                            "Fila " + actual.fila + ": el grupo tiene dos materias con horario y fechas que se traslapan."));
                }
                if (actual.asignatura.getId().equals(prev.asignatura.getId())) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("mensaje",
                            "Fila " + actual.fila + ": la misma asignatura aparece dos veces el mismo día con fechas traslapadas."));
                }
            }
        }

        int cancelados = 0;
        for (HorarioBloque existente : activosGrupo) {
            if (existente.getId() != null && !idsRecibidos.contains(existente.getId())) {
                existente.setEstatus(HorarioBloque.EstatusHorario.CANCELADO);
                horarioBloqueRepository.save(existente);
                cancelados++;
            }
        }

        List<HorarioBloque> guardados = new ArrayList<>();
        for (PreparedHorario p : preparados) {
            HorarioBloque existente = p.id != null ? activosPorId.get(p.id) : null;
            boolean versionar = existente != null
                    && bloqueTieneHistorialEvaluacion(existente)
                    && requiereVersionadoHistorico(existente, p.programa, p.asignatura, p.maestro, grupo, p.periodo,
                    p.dia, p.horaInicio, p.horaFin, p.fechaInicio, p.fechaFin, p.aula);
            HorarioBloque bloque = versionar ? new HorarioBloque() : (existente != null ? existente : new HorarioBloque());
            if (versionar) {
                existente.setEstatus(HorarioBloque.EstatusHorario.CANCELADO);
                horarioBloqueRepository.save(existente);
            }
            bloque.setPrograma(p.programa);
            bloque.setAsignatura(p.asignatura);
            bloque.setMaestro(p.maestro);
            bloque.setDia(p.dia);
            bloque.setHoraInicio(p.horaInicio);
            bloque.setHoraFin(p.horaFin);
            bloque.setFechaInicio(p.fechaInicio);
            bloque.setFechaFin(p.fechaFin);
            bloque.setGrupoEntity(grupo);
            bloque.setPeriodoAcademico(p.periodo != null ? p.periodo : grupo.getPeriodoAcademico());
            bloque.setAula(p.aula);
            bloque.setEstatus(p.estatus);
            guardados.add(horarioBloqueRepository.save(bloque));
        }

        return ResponseEntity.ok(Map.of(
                "mensaje", "Horario actualizado",
                "guardados", guardados,
                "cancelados", cancelados));
    }

    /**
     * Eliminar bloques de un grupo. Sin {@code periodoAcademicoId}: todo el horario del grupo.
     * Con {@code periodoAcademicoId}: solo bloques de ese periodo (legacy).
     * <p>
     * Se usa {@code DELETE /horarios?grupoId=…} (sin segmento de ruta extra) para no confundir la ruta
     * con {@code DELETE /horarios/{id}} (un id numérico).
     */
    @DeleteMapping(params = "grupoId")
    @RequierePermiso("ACTUALIZAR_HORARIOS")
    @Transactional
    public ResponseEntity<?> eliminarPorGrupoQuery(
            @RequestParam Long grupoId,
            @RequestParam(required = false) Long periodoAcademicoId,
            @RequestParam(required = false, defaultValue = "false") boolean force) {
        Grupo grupo = grupoRepository.findById(grupoId).orElse(null);
        if (grupo != null) {
            String mensajePeriodo = gestionAcademicaEstadoService.validarEdicionHorario(grupo.getPeriodoAcademico());
            if (mensajePeriodo != null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("mensaje", mensajePeriodo));
            }
        }
        List<HorarioBloque> bloques = periodoAcademicoId != null
                ? horarioBloqueRepository.findByGrupoEntity_IdAndPeriodoAcademico_Id(grupoId, periodoAcademicoId)
                : horarioBloqueRepository.findByGrupoEntity_Id(grupoId);
        int n = 0;
        for (HorarioBloque b : bloques) {
            if (b == null || b.getId() == null) continue;
            // Soft delete: mantener histórico y referencias (evaluación docente, etc.)
            b.setEstatus(HorarioBloque.EstatusHorario.CANCELADO);
            horarioBloqueRepository.save(b);
            n++;
        }
        return ResponseEntity.ok(Map.of("mensaje", "Bloques desactivados", "desactivados", n, "force", force));
    }

    /**
     * Eliminar un bloque de horario por ID.
     */
    @DeleteMapping("/{id}")
    @RequierePermiso("ACTUALIZAR_HORARIOS")
    @Transactional
    public ResponseEntity<?> eliminar(@PathVariable Long id,
                                      @RequestParam(required = false, defaultValue = "false") boolean force) {
        Optional<HorarioBloque> opt = horarioBloqueRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        // Soft delete: mantener histórico y referencias
        HorarioBloque b = opt.get();
        String mensajePeriodo = gestionAcademicaEstadoService.validarEdicionHorario(
                b.getPeriodoAcademico() != null
                        ? b.getPeriodoAcademico()
                        : (b.getGrupoEntity() != null ? b.getGrupoEntity().getPeriodoAcademico() : null));
        if (mensajePeriodo != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("mensaje", mensajePeriodo));
        }
        b.setEstatus(HorarioBloque.EstatusHorario.CANCELADO);
        horarioBloqueRepository.save(b);
        return ResponseEntity.ok(Map.of("mensaje", "Bloque desactivado", "id", id, "force", force));
    }

    private static String nombreAsignaturaLegible(Asignatura a) {
        if (a == null) {
            return "Asignatura";
        }
        if (a.getNombre() != null && !a.getNombre().isBlank()) {
            return a.getNombre().trim();
        }
        if (a.getClave() != null && !a.getClave().isBlank()) {
            return a.getClave().trim();
        }
        if (a.getId() != null) {
            return "Asignatura id " + a.getId();
        }
        return "Asignatura";
    }

    /**
     * Valida que el grupo sea compatible con el horario (programa, asignatura, periodo).
     * @return mensaje de error o null si es válido
     */
    private String validarGrupoParaHorario(Grupo grupo, ProgramaEducativo programa,
                                           Asignatura asignatura, PeriodoAcademico periodo) {
        if (periodo != null && grupo.getPeriodoAcademico() != null && !grupo.getPeriodoAcademico().getId().equals(periodo.getId())) {
            return "El grupo debe pertenecer al mismo periodo académico seleccionado.";
        }
        if (grupo.getPrograma() != null) {
            if (!grupo.getPrograma().getId().equals(programa.getId())) {
                return "El grupo debe pertenecer al mismo programa seleccionado.";
            }
        } else if (grupo.getAsignatura() != null) {
            if (grupo.getAsignatura().getPrograma() == null || !grupo.getAsignatura().getPrograma().getId().equals(programa.getId())) {
                return "El grupo debe pertenecer al mismo programa seleccionado.";
            }
            if (!grupo.getAsignatura().getId().equals(asignatura.getId())) {
                return "El grupo debe corresponder a la asignatura seleccionada.";
            }
        } else {
            return "El grupo no tiene programa ni asignatura asignados.";
        }
        if (grupo.getEstatus() != Grupo.EstatusGrupo.ACTIVO) {
            return "Solo se pueden asignar horarios a grupos activos.";
        }
        return null;
    }

    private String resolverGrupoNombre(HorarioBloqueRequest request) {
        if (request.getGrupoId() != null) {
            return grupoRepository.findById(request.getGrupoId())
                    .map(Grupo::getNombre)
                    .orElse(request.getGrupo());
        }
        return request.getGrupo();
    }

    private void asignarGrupo(HorarioBloque bloque, HorarioBloqueRequest request) {
        if (request.getGrupoId() != null) {
            grupoRepository.findById(request.getGrupoId()).ifPresent(bloque::setGrupoEntity);
        }
        if (bloque.getGrupoEntity() == null && request.getGrupo() != null && !request.getGrupo().isBlank()) {
            bloque.setGrupo(request.getGrupo());
        }
    }

    /**
     * Resuelve la asignatura desde asignaturaId o idAsignatura (identificador de negocio).
     * Prioridad: asignaturaId si está presente; si no, idAsignatura + programaId.
     * Si idAsignatura no encuentra, intenta por clave como fallback.
     */
    private Asignatura resolverAsignatura(HorarioBloqueRequest request) {
        if (request.getAsignaturaId() != null) {
            return asignaturaRepository.findById(request.getAsignaturaId()).orElse(null);
        }
        if (request.getIdAsignatura() != null && !request.getIdAsignatura().isBlank()
                && request.getProgramaId() != null) {
            String idAsig = request.getIdAsignatura().trim();
            return asignaturaRepository.findByProgramaIdAndIdAsignatura(request.getProgramaId(), idAsig)
                    .or(() -> asignaturaRepository.findByProgramaIdAndClave(request.getProgramaId(), idAsig))
                    .orElse(null);
        }
        return null;
    }

    private PeriodoAcademico resolverPeriodoAcademico(HorarioBloqueRequest request) {
        if (request.getPeriodoAcademicoId() != null) {
            return periodoAcademicoService.findById(request.getPeriodoAcademicoId()).orElse(null);
        }
        if (request.getPeriodoIngreso() != null && !request.getPeriodoIngreso().isBlank()) {
            ProgramaEducativo.TipoPeriodo tipo = null;
            try {
                if (request.getProgramaId() != null) {
                    var progOpt = programaEducativoRepository.findById(request.getProgramaId());
                    if (progOpt.isPresent()) {
                        tipo = progOpt.get().getTipoPeriodo();
                    }
                }
            } catch (Exception ignored) {}
            return periodoAcademicoService.asegurarPeriodo(request.getPeriodoIngreso().trim(), tipo);
        }
        return null;
    }

    /** Asegura formato HH:mm (acepta HH:mm o HH:mm:ss del frontend) */
    private String normalizarHora(String hora) {
        if (hora == null || hora.isBlank()) return "08:00";
        String s = hora.trim();
        if (s.length() > 5) s = s.substring(0, 5);
        return s;
    }

    /** Dos intervalos [a1,a2] y [b1,b2] se solapan si a1 < b2 y b1 < a2 */
    private boolean horariosSeSolapan(LocalTime a1, LocalTime a2, LocalTime b1, LocalTime b2) {
        return a1.isBefore(b2) && b1.isBefore(a2);
    }

    /** Dos rangos de fechas [aIni,aFin] y [bIni,bFin] se solapan; null = sin límite. */
    private boolean fechasSeSolapan(LocalDate aIni, LocalDate aFin, LocalDate bIni, LocalDate bFin) {
        LocalDate ai = aIni != null ? aIni : LocalDate.MIN;
        LocalDate af = aFin != null ? aFin : LocalDate.MAX;
        LocalDate bi = bIni != null ? bIni : LocalDate.MIN;
        LocalDate bf = bFin != null ? bFin : LocalDate.MAX;
        return !ai.isAfter(bf) && !bi.isAfter(af);
    }

    private PeriodoAcademico periodoOperacionHorario(PeriodoAcademico periodoRequest, Grupo grupo) {
        if (periodoRequest != null) {
            return periodoRequest;
        }
        return grupo != null ? grupo.getPeriodoAcademico() : null;
    }

    private boolean bloqueTieneHistorialEvaluacion(HorarioBloque bloque) {
        return bloque != null
                && bloque.getId() != null
                && evaluacionDocenteRespuestaRepository.existsByHorarioBloque_Id(bloque.getId());
    }

    private boolean requiereVersionadoHistorico(HorarioBloque actual,
                                                ProgramaEducativo programa,
                                                Asignatura asignatura,
                                                Maestro maestro,
                                                Grupo grupo,
                                                PeriodoAcademico periodo,
                                                HorarioBloque.DiaSemana dia,
                                                LocalTime horaInicio,
                                                LocalTime horaFin,
                                                LocalDate fechaInicio,
                                                LocalDate fechaFin,
                                                String aula) {
        if (actual == null) {
            return false;
        }
        Long programaId = programa != null ? programa.getId() : null;
        Long asignaturaId = asignatura != null ? asignatura.getId() : null;
        Long maestroId = maestro != null ? maestro.getId() : null;
        Long grupoId = grupo != null ? grupo.getId() : null;
        Long periodoId = periodo != null ? periodo.getId()
                : (grupo != null && grupo.getPeriodoAcademico() != null ? grupo.getPeriodoAcademico().getId() : null);
        Long actualProgramaId = actual.getPrograma() != null ? actual.getPrograma().getId() : null;
        Long actualAsignaturaId = actual.getAsignatura() != null ? actual.getAsignatura().getId() : null;
        Long actualMaestroId = actual.getMaestro() != null ? actual.getMaestro().getId() : null;
        Long actualGrupoId = actual.getGrupoEntity() != null ? actual.getGrupoEntity().getId() : null;
        Long actualPeriodoId = actual.getPeriodoAcademico() != null ? actual.getPeriodoAcademico().getId() : null;
        return !Objects.equals(actualProgramaId, programaId)
                || !Objects.equals(actualAsignaturaId, asignaturaId)
                || !Objects.equals(actualMaestroId, maestroId)
                || !Objects.equals(actualGrupoId, grupoId)
                || !Objects.equals(actualPeriodoId, periodoId)
                || actual.getDia() != dia
                || !Objects.equals(actual.getHoraInicio(), horaInicio)
                || !Objects.equals(actual.getHoraFin(), horaFin)
                || !Objects.equals(actual.getFechaInicio(), fechaInicio)
                || !Objects.equals(actual.getFechaFin(), fechaFin)
                || !Objects.equals(normalizarAula(actual.getAula()), normalizarAula(aula));
    }

    private String normalizarAula(String aula) {
        return aula == null ? "" : aula.trim();
    }

    private static class PreparedHorario {
        Long id;
        int fila;
        ProgramaEducativo programa;
        Asignatura asignatura;
        Maestro maestro;
        PeriodoAcademico periodo;
        HorarioBloque.DiaSemana dia;
        LocalTime horaInicio;
        LocalTime horaFin;
        LocalDate fechaInicio;
        LocalDate fechaFin;
        String aula;
        HorarioBloque.EstatusHorario estatus;
    }

    /**
     * Crear múltiples bloques de horario en una sola petición.
     */
    @PostMapping("/batch")
    @RequierePermiso("ACTUALIZAR_HORARIOS")
    public ResponseEntity<?> crearBatch(@Valid @RequestBody List<HorarioBloqueRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("mensaje", "Se requiere al menos un bloque."));
        }
        try {
            List<HorarioBloque> guardados = new java.util.ArrayList<>();
            for (int i = 0; i < requests.size(); i++) {
                HorarioBloqueRequest req = requests.get(i);
                final int fila = i + 1;
                ProgramaEducativo programa = programaEducativoRepository.findById(req.getProgramaId()).orElse(null);
                if (programa == null) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("mensaje", "Fila " + fila + " (sin asignatura aún): programa no encontrado."));
                }
                Asignatura asignatura = resolverAsignatura(req);
                if (asignatura == null) {
                    if (req.getAsignaturaId() == null && (req.getIdAsignatura() == null || req.getIdAsignatura().isBlank())) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(Map.of("mensaje", "Fila " + fila + ": debes indicar la asignatura (asignaturaId o idAsignatura)."));
                    }
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("mensaje", "Fila " + fila + ": asignatura no encontrada."));
                }
                final String etiquetaAsig = "«" + nombreAsignaturaLegible(asignatura) + "»";
                Maestro maestro = null;
                if (req.getMaestroId() != null) {
                    maestro = maestroRepository.findById(req.getMaestroId()).orElse(null);
                    if (maestro == null) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(Map.of("mensaje", etiquetaAsig + " (fila " + fila + "): maestro no encontrado."));
                    }
                }
                PeriodoAcademico periodo = resolverPeriodoAcademico(req);
                if (req.getGrupoId() == null) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("mensaje", etiquetaAsig + " (fila " + fila + "): debes seleccionar un grupo."));
                }
                Optional<Grupo> grupoOpt = grupoRepository.findById(req.getGrupoId());
                if (grupoOpt.isEmpty()) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("mensaje", etiquetaAsig + " (fila " + fila + "): grupo no encontrado."));
                }
                Grupo grupo = grupoOpt.get();
                String mensajePeriodo = gestionAcademicaEstadoService.validarEdicionHorario(periodoOperacionHorario(periodo, grupo));
                if (mensajePeriodo != null) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("mensaje", etiquetaAsig + " (fila " + fila + "): " + mensajePeriodo));
                }
                String errGrupo = validarGrupoParaHorario(grupo, programa, asignatura, periodo);
                if (errGrupo != null) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("mensaje", etiquetaAsig + " (fila " + fila + "): " + errGrupo));
                }
                String hIni = normalizarHora(req.getHoraInicio());
                String hFin = normalizarHora(req.getHoraFin());
                LocalTime horaInicio = LocalTime.parse(hIni, TIME_FORMAT);
                LocalTime horaFin = LocalTime.parse(hFin, TIME_FORMAT);
                if (!horaFin.isAfter(horaInicio)) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of("mensaje", etiquetaAsig + " (fila " + fila + "): la hora de fin debe ser posterior a la de inicio."));
                }
                var cAulaBatch = horarioBloqueService.buscarConflictoAulaEnPrograma(
                        req.getProgramaId(),
                        req.getDia(), horaInicio, horaFin, req.getAula(), req.getFechaInicio(), req.getFechaFin(), null);
                if (cAulaBatch.isPresent()) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of("mensaje", horarioBloqueService.mensajeConflictoSalon(
                                    nombreAsignaturaLegible(asignatura), req.getDia(), horaInicio, horaFin,
                                    req.getAula(), cAulaBatch.get())));
                }
                if (maestro != null) {
                    var cMaeBatch = horarioBloqueService.buscarConflictoMaestroEnPrograma(
                            req.getProgramaId(),
                            req.getMaestroId(), req.getDia(), horaInicio, horaFin,
                            req.getFechaInicio(), req.getFechaFin(), null);
                    if (cMaeBatch.isPresent()) {
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(Map.of("mensaje", horarioBloqueService.mensajeConflictoDocente(
                                        nombreAsignaturaLegible(asignatura), req.getDia(), horaInicio, horaFin,
                                        cMaeBatch.get())));
                    }
                }
                String gNombre = resolverGrupoNombre(req);
                if (gNombre != null && !gNombre.isBlank()) {
                    var dupBatch = horarioBloqueService.buscarBloqueMismaAsignaturaGrupoDiaEnPrograma(
                            req.getProgramaId(),
                            asignatura.getId(), gNombre, req.getDia(), req.getFechaInicio(), req.getFechaFin(), null);
                    if (dupBatch.isPresent()) {
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(Map.of("mensaje", horarioBloqueService.mensajeDuplicadoMismaMateriaGrupoDia(
                                        nombreAsignaturaLegible(asignatura), gNombre, req.getDia(), dupBatch.get())));
                    }
                }
                for (int k = 0; k < i; k++) {
                    HorarioBloqueRequest prev = requests.get(k);
                    boolean mismoPrograma = prev.getProgramaId() != null && req.getProgramaId() != null
                            && prev.getProgramaId().equals(req.getProgramaId());
                    if (mismoPrograma
                            && prev.getMaestroId() != null && req.getMaestroId() != null
                            && prev.getMaestroId().equals(req.getMaestroId()) && prev.getDia() == req.getDia()) {
                        String pHIni = normalizarHora(prev.getHoraInicio());
                        String pHFin = normalizarHora(prev.getHoraFin());
                        LocalTime pHoraInicio = LocalTime.parse(pHIni, TIME_FORMAT);
                        LocalTime pHoraFin = LocalTime.parse(pHFin, TIME_FORMAT);
                        if (fechasSeSolapan(req.getFechaInicio(), req.getFechaFin(), prev.getFechaInicio(), prev.getFechaFin())
                                && horariosSeSolapan(horaInicio, horaFin, pHoraInicio, pHoraFin)) {
                            Asignatura prevAsigSol = resolverAsignatura(prev);
                            String nomPrev = prevAsigSol != null ? nombreAsignaturaLegible(prevAsigSol) : "otra asignatura";
                            return ResponseEntity.status(HttpStatus.CONFLICT)
                                    .body(Map.of("mensaje", etiquetaAsig + ": el mismo docente ya está en «" + nomPrev
                                            + "» el " + (req.getDia() != null ? req.getDia().getNombre() : "—")
                                            + " con horario que se cruza con este bloque. Cambia docente u horas en una de las dos."));
                        }
                    }
                    String prevGNombre = resolverGrupoNombre(prev);
                    Asignatura prevAsig = resolverAsignatura(prev);
                    if (mismoPrograma && gNombre != null && gNombre.equals(prevGNombre)
                            && prevAsig != null && asignatura.getId().equals(prevAsig.getId()) && req.getDia() == prev.getDia()) {
                        if (fechasSeSolapan(req.getFechaInicio(), req.getFechaFin(), prev.getFechaInicio(), prev.getFechaFin())) {
                            return ResponseEntity.status(HttpStatus.CONFLICT)
                                    .body(Map.of("mensaje", etiquetaAsig + ": en esta misma solicitud hay dos sesiones el mismo día para el mismo grupo; deja solo una o cambia el día de una."));
                        }
                    }
                }
                HorarioBloque bloque = new HorarioBloque();
                bloque.setPrograma(programa);
                bloque.setAsignatura(asignatura);
                bloque.setMaestro(maestro);
                bloque.setDia(req.getDia());
                bloque.setHoraInicio(horaInicio);
                bloque.setHoraFin(horaFin);
                bloque.setFechaInicio(req.getFechaInicio());
                bloque.setFechaFin(req.getFechaFin());
                asignarGrupo(bloque, req);
                bloque.setPeriodoAcademico(periodo != null ? periodo : grupo.getPeriodoAcademico());
                bloque.setAula(req.getAula());
                bloque.setEstatus(req.getEstatus() != null ? req.getEstatus() : HorarioBloque.EstatusHorario.ACTIVO);
                guardados.add(horarioBloqueRepository.save(bloque));
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(guardados);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("mensaje", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al crear bloques: " + e.getMessage());
        }
    }
}
