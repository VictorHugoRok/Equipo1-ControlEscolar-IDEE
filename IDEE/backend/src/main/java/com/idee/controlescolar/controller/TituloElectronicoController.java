package com.idee.controlescolar.controller;

import com.idee.controlescolar.dto.TituloElectronicoRequest;
import com.idee.controlescolar.dto.TituloElectronicoResponse;
import com.idee.controlescolar.model.EstatusTitulo;
import com.idee.controlescolar.service.TituloElectronicoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controlador REST para la gestión de títulos profesionales electrónicos.
 */
@RestController
@RequestMapping("/api/titulos-electronicos")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class TituloElectronicoController {

    private final TituloElectronicoService tituloService;

    /**
     * Genera un nuevo título profesional electrónico.
     *
     * POST /api/titulos-electronicos
     */
    @PostMapping
    public ResponseEntity<TituloElectronicoResponse> generarTitulo(
            @Valid @RequestBody TituloElectronicoRequest request) {
        try {
            log.info("Solicitud de generación de título para alumno: {}", request.getAlumnoId());
            TituloElectronicoResponse response = tituloService.generarTitulo(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error al generar título: {}", e.getMessage(), e);
            throw new RuntimeException("Error al generar título: " + e.getMessage());
        }
    }

    /**
     * Obtiene todos los títulos de un alumno.
     *
     * GET /api/titulos-electronicos/alumno/{alumnoId}
     */
    @GetMapping("/alumno/{alumnoId}")
    public ResponseEntity<List<TituloElectronicoResponse>> obtenerTitulosPorAlumno(
            @PathVariable Long alumnoId) {
        try {
            List<TituloElectronicoResponse> titulos = tituloService.obtenerTitulosPorAlumno(alumnoId);
            return ResponseEntity.ok(titulos);
        } catch (Exception e) {
            log.error("Error al obtener títulos del alumno {}: {}", alumnoId, e.getMessage());
            throw new RuntimeException("Error al obtener títulos: " + e.getMessage());
        }
    }

    /**
     * Obtiene un título por su ID.
     *
     * GET /api/titulos-electronicos/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<TituloElectronicoResponse> obtenerTitulo(@PathVariable Long id) {
        try {
            TituloElectronicoResponse titulo = tituloService.obtenerTituloPorId(id);
            return ResponseEntity.ok(titulo);
        } catch (Exception e) {
            log.error("Error al obtener título {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Busca un título por su folio de control.
     *
     * GET /api/titulos-electronicos/folio/{folioControl}
     */
    @GetMapping("/folio/{folioControl}")
    public ResponseEntity<TituloElectronicoResponse> obtenerTituloPorFolio(
            @PathVariable String folioControl) {
        try {
            TituloElectronicoResponse titulo = tituloService.obtenerTituloPorFolio(folioControl);
            return ResponseEntity.ok(titulo);
        } catch (Exception e) {
            log.error("Error al buscar título con folio {}: {}", folioControl, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Actualiza el estatus de un título.
     *
     * PUT /api/titulos-electronicos/{id}/estatus
     */
    @PutMapping("/{id}/estatus")
    public ResponseEntity<TituloElectronicoResponse> actualizarEstatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        try {
            String estatusStr = body.get("estatus");
            EstatusTitulo estatus = EstatusTitulo.valueOf(estatusStr);

            TituloElectronicoResponse titulo = tituloService.actualizarEstatus(id, estatus);
            return ResponseEntity.ok(titulo);
        } catch (IllegalArgumentException e) {
            log.error("Estatus inválido: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error al actualizar estatus del título {}: {}", id, e.getMessage());
            throw new RuntimeException("Error al actualizar estatus: " + e.getMessage());
        }
    }

    /**
     * Descarga el archivo XML de un título.
     *
     * GET /api/titulos-electronicos/{id}/descargar-xml
     */
    @GetMapping("/{id}/descargar-xml")
    public ResponseEntity<byte[]> descargarXml(@PathVariable Long id) {
        try {
            byte[] xmlBytes = tituloService.descargarXml(id);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            headers.setContentDispositionFormData("attachment", "titulo_" + id + ".xml");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(xmlBytes);
        } catch (Exception e) {
            log.error("Error al descargar XML del título {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Valida los requisitos de un alumno para obtener título.
     *
     * GET /api/titulos-electronicos/validar-requisitos/{alumnoId}
     */
    @GetMapping("/validar-requisitos/{alumnoId}")
    public ResponseEntity<Map<String, Object>> validarRequisitos(@PathVariable Long alumnoId) {
        try {
            boolean cumpleRequisitos = tituloService.validarRequisitosAlumno(alumnoId);

            return ResponseEntity.ok(Map.of(
                    "alumnoId", alumnoId,
                    "cumpleRequisitos", cumpleRequisitos,
                    "mensaje", "El alumno cumple con todos los requisitos"
            ));
        } catch (Exception e) {
            log.warn("El alumno {} no cumple requisitos: {}", alumnoId, e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "alumnoId", alumnoId,
                    "cumpleRequisitos", false,
                    "mensaje", e.getMessage()
            ));
        }
    }

    /**
     * Manejo de excepciones global para este controlador.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException e) {
        log.error("Error en TituloElectronicoController: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "Error al procesar la solicitud",
                        "mensaje", e.getMessage()
                ));
    }
}
