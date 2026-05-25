// ============================================
// JWT Authentication - Frontend (login seguro + refresh token)
// ============================================

var AUTH_REFRESH_TOKEN_KEY = 'refreshToken';
var AUTH_TOKEN_EXPIRES_AT_KEY = 'tokenExpiresAt';
var AUTH_PROACTIVE_REFRESH_MS = 5 * 60 * 1000; // Renovar 5 min antes de que expire

function getApiBaseUrl() {
    return (typeof API_URL !== 'undefined' && API_URL) ? API_URL : 'http://localhost:8080/api';
}

/**
 * Prefijo de ruta cuando la app está bajo un subdirectorio (p. ej. /app/index.html → /app/).
 */
function getAppPathPrefix() {
    var path = window.location.pathname || '';
    if (path.indexOf('/pages/') !== -1) {
        return path.substring(0, path.indexOf('/pages/') + 1);
    }
    var dir = path.replace(/\/[^/]*$/, '/');
    return dir === '/' ? '' : dir;
}

/**
 * Login con email y contraseña.
 * Guarda access token, refresh token y hora de expiración para renovación automática.
 * Los errores incluyen `loginHttpStatus` y `loginPayload` cuando el servidor devuelve JSON estructurado.
 */
function isApiMixedContentBlocked() {
    if (typeof location === 'undefined') return false;
    if (location.protocol !== 'https:') return false;
    var b = getApiBaseUrl();
    return b && b.indexOf('http://') === 0;
}

async function login(email, password) {
    var apiBase = getApiBaseUrl();
    try {
        if (isApiMixedContentBlocked()) {
            var mixed = new Error(
                'La página carga con HTTPS y el navegador bloquea la conexión al servidor (HTTP). Usa una dirección HTTPS del API o publica web y API en el mismo sitio (por ejemplo /api).'
            );
            mixed.loginHttpStatus = 0;
            mixed.code = 'MIXED_CONTENT';
            throw mixed;
        }

        console.log('🔐 [AUTH] Iniciando login para:', email);

        const response = await fetch(`${apiBase}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password })
        });

        const contentType = (response.headers.get('Content-Type') || '');
        const isJson = contentType.indexOf('application/json') !== -1;
        const body = isJson ? await response.json().catch(function () { return {}; }) : {};

        if (response.status === 429) {
            var e429 = new Error(body.message || 'Demasiados intentos fallidos. Debes esperar antes de volver a intentarlo.');
            e429.loginHttpStatus = 429;
            e429.loginPayload = body;
            throw e429;
        }

        if (response.status === 400) {
            var detalle400 = body.message || (body.errors && body.errors[0] && body.errors[0].defaultMessage) || '';
            var e400 = new Error(detalle400 || 'Revisa el correo y la contraseña: hay datos inválidos o incompletos.');
            e400.loginHttpStatus = 400;
            e400.loginPayload = body;
            throw e400;
        }

        if (response.status === 401) {
            var e401 = new Error(body.message || 'Correo o contraseña incorrectos.');
            e401.loginHttpStatus = 401;
            e401.loginPayload = body;
            throw e401;
        }

        if (response.status === 403) {
            var e403 = new Error(body.message || 'Acceso denegado. Si el problema continúa, contacta a soporte.');
            e403.loginHttpStatus = 403;
            e403.loginPayload = body;
            throw e403;
        }

        if (response.status >= 500) {
            var e5 = new Error(body.message || 'El servidor no pudo completar el inicio de sesión. Intenta de nuevo en unos minutos.');
            e5.loginHttpStatus = response.status;
            e5.loginPayload = body;
            throw e5;
        }

        if (!response.ok) {
            var eOther = new Error(body.message || ('Error al iniciar sesión (código ' + response.status + ').'));
            eOther.loginHttpStatus = response.status;
            eOther.loginPayload = body;
            throw eOther;
        }

        const data = await Promise.resolve(body);
        console.log('✅ [AUTH] Login exitoso:', data.email);

        if (!data.token) {
            var eNoTok = new Error('El servidor no devolvió un token de acceso. Intenta de nuevo o contacta a soporte.');
            eNoTok.loginHttpStatus = response.status;
            throw eNoTok;
        }

        saveSession(data);
        scheduleProactiveRefresh(data.expiresIn);
        console.log('💾 [AUTH] Sesión guardada (token + refresh token)');
        return data;
    } catch (error) {
        if (error && error.code === 'MIXED_CONTENT') {
            throw error;
        }
        if (error && error.loginHttpStatus != null) {
            console.error('❌ [AUTH] Error en login:', error.message);
            throw error;
        }
        if (error instanceof TypeError) {
            var net = new Error(
                'No pudimos conectar con el servidor. Comprueba la dirección del sistema, que el servicio esté en marcha y que no haya bloqueos de red o CORS.'
            );
            net.loginHttpStatus = 0;
            net.code = 'NETWORK';
            console.error('❌ [AUTH] Comunicación:', error);
            throw net;
        }
        console.error('❌ [AUTH] Error en login:', error && error.message);
        throw error;
    }
}

/**
 * Mensaje legible para la UI a partir del error devuelto por login().
 */
function formatLoginErrorForUser(err) {
    if (!err) return 'No se pudo iniciar sesión. Intenta de nuevo.';
    if (err.code === 'MIXED_CONTENT' || err.code === 'NETWORK') return err.message;
    if (err.message) return err.message;
    return 'No se pudo iniciar sesión. Intenta de nuevo.';
}

/** Monitor de inactividad: cierra sesión tras AUTH_IDLE_TIMEOUT_MS sin interacción (pestaña visible). */
var _idleMonitorInstalled = false;
var _idleCheckInterval = null;
var _idleLastBump = 0;
var _idleBumpHandler = null;

function getIdleTimeoutMs() {
    if (typeof AUTH_IDLE_TIMEOUT_MS !== 'undefined' && AUTH_IDLE_TIMEOUT_MS > 60000) {
        return AUTH_IDLE_TIMEOUT_MS;
    }
    return 30 * 60 * 1000;
}

function bumpIdleActivity() {
    _idleLastBump = Date.now();
}

function initIdleSessionMonitor() {
    if (typeof document === 'undefined') return;
    bumpIdleActivity();
    if (_idleMonitorInstalled) return;
    var events = ['mousedown', 'keydown', 'touchstart', 'scroll', 'click', 'wheel'];
    _idleBumpHandler = function () {
        if (typeof document !== 'undefined' && document.visibilityState === 'visible') {
            bumpIdleActivity();
        }
    };
    events.forEach(function (ev) {
        document.addEventListener(ev, _idleBumpHandler, true);
    });
    _idleMonitorInstalled = true;
    _idleCheckInterval = setInterval(function () {
        if (typeof document !== 'undefined' && document.visibilityState === 'hidden') return;
        if (typeof isAuthenticated !== 'function' || !isAuthenticated()) {
            stopIdleSessionMonitor();
            return;
        }
        if (Date.now() - _idleLastBump > getIdleTimeoutMs()) {
            stopIdleSessionMonitor();
            logout('Tu sesión se cerró por inactividad.');
        }
    }, 15000);
}

function stopIdleSessionMonitor() {
    if (_idleCheckInterval) {
        clearInterval(_idleCheckInterval);
        _idleCheckInterval = null;
    }
    if (_idleBumpHandler && typeof document !== 'undefined') {
        var events = ['mousedown', 'keydown', 'touchstart', 'scroll', 'click', 'wheel'];
        events.forEach(function (ev) {
            document.removeEventListener(ev, _idleBumpHandler, true);
        });
    }
    _idleBumpHandler = null;
    _idleMonitorInstalled = false;
}

function tryInitIdleWhenReady() {
    if (typeof document === 'undefined' || typeof isAuthenticated !== 'function') return;
    if (!isAuthenticated()) return;
    initIdleSessionMonitor();
}

if (typeof document !== 'undefined') {
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', tryInitIdleWhenReady);
    } else {
        tryInitIdleWhenReady();
    }
}

/**
 * Guarda en localStorage: token, refreshToken, usuario y hora de expiración del token.
 */
/**
 * Alinea rolActivo y userTipo con la lista de roles (multi-rol / selector de perfil).
 * Definida aquí para que login funcione aunque sistema-roles-unified.js no esté cargado.
 */
function ensureRolActivoInStorage() {
    try {
        var raw = localStorage.getItem('userRoles');
        var lista = [];
        if (raw) {
            var parsed = JSON.parse(raw);
            if (Array.isArray(parsed)) lista = parsed;
        }
        if (!lista.length) {
            var ut = localStorage.getItem('userTipo');
            if (ut) lista = [ut];
        }
        if (!lista.length) return;
        var preferred = lista.find(function (r) { return r && r !== 'SIN_ROL'; }) || lista[0];
        var st = localStorage.getItem('rolActivo');
        if (!st || lista.indexOf(st) === -1) {
            localStorage.setItem('rolActivo', preferred);
        } else if (st === 'SIN_ROL' && preferred && preferred !== 'SIN_ROL') {
            localStorage.setItem('rolActivo', preferred);
        }
        localStorage.setItem('userTipo', localStorage.getItem('rolActivo'));
    } catch (e) { /* ignorar */ }
}

/**
 * Nombre para mostrar en el menú lateral: capitaliza cada palabra sin tocar la base de datos.
 * Si ves el nombre en minúsculas, suele ser porque se capturó así en el formulario o en la ficha (Personal/Maestro).
 */
function formatearNombreMostrar(raw) {
    if (raw == null || typeof raw !== 'string') {
        return '';
    }
    var s = raw.trim();
    if (!s) {
        return '';
    }
    return s.split(/\s+/).map(function (w) {
        if (!w.length) {
            return w;
        }
        return w.charAt(0).toUpperCase() + w.slice(1).toLowerCase();
    }).join(' ');
}

/**
 * Roles efectivos desde localStorage (multi-rol). Misma semántica que el resto del front.
 */
function obtenerRolesDesdeStorage() {
    try {
        var raw = localStorage.getItem('userRoles');
        if (raw) {
            var parsed = JSON.parse(raw);
            if (Array.isArray(parsed) && parsed.length) return parsed;
        }
    } catch (e) { /* ignorar */ }
    var t = localStorage.getItem('userTipo');
    return t ? [t] : [];
}

/** Sin ningún rol operativo: lista vacía o solo SIN_ROL */
function usuarioEsSoloSinRol() {
    var roles = obtenerRolesDesdeStorage();
    if (!roles.length) return true;
    return roles.every(function (r) { return r === 'SIN_ROL'; });
}

function usuarioTieneRolOperativo() {
    return obtenerRolesDesdeStorage().some(function (r) { return r && r !== 'SIN_ROL'; });
}

function navigateToAppPage(destination) {
    if (window.location.protocol === 'file:') {
        window.location.href = destination;
        return;
    }
    var prefix = typeof getAppPathPrefix === 'function' ? getAppPathPrefix() : '';
    if (prefix) {
        window.location.href = window.location.origin + prefix + destination;
    } else {
        window.location.href = destination.indexOf('/') === 0 ? destination : '/' + destination;
    }
}

function redirectToSinRolAsignadoPage() {
    navigateToAppPage('pages/sin-rol-asignado.html');
}

/**
 * Usuarios solo con SIN_ROL no deben ver el portal; solo la pantalla informativa.
 * No redirige desde la raíz de login (index) ni desde la propia página sin rol.
 */
function enforceSoloSinRolLandingPage() {
    if (typeof DEV_SKIP_AUTH !== 'undefined' && DEV_SKIP_AUTH) return false;
    if (typeof usuarioEsSoloSinRol !== 'function' || !usuarioEsSoloSinRol()) return false;
    var path = window.location.pathname || '';
    var href = window.location.href || '';
    if (href.indexOf('sin-rol-asignado.html') !== -1) return false;
    if (path.indexOf('index.html') !== -1 && path.indexOf('/pages/') === -1) return false;
    redirectToSinRolAsignadoPage();
    return true;
}

function saveSession(data) {
    var expiresInSec = data.expiresIn != null ? data.expiresIn : 86400;
    var expiresAt = Date.now() + (expiresInSec * 1000);
    localStorage.setItem('token', data.token);
    localStorage.setItem(AUTH_REFRESH_TOKEN_KEY, data.refreshToken || '');
    localStorage.setItem(AUTH_TOKEN_EXPIRES_AT_KEY, String(expiresAt));
    localStorage.setItem('userEmail', data.email);
    localStorage.setItem('userTipo', data.tipoUsuario);
    if (data.roles && Array.isArray(data.roles) && data.roles.length) {
        localStorage.setItem('userRoles', JSON.stringify(data.roles));
    } else if (data.tipoUsuario) {
        localStorage.setItem('userRoles', JSON.stringify([data.tipoUsuario]));
    }
    if (data.nombreCompleto) {
        localStorage.setItem('userNombreCompleto', data.nombreCompleto);
    } else {
        localStorage.removeItem('userNombreCompleto');
    }
    // Forzar cambio de contraseña en el siguiente acceso (si aplica)
    try {
        var must = !!(data && data.mustChangePassword);
        localStorage.setItem('mustChangePassword', must ? 'true' : 'false');
    } catch (e) { /* ignorar */ }
    var uid = data.usuarioId != null ? data.usuarioId : data.id;
    localStorage.setItem('userId', uid != null ? String(uid) : '');
    ensureRolActivoInStorage();
    initIdleSessionMonitor();
}

/**
 * Programa una renovación automática del token unos minutos antes de que expire.
 */
var _proactiveRefreshTimer = null;
function scheduleProactiveRefresh(expiresInSec) {
    if (_proactiveRefreshTimer) {
        clearTimeout(_proactiveRefreshTimer);
        _proactiveRefreshTimer = null;
    }
    if (!expiresInSec || expiresInSec < 60) return;
    var msUntilRefresh = (expiresInSec * 1000) - AUTH_PROACTIVE_REFRESH_MS;
    if (msUntilRefresh < 10000) msUntilRefresh = 10000;
    _proactiveRefreshTimer = setTimeout(function () {
        _proactiveRefreshTimer = null;
        refreshAccessToken().then(function () {
            var expAt = localStorage.getItem(AUTH_TOKEN_EXPIRES_AT_KEY);
            if (expAt) {
                var secsLeft = Math.floor((Number(expAt) - Date.now()) / 1000);
                if (secsLeft > 60) scheduleProactiveRefresh(secsLeft);
            }
        }).catch(function () {});
    }, msUntilRefresh);
}

/** Evita múltiples redirecciones al cerrar sesión */
var _logoutInProgress = false;
/** Una sola petición de refresh a la vez */
var _refreshPromise = null;
/** Una sola petición /auth/me por carga de página */
var _getCurrentUserPromise = null;

var AUTH_ME_CACHE_KEY = 'auth_me_cache';
var AUTH_ME_CACHE_TTL_MS = 2 * 60 * 1000;

/**
 * Guard global contra recargas accidentales por formularios.
 * Permite submit nativo solo si el form define: data-allow-native-submit="true".
 */
function installGlobalFormGuard() {
    if (window.__globalFormGuardInstalled) return;
    window.__globalFormGuardInstalled = true;

    // Normaliza botones sin type dentro de forms.
    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('form button:not([type])').forEach(function (btn) {
            btn.setAttribute('type', 'button');
        });
    });

    // Bloquear navegación por submit nativo no explícitamente permitido.
    document.addEventListener('submit', function (event) {
        var form = event.target;
        if (!form || form.tagName !== 'FORM') return;
        if (form.dataset && form.dataset.allowNativeSubmit === 'true') return;
        event.preventDefault();
        event.stopPropagation();
    }, true);

    // Evitar submit implícito por Enter en formularios JS.
    document.addEventListener('keydown', function (event) {
        if (event.key !== 'Enter') return;
        if (event.defaultPrevented) return;
        var target = event.target;
        if (!target) return;
        if (target.tagName === 'TEXTAREA') return;
        var form = target.closest ? target.closest('form') : null;
        if (!form) return;
        if (form.dataset && form.dataset.allowNativeSubmit === 'true') return;
        event.preventDefault();
    }, true);

    // Blindaje adicional para submits programáticos.
    var nativeSubmit = HTMLFormElement.prototype.submit;
    HTMLFormElement.prototype.submit = function () {
        if (this && this.dataset && this.dataset.allowNativeSubmit === 'true') {
            return nativeSubmit.call(this);
        }
        var ev = new Event('submit', { bubbles: true, cancelable: true });
        this.dispatchEvent(ev);
        return false;
    };
}
installGlobalFormGuard();

function getAuthMeCache() {
    try {
        var raw = sessionStorage.getItem(AUTH_ME_CACHE_KEY);
        if (!raw) return null;
        var obj = JSON.parse(raw);
        if (!obj || !obj.data || !obj.ts) return null;
        if (Date.now() - obj.ts > AUTH_ME_CACHE_TTL_MS) return null;
        return obj.data;
    } catch (e) {
        return null;
    }
}

function setAuthMeCache(userData) {
    try {
        sessionStorage.setItem(AUTH_ME_CACHE_KEY, JSON.stringify({ data: userData, ts: Date.now() }));
    } catch (e) {}
}

function clearAuthMeCache() {
    try {
        sessionStorage.removeItem(AUTH_ME_CACHE_KEY);
    } catch (e) {}
}

function resetAuthStateForLoginPage() {
    _logoutInProgress = false;
    _getCurrentUserPromise = null;
    _refreshPromise = null;
    clearAuthMeCache();
}

/**
 * Renovar access token usando refresh token. Si hay otro refresh en curso, reutiliza esa promesa.
 */
function refreshAccessToken() {
    var ref = getRefreshToken();
    if (!ref) return Promise.reject(new Error('No hay refresh token'));

    if (_refreshPromise) return _refreshPromise;

    _refreshPromise = fetch(`${getApiBaseUrl()}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: ref })
    }).then(function (response) {
        _refreshPromise = null;
        if (!response.ok) {
            if (response.status === 401) {
                console.warn('[AUTH] Refresh token rechazado');
                logout('Tu sesión expiró o cerró por seguridad. Inicia sesión de nuevo.');
            }
            return Promise.reject(new Error('Refresh falló'));
        }
        return response.json();
    }).then(function (data) {
        if (data.token) {
            var expiresInSec = data.expiresIn != null ? data.expiresIn : 86400;
            var expiresAt = Date.now() + (expiresInSec * 1000);
            localStorage.setItem('token', data.token);
            localStorage.setItem(AUTH_TOKEN_EXPIRES_AT_KEY, String(expiresAt));
            if (data.refreshToken) {
                localStorage.setItem(AUTH_REFRESH_TOKEN_KEY, data.refreshToken);
            }
            scheduleProactiveRefresh(expiresInSec);
            console.log('✅ [AUTH] Token renovado automáticamente');
        }
        return data;
    }).catch(function (err) {
        _refreshPromise = null;
        return Promise.reject(err);
    });

    return _refreshPromise;
}

function getRefreshToken() {
    return localStorage.getItem(AUTH_REFRESH_TOKEN_KEY) || '';
}

/**
 * Logout: limpia sesión local, opcionalmente notifica al backend y redirige a login.
 * @param {string} [loginNotice] Si se indica, se guarda en sessionStorage para mostrarlo en index.html
 */
function logout(loginNotice) {
    stopIdleSessionMonitor();
    if (_logoutInProgress) return;
    _logoutInProgress = true;
    if (loginNotice) {
        try {
            sessionStorage.setItem('login_notice', loginNotice);
        } catch (e) {}
    }
    console.warn('👋 [AUTH] Cerrando sesión');
    _getCurrentUserPromise = null;
    _refreshPromise = null;
    clearAuthMeCache();
    if (_proactiveRefreshTimer) {
        clearTimeout(_proactiveRefreshTimer);
        _proactiveRefreshTimer = null;
    }

    var token = getToken();
    localStorage.removeItem('token');
    localStorage.removeItem(AUTH_REFRESH_TOKEN_KEY);
    localStorage.removeItem(AUTH_TOKEN_EXPIRES_AT_KEY);
    localStorage.removeItem('userEmail');
    localStorage.removeItem('userTipo');
    localStorage.removeItem('userRoles');
    localStorage.removeItem('userId');
    localStorage.removeItem('rolActivo');
    localStorage.removeItem('userNombreCompleto');

    var logoutFinished = false;
    function finishLogout() {
        if (logoutFinished) return;
        logoutFinished = true;
        _logoutInProgress = false;
        var path = window.location.pathname || '';
        if (path.endsWith('index.html') || path === '/' || path.endsWith('/')) {
            return;
        }
        window.location.href = getLoginPath();
    }

    if (token) {
        fetch(`${getApiBaseUrl()}/auth/logout`, {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' },
            keepalive: true
        })
            .catch(function () {})
            .finally(finishLogout);
        setTimeout(finishLogout, 5000);
    } else {
        finishLogout();
    }
}

function getToken() {
    return localStorage.getItem('token');
}

function mustChangePasswordFlag() {
    try {
        return (localStorage.getItem('mustChangePassword') || '') === 'true';
    } catch (e) {
        return false;
    }
}

function setMustChangePasswordFlag(v) {
    try {
        localStorage.setItem('mustChangePassword', v ? 'true' : 'false');
    } catch (e) {}
}

function ensurePasswordChangeModal() {
    if (typeof document === 'undefined') return;
    if (document.getElementById('modalForcePasswordChange')) return;

    var host = document.createElement('div');
    host.innerHTML = [
        '<div class="modal fade" id="modalForcePasswordChange" tabindex="-1" aria-hidden="true">',
        '  <div class="modal-dialog modal-dialog-centered">',
        '    <div class="modal-content">',
        '      <div class="modal-header">',
        '        <h5 class="modal-title">Actualiza tu contraseña</h5>',
        '      </div>',
        '      <div class="modal-body">',
        '        <div class="alert alert-warning small mb-3">',
        '          Por seguridad, debes cambiar tu contraseña antes de continuar.',
        '        </div>',
        '        <div id="forcePwdFeedback" class="alert d-none" role="alert"></div>',
        '        <div class="mb-2">',
        '          <label class="form-label" for="forcePwdCurrent">Contraseña actual</label>',
        '          <div class="input-group">',
        '            <input type="password" class="form-control" id="forcePwdCurrent" autocomplete="current-password" />',
        '            <button class="btn btn-outline-secondary" type="button" id="toggleForcePwdCurrent" aria-label="Mostrar u ocultar contraseña actual" title="Mostrar/ocultar">',
        '              <i class="bi bi-eye" aria-hidden="true"></i>',
        '            </button>',
        '          </div>',
        '        </div>',
        '        <div class="mb-2">',
        '          <label class="form-label" for="forcePwdNew">Nueva contraseña</label>',
        '          <div class="input-group">',
        '            <input type="password" class="form-control" id="forcePwdNew" autocomplete="new-password" />',
        '            <button class="btn btn-outline-secondary" type="button" id="toggleForcePwdNew" aria-label="Mostrar u ocultar nueva contraseña" title="Mostrar/ocultar">',
        '              <i class="bi bi-eye" aria-hidden="true"></i>',
        '            </button>',
        '          </div>',
        '          <div class="form-text">Mínimo 6 caracteres.</div>',
        '        </div>',
        '        <div class="mb-1">',
        '          <label class="form-label" for="forcePwdNew2">Confirmar nueva contraseña</label>',
        '          <div class="input-group">',
        '            <input type="password" class="form-control" id="forcePwdNew2" autocomplete="new-password" />',
        '            <button class="btn btn-outline-secondary" type="button" id="toggleForcePwdNew2" aria-label="Mostrar u ocultar confirmación de contraseña" title="Mostrar/ocultar">',
        '              <i class="bi bi-eye" aria-hidden="true"></i>',
        '            </button>',
        '          </div>',
        '        </div>',
        '      </div>',
        '      <div class="modal-footer">',
        '        <button type="button" class="btn btn-ide" id="btnForcePwdSave">Guardar contraseña</button>',
        '      </div>',
        '    </div>',
        '  </div>',
        '</div>'
    ].join('');
    document.body.appendChild(host.firstChild);
}

function showForcePasswordChangeModal(opts) {
    opts = opts || {};
    if (typeof document === 'undefined') return;
    ensurePasswordChangeModal();

    var modalEl = document.getElementById('modalForcePasswordChange');
    if (!modalEl) return;

    var fb = document.getElementById('forcePwdFeedback');
    var cur = document.getElementById('forcePwdCurrent');
    var nw = document.getElementById('forcePwdNew');
    var nw2 = document.getElementById('forcePwdNew2');
    var btn = document.getElementById('btnForcePwdSave');
    var tCur = document.getElementById('toggleForcePwdCurrent');
    var tNew = document.getElementById('toggleForcePwdNew');
    var tNew2 = document.getElementById('toggleForcePwdNew2');

    function setFeedback(msg, type) {
        if (!fb) return;
        fb.classList.remove('d-none', 'alert-danger', 'alert-success', 'alert-warning');
        fb.classList.add(type ? ('alert-' + type) : 'alert-warning');
        fb.textContent = msg || '';
    }

    function clearFeedback() {
        if (!fb) return;
        fb.classList.add('d-none');
        fb.textContent = '';
    }

    function setLoading(v) {
        if (!btn) return;
        btn.disabled = !!v;
        btn.textContent = v ? 'Guardando…' : 'Guardar contraseña';
    }

    if (cur) cur.value = '';
    if (nw) nw.value = '';
    if (nw2) nw2.value = '';
    clearFeedback();
    setLoading(false);

    function bindToggleOnce(btnEl, inputEl) {
        if (!btnEl || !inputEl) return;
        if (btnEl.__boundPwdToggle) return;
        btnEl.__boundPwdToggle = true;
        btnEl.addEventListener('click', function () {
            var isPassword = (inputEl.getAttribute('type') || 'password') === 'password';
            inputEl.setAttribute('type', isPassword ? 'text' : 'password');
            var icon = btnEl.querySelector ? btnEl.querySelector('i') : null;
            if (icon && icon.classList) {
                icon.classList.remove('bi-eye', 'bi-eye-slash');
                icon.classList.add(isPassword ? 'bi-eye-slash' : 'bi-eye');
            }
        });
    }

    bindToggleOnce(tCur, cur);
    bindToggleOnce(tNew, nw);
    bindToggleOnce(tNew2, nw2);

    if (!btn.__forcePwdBound) {
        btn.__forcePwdBound = true;
        btn.addEventListener('click', async function () {
            try {
                clearFeedback();
                var currentPassword = (cur && cur.value || '').trim();
                var newPassword = (nw && nw.value || '').trim();
                var newPassword2 = (nw2 && nw2.value || '').trim();

                if (!currentPassword) {
                    setFeedback('Ingresa tu contraseña actual.', 'warning');
                    return;
                }
                if (newPassword.length < 6) {
                    setFeedback('La nueva contraseña debe tener al menos 6 caracteres.', 'warning');
                    return;
                }
                if (newPassword !== newPassword2) {
                    setFeedback('La confirmación no coincide.', 'warning');
                    return;
                }

                setLoading(true);
                await authFetch('/auth/change-password', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ currentPassword: currentPassword, newPassword: newPassword })
                });
                setMustChangePasswordFlag(false);

                setFeedback('Contraseña actualizada. Continuando…', 'success');
                setTimeout(function () {
                    try {
                        if (opts && typeof opts.onSuccess === 'function') opts.onSuccess();
                        else window.location.reload();
                    } catch (e) {}
                }, 350);
            } catch (e) {
                console.error(e);
                var msg = (e && e.message) ? e.message : 'No se pudo actualizar la contraseña.';
                setFeedback(msg, 'danger');
            } finally {
                setLoading(false);
            }
        });
    }

    // Mostrar con Bootstrap si está disponible; si no, fallback simple
    try {
        if (window.bootstrap && window.bootstrap.Modal) {
            var m = window.bootstrap.Modal.getOrCreateInstance(modalEl, { backdrop: 'static', keyboard: false });
            m.show();
        } else {
            modalEl.classList.add('show');
            modalEl.style.display = 'block';
            modalEl.removeAttribute('aria-hidden');
            modalEl.setAttribute('aria-modal', 'true');
            // backdrop manual
            if (!document.getElementById('forcePwdBackdrop')) {
                var b = document.createElement('div');
                b.id = 'forcePwdBackdrop';
                b.className = 'modal-backdrop fade show';
                document.body.appendChild(b);
            }
        }
    } catch (e) {}
}

function enforceMustChangePasswordOnPage() {
    if (typeof document === 'undefined') return;
    if (typeof DEV_SKIP_AUTH !== 'undefined' && DEV_SKIP_AUTH) return;
    if (window.location && String(window.location.pathname || '').indexOf('index.html') !== -1) return;
    if (!mustChangePasswordFlag()) return;
    if (typeof isAuthenticated === 'function' && !isAuthenticated()) return;
    showForcePasswordChangeModal();
}

if (typeof document !== 'undefined') {
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', enforceMustChangePasswordOnPage);
    } else {
        enforceMustChangePasswordOnPage();
    }
}

/**
 * Verificación local del JWT (solo expiración; no valida firma).
 */
function isValidJwt(token) {
    if (!token) return false;
    try {
        var parts = token.split('.');
        if (parts.length !== 3) return false;
        var decoded = JSON.parse(atob(parts[1]));
        if (!decoded.exp) return false;
        var expiryTime = decoded.exp * 1000;
        return expiryTime > Date.now();
    } catch (e) {
        return false;
    }
}

/**
 * Hay sesión si tenemos token válido O refresh token (podemos renovar sin volver a login).
 */
function isAuthenticated() {
    var token = getToken();
    if (token && isValidJwt(token)) return true;
    if (getRefreshToken()) return true;
    return false;
}

/**
 * Obtiene el usuario actual. Si el token expiró o falta, intenta renovar con refresh token y luego /me.
 */
async function getCurrentUser() {
    var cached = getAuthMeCache();
    if (cached) return Promise.resolve(cached);

    if (_getCurrentUserPromise) return _getCurrentUserPromise;

    _getCurrentUserPromise = (async function () {
        var currentToken = getToken();
        if (!currentToken || !isValidJwt(currentToken)) {
            try {
                await refreshAccessToken();
                currentToken = getToken();
            } catch (e) {
                _getCurrentUserPromise = null;
                if (!getRefreshToken()) throw new Error('No hay token');
                throw new Error('Sesión expirada');
            }
        }
        if (!currentToken) throw new Error('No hay token');

        try {
            var apiBase = getApiBaseUrl();
            var response = await fetch(`${apiBase}/auth/me`, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': 'Bearer ' + currentToken
                }
            });

            if (response.status === 401) {
                try {
                    await refreshAccessToken();
                    currentToken = getToken();
                    response = await fetch(`${apiBase}/auth/me`, {
                        method: 'GET',
                        headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + currentToken }
                    });
                } catch (e) {
                    _getCurrentUserPromise = null;
                    clearAuthMeCache();
                    logout('Tu sesión expiró o cerró por seguridad. Inicia sesión de nuevo.');
                    throw new Error('Sesión expirada');
                }
            }

            if (!response.ok) {
                _getCurrentUserPromise = null;
                throw new Error('Error ' + response.status);
            }

            var userData = await response.json();
            if (userData.tipoUsuario) {
                localStorage.setItem('userTipo', userData.tipoUsuario);
            }
            if (userData.roles && Array.isArray(userData.roles) && userData.roles.length) {
                localStorage.setItem('userRoles', JSON.stringify(userData.roles));
            } else if (userData.tipoUsuario) {
                localStorage.setItem('userRoles', JSON.stringify([userData.tipoUsuario]));
            }
            if (userData.nombreCompleto) {
                localStorage.setItem('userNombreCompleto', userData.nombreCompleto);
            }
            setMustChangePasswordFlag(!!(userData && userData.mustChangePassword));
            ensureRolActivoInStorage();
            setAuthMeCache(userData);
            return userData;
        } catch (error) {
            _getCurrentUserPromise = null;
            if (error.message && error.message.includes('Sesión expirada')) {
                clearAuthMeCache();
                logout('Tu sesión expiró o cerró por seguridad. Inicia sesión de nuevo.');
            }
            throw error;
        }
    })();

    return _getCurrentUserPromise;
}

/**
 * Peticiones HTTP con JWT. En 401 intenta renovar token una vez y reintenta la petición.
 */
async function authFetch(url, options = {}) {
    var token = getToken();
    if (!token && (typeof DEV_SKIP_AUTH === 'undefined' || !DEV_SKIP_AUTH)) {
        throw new Error('No hay token de autenticación');
    }

    var doRequest = function (t) {
        var headers = { ...(options.headers || {}) };
        if (t) headers['Authorization'] = 'Bearer ' + t;
        // No forzar application/json en POST/PUT sin cuerpo: algunos proxies/Spring pueden rechazar cuerpo vacío con ese Content-Type.
        if (!(options.body instanceof FormData) && !headers['Content-Type']) {
            var b = options.body;
            if (b !== undefined && b !== null && String(b) !== '') {
                headers['Content-Type'] = 'application/json';
            }
        }
        var apiBase = getApiBaseUrl();
        return fetch(`${apiBase}${url}`, { ...options, headers });
    };

    var response = await doRequest(token);

    if (response.status === 401) {
        try {
            await refreshAccessToken();
            token = getToken();
            response = await doRequest(token);
        } catch (e) {
            console.error('❌ [AUTH] Token rechazado y refresh fallido');
            logout('Tu sesión expiró o cerró por seguridad. Inicia sesión de nuevo.');
            throw new Error('Sesión expirada');
        }
    }

    if (response.status === 401) {
        logout('Tu sesión expiró o cerró por seguridad. Inicia sesión de nuevo.');
        throw new Error('Sesión expirada');
    }

    if (response.status === 204) return null;

    if (!response.ok) {
        // Leer el cuerpo una sola vez: Spring suele devolver errores como text/plain, no JSON (p. ej. badRequest().body("...")).
        var rawBody = await response.text().catch(function () { return ''; });
        var trimmed = String(rawBody || '').trim();
        var error = null;
        if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
            try {
                error = JSON.parse(trimmed);
            } catch (parseErr) {
                error = null;
            }
        }
        if (error && typeof error === 'object') {
            var rawMsg = (error.mensaje || error.message || error.error) ? (error.mensaje || error.message || error.error) : '';
            var msg = String(rawMsg || trimmed || 'Error en petición');

            // Mensajes amigables para errores comunes (integridad referencial / constraints)
            var low = msg.toLowerCase();
            var isFkViolation = low.includes('sqlstate: 23503') || low.includes('viola la llave for') || low.includes('foreign key');
            var mentionsGrupoAlumno = low.includes('grupo_alumno');
            if (isFkViolation && mentionsGrupoAlumno) {
                msg = 'No se puede eliminar el alumno porque está inscrito en uno o más grupos. Primero elimínalo del grupo y vuelve a intentarlo.';
            }

            throw new Error(msg);
        }
        if (trimmed) {
            throw new Error(trimmed);
        }
        throw new Error('HTTP ' + response.status);
    }

    contentType = response.headers.get('Content-Type') || '';
    if (contentType.includes('application/json')) return response.json();
    return null;
}

function redirectByUserType(tipoUsuario) {
    if (typeof usuarioEsSoloSinRol === 'function' && usuarioEsSoloSinRol()) {
        redirectToSinRolAsignadoPage();
        return;
    }
    var redirects = {
        'ALUMNO': 'pages/alumno.html',
        'MAESTRO': 'pages/maestro.html',
        'ADMIN': 'pages/dashboard.html',
        'COORDINADOR_ACADEMICO': 'pages/dashboard.html',
        'SECRETARIA_ACADEMICA': 'pages/dashboard.html',
        'SECRETARIA_ADMINISTRATIVA': 'pages/dashboard.html',
        'SIN_ROL': 'pages/dashboard.html'
    };
    var effective = tipoUsuario;
    if (typeof ensureRolActivoInStorage === 'function') ensureRolActivoInStorage();
    if (typeof localStorage !== 'undefined') {
        var ut = localStorage.getItem('userTipo');
        if (ut && redirects[ut]) effective = ut;
    }
    var destination = redirects[effective];
    if (!destination) {
        console.error('❌ Tipo de usuario no reconocido:', effective);
        logout();
        return;
    }
    navigateToAppPage(destination);
}

function protectPage() {
    if (typeof DEV_SKIP_AUTH !== 'undefined' && DEV_SKIP_AUTH) {
        console.warn('⚠️ DEV_SKIP_AUTH activo');
        return;
    }
    if (!isAuthenticated()) {
        console.warn('🔒 Página protegida - redirigiendo a login');
        window.location.href = getLoginPath();
    }
}

function getLoginPath() {
    var path = window.location.pathname || '';
    return path.includes('/pages/') ? '../index.html' : 'index.html';
}
