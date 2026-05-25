package com.idee.controlescolar.controller;
import com.idee.controlescolar.dto.AuthResponse;
import com.idee.controlescolar.dto.ErrorResponse;
import com.idee.controlescolar.dto.LoginErrorResponse;
import com.idee.controlescolar.dto.LoginRequest;
import com.idee.controlescolar.dto.MeResponse;
import com.idee.controlescolar.dto.RefreshRequest;
import com.idee.controlescolar.dto.RefreshResponse;
import com.idee.controlescolar.model.Alumno;
import com.idee.controlescolar.model.Maestro;
import com.idee.controlescolar.model.Personal;
import com.idee.controlescolar.model.Usuario;
import com.idee.controlescolar.repository.AlumnoRepository;
import com.idee.controlescolar.repository.MaestroRepository;
import com.idee.controlescolar.repository.PersonalRepository;
import com.idee.controlescolar.security.JwtUtil;
import com.idee.controlescolar.security.LoginAttemptService;
import com.idee.controlescolar.service.RefreshTokenService;
import com.idee.controlescolar.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    @Value("${app.allow-init-usuarios:false}")
    private boolean allowInitUsuarios;

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UsuarioService usuarioService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final PersonalRepository personalRepository;
    private final MaestroRepository maestroRepository;
    private final AlumnoRepository alumnoRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Login: autentica y devuelve access token + refresh token.
     * El frontend puede renovar la sesión con el refresh token sin pedir contraseña de nuevo.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        try {
            log.debug("📝 Login attempt: {}", loginRequest.getEmail());

            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequest.getEmail(),
                    loginRequest.getPassword()
                )
            );

            Usuario usuario = (Usuario) authentication.getPrincipal();
            String jwt = jwtUtil.generateToken(usuario);
            String refreshToken = refreshTokenService.createRefreshToken(usuario);
            long expiresIn = jwtUtil.getExpirationSeconds();

            log.info("✅ Login exitoso: {}", usuario.getEmail());

            List<String> rolesLista = rolesEfectivosParaApi(usuario);

            AuthResponse authResp = new AuthResponse(
                jwt,
                usuario.getEmail(),
                usuario.getTipoUsuario().name(),
                usuario.getId(),
                refreshToken,
                expiresIn,
                rolesLista
            );
            authResp.setNombreCompleto(resolverNombreCompleto(usuario.getId()));
            authResp.setMustChangePassword(Boolean.TRUE.equals(usuario.getMustChangePassword()));
            return ResponseEntity.ok(authResp);

        } catch (AuthenticationException e) {
            log.warn("❌ Autenticación fallida: {}", loginRequest.getEmail());
            String ip = getClientIp(request);
            LoginAttemptService.AttemptSummary summary = loginAttemptService.recordFailureAndGetSummary(ip);
            String msg = buildLoginFailureMessage(summary);
            LoginErrorResponse body = new LoginErrorResponse(
                401,
                msg,
                summary.failedAttempts,
                summary.maxAttempts,
                summary.remainingBeforeBlock,
                summary.windowSeconds,
                null
            );
            return ResponseEntity.status(401).body(body);
        } catch (Exception e) {
            log.error("❌ Error en login", e);
            return ResponseEntity.status(500)
                .body(new ErrorResponse(500, "Error interno del servidor. Intenta más tarde."));
        }
    }

    private static String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String buildLoginFailureMessage(LoginAttemptService.AttemptSummary s) {
        if (s.remainingBeforeBlock == 0) {
            return String.format(
                "Correo o contraseña incorrectos. Has llegado al máximo de %d intentos permitidos. Por seguridad, deberás esperar unos %d segundos antes de volver a intentarlo.",
                s.maxAttempts,
                s.windowSeconds
            );
        }
        String quedan = s.remainingBeforeBlock == 1
            ? "Te queda 1 intento más"
            : String.format("Te quedan %d intentos más", s.remainingBeforeBlock);
        return String.format(
            "Correo o contraseña incorrectos. Intento %d de %d. %s; después el acceso se pausará unos segundos por seguridad.",
            s.failedAttempts,
            s.maxAttempts,
            quedan
        );
    }

    /**
     * Renovar access token usando refresh token. Rotación: se invalida el refresh anterior y se devuelve uno nuevo.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshRequest request) {
        try {
            return refreshTokenService.validateAndGetUsuario(request.getRefreshToken())
                .map(usuario -> {
                    refreshTokenService.revokeByToken(request.getRefreshToken());
                    String newJwt = jwtUtil.generateToken(usuario);
                    String newRefreshToken = refreshTokenService.createRefreshToken(usuario);
                    long expiresIn = jwtUtil.getExpirationSeconds();
                    log.debug("✅ Token renovado para: {}", usuario.getEmail());
                    return ResponseEntity.<Object>ok(new RefreshResponse(newJwt, "Bearer", expiresIn, newRefreshToken));
                })
                .orElse(ResponseEntity.status(401).body(new ErrorResponse(401, "Refresh token inválido o expirado")));
        } catch (Exception e) {
            log.error("❌ Error en refresh", e);
            return ResponseEntity.status(401).body(new ErrorResponse(401, "Refresh token inválido o expirado"));
        }
    }

    /**
     * Cerrar sesión en el servidor: invalida todos los refresh tokens del usuario.
     * Opcional; el frontend puede llamarlo al hacer logout para mayor seguridad.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Usuario) {
            Usuario u = (Usuario) authentication.getPrincipal();
            refreshTokenService.revokeAllForUser(u.getId());
            log.debug("✅ Logout (tokens revocados) para: {}", u.getEmail());
        }
        return ResponseEntity.ok(Map.of("mensaje", "Sesión cerrada"));
    }

    /**
     * Retorna el usuario actual desde el SecurityContext.
     * Usa un DTO (MeResponse) para no serializar Usuario completo: evita
     * enviar password y no dispara lazy loading de alumno/maestro/personal
     * (consultas pesadas y N+1).
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401)
                    .body(new ErrorResponse(401, "No autenticado"));
            }

            Usuario usuario = (Usuario) authentication.getPrincipal();
            log.debug("✅ /me - Usuario: {}", usuario.getEmail());

            List<String> rolesListaMe = rolesEfectivosParaApi(usuario);

            MeResponse dto = new MeResponse(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getTipoUsuario().name(),
                usuario.getActivo(),
                rolesListaMe,
                resolverNombreCompleto(usuario.getId()),
                Boolean.TRUE.equals(usuario.getMustChangePassword())
            );
            return ResponseEntity.ok(dto);

        } catch (Exception e) {
            log.error("❌ Error en /me", e);
            return ResponseEntity.status(401)
                .body(new ErrorResponse(401, "Error al obtener usuario"));
        }
    }

    /** Solicitud para cambiar contraseña (primer acceso o cambio voluntario). */
    public static class ChangePasswordRequest {
        public String currentPassword;
        public String newPassword;
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest req, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof Usuario)) {
            return ResponseEntity.status(401).body(new ErrorResponse(401, "No autenticado"));
        }
        Usuario u = (Usuario) authentication.getPrincipal();
        try {
            String cur = req != null ? (req.currentPassword != null ? req.currentPassword : "") : "";
            String nw = req != null ? (req.newPassword != null ? req.newPassword : "") : "";
            if (nw.trim().length() < 6) {
                return ResponseEntity.badRequest().body(new ErrorResponse(400, "La nueva contraseña debe tener al menos 6 caracteres."));
            }
            if (!passwordEncoder.matches(cur, u.getPassword())) {
                return ResponseEntity.status(401).body(new ErrorResponse(401, "La contraseña actual no es correcta."));
            }
            // Actualizar y limpiar bandera
            usuarioService.cambiarPassword(u.getId(), nw.trim(), false);
            return ResponseEntity.ok(java.util.Map.of("mensaje", "Contraseña actualizada"));
        } catch (Exception e) {
            log.error("Error al cambiar contraseña", e);
            return ResponseEntity.status(500).body(new ErrorResponse(500, "No se pudo cambiar la contraseña"));
        }
    }

    /**
     * Endpoint de inicialización (SOLO para desarrollo/testing)
     */
    @PostMapping("/init-usuarios")
    public ResponseEntity<?> initUsers() {
        if (!allowInitUsuarios) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        try {
            log.info("🔧 Inicializando usuarios de prueba...");

            // Mismo criterio que DataLoader: solo administrador inicial (solo desarrollo).
            Object[][] usuarios = {
                {"sistemas@idee.edu.mx", "idee1234", Usuario.TipoUsuario.ADMIN}
            };

            Map<String, String> creados = new HashMap<>();

            for (Object[] data : usuarios) {
                String email = (String) data[0];
                String password = (String) data[1];
                Usuario.TipoUsuario tipo = (Usuario.TipoUsuario) data[2];

                if (!usuarioService.existsByEmail(email)) {
                    Usuario usuario = new Usuario();
                    usuario.setEmail(email);
                    usuario.setPassword(password);
                    usuario.setTipoUsuario(tipo);
                    usuario.setActivo(true);

                    usuarioService.create(usuario);
                    creados.put(email, "✅ Creado");
                } else {
                    creados.put(email, "ℹ️ Ya existe");
                }
            }

            log.info("✅ Inicialización completada. Usuarios: {}", creados.size());

            Map<String, Object> response = new HashMap<>();
            response.put("mensaje", "Inicialización completada");
            response.put("usuarios", creados);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error al inicializar usuarios", e);
            return ResponseEntity.status(500)
                .body(new ErrorResponse(500, "Error: " + e.getMessage()));
        }
    }

    /**
     * Roles expuestos al cliente: mismos que {@link Usuario#getRolesEfectivos()}, más {@code MAESTRO}
     * si existe ficha en {@code maestros} (alineado con {@code MaestroController} / portal docente).
     */
    private List<String> rolesEfectivosParaApi(Usuario usuario) {
        List<String> rolesLista = new ArrayList<>(usuario.getRolesEfectivos().stream()
                .map(Enum::name)
                .collect(Collectors.toList()));
        if (!rolesLista.contains(Usuario.TipoUsuario.MAESTRO.name())
                && maestroRepository.findByUsuarioId(usuario.getId()).isPresent()) {
            rolesLista.add(Usuario.TipoUsuario.MAESTRO.name());
        }
        return rolesLista;
    }

    private String resolverNombreCompleto(Long usuarioId) {
        if (usuarioId == null) {
            return null;
        }
        Optional<Personal> op = personalRepository.findByUsuario_Id(usuarioId);
        if (op.isPresent()) {
            Personal p = op.get();
            return joinNombreTres(p.getNombre(), p.getApellidoPaterno(), p.getApellidoMaterno());
        }
        Optional<Maestro> om = maestroRepository.findByUsuarioId(usuarioId);
        if (om.isPresent()) {
            Maestro m = om.get();
            return joinNombreTres(m.getNombre(), m.getApellidoPaterno(), m.getApellidoMaterno());
        }
        Optional<Alumno> oa = alumnoRepository.findByUsuarioId(usuarioId);
        return oa.map(Alumno::getNombreCompleto).orElse(null);
    }

    private static String joinNombreTres(String nombre, String apellidoPaterno, String apellidoMaterno) {
        StringBuilder sb = new StringBuilder();
        if (nombre != null && !nombre.isBlank()) {
            sb.append(nombre.trim());
        }
        if (apellidoPaterno != null && !apellidoPaterno.isBlank()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(apellidoPaterno.trim());
        }
        if (apellidoMaterno != null && !apellidoMaterno.isBlank()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(apellidoMaterno.trim());
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
}
