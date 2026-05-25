package com.idee.controlescolar.controller;

import com.idee.controlescolar.dto.EvaluacionDocenteCrearFormularioRequest;
import com.idee.controlescolar.dto.EvaluacionDocenteInformeRequest;
import com.idee.controlescolar.dto.EvaluacionDocenteResponderRequest;
import com.idee.controlescolar.model.Alumno;
import com.idee.controlescolar.model.Maestro;
import com.idee.controlescolar.model.Usuario;
import com.idee.controlescolar.repository.AlumnoRepository;
import com.idee.controlescolar.repository.MaestroRepository;
import com.idee.controlescolar.security.RequierePermiso;
import com.idee.controlescolar.service.EvaluacionDocenteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/evaluaciones-docente")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EvaluacionDocenteController {

    private final EvaluacionDocenteService evaluacionDocenteService;
    private final AlumnoRepository alumnoRepository;
    private final MaestroRepository maestroRepository;

    @PostMapping("/formularios")
    @RequierePermiso("GESTIONAR_EVALUACION_DOCENTE")
    public ResponseEntity<?> crearFormulario(@RequestBody EvaluacionDocenteCrearFormularioRequest body) {
        return ResponseEntity.ok(evaluacionDocenteService.crearFormulario(body));
    }

    @GetMapping("/formularios")
    @RequierePermiso("GESTIONAR_EVALUACION_DOCENTE")
    public ResponseEntity<List<Map<String, Object>>> listarFormularios() {
        return ResponseEntity.ok(evaluacionDocenteService.listarFormulariosResumen());
    }

    @GetMapping("/formularios/{id}")
    @RequierePermiso("GESTIONAR_EVALUACION_DOCENTE")
    public ResponseEntity<?> obtenerFormulario(@PathVariable Long id) {
        return ResponseEntity.ok(evaluacionDocenteService.obtenerFormularioDetalle(id));
    }

    @PutMapping("/formularios/{id}")
    @RequierePermiso("GESTIONAR_EVALUACION_DOCENTE")
    public ResponseEntity<?> actualizarFormulario(@PathVariable Long id, @RequestBody EvaluacionDocenteCrearFormularioRequest body) {
        return ResponseEntity.ok(evaluacionDocenteService.actualizarFormularioCompleto(id, body));
    }

    @DeleteMapping("/formularios/{id}")
    @RequierePermiso("GESTIONAR_EVALUACION_DOCENTE")
    public ResponseEntity<?> eliminarFormulario(@PathVariable Long id) {
        evaluacionDocenteService.eliminarFormulario(id);
        return ResponseEntity.ok(Map.of("message", "Evaluación eliminada."));
    }

    @GetMapping("/excel/plantilla")
    @RequierePermiso("GESTIONAR_EVALUACION_DOCENTE")
    public ResponseEntity<byte[]> descargarPlantillaExcel() {
        byte[] bytes = evaluacionDocenteService.generarPlantillaExcel();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"evaluacion_docente_plantilla.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @GetMapping("/excel/formulario/{id}")
    @RequierePermiso("GESTIONAR_EVALUACION_DOCENTE")
    public ResponseEntity<byte[]> descargarFormularioExcel(@PathVariable Long id) {
        byte[] bytes = evaluacionDocenteService.generarExcelDeFormulario(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"evaluacion_docente_" + id + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @PostMapping(value = "/excel/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequierePermiso("GESTIONAR_EVALUACION_DOCENTE")
    public ResponseEntity<?> importarDesdeExcel(@RequestPart("archivo") MultipartFile archivo) {
        evaluacionDocenteService.importarDesdeExcel(archivo);
        return ResponseEntity.ok(Map.of("message", "Excel importado."));
    }

    @GetMapping("/alumno/contexto")
    @RequierePermiso("RESPONDER_EVALUACION_DOCENTE")
    public ResponseEntity<?> contextoAlumno(Authentication authentication) {
        Alumno a = obtenerAlumno(authentication);
        if (a == null) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(evaluacionDocenteService.contextoParaAlumno(a));
    }

    @GetMapping("/secretaria/contexto")
    @RequierePermiso("APLICAR_EVALUACION_ACADEMICA")
    public ResponseEntity<?> contextoSecretaria(Authentication authentication) {
        Usuario u = obtenerUsuario(authentication);
        if (u == null || !(u.tieneRol(Usuario.TipoUsuario.SECRETARIA_ACADEMICA)
                || u.tieneRol(Usuario.TipoUsuario.COORDINADOR_ACADEMICO)
                || u.tieneRol(Usuario.TipoUsuario.ADMIN))) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(evaluacionDocenteService.contextoParaSecretaria(u));
    }

    @GetMapping("/prueba/contexto")
    @RequierePermiso("GESTIONAR_EVALUACION_DOCENTE")
    public ResponseEntity<?> contextoPruebaAdmin(@RequestParam Long formularioId) {
        return ResponseEntity.ok(evaluacionDocenteService.contextoPruebaParaAdmin(formularioId));
    }

    @PostMapping("/alumno/responder")
    @RequierePermiso("RESPONDER_EVALUACION_DOCENTE")
    public ResponseEntity<?> responder(Authentication authentication, @RequestBody EvaluacionDocenteResponderRequest body) {
        Alumno a = obtenerAlumno(authentication);
        if (a == null) {
            return ResponseEntity.status(403).build();
        }
        evaluacionDocenteService.guardarRespuestaAlumno(a, body);
        return ResponseEntity.ok(Map.of("message", "Evaluación registrada. Gracias."));
    }

    @PostMapping("/secretaria/responder")
    @RequierePermiso("APLICAR_EVALUACION_ACADEMICA")
    public ResponseEntity<?> responderSecretaria(Authentication authentication, @RequestBody EvaluacionDocenteResponderRequest body) {
        Usuario u = obtenerUsuario(authentication);
        if (u == null || !(u.tieneRol(Usuario.TipoUsuario.SECRETARIA_ACADEMICA)
                || u.tieneRol(Usuario.TipoUsuario.COORDINADOR_ACADEMICO)
                || u.tieneRol(Usuario.TipoUsuario.ADMIN))) {
            return ResponseEntity.status(403).build();
        }
        evaluacionDocenteService.guardarRespuestaSecretaria(u, body);
        return ResponseEntity.ok(Map.of("message", "Evaluación registrada. Gracias."));
    }

    @GetMapping("/secretaria/informe/detalle")
    @RequierePermiso("APLICAR_EVALUACION_ACADEMICA")
    public ResponseEntity<?> informeAcademicoDetalle(
            Authentication authentication,
            @RequestParam Long formularioId,
            @RequestParam Long maestroId,
            @RequestParam Long horarioBloqueId) {
        Usuario u = obtenerUsuario(authentication);
        if (u == null || !(u.tieneRol(Usuario.TipoUsuario.SECRETARIA_ACADEMICA)
                || u.tieneRol(Usuario.TipoUsuario.COORDINADOR_ACADEMICO)
                || u.tieneRol(Usuario.TipoUsuario.ADMIN))) {
            return ResponseEntity.status(403).build();
        }
        try {
            return ResponseEntity.ok(evaluacionDocenteService.informeAcademicoDetalleParaAdministrativo(
                    u, formularioId, maestroId, horarioBloqueId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/secretaria/informe")
    @RequierePermiso("APLICAR_EVALUACION_ACADEMICA")
    public ResponseEntity<?> guardarInformeAcademico(
            Authentication authentication,
            @RequestBody EvaluacionDocenteInformeRequest body) {
        Usuario u = obtenerUsuario(authentication);
        if (u == null || !(u.tieneRol(Usuario.TipoUsuario.SECRETARIA_ACADEMICA)
                || u.tieneRol(Usuario.TipoUsuario.COORDINADOR_ACADEMICO)
                || u.tieneRol(Usuario.TipoUsuario.ADMIN))) {
            return ResponseEntity.status(403).build();
        }
        if (body == null || body.getFormularioId() == null || body.getMaestroId() == null || body.getHorarioBloqueId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "formularioId, maestroId y horarioBloqueId son obligatorios."));
        }
        try {
            return ResponseEntity.ok(evaluacionDocenteService.actualizarInformeAcademicoParaDocente(
                    u, body.getFormularioId(), body.getMaestroId(), body.getHorarioBloqueId(), body.getInformeParaDocente()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/maestro/informes-academicos")
    @RequierePermiso("VER_RESULTADOS_EVALUACION_DOCENTE")
    public ResponseEntity<List<Map<String, Object>>> informesAcademicosDocente(Authentication authentication) {
        Maestro m = obtenerMaestro(authentication);
        if (m == null) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(evaluacionDocenteService.listarInformesAcademicosParaDocente(m));
    }

    @GetMapping("/maestro/informes-academicos/resumen")
    @RequierePermiso("VER_RESULTADOS_EVALUACION_DOCENTE")
    public ResponseEntity<Map<String, Object>> informesAcademicosResumenDocente(Authentication authentication) {
        Maestro m = obtenerMaestro(authentication);
        if (m == null) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(evaluacionDocenteService.resumenInformesAcademicosParaMaestro(m));
    }

    @PostMapping("/maestro/informes-academicos/{respuestaId}/marcar-leido")
    @RequierePermiso("VER_RESULTADOS_EVALUACION_DOCENTE")
    public ResponseEntity<?> marcarInformeAcademicoLeido(
            Authentication authentication,
            @PathVariable Long respuestaId) {
        Maestro m = obtenerMaestro(authentication);
        if (m == null) {
            return ResponseEntity.status(403).build();
        }
        try {
            return ResponseEntity.ok(evaluacionDocenteService.marcarInformeAcademicoLeidoParaMaestro(m, respuestaId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/prueba/responder")
    @RequierePermiso("GESTIONAR_EVALUACION_DOCENTE")
    public ResponseEntity<?> responderPruebaAdmin(@RequestBody EvaluacionDocenteResponderRequest body) {
        evaluacionDocenteService.validarRespuestaPrueba(body);
        return ResponseEntity.ok(Map.of("message", "Respuestas recibidas. (No se guardaron)"));
    }

    @GetMapping("/maestro/resultados")
    @RequierePermiso("VER_RESULTADOS_EVALUACION_DOCENTE")
    public ResponseEntity<?> resultadosMaestro(
            Authentication authentication,
            @RequestParam(required = false) Long formularioId,
            @RequestParam(required = false) Long horarioBloqueId) {
        Maestro m = obtenerMaestro(authentication);
        if (m == null) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(evaluacionDocenteService.resultadosAnonimosParaMaestro(m, formularioId, horarioBloqueId));
    }

    @GetMapping("/autoevaluacion/contexto")
    @RequierePermiso("RESPONDER_AUTOEVALUACION")
    public ResponseEntity<?> contextoAutoevaluacion(Authentication authentication) {
        Maestro m = obtenerMaestro(authentication);
        if (m == null) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(evaluacionDocenteService.contextoParaAutoevaluacion(m));
    }

    @PostMapping("/autoevaluacion/responder")
    @RequierePermiso("RESPONDER_AUTOEVALUACION")
    public ResponseEntity<?> responderAutoevaluacion(Authentication authentication, @RequestBody EvaluacionDocenteResponderRequest body) {
        Maestro m = obtenerMaestro(authentication);
        if (m == null) {
            return ResponseEntity.status(403).build();
        }
        evaluacionDocenteService.guardarRespuestaAutoevaluacion(m, body);
        return ResponseEntity.ok(Map.of("message", "Autoevaluación registrada. Gracias."));
    }

    private Alumno obtenerAlumno(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Usuario u = (Usuario) authentication.getPrincipal();
        if (u.getTipoUsuario() != Usuario.TipoUsuario.ALUMNO) {
            return null;
        }
        return alumnoRepository.findByUsuarioId(u.getId()).orElse(null);
    }

    private Maestro obtenerMaestro(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Usuario u = (Usuario) authentication.getPrincipal();
        if (!u.tieneRol(Usuario.TipoUsuario.MAESTRO)) {
            return null;
        }
        return maestroRepository.findByUsuarioId(u.getId()).orElse(null);
    }

    private Usuario obtenerUsuario(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return (Usuario) authentication.getPrincipal();
    }
}
