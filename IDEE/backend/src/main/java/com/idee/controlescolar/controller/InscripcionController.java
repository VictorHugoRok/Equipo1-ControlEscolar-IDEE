package com.idee.controlescolar.controller;

import com.idee.controlescolar.dto.TipoInscripcion;
import com.idee.controlescolar.model.Alumno;
import com.idee.controlescolar.model.AlumnoPrograma;
import com.idee.controlescolar.model.Grupo;
import com.idee.controlescolar.model.ProgramaEducativo;
import com.idee.controlescolar.repository.AlumnoRepository;
import com.idee.controlescolar.repository.AlumnoProgramaRepository;
import com.idee.controlescolar.repository.GrupoRepository;
import com.idee.controlescolar.repository.ProgramaEducativoRepository;
import com.idee.controlescolar.security.RequierePermiso;
import com.idee.controlescolar.service.InscripcionEjecucionService;
import com.idee.controlescolar.service.PeriodoAcademicoService;
import com.idee.controlescolar.service.ProgresoAcademicoNivelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Controlador para el flujo de inscripción y reinscripción de alumnos.
 * - Inscripción: alumnos con matrícula activa que nunca han cursado.
 * - Reinscripción: alumnos que ya cursaron al menos 1 periodo.
 */
@RestController
@RequestMapping("/inscripciones")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class InscripcionController {

    private final AlumnoRepository alumnoRepository;
    private final AlumnoProgramaRepository alumnoProgramaRepository;
    private final GrupoRepository grupoRepository;
    private final ProgramaEducativoRepository programaRepository;
    private final PeriodoAcademicoService periodoAcademicoService;
    private final ProgresoAcademicoNivelService progresoAcademicoNivelService;
    private final InscripcionEjecucionService inscripcionEjecucionService;

    /**
     * Alumnos disponibles para inscripción o reinscripción por programa.
     * GET /api/inscripciones/alumnos-disponibles?programaId=X&tipo=INSCRIPCION|REINSCRIPCION
     * &periodoIngresoId= opcional: filtra por periodo académico de ingreso del alumno (FK).
     * - REINSCRIPCION: solo alumnos cuyo ingreso coincide con ese periodo.
     * - INSCRIPCION: solo alumnos sin periodo de ingreso asignado o con ese mismo periodo.
     */
    @GetMapping("/alumnos-disponibles")
    @RequierePermiso("VER_ALUMNOS")
    public ResponseEntity<?> alumnosDisponibles(
            @RequestParam(required = false) Long programaId,
            @RequestParam(required = false) Long periodoIngresoId,
            @RequestParam(required = false) TipoInscripcion tipo) {

        ProgramaEducativo programa = null;
        if (programaId != null) {
            programa = programaRepository.findById(programaId).orElse(null);
            if (programa == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Programa no encontrado"));
            }
        }

        if (programaId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Indica programaId para listar alumnos disponibles."));
        }

        if (periodoIngresoId != null && periodoAcademicoService.findById(periodoIngresoId).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Periodo de ingreso no encontrado."));
        }

        List<AlumnoPrograma> candidatos = alumnoProgramaRepository.findByPrograma_IdAndEstatusMatriculaOrderByAlumno_ApellidoPaternoAsc(
                programaId, AlumnoPrograma.EstatusMatriculaPrograma.ACTIVA);
        if (candidatos == null) candidatos = new ArrayList<>();

        Map<Long, List<Grupo>> gruposPorAlumno = new HashMap<>();
        for (AlumnoPrograma ap : candidatos) {
            Alumno a = ap.getAlumno();
            if (a == null || a.getId() == null) continue;
            List<Grupo> grupos = grupoRepository.findByAlumnos_Id(a.getId());
            if (grupos == null) {
                grupos = List.of();
            } else if (programaId != null) {
                grupos = grupos.stream()
                        .filter(g -> perteneceAlPrograma(g, programaId))
                        .toList();
            }
            gruposPorAlumno.put(a.getId(), grupos);
        }

        // En modo unificado ya no se separa por tipo en UI; conservar compatibilidad si llega el param.
        TipoInscripcion tipoReq = (tipo != null ? tipo : TipoInscripcion.AUTO);

        List<Map<String, Object>> resultado = new ArrayList<>();
        for (AlumnoPrograma ap : candidatos) {
            Alumno a = ap.getAlumno();
            if (a == null || a.getId() == null) continue;
            if (periodoIngresoId != null) {
                if (tipoReq == TipoInscripcion.REINSCRIPCION) {
                    if (ap.getPeriodoIngreso() == null || !periodoIngresoId.equals(ap.getPeriodoIngreso().getId())) {
                        continue;
                    }
                } else {
                    if (ap.getPeriodoIngreso() != null && !periodoIngresoId.equals(ap.getPeriodoIngreso().getId())) {
                        continue;
                    }
                }
            }
            /*
             * Progreso siempre respecto al programa del filtro (cuando hay programaId), no solo al proxy del alumno.
             * Así los de nuevo ingreso no quedan fuera por desincronía de periodoCursando vs getPeriodosAprobados()
             * cuando aún no hay niveles completados por calificaciones confirmadas.
             */
            ProgramaEducativo progCalc;
            if (programaId != null && programa != null) {
                progCalc = programa;
            } else {
                progCalc = ap.getPrograma();
                if (progCalc == null) {
                    continue;
                }
            }

            int nivelPlan = progresoAcademicoNivelService.calcularNivelActualPlan(a.getId(), progCalc);
            int periodosCompletados = progresoAcademicoNivelService.contarNivelesCompletamenteAprobados(a.getId(), progCalc);
            long califConfirmadasPlan = progresoAcademicoNivelService.contarCalificacionesConfirmadasEnPlan(a.getId(), progCalc);

            /*
             * Inscripción: sin niveles completos por calificaciones confirmadas, o sin ninguna calificación
             * confirmada en el plan pese a desincronía (p. ej. periodoCursando / contador vs expediente real).
             */
            boolean esInscripcion = periodosCompletados == 0
                    || (tipoReq == TipoInscripcion.INSCRIPCION && periodosCompletados > 0 && califConfirmadasPlan == 0);
            boolean esReinscripcion = periodosCompletados >= 1;

            if (tipoReq == TipoInscripcion.INSCRIPCION && !esInscripcion) continue;
            if (tipoReq == TipoInscripcion.REINSCRIPCION && !esReinscripcion) continue;

            String periodoIngreso = (ap.getPeriodoIngreso() != null ? ap.getPeriodoIngreso().getCodigo() : "");
            String periodoEscolarSegunNivel = "";
            if (periodoIngreso != null && !periodoIngreso.isBlank() && progCalc.getTipoPeriodo() != null) {
                periodoEscolarSegunNivel = PeriodoAcademicoService.codigoPeriodoDelNivelDelPlan(
                        periodoIngreso.trim(), progCalc.getTipoPeriodo(), nivelPlan).orElse("");
            }
            Integer periodoQueCursara = null;
            List<Map<String, Object>> gruposActuales = new ArrayList<>();

            // Nivel del plan que corresponde cursar (no periodoCursando+1 a ciegas).
            // Nuevo ingreso típicamente será 1; reingreso será su nivel calculado.
            periodoQueCursara = nivelPlan;
            for (Grupo g : gruposPorAlumno.getOrDefault(a.getId(), List.of())) {
                gruposActuales.add(Map.of(
                        "id", g.getId(),
                        "nombre", g.getNombre() != null ? g.getNombre() : "",
                        "cicloEscolar", g.getCicloEscolar() != null ? g.getCicloEscolar() : ""
                ));
            }
            String tipoPeriodoPrograma = "SEMESTRE";
            if (progCalc.getTipoPeriodo() != null) {
                tipoPeriodoPrograma = progCalc.getTipoPeriodo().name();
            }

            resultado.add(Map.<String, Object>ofEntries(
                    Map.entry("id", a.getId()),
                    Map.entry("matricula", a.getMatricula() != null ? a.getMatricula() : ""),
                    Map.entry("nombre", a.getNombreCompleto()),
                    Map.entry("programaId", progCalc.getId()),
                    Map.entry("programaNombre", progCalc.getNombre() != null ? progCalc.getNombre() : ""),
                    Map.entry("periodoIngreso", periodoIngreso != null ? periodoIngreso : ""),
                    Map.entry("periodoCursando", nivelPlan),
                    Map.entry("periodosCompletados", periodosCompletados),
                    Map.entry("esReingreso", esReinscripcion),
                    Map.entry("tipoPeriodoPrograma", tipoPeriodoPrograma),
                    Map.entry("periodoQueCursara", periodoQueCursara),
                    Map.entry("periodoAcademicoSegunNivelActual", periodoEscolarSegunNivel),
                    Map.entry("periodoAcademicoSiguiente", periodoEscolarSegunNivel),
                    Map.entry("gruposActuales", gruposActuales)
            ));
        }

        return ResponseEntity.ok(resultado);
    }

    /**
     * Ejecuta inscripción o reinscripción masiva por cohorte (con exclusiones) o por lista de IDs.
     * Body JSON: tipo, grupoId, periodoAcademicoId (obligatorio: ingreso si INSCRIPCION, escolar actual si REINSCRIPCION),
     * cohorteId (opcional), alumnoIds (opcional), excluirAlumnoIds (opcional, típico con cohorte).
     */
    @PostMapping("/ejecutar")
    @RequierePermiso("ACTUALIZAR_GRUPOS")
    public ResponseEntity<?> ejecutar(@RequestBody Map<String, Object> body) {
        TipoInscripcion tipo;
        try {
            Object raw = body.get("tipo");
            if (raw == null || String.valueOf(raw).trim().isEmpty()) {
                tipo = TipoInscripcion.AUTO;
            } else {
                tipo = TipoInscripcion.valueOf(String.valueOf(raw).trim().toUpperCase());
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "tipo debe ser AUTO, INSCRIPCION o REINSCRIPCION"));
        }
        Long grupoId = longVal(body.get("grupoId"));
        if (grupoId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "grupoId es obligatorio"));
        }
        Long periodoAcademicoId = longVal(body.get("periodoAcademicoId"));
        Long cohorteId = longVal(body.get("cohorteId"));
        List<Long> alumnoIds = longList(body.get("alumnoIds"));
        List<Long> excluir = longList(body.get("excluirAlumnoIds"));

        Map<String, Object> out = inscripcionEjecucionService.ejecutar(
                tipo, grupoId, periodoAcademicoId, cohorteId, alumnoIds, excluir);
        if (Boolean.FALSE.equals(out.get("ok")) && out.get("error") != null) {
            return ResponseEntity.badRequest().body(out);
        }
        return ResponseEntity.ok(out);
    }

    private static Long longVal(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<Long> longList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Long> out = new ArrayList<>();
        for (Object o : list) {
            Long v = longVal(o);
            if (v != null) {
                out.add(v);
            }
        }
        return out;
    }

    private boolean perteneceAlPrograma(Grupo g, Long programaId) {
        if (g.getPrograma() != null && programaId.equals(g.getPrograma().getId())) return true;
        return g.getAsignatura() != null && g.getAsignatura().getPrograma() != null
                && programaId.equals(g.getAsignatura().getPrograma().getId());
    }
}
