/**
 * ROLES.JS - DEFINICIÓN ÚNICA Y CENTRALIZADA DE ROLES
 * =====================================================
 * 
 * Este archivo es la ÚNICA fuente de verdad para roles y permisos.
 * Se carga PRIMERO para evitar conflictos.
 * 
 * NO debe haber otra definición de ROLES en el proyecto.
 */

// ============================================
// DEFINICIÓN ÚNICA DE ROLES
// ============================================
const ROLES = Object.freeze({
    ADMIN: 'ADMIN',
    SECRETARIA_ACADEMICA: 'SECRETARIA_ACADEMICA',
    SECRETARIO_ADMINISTRATIVO: 'SECRETARIO_ADMINISTRATIVO'
});

/**
 * PERMISOS POR ROL - Definición centralizada
 * 
 * Cada rol tiene:
 * - secciones: qué módulos puede ver
 * - permisos: qué acciones puede ejecutar
 * - display: cómo se muestra en la UI
 */
const PERMISOS_POR_ROL = Object.freeze({
    ADMIN: {
        secciones: [
            'programasSection',
            'docentesSection',
            'alumnosSection',
            'calificacionesSection',
            'horariosSection'
        ],
        puedeVerCalificaciones: true,
        puedeEditarCalificaciones: false,
        puedeConfirmarCalificaciones: false,
        puedeVerTitulos: false,
        puedeVerCertificados: false,
        puedeVerConstancias: false,
        puedeVerConfiguracionSep: false,
        rolDisplay: 'Administrativo',
        rolBadge: 'Control escolar'
    },
    SECRETARIA_ACADEMICA: {
        secciones: [
            'programasSection',
            'docentesSection',
            'alumnosSection',
            'calificacionesSection',
            'horariosSection',
            'certificadosSection',
            'constanciasSection',
            'configuracionSepSection'
        ],
        puedeVerCalificaciones: true,
        puedeEditarCalificaciones: true,
        puedeConfirmarCalificaciones: true,
        puedeVerTitulos: true,
        puedeVerCertificados: true,
        puedeVerConstancias: true,
        puedeVerConfiguracionSep: true,
        rolDisplay: 'Secretaría Académica',
        rolBadge: 'Control Académico'
    },
    SECRETARIO_ADMINISTRATIVO: {
        secciones: [
            'programasSection',
            'docentesSection',
            'horariosSection',
            'certificadosSection',
            'constanciasSection'
        ],
        puedeVerCalificaciones: false,
        puedeEditarCalificaciones: false,
        puedeConfirmarCalificaciones: false,
        puedeVerTitulos: false,
        puedeVerCertificados: true,
        puedeVerConstancias: true,
        puedeVerConfiguracionSep: false,
        rolDisplay: 'Secretaría Administrativa',
        rolBadge: 'Gestión Administrativa'
    }
});

/**
 * Obtener permisos de un rol específico
 * @param {string} rol - El rol del usuario
 * @returns {object|null} Los permisos del rol o null si no existe
 */
function obtenerPermisosDelRol(rol) {
    return PERMISOS_POR_ROL[rol] || null;
}

/**
 * Validar si un rol es reconocido
 * @param {string} rol - El rol a validar
 * @returns {boolean} true si el rol es válido
 */
function esRolValido(rol) {
    return PERMISOS_POR_ROL.hasOwnProperty(rol);
}

console.log('✅ [ROLES] Sistema de roles centralizado cargado');
