package com.idee.controlescolar.controller;

import com.idee.controlescolar.dto.GrupoRequest;
import com.idee.controlescolar.model.*;
import com.idee.controlescolar.repository.*;
import com.idee.controlescolar.security.RequierePermiso;
import com.idee.controlescolar.service.GestionAcademicaEstadoService;
import com.idee.controlescolar.service.PeriodoAcademicoService;
import com.idee.controlescolar.service.ProgramaAccesoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Controlador REST para gestionar grupos (asignatura + maestro + alumnos).
 * La secretaría académica crea grupos y asigna maestros y alumnos.
 */
@RestController
@RequestMapping("/grupos")
@CrossOrigin(origins = "*")
public class GrupoController {

    @Autowired
    private GrupoRepository grupoRepository;

    @Autowired
    private AsignaturaRepository asignaturaRepository;

    @Autowired
    private MaestroRepository maestroRepository;

    @Autowired
    private AlumnoRepository alumnoRepository;

    @Autowired
    private PeriodoAcademicoService periodoAcademicoService;

    @Autowired
    private ProgramaAccesoService programaAccesoService;

    @Autowired
    private ProgramaEducativoRepository programaEducativoRepository;

    @Autowired
    private CriterioEvaluacionRepository criterioEvaluacionRepository;

    @Autowired
    private HorarioBloqueRepository horarioBloqueRepository;

    @Autowired
    private CalificacionRepository calificacionRepository;

    @Autowired
    private GestionAcademicaEstadoService gestionAcademicaEstadoService;

    @GetMapping
    @RequierePermiso("VER_PROGRAMAS")
    public ResponseEntity<List<Grupo>> listar(
            @RequestParam(required = false) Long maestroId,
            @RequestParam(required = false) Long programaId,
            @RequestParam(required = false) Long asignaturaId,
            @RequestParam(required = false) Long periodoAcademicoId,
            @RequestParam(required = false) Integer periodoNumero,
            Authentication authentication) {
        Usuario u = (authentication != null && authentication.getPrincipal() instanceof Usuario)
                ? (Usuario) authentication.getPrincipal()
                : null;
        List<Grupo> grupos = grupoRepository.findAllForApiList(
                maestroId, programaId, periodoAcademicoId, periodoNumero, asignaturaId);
        if (programaAccesoService.esCoordinadorAcademico(u)) {
            var permitidos = programaAccesoService.programaIdsPermitidos(u);
            if (permitidos.isEmpty()) return ResponseEntity.ok(List.of());
            if (programaId != null && !permitidos.contains(programaId)) return ResponseEntity.status(403).body(List.of());
            grupos = grupos.stream().filter(g -> {
                if (g.getPrograma() != null && permitidos.contains(g.getPrograma().getId())) return true;
                return g.getAsignatura() != null
                        && g.getAsignatura().getPrograma() != null
                        && permitidos.contains(g.getAsignatura().getPrograma().getId());
            }).toList();
        }
        return ResponseEntity.ok(grupos);
    }

    /**
     * Grupos en estatus ACTIVO con al menos un alumno inscrito.
     * Usado en la pantalla de calificaciones del personal administrativo (desplegable de clases).
     */
    @GetMapping("/activos-con-alumnos")
    @RequierePermiso({"VER_PROGRAMAS", "VER_CALIFICACIONES"})
    public ResponseEntity<List<Grupo>> listarActivosConAlumnos(Authentication authentication) {
        Usuario u = (authentication != null && authentication.getPrincipal() instanceof Usuario)
                ? (Usuario) authentication.getPrincipal()
                : null;
        List<Grupo> grupos = grupoRepository.findActivosConAlumnosInscritos();
        if (programaAccesoService.esCoordinadorAcademico(u)) {
            var permitidos = programaAccesoService.programaIdsPermitidos(u);
            if (permitidos.isEmpty()) {
                return ResponseEntity.ok(List.of());
            }
            grupos = grupos.stream().filter(g -> {
                if (g.getPrograma() != null && permitidos.contains(g.getPrograma().getId())) {
                    return true;
                }
                return g.getAsignatura() != null
                        && g.getAsignatura().getPrograma() != null
                        && permitidos.contains(g.getAsignatura().getPrograma().getId());
            }).toList();
        }
        return ResponseEntity.ok(grupos);
    }

    /**
     * Grupos activos con alumnos y al menos una materia en horario activo (todas las clases del grupo, todos los docentes).
     */
    @GetMapping("/activos-con-clases-horario")
    @RequierePermiso({"VER_PROGRAMAS", "VER_CALIFICACIONES"})
    public ResponseEntity<List<Grupo>> listarActivosConClasesHorario(Authentication authentication) {
        Usuario u = (authentication != null && authentication.getPrincipal() instanceof Usuario)
                ? (Usuario) authentication.getPrincipal()
                : null;
        List<Grupo> grupos = grupoRepository.findActivosConAlumnosYClaseEnHorario();
        if (programaAccesoService.esCoordinadorAcademico(u)) {
            var permitidos = programaAccesoService.programaIdsPermitidos(u);
            if (permitidos.isEmpty()) {
                return ResponseEntity.ok(List.of());
            }
            grupos = grupos.stream().filter(g -> {
                if (g.getPrograma() != null && permitidos.contains(g.getPrograma().getId())) {
                    return true;
                }
                return g.getAsignatura() != null
                        && g.getAsignatura().getPrograma() != null
                        && permitidos.contains(g.getAsignatura().getPrograma().getId());
            }).toList();
        }
        return ResponseEntity.ok(grupos);
    }

    @GetMapping("/{id}")
    @RequierePermiso("VER_PROGRAMAS")
    public ResponseEntity<?> obtenerPorId(@PathVariable Long id) {
        return grupoRepository.findWithDetailsForInscripcion(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @RequierePermiso("ACTUALIZAR_GRUPOS")
    public ResponseEntity<?> crear(@RequestBody GrupoRequest request) {
        try {
            if (request.getNombre() == null || request.getNombre().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "El nombre del grupo es requerido."));
            }
            String nombreTrim = request.getNombre().trim();
            if (grupoRepository.existsByNombre(nombreTrim)) {
                return ResponseEntity.badRequest().body(Map.of("message",
                        "Ya existe un grupo con ese nombre. El nombre debe ser único en el sistema."));
            }

            boolean modoAvanzado = request.getAsignaturaId() != null;

            PeriodoAcademico periodo = resolverPeriodoAcademico(request);
            String mensajePeriodo = gestionAcademicaEstadoService.validarEdicionGrupo(periodo);
            if (mensajePeriodo != null) {
                return ResponseEntity.badRequest().body(Map.of("message", mensajePeriodo));
            }
            String cicloNombre = periodo != null ? periodo.getCodigo() : null;

            if (modoAvanzado) {
                Asignatura asignatura = asignaturaRepository.findById(request.getAsignaturaId()).orElse(null);
                if (asignatura == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Asignatura no encontrada."));
                }
                if (cicloNombre == null) {
                    return ResponseEntity.badRequest().body(Map.of("message", "El periodo académico es requerido."));
                }
                ProgramaEducativo prog = asignatura.getPrograma();
                if (prog != null && grupoRepository.existsByNombreAndProgramaIdAndCicloEscolar(
                        nombreTrim, prog.getId(), cicloNombre)) {
                    return ResponseEntity.badRequest().body(Map.of("message",
                            "Ya existe un grupo básico con ese nombre para este programa y ciclo, que cubre todas las asignaturas incluida la seleccionada."));
                }
                if (grupoRepository.existsByNombreAndAsignaturaId(nombreTrim, asignatura.getId())) {
                    return ResponseEntity.badRequest().body(Map.of("message",
                            "Ya existe un grupo con ese nombre para esta asignatura."));
                }
            } else {
                if (request.getProgramaId() == null) {
                    return ResponseEntity.badRequest().body(Map.of("message", "El programa es requerido."));
                }
                if (request.getNumeroPeriodo() == null) {
                    return ResponseEntity.badRequest().body(Map.of("message",
                            "El número de periodo es requerido (ej. primer semestre = 1)."));
                }
                if (grupoRepository.existsByNombreAndProgramaIdAndNumeroPeriodoBasico(
                        nombreTrim, request.getProgramaId(), request.getNumeroPeriodo())) {
                    return ResponseEntity.badRequest().body(Map.of("message",
                            "Ya existe un grupo con ese nombre para este programa y este número de periodo."));
                }
            }
            Grupo grupo = new Grupo();
            grupo.setNombre(nombreTrim);
            grupo.setPeriodoAcademico(periodo);
            grupo.setEstatus(resolverEstatus(request.getEstatus()));
            grupo.setMaestro(null); // El maestro se asigna en la pantalla de horarios

            if (modoAvanzado) {
                asignaturaRepository.findById(request.getAsignaturaId()).ifPresent(a -> {
                    grupo.setAsignatura(a);
                    if (a.getPeriodo() != null) {
                        grupo.setNumeroPeriodo(a.getPeriodo().getNumero());
                    }
                });
            } else {
                programaEducativoRepository.findById(request.getProgramaId()).ifPresent(grupo::setPrograma);
                grupo.setNumeroPeriodo(request.getNumeroPeriodo());
            }

            if (request.getAlumnoIds() != null && !request.getAlumnoIds().isEmpty()) {
                List<Alumno> alumnos = new ArrayList<>();
                for (Long alumnoId : request.getAlumnoIds()) {
                    alumnoRepository.findById(alumnoId).ifPresent(alumnos::add);
                }
                grupo.setAlumnos(alumnos);
            }

            Grupo guardado = grupoRepository.save(grupo);
            return ResponseEntity.status(HttpStatus.CREATED).body(guardado);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al crear grupo: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @RequierePermiso("ACTUALIZAR_GRUPOS")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody GrupoRequest request) {
        Optional<Grupo> opt = grupoRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Grupo grupo = opt.get();
        try {
            PeriodoAcademico periodo = resolverPeriodoAcademico(request);
            String mensajePeriodo = gestionAcademicaEstadoService.validarEdicionGrupo(periodoOperacionGrupo(grupo, periodo));
            if (mensajePeriodo != null) {
                return ResponseEntity.badRequest().body(Map.of("message", mensajePeriodo));
            }
            String mensajeCambioEstructural = validarCambioEstructuralGrupo(grupo, request, periodo);
            if (mensajeCambioEstructural != null) {
                return ResponseEntity.badRequest().body(Map.of("message", mensajeCambioEstructural));
            }
            if (periodo != null) {
                grupo.setPeriodoAcademico(periodo);
            }
            if (request.getAsignaturaId() != null) {
                asignaturaRepository.findById(request.getAsignaturaId())
                        .ifPresent(a -> {
                            grupo.setAsignatura(a);
                            grupo.setPrograma(null);
                            if (a.getPeriodo() != null) {
                                grupo.setNumeroPeriodo(a.getPeriodo().getNumero());
                            }
                        });
            } else if (request.getProgramaId() != null) {
                programaEducativoRepository.findById(request.getProgramaId())
                        .ifPresent(p -> { grupo.setPrograma(p); grupo.setAsignatura(null); });
                if (request.getPeriodoAcademicoId() == null
                        && (request.getPeriodoIngreso() == null || request.getPeriodoIngreso().isBlank())) {
                    grupo.setPeriodoAcademico(null);
                }
            }
            if (request.getNumeroPeriodo() != null && request.getAsignaturaId() == null) {
                grupo.setNumeroPeriodo(request.getNumeroPeriodo());
            }

            if (request.getNombre() != null && !request.getNombre().isBlank()) {
                String nombreTrim = request.getNombre().trim();
                if (grupoRepository.existsByNombreAndIdNot(nombreTrim, id)) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Ya existe otro grupo con ese nombre. El nombre debe ser único en el sistema."));
                }
                grupo.setNombre(nombreTrim);
            }
            grupo.setMaestro(null); // El maestro se asigna en la pantalla de horarios
            if (request.getEstatus() != null && !request.getEstatus().isBlank()) {
                grupo.setEstatus(resolverEstatus(request.getEstatus()));
            }
            if (request.getAlumnoIds() != null) {
                if (grupo.getEstatus() != Grupo.EstatusGrupo.ACTIVO) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("message", "Solo se pueden inscribir alumnos en grupos activos."));
                }
                List<Alumno> alumnos = new ArrayList<>();
                for (Long alumnoId : request.getAlumnoIds()) {
                    alumnoRepository.findById(alumnoId).ifPresent(alumnos::add);
                }
                grupo.setAlumnos(alumnos);
            }
            return ResponseEntity.ok(grupoRepository.save(grupo));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al actualizar grupo: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @RequierePermiso("ACTUALIZAR_GRUPOS")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        Optional<Grupo> optGrupo = grupoRepository.findById(id);
        if (optGrupo.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Grupo grupo = optGrupo.get();
        String mensajePeriodo = gestionAcademicaEstadoService.validarEdicionGrupo(grupo.getPeriodoAcademico());
        if (mensajePeriodo != null) {
            return ResponseEntity.badRequest().body(Map.of("message", mensajePeriodo));
        }
        if (grupoTieneReferencias(grupo)) {
            if (grupo.getEstatus() != Grupo.EstatusGrupo.CANCELADO) {
                grupo.setEstatus(Grupo.EstatusGrupo.CANCELADO);
                grupoRepository.save(grupo);
            }
            return ResponseEntity.ok(Map.of(
                    "message", "El grupo no se elimino fisicamente porque ya tiene referencias academicas; se marco como cancelado.",
                    "grupoId", grupo.getId(),
                    "estatus", grupo.getEstatus().name()
            ));
        }
        grupoRepository.delete(grupo);
        return ResponseEntity.ok(Map.of("message", "Grupo eliminado correctamente.", "grupoId", id));
    }

    /**
     * Quita un alumno de un grupo.
     */
    @DeleteMapping("/{grupoId}/alumnos/{alumnoId}")
    @RequierePermiso("ACTUALIZAR_GRUPOS")
    public ResponseEntity<?> quitarAlumno(@PathVariable Long grupoId, @PathVariable Long alumnoId) {
        Optional<Grupo> optGrupo = grupoRepository.findById(grupoId);
        if (optGrupo.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Grupo grupo = optGrupo.get();
        String mensajePeriodo = gestionAcademicaEstadoService.validarEdicionGrupo(grupo.getPeriodoAcademico());
        if (mensajePeriodo != null) {
            return ResponseEntity.badRequest().body(mensajePeriodo);
        }
        if (calificacionRepository.existsByAlumno_IdAndGrupo_Id(alumnoId, grupoId)) {
            return ResponseEntity.badRequest().body("No se puede retirar al alumno porque el grupo ya tiene calificaciones registradas para esa inscripcion.");
        }
        boolean removido = grupo.getAlumnos().removeIf(a -> a.getId().equals(alumnoId));
        if (!removido) {
            return ResponseEntity.badRequest().body("El alumno no está inscrito en este grupo.");
        }
        grupoRepository.save(grupo);
        return ResponseEntity.ok().build();
    }

    /**
     * Resuelve el estatus del grupo.
     */
    private Grupo.EstatusGrupo resolverEstatus(String estatus) {
        if (estatus == null || estatus.isBlank()) return Grupo.EstatusGrupo.ACTIVO;
        try {
            return Grupo.EstatusGrupo.valueOf(estatus.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return Grupo.EstatusGrupo.ACTIVO;
        }
    }

    /**
     * Resuelve PeriodoAcademico desde periodoAcademicoId o periodoIngreso (código).
     */
    private PeriodoAcademico resolverPeriodoAcademico(GrupoRequest request) {
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

    private PeriodoAcademico periodoOperacionGrupo(Grupo grupo, PeriodoAcademico periodoRequest) {
        return periodoRequest != null ? periodoRequest : (grupo != null ? grupo.getPeriodoAcademico() : null);
    }

    private boolean grupoTieneReferencias(Grupo grupo) {
        if (grupo == null || grupo.getId() == null) {
            return false;
        }
        if (!horarioBloqueRepository.findByGrupoEntity_Id(grupo.getId()).isEmpty()) {
            return true;
        }
        if (calificacionRepository.existsByGrupo_Id(grupo.getId())) {
            return true;
        }
        if (!criterioEvaluacionRepository.findByGrupo_Id(grupo.getId()).isEmpty()) {
            return true;
        }
        return grupo.getAlumnos() != null && !grupo.getAlumnos().isEmpty();
    }

    private boolean grupoTieneUsoAcademico(Grupo grupo) {
        if (grupo == null || grupo.getId() == null) {
            return false;
        }
        return !horarioBloqueRepository.findByGrupoEntity_Id(grupo.getId()).isEmpty()
                || calificacionRepository.existsByGrupo_Id(grupo.getId());
    }

    private String validarCambioEstructuralGrupo(Grupo grupo, GrupoRequest request, PeriodoAcademico periodoResuelto) {
        if (!grupoTieneUsoAcademico(grupo)) {
            return null;
        }
        boolean cambiaNombre = request.getNombre() != null
                && !request.getNombre().isBlank()
                && !request.getNombre().trim().equals(grupo.getNombre());
        Long programaActualId = grupo.getPrograma() != null ? grupo.getPrograma().getId() : null;
        Long asignaturaActualId = grupo.getAsignatura() != null ? grupo.getAsignatura().getId() : null;
        Long periodoActualId = grupo.getPeriodoAcademico() != null ? grupo.getPeriodoAcademico().getId() : null;
        boolean cambiaAsignatura = request.getAsignaturaId() != null && !Objects.equals(asignaturaActualId, request.getAsignaturaId());
        boolean cambiaPrograma = request.getProgramaId() != null
                && (grupo.getAsignatura() != null || !Objects.equals(programaActualId, request.getProgramaId()));
        boolean cambiaNumeroPeriodo = request.getNumeroPeriodo() != null
                && request.getAsignaturaId() == null
                && !Objects.equals(grupo.getNumeroPeriodo(), request.getNumeroPeriodo());
        boolean limpiaPeriodo = request.getProgramaId() != null
                && request.getPeriodoAcademicoId() == null
                && (request.getPeriodoIngreso() == null || request.getPeriodoIngreso().isBlank())
                && periodoActualId != null;
        boolean cambiaPeriodo = periodoResuelto != null
                ? !Objects.equals(periodoActualId, periodoResuelto.getId())
                : limpiaPeriodo;
        if (!cambiaNombre && !cambiaAsignatura && !cambiaPrograma && !cambiaNumeroPeriodo && !cambiaPeriodo) {
            return null;
        }
        return "Este grupo ya tiene horario o calificaciones registradas. Para conservar el historial, solo puedes cambiar su estatus; si necesitas otra configuracion, crea un grupo nuevo y cancela este.";
    }
}
