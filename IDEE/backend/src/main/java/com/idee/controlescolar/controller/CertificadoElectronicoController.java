package com.idee.controlescolar.controller;

import com.idee.controlescolar.dto.CertificadoElectronicoBatchRequest;
import com.idee.controlescolar.dto.CertificadoElectronicoBatchResponse;
import com.idee.controlescolar.dto.CertificadoElectronicoRequest;
import com.idee.controlescolar.dto.CertificadoElectronicoResponse;
import com.idee.controlescolar.service.CertificadoElectronicoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para certificados electrónicos (DEC).
 * Misma seguridad que el resto: requiere autenticación JWT.
 */
@RestController
@RequestMapping("/certificados-electronicos")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class CertificadoElectronicoController {

    private final CertificadoElectronicoService certificadoService;

    /**
     * Generación masiva (1..50) en un solo disparo.
     * POST /api/certificados-electronicos/batch
     */
    @PostMapping("/batch")
    public ResponseEntity<CertificadoElectronicoBatchResponse> generarBatch(@Valid @RequestBody CertificadoElectronicoBatchRequest request) {
        try {
            CertificadoElectronicoBatchResponse response = certificadoService.generarCertificadosBatch(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error en generación batch de certificados: {}", e.getMessage(), e);
            throw new RuntimeException("Error al generar certificados: " + e.getMessage());
        }
    }

    /**
     * Genera un certificado electrónico (DEC).
     * POST /api/certificados-electronicos
     */
    @PostMapping
    public ResponseEntity<CertificadoElectronicoResponse> generarCertificado(
            @Valid @RequestBody CertificadoElectronicoRequest request) {
        try {
            log.info("Solicitud de generación de certificado para alumno: {}", request.getAlumnoId());
            CertificadoElectronicoResponse response = certificadoService.generarCertificado(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error al generar certificado: {}", e.getMessage(), e);
            throw new RuntimeException("Error al generar certificado: " + e.getMessage());
        }
    }

    /**
     * Obtiene todos los certificados de un alumno.
     * GET /api/certificados-electronicos/alumno/{alumnoId}
     */
    @GetMapping("/alumno/{alumnoId}")
    public ResponseEntity<List<CertificadoElectronicoResponse>> obtenerPorAlumno(
            @PathVariable Long alumnoId) {
        try {
            List<CertificadoElectronicoResponse> lista = certificadoService.obtenerPorAlumno(alumnoId);
            return ResponseEntity.ok(lista);
        } catch (Exception e) {
            log.error("Error al obtener certificados del alumno {}: {}", alumnoId, e.getMessage());
            throw new RuntimeException("Error al obtener certificados: " + e.getMessage());
        }
    }

    /**
     * Obtiene todos los certificados existentes, ordenados del más reciente al más antiguo.
     * GET /api/certificados-electronicos
     */
    @GetMapping
    public ResponseEntity<List<CertificadoElectronicoResponse>> obtenerTodos() {
        try {
            List<CertificadoElectronicoResponse> lista = certificadoService.obtenerTodosOrdenadosRecientes();
            return ResponseEntity.ok(lista);
        } catch (Exception e) {
            log.error("Error al obtener certificados: {}", e.getMessage());
            throw new RuntimeException("Error al obtener certificados: " + e.getMessage());
        }
    }

    /**
     * Obtiene un certificado por su ID.
     * GET /api/certificados-electronicos/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<CertificadoElectronicoResponse> obtenerPorId(@PathVariable Long id) {
        try {
            CertificadoElectronicoResponse cert = certificadoService.obtenerPorId(id);
            return ResponseEntity.ok(cert);
        } catch (Exception e) {
            log.error("Error al obtener certificado {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Vista previa del certificado en formato PDF.
     * GET /api/certificados-electronicos/{id}/vista-previa
     * Devuelve el PDF para visualización en el navegador.
     */
    @GetMapping(value = "/{id}/vista-previa", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> vistaPrevia(@PathVariable Long id) {
        try {
            var cert = certificadoService.obtenerPorId(id);
            if (cert != null && !cert.isValidoXsd()) {
                return ResponseEntity.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(java.util.Map.of("error", "Este certificado no pasó la validación XSD. No se puede visualizar."));
            }
            byte[] pdf = certificadoService.obtenerPdfVistaPrevia(id);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (Exception e) {
            log.error("Error al obtener vista previa del certificado {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Descarga el certificado como PDF.
     * GET /api/certificados-electronicos/{id}/descargar-pdf
     */
    @GetMapping("/{id}/descargar-pdf")
    public ResponseEntity<?> descargarPdf(@PathVariable Long id) {
        try {
            var cert = certificadoService.obtenerPorId(id);
            if (cert != null && !cert.isValidoXsd()) {
                return ResponseEntity.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(java.util.Map.of("error", "Este certificado no pasó la validación XSD. No se puede descargar."));
            }
            byte[] pdf = certificadoService.obtenerPdfVistaPrevia(id);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            String folio = (cert != null && cert.getFolioControl() != null) ? cert.getFolioControl() : ("certificado_" + id);
            String full = (cert != null && cert.getAlumnoNombreCompleto() != null) ? cert.getAlumnoNombreCompleto().trim() : "";
            String[] parts = full.isBlank() ? new String[0] : full.split("\\s+");
            String ap = parts.length >= 2 ? parts[parts.length - 2] : "";
            String am = parts.length >= 1 ? parts[parts.length - 1] : "";
            String fn = (folio + "_" + ap + "_" + am).replaceAll("[\\\\/:*?\"<>|]+", "").trim();
            if (fn.isBlank()) fn = "certificado_" + id;
            headers.setContentDispositionFormData("attachment", fn + ".pdf");
            return ResponseEntity.ok().headers(headers).body(pdf);
        } catch (Exception e) {
            log.error("Error al descargar PDF del certificado {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Descarga el XML del certificado.
     * GET /api/certificados-electronicos/{id}/descargar-xml
     */
    @GetMapping("/{id}/descargar-xml")
    public ResponseEntity<?> descargarXml(@PathVariable Long id) {
        try {
            var cert = certificadoService.obtenerPorId(id);
            if (cert != null && !cert.isValidoXsd()) {
                return ResponseEntity.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(java.util.Map.of("error", "Este certificado no pasó la validación XSD. No se puede descargar."));
            }
            byte[] xmlBytes = certificadoService.obtenerXmlComoBytes(id);
            if (xmlBytes == null || xmlBytes.length == 0) {
                return ResponseEntity.notFound().build();
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            String folio = (cert != null && cert.getFolioControl() != null) ? cert.getFolioControl() : ("certificado_" + id);
            String full = (cert != null && cert.getAlumnoNombreCompleto() != null) ? cert.getAlumnoNombreCompleto().trim() : "";
            String[] parts = full.isBlank() ? new String[0] : full.split("\\s+");
            String ap = parts.length >= 2 ? parts[parts.length - 2] : "";
            String am = parts.length >= 1 ? parts[parts.length - 1] : "";
            String fn = (folio + "_" + ap + "_" + am).replaceAll("[\\\\/:*?\"<>|]+", "").trim();
            if (fn.isBlank()) fn = "certificado_" + id;
            headers.setContentDispositionFormData("attachment", fn + ".xml");
            return ResponseEntity.ok().headers(headers).body(xmlBytes);
        } catch (Exception e) {
            log.error("Error al descargar XML del certificado {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Descarga la cadena original del certificado empaquetada en XML (solo para pruebas).
     * GET /api/certificados-electronicos/{id}/cadena-original
     */
    @GetMapping("/{id}/cadena-original")
    public ResponseEntity<?> descargarCadenaOriginal(@PathVariable Long id) {
        try {
            var cert = certificadoService.obtenerPorId(id);
            byte[] xmlBytes = certificadoService.obtenerCadenaOriginalComoXml(id);
            if (xmlBytes == null || xmlBytes.length == 0) {
                return ResponseEntity.notFound().build();
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            String folio = (cert != null && cert.getFolioControl() != null) ? cert.getFolioControl() : ("cadena_original_" + id);
            String full = (cert != null && cert.getAlumnoNombreCompleto() != null) ? cert.getAlumnoNombreCompleto().trim() : "";
            String[] parts = full.isBlank() ? new String[0] : full.split("\\s+");
            String ap = parts.length >= 2 ? parts[parts.length - 2] : "";
            String am = parts.length >= 1 ? parts[parts.length - 1] : "";
            String fn = ("cadena_original_" + folio + "_" + ap + "_" + am).replaceAll("[\\\\/:*?\"<>|]+", "").trim();
            if (fn.isBlank()) fn = "cadena_original_" + id;
            headers.setContentDispositionFormData("attachment", fn + ".xml");
            return ResponseEntity.ok().headers(headers).body(xmlBytes);
        } catch (Exception e) {
            log.error("Error al descargar cadena original del certificado {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Elimina todos los certificados electrónicos.
     * DELETE /api/certificados-electronicos
     * Pensado para limpieza de datos de prueba en entornos de desarrollo.
     */
    @DeleteMapping
    public ResponseEntity<java.util.Map<String, String>> eliminarTodos() {
        try {
            certificadoService.eliminarTodos();
            return ResponseEntity.ok(java.util.Map.of("mensaje", "Todos los certificados electrónicos han sido eliminados."));
        } catch (Exception e) {
            log.error("Error al eliminar todos los certificados: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of(
                            "error", "No se pudieron eliminar los certificados",
                            "mensaje", e.getMessage()
                    ));
        }
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<java.util.Map<String, String>> handleRuntimeException(RuntimeException e) {
        log.error("Error en CertificadoElectronicoController: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(java.util.Map.of(
                        "error", "Error al procesar la solicitud",
                        "mensaje", e.getMessage()
                ));
    }
}
