package com.idee.controlescolar.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idee.controlescolar.dto.LoginErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Limita fuerza bruta por IP en {@code POST /api/auth/login}: bloquea si ya se superó el máximo
 * de fallos en la ventana. Los intentos fallidos se registran en el controlador de login;
 * un login exitoso reinicia el contador de la IP.
 */
@Component
@Order(1)
@Slf4j
@RequiredArgsConstructor
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final LoginAttemptService loginAttemptService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !request.getRequestURI().endsWith("/api/auth/login")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        if (loginAttemptService.isBlocked(clientIp)) {
            log.warn("Login bloqueado por demasiados fallos (IP): {}", clientIp);
            int ws = loginAttemptService.getWindowSeconds();
            int max = loginAttemptService.getMaxFailuresPerWindow();
            LoginErrorResponse body = new LoginErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                String.format(
                    "Por seguridad, el inicio de sesión está pausado un momento: hubo demasiados intentos fallidos. Espera unos %d segundos y vuelve a intentarlo.",
                    ws
                ),
                null,
                max,
                0,
                ws,
                ws
            );
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return;
        }

        var wrapper = new HttpStatusCapturingResponseWrapper(response);
        filterChain.doFilter(request, wrapper);

        int status = wrapper.getCapturedStatus();
        if (status >= 200 && status < 300) {
            loginAttemptService.clearFailures(clientIp);
        }
    }

    private static String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
