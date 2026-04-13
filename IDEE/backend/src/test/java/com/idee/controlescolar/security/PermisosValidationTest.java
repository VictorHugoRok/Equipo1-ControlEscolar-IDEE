package com.idee.controlescolar.security;

import com.idee.controlescolar.model.*;
import com.idee.controlescolar.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
    private AlumnoRepository alumnoRepository;

    @Autowired
    private AsignaturaRepository asignaturaRepository;

    @Autowired
    private ProgramaEducativoRepository programaRepository;

    @Autowired
    private CalificacionRepository calificacionRepository;

    @Autowired
    private TituloElectronicoRepository tituloRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private String tokenAdmin;
    private String tokenSecretaria;
    private Long calificacionTestId;
    private Long tituloTestId;

    @BeforeEach
    public void setUp() {
        // ── Usuarios ──────────────────────────────────────────────
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

        tokenAdmin = jwtUtil.generateToken(admin);
        tokenSecretaria = jwtUtil.generateToken(secretaria);

        // ── Programa educativo ────────────────────────────────────
        ProgramaEducativo programa = programaRepository.findByClave("TEST-PROG")
            .orElseGet(() -> {
                ProgramaEducativo p = new ProgramaEducativo();
                p.setClave("TEST-PROG");
                p.setNombre("Programa de Prueba");
                p.setTipoPrograma(ProgramaEducativo.TipoPrograma.LICENCIATURA);
                p.setEstatus(ProgramaEducativo.EstatusPrograma.ACTIVO);
                return programaRepository.save(p);
            });

        // ── Alumno ────────────────────────────────────────────────
        Alumno alumno = alumnoRepository.findByMatricula("TEST-MAT-001")
            .orElseGet(() -> {
                Alumno a = new Alumno();
                a.setMatricula("TEST-MAT-001");
                a.setNombre("Alumno");
                a.setApellidoPaterno("De");
                a.setApellidoMaterno("Prueba");
                a.setCurp("ADPR900101HDFRRST01");
                a.setEstatusMatricula(Alumno.EstatusMatricula.ACTIVA);
                return alumnoRepository.save(a);
            });

        // ── Asignatura ────────────────────────────────────────────
        Asignatura asignatura = new Asignatura();
        asignatura.setClave("TEST-AS-001");
        asignatura.setNombre("Asignatura de Prueba");
        asignatura = asignaturaRepository.save(asignatura);

        // ── Calificacion (nueva por cada test) ────────────────────
        Calificacion cal = new Calificacion();
        cal.setCalificacionFinal(85.0);
        cal.setAsistenciaPorcentaje(90.0);
        cal.setAlumno(alumno);
        cal.setAsignatura(asignatura);
        cal.setConfirmada(false);
        calificacionTestId = calificacionRepository.save(cal).getId();

        // ── TituloElectronico (findOrCreate) ──────────────────────
        TituloElectronico titulo = tituloRepository.findByFolioControl("TEST-FOLIO-001")
            .orElseGet(() -> {
                TituloElectronico t = new TituloElectronico();
                t.setFolioControl("TEST-FOLIO-001");
                t.setAlumno(alumno);
                t.setPrograma(programa);
                t.setFechaExpedicion(LocalDate.of(2025, 6, 1));
                t.setIdModalidadTitulacion("1");
                t.setModalidadTitulacion("Por Tesis");
                t.setCumplioServicioSocial(true);
                t.setEstatus(EstatusTitulo.GENERADO);
                return tituloRepository.save(t);
            });
        tituloTestId = titulo.getId();
    }

    /**
     * TEST 1: ADMIN intenta CONFIRMAR calificación → DEBE FALLAR (403)
     */
    @Test
    public void testAdminNoPuedeConfirmarCalificaciones() throws Exception {
        mockMvc.perform(
            post("/api/calificaciones/" + calificacionTestId + "/confirmar")
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
            post("/api/calificaciones/" + calificacionTestId + "/confirmar")
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
        String calificacionJson = "{\"calificacionFinal\": 95.0, \"observaciones\": \"Excelente\"}";

        mockMvc.perform(
            put("/api/calificaciones/" + calificacionTestId)
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
        String calificacionJson = "{\"calificacionFinal\": 95.0, \"observaciones\": \"Excelente\"}";

        mockMvc.perform(
            put("/api/calificaciones/" + calificacionTestId)
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
     * Spring Security bloquea antes de llegar al controller.
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
     * TEST 8: SECRETARIA_ACADEMICA puede generar títulos → seguridad no bloquea (no 401/403).
     * La lógica de negocio (validaciones, XML, firma) se prueba en tests específicos del servicio.
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
        .andExpect(result -> {
            int status = result.getResponse().getStatus();
            assertFalse(status == 401 || status == 403,
                "SECRETARIA_ACADEMICA fue bloqueada por seguridad con status " + status);
        });
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
     */
    @Test
    public void testAdminNoPuedeFirmarTitulos() throws Exception {
        mockMvc.perform(
            post("/api/titulos-electronicos/" + tituloTestId + "/firmar")
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
            post("/api/titulos-electronicos/" + tituloTestId + "/firmar")
                .header("Authorization", "Bearer " + tokenSecretaria)
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isOk());
    }
}
