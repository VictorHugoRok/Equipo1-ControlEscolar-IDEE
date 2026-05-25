package com.idee.controlescolar.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cuenta intentos fallidos de login por IP en un intervalo de tiempo fijo desde el primer fallo.
 * Si pasa {@code windowSeconds} sin nuevos fallos, el contador se reinicia. Un login correcto limpia el contador.
 */
@Service
public class LoginAttemptService {

    @Value("${app.login-rate-limit-max:10}")
    private int maxFailuresPerWindow;

    @Value("${app.login-rate-limit-window-seconds:60}")
    private int windowSeconds;

    public int getMaxFailuresPerWindow() {
        return maxFailuresPerWindow;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    private static final class Window {
        long windowStartMs;
        int failures;
    }

    private final Map<String, Window> failuresByIp = new ConcurrentHashMap<>();

    public boolean isBlocked(String ip) {
        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;
        Window w = failuresByIp.get(ip);
        if (w == null) {
            return false;
        }
        if (now - w.windowStartMs > windowMs) {
            return false;
        }
        return w.failures >= maxFailuresPerWindow;
    }

    public void recordFailure(String ip) {
        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;
        failuresByIp.compute(ip, (k, v) -> {
            if (v == null || now - v.windowStartMs > windowMs) {
                Window n = new Window();
                n.windowStartMs = now;
                n.failures = 1;
                return n;
            }
            v.failures++;
            return v;
        });
    }

    public void clearFailures(String ip) {
        failuresByIp.remove(ip);
    }

    /**
     * Registra un intento fallido y devuelve contadores para mensajes al usuario.
     */
    public AttemptSummary recordFailureAndGetSummary(String ip) {
        recordFailure(ip);
        long now = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;
        Window w = failuresByIp.get(ip);
        int failures = 0;
        if (w != null && now - w.windowStartMs <= windowMs) {
            failures = w.failures;
        }
        int remaining = Math.max(0, maxFailuresPerWindow - failures);
        return new AttemptSummary(failures, maxFailuresPerWindow, remaining, windowSeconds);
    }

    public static final class AttemptSummary {
        public final int failedAttempts;
        public final int maxAttempts;
        /** Intentos fallidos que aún se permiten antes de bloquear (0 = ya no quedan en esta ventana). */
        public final int remainingBeforeBlock;
        public final int windowSeconds;

        public AttemptSummary(int failedAttempts, int maxAttempts, int remainingBeforeBlock, int windowSeconds) {
            this.failedAttempts = failedAttempts;
            this.maxAttempts = maxAttempts;
            this.remainingBeforeBlock = remainingBeforeBlock;
            this.windowSeconds = windowSeconds;
        }
    }
}
