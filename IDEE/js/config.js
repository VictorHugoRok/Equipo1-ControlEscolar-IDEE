/**
 * Configuración global de la aplicación
 * Este archivo debe cargarse PRIMERO antes de cualquier otro script
 */

// Configuración de la API
const API_URL = 'http://localhost:8080/api';
const API_BASE_URL = 'http://localhost:8080/api';

// Bandera de desarrollo: al activar permite que el frontend haga peticiones
// al backend sin requerir un token. Útil para pruebas locales cuando
// el backend está configurado como `permitAll()`.
const DEV_SKIP_AUTH = false;  // ← CAMBIAR A false PARA ACTIVAR AUTENTICACIÓN REAL

// Exportar para uso en módulos
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { API_URL, API_BASE_URL };
}
