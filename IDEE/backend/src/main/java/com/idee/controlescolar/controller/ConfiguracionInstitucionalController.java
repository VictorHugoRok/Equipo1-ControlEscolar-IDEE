package com.idee.controlescolar.controller;

import com.idee.controlescolar.model.ConfiguracionInstitucional;
import com.idee.controlescolar.repository.ConfiguracionInstitucionalRepository;
import com.idee.controlescolar.service.FirmaDigitalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * Controlador REST para la configuración institucional.
 */
@RestController
@RequestMapping("/api/configuracion-institucional")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class ConfiguracionInstitucionalController {

    private final ConfiguracionInstitucionalRepository configuracionRepository;
    private final FirmaDigitalService firmaDigitalService;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ConfiguracionInstitucional> obtenerConfiguracion() {
        return configuracionRepository.findByActivoTrue()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ConfiguracionInstitucional> crearConfiguracion(
            @RequestBody ConfiguracionInstitucional configuracion) {
        configuracion.setActivo(true);
        ConfiguracionInstitucional guardada = configuracionRepository.save(configuracion);
        return ResponseEntity.ok(guardada);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ConfiguracionInstitucional> actualizarConfiguracion(
            @PathVariable Long id,
            @RequestBody ConfiguracionInstitucional configuracion) {
        return configuracionRepository.findById(id)
                .map(existing -> {
                    configuracion.setId(id);
                    return ResponseEntity.ok(configuracionRepository.save(configuracion));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Endpoint MEJORADO para subir archivos de certificados (.cer y .key) con VALIDACIÓN
     * POST /api/configuracion-institucional/certificados
     *
     * Ahora valida que el .cer y .key sean par válido y que la contraseña sea correcta
     * ANTES de guardarlos en BD.
     */
    @PostMapping(value = "/certificados", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> subirCertificados(
            @RequestPart("cer") MultipartFile cer,
            @RequestPart("key") MultipartFile key,
            @RequestPart("password") String password) {

        try {
            log.info("Recibiendo certificados para carga: cer={}, key={}, passwordLength={}",
                cer.getOriginalFilename(), key.getOriginalFilename(), password.length());

            ConfiguracionInstitucional cfg = configuracionRepository.findByActivoTrue()
                .orElseThrow(() -> new IllegalStateException("No existe configuración activa"));

            byte[] cerBytes = cer.getBytes();
            byte[] keyBytes = key.getBytes();

            log.info("Tamaño archivos: cer={} bytes, key={} bytes", cerBytes.length, keyBytes.length);

            // 1) VALIDAR que sean par correcto (CRÍTICO antes de guardar)
            log.info("Validando que .cer y .key sean par válido...");
            boolean ok = firmaDigitalService.validarParCertificadoLlaveDesdeBytes(cerBytes, keyBytes, password);

            if (!ok) {
                log.error("VALIDACIÓN FALLIDA: El .cer y .key no son par o el password es incorrecto");
                return ResponseEntity.badRequest().body(
                    Map.of("error", "El .cer y .key no son par válido o el password es incorrecto")
                );
            }

            log.info("Validación exitosa: certificados son par válido");

            // 2) Limpiar certificados anteriores primero (para evitar problemas con LOBs)
            cfg.setCertificadoData(null);
            cfg.setLlavePrivadaData(null);
            configuracionRepository.saveAndFlush(cfg);

            // 3) Guardar nuevos certificados
            cfg.setCertificadoData(cerBytes);
            cfg.setCertificadoFilename(cer.getOriginalFilename());
            cfg.setLlavePrivadaData(keyBytes);
            cfg.setLlavePrivadaFilename(key.getOriginalFilename());

            // Guardar password (puedes encriptarlo si lo deseas)
            // cfg.setPasswordLlavePrivada(firmaDigitalService.encriptarPassword(password));
            cfg.setPasswordLlavePrivada(password);

            configuracionRepository.saveAndFlush(cfg);

            log.info("Certificados cargados y guardados correctamente en configuración ID: {}", cfg.getId());

            return ResponseEntity.ok(Map.of(
                "mensaje", "Certificados cargados correctamente",
                "certificadoFilename", cer.getOriginalFilename(),
                "llavePrivadaFilename", key.getOriginalFilename(),
                "certificadoSize", cerBytes.length,
                "llavePrivadaSize", keyBytes.length,
                "validado", true
            ));
        } catch (Exception e) {
            log.error("Error al subir certificados: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(
                Map.of("error", "Error al procesar certificados: " + e.getMessage())
            );
        }
    }

    /**
     * Endpoint para VALIDAR certificados SIN guardarlos
     * POST /api/configuracion-institucional/certificados/validar
     *
     * Prueba si el .cer y .key son válidos y da información detallada del error
     */
    @PostMapping(value = "/certificados/validar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> validarCertificadosSinGuardar(
            @RequestPart("cer") MultipartFile cer,
            @RequestPart("key") MultipartFile key,
            @RequestPart("password") String password) {

        Map<String, Object> resultado = new HashMap<>();

        try {
            byte[] cerBytes = cer.getBytes();
            byte[] keyBytes = key.getBytes();

            resultado.put("cerFilename", cer.getOriginalFilename());
            resultado.put("keyFilename", key.getOriginalFilename());
            resultado.put("cerSize", cerBytes.length);
            resultado.put("keySize", keyBytes.length);
            resultado.put("passwordLength", password.length());

            // Paso 1: Intentar cargar el certificado
            log.info("Intentando cargar certificado...");
            try {
                var cert = firmaDigitalService.cargarCertificadoDesdeBytes(cerBytes);
                resultado.put("certificadoCargado", true);
                resultado.put("certificadoInfo", "Emisor: " + cert.getIssuerX500Principal().getName());
                log.info("✓ Certificado cargado OK");
            } catch (Exception e) {
                resultado.put("certificadoCargado", false);
                resultado.put("errorCertificado", e.getMessage());
                log.error("✗ Error cargando certificado: {}", e.getMessage());
                return ResponseEntity.ok(resultado);
            }

            // Paso 2: Intentar cargar la llave privada
            log.info("Intentando cargar llave privada con password...");
            try {
                var privateKey = firmaDigitalService.cargarLlavePrivadaDesdeBytes(keyBytes, password);
                resultado.put("llavePrivadaCargada", true);
                resultado.put("llaveAlgoritmo", privateKey.getAlgorithm());
                log.info("✓ Llave privada cargada OK (algoritmo: {})", privateKey.getAlgorithm());
            } catch (Exception e) {
                resultado.put("llavePrivadaCargada", false);
                resultado.put("errorLlavePrivada", e.getMessage());
                resultado.put("sugerencia", "La contraseña podría ser incorrecta o el archivo .key no está en formato válido");
                log.error("✗ Error cargando llave privada: {}", e.getMessage());
                return ResponseEntity.ok(resultado);
            }

            // Paso 3: Validar que sean par
            log.info("Validando que certificado y llave sean par...");
            boolean parValido = firmaDigitalService.validarParCertificadoLlaveDesdeBytes(cerBytes, keyBytes, password);
            resultado.put("parValido", parValido);

            if (parValido) {
                resultado.put("mensaje", "✓ OK: Los certificados son válidos y la contraseña es correcta");
                resultado.put("puedeGuardar", true);
                log.info("✓ Validación completa exitosa");
            } else {
                resultado.put("mensaje", "✗ El certificado y la llave no corresponden entre sí");
                resultado.put("puedeGuardar", false);
                log.error("✗ Certificado y llave no son par");
            }

            return ResponseEntity.ok(resultado);

        } catch (Exception e) {
            log.error("Error general en validación: {}", e.getMessage(), e);
            resultado.put("error", e.getMessage());
            resultado.put("stackTrace", e.getClass().getName());
            return ResponseEntity.ok(resultado);
        }
    }

    /**
     * Endpoint de DIAGNÓSTICO para verificar certificados cargados
     * GET /api/configuracion-institucional/certificados/diagnostico
     *
     * Devuelve si hay certificados, si son válidos, y su información.
     */
    @GetMapping("/certificados/diagnostico")
    public ResponseEntity<?> diagnosticoCertificados() {
        try {
            ConfiguracionInstitucional cfg = configuracionRepository.findByActivoTrue()
                .orElseThrow(() -> new IllegalStateException("No existe configuración activa"));

            Map<String, Object> diagnostico = new HashMap<>();

            // Check 1: ¿Tiene certificados cargados?
            boolean tieneCertificados = cfg.tieneCertificados();
            diagnostico.put("tieneCertificados", tieneCertificados);

            if (!tieneCertificados) {
                diagnostico.put("mensaje", "No hay .cer/.key cargados");
                diagnostico.put("cerBytes", cfg.getCertificadoData() == null ? 0 : cfg.getCertificadoData().length);
                diagnostico.put("keyBytes", cfg.getLlavePrivadaData() == null ? 0 : cfg.getLlavePrivadaData().length);
                diagnostico.put("hasPassword", cfg.getPasswordLlavePrivada() != null && !cfg.getPasswordLlavePrivada().isBlank());
                return ResponseEntity.ok(diagnostico);
            }

            diagnostico.put("cerFilename", cfg.getCertificadoFilename());
            diagnostico.put("keyFilename", cfg.getLlavePrivadaFilename());
            diagnostico.put("cerBytes", cfg.getCertificadoData().length);
            diagnostico.put("keyBytes", cfg.getLlavePrivadaData().length);

            // Check 2: ¿Son par válido y password correcto?
            String pass = cfg.getPasswordLlavePrivada();
            // Si guardaste encriptado: pass = firmaDigitalService.desencriptarPassword(pass);

            log.info("Validando par certificado/llave desde BD...");
            boolean parValido = firmaDigitalService.validarParCertificadoLlaveDesdeBytes(
                cfg.getCertificadoData(),
                cfg.getLlavePrivadaData(),
                pass
            );

            diagnostico.put("parValido", parValido);
            diagnostico.put("passwordCorrecto", parValido);

            if (parValido) {
                // Obtener info del certificado
                String infoCert = firmaDigitalService.obtenerInfoCertificadoDesdeBytes(cfg.getCertificadoData());
                diagnostico.put("infoCertificado", infoCert);
                diagnostico.put("mensaje", "OK: .cer y .key son par válido y password correcto");
            } else {
                diagnostico.put("mensaje", "ERROR: no son par válido o password incorrecto");
            }

            return ResponseEntity.ok(diagnostico);

        } catch (Exception e) {
            log.error("Error en diagnóstico de certificados: {}", e.getMessage(), e);
            return ResponseEntity.ok(Map.of(
                "error", e.getMessage(),
                "mensaje", "Error al realizar diagnóstico"
            ));
        }
    }

    /**
     * Endpoint LEGACY para compatibilidad (mantener si ya lo usas)
     * POST /api/configuracion-institucional/{id}/certificados
     */
    @PostMapping("/{id}/certificados")
    public ResponseEntity<Map<String, Object>> subirCertificadosLegacy(
            @PathVariable Long id,
            @RequestParam("certificado") MultipartFile certificadoFile,
            @RequestParam("llavePrivada") MultipartFile llavePrivadaFile,
            @RequestParam("password") String password) {

        try {
            return configuracionRepository.findById(id)
                    .map(config -> {
                        try {
                            // Validar archivos
                            if (certificadoFile.isEmpty() || llavePrivadaFile.isEmpty()) {
                                Map<String, Object> error = new HashMap<>();
                                error.put("error", "Los archivos son requeridos");
                                return ResponseEntity.badRequest().body(error);
                            }

                            // Validar extensiones
                            String cerFilename = certificadoFile.getOriginalFilename();
                            String keyFilename = llavePrivadaFile.getOriginalFilename();

                            if (cerFilename == null || !cerFilename.toLowerCase().endsWith(".cer")) {
                                Map<String, Object> error = new HashMap<>();
                                error.put("error", "El certificado debe ser un archivo .cer");
                                return ResponseEntity.badRequest().body(error);
                            }

                            if (keyFilename == null || !keyFilename.toLowerCase().endsWith(".key")) {
                                Map<String, Object> error = new HashMap<>();
                                error.put("error", "La llave privada debe ser un archivo .key");
                                return ResponseEntity.badRequest().body(error);
                            }

                            byte[] cerBytes = certificadoFile.getBytes();
                            byte[] keyBytes = llavePrivadaFile.getBytes();

                            // VALIDAR par antes de guardar
                            boolean ok = firmaDigitalService.validarParCertificadoLlaveDesdeBytes(cerBytes, keyBytes, password);
                            if (!ok) {
                                Map<String, Object> error = new HashMap<>();
                                error.put("error", "El .cer y .key no son par válido o el password es incorrecto");
                                return ResponseEntity.badRequest().body(error);
                            }

                            // Guardar archivos en la base de datos
                            config.setCertificadoData(cerBytes);
                            config.setCertificadoFilename(cerFilename);
                            config.setLlavePrivadaData(keyBytes);
                            config.setLlavePrivadaFilename(keyFilename);
                            config.setPasswordLlavePrivada(password);

                            configuracionRepository.save(config);

                            log.info("Certificados guardados exitosamente para configuración ID: {}", id);

                            Map<String, Object> response = new HashMap<>();
                            response.put("mensaje", "Certificados guardados exitosamente");
                            response.put("certificadoFilename", cerFilename);
                            response.put("llavePrivadaFilename", keyFilename);
                            response.put("certificadoSize", certificadoFile.getSize());
                            response.put("llavePrivadaSize", llavePrivadaFile.getSize());

                            return ResponseEntity.ok(response);

                        } catch (Exception e) {
                            log.error("Error al procesar archivos de certificados: {}", e.getMessage(), e);
                            Map<String, Object> error = new HashMap<>();
                            error.put("error", "Error al procesar los archivos: " + e.getMessage());
                            return ResponseEntity.internalServerError().body(error);
                        }
                    })
                    .orElse(ResponseEntity.notFound().build());

        } catch (Exception e) {
            log.error("Error en endpoint de certificados: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error al subir certificados: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}
