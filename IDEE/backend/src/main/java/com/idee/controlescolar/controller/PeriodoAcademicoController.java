package com.idee.controlescolar.controller;

import com.idee.controlescolar.dto.CambiarEstadoGestionRequest;
import com.idee.controlescolar.dto.CrearPeriodoAcademicoGestionRequest;
import com.idee.controlescolar.model.EstadoGestionPeriodoAcademico;
import com.idee.controlescolar.model.PeriodoAcademico;
import com.idee.controlescolar.model.ProgramaEducativo;
import com.idee.controlescolar.repository.ProgramaEducativoRepository;
import com.idee.controlescolar.security.RequierePermiso;
import com.idee.controlescolar.service.PeriodoAcademicoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * API para catálogo de periodos académicos.
 * Usado para periodo de ingreso, egreso, calificaciones, grupos y horarios.
 */
@RestController
@RequestMapping("/periodos-academicos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PeriodoAcademicoController {

    private final PeriodoAcademicoService periodoAcademicoService;
    private final ProgramaEducativoRepository programaEducativoRepository;

    @GetMapping
    @RequierePermiso("VER_ALUMNOS")
    public ResponseEntity<List<PeriodoAcademico>> listar(
            @RequestParam(required = false) Long programaId,
            @RequestParam(required = false) String tipoPeriodo
    ) {
        if (programaId != null) {
            ProgramaEducativo p = programaEducativoRepository.findById(programaId).orElse(null);
            ProgramaEducativo.TipoPeriodo tipo = (p != null && p.getTipoPeriodo() != null)
                    ? p.getTipoPeriodo()
                    : ProgramaEducativo.TipoPeriodo.SEMESTRE;
            return ResponseEntity.ok(periodoAcademicoService.listarDisponiblesPorTipo(tipo));
        }
        ProgramaEducativo.TipoPeriodo tipo = null;
        if (tipoPeriodo != null && !tipoPeriodo.isBlank()) {
            try {
                tipo = ProgramaEducativo.TipoPeriodo.valueOf(tipoPeriodo.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        if (tipo != null) {
            return ResponseEntity.ok(periodoAcademicoService.listarDisponiblesPorTipo(tipo));
        }
        return ResponseEntity.ok(periodoAcademicoService.listarDisponibles());
    }

    /**
     * Periodo académico en estado ACTIVO según el tipo de periodo del programa (semestre, cuatrimestre, etc.).
     * Requerido para inscripciones: si no hay ninguno, devuelve 400 con mensaje claro.
     */
    @GetMapping("/activo-por-programa/{programaId}")
    @RequierePermiso("VER_ALUMNOS")
    public ResponseEntity<?> obtenerActivoPorPrograma(@PathVariable Long programaId) {
        ProgramaEducativo p = programaEducativoRepository.findById(programaId).orElse(null);
        if (p == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Programa educativo no encontrado."));
        }
        ProgramaEducativo.TipoPeriodo tipo = p.getTipoPeriodo() != null
                ? p.getTipoPeriodo()
                : ProgramaEducativo.TipoPeriodo.SEMESTRE;
        return periodoAcademicoService.obtenerPeriodoActivoPorTipo(tipo)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.badRequest().body(Map.of(
                        "error",
                        "No hay un periodo academico en estado Activo para el tipo " + tipo
                                + ". Configuralo en \"Calendario academico\".")));
    }

    @GetMapping("/activo")
    @RequierePermiso("VER_ALUMNOS")
    public ResponseEntity<?> obtenerActivo(
            @RequestParam(required = false) String tipoPeriodo
    ) {
        ProgramaEducativo.TipoPeriodo tipo = null;
        if (tipoPeriodo != null && !tipoPeriodo.isBlank()) {
            try {
                tipo = ProgramaEducativo.TipoPeriodo.valueOf(tipoPeriodo.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        if (tipo != null) {
            var optTipo = periodoAcademicoService.obtenerPeriodoActivoPorTipo(tipo);
            if (optTipo.isPresent()) {
                return ResponseEntity.ok(optTipo.get());
            }
        } else {
            var opt = periodoAcademicoService.obtenerPeriodoActivo();
            if (opt.isPresent()) {
                return ResponseEntity.ok(opt.get());
            }
        }
        return ResponseEntity.ok(Map.of("codigo", periodoAcademicoService.codigoPeriodoVigenteOCalculado(
                tipo != null ? tipo : ProgramaEducativo.TipoPeriodo.SEMESTRE)));
    }

    @GetMapping("/codigo-actual")
    public ResponseEntity<Map<String, String>> codigoActual(
            @RequestParam(required = false) String tipoPeriodo) {
        ProgramaEducativo.TipoPeriodo tipo = null;
        if (tipoPeriodo != null && !tipoPeriodo.isBlank()) {
            try {
                tipo = ProgramaEducativo.TipoPeriodo.valueOf(tipoPeriodo.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        String codigo = periodoAcademicoService.codigoPeriodoVigenteOCalculado(
                tipo != null ? tipo : ProgramaEducativo.TipoPeriodo.SEMESTRE);
        return ResponseEntity.ok(Map.of("codigo", codigo));
    }

    @GetMapping("/por-ciclo/{cicloId}")
    @RequierePermiso("VER_ALUMNOS")
    public ResponseEntity<List<PeriodoAcademico>> listarPorCiclo(@PathVariable Long cicloId) {
        return ResponseEntity.ok(periodoAcademicoService.listarPorCiclo(cicloId));
    }

    @PostMapping("/alta-administrativa")
    @RequierePermiso("GESTIONAR_CICLOS_Y_PERIODOS")
    public ResponseEntity<PeriodoAcademico> altaAdministrativa(@RequestBody CrearPeriodoAcademicoGestionRequest body) {
        if (body == null || body.getCicloId() == null) {
            throw new IllegalArgumentException("cicloId es obligatorio.");
        }
        ProgramaEducativo.TipoPeriodo tipo = ProgramaEducativo.TipoPeriodo.SEMESTRE;
        if (body.getTipoPeriodo() != null && !body.getTipoPeriodo().isBlank()) {
            try {
                tipo = ProgramaEducativo.TipoPeriodo.valueOf(body.getTipoPeriodo().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("tipoPeriodo inválido.");
            }
        }
        PeriodoAcademico p = periodoAcademicoService.crearPeriodoAdministrativo(
                body.getCicloId(),
                tipo,
                body.getNumero() != null ? body.getNumero() : 1,
                body.getNombre(),
                body.getCodigo(),
                body.getFechaInicio(),
                body.getFechaFin());
        return ResponseEntity.ok(p);
    }

    @PatchMapping("/{id}/estado-gestion")
    @RequierePermiso("GESTIONAR_CICLOS_Y_PERIODOS")
    public ResponseEntity<PeriodoAcademico> cambiarEstadoGestion(
            @PathVariable Long id,
            @RequestBody CambiarEstadoGestionRequest body) {
        if (body == null || body.getEstado() == null || body.getEstado().isBlank()) {
            throw new IllegalArgumentException("estado es obligatorio (INACTIVO, ACTIVO, CERRADO).");
        }
        EstadoGestionPeriodoAcademico nuevo;
        try {
            nuevo = EstadoGestionPeriodoAcademico.valueOf(body.getEstado().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado inválido.");
        }
        return ResponseEntity.ok(periodoAcademicoService.cambiarEstadoGestion(id, nuevo));
    }

    @PutMapping("/{id}")
    @RequierePermiso("GESTIONAR_CICLOS_Y_PERIODOS")
    public ResponseEntity<PeriodoAcademico> actualizarAdministrativo(
            @PathVariable Long id,
            @RequestBody CrearPeriodoAcademicoGestionRequest body) {
        if (body == null) {
            throw new IllegalArgumentException("Cuerpo de la petición vacío.");
        }
        ProgramaEducativo.TipoPeriodo tipo = ProgramaEducativo.TipoPeriodo.SEMESTRE;
        if (body.getTipoPeriodo() != null && !body.getTipoPeriodo().isBlank()) {
            try {
                tipo = ProgramaEducativo.TipoPeriodo.valueOf(body.getTipoPeriodo().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("tipoPeriodo inválido.");
            }
        }
        PeriodoAcademico p = periodoAcademicoService.actualizarPeriodoAdministrativo(
                id,
                body.getCicloId(),
                tipo,
                body.getNumero() != null ? body.getNumero() : 1,
                body.getNombre(),
                body.getCodigo(),
                body.getFechaInicio(),
                body.getFechaFin());
        return ResponseEntity.ok(p);
    }

    @DeleteMapping("/{id}")
    @RequierePermiso("GESTIONAR_CICLOS_Y_PERIODOS")
    public ResponseEntity<Void> eliminarAdministrativo(@PathVariable Long id) {
        periodoAcademicoService.eliminarPeriodoAdministrativo(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @RequierePermiso("VER_ALUMNOS")
    public ResponseEntity<?> obtenerPorId(@PathVariable Long id) {
        return periodoAcademicoService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/por-codigo/{codigo}")
    @RequierePermiso("VER_ALUMNOS")
    public ResponseEntity<?> obtenerPorCodigo(
            @PathVariable String codigo,
            @RequestParam(required = false) String tipoPeriodo
    ) {
        ProgramaEducativo.TipoPeriodo tipo = null;
        if (tipoPeriodo != null && !tipoPeriodo.isBlank()) {
            try {
                tipo = ProgramaEducativo.TipoPeriodo.valueOf(tipoPeriodo.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        if (tipo != null) {
            return periodoAcademicoService.findByTipoYCodigo(tipo, codigo)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }
        return periodoAcademicoService.findByCodigo(codigo)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
