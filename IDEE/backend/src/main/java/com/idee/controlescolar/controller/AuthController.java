package com.idee.controlescolar.controller;
import com.idee.controlescolar.dto.AuthResponse;
import com.idee.controlescolar.dto.LoginRequest;
import com.idee.controlescolar.dto.ErrorResponse;
import com.idee.controlescolar.model.Usuario;
import com.idee.controlescolar.security.JwtUtil;
import com.idee.controlescolar.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UsuarioService usuarioService;

    /**
     * ÚNICO responsable de generar JWT
     * - Autentica con AuthenticationManager
     * - Genera token
     * - Retorna datos al cliente
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            log.debug("📝 Login attempt: {}", loginRequest.getEmail());

            // Autenticar con AuthenticationManager (valida credenciales)
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequest.getEmail(),
                    loginRequest.getPassword()
                )
            );

            // Generar JWT
            Usuario usuario = (Usuario) authentication.getPrincipal();
            String jwt = jwtUtil.generateToken(usuario);

            log.info("✅ Login exitoso: {}", usuario.getEmail());

            // Retornar token + datos del usuario
            return ResponseEntity.ok(new AuthResponse(
                jwt,
                usuario.getEmail(),
                usuario.getTipoUsuario().name(),
                usuario.getId()
            ));

        } catch (AuthenticationException e) {
            log.warn("❌ Autenticación fallida: {}", loginRequest.getEmail());
            return ResponseEntity.status(401)
                .body(new ErrorResponse(401, "Credenciales incorrectas"));
        } catch (Exception e) {
            log.error("❌ Error en login", e);
            return ResponseEntity.status(500)
                .body(new ErrorResponse(500, "Error interno del servidor"));
        }
    }

    /**
     * Retorna el usuario actual desde el SecurityContext
     * - El JwtAuthenticationFilter YA validó el token
     * - Spring Security inyecta la Authentication
     * - NO validar token aquí
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

            return ResponseEntity.ok(usuario);

        } catch (Exception e) {
            log.error("❌ Error en /me", e);
            return ResponseEntity.status(401)
                .body(new ErrorResponse(401, "Error al obtener usuario"));
        }
    }

    /**
     * Endpoint de inicialización (SOLO para desarrollo/testing)
     */
    @PostMapping("/init-usuarios")
    public ResponseEntity<?> initUsers() {
        try {
            log.info("🔧 Inicializando usuarios de prueba...");

            Object[][] usuarios = {
                {"admin@idee.edu.mx", "admin123", Usuario.TipoUsuario.ADMIN},
                {"secacademica@idee.edu.mx", "secacademica123", Usuario.TipoUsuario.SECRETARIA_ACADEMICA},
                {"maestro@idee.edu.mx", "maestro123", Usuario.TipoUsuario.MAESTRO},
                {"alumno@idee.edu.mx", "alumno123", Usuario.TipoUsuario.ALUMNO}
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
}
