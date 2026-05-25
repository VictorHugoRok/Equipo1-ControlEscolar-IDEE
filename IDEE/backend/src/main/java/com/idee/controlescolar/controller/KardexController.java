package com.idee.controlescolar.controller;

import com.idee.controlescolar.model.*;
import com.idee.controlescolar.repository.*;
import com.idee.controlescolar.security.RequierePermiso;
import com.idee.controlescolar.service.EvaluacionDocenteService;
import com.idee.controlescolar.service.PeriodoAcademicoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Controlador para consulta de kardex (historial académico) de alumnos.
 */
@RestController
@RequestMapping("/kardex")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class KardexController {

    private static final double CALIF_MINIMA_APROBATORIA = 7.0;
    private static final String[] ORDINALES = {"", "1er", "2do", "3er", "4to", "5to", "6to", "7mo", "8vo", "9no", "10mo"};

    private final AlumnoRepository alumnoRepository;
    private final CalificacionRepository calificacionRepository;
    private final AsignaturaRepository asignaturaRepository;
    private final GrupoRepository grupoRepository;
    private final ProgramaEducativoRepository programaRepository;
    private final PeriodoAcademicoService periodoAcademicoService;
    private final EvaluacionDocenteService evaluacionDocenteService;

    /**
     * Busca alumnos por criterio (nombre, matrícula, CURP) con filtros opcionales.
     */
    @GetMapping("/buscar")
    @RequierePermiso("VER_ALUMNOS")
    public ResponseEntity<List<Map<String, Object>>> buscar(
            @RequestParam(required = false) String criterio,
            @RequestParam(required = false) Long programaId,
            @RequestParam(required = false) Long grupoId,
            @RequestParam(required = false) String periodoInicio) {

        List<Alumno> alumnos;
        if (criterio != null && !criterio.trim().isEmpty()) {
            String c = criterio.trim();
            if (c.length() == 18 && c.matches("[A-Za-z0-9]+")) {
                alumnos = alumnoRepository.findByCurp(c).map(List::of).orElse(List.of());
            } else if (c.matches("[A-Za-z0-9_-]+") && c.length() <= 30) {
                alumnos = alumnoRepository.findByMatricula(c).map(List::of).orElse(List.of());
            } else {
                alumnos = alumnoRepository.buscarPorCriterio(c);
            }
        } else {
            alumnos = alumnoRepository.findAll();
        }

        if (programaId != null) {
            alumnos = alumnos.stream()
                    .filter(a -> a.getPrograma() != null && programaId.equals(a.getPrograma().getId()))
                    .collect(Collectors.toList());
        }
        if (grupoId != null) {
            Optional<Grupo> grupoOpt = grupoRepository.findById(grupoId);
            if (grupoOpt.isPresent()) {
                Set<Long> idsAlumnos = grupoOpt.get().getAlumnos().stream().map(Alumno::getId).collect(Collectors.toSet());
                alumnos = alumnos.stream().filter(a -> idsAlumnos.contains(a.getId())).collect(Collectors.toList());
            }
        }
        if (periodoInicio != null && !periodoInicio.isBlank()) {
            String p = periodoInicio.trim();
            List<Long> idsPeriodoIngreso = calificacionRepository.findAlumnoIdsByPeriodoIngreso(p);
            Set<Long> idsSet = new java.util.HashSet<>(idsPeriodoIngreso);
            alumnos = alumnos.stream()
                    .filter(a -> idsSet.contains(a.getId()))
                    .collect(Collectors.toList());
        }

        List<Map<String, Object>> resultado = alumnos.stream()
                .map(this::aMapaResumen)
                .collect(Collectors.toList());

        return ResponseEntity.ok(resultado);
    }

    /**
     * Kardex del alumno vinculado al usuario autenticado (solo rol ALUMNO).
     */
    @GetMapping("/mi-kardex")
    @RequierePermiso("VER_KARDEX_PROPIO")
    public ResponseEntity<?> obtenerKardexPropio(Authentication authentication,
                                                 @RequestParam(required = false) Long programaId) {
        Long alumnoId = resolverAlumnoIdDesdeAutenticacion(authentication);
        if (alumnoId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Tu cuenta no está vinculada a un expediente de alumno. Contacta a control escolar."));
        }
        return construirRespuestaKardex(alumnoId, programaId);
    }

    /**
     * Historial de calificaciones del alumno autenticado.
     */
    @GetMapping("/mi-kardex/historial-calificaciones")
    @RequierePermiso("VER_KARDEX_PROPIO")
    public ResponseEntity<?> historialCalificacionesPropio(Authentication authentication,
                                                           @RequestParam(required = false) Long programaId) {
        Long alumnoId = resolverAlumnoIdDesdeAutenticacion(authentication);
        if (alumnoId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Tu cuenta no está vinculada a un expediente de alumno."));
        }
        Alumno alumno = alumnoRepository.findById(alumnoId).orElse(null);
        if (alumno == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Alumno no encontrado"));
        }
        return construirHistorialCalificacionesParaAlumnoPropio(alumno, programaId);
    }

    /**
     * Asignaturas del plan del programa del alumno autenticado (para matriz gráfica del kardex).
     */
    @GetMapping("/mi-kardex/asignaturas-plan")
    @RequierePermiso("VER_KARDEX_PROPIO")
    public ResponseEntity<?> asignaturasPlanPropio(Authentication authentication,
                                                   @RequestParam(required = false) Long programaId) {
        Long alumnoId = resolverAlumnoIdDesdeAutenticacion(authentication);
        if (alumnoId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Tu cuenta no está vinculada a un expediente de alumno."));
        }
        Alumno alumno = alumnoRepository.findByIdConProgramasInscripcion(alumnoId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumno no encontrado"));
        AlumnoPrograma ap = resolverInscripcionPrograma(alumno, programaId);
        ProgramaEducativo programa = ap != null ? ap.getPrograma() : alumno.getPrograma();
        if (programa == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "El alumno no tiene programa asignado"));
        }
        List<Asignatura> plan = asignaturaRepository.findByProgramaIdWithPeriodoYPrograma(programa.getId());
        Asignatura.ordenarListaPorIdAsignatura(plan);
        return ResponseEntity.ok(plan);
    }

    private Long resolverAlumnoIdDesdeAutenticacion(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Usuario usuario)) {
            return null;
        }
        return alumnoRepository.findByUsuarioId(usuario.getId())
                .map(Alumno::getId)
                .orElse(null);
    }

    /**
     * Obtiene el kardex completo de un alumno (personal administrativo).
     */
    @GetMapping("/{alumnoId:\\d+}")
    @RequierePermiso("VER_ALUMNOS")
    public ResponseEntity<?> obtenerKardex(@PathVariable Long alumnoId,
                                          @RequestParam(required = false) Long programaId) {
        return construirRespuestaKardex(alumnoId, programaId);
    }

    private AlumnoPrograma resolverInscripcionPrograma(Alumno alumno, Long programaId) {
        if (alumno == null) {
            return null;
        }
        if (programaId == null) {
            // default: primera inscripción si existe
            return (alumno.getProgramasAsignados() != null)
                    ? alumno.getProgramasAsignados().stream().filter(Objects::nonNull).findFirst().orElse(null)
                    : null;
        }
        if (alumno.getProgramasAsignados() == null) {
            return null;
        }
        return alumno.getProgramasAsignados().stream()
                .filter(Objects::nonNull)
                .filter(ap -> ap.getPrograma() != null && ap.getPrograma().getId() != null && programaId.equals(ap.getPrograma().getId()))
                .findFirst()
                .orElse(null);
    }

    private ResponseEntity<?> construirRespuestaKardex(Long alumnoId, Long programaId) {
        try {
            Alumno alumno = alumnoRepository.findByIdConProgramasInscripcion(alumnoId)
                    .orElseThrow(() -> new RuntimeException("Alumno no encontrado"));

            AlumnoPrograma ap = resolverInscripcionPrograma(alumno, programaId);
            ProgramaEducativo programa = ap != null ? ap.getPrograma() : alumno.getPrograma();
            if (programa == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "El alumno no tiene programa asignado"));
            }

            List<Asignatura> asignaturasPlan = new ArrayList<>(asignaturaRepository.findByProgramaId(programa.getId()));
            Asignatura.ordenarListaPorIdAsignatura(asignaturasPlan);
            List<Asignatura> asignaturasQueCuentan = asignaturasPlan.stream()
                    .filter(Asignatura::cuentaEnPlanAcademico)
                    .collect(Collectors.toList());

            int materiasTotales = asignaturasQueCuentan.size();
            int creditosTotales = asignaturasQueCuentan.stream()
                    .mapToInt(a -> a.getCreditos() != null ? a.getCreditos() : 0)
                    .sum();
            if (creditosTotales <= 0 && programa.getCreditosTotales() != null) {
                creditosTotales = programa.getCreditosTotales();
            }

            List<Calificacion> calificaciones = calificacionRepository.findByAlumnoId(alumno.getId());
            List<Calificacion> califPrograma = calificaciones.stream()
                    .filter(c -> c.getAsignatura() != null && c.getAsignatura().getPrograma() != null
                            && c.getAsignatura().getPrograma().getId().equals(programa.getId()))
                    .filter(c -> c.getAsignatura().cuentaEnPlanAcademico())
                    .collect(Collectors.toList());

            List<Calificacion> califAprobadas = califPrograma.stream()
                    .filter(c -> esAprobatoria(c))
                    .collect(Collectors.toList());

            int materiasAprobadas = califAprobadas.size();
            int creditosAprobados = califAprobadas.stream()
                    .mapToInt(c -> c.getAsignatura().getCreditos() != null ? c.getAsignatura().getCreditos() : 0)
                    .sum();

            double promedioGeneral = 0;
            if (!califPrograma.isEmpty()) {
                double suma = califPrograma.stream()
                        .mapToDouble(c -> c.getCalificacionFinal() != null ? c.getCalificacionFinal() : 0)
                        .sum();
                promedioGeneral = suma / califPrograma.size();
            }

            boolean esEgresado = (ap != null && ap.getEstatusMatricula() == AlumnoPrograma.EstatusMatriculaPrograma.EGRESADO)
                    || (ap == null && alumno.getEstatusMatricula() == Alumno.EstatusMatricula.EGRESADO);

            // Para egresados (carga histórica), el periodoCursando suele quedarse en 1 aunque existan calificaciones
            // en periodos posteriores. Para que el kardex refleje correctamente el avance, usamos el máximo periodo
            // del plan encontrado en las calificaciones (o, si no hay, la duración del programa).
            Integer maxPeriodoPorCalifs = califPrograma.stream()
                    .map(c -> c.getAsignatura() != null && c.getAsignatura().getPeriodo() != null
                            ? c.getAsignatura().getPeriodo().getNumero()
                            : null)
                    .filter(Objects::nonNull)
                    .max(Integer::compareTo)
                    .orElse(null);
            Integer duracionPlan = programa.getDuracionPeriodos();

            Integer periodoCursandoEfectivo = (ap != null ? ap.getPeriodoCursando() : alumno.getPeriodoCursando());
            if (esEgresado) {
                if (maxPeriodoPorCalifs != null) {
                    if (periodoCursandoEfectivo == null || periodoCursandoEfectivo < maxPeriodoPorCalifs) {
                        periodoCursandoEfectivo = maxPeriodoPorCalifs;
                    }
                } else if (duracionPlan != null && duracionPlan > 0) {
                    if (periodoCursandoEfectivo == null || periodoCursandoEfectivo < duracionPlan) {
                        periodoCursandoEfectivo = duracionPlan;
                    }
                }
            }

            String periodoActualNum = formatearPeriodoOrdinal(periodoCursandoEfectivo, programa.getTipoPeriodo());

            String periodoIngresoBase = null;
            if (ap != null && ap.getPeriodoIngreso() != null && ap.getPeriodoIngreso().getCodigo() != null) {
                periodoIngresoBase = ap.getPeriodoIngreso().getCodigo();
            } else {
                periodoIngresoBase = alumno.getPeriodoIngreso();
            }
            String periodoIngreso = obtenerPeriodoIngreso(califPrograma, periodoIngresoBase);

            String periodoEgreso;
            if (esEgresado) {
                periodoEgreso = null;
                Integer dur = programa.getDuracionPeriodos();
                String ingresoCod = periodoIngresoBase;
                if (dur != null && dur > 0 && ingresoCod != null && !ingresoCod.isBlank()) {
                    periodoEgreso = PeriodoAcademicoService.codigoPeriodoDelNivelDelPlan(
                            ingresoCod.trim(), programa.getTipoPeriodo(), dur).orElse(null);
                }
                if (periodoEgreso == null) {
                    periodoEgreso = obtenerPeriodoEgreso(califPrograma);
                }
            } else {
                periodoEgreso = "En curso";
            }

            String codigoEscolarNivel = null;
            String periodoActual = null;
            if (!esEgresado) {
                codigoEscolarNivel = (ap != null && ap.getPeriodoAcademicoActual() != null && ap.getPeriodoAcademicoActual().getCodigo() != null)
                        ? ap.getPeriodoAcademicoActual().getCodigo()
                        : (alumno.getPeriodoAcademicoActual() != null && alumno.getPeriodoAcademicoActual().getCodigo() != null
                        ? alumno.getPeriodoAcademicoActual().getCodigo()
                        : PeriodoAcademicoService.codigoPeriodoDelNivelDelPlan(
                                periodoIngresoBase != null ? periodoIngresoBase.trim() : "",
                                programa.getTipoPeriodo(),
                                periodoCursandoEfectivo != null ? periodoCursandoEfectivo : 1).orElse(null)
                );

                periodoActual = (codigoEscolarNivel != null && !codigoEscolarNivel.isBlank())
                        ? codigoEscolarNivel + " — " + periodoActualNum
                        : ((periodoIngresoBase != null ? periodoIngresoBase + " — " : "") + periodoActualNum);
            }

            double promedioPeriodo = calcularPromedioPeriodoActual(califPrograma, codigoEscolarNivel);

            double porcentajeAprobado = creditosTotales > 0 ? (100.0 * creditosAprobados / creditosTotales) : 0;

            Map<String, Object> kardex = new LinkedHashMap<>();
            kardex.put("matricula", alumno.getMatricula());
            kardex.put("nombre", alumno.getNombreCompleto());
            kardex.put("curp", alumno.getCurp());
            kardex.put("programaId", programa.getId());
            kardex.put("periodoActual", periodoActual);
            kardex.put("periodoActualNum", periodoActualNum);
            kardex.put("periodoAcademicoActualCodigo", codigoEscolarNivel != null ? codigoEscolarNivel : "—");
            kardex.put("programaEstudios", programa.getNombre());
            kardex.put("modalidad", programa.getModalidad() != null ? programa.getModalidad().name() : "—");
            kardex.put("planEstudios", programa.getClave() != null ? programa.getClave() : "—");
            kardex.put("periodoIngreso", periodoIngreso);
            kardex.put("periodoEgreso", periodoEgreso);
            String estMat = (ap != null && ap.getEstatusMatricula() != null)
                    ? ap.getEstatusMatricula().name()
                    : (alumno.getEstatusMatricula() != null ? alumno.getEstatusMatricula().name() : "—");
            kardex.put("situacionEstatus", estMat);
            kardex.put("creditosPlan", creditosTotales);
            kardex.put("creditosAprobados", creditosAprobados);
            kardex.put("porcentajeAprobado", Math.round(porcentajeAprobado * 100) / 100.0);
            kardex.put("materiasTotales", materiasTotales);
            kardex.put("materiasAprobadas", materiasAprobadas);
            kardex.put("promedioGeneral", Math.round(promedioGeneral * 100) / 100.0);
            kardex.put("promedioPeriodo", Math.round(promedioPeriodo * 100) / 100.0);
            kardex.put("periodoCursando", periodoCursandoEfectivo);
            kardex.put("duracionPeriodos", programa.getDuracionPeriodos());
            kardex.put("tipoPeriodo", programa.getTipoPeriodo() != null ? programa.getTipoPeriodo().name() : null);

            return ResponseEntity.ok(kardex);
        } catch (Exception e) {
            log.error("Error al obtener kardex: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Clave de ordenación del periodo institucional (año + número dentro del año).
     * Sirve para semestres (2025-1, 2025-2), cuatrimestres (2025-1…4), trimestres, etc.
     */
    private record ClavePeriodoInstitucional(int anio, int numeroEnAnio, long fechaInicioEpoch) implements Comparable<ClavePeriodoInstitucional> {
        static final ClavePeriodoInstitucional AL_FINAL = new ClavePeriodoInstitucional(Integer.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE);

        @Override
        public int compareTo(ClavePeriodoInstitucional o) {
            int c = Integer.compare(anio, o.anio);
            if (c != 0) return c;
            c = Integer.compare(numeroEnAnio, o.numeroEnAnio);
            if (c != 0) return c;
            return Long.compare(fechaInicioEpoch, o.fechaInicioEpoch);
        }
    }

    private static ClavePeriodoInstitucional clavePeriodoDesdeCalificacion(Calificacion c) {
        PeriodoAcademico pa = c.getPeriodoAcademico();
        if (pa != null) {
            int anio = pa.getAnio() != null ? pa.getAnio() : 0;
            int num = pa.getNumero() != null ? pa.getNumero() : 0;
            long epoch = pa.getFechaInicio() != null ? pa.getFechaInicio().toEpochDay() : 0L;
            return new ClavePeriodoInstitucional(anio, num, epoch);
        }
        return parseCodigoPeriodoAcademico(c.getPeriodo())
                .map(arr -> new ClavePeriodoInstitucional(arr[0], arr[1], 0L))
                .orElse(ClavePeriodoInstitucional.AL_FINAL);
    }

    /**
     * Espera códigos como {@code 2025-1}, {@code 2025-2}, etc. (cualquier cantidad de ciclos por año).
     */
    private static Optional<int[]> parseCodigoPeriodoAcademico(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        String s = codigo.trim();
        int dash = s.indexOf('-');
        if (dash <= 0 || dash >= s.length() - 1) {
            return Optional.empty();
        }
        try {
            int anio = Integer.parseInt(s.substring(0, dash).trim());
            String rest = s.substring(dash + 1).trim();
            int end = 0;
            while (end < rest.length() && Character.isDigit(rest.charAt(end))) {
                end++;
            }
            if (end == 0) {
                return Optional.empty();
            }
            int num = Integer.parseInt(rest.substring(0, end));
            return Optional.of(new int[]{anio, num});
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static int numeroPeriodoPlan(Calificacion c) {
        if (c.getAsignatura() == null || c.getAsignatura().getPeriodo() == null) {
            return Integer.MAX_VALUE;
        }
        Integer n = c.getAsignatura().getPeriodo().getNumero();
        return n != null ? n : Integer.MAX_VALUE;
    }

    private static String nombreAsignatura(Calificacion c) {
        if (c.getAsignatura() == null || c.getAsignatura().getNombre() == null) {
            return "";
        }
        return c.getAsignatura().getNombre();
    }

    /**
     * Historial de calificaciones del alumno (consulta).
     * Orden cronológico por periodo académico institucional (año + número de ciclo: semestre, cuatrimestre, etc.),
     * luego por periodo del plan de estudios y nombre de asignatura.
     */
    @GetMapping("/{alumnoId:\\d+}/historial-calificaciones")
    @RequierePermiso("VER_ALUMNOS")
    public ResponseEntity<?> historialCalificaciones(@PathVariable Long alumnoId,
                                                     @RequestParam(required = false) Long programaId) {
        return construirHistorialCalificaciones(alumnoId, programaId);
    }

    private ResponseEntity<?> construirHistorialCalificaciones(Long alumnoId, Long programaId) {
        if (!alumnoRepository.existsById(alumnoId)) {
            return ResponseEntity.notFound().build();
        }
        List<Calificacion> lista = calificacionRepository.findByAlumnoIdForKardexHistorial(alumnoId);
        if (lista == null) {
            lista = List.of();
        }

        Comparator<Calificacion> ordenHistorial = Comparator
                .comparing(KardexController::clavePeriodoDesdeCalificacion)
                .thenComparingInt(KardexController::numeroPeriodoPlan)
                .thenComparing(KardexController::nombreAsignatura, String.CASE_INSENSITIVE_ORDER);

        List<Map<String, Object>> filas = lista.stream()
                .filter(c -> c.getAsignatura() != null && c.getAsignatura().cuentaEnPlanAcademico())
                .filter(c -> {
                    if (programaId == null) return true;
                    return c.getAsignatura() != null
                            && c.getAsignatura().getPrograma() != null
                            && c.getAsignatura().getPrograma().getId() != null
                            && programaId.equals(c.getAsignatura().getPrograma().getId());
                })
                .sorted(ordenHistorial)
                .map(c -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", c.getId());
                    row.put("periodo", c.getPeriodoDisplay() != null ? c.getPeriodoDisplay() : "—");
                    Asignatura asig = c.getAsignatura();
                    row.put("asignaturaClave", asig != null && asig.getClave() != null ? asig.getClave() : "");
                    row.put("asignaturaNombre", asig != null && asig.getNombre() != null ? asig.getNombre() : "—");
                    Integer numPeriodoAsig = null;
                    if (asig != null && asig.getPeriodo() != null) {
                        numPeriodoAsig = asig.getPeriodo().getNumero();
                    }
                    row.put("periodoAsignatura", numPeriodoAsig);
                    row.put("calificacionFinal", c.getCalificacionFinal());
                    row.put("estatus", c.getEstatus() != null ? c.getEstatus().name() : "—");
                    row.put("estadoAprobacion", c.getEstadoAprobacion() != null ? c.getEstadoAprobacion().name() : "—");
                    row.put("tipoEvaluacion", c.getTipoEvaluacion() != null ? c.getTipoEvaluacion().name() : "—");
                    return row;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(filas);
    }

    /**
     * Historial para el alumno autenticado: aplica el bloqueo por Evaluación Docente.
     * No agrega botones ni textos extra; solo marca la fila como bloqueada y oculta el valor numérico.
     */
    private ResponseEntity<?> construirHistorialCalificacionesParaAlumnoPropio(Alumno alumno, Long programaId) {
        List<Calificacion> lista = calificacionRepository.findByAlumnoIdForKardexHistorial(alumno.getId());
        if (lista == null) lista = List.of();

        Comparator<Calificacion> ordenHistorial = Comparator
                .comparing(KardexController::clavePeriodoDesdeCalificacion)
                .thenComparingInt(KardexController::numeroPeriodoPlan)
                .thenComparing(KardexController::nombreAsignatura, String.CASE_INSENSITIVE_ORDER);

        List<Map<String, Object>> filas = lista.stream()
                .filter(c -> c.getAsignatura() != null && c.getAsignatura().cuentaEnPlanAcademico())
                .filter(c -> {
                    if (programaId == null) return true;
                    return c.getAsignatura() != null
                            && c.getAsignatura().getPrograma() != null
                            && c.getAsignatura().getPrograma().getId() != null
                            && programaId.equals(c.getAsignatura().getPrograma().getId());
                })
                .sorted(ordenHistorial)
                .map(c -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", c.getId());
                    row.put("periodo", c.getPeriodoDisplay() != null ? c.getPeriodoDisplay() : "—");
                    Asignatura asig = c.getAsignatura();
                    row.put("asignaturaClave", asig != null && asig.getClave() != null ? asig.getClave() : "");
                    row.put("asignaturaNombre", asig != null && asig.getNombre() != null ? asig.getNombre() : "—");
                    Integer numPeriodoAsig = null;
                    if (asig != null && asig.getPeriodo() != null) {
                        numPeriodoAsig = asig.getPeriodo().getNumero();
                    }
                    row.put("periodoAsignatura", numPeriodoAsig);
                    boolean ocultar = false;
                    try {
                        ocultar = evaluacionDocenteService.debeOcultarCalificacionPorEvaluacion(alumno, c);
                    } catch (Exception ignored) {}
                    if (ocultar) {
                        row.put("calificacionFinal", null);
                        row.put("bloqueadaPorEvaluacion", true);
                    } else {
                        row.put("calificacionFinal", c.getCalificacionFinal());
                        row.put("bloqueadaPorEvaluacion", false);
                    }
                    row.put("estatus", c.getEstatus() != null ? c.getEstatus().name() : "—");
                    row.put("estadoAprobacion", c.getEstadoAprobacion() != null ? c.getEstadoAprobacion().name() : "—");
                    row.put("tipoEvaluacion", c.getTipoEvaluacion() != null ? c.getTipoEvaluacion().name() : "—");
                    return row;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(filas);
    }

    /**
     * Obtiene programas, grupos y ciclos para los filtros.
     */
    @GetMapping("/filtros")
    @RequierePermiso("VER_ALUMNOS")
    public ResponseEntity<Map<String, Object>> obtenerFiltros() {
        List<ProgramaEducativo> programas = programaRepository.findAll().stream()
                .filter(p -> p.getEstatus() == ProgramaEducativo.EstatusPrograma.ACTIVO)
                .sorted(Comparator.comparing(ProgramaEducativo::getNombre))
                .collect(Collectors.toList());
        List<Grupo> grupos = grupoRepository.findAllForApiList(null, null, null, null, null);
        // Periodos por tipo (para que el frontend pueda filtrar según el tipo de plan del programa)
        List<PeriodoAcademico> periodosAcad = java.util.Arrays.stream(ProgramaEducativo.TipoPeriodo.values())
                .flatMap(t -> periodoAcademicoService.listarDisponiblesPorTipo(t).stream())
                .toList();

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("programas", programas.stream().map(p -> Map.of("id", p.getId(), "nombre", p.getNombre() != null ? p.getNombre() : "")).collect(Collectors.toList()));
        resultado.put("grupos", grupos.stream().map(g -> Map.of(
                "id", g.getId(),
                "nombre", g.getNombre() != null ? g.getNombre() : "",
                "programaId", g.getPrograma() != null ? g.getPrograma().getId() : null
        )).collect(Collectors.toList()));
        resultado.put("periodos", periodosAcad.stream().map(p -> Map.<String, Object>of(
                "nombre", p.getCodigo() != null ? p.getCodigo() : "",
                "id", p.getId(),
                "tipoPeriodo", p.getTipoPeriodo() != null ? p.getTipoPeriodo().name() : "SEMESTRE"
        )).collect(Collectors.toList()));

        return ResponseEntity.ok(resultado);
    }

    private Map<String, Object> aMapaResumen(Alumno a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("matricula", a.getMatricula());
        m.put("nombre", a.getNombreCompleto());
        m.put("curp", a.getCurp());
        m.put("programaNombre", a.getPrograma() != null ? a.getPrograma().getNombre() : "");
        return m;
    }

    private static boolean esAprobatoria(Calificacion c) {
        if (Calificacion.EstatusCalificacion.APROBADO.equals(c.getEstatus())) return true;
        if (c.getCalificacionFinal() != null && c.getCalificacionFinal() >= CALIF_MINIMA_APROBATORIA) return true;
        return false;
    }

    private String formatearPeriodoOrdinal(Integer num, ProgramaEducativo.TipoPeriodo tipo) {
        if (num == null || num < 1) return "—";
        String tipoStr = tipo != null ? tipo.name().toLowerCase() : "periodo";
        if (tipoStr.startsWith("semestre")) tipoStr = "semestre";
        else if (tipoStr.startsWith("cuatrimestre") || tipoStr.startsWith("tetramestre")) tipoStr = "cuatrimestre";
        else if (tipoStr.startsWith("trimestre")) tipoStr = "trimestre";
        String ord = (num >= 1 && num < ORDINALES.length) ? ORDINALES[num] : num + "°";
        return ord + " " + tipoStr;
    }

    private String obtenerPeriodoIngreso(List<Calificacion> calif, String periodoIngreso) {
        if (calif == null || calif.isEmpty()) return periodoIngreso != null && !periodoIngreso.isBlank() ? periodoIngreso.trim() : "—";
        return calif.stream().map(Calificacion::getPeriodo).filter(Objects::nonNull).min(String::compareTo).orElse(periodoIngreso != null ? periodoIngreso : "—");
    }

    private String obtenerPeriodoEgreso(List<Calificacion> calif) {
        if (calif == null || calif.isEmpty()) return "—";
        return calif.stream().map(Calificacion::getPeriodo).filter(Objects::nonNull).max(String::compareTo).orElse("—");
    }

    /**
     * Promedio de calificaciones cuyo periodo escolar coincide con el código del nivel actual
     * (ej. 2025-1), comparando {@link Calificacion#getPeriodo()} o el código del {@link PeriodoAcademico} vinculado.
     */
    private double calcularPromedioPeriodoActual(List<Calificacion> calif, String codigoPeriodoEscolarObjetivo) {
        if (calif == null || calif.isEmpty() || codigoPeriodoEscolarObjetivo == null || codigoPeriodoEscolarObjetivo.isBlank()) {
            return 0;
        }
        String target = codigoPeriodoEscolarObjetivo.trim();
        List<Calificacion> delPeriodo = calif.stream()
                .filter(c -> target.equals(c.getPeriodo())
                        || (c.getPeriodoAcademico() != null && target.equals(c.getPeriodoAcademico().getCodigo())))
                .collect(Collectors.toList());
        if (delPeriodo.isEmpty()) {
            return 0;
        }
        return delPeriodo.stream().mapToDouble(c -> c.getCalificacionFinal() != null ? c.getCalificacionFinal() : 0).average().orElse(0);
    }
}
