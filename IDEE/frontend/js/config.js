/**
 * Configuración global de la aplicación
 * Este archivo debe cargarse PRIMERO antes de cualquier otro script
 *
 * API (dev vs prod):
 * - Local (Live Server, etc.): http://localhost:8080/api → backend con mvn spring-boot:run
 * - Producción (VPS): ruta relativa /api → mismo origen; Nginx (u otro) debe enrutar /api al Spring Boot
 *
 * Override opcional antes de cargar config.js: window.__API_BASE__ = 'https://otro.dominio.com/api'
 */

(function () {
    function resolveApiBaseUrl() {
        if (typeof window !== 'undefined' && window.__API_BASE__) {
            var o = String(window.__API_BASE__).trim().replace(/\/$/, '');
            if (o) return o;
        }
        var host = '';
        try {
            host = (window.location && window.location.hostname) ? String(window.location.hostname) : '';
        } catch (e) {}
        // Desarrollo en esta máquina (Live Server suele ser localhost o 127.0.0.1)
        if (host === 'localhost' || host === '127.0.0.1' || host === '') {
            var devPort = (typeof window !== 'undefined' && window.__BACKEND_PORT__) ? String(window.__BACKEND_PORT__) : '8080';
            return 'http://localhost:' + devPort + '/api';
        }
        // Mismo sitio que la página (VPS con proxy /api → backend)
        return '/api';
    }

    var base = resolveApiBaseUrl();

    // Asignar globales esperadas por el resto del front (const en ámbito script)
    window.__RESOLVED_API_BASE__ = base;
})();

const API_URL = typeof window !== 'undefined' && window.__RESOLVED_API_BASE__
    ? window.__RESOLVED_API_BASE__
    : 'http://localhost:8080/api';
const API_BASE_URL = API_URL;

// Cierre de sesión por inactividad (solo con la app visible; cualquier interacción reinicia el contador)
const AUTH_IDLE_TIMEOUT_MS = 30 * 60 * 1000;

// Bandera de desarrollo
const DEV_SKIP_AUTH = false;

// Exportar para uso en módulos
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { API_URL, API_BASE_URL, AUTH_IDLE_TIMEOUT_MS };
}
