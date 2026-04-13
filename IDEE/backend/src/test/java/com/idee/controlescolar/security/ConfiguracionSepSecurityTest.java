package com.idee.controlescolar.security;

import com.idee.controlescolar.model.Usuario;
import com.idee.controlescolar.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de seguridad — Configuración SEP
 * =======================================
 * Valida los permisos de acceso a los endpoints de configuración institucional
 * y responsables de firma según el rol del usuario.
 *
 * Tests incluidos:
 *   CI-02 — ADMIN no puede GET /configuracion-institucional          → 403
 *   CI-03 — Sin token no puede GET /configuracion-institucional       → 401
 *   RF-07 — ADMIN no debería poder POST /responsables-firma          → actualmente PASA (bug de seguridad)
 *
 * Ejecutar con: mvn test -Dtest=ConfiguracionSepSecurityTest
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ConfiguracionSepSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private String tokenAdmin;
    private String tokenSecretaria;

    @BeforeEach
    public void setUp() {
        Usuario admin = usuarioRepository.findByEmail("admin-sep-sec@example.com")
            .orElseGet(() -> {
                Usuario u = new Usuario();
                u.setEmail("admin-sep-sec@example.com");
                u.setPassword("encodedPassword");
                u.setTipoUsuario(Usuario.TipoUsuario.ADMIN);
                u.setActivo(true);
                return usuarioRepository.save(u);
            });

        Usuario secretaria = usuarioRepository.findByEmail("secretaria-sep-sec@example.com")
            .orElseGet(() -> {
                Usuario u = new Usuario();
                u.setEmail("secretaria-sep-sec@example.com");
                u.setPassword("encodedPassword");
                u.setTipoUsuario(Usuario.TipoUsuario.SECRETARIA_ACADEMICA);
                u.setActivo(true);
                return usuarioRepository.save(u);
            });

        tokenAdmin      = jwtUtil.generateToken(admin);
        tokenSecretaria = jwtUtil.generateToken(secretaria);
    }

    // =========================================================
    // CI-02 — ADMIN intenta GET /configuracion-institucional → 403
    // =========================================================
    @Test
    @DisplayName("CI-02 GET /configuracion-institucional — ADMIN recibe 403 Forbidden")
    public void testAdminNoPuedeVerConfiguracionInstitucional() throws Exception {
        mockMvc.perform(
            get("/api/configuracion-institucional")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
    }

    // =========================================================
    // CI-02b — SECRETARIA puede GET /configuracion-institucional → no es 401 ni 403
    // =========================================================
    @Test
    @DisplayName("CI-02b GET /configuracion-institucional — SECRETARIA_ACADEMICA no es bloqueada por seguridad")
    public void testSecretariaPuedeVerConfiguracionInstitucional() throws Exception {
        mockMvc.perform(
            get("/api/configuracion-institucional")
                .header("Authorization", "Bearer " + tokenSecretaria)
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(result -> {
            int status = result.getResponse().getStatus();
            if (status == 401 || status == 403) {
                throw new AssertionError(
                    "SECRETARIA_ACADEMICA fue bloqueada por seguridad con status " + status);
            }
        });
    }

    // =========================================================
    // CI-03 — Sin token GET /configuracion-institucional → 401
    // =========================================================
    @Test
    @DisplayName("CI-03 GET /configuracion-institucional — sin token retorna 401 Unauthorized")
    public void testSinTokenNoPuedeVerConfiguracionInstitucional() throws Exception {
        mockMvc.perform(
            get("/api/configuracion-institucional")
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isUnauthorized());
    }

    // =========================================================
    // CI-03b — Token inválido GET /configuracion-institucional → 401
    // =========================================================
    @Test
    @DisplayName("CI-03b GET /configuracion-institucional — token inválido retorna 401 Unauthorized")
    public void testTokenInvalidoNoPuedeVerConfiguracionInstitucional() throws Exception {
        mockMvc.perform(
            get("/api/configuracion-institucional")
                .header("Authorization", "Bearer TOKEN_INVALIDO_XYZ")
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isUnauthorized());
    }

    // =========================================================
    // CI admin POST → 403
    // =========================================================
    @Test
    @DisplayName("CI-admin POST /configuracion-institucional — ADMIN recibe 403 Forbidden")
    public void testAdminNoPuedeCrearConfiguracionInstitucional() throws Exception {
        String body = "{\"cveInstitucion\":\"TEST\",\"nombreInstitucion\":\"Test\","
                    + "\"idEntidadFederativa\":\"09\",\"entidadFederativa\":\"CDMX\"}";

        mockMvc.perform(
            post("/api/configuracion-institucional")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
    }

    // =========================================================
    // CI admin PUT → 403
    // =========================================================
    @Test
    @DisplayName("CI-admin PUT /configuracion-institucional/1 — ADMIN recibe 403 Forbidden")
    public void testAdminNoPuedeActualizarConfiguracionInstitucional() throws Exception {
        String body = "{\"cveInstitucion\":\"UPD\",\"nombreInstitucion\":\"Actualizado\","
                    + "\"idEntidadFederativa\":\"09\",\"entidadFederativa\":\"CDMX\"}";

        mockMvc.perform(
            put("/api/configuracion-institucional/1")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
    }

    // =========================================================
    // RF-07 — ADMIN no debería poder POST /responsables-firma
    //
    // ESTADO ACTUAL: Este test FALLARÁ porque ResponsableFirmaController
    // no tiene @PreAuthorize. Documenta el bug de seguridad existente.
    // Para corregirlo: agregar @PreAuthorize("hasRole('SECRETARIA_ACADEMICA')")
    // a los métodos POST, PUT y DELETE de ResponsableFirmaController.
    // =========================================================
    @Test
    @DisplayName("RF-07 POST /responsables-firma — ADMIN debería recibir 403 [BUG: actualmente NO tiene @PreAuthorize]")
    public void testAdminNoPuedeCrearResponsableFirma() throws Exception {
        String body = "{\"nombre\":\"Test\",\"primerApellido\":\"User\","
                    + "\"curp\":\"TUSR900101HDFRRST01\",\"idCargo\":\"01\","
                    + "\"cargo\":\"Director\",\"ordenFirma\":1}";

        mockMvc.perform(
            post("/api/responsables-firma")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
        // ESPERADO tras corregir el bug:
        .andExpect(status().isForbidden());
        // Si falla aquí, el controlador no tiene @PreAuthorize
        // → agregar en ResponsableFirmaController:
        //   @PreAuthorize("hasRole('SECRETARIA_ACADEMICA')")
    }

    // =========================================================
    // RF-07b — SECRETARIA puede POST /responsables-firma → no bloqueada
    // =========================================================
    @Test
    @DisplayName("RF-07b POST /responsables-firma — SECRETARIA_ACADEMICA no es bloqueada por seguridad")
    public void testSecretariaPuedeCrearResponsableFirma() throws Exception {
        String body = "{\"nombre\":\"Ana\",\"primerApellido\":\"López\","
                    + "\"curp\":\"LOAA900101MDFRRSA01\",\"idCargo\":\"02\","
                    + "\"cargo\":\"Secretaria Académica\",\"ordenFirma\":1}";

        mockMvc.perform(
            post("/api/responsables-firma")
                .header("Authorization", "Bearer " + tokenSecretaria)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
        .andExpect(result -> {
            int status = result.getResponse().getStatus();
            if (status == 401 || status == 403) {
                throw new AssertionError(
                    "SECRETARIA_ACADEMICA fue bloqueada por seguridad con status " + status);
            }
        });
    }
}
