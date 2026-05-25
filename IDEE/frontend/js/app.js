// Funciones auxiliares para todas las páginas

/**
 * Evita recargas accidentales por submit implícito:
 * - Botones dentro de <form> sin type => type="button"
 * - Forms sin action real (vacío, "#", "javascript:...") no navegan por submit
 */
function setupPreventUnexpectedReloads() {
    if (window.__preventUnexpectedReloadsReady) return;
    window.__preventUnexpectedReloadsReady = true;

    // 1) Normalizar botones sin type dentro de formularios.
    document.querySelectorAll('form button:not([type])').forEach(function (btn) {
        btn.setAttribute('type', 'button');
    });

    // 2) Capturar submit y bloquear navegación no intencional.
    document.addEventListener('submit', function (event) {
        var form = event.target;
        if (!form || form.tagName !== 'FORM') return;
        if (form.dataset && form.dataset.allowNativeSubmit === 'true') return;

        var actionAttr = (form.getAttribute('action') || '').trim().toLowerCase();
        var shouldBlock = !actionAttr || actionAttr === '#' || actionAttr.indexOf('javascript:') === 0;
        if (shouldBlock) {
            // Solo prevenir la navegación por defecto; no usar stopPropagation, porque este listener
            // va en fase de captura y cortaría los manejadores del formulario en fase burbuja (p. ej. guardar grupo).
            event.preventDefault();
        }
    }, true);
}

// Validar sesión inmediatamente al cargar la página (NUEVA)
async function validateSessionOnPageLoad() {
    console.log('🔐 Validando sesión al cargar página...');
    
    // Verificar que hay token y no está expirado
    if (!isAuthenticated()) {
        console.log('❌ Sesión inválida o expirada');
        window.location.href = getLoginPath();
        return null;
    }
    
    try {
        // Validar token con el backend
        console.log('✅ Token válido en cliente, verificando con backend...');
        const userData = await getCurrentUser();
        console.log('✅ Sesión validada exitosamente:', userData.email);
        return userData;
    } catch (error) {
        console.error('❌ Error validando sesión con backend:', error);
        // ✅ SOLO logout si es realmente 401 (token inválido)
        if (error.message && error.message.includes('401')) {
            console.warn('⚠️ Token rechazado por backend (401) - cerrando sesión');
            logout();
            window.location.href = getLoginPath();
        } else {
            // ⚠️ Error de red o timeout - NO hacer logout
            console.warn('⚠️ Error de red/timeout - sesión se mantiene activa:', error.message);
            throw error;
        }
        return null;
    }
}

// Cargar información del usuario actual
async function loadCurrentUserInfo() {
    try {
        // ✅ Si llegamos aquí, protectPage() YA validó existencia de token
        // protectPage() hubiera redirigido a login si no hay autenticación
        // No necesitamos validar de nuevo con isAuthenticated()
        
        console.log('✅ Token válido en cliente, obteniendo datos de usuario...');
        const userData = await getCurrentUser();
        
        console.log('✅ Datos de usuario cargados exitosamente:', userData.email);
        return userData;
    } catch (error) {
        console.error('❌ Error al cargar usuario:', error);
        
        // IMPORTANTE: Solo hacer logout si el error es 401 (token rechazado por backend)
        // No hacer logout en caso de timeout o error de red
        if (error.message && error.message.includes('401')) {
            console.warn('⚠️ Token rechazado por backend (401) - cerrando sesión');
            logout();
        } else {
            console.warn('⚠️ Error de red o timeout - mantener sesión activa', error);
        }
        
        return null;
    }
}

// Actualizar el email mostrado en el navbar
function updateNavbarEmail(email) {
    const emailElements = document.querySelectorAll('.navbar .text-light');
    emailElements.forEach(el => {
        if (el.textContent.includes('@')) {
            el.textContent = email;
        }
    });
}

// Actualizar nombre de usuario en el navbar
function updateNavbarUserName(nombre) {
    const userNameElements = document.querySelectorAll('.user-name');
    userNameElements.forEach(el => {
        el.textContent = nombre;
    });
}

// Manejar cerrar sesión - modal de confirmación para todos los roles (Admin, Secretaria Académica, Maestro, Alumno)
function setupLogoutButtons() {
    const seen = new Set();
    document.querySelectorAll('#btnLogout, a[href*="index.html"]').forEach(btn => {
        if (seen.has(btn)) return;
        const text = (btn.textContent || '').trim();
        const isLogout = btn.id === 'btnLogout' || text.includes('Cerrar sesión') || text.includes('Salir');
        if (isLogout && !btn.dataset.logoutSetup) {
            seen.add(btn);
            btn.dataset.logoutSetup = '1';
            btn.addEventListener('click', (e) => {
                e.preventDefault();
                e.stopPropagation();
                if (typeof showLogoutConfirmModal === 'function' && typeof logout === 'function') {
                    showLogoutConfirmModal(() => logout());
                } else if (typeof logout === 'function') {
                    if (confirm('¿Estás seguro de que deseas cerrar sesión?')) logout();
                }
            });
        }
    });
}

// Mostrar modal personalizado para cerrar sesion
function showLogoutConfirmModal(onConfirm) {
    const modalElement = getLogoutConfirmModalElement();
    if (!modalElement || typeof bootstrap === 'undefined') {
        if (confirm('¿Estás seguro de que deseas cerrar sesión?')) {
            onConfirm();
        }
        return;
    }

    const confirmBtn = modalElement.querySelector('#logoutConfirmButton');
    if (confirmBtn) {
        confirmBtn.onclick = () => {
            const modalInstance = bootstrap.Modal.getInstance(modalElement);
            if (modalInstance) {
                modalInstance.hide();
            }
            onConfirm();
        };
    }

    const modal = bootstrap.Modal.getOrCreateInstance(modalElement);
    modal.show();
}

function getLogoutConfirmModalElement() {
    let modal = document.getElementById('logoutConfirmModal');
    if (modal) {
        return modal;
    }

    modal = document.createElement('div');
    modal.id = 'logoutConfirmModal';
    modal.className = 'modal fade';
    modal.tabIndex = -1;
    modal.setAttribute('aria-hidden', 'true');
    modal.innerHTML = `
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content shadow-lg border-0 overflow-hidden" style="border-radius: 1rem;">
                <div class="modal-header border-0 pb-0" style="background: linear-gradient(135deg, #2a5a8f 0%, #184170 100%); color: #fff;">
                    <div class="d-flex align-items-center w-100">
                        <div class="rounded-circle d-flex align-items-center justify-content-center me-3" style="width: 48px; height: 48px; background: rgba(255,255,255,0.2);">
                            <i class="bi bi-box-arrow-right" style="font-size: 1.5rem;"></i>
                        </div>
                        <div>
                            <h5 class="modal-title mb-0 fw-semibold">Cerrar sesión</h5>
                            <small class="opacity-75">Confirmar salida del sistema</small>
                        </div>
                    </div>
                    <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Cerrar"></button>
                </div>
                <div class="modal-body py-4 text-dark">
                    <p class="mb-0">¿Estás seguro de que deseas cerrar sesión? Tendrás que volver a iniciar sesión para acceder al sistema.</p>
                </div>
                <div class="modal-footer border-0 pt-0 bg-light">
                    <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Cancelar</button>
                    <button type="button" class="btn btn-ide px-4" id="logoutConfirmButton">
                        <i class="bi bi-box-arrow-right me-2"></i>Cerrar sesión
                    </button>
                </div>
            </div>
        </div>
    `;

    document.body.appendChild(modal);
    return modal;
}

// Formatear fecha
function formatDate(dateString) {
    if (!dateString) return 'N/A';
    const date = new Date(dateString);
    return date.toLocaleDateString('es-MX', {
        year: 'numeric',
        month: 'long',
        day: 'numeric'
    });
}

// Formatear fecha corta
function formatDateShort(dateString) {
    if (!dateString) return 'N/A';
    const date = new Date(dateString);
    return date.toLocaleDateString('es-MX', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit'
    });
}

// Mostrar mensaje de error
function showError(message) {
    if (typeof window.showSystemToast === 'function') {
        window.showSystemToast(String(message || 'Error'), { type: 'error', durationMs: 5200 });
        return;
    }
    alert(`Error: ${message}`);
}

// Mostrar mensaje de éxito
function showSuccess(message) {
    if (typeof window.showSystemToast === 'function') {
        window.showSystemToast(String(message || 'OK'), { type: 'success' });
        return;
    }
    alert(`✓ ${message}`);
}

// Mostrar loading
function showLoading(element, show = true) {
    if (show) {
        element.innerHTML = '<div class="text-center"><div class="spinner-border" role="status"><span class="visually-hidden">Cargando...</span></div></div>';
    }
}

// Crear tabla vacía con mensaje
function createEmptyTableMessage(message = 'No hay datos disponibles') {
    return `
        <tr>
            <td colspan="100%" class="text-center text-muted py-4">
                ${message}
            </td>
        </tr>
    `;
}

// Manejar errores de red
function handleNetworkError(error) {
    console.error('Error de red:', error);
    if (error.message.includes('fetch')) {
        showError('No se pudo conectar con el servidor. Verifica que el backend esté corriendo.');
    } else if (error.message.includes('401')) {
        showError('Sesión expirada. Por favor, inicia sesión nuevamente.');
        logout();
    } else {
        showError(error.message || 'Ocurrió un error al procesar la solicitud.');
    }
}

/**
 * Notificaciones del sistema (toast tipo donut) y confirmaciones por modal.
 * Objetivo: evitar diálogos nativos del navegador (alert/confirm) en todo el sistema.
 */
(function initUiNotifications() {
    if (window.__uiNotificationsReady) return;
    window.__uiNotificationsReady = true;

    // ---- Toast (si no existe showSystemToast, crear uno equivalente a ui-toast.js)
    if (typeof window.showSystemToast !== 'function') {
        function ensureContainer() {
            var id = 'systemToastContainer';
            var el = document.getElementById(id);
            if (el) return el;
            el = document.createElement('div');
            el.id = id;
            el.className = 'system-toast-container';
            document.body.appendChild(el);
            return el;
        }
        function iconFor(type) {
            if (type === 'success') return 'bi-check-circle';
            if (type === 'error' || type === 'danger') return 'bi-exclamation-triangle';
            if (type === 'warning') return 'bi-exclamation-circle';
            return 'bi-info-circle';
        }
        function normalizeType(type) {
            if (!type) return 'info';
            var t = String(type).toLowerCase();
            if (t === 'danger') t = 'error';
            if (!['success', 'error', 'warning', 'info'].includes(t)) t = 'info';
            return t;
        }
        window.showSystemToast = function showSystemToast(message, opts) {
            opts = opts || {};
            var type = normalizeType(opts.type);
            var durationMs = typeof opts.durationMs === 'number' ? opts.durationMs : 4200;
            var container = ensureContainer();
            var toast = document.createElement('div');
            toast.className = 'system-toast system-toast--' + type;
            toast.setAttribute('role', 'status');
            toast.setAttribute('aria-live', 'polite');
            var msg = document.createElement('div');
            msg.className = 'system-toast__msg';
            msg.innerHTML =
                '<i class="bi ' + iconFor(type) + ' system-toast__icon" aria-hidden="true"></i>' +
                '<span class="system-toast__text"></span>';
            var textEl = msg.querySelector('.system-toast__text');
            if (textEl) textEl.textContent = String(message || '');
            var btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'system-toast__close';
            btn.setAttribute('aria-label', 'Cerrar notificación');
            btn.innerHTML = '&times;';
            var closed = false;
            function close() {
                if (closed) return;
                closed = true;
                toast.classList.add('is-hiding');
                window.setTimeout(function () { try { toast.remove(); } catch (_) { } }, 180);
            }
            btn.addEventListener('click', close);
            toast.addEventListener('click', function (e) {
                if (e && e.target && e.target.tagName && String(e.target.tagName).toLowerCase() === 'a') return;
                if (e && e.target === btn) return;
                close();
            });
            toast.appendChild(msg);
            toast.appendChild(btn);
            container.appendChild(toast);
            // Detectar si el texto ocupa múltiples líneas (para alinear y ajustar borde)
            try {
                window.requestAnimationFrame(function () {
                    try {
                        if (!textEl) return;
                        // Si el texto se envuelve, produce más de 1 rectángulo de línea.
                        var rects = textEl.getClientRects ? textEl.getClientRects() : null;
                        var multi = rects && rects.length > 1;
                        if (multi) toast.classList.add('is-multiline');
                        else toast.classList.remove('is-multiline');
                    } catch (_) { }
                });
            } catch (_) {}
            window.setTimeout(function () { toast.classList.add('is-show'); }, 10);
            if (durationMs > 0) window.setTimeout(close, durationMs);
        };
    }

    // ---- Confirm modal (async)
    function getConfirmModalElement() {
        var modal = document.getElementById('systemConfirmModal');
        if (modal) return modal;
        modal = document.createElement('div');
        modal.id = 'systemConfirmModal';
        modal.className = 'modal fade';
        modal.tabIndex = -1;
        modal.setAttribute('aria-hidden', 'true');
        modal.innerHTML = `
            <div class="modal-dialog modal-dialog-centered">
              <div class="modal-content shadow-lg border-0 overflow-hidden" style="border-radius: 1rem;">
                <div class="modal-header border-0 pb-0" style="background: linear-gradient(135deg, #2a5a8f 0%, #184170 100%); color: #fff;">
                  <div class="d-flex align-items-center w-100">
                    <div class="rounded-circle d-flex align-items-center justify-content-center me-3" style="width: 44px; height: 44px; background: rgba(255,255,255,0.2);">
                      <i class="bi bi-question-circle" style="font-size: 1.35rem;"></i>
                    </div>
                    <div>
                      <h6 class="modal-title mb-0 fw-semibold" id="systemConfirmModalTitle">Confirmar</h6>
                      <small class="opacity-75" id="systemConfirmModalSubtitle">Acción requerida</small>
                    </div>
                  </div>
                  <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Cerrar"></button>
                </div>
                <div class="modal-body py-4 text-dark">
                  <p class="mb-0" id="systemConfirmModalMessage"></p>
                </div>
                <div class="modal-footer border-0 pt-0 bg-light">
                  <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal" id="systemConfirmCancelBtn">Cancelar</button>
                  <button type="button" class="btn btn-ide px-4" id="systemConfirmOkBtn">Confirmar</button>
                </div>
              </div>
            </div>
        `;
        document.body.appendChild(modal);
        return modal;
    }

    window.uiConfirm = function uiConfirm(message, opts) {
        opts = opts || {};
        return new Promise(function (resolve) {
            var modalEl = getConfirmModalElement();
            if (!modalEl || typeof bootstrap === 'undefined' || !bootstrap.Modal) {
                // Último recurso (idealmente nunca): no usar confirm nativo.
                resolve(false);
                if (typeof window.showSystemToast === 'function') {
                    window.showSystemToast(String(message || 'Confirmación requerida'), { type: 'warning', durationMs: 5200 });
                }
                return;
            }
            var titleEl = modalEl.querySelector('#systemConfirmModalTitle');
            var subEl = modalEl.querySelector('#systemConfirmModalSubtitle');
            var msgEl = modalEl.querySelector('#systemConfirmModalMessage');
            var okBtn = modalEl.querySelector('#systemConfirmOkBtn');
            var cancelBtn = modalEl.querySelector('#systemConfirmCancelBtn');
            if (titleEl) titleEl.textContent = opts.title || 'Confirmar';
            if (subEl) subEl.textContent = opts.subtitle || 'Acción requerida';
            if (msgEl) msgEl.textContent = String(message || '');
            if (okBtn) okBtn.textContent = opts.okText || 'Confirmar';
            if (cancelBtn) cancelBtn.textContent = opts.cancelText || 'Cancelar';

            var done = false;
            function finish(val) {
                if (done) return;
                done = true;
                resolve(!!val);
            }
            function onHidden() {
                modalEl.removeEventListener('hidden.bs.modal', onHidden);
                if (!done) finish(false);
            }
            modalEl.addEventListener('hidden.bs.modal', onHidden);

            if (okBtn) okBtn.onclick = function () {
                var inst = bootstrap.Modal.getInstance(modalEl);
                if (inst) inst.hide();
                finish(true);
            };
            if (cancelBtn) cancelBtn.onclick = function () {
                var inst = bootstrap.Modal.getInstance(modalEl);
                if (inst) inst.hide();
                finish(false);
            };

            bootstrap.Modal.getOrCreateInstance(modalEl).show();
        });
    };

    // ---- Reemplazo de alert nativo
    if (!window.__nativeAlert) window.__nativeAlert = window.alert;
    window.alert = function (message) {
        if (typeof window.showSystemToast === 'function') {
            window.showSystemToast(String(message || ''), { type: 'info' });
            return;
        }
        // Fallback (idealmente nunca)
        try { window.__nativeAlert(message); } catch (_) { }
    };
})();

// Validar que el usuario tenga un rol específico
function validateUserRole(requiredRole) {
    // En modo desarrollo, no forzamos la existencia de userTipo
    if (typeof DEV_SKIP_AUTH !== 'undefined' && DEV_SKIP_AUTH) {
        console.warn('⚠️ DEV_SKIP_AUTH activo: omitiendo validación de rol (modo desarrollo)');
        return true;
    }

    const userTipo = localStorage.getItem('userTipo');
    if (!userTipo) {
        // ⚠️ NO hacer logout inmediatamente
        // El usuario podría estar logueado pero localStorage fue limpiado por otra razón
        // Mejor: Solo retornar false para bloquear acceso, pero no logout
        console.warn('⚠️ [validateUserRole] userTipo no encontrado en localStorage');
        console.warn('⚠️ [validateUserRole] Token podría ser válido, intentar obtener rol del backend');
        return false;
    }

    var active = null;
    if (typeof obtenerRolActivoParaUi === 'function') {
        active = obtenerRolActivoParaUi();
    }
    if (!active) {
        active = userTipo;
    }

    if (Array.isArray(requiredRole)) {
        return requiredRole.some(function (r) { return r === active; });
    }

    return requiredRole === active;
}

// Inicialización común para todas las páginas
async function initializePage(requiredRole) {
    // PASO 1: Proteger la página (verificar que hay token válido)
    protectPage();

    if (!(typeof DEV_SKIP_AUTH !== 'undefined' && DEV_SKIP_AUTH)) {
        if (typeof usuarioEsSoloSinRol === 'function' && usuarioEsSoloSinRol()) {
            if (typeof redirectToSinRolAsignadoPage === 'function') redirectToSinRolAsignadoPage();
            return null;
        }
    }

    // PASO 2: Token presente
    if (!isAuthenticated()) {
        console.error('❌ [initializePage] Sesión inválida - token expirado o no disponible');
        logout();
        return null;
    }

    // PASO 3: Sincronizar roles con /auth/me antes de validar el rol de la página (evita datos viejos en localStorage)
    console.log('ℹ️ [initializePage] Cargando datos del usuario...');
    const userData = await loadCurrentUserInfo();

    // PASO 4: Validar rol activo (si se especifica), ya con lista de roles alineada al servidor
    if (requiredRole && !validateUserRole(requiredRole)) {
        alert('No tienes permisos para acceder a esta página');
        logout();
        return null;
    }

    // PASO 5: Configurar botones de logout
    setupLogoutButtons();

    if (userData) {
        updateNavbarEmail(userData.email);
        console.log('✅ [initializePage] Datos de usuario cargados exitosamente');
    } else {
        console.warn('⚠️ [initializePage] No se pudieron cargar datos del usuario');
        // Si no podemos cargar los datos pero la sesión es válida, continuamos
        // La sesión se mantiene activa
    }

    // PASO 6: Exponer el usuario actual globalmente en window.currentUser
    // Normalizar la propiedad de rol para compatibilidad con gestion-permisos.js y sistema-roles-unified.js
    if (userData) {
        var rolesArr = userData.roles;
        if (!rolesArr || !rolesArr.length) {
            rolesArr = userData.tipoUsuario ? [userData.tipoUsuario] : [];
        }
        if (typeof ensureRolActivoInStorage === 'function') {
            ensureRolActivoInStorage();
        }
        var rolUi = (typeof obtenerRolActivoParaUi === 'function') ? obtenerRolActivoParaUi() : null;
        window.currentUser = Object.assign({}, userData, {
            rol: rolUi || userData.rol || userData.tipoUsuario || userData.tipo || localStorage.getItem('userTipo'),
            roles: rolesArr
        });
    } else {
        // Aunque no tengamos datos de backend, creamos el objeto con info local
        const userEmail = localStorage.getItem('userEmail');
        const userTipo = localStorage.getItem('userTipo');
        if (userEmail && userTipo) {
            window.currentUser = {
                email: userEmail,
                tipoUsuario: userTipo,
                rol: userTipo
            };
            console.log('⚠️ [initializePage] Usando datos de sesión local (backend no disponible)');
        }
    }

    return userData || window.currentUser;
}

/**
 * Redireccionar al dashboard correcto según el rol
 * Ambos roles (ADMIN y SECRETARIA_ACADEMICA) van a la misma página dashboard.html
 */
function redirectToDashboard() {
    const userRole = localStorage.getItem('userTipo') || 
                     (window.currentUser && window.currentUser.rol);
    
    if (userRole === 'ADMIN' || userRole === 'SECRETARIA_ACADEMICA') {
        window.location.href = '/pages/dashboard.html';
    } else {
        console.error('Rol no soportado para dashboard:', userRole);
        logout();
    }
}

document.addEventListener('DOMContentLoaded', function () {
    setupPreventUnexpectedReloads();
});

// El sidebar se inyecta asíncrono (load-sidebar.js); volver a enlazar cerrar sesión cuando exista #btnLogout
document.addEventListener('sidebarLoaded', function () {
    if (typeof setupLogoutButtons === 'function') {
        setupLogoutButtons();
    }
});

// Convertir objetos a opciones de select
function populateSelect(selectElement, options, valueKey = 'id', textKey = 'nombre') {
    selectElement.innerHTML = '<option value="">Seleccionar...</option>';
    options.forEach(option => {
        const optionElement = document.createElement('option');
        optionElement.value = option[valueKey];
        optionElement.textContent = option[textKey];
        selectElement.appendChild(optionElement);
    });
}
