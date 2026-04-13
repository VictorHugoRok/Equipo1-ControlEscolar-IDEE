package com.idee.controlescolar.controller;

import com.idee.controlescolar.model.ConfiguracionInstitucional;
import com.idee.controlescolar.repository.ConfiguracionInstitucionalRepository;
import com.idee.controlescolar.service.FirmaDigitalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios — ConfiguracionInstitucionalController
 * ======================================================
 * Componente probado : ConfiguracionInstitucionalController
 * Dependencias mock  : ConfiguracionInstitucionalRepository, FirmaDigitalService
 * Framework          : JUnit 5 + Mockito + AssertJ
 *
 * Ejecutar con: mvn test -Dtest=ConfiguracionInstitucionalControllerTest
 */
@ExtendWith(MockitoExtension.class)
class ConfiguracionInstitucionalControllerTest {

    @Mock
    private ConfiguracionInstitucionalRepository configuracionRepository;

    @Mock
    private FirmaDigitalService firmaDigitalService;

    @InjectMocks
    private ConfiguracionInstitucionalController controller;

    // =========================================================
    // CI-01 — GET / config activa existe → 200 con datos
    // =========================================================
    @Test
    @DisplayName("CI-01 GET /configuracion-institucional — config activa retorna HTTP 200 con la entidad")
    void obtenerConfiguracion_existeConfigActiva_retorna200ConDatos() {
        ConfiguracionInstitucional config = mock(ConfiguracionInstitucional.class);
        when(configuracionRepository.findByActivoTrue()).thenReturn(Optional.of(config));

        ResponseEntity<ConfiguracionInstitucional> resp = controller.obtenerConfiguracion();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isSameAs(config);
    }

    // =========================================================
    // CI-04 — GET / no existe config activa → 404
    // =========================================================
    @Test
    @DisplayName("CI-04 GET /configuracion-institucional — sin config activa retorna HTTP 404")
    void obtenerConfiguracion_noExisteConfig_retorna404() {
        when(configuracionRepository.findByActivoTrue()).thenReturn(Optional.empty());

        ResponseEntity<ConfiguracionInstitucional> resp = controller.obtenerConfiguracion();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNull();
    }

    // =========================================================
    // CI-05 — POST / datos válidos → 200, activo=true guardado
    // =========================================================
    @Test
    @DisplayName("CI-05 POST /configuracion-institucional — datos válidos retorna HTTP 200 y persiste con activo=true")
    void crearConfiguracion_datosValidos_retorna200YGuardaConActivoTrue() {
        ConfiguracionInstitucional nueva = new ConfiguracionInstitucional();
        nueva.setCveInstitucion("IDEE001");
        nueva.setNombreInstitucion("Instituto IDEE");
        nueva.setIdEntidadFederativa("09");
        nueva.setEntidadFederativa("Ciudad de México");

        ConfiguracionInstitucional guardada = new ConfiguracionInstitucional();
        guardada.setId(1L);
        guardada.setCveInstitucion("IDEE001");
        guardada.setActivo(true);

        when(configuracionRepository.save(any(ConfiguracionInstitucional.class))).thenReturn(guardada);

        ResponseEntity<ConfiguracionInstitucional> resp = controller.crearConfiguracion(nueva);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getActivo()).isTrue();
        // Verificar que el flag activo se setea antes de guardar
        verify(configuracionRepository, times(1)).save(argThat(c -> c.getActivo() == Boolean.TRUE));
    }

    // =========================================================
    // CI-07 — PUT /{id} id existente → 200 con entidad actualizada
    // =========================================================
    @Test
    @DisplayName("CI-07 PUT /configuracion-institucional/{id} — ID existente retorna HTTP 200 y persiste cambios")
    void actualizarConfiguracion_idExistente_retorna200YPersisteCambios() {
        ConfiguracionInstitucional existente = mock(ConfiguracionInstitucional.class);
        ConfiguracionInstitucional cambios   = new ConfiguracionInstitucional();
        cambios.setNombreInstitucion("Nombre Actualizado");

        when(configuracionRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(configuracionRepository.save(any(ConfiguracionInstitucional.class))).thenReturn(cambios);

        ResponseEntity<ConfiguracionInstitucional> resp = controller.actualizarConfiguracion(1L, cambios);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(configuracionRepository, times(1)).save(any(ConfiguracionInstitucional.class));
    }

    // =========================================================
    // CI-08 — PUT /{id} id inexistente → 404
    // =========================================================
    @Test
    @DisplayName("CI-08 PUT /configuracion-institucional/9999 — ID inexistente retorna HTTP 404")
    void actualizarConfiguracion_idInexistente_retorna404() {
        when(configuracionRepository.findById(9999L)).thenReturn(Optional.empty());

        ResponseEntity<ConfiguracionInstitucional> resp =
                controller.actualizarConfiguracion(9999L, new ConfiguracionInstitucional());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(configuracionRepository, never()).save(any());
    }

    // =========================================================
    // CERT-01 — POST /certificados par válido → 200 + validado=true
    // =========================================================
    @Test
    @DisplayName("CERT-01 POST /certificados — par .cer/.key válido retorna HTTP 200 con validado=true")
    void subirCertificados_parValido_retorna200ConValidadoTrue() throws Exception {
        byte[] cerBytes = new byte[]{1, 2, 3};
        byte[] keyBytes = new byte[]{4, 5, 6};

        MultipartFile cer = mock(MultipartFile.class);
        MultipartFile key = mock(MultipartFile.class);
        when(cer.getBytes()).thenReturn(cerBytes);
        when(cer.getOriginalFilename()).thenReturn("certificado.cer");
        when(key.getBytes()).thenReturn(keyBytes);
        when(key.getOriginalFilename()).thenReturn("llave.key");

        ConfiguracionInstitucional cfg = new ConfiguracionInstitucional();
        cfg.setId(1L);
        when(configuracionRepository.findByActivoTrue()).thenReturn(Optional.of(cfg));
        when(configuracionRepository.saveAndFlush(any())).thenReturn(cfg);
        when(firmaDigitalService.validarParCertificadoLlaveDesdeBytes(cerBytes, keyBytes, "password123"))
                .thenReturn(true);

        ResponseEntity<?> resp = controller.subirCertificados(cer, key, "password123");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertThat(body).containsEntry("validado", true);
        // Verificar que se guardaron los bytes en la entidad
        assertThat(cfg.getCertificadoData()).isEqualTo(cerBytes);
        assertThat(cfg.getLlavePrivadaData()).isEqualTo(keyBytes);
    }

    // =========================================================
    // CERT-02 — POST /certificados .cer y .key no son par → 400
    // =========================================================
    @Test
    @DisplayName("CERT-02 POST /certificados — .cer y .key que no son par retorna HTTP 400")
    void subirCertificados_parInvalido_retorna400() throws Exception {
        MultipartFile cer = mock(MultipartFile.class);
        MultipartFile key = mock(MultipartFile.class);
        when(cer.getBytes()).thenReturn(new byte[]{1});
        when(cer.getOriginalFilename()).thenReturn("otro.cer");
        when(key.getBytes()).thenReturn(new byte[]{2});
        when(key.getOriginalFilename()).thenReturn("otro.key");

        ConfiguracionInstitucional cfg = new ConfiguracionInstitucional();
        cfg.setId(1L);
        when(configuracionRepository.findByActivoTrue()).thenReturn(Optional.of(cfg));
        when(firmaDigitalService.validarParCertificadoLlaveDesdeBytes(any(), any(), anyString()))
                .thenReturn(false);

        ResponseEntity<?> resp = controller.subirCertificados(cer, key, "cualquierPass");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertThat(body).containsKey("error");
        // Verificar que NO se guardó nada en la base de datos
        verify(configuracionRepository, never()).save(any());
    }

    // =========================================================
    // CERT-03 — POST /certificados par correcto pero password incorrecto → 400
    // =========================================================
    @Test
    @DisplayName("CERT-03 POST /certificados — password incorrecto retorna HTTP 400 sin guardar")
    void subirCertificados_passwordIncorrecto_retorna400SinGuardar() throws Exception {
        MultipartFile cer = mock(MultipartFile.class);
        MultipartFile key = mock(MultipartFile.class);
        when(cer.getBytes()).thenReturn(new byte[]{10, 20});
        when(cer.getOriginalFilename()).thenReturn("cert.cer");
        when(key.getBytes()).thenReturn(new byte[]{30, 40});
        when(key.getOriginalFilename()).thenReturn("llave.key");

        ConfiguracionInstitucional cfg = new ConfiguracionInstitucional();
        cfg.setId(2L);
        when(configuracionRepository.findByActivoTrue()).thenReturn(Optional.of(cfg));
        // Password incorrecto → validar devuelve false
        when(firmaDigitalService.validarParCertificadoLlaveDesdeBytes(any(), any(), eq("passwordMalo")))
                .thenReturn(false);

        ResponseEntity<?> resp = controller.subirCertificados(cer, key, "passwordMalo");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(configuracionRepository, never()).save(any());
    }

    // =========================================================
    // CERT-04 — POST /certificados sin config activa en BD → 500
    // =========================================================
    @Test
    @DisplayName("CERT-04 POST /certificados — sin configuración activa retorna HTTP 500")
    void subirCertificados_sinConfigActiva_retorna500() throws Exception {
        MultipartFile cer = mock(MultipartFile.class);
        MultipartFile key = mock(MultipartFile.class);
        when(cer.getBytes()).thenReturn(new byte[]{1});
        when(cer.getOriginalFilename()).thenReturn("c.cer");
        when(key.getBytes()).thenReturn(new byte[]{2});
        when(key.getOriginalFilename()).thenReturn("k.key");

        when(configuracionRepository.findByActivoTrue()).thenReturn(Optional.empty());

        ResponseEntity<?> resp = controller.subirCertificados(cer, key, "pass");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertThat(body).containsKey("error");
    }

    // =========================================================
    // CERT-05 — POST /certificados/validar par correcto → puedeGuardar=true
    // =========================================================
    @Test
    @DisplayName("CERT-05 POST /certificados/validar — par válido retorna parValido=true y puedeGuardar=true")
    void validarCertificados_parValido_retornaParValidoTrue() throws Exception {
        byte[] cerBytes = new byte[]{1, 2, 3};
        byte[] keyBytes = new byte[]{4, 5, 6};

        MultipartFile cer = mock(MultipartFile.class);
        MultipartFile key = mock(MultipartFile.class);
        when(cer.getBytes()).thenReturn(cerBytes);
        when(cer.getOriginalFilename()).thenReturn("cert.cer");
        when(key.getBytes()).thenReturn(keyBytes);
        when(key.getOriginalFilename()).thenReturn("key.key");

        X509Certificate certMock = mock(X509Certificate.class);
        when(certMock.getIssuerX500Principal()).thenReturn(
                new javax.security.auth.x500.X500Principal("CN=SAT, O=SAT, C=MX"));
        when(firmaDigitalService.cargarCertificadoDesdeBytes(cerBytes)).thenReturn(certMock);

        java.security.PrivateKey pkMock = mock(java.security.PrivateKey.class);
        when(pkMock.getAlgorithm()).thenReturn("RSA");
        when(firmaDigitalService.cargarLlavePrivadaDesdeBytes(keyBytes, "passOK")).thenReturn(pkMock);
        when(firmaDigitalService.validarParCertificadoLlaveDesdeBytes(cerBytes, keyBytes, "passOK"))
                .thenReturn(true);

        ResponseEntity<?> resp = controller.validarCertificadosSinGuardar(cer, key, "passOK");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertThat(body).containsEntry("parValido", true);
        assertThat(body).containsEntry("puedeGuardar", true);
    }

    // =========================================================
    // CERT-06 — POST /certificados/validar .cer corrupto → certificadoCargado=false
    // =========================================================
    @Test
    @DisplayName("CERT-06 POST /certificados/validar — .cer corrupto retorna certificadoCargado=false con error")
    void validarCertificados_cerCorrupto_retornaCertificadoCargadoFalse() throws Exception {
        byte[] cerBytes = new byte[]{0, 0, 0};
        byte[] keyBytes = new byte[]{1, 2, 3};

        MultipartFile cer = mock(MultipartFile.class);
        MultipartFile key = mock(MultipartFile.class);
        when(cer.getBytes()).thenReturn(cerBytes);
        when(cer.getOriginalFilename()).thenReturn("corrupto.cer");
        when(key.getBytes()).thenReturn(keyBytes);
        when(key.getOriginalFilename()).thenReturn("llave.key");

        when(firmaDigitalService.cargarCertificadoDesdeBytes(cerBytes))
                .thenThrow(new RuntimeException("El archivo no es un certificado X.509 válido"));

        ResponseEntity<?> resp = controller.validarCertificadosSinGuardar(cer, key, "pass");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertThat(body).containsEntry("certificadoCargado", false);
        assertThat(body).containsKey("errorCertificado");
    }

    // =========================================================
    // CERT-07 — GET /certificados/diagnostico con .cer/.key cargados → parValido=true
    // =========================================================
    @Test
    @DisplayName("CERT-07 GET /certificados/diagnostico — configuración con .cer/.key válidos retorna parValido=true")
    void diagnosticoCertificados_conCertificadosCargados_retornaParValido() throws Exception {
        ConfiguracionInstitucional cfg = new ConfiguracionInstitucional();
        cfg.setId(1L);
        cfg.setCertificadoData(new byte[]{1, 2, 3});
        cfg.setCertificadoFilename("cert.cer");
        cfg.setLlavePrivadaData(new byte[]{4, 5, 6});
        cfg.setLlavePrivadaFilename("llave.key");
        cfg.setPasswordLlavePrivada("passValido");

        when(configuracionRepository.findByActivoTrue()).thenReturn(Optional.of(cfg));
        when(firmaDigitalService.validarParCertificadoLlaveDesdeBytes(
                cfg.getCertificadoData(), cfg.getLlavePrivadaData(), "passValido"))
                .thenReturn(true);
        when(firmaDigitalService.obtenerInfoCertificadoDesdeBytes(cfg.getCertificadoData()))
                .thenReturn("CN=IDEE, O=Instituto, C=MX");

        ResponseEntity<?> resp = controller.diagnosticoCertificados();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertThat(body).containsEntry("tieneCertificados", true);
        assertThat(body).containsEntry("parValido", true);
        assertThat(body).containsEntry("passwordCorrecto", true);
    }

    // =========================================================
    // CERT-08 — GET /certificados/diagnostico sin .cer/.key → tieneCertificados=false
    // =========================================================
    @Test
    @DisplayName("CERT-08 GET /certificados/diagnostico — sin certificados cargados retorna tieneCertificados=false")
    void diagnosticoCertificados_sinCertificados_retornaTieneCertificadosFalse() {
        ConfiguracionInstitucional cfg = new ConfiguracionInstitucional();
        cfg.setId(1L);
        // Sin datos binarios ni password → tieneCertificados() = false
        cfg.setCertificadoData(null);
        cfg.setLlavePrivadaData(null);
        cfg.setPasswordLlavePrivada(null);

        when(configuracionRepository.findByActivoTrue()).thenReturn(Optional.of(cfg));

        ResponseEntity<?> resp = controller.diagnosticoCertificados();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) resp.getBody();
        assertThat(body).containsEntry("tieneCertificados", false);
        // No debe haberse llamado al servicio de firma
        verify(firmaDigitalService, never()).validarParCertificadoLlaveDesdeBytes(any(), any(), any());
    }
}
