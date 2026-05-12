/**
 * Gestión de permisos basados en rol
 * Controla la visibilidad y habilitación de elementos según el rol del usuario
 * 
 * ⚠️ IMPORTANTE: Este archivo NO debe definir ROLES ni PERMISOS_POR_ROL
 * Esas definiciones están CENTRALIZADAS en roles.js
 * 
 * Este archivo solo contiene funciones de navegación compatible con ambas páginas
 */

// ✅ Verificar que roles.js se cargó primero
if (typeof ROLES === 'undefined' || typeof PERMISOS_POR_ROL === 'undefined') {
    console.error('❌ [GESTION-PERMISOS] Error: roles.js no está cargado.');
    console.error('Orden correcto: config.js → roles.js → auth.js → ...');
}

// configurarNavegacionAdmin y activarAdminSection están definidas en sistema-roles-unified.js
// No se redefinen aquí para evitar conflictos de permisos.

/**
 * Aplicar permisos de calificaciones
 */
function aplicarPermisosCalificaciones(rol, permisos) {
    const seccionCalificaciones = document.getElementById('calificacionesSection');
    if (!seccionCalificaciones) return;

    // Ajustar título según rol
    const titulo = seccionCalificaciones.querySelector('.section-title');
    if (titulo) {
        if (rol === 'ADMIN') {
            titulo.textContent = 'Calificaciones';
        } else if (rol === 'SECRETARIA_ACADEMICA') {
            titulo.textContent = 'Calificaciones (confirmación)';
        }
    }

    // Ajustar descripción según rol
    const descripcion = seccionCalificaciones.querySelector('.text-muted');
    if (descripcion) {
        if (rol === 'ADMIN') {
            descripcion.textContent = 'El maestro captura las calificaciones con sus criterios. Aquí el personal administrativo solo puede ver.';
        } else if (rol === 'SECRETARIA_ACADEMICA') {
            descripcion.textContent = 'El maestro captura las calificaciones con sus criterios. Aquí el personal administrativo las revisa, corrige si es necesario y las confirma.';
        }
    }

    // Aplicar permisos de edición en los campos de calificaciones
    const camposCalificacion = seccionCalificaciones.querySelectorAll('input[type="number"]');
    camposCalificacion.forEach(campo => {
        if (!permisos.puedeEditarCalificaciones) {
            campo.disabled = true;
            campo.classList.add('disabled-field');
        } else {
            campo.disabled = false;
            campo.classList.remove('disabled-field');
        }
    });

    // Deshabilitar/activar botones de confirmar según permiso
    const botones = seccionCalificaciones.querySelectorAll('button');
    botones.forEach(btn => {
        const text = (btn.textContent || '').trim().toLowerCase();
        if (text.includes('confirmar') || text.includes('confirmar calificación')) {
            btn.disabled = !permisos.puedeEditarCalificaciones;
            if (!permisos.puedeEditarCalificaciones) {
                btn.classList.add('disabled-field');
            } else {
                btn.classList.remove('disabled-field');
            }
        }
    });
}

/**
 * Aplicar permisos de navegación
 */
function aplicarPermisosNavegación(rol, permisos) {
    // Aplicar estilos CSS para indicar campos deshabilitados
    agregarEstilosDeshabilitados();
}

/**
 * Agregar estilos CSS para campos deshabilitados
 */
function agregarEstilosDeshabilitados() {
    if (document.getElementById('estilos-deshabilitados')) {
        return; // Ya se agregó
    }

    const style = document.createElement('style');
    style.id = 'estilos-deshabilitados';
    style.innerHTML = `
        input.disabled-field,
        select.disabled-field {
            background-color: #f5f5f5 !important;
            color: #999 !important;
            cursor: not-allowed !important;
        }
        
        input.disabled-field:hover,
        select.disabled-field:hover {
            border-color: #dee2e6 !important;
        }
    `;
    document.head.appendChild(style);
}

/**
 * Inicializar el sistema de permisos
 * Se debe llamar después de que currentUser esté disponible
 */
function inicializarSistemaPermisos() {
    if (!window.currentUser) {
        console.warn('currentUser no está disponible todavía');
        return;
    }

    const rol = obtenerRolActual();
    if (rol) {
        console.log(`Aplicando permisos para rol: ${rol}`);
        aplicarPermisosSegúnRol(rol);
    } else {
        console.warn('No se pudo determinar el rol del usuario');
    }
}

/**
 * Hook para ejecutarse después de la inicialización de la página
 * Se debe llamar en el script de inicialización principal
 */
function onInitializationComplete() {
    inicializarSistemaPermisos();
}
