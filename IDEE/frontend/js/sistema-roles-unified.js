/**
 * SISTEMA UNIFICADO DE ROLES Y PERMISOS
 * =====================================
 * 
 * ⚠️ IMPORTANTE: Este archivo DEBE cargarse DESPUÉS de roles.js
 * 
 * Este script gestiona los permisos y visibilidad de elementos según el rol del usuario.
 * Conecta admin y secretaria académica en una única página con lógica condicional.
 * 
 * Las definiciones de ROLES y PERMISOS_POR_ROL están en roles.js (única fuente de verdad)
 */

// ✅ Verificar que roles.js se cargó primero
if (typeof ROLES === 'undefined' || typeof PERMISOS_POR_ROL === 'undefined') {
    console.error('❌ [SISTEMA-ROLES] Error: roles.js no está cargado. Asegúrate de cargar roles.js ANTES de sistema-roles-unified.js');
    console.error('Orden correcto en HTML: config.js → roles.js → auth.js → sistema-roles-unified.js');
}

/**
 * Obtener el rol actual del usuario desde la sesión
 * 
 * ⚠️ CRÍTICO: El backend devuelve 'tipoUsuario', no 'rol'
 * localStorage también usa 'userTipo'
 * 
 * Precedencia:
 * 1. window.currentUser.tipoUsuario (después de autenticar)
 * 2. localStorage.userTipo (en reload)
 * 3. null (no autenticado)
 */
function obtenerRolActual() {
    // 1️⃣ Intentar desde window.currentUser (después de login)
    if (window.currentUser && window.currentUser.tipoUsuario) {
        console.log('[ROLES] Rol desde window.currentUser:', window.currentUser.tipoUsuario);
        return window.currentUser.tipoUsuario;
    }

    // 2️⃣ Fallback: desde localStorage (en reload de página)
    const rolDesdeStorage = localStorage.getItem('userTipo');
    if (rolDesdeStorage) {
        console.log('[ROLES] Rol desde localStorage:', rolDesdeStorage);
        return rolDesdeStorage;
    }

    // 3️⃣ No autenticado
    console.warn('[ROLES] No se encontró rol (usuario no autenticado)');
    return null;
}

/**
 * Obtener permisos del rol actual
 */
function obtenerPermisosActuales() {
    const rol = obtenerRolActual();
    return PERMISOS_POR_ROL[rol] || null;
}

/**
 * Aplicar permisos según el rol del usuario
 * 
 * ⚠️ IMPORTANTE: NO hacer logout por errores de JavaScript
 * El logout SOLO debe ocurrir si el backend responde 401
 */
function aplicarPermisosSegunRol() {
    const rol = obtenerRolActual();

    // ❌ Si no hay rol, no es un error fatal
    if (!rol) {
        console.warn('⚠️ [ROLES] No hay rol disponible aún (usuario puede no estar autenticado)');
        console.warn('Esto es normal si se llama antes de que getCurrentUser() termine');
        return;
    }

    // ✅ Verificar que el rol es válido
    if (!esRolValido(rol)) {
        console.error('❌ [ROLES] Rol no reconocido:', rol);
        console.error('Roles válidos:', Object.keys(PERMISOS_POR_ROL));
        
        // ⚠️ NO hacer logout aquí - podría ser un error temporal
        // El logout SOLO debe ocurrir por respuesta 401 del backend
        console.warn('⚠️ No se aplicarán permisos, pero NO cerrando sesión');
        return;
    }

    const permisos = PERMISOS_POR_ROL[rol];
    console.log(`✅ [ROLES] Aplicando permisos para rol: ${rol}`);

    // 1. Actualizar navbar con nombre del rol
    actualizarNavbar(permisos);

    // 2. Controlar visibilidad de secciones
    controlarVisibilidadSecciones(permisos);

    // 3. Controlar interfaz de calificaciones según permisos
    controlarInterfazCalificaciones(permisos);

    // 4. Controlar menú de Títulos y Certificados
    controlarMenuTitulosCertificados(permisos);

    // 5. Deshabilitar/habilitar elementos interactivos
    controlarElementosInteractivos(permisos);
}

/**
 * Actualizar navbar con información del rol
 */
function actualizarNavbar(permisos) {
    // Actualizar nombre del módulo
    const navbarBrand = document.getElementById('navbarBrand');
    if (navbarBrand) {
        navbarBrand.textContent = `IDEE • ${permisos.rolDisplay}`;
    }

    // Actualizar badge de rol
    const navbarRole = document.getElementById('navbarRole');
    if (navbarRole) {
        navbarRole.textContent = permisos.rolBadge;
    }
}

/**
 * Controlar la visibilidad de secciones según rol
 */
function controlarVisibilidadSecciones(permisos) {
    // Ocultar todas las secciones primero
    const todasLasSecciones = document.querySelectorAll('.admin-section');
    todasLasSecciones.forEach(seccion => {
        seccion.classList.add('d-none');
    });

    // Mostrar solo las permitidas para este rol
    permisos.secciones.forEach(sectionId => {
        const seccion = document.getElementById(sectionId);
        if (seccion) {
            seccion.classList.remove('d-none');
        }
    });

    // Mostrar la primera sección por defecto
    if (permisos.secciones.length > 0) {
        const primeraSectionId = permisos.secciones[0];
        const primeraSect = document.getElementById(primeraSectionId);
        if (primeraSect) {
            primeraSect.classList.remove('d-none');
        }
    }
}

/**
 * Controlar la interfaz de calificaciones según permisos
 */
function controlarInterfazCalificaciones(permisos) {
    const calificacionesSection = document.getElementById('calificacionesSection');
    if (!calificacionesSection) return;

    // Actualizar descripción
    const descElement = document.getElementById('calificacionesDesc');
    if (descElement) {
        if (permisos.puedeEditarCalificaciones) {
            descElement.textContent = 'El maestro captura las calificaciones con sus criterios. Aquí puede visualizar y editar calificaciones.';
        } else {
            descElement.textContent = 'El maestro captura las calificaciones con sus criterios. Aquí solo puede visualizar.';
        }
    }

    // Controlar campos de edición
    const calificacionesTable = calificacionesSection.querySelector('table');
    if (calificacionesTable) {
        const inputsCalificaciones = calificacionesTable.querySelectorAll('input[type="number"]');
        inputsCalificaciones.forEach(input => {
            input.disabled = !permisos.puedeEditarCalificaciones;
        });
    }

    // Mostrar/ocultar columna de acciones (Confirmar)
    const accionHeader = document.getElementById('calificacionesActionHeader');
    const accionCell = document.getElementById('calificacionesActionCell');
    
    if (permisos.puedeConfirmarCalificaciones) {
        if (accionHeader) accionHeader.classList.remove('d-none');
        if (accionCell) accionCell.classList.remove('d-none');
        
        // Actualizar footer
        const footer = document.getElementById('calificacionesFooter');
        if (footer) {
            footer.textContent = 'Nota: Una vez confirmado aquí, el maestro ya no puede modificar la calificación.';
        }
    } else {
        if (accionHeader) accionHeader.classList.add('d-none');
        if (accionCell) accionCell.classList.add('d-none');
        
        // Actualizar footer
        const footer = document.getElementById('calificacionesFooter');
        if (footer) {
            footer.textContent = 'Nota: Solo puede visualizar las calificaciones.';
        }
    }
}

/**
 * Controlar visibilidad del menú de Títulos y Certificados
 */
function controlarMenuTitulosCertificados(permisos) {
    const menuCertificados = document.getElementById('menuCertificados');
    const menuConstancias = document.getElementById('menuConstancias');
    const menuConfigSep = document.getElementById('menuConfigSep');
    const menuTitulos = document.getElementById('menuTitulos');
    const dividerSep = document.getElementById('dividerSep');

    // Mostrar/ocultar Certificados
    if (menuCertificados) {
        if (permisos.puedeVerCertificados) {
            menuCertificados.classList.remove('d-none');
        } else {
            menuCertificados.classList.add('d-none');
        }
    }

    // Mostrar/ocultar Constancias
    if (menuConstancias) {
        if (permisos.puedeVerConstancias) {
            menuConstancias.classList.remove('d-none');
        } else {
            menuConstancias.classList.add('d-none');
        }
    }

    // Mostrar/ocultar divider y opciones SEP
    const mostrarSep = permisos.puedeVerConfiguracionSep || permisos.puedeVerTitulos;
    
    if (dividerSep) {
        if (mostrarSep && (permisos.puedeVerCertificados || permisos.puedeVerConstancias)) {
            dividerSep.classList.remove('d-none');
        } else {
            dividerSep.classList.add('d-none');
        }
    }

    if (menuConfigSep) {
        if (permisos.puedeVerConfiguracionSep) {
            menuConfigSep.classList.remove('d-none');
        } else {
            menuConfigSep.classList.add('d-none');
        }
    }

    if (menuTitulos) {
        if (permisos.puedeVerTitulos) {
            menuTitulos.classList.remove('d-none');
        } else {
            menuTitulos.classList.add('d-none');
        }
    }
}

/**
 * Controlar elementos interactivos según permisos
 */
function controlarElementosInteractivos(permisos) {
    // Deshabilitar elementos de edición en calificaciones si no tiene permiso
    if (!permisos.puedeEditarCalificaciones) {
        // Buscar y deshabilitar inputs de calificación
        const calificacionesSection = document.getElementById('calificacionesSection');
        if (calificacionesSection) {
            const inputs = calificacionesSection.querySelectorAll('input[type="number"], textarea, button.btn-ide, button.btn-outline-secondary');
            inputs.forEach(input => {
                if (input.classList.contains('form-control')) {
                    input.disabled = true;
                }
            });
        }
    }
}

/**
 * Configurar navegación entre secciones (compatible con ambos roles)
 */
function configurarNavegacionAdmin() {
    const adminNavLinks = document.querySelectorAll("[data-admin-section]");
    
    adminNavLinks.forEach((link) => {
        link.addEventListener("click", function (e) {
            e.preventDefault();
            
            const sectionId = this.getAttribute("data-admin-section");
            const permisos = obtenerPermisosActuales();
            
            // Verificar si el usuario tiene acceso a esta sección
            if (!permisos.secciones.includes(sectionId)) {
                alert('No tienes permiso para acceder a esta sección.');
                return;
            }
            
            activarAdminSection(sectionId);

            // Actualizar navegación activa
            document.querySelectorAll("#adminNavbar .nav-link").forEach((nav) => {
                nav.classList.remove("active");
            });
            
            if (this.classList.contains("dropdown-item")) {
                const parentLink = this.closest(".dropdown").querySelector(".nav-link");
                if (parentLink) {
                    parentLink.classList.add("active");
                }
            } else {
                this.classList.add("active");
            }
        });
    });
}

/**
 * Activar una sección específica
 */
function activarAdminSection(sectionId) {
    document.querySelectorAll(".admin-section").forEach((sec) => {
        sec.classList.add("d-none");
    });
    
    const section = document.getElementById(sectionId);
    if (section) {
        section.classList.remove("d-none");
    }
}

/**
 * Inicializar el sistema de roles al cargar la página
 */
async function inicializarSistemaRoles() {
    try {
        // Esperar a que la página esté cargada
        if (document.readyState !== 'loading') {
            // DOM ya está cargado
            procederConInicializacion();
        } else {
            // Esperar al evento DOMContentLoaded
            document.addEventListener('DOMContentLoaded', procederConInicializacion);
        }
    } catch (error) {
        console.error('Error al inicializar sistema de roles:', error);
    }
}

/**
 * Proceder con la inicialización
 */
function procederConInicializacion() {
    // Configurar navegación
    configurarNavegacionAdmin();

    // Aplicar permisos según rol (esto ocurrirá después de que initializePage() establezca currentUser)
    // Si ya hay un usuario cargado, aplicar permisos inmediatamente
    if (window.currentUser) {
        aplicarPermisosSegunRol();
    }
}

/**
 * Función para redirigir según el rol (se llama desde app.js después de autenticar)
 * Retorna la página a la que debe ir el usuario
 */
function obtenerPaginaSegunRol(rol) {
    // Ambos roles usan la misma página: dashboard.html
    return '/pages/dashboard.html';
}

/**
 * Validar acceso a la página según el rol
 * Se ejecuta al cargar dashboard.html
 */
async function validarAccesoADashboard() {
    try {
        // Verificar autenticación
        const user = window.currentUser;
        
        if (!user) {
            console.error('Usuario no autenticado');
            window.location.href = '../index.html';
            return;
        }

        // Verificar que el rol sea válido
        if (!PERMISOS_POR_ROL[user.rol]) {
            console.error('Rol no válido:', user.rol);
            logout();
            return;
        }

        // Aplicar permisos
        aplicarPermisosSegunRol();
        
        console.log(`Acceso permitido para rol: ${user.rol}`);
    } catch (error) {
        console.error('Error al validar acceso:', error);
        logout();
    }
}

// Inicializar cuando el DOM esté listo
document.addEventListener('DOMContentLoaded', () => {
    configurarNavegacionAdmin();
    
    // Validar acceso después de un pequeño delay para permitir que initializePage() se ejecute
    setTimeout(() => {
        if (window.currentUser) {
            aplicarPermisosSegunRol();
        }
    }, 500);
});
