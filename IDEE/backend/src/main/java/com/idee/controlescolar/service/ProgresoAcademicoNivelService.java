package com.idee.controlescolar.service;

import com.idee.controlescolar.model.Asignatura;
import com.idee.controlescolar.model.AlumnoPrograma;
import com.idee.controlescolar.model.Calificacion;
import com.idee.controlescolar.model.Alumno;
import com.idee.controlescolar.model.PeriodoAcademico;
import com.idee.controlescolar.model.ProgramaEducativo;
import com.idee.controlescolar.repository.AsignaturaRepository;
import com.idee.controlescolar.repository.CalificacionRepository;
import com.idee.controlescolar.repository.AlumnoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Progreso académico por niveles del plan de estudios.
 * <p>
 * Cada programa define {@link ProgramaEducativo#getDuracionPeriodos()} y tipo de periodo; las asignaturas
 * pertenecen a un número de periodo del plan (1..N). Para "pasar" un nivel, el alumno debe tener
 * calificación <strong>confirmada</strong> y <strong>aprobatoria</strong> en todas las materias de ese nivel
 * que {@link Asignatura#cuentaEnPlanAcademico() cuentan} (excluye extracurricular, etc.).
 * </p>
 * <p>
 * {@link Alumno#getPeriodoCursando()} se interpreta como el <strong>nivel actual</strong> del plan que el
 * alumno cursa (1-based): es el primer nivel aún no completado según calificaciones, o N+1 si ya completó todos.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProgresoAcademicoNivelService {

    private final CalificacionRepository calificacionRepository;
    private final AsignaturaRepository asignaturaRepository;
    private final AlumnoRepository alumnoRepository;
    private final PeriodoAcademicoService periodoAcademicoService;

    public static final double CALIF_MINIMA_APROBATORIA = 7.0;

    /**
     * Código de periodo escolar (ej. 2026-1, 2026-2) para una inscripción concreta: prioriza {@link AlumnoPrograma#getPeriodoAcademicoActual()},
     * o lo calcula desde periodo de ingreso + tipo de periodo del programa + nivel cursando.
     */
    public Optional<String> resolverCodigoPeriodoEscolarParaInscripcion(AlumnoPrograma ap) {
        if (ap == null) {
            return Optional.empty();
        }
        if (ap.getPeriodoAcademicoActual() != null
                && ap.getPeriodoAcademicoActual().getCodigo() != null
                && !ap.getPeriodoAcademicoActual().getCodigo().isBlank()) {
            return Optional.of(ap.getPeriodoAcademicoActual().getCodigo().trim());
        }
        PeriodoAcademico pi = ap.getPeriodoIngreso();
        if (pi == null || pi.getCodigo() == null || pi.getCodigo().isBlank()) {
            return Optional.empty();
        }
        ProgramaEducativo prog = ap.getPrograma();
        if (prog == null || prog.getTipoPeriodo() == null) {
            return Optional.empty();
        }
        int nivel = ap.getPeriodoCursando() != null && ap.getPeriodoCursando() >= 1 ? ap.getPeriodoCursando() : 1;
        return PeriodoAcademicoService.codigoPeriodoDelNivelDelPlan(
                pi.getCodigo().trim(), prog.getTipoPeriodo(), nivel);
    }

    public static boolean esCalificacionAprobatoria(Calificacion c) {
        if (c == null) {
            return false;
        }
        if (Calificacion.EstatusCalificacion.APROBADO.equals(c.getEstatus())) {
            return true;
        }
        return c.getCalificacionFinal() != null && c.getCalificacionFinal() >= CALIF_MINIMA_APROBATORIA;
    }

    /**
     * Una fila por asignatura; si hay varias calificaciones, prioriza confirmada y mayor id.
     */
    public Map<Long, Calificacion> indexarCalificacionesPreferirConfirmada(Long alumnoId) {
        List<Calificacion> todas = calificacionRepository.findByAlumnoId(alumnoId);
        Map<Long, Calificacion> map = new HashMap<>();
        for (Calificacion c : todas) {
            if (c.getAsignatura() == null) {
                continue;
            }
            Long aid = c.getAsignatura().getId();
            Calificacion ex = map.get(aid);
            if (ex == null) {
                map.put(aid, c);
                continue;
            }
            boolean cConf = Boolean.TRUE.equals(c.getConfirmada());
            boolean exConf = Boolean.TRUE.equals(ex.getConfirmada());
            if (cConf && !exConf) {
                map.put(aid, c);
            } else if (cConf == exConf && c.getId() != null && ex.getId() != null && c.getId() > ex.getId()) {
                map.put(aid, c);
            }
        }
        return map;
    }

    public boolean asignaturaAprobadaConfirmada(Map<Long, Calificacion> califPorAsignatura, Long asignaturaId) {
        Calificacion c = califPorAsignatura.get(asignaturaId);
        return c != null && Boolean.TRUE.equals(c.getConfirmada()) && esCalificacionAprobatoria(c);
    }

    private boolean nivelPlanCompletamenteAprobado(List<Asignatura> materiasDelNivel,
                                                     Map<Long, Calificacion> califPorAsignatura) {
        for (Asignatura a : materiasDelNivel) {
            if (!asignaturaAprobadaConfirmada(califPorAsignatura, a.getId())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Nivel del plan que el alumno cursa ahora (1..M+1): último nivel totalmente aprobado + 1.
     * Los niveles sin materias que cuentan se omiten (no bloquean el avance).
     */
    public int calcularNivelActualPlan(Long alumnoId, ProgramaEducativo programa) {
        if (programa == null || programa.getId() == null) {
            return 1;
        }
        List<Asignatura> queCuentan = asignaturaRepository.findByProgramaId(programa.getId()).stream()
                .filter(Asignatura::cuentaEnPlanAcademico)
                .toList();
        int maxDesdeAsignaturas = queCuentan.stream()
                .map(a -> a.getPeriodo() != null ? a.getPeriodo().getNumero() : null)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        int declarado = programa.getDuracionPeriodos() != null && programa.getDuracionPeriodos() > 0
                ? programa.getDuracionPeriodos()
                : 0;
        int maxNivel = Math.max(maxDesdeAsignaturas, declarado);
        if (maxNivel <= 0) {
            return 1;
        }
        Map<Long, Calificacion> califPorAsignatura = indexarCalificacionesPreferirConfirmada(alumnoId);
        int ultimoNivelCompleto = 0;
        for (int p = 1; p <= maxNivel; p++) {
            final int numPlan = p;
            List<Asignatura> enP = queCuentan.stream()
                    .filter(a -> a.getPeriodo() != null && numPlan == a.getPeriodo().getNumero())
                    .toList();
            if (enP.isEmpty()) {
                continue;
            }
            if (!nivelPlanCompletamenteAprobado(enP, califPorAsignatura)) {
                break;
            }
            ultimoNivelCompleto = p;
        }
        return ultimoNivelCompleto + 1;
    }

    /** Cantidad de niveles del plan ya completados (todas las materias del nivel aprobadas y confirmadas). */
    public int contarNivelesCompletamenteAprobados(Long alumnoId, ProgramaEducativo programa) {
        int nivel = calcularNivelActualPlan(alumnoId, programa);
        return Math.max(0, nivel - 1);
    }

    /**
     * Calificaciones confirmadas del alumno en el programa, solo asignaturas que cuentan en el plan académico.
     */
    public long contarCalificacionesConfirmadasEnPlan(Long alumnoId, ProgramaEducativo programa) {
        if (programa == null || programa.getId() == null) {
            return 0;
        }
        final Long pid = programa.getId();
        return calificacionRepository.findByAlumnoId(alumnoId).stream()
                .filter(c -> Boolean.TRUE.equals(c.getConfirmada()))
                .filter(c -> {
                    Asignatura asig = c.getAsignatura();
                    return asig != null
                            && asig.getPrograma() != null
                            && pid.equals(asig.getPrograma().getId())
                            && asig.cuentaEnPlanAcademico();
                })
                .count();
    }

    /**
     * Resumen por cada nivel del plan que tenga al menos una materia que cuenta: totales, aprobadas confirmadas y si está completo.
     * El rango de niveles coincide con {@link #calcularNivelActualPlan(Long, ProgramaEducativo)} (max entre plan y asignaturas).
     */
    public List<Map<String, Object>> resumenPorNivelDelPlan(Long alumnoId, ProgramaEducativo programa) {
        List<Map<String, Object>> resultado = new ArrayList<>();
        if (programa == null || programa.getId() == null) {
            return resultado;
        }
        List<Asignatura> queCuentan = asignaturaRepository.findByProgramaId(programa.getId()).stream()
                .filter(Asignatura::cuentaEnPlanAcademico)
                .toList();
        int maxDesdeAsignaturas = queCuentan.stream()
                .map(a -> a.getPeriodo() != null ? a.getPeriodo().getNumero() : null)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        int declarado = programa.getDuracionPeriodos() != null && programa.getDuracionPeriodos() > 0
                ? programa.getDuracionPeriodos()
                : 0;
        int maxNivel = Math.max(maxDesdeAsignaturas, declarado);
        if (maxNivel <= 0) {
            return resultado;
        }
        Map<Long, Calificacion> califPorAsignatura = indexarCalificacionesPreferirConfirmada(alumnoId);
        for (int p = 1; p <= maxNivel; p++) {
            final int numPlan = p;
            List<Asignatura> enP = queCuentan.stream()
                    .filter(a -> a.getPeriodo() != null && numPlan == a.getPeriodo().getNumero())
                    .toList();
            if (enP.isEmpty()) {
                continue;
            }
            long aprobadasEnP = enP.stream()
                    .filter(a -> asignaturaAprobadaConfirmada(califPorAsignatura, a.getId()))
                    .count();
            boolean completo = aprobadasEnP == enP.size();
            String nombrePeriodoPlan = enP.get(0).getPeriodo() != null && enP.get(0).getPeriodo().getNombre() != null
                    ? enP.get(0).getPeriodo().getNombre()
                    : "Nivel " + p;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("numeroPlan", p);
            row.put("nombrePeriodoPlan", nombrePeriodoPlan);
            row.put("materiasTotales", enP.size());
            row.put("materiasAprobadas", (int) aprobadasEnP);
            row.put("completo", completo);
            resultado.add(row);
        }
        return resultado;
    }

    /**
     * Periodo escolar (calendario) que corresponde al nivel del plan: ingreso = nivel 1, cada nivel siguiente
     * avanza un periodo en la secuencia del {@link ProgramaEducativo#getTipoPeriodo()}.
     *
     * @return true si se modificó {@link Alumno#getPeriodoAcademicoActual()}
     */
    public boolean actualizarPeriodoAcademicoActualDesdeNivel(Alumno a) {
        if (a == null || a.getPrograma() == null || a.getPeriodoCursando() == null) {
            return false;
        }
        String ingreso = a.getPeriodoIngreso();
        if (ingreso == null || ingreso.isBlank()) {
            return false;
        }
        ProgramaEducativo.TipoPeriodo tipo = a.getPrograma().getTipoPeriodo();
        Optional<String> codOpt = PeriodoAcademicoService.codigoPeriodoDelNivelDelPlan(
                ingreso.trim(), tipo, a.getPeriodoCursando());
        if (codOpt.isEmpty()) {
            return false;
        }
        PeriodoAcademico pa;
        try {
            pa = periodoAcademicoService.asegurarPeriodo(codOpt.get(), tipo);
        } catch (IllegalArgumentException ex) {
            log.warn("Periodo académico no registrado para alumno {} ({}): {}", a.getId(), codOpt.get(), ex.getMessage());
            return false;
        }
        if (pa == null) {
            return false;
        }
        if (a.getPeriodoAcademicoActual() != null && pa.getId().equals(a.getPeriodoAcademicoActual().getId())) {
            return false;
        }
        a.setPeriodoAcademicoActual(pa);
        return true;
    }

    /**
     * Recalcula y persiste solo el periodo escolar actual del alumno (cualquier estatus).
     * Útil tras ajustes manuales de {@link Alumno#getPeriodoCursando()}.
     */
    @Transactional
    public void sincronizarPeriodoAcademicoEscolarDesdeNivel(Long alumnoId) {
        Alumno a = alumnoRepository.findById(alumnoId).orElse(null);
        if (a == null) {
            return;
        }
        if (actualizarPeriodoAcademicoActualDesdeNivel(a)) {
            alumnoRepository.save(a);
            log.debug("periodoAcademicoActual sincronizado alumno {} -> {}", alumnoId,
                    a.getPeriodoAcademicoActual() != null ? a.getPeriodoAcademicoActual().getCodigo() : "");
        }
    }

    /**
     * Actualiza {@link Alumno#setPeriodoCursando(Integer)} según niveles completados.
     * Solo alumnos {@link Alumno.EstatusMatricula#ACTIVA}; no modifica egresados ni otros estatus.
     * También actualiza {@link Alumno#getPeriodoAcademicoActual()} según ingreso + nivel.
     */
    @Transactional
    public void sincronizarPeriodoCursandoDesdeNiveles(Long alumnoId) {
        Alumno a = alumnoRepository.findById(alumnoId).orElse(null);
        if (a == null || a.getPrograma() == null) {
            return;
        }
        if (a.getEstatusMatricula() != Alumno.EstatusMatricula.ACTIVA) {
            log.debug("Sync nivel: alumno {} omitido (estatus {})", alumnoId, a.getEstatusMatricula());
            return;
        }
        int nuevo = calcularNivelActualPlan(alumnoId, a.getPrograma());
        boolean dirty = false;
        if (a.getPeriodoCursando() == null || !a.getPeriodoCursando().equals(nuevo)) {
            a.setPeriodoCursando(nuevo);
            dirty = true;
            log.debug("periodoCursando sincronizado alumno {} -> {}", alumnoId, nuevo);
        }
        if (actualizarPeriodoAcademicoActualDesdeNivel(a)) {
            dirty = true;
        }
        if (dirty) {
            alumnoRepository.save(a);
        }
    }
}
