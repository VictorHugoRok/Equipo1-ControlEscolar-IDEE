package com.idee.controlescolar.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.idee.controlescolar.model.Usuario;
import com.idee.controlescolar.repository.UsuarioRepository;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TESTS DE VALIDACIÓN DE PERMISOS
 * ================================
 * 
 * Estos tests validan que:
 * 1. ADMIN NO puede editar/confirmar calificaciones
 * 2. SECRETARIA_ACADEMICA SÍ puede
 * 3. ADMIN NO puede ver títulos
 * 4. SECRETARIA_ACADEMICA SÍ puede
 * 
 * Ejecutar con: mvn test -Dtest=PermisosValidationTest
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PermisosValidationTest {

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
        // Crear usuarios de prueba si no existen
        Usuario admin = usuarioRepository.findByEmail("admin-test@example.com")
            .orElseGet(() -> {
                Usuario u = new Usuario();
                u.setEmail("admin-test@example.com");
                u.setPassword("encodedPassword");
                u.setTipoUsuario(Usuario.TipoUsuario.ADMIN);
                u.setActivo(true);
                return usuarioRepository.save(u);
            });

        Usuario secretaria = usuarioRepository.findByEmail("secretaria-test@example.com")
            .orElseGet(() -> {
                Usuario u = new Usuario();
                u.setEmail("secretaria-test@example.com");
                u.setPassword("encodedPassword");
                u.setTipoUsuario(Usuario.TipoUsuario.SECRETARIA_ACADEMICA);
                u.setActivo(true);
                return usuarioRepository.save(u);
            });

        // Generar tokens JWT
        tokenAdmin = jwtUtil.generateToken(admin);
        tokenSecretaria = jwtUtil.generateToken(secretaria);
    }

    /**
     * TEST 1: ADMIN intenta CONFIRMAR calificación → DEBE FALLAR (403)
     */
    @Test
    public void testAdminNoPuedeConfirmarCalificaciones() throws Exception {
        mockMvc.perform(
            post("/api/calificaciones/1/confirmar")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
    }

    /**
     * TEST 2: SECRETARIA_ACADEMICA CONFIRMA calificación → DEBE FUNCIONAR (200)
     */
    @Test
    public void testSecretariaPuedeConfirmarCalificaciones() throws Exception {
        mockMvc.perform(
            post("/api/calificaciones/1/confirmar")
                .header("Authorization", "Bearer " + tokenSecretaria)
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isOk());
    }

    /**
     * TEST 3: ADMIN intenta EDITAR calificación → DEBE FALLAR (403)
     */
    @Test
    public void testAdminNoPuedeEditarCalificaciones() throws Exception {
        String calificacionJson = "{\"calificacion\": 95, \"observaciones\": \"Excelente\"}";
        
        mockMvc.perform(
            put("/api/calificaciones/1")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(calificacionJson)
        )
        .andExpect(status().isForbidden());
    }

    /**
     * TEST 4: SECRETARIA_ACADEMICA EDITA calificación → DEBE FUNCIONAR (200)
     */
    @Test
    public void testSecretariaPuedeEditarCalificaciones() throws Exception {
        String calificacionJson = "{\"calificacion\": 95, \"observaciones\": \"Excelente\"}";
        
        mockMvc.perform(
            put("/api/calificaciones/1")
                .header("Authorization", "Bearer " + tokenSecretaria)
                .contentType(MediaType.APPLICATION_JSON)
                .content(calificacionJson)
        )
        .andExpect(status().isOk());
    }

    /**
     * TEST 5: ADMIN intenta VER títulos → DEBE FALLAR (403)
     */
    @Test
    public void testAdminNoPuedeVerTitulos() throws Exception {
        mockMvc.perform(
            get("/api/titulos-electronicos")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isForbidden());
    }

    /**
     * TEST 6: SECRETARIA_ACADEMICA VE títulos → DEBE FUNCIONAR (200)
     */
    @Test
    public void testSecretariaPuedeVerTitulos() throws Exception {
        mockMvc.perform(
            get("/api/titulos-electronicos")
                .header("Authorization", "Bearer " + tokenSecretaria)
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isOk());
    }

    /**
     * TEST 7: ADMIN intenta GENERAR título → DEBE FALLAR (403)
     */
    @Test
    public void testAdminNoPuedeGenerarTitulos() throws Exception {
        String tituloJson = "{\"alumnoId\": 1, \"estado\": \"pendiente\"}";
        
        mockMvc.perform(
            post("/api/titulos-electronicos")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(tituloJson)
        )
        .andExpect(status().isForbidden());
    }

    /**
     * TEST 8: SECRETARIA_ACADEMICA GENERA título → DEBE FUNCIONAR (200)
     */
    @Test
    public void testSecretariaPuedeGenerarTitulos() throws Exception {
        String tituloJson = "{\"alumnoId\": 1, \"estado\": \"pendiente\"}";
        
        mockMvc.perform(
            post("/api/titulos-electronicos")
                .header("Authorization", "Bearer " + tokenSecretaria)
                .contentType(MediaType.APPLICATION_JSON)
                .content(tituloJson)
        )
        .andExpect(status().isOk());
    }

    /**
     * TEST 9: ADMIN PUEDE VER calificaciones (lectura) → DEBE FUNCIONAR (200)
     * 
     * Nota: ADMIN tiene permiso VER_CALIFICACIONES pero NO EDITAR/CONFIRMAR
     */
    @Test
    public void testAdminPuedeVerCalificaciones() throws Exception {
        mockMvc.perform(
            get("/api/calificaciones")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isOk());
    }

    /**
     * TEST 10: SECRETARIA_ACADEMICA PUEDE VER calificaciones → DEBE FUNCIONAR (200)
     */
    @Test
    public void testSecretariaPuedeVerCalificaciones() throws Exception {
        mockMvc.perform(
            get("/api/calificaciones")
                .header("Authorization", "Bearer " + tokenSecretaria)
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isOk());
    }

    /**
     * TEST 11: Usuario no autenticado NO puede acceder → DEBE FALLAR (401)
     */
    @Test
    public void testNoAutenticadoNoPuedeAcceder() throws Exception {
        mockMvc.perform(
            get("/api/calificaciones")
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isUnauthorized());
    }

    /**
     * TEST 12: Token inválido NO puede acceder → DEBE FALLAR (401)
     */
    @Test
    public void testTokenInvalidoNoPuedeAcceder() throws Exception {
        mockMvc.perform(
            get("/api/calificaciones")
                .header("Authorization", "Bearer TOKEN_INVALIDO")
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isUnauthorized());
    }

    /**
     * TEST 13: ADMIN intenta FIRMAR títulos → DEBE FALLAR (403)
     * 
     * Solo SECRETARIA_ACADEMICA puede firmar
     */
    @Test
    public void testAdminNoPuedeFirmarTitulos() throws Exception {
        mockMvc.perform(
            post("/api/titulos-electronicos/1/firmar")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isForbidden());
    }

    /**
     * TEST 14: SECRETARIA_ACADEMICA FIRMA títulos → DEBE FUNCIONAR (200)
     */
    @Test
    public void testSecretariaPuedeFirmarTitulos() throws Exception {
        mockMvc.perform(
            post("/api/titulos-electronicos/1/firmar")
                .header("Authorization", "Bearer " + tokenSecretaria)
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isOk());
    }
}
