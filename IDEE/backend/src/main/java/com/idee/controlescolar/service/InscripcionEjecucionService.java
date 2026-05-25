package com.idee.controlescolar.service;

import com.idee.controlescolar.dto.TipoInscripcion;
import com.idee.controlescolar.model.Alumno;
import com.idee.controlescolar.model.AlumnoPrograma;
import com.idee.controlescolar.model.Asignatura;
import com.idee.controlescolar.model.Calificacion;
import com.idee.controlescolar.model.Grupo;
import com.idee.controlescolar.model.Cohorte;
import com.idee.controlescolar.model.PeriodoAcademico;
import com.idee.controlescolar.model.ProgramaEducativo;
import com.idee.controlescolar.repository.AlumnoRepository;
import com.idee.controlescolar.repository.AlumnoProgramaRepository;
import com.idee.controlescolar.repository.CohorteRepository;
import com.idee.controlescolar.repository.GrupoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Inscripción y reinscripción masiva (cohorte o lista) con validaciones de nivel del plan
 * y materias ya aprobadas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InscripcionEjecucionService {

    private final AlumnoRepository alumnoRepository;
    private final AlumnoProgramaRepository alumnoProgramaRepository;
    private final GrupoRepository grupoRepository;
    private final CohorteRepository cohorteRepository;
    private final PeriodoAcademicoService periodoAcademicoService;
    private final GestionAcademicaEstadoService gestionAcademicaEstadoService;
    private final ProgresoAcademicoNivelService progresoAcademicoNivelService;

    @Transactional
    public Map<String, Object> ejecutar(TipoInscripcion tipo,
                                        Long grupoId,
                                        Long periodoAcademicoId,
                                        Long cohorteId,
                                        List<Long> alumnoIdsSolicitados,
                                        List<Long> excluirAlumnoIds) {
        List<Map<String, Object>> detalle = new ArrayList<>();
        int agregadosAlGrupo = 0;
        int yaEnGrupo = 0;
        int omitidos = 0;
        int errores = 0;

        Optional<Grupo> optG = grupoRepository.findWithDetailsForInscripcion(grupoId);
        if (optG.isEmpty()) {
            return Map.of("ok", false, "error", "Grupo no encontrado", "agregadosAlGrupo", 0, "yaEnGrupo", 0, "omitidos", 0, "errores", 1, "detalle", List.of());
        }
        Grupo grupo = optG.get();
        if (grupo.getEstatus() != Grupo.EstatusGrupo.ACTIVO) {
            return Map.of("ok", false, "error", "El grupo no está activo.", "agregadosAlGrupo", 0, "yaEnGrupo", 0, "omitidos", 0, "errores", 1, "detalle", List.of());
        }

        Long programaGrupoId = programaIdDelGrupo(grupo);
        if (programaGrupoId == null) {
            return Map.of("ok", false, "error", "El grupo no tiene programa asociado.", "agregadosAlGrupo", 0, "yaEnGrupo", 0, "omitidos", 0, "errores", 1, "detalle", List.of());
        }

        PeriodoAcademico periodoOperacion = null;
        if (periodoAcademicoId != null) {
            periodoOperacion = periodoAcademicoService.findById(periodoAcademicoId).orElse(null);
            if (periodoOperacion == null) {
                return Map.of("ok", false, "error", "Periodo académico no encontrado.", "agregadosAlGrupo", 0, "yaEnGrupo", 0, "omitidos", 0, "errores", 1, "detalle", List.of());
            }
        } else if (grupo.getPeriodoAcademico() != null) {
            periodoOperacion = grupo.getPeriodoAcademico();
        }
        String mensajePeriodo = gestionAcademicaEstadoService.validarInscripcion(periodoOperacion);
        if (mensajePeriodo != null) {
            return Map.of("ok", false, "error", mensajePeriodo, "agregadosAlGrupo", 0, "yaEnGrupo", 0, "omitidos", 0, "errores", 1, "detalle", List.of());
        }
        if (grupo.getPeriodoAcademico() != null && periodoOperacion != null
                && !grupo.getPeriodoAcademico().getId().equals(periodoOperacion.getId())) {
            return Map.of("ok", false, "error",
                    "El grupo pertenece a un periodo académico distinto al seleccionado para la inscripción.",
                    "agregadosAlGrupo", 0, "yaEnGrupo", 0, "omitidos", 0, "errores", 1, "detalle", List.of());
        }

        Set<Long> excluir = excluirAlumnoIds == null ? Set.of() : new LinkedHashSet<>(excluirAlumnoIds);

        Set<Long> candidatos = new LinkedHashSet<>();
        if (cohorteId != null) {
            Cohorte cohorte = cohorteRepository.findById(cohorteId).orElse(null);
            if (cohorte == null) {
                return Map.of("ok", false, "error", "Cohorte no encontrada.", "agregadosAlGrupo", 0, "yaEnGrupo", 0, "omitidos", 0, "errores", 1, "detalle", List.of());
            }
            if (cohorte.getPrograma() == null || !programaGrupoId.equals(cohorte.getPrograma().getId())) {
                return Map.of("ok", false,
                        "error", "La cohorte no pertenece al mismo programa educativo que el grupo seleccionado.",
                        "agregadosAlGrupo", 0, "yaEnGrupo", 0, "omitidos", 0, "errores", 1, "detalle", List.of());
            }
            List<Alumno> miembros = alumnoRepository.findByCohortes_IdOrderByApellidoPaternoAsc(cohorteId);
            for (Alumno a : miembros) {
                candidatos.add(a.getId());
            }
            candidatos.removeAll(excluir);
            if (alumnoIdsSolicitados != null && !alumnoIdsSolicitados.isEmpty()) {
                Set<Long> filtro = new LinkedHashSet<>(alumnoIdsSolicitados);
                candidatos.retainAll(filtro);
            }
        } else {
            if (alumnoIdsSolicitados == null || alumnoIdsSolicitados.isEmpty()) {
                return Map.of("ok", false, "error", "Indique cohorte o lista de alumnos.", "agregadosAlGrupo", 0, "yaEnGrupo", 0, "omitidos", 0, "errores", 0, "detalle", List.of());
            }
            candidatos.addAll(alumnoIdsSolicitados);
            candidatos.removeAll(excluir);
        }

        if (candidatos.isEmpty()) {
            return Map.of("ok", false, "error", "No hay alumnos elegibles tras aplicar filtros y exclusiones.", "agregadosAlGrupo", 0, "yaEnGrupo", 0, "omitidos", 0, "errores", 0, "detalle", List.of());
        }

        List<Alumno> alumnosEnGrupo = new ArrayList<>(grupo.getAlumnos() != null ? grupo.getAlumnos() : List.of());

        for (Long alumnoId : candidatos) {
            Optional<Alumno> optA = alumnoRepository.findById(alumnoId);
            if (optA.isEmpty()) {
                detalle.add(Map.of("alumnoId", alumnoId, "estado", "ERROR", "mensaje", "Alumno no encontrado"));
                errores++;
                continue;
            }
            Alumno alumno = optA.get();
            AlumnoPrograma ap = alumnoProgramaRepository.findByAlumno_IdAndPrograma_Id(alumno.getId(), programaGrupoId).orElse(null);
            TipoInscripcion tipoEfectivo = resolverTipoEfectivo(tipo, alumno, ap);
            String r = validar(tipoEfectivo, alumno, ap, grupo, programaGrupoId);
            if (r != null) {
                detalle.add(Map.of(
                        "alumnoId", alumnoId,
                        "matricula", n(alumno.getMatricula()),
                        "estado", "OMITIDO",
                        "procesoAcademico", tipoEfectivo.name(),
                        "mensaje", r
                ));
                omitidos++;
                continue;
            }

            boolean ya = alumnosEnGrupo.stream().anyMatch(a -> a.getId().equals(alumnoId));

            try {
                if (tipoEfectivo == TipoInscripcion.INSCRIPCION) {
                    aplicarDatosInscripcion(ap, periodoAcademicoId);
                } else {
                    quitarDeOtrosGruposMismoPrograma(alumno.getId(), grupoId, programaGrupoId);
                    aplicarDatosReinscripcion(alumno, ap, periodoAcademicoId);
                }
                if (ap != null) alumnoProgramaRepository.save(ap);
                // El progreso/periodo actual se sincroniza por programa en el siguiente ajuste de ProgresoAcademicoNivelService
                // (aquí se mantiene la operación de inscripción/reinscripción sin depender del campo legacy en Alumno).

                if (!ya) {
                    alumnosEnGrupo.add(alumno);
                    agregadosAlGrupo++;
                    detalle.add(Map.of(
                            "alumnoId", alumnoId,
                            "matricula", n(alumno.getMatricula()),
                            "estado", "INSCRITO",
                            "procesoAcademico", tipoEfectivo.name(),
                            "etapaPeriodo", periodoOperacion != null ? gestionAcademicaEstadoService.etiquetaEtapa(periodoOperacion) : "",
                            "mensaje", "Operacion academica aplicada y alumno agregado al grupo"
                    ));
                } else {
                    yaEnGrupo++;
                    detalle.add(Map.of(
                            "alumnoId", alumnoId,
                            "matricula", n(alumno.getMatricula()),
                            "estado", "YA_EN_GRUPO",
                            "procesoAcademico", tipoEfectivo.name(),
                            "etapaPeriodo", periodoOperacion != null ? gestionAcademicaEstadoService.etiquetaEtapa(periodoOperacion) : "",
                            "mensaje", "Ya estaba en el grupo; se actualizo el expediente academico"
                    ));
                }
            } catch (Exception e) {
                log.warn("Error inscribiendo alumno {}: {}", alumnoId, e.getMessage());
                detalle.add(Map.of(
                        "alumnoId", alumnoId,
                        "matricula", n(alumno.getMatricula()),
                        "estado", "ERROR",
                        "procesoAcademico", tipoEfectivo.name(),
                        "mensaje", e.getMessage() != null ? e.getMessage() : "Error al guardar"
                ));
                errores++;
            }
        }

        grupo.setAlumnos(alumnosEnGrupo);
        grupoRepository.save(grupo);

        boolean ok = errores == 0;
        return Map.of(
                "ok", ok,
                "agregadosAlGrupo", agregadosAlGrupo,
                "yaEnGrupo", yaEnGrupo,
                "omitidos", omitidos,
                "errores", errores,
                "detalle", detalle
        );
    }

    private static String n(String s) {
        return s != null ? s : "";
    }

    private TipoInscripcion resolverTipoEfectivo(TipoInscripcion tipoReq, Alumno alumno, AlumnoPrograma ap) {
        if (tipoReq != null && tipoReq != TipoInscripcion.AUTO) {
            return tipoReq;
        }
        ProgramaEducativo prog = ap != null ? ap.getPrograma() : null;
        if (prog == null) {
            return TipoInscripcion.INSCRIPCION;
        }
        int nivelesCompletos = progresoAcademicoNivelService.contarNivelesCompletamenteAprobados(alumno.getId(), prog);
        long califConfirmadasPlan = progresoAcademicoNivelService.contarCalificacionesConfirmadasEnPlan(alumno.getId(), prog);
        // Heurística compatible con el flujo anterior: si hay niveles completos y califs confirmadas → reingreso
        if (nivelesCompletos >= 1 && califConfirmadasPlan > 0) {
            return TipoInscripcion.REINSCRIPCION;
        }
        return TipoInscripcion.INSCRIPCION;
    }

    private Long programaIdDelGrupo(Grupo g) {
        if (g.getPrograma() != null) {
            return g.getPrograma().getId();
        }
        if (g.getAsignatura() != null && g.getAsignatura().getPrograma() != null) {
            return g.getAsignatura().getPrograma().getId();
        }
        return null;
    }

    private static ProgramaEducativo.TipoPeriodo tipoPeriodoDelGrupo(Grupo g) {
        if (g.getPrograma() != null && g.getPrograma().getTipoPeriodo() != null) {
            return g.getPrograma().getTipoPeriodo();
        }
        if (g.getAsignatura() != null && g.getAsignatura().getPrograma() != null
                && g.getAsignatura().getPrograma().getTipoPeriodo() != null) {
            return g.getAsignatura().getPrograma().getTipoPeriodo();
        }
        return ProgramaEducativo.TipoPeriodo.SEMESTRE;
    }

    /**
     * @return mensaje de error o null si pasa
     */
    private String validar(TipoInscripcion tipo, Alumno alumno, AlumnoPrograma ap, Grupo grupo, Long programaGrupoId) {
        if (ap == null) {
            return "El alumno no está asignado a este programa; asígnalo primero para poder inscribirlo.";
        }
        if (ap.getEstatusMatricula() != AlumnoPrograma.EstatusMatriculaPrograma.ACTIVA) {
            return "La matrícula del alumno no está activa en este programa.";
        }
        ProgramaEducativo prog = ap.getPrograma();
        int nivelesCompletos = progresoAcademicoNivelService.contarNivelesCompletamenteAprobados(alumno.getId(), prog);
        int nivelQueCursa = progresoAcademicoNivelService.calcularNivelActualPlan(alumno.getId(), prog);
        long califConfirmadasPlan = progresoAcademicoNivelService.contarCalificacionesConfirmadasEnPlan(alumno.getId(), prog);

        if (tipo == TipoInscripcion.INSCRIPCION) {
            if (nivelesCompletos > 0 && califConfirmadasPlan > 0) {
                return "Solo nuevos ingreso: el alumno ya tiene nivel(es) completado(s) con calificaciones confirmadas en el plan.";
            }
        } else {
            if (nivelesCompletos < 1) {
                return "Reinscripción: el alumno aún no ha completado ningún nivel del plan.";
            }
        }

        if (grupo.getAsignatura() == null) {
            if (grupo.getNumeroPeriodo() == null) {
                return "El grupo no tiene número de periodo del plan; no se puede validar el nivel respecto al expediente.";
            }
            if (tipo == TipoInscripcion.INSCRIPCION) {
                if (!Integer.valueOf(1).equals(grupo.getNumeroPeriodo())) {
                    return "Inscripción de nuevo ingreso: el grupo debe ser del primer periodo del plan.";
                }
            } else if (!grupo.getNumeroPeriodo().equals(nivelQueCursa)) {
                return "El grupo es del periodo " + grupo.getNumeroPeriodo()
                        + " del plan; el alumno debe cursar el nivel " + nivelQueCursa + ".";
            }
        }

        Asignatura asig = grupo.getAsignatura();
        if (asig != null && asig.getPeriodo() != null && asig.getPeriodo().getNumero() != null) {
            int numPlan = asig.getPeriodo().getNumero();
            if (numPlan != nivelQueCursa) {
                return "La asignatura del grupo es del " + numPlan + "° periodo del plan; el alumno debe cursar el nivel " + nivelQueCursa + ".";
            }
            if (asig.cuentaEnPlanAcademico()) {
                Map<Long, Calificacion> porAsig = progresoAcademicoNivelService.indexarCalificacionesPreferirConfirmada(alumno.getId());
                if (progresoAcademicoNivelService.asignaturaAprobadaConfirmada(porAsig, asig.getId())) {
                    return "El alumno ya tiene aprobada esta materia; no puede cursar de nuevo un periodo ya aprobado.";
                }
            }
        }

        return null;
    }

    private void aplicarDatosInscripcion(AlumnoPrograma ap, Long periodoAcademicoId) {
        if (ap == null) {
            return;
        }
        ap.setPeriodoCursando(1);
        if (periodoAcademicoId != null) {
            PeriodoAcademico pa = periodoAcademicoService.findById(periodoAcademicoId)
                    .orElseThrow(() -> new IllegalStateException("Periodo no encontrado"));
            // Para inscripción: periodoAcademicoId representa el ingreso del alumno a este programa.
            ap.setPeriodoIngreso(pa);
        }
    }

    private void aplicarDatosReinscripcion(Alumno alumno, AlumnoPrograma ap, Long periodoAcademicoId) {
        ProgramaEducativo prog = ap != null ? ap.getPrograma() : null;
        if (prog == null) {
            throw new IllegalStateException("Alumno sin programa");
        }
        int nivel = progresoAcademicoNivelService.calcularNivelActualPlan(alumno.getId(), prog);
        if (ap != null) {
            ap.setPeriodoCursando(nivel);
        }
        if (periodoAcademicoId != null) {
            PeriodoAcademico pa = periodoAcademicoService.findById(periodoAcademicoId)
                    .orElseThrow(() -> new IllegalStateException("Periodo académico no encontrado"));
            // En reinscripción, si el alumno no tiene ingreso definido para el programa, asignarlo.
            if (ap != null && ap.getPeriodoIngreso() == null) {
                ap.setPeriodoIngreso(pa);
            }
        }
    }

    private void quitarDeOtrosGruposMismoPrograma(Long alumnoId, Long grupoDestinoId, Long programaId) {
        List<Grupo> grupos = grupoRepository.findByAlumnos_Id(alumnoId);
        for (Grupo g : grupos) {
            if (g.getId().equals(grupoDestinoId)) {
                continue;
            }
            Long pid = programaIdDelGrupo(g);
            if (pid != null && pid.equals(programaId)) {
                g.getAlumnos().removeIf(a -> a.getId().equals(alumnoId));
                grupoRepository.save(g);
            }
        }
    }
}
