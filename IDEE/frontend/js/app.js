// Funciones auxiliares para todas las páginas

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

// Manejar cerrar sesión
function setupLogoutButtons() {
    const logoutButtons = document.querySelectorAll('a[href*="index.html"]');
    logoutButtons.forEach(btn => {
        if (btn.textContent.includes('Cerrar sesión') || btn.textContent.includes('Salir')) {
            btn.addEventListener('click', (e) => {
                e.preventDefault();
                showLogoutConfirmModal(() => logout());
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
            <div class="modal-content">
                <div class="modal-header bg-soft-primary">
                    <h5 class="modal-title text-dark">Confirmar cierre de sesión</h5>
                    <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Cerrar"></button>
                </div>
                <div class="modal-body text-dark">
                    ¿Estás seguro de que deseas cerrar sesión?
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Cancelar</button>
                    <button type="button" class="btn btn-ide" id="logoutConfirmButton">Cerrar sesión</button>
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
    alert(`Error: ${message}`);
}

// Mostrar mensaje de éxito
function showSuccess(message) {
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

    if (Array.isArray(requiredRole)) {
        return requiredRole.includes(userTipo);
    }

    return userTipo === requiredRole;
}

// Inicialización común para todas las páginas
async function initializePage(requiredRole) {
    // PASO 1: Proteger la página (verificar que hay token válido)
    protectPage();

    // PASO 2: Validar rol (si se especifica)
    if (requiredRole && !validateUserRole(requiredRole)) {
        alert('No tienes permisos para acceder a esta página');
        logout();
        return null;
    }

    // PASO 3: Configurar botones de logout
    setupLogoutButtons();

    // PASO 4: Verificar que la sesión sigue siendo válida ANTES de cargar datos
    // Esto es importante para evitar que timeouts del backend cierren la sesión
    if (!isAuthenticated()) {
        console.error('❌ [initializePage] Sesión inválida - token expirado o no disponible');
        logout();
        return null;
    }

    // PASO 5: Cargar datos del usuario
    console.log('ℹ️ [initializePage] Cargando datos del usuario...');
    const userData = await loadCurrentUserInfo();

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
        window.currentUser = Object.assign({}, userData, {
            rol: userData.rol || userData.tipoUsuario || userData.tipo || localStorage.getItem('userTipo')
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
