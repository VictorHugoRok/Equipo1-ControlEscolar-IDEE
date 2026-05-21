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
// Nombres deben coincidir con el backend (Usuario.TipoUsuario)
const ROLES = Object.freeze({
    ADMIN: 'ADMIN',
    COORDINADOR_ACADEMICO: 'COORDINADOR_ACADEMICO',
    SECRETARIA_ACADEMICA: 'SECRETARIA_ACADEMICA',
    SECRETARIA_ADMINISTRATIVA: 'SECRETARIA_ADMINISTRATIVA',
    MAESTRO: 'MAESTRO',
    ALUMNO: 'ALUMNO',
    SIN_ROL: 'SIN_ROL'
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
            'personalSection',
            'gruposSection',
            'horariosSection',
            'inscripcionesSection',
            'ciclosPeriodosSection',
            'cohortesSection',
            'calificacionesSection',
            'kardexSection',
            'certificadosSection',
            'capCalifCertSection',
            'constanciasSection',
            'configuracionSepSection',
            'evaluacionDocenteSection'
        ],
        puedeVerCalificaciones: true,
        puedeEditarCalificaciones: true,
        puedeConfirmarCalificaciones: true,
        puedeVerTitulos: true,
        puedeVerCertificados: true,
        puedeVerConstancias: true,
        puedeVerConfiguracionSep: true,
        rolDisplay: 'Administrador',
        rolBadge: 'Acceso total'
    },
    SECRETARIA_ACADEMICA: {
        secciones: [
            'programasSection',
            'personalSection',
            'gruposSection',
            'horariosSection',
            'inscripcionesSection',
            'ciclosPeriodosSection',
            'cohortesSection',
            'calificacionesSection',
            'kardexSection',
            'certificadosSection',
            'capCalifCertSection',
            'constanciasSection',
            'configuracionSepSection',
            'evaluacionDocenteSection'
        ],
        puedeVerCalificaciones: true,
        puedeEditarCalificaciones: true,
        puedeConfirmarCalificaciones: true,
        puedeVerTitulos: true,
        puedeVerCertificados: true,
        puedeVerConstancias: true,
        puedeVerConfiguracionSep: true,
        rolDisplay: 'Secretaria Académica',
        rolBadge: 'Control Académico'
    },
    COORDINADOR_ACADEMICO: {
        secciones: [
            'personalSection',
            'gruposSection',
            'horariosSection',
            'inscripcionesSection',
            'cohortesSection',
            'calificacionesSection',
            'kardexSection'
        ],
        puedeVerCalificaciones: true,
        puedeEditarCalificaciones: true,
        puedeConfirmarCalificaciones: true,
        puedeVerTitulos: false,
        puedeVerCertificados: false,
        puedeVerConstancias: false,
        puedeVerConfiguracionSep: false,
        rolDisplay: 'Coordinador Académico',
        rolBadge: 'Solo programas asignados'
    },
    SECRETARIA_ADMINISTRATIVA: {
        secciones: [
            'programasSection',
            'personalSection',
            'gruposSection',
            'horariosSection',
            'inscripcionesSection',
            'cohortesSection',
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
        rolDisplay: 'Secretaria Administrativa',
        rolBadge: 'Gestión Administrativa'
    },
    MAESTRO: {
        secciones: [
            'calificacionesSection'
        ],
        puedeVerCalificaciones: true,
        puedeEditarCalificaciones: false,
        puedeConfirmarCalificaciones: false,
        puedeVerTitulos: false,
        puedeVerCertificados: false,
        puedeVerConstancias: false,
        puedeVerConfiguracionSep: false,
        rolDisplay: 'Maestro',
        rolBadge: 'Docente'
    },
    ALUMNO: {
        secciones: [
            'kardexSection'
        ],
        puedeVerCalificaciones: false,
        puedeEditarCalificaciones: false,
        puedeConfirmarCalificaciones: false,
        puedeVerTitulos: false,
        puedeVerCertificados: false,
        puedeVerConstancias: false,
        puedeVerConfiguracionSep: false,
        rolDisplay: 'Alumno',
        rolBadge: 'Estudiante'
    },
    SIN_ROL: {
        secciones: [],
        puedeVerCalificaciones: false,
        puedeEditarCalificaciones: false,
        puedeConfirmarCalificaciones: false,
        puedeVerTitulos: false,
        puedeVerCertificados: false,
        puedeVerConstancias: false,
        puedeVerConfiguracionSep: false,
        rolDisplay: 'Sin rol asignado',
        rolBadge: 'Pendiente de asignación'
    }
});

/**
 * Etiqueta corta alineada con la columna «Roles» en la lista de usuarios/personal
 * (chips) y con el conmutador de perfil bajo el logo. Mantiene nombres como «Docente»,
 * «Sec. Acad.», «Coord. Acad.», etc.
 * @param {string} rol - Código de rol (ej. MAESTRO, SECRETARIA_ACADEMICA)
 * @returns {string}
 */
function etiquetaRolListaUsuarios(rol) {
    var r = String(rol || '').toUpperCase().trim();
    switch (r) {
        case 'ALUMNO': return 'Estudiante';
        case 'MAESTRO': return 'Docente';
        case 'COORDINADOR_ACADEMICO': return 'Coord. Acad.';
        case 'SECRETARIA_ACADEMICA': return 'Sec. Acad.';
        case 'SECRETARIA_ADMINISTRATIVA': return 'Sec. Admin.';
        case 'ADMIN': return 'Admin.';
        case 'SIN_ROL': return 'Sin Rol';
        default: return rol == null ? '' : String(rol);
    }
}

/**
 * Obtener permisos de un rol específico
 * @param {string} rol - El rol del usuario
 * @returns {object|null} Los permisos del rol o null si no existe
 */
function obtenerPermisosDelRol(rol) {
    return PERMISOS_POR_ROL[rol] || null;
}

function unionSeccionesRoles(a, b) {
    var s = new Set((a || []).concat(b || []));
    return Array.from(s);
}

/**
 * Unión de permisos de interfaz para varios roles (secciones y flags booleanos OR).
 */
function fusionarPermisosPorRoles(roles) {
    if (!roles || roles.length === 0) return null;
    var valid = roles.filter(function (r) { return PERMISOS_POR_ROL[r]; });
    if (valid.length === 0) return null;
    if (valid.length === 1) {
        var solo = JSON.parse(JSON.stringify(PERMISOS_POR_ROL[valid[0]]));
        solo.rolDisplay = etiquetaRolListaUsuarios(valid[0]);
        return solo;
    }
    var base = JSON.parse(JSON.stringify(PERMISOS_POR_ROL[valid[0]]));
    for (var i = 1; i < valid.length; i++) {
        var p = PERMISOS_POR_ROL[valid[i]];
        base.secciones = unionSeccionesRoles(base.secciones, p.secciones);
        Object.keys(p).forEach(function (k) {
            if (k === 'secciones' || k === 'rolDisplay' || k === 'rolBadge') return;
            if (typeof p[k] === 'boolean') base[k] = !!(base[k] || p[k]);
        });
    }
    base.rolDisplay = valid.map(function (r) { return etiquetaRolListaUsuarios(r); }).join(' · ');
    base.rolBadge = valid.length > 1 ? 'Perfiles múltiples' : etiquetaRolListaUsuarios(valid[0]);
    return base;
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
