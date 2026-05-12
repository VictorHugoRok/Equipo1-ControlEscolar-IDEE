// ============================================
// JWT Authentication - Frontend
// ============================================

/**
 * Login con email y contraseña
 * Retorna: { token, email, tipoUsuario, id }
 */
async function login(email, password) {
    try {
        console.log('🔐 [AUTH] Iniciando login para:', email);
        
        const response = await fetch(`${API_URL}/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password })
        });

        if (!response.ok) {
            let errorMessage = 'Credenciales incorrectas';
            try {
                const errorData = await response.json();
                errorMessage = errorData.message || errorData.error || errorMessage;
            } catch (e) {}
            throw new Error(errorMessage);
        }

        const data = await response.json();
        console.log('✅ [AUTH] Login exitoso:', data.email);

        // ⚠️ CRÍTICO: Verificar que el backend envió el token
        if (!data.token) {
            throw new Error('Servidor no devolvió token JWT');
        }

        // Guardar PERMANENTEMENTE en localStorage
        localStorage.setItem('token', data.token);
        localStorage.setItem('userEmail', data.email);
        localStorage.setItem('userTipo', data.tipoUsuario);
        localStorage.setItem('userId', data.id);

        console.log('💾 [AUTH] Token guardado en localStorage');
        return data;

    } catch (error) {
        console.error('❌ [AUTH] Error en login:', error.message);
        throw error;
    }
}

/**
 * Logout: limpia localStorage y redirige a login
 */
function logout() {
    console.warn('👋 [AUTH] Cerrando sesión');
    localStorage.removeItem('token');
    localStorage.removeItem('userEmail');
    localStorage.removeItem('userTipo');
    localStorage.removeItem('userId');
    window.location.href = getLoginPath();
}

/**
 * Obtiene el token desde localStorage
 */
function getToken() {
    return localStorage.getItem('token');
}

/**
 * Decodifica JWT sin validar firma (solo para verificar expiración local)
 * ⚠️ NO usar para seguridad, solo para UX (mostrar si está expirado)
 */
function isValidJwt(token) {
    if (!token) return false;
    
    try {
        const parts = token.split('.');
        if (parts.length !== 3) return false;
        
        const decoded = JSON.parse(atob(parts[1]));
        if (!decoded.exp) return false;
        
        const expiryTime = decoded.exp * 1000;
        const now = Date.now();
        
        if (expiryTime < now) {
            console.warn('⚠️ [AUTH] Token expirado localmente');
            return false;
        }
        
        const secsLeft = Math.floor((expiryTime - now) / 1000);
        console.debug(`[AUTH] Token válido, expira en ${Math.floor(secsLeft / 3600)}h`);
        return true;
        
    } catch (e) {
        console.error('❌ [AUTH] Error validando JWT:', e.message);
        return false;
    }
}

/**
 * Verifica si hay token válido en localStorage
 * ⚠️ NO hace logout automático sin razón
 */
function isAuthenticated() {
    const token = getToken();
    if (!token) {
        console.debug('[AUTH] No hay token');
        return false;
    }
    
    // Solo verificar expiración LOCAL, no hacer logout aquí
    if (!isValidJwt(token)) {
        console.warn('[AUTH] Token expirado localmente');
        return false;
    }
    
    return true;
}

/**
 * Obtiene datos del usuario actual validando con el backend
 * El backend YA validó el JWT en JwtAuthenticationFilter
 * Aquí solo se obtienen los datos del usuario autenticado
 */
async function getCurrentUser() {
    try {
        const token = getToken();
        if (!token) {
            console.error('❌ [AUTH] No hay token para validar');
            throw new Error('No hay token');
        }

        console.debug('[AUTH] Validando sesión con backend...');

        const response = await fetch(`${API_URL}/auth/me`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            }
        });

        // Si backend responde 401, es porque el token es inválido/expirado
        if (response.status === 401) {
            console.error('❌ [AUTH] Backend rechazó token (401) - sesión inválida');
            logout();
            throw new Error('Sesión expirada');
        }

        if (!response.ok) {
            throw new Error(`Error ${response.status}`);
        }

        const userData = await response.json();
        console.log('✅ [AUTH] Sesión validada:', userData.email);
        return userData;

    } catch (error) {
        console.error('❌ [AUTH] Error validando sesión:', error.message);
        // SOLO hacer logout si es 401 (backend lo dice)
        if (error.message.includes('401')) {
            logout();
        }
        throw error;
    }
}

/**
 * Envía peticiones HTTP con JWT automáticamente
 * ÚNICO lugar donde se agrega el Authorization header
 */
async function authFetch(url, options = {}) {
    const token = getToken();
    const headers = { ...options.headers };

    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    } else if (typeof DEV_SKIP_AUTH === 'undefined' || !DEV_SKIP_AUTH) {
        throw new Error('No hay token de autenticación');
    }

    // Content-Type solo si no es FormData
    if (!(options.body instanceof FormData) && !headers['Content-Type']) {
        headers['Content-Type'] = 'application/json';
    }

    const response = await fetch(`${API_URL}${url}`, {
        ...options,
        headers
    });

    // Si backend responde 401, limpiar sesión
    if (response.status === 401) {
        console.error('❌ [AUTH] Token rechazado (401)');
        logout();
        throw new Error('Sesión expirada');
    }

    if (response.status === 204) {
        return null;
    }

    if (!response.ok) {
        const contentType = response.headers.get('Content-Type') || '';
        if (contentType.includes('application/json')) {
            const error = await response.json();
            throw new Error(error.message || 'Error en petición');
        }
        throw new Error(`HTTP ${response.status}`);
    }

    const contentType = response.headers.get('Content-Type') || '';
    if (contentType.includes('application/json')) {
        return response.json();
    }

    return null;
}

/**
 * Redirige al usuario según su tipo
 */
function redirectByUserType(tipoUsuario) {
    const redirects = {
        'ALUMNO': 'pages/alumno.html',
        'MAESTRO': 'pages/maestro.html',
        'ADMIN': 'pages/dashboard.html',
        'SECRETARIA_ACADEMICA': 'pages/dashboard.html',
        'SECRETARIO_ADMINISTRATIVO': 'pages/dashboard.html'
    };

    const destination = redirects[tipoUsuario];
    if (destination) {
        window.location.href = destination;
    } else {
        console.error('❌ Tipo de usuario no reconocido:', tipoUsuario);
        logout();
    }
}

/**
 * Protege página: redirige a login si no hay sesión
 */
function protectPage() {
    if (typeof DEV_SKIP_AUTH !== 'undefined' && DEV_SKIP_AUTH) {
        console.warn('⚠️ DEV_SKIP_AUTH activo');
        return;
    }

    if (!isAuthenticated()) {
        console.warn('🔒 Página protegida - sin autenticación, redirigiendo a login');
        window.location.href = getLoginPath();
    }
}

/**
 * Obtiene la ruta correcta al login según ubicación
 */
function getLoginPath() {
    const path = window.location.pathname || '';
    return path.includes('/pages/') ? '../index.html' : 'index.html';
}
