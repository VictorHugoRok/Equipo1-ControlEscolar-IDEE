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
 * Lista de roles efectivos (principal + adicionales), misma fuente que el backend.
 */
function obtenerRolesActualesLista() {
    if (window.currentUser && Array.isArray(window.currentUser.roles) && window.currentUser.roles.length) {
        return window.currentUser.roles;
    }
    try {
        var raw = localStorage.getItem('userRoles');
        if (raw) {
            var parsed = JSON.parse(raw);
            if (Array.isArray(parsed) && parsed.length) return parsed;
        }
    } catch (e) { /* ignorar */ }
    var uno = localStorage.getItem('userTipo');
    return uno ? [uno] : [];
}

/**
 * Rol activo para la UI (menú, permisos de pantalla): el elegido en el conmutador multi-rol.
 */
function obtenerRolActivoParaUi() {
    if (typeof ensureRolActivoInStorage === 'function') {
        ensureRolActivoInStorage();
    }
    var lista = obtenerRolesActualesLista();
    if (!lista.length) return null;
    var st = localStorage.getItem('rolActivo');
    if (st && lista.indexOf(st) !== -1) return st;
    return lista[0];
}

/**
 * Cambia el perfil activo y navega al inicio del portal correspondiente (misma sesión).
 */
function establecerRolActivoSidebar(nuevoRol) {
    var lista = obtenerRolesActualesLista();
    if (!nuevoRol || lista.indexOf(nuevoRol) === -1) return;
    localStorage.setItem('rolActivo', nuevoRol);
    localStorage.setItem('userTipo', nuevoRol);
    try {
        sessionStorage.removeItem('auth_me_cache');
    } catch (e) { /* ignorar */ }
    if (typeof redirectByUserType === 'function') {
        redirectByUserType(nuevoRol);
    } else {
        window.location.reload();
    }
}

/**
 * Construye el selector bajo el logo (óvalo) cuando hay más de un rol.
 */
function inicializarSelectorRolSidebar() {
    var wrap = document.getElementById('sidebarRolSwitcherWrap');
    if (!wrap) return;
    var lista = obtenerRolesActualesLista();
    var activo = obtenerRolActivoParaUi();
    if (!lista.length || !activo) {
        wrap.innerHTML = '<span class="sidebar-badge-academico" id="sidebarRolActivoLabel">—</span>';
        return;
    }
    var labelText = (typeof etiquetaRolListaUsuarios === 'function')
        ? etiquetaRolListaUsuarios(activo)
        : ((PERMISOS_POR_ROL[activo] && PERMISOS_POR_ROL[activo].rolDisplay) ? PERMISOS_POR_ROL[activo].rolDisplay : activo);

    if (lista.length <= 1) {
        wrap.innerHTML = '<span class="sidebar-badge-academico" id="sidebarRolActivoLabel"></span>';
        var el = document.getElementById('sidebarRolActivoLabel');
        if (el) el.textContent = labelText;
        return;
    }

    var chevron = (typeof bootstrap !== 'undefined')
        ? '<i class="bi bi-chevron-down sidebar-rol-caret ms-1" aria-hidden="true"></i>'
        : '';
    wrap.innerHTML =
        '<div class="dropdown sidebar-rol-dropdown">' +
        '<button type="button" class="sidebar-badge-academico dropdown-toggle" id="sidebarRolSwitcherBtn" data-bs-toggle="dropdown" aria-expanded="false" aria-label="Cambiar perfil activo">' +
        '<span id="sidebarRolActivoLabel"></span>' + chevron +
        '</button>' +
        '<ul class="dropdown-menu dropdown-menu-dark sidebar-rol-menu" id="sidebarRolDropdownMenu"></ul>' +
        '</div>';

    var lab = document.getElementById('sidebarRolActivoLabel');
    if (lab) lab.textContent = labelText;

    var menu = document.getElementById('sidebarRolDropdownMenu');
    if (!menu) return;
    lista.forEach(function (r) {
        var txt = (typeof etiquetaRolListaUsuarios === 'function')
            ? etiquetaRolListaUsuarios(r)
            : ((PERMISOS_POR_ROL[r] && PERMISOS_POR_ROL[r].rolDisplay) ? PERMISOS_POR_ROL[r].rolDisplay : r);
        var li = document.createElement('li');
        var a = document.createElement('button');
        a.type = 'button';
        a.className = 'dropdown-item' + (r === activo ? ' active' : '');
        a.textContent = txt;
        a.setAttribute('data-rol-activo', r);
        a.addEventListener('click', function () {
            if (r !== activo) establecerRolActivoSidebar(r);
        });
        li.appendChild(a);
        menu.appendChild(li);
    });
}

/**
 * Obtener el rol actual del usuario desde la sesión (equivale al rol activo en multi-rol).
 */
function obtenerRolActual() {
    var active = obtenerRolActivoParaUi();
    if (active) {
        console.log('[ROLES] Rol activo (UI):', active);
        return active;
    }
    if (window.currentUser && window.currentUser.tipoUsuario) {
        console.log('[ROLES] Rol desde window.currentUser:', window.currentUser.tipoUsuario);
        return window.currentUser.tipoUsuario;
    }
    var rolDesdeStorage = localStorage.getItem('userTipo');
    if (rolDesdeStorage) {
        console.log('[ROLES] Rol desde localStorage:', rolDesdeStorage);
        return rolDesdeStorage;
    }
    console.warn('[ROLES] No se encontró rol (usuario no autenticado)');
    return null;
}

/**
 * Permisos del rol activo únicamente (vista actual del conmutador).
 */
function obtenerPermisosActuales() {
    var rol = obtenerRolActivoParaUi();
    if (rol && PERMISOS_POR_ROL[rol]) {
        return PERMISOS_POR_ROL[rol];
    }
    return null;
}

/**
 * Página de inicio del portal según el rol activo (rutas relativas a /pages/).
 * Docentes y estudiantes no deben usar dashboard.html.
 */
function redirigirAPaginaInicioPortal() {
    if (typeof ensureRolActivoInStorage === 'function') {
        ensureRolActivoInStorage();
    }
    var rol = typeof obtenerRolActivoParaUi === 'function' ? obtenerRolActivoParaUi() : null;
    if (!rol && window.currentUser) {
        rol = window.currentUser.rol || window.currentUser.tipoUsuario;
    }
    if (!rol) {
        rol = localStorage.getItem('userTipo');
    }
    rol = rol ? String(rol).toUpperCase().trim() : '';
    if (rol === 'MAESTRO') {
        window.location.href = 'maestro.html';
        return;
    }
    if (rol === 'ALUMNO') {
        window.location.href = 'alumno.html';
        return;
    }
    if (rol === 'SIN_ROL') {
        if (typeof redirectToSinRolAsignadoPage === 'function') {
            redirectToSinRolAsignadoPage();
            return;
        }
        window.location.href = 'sin-rol-asignado.html';
        return;
    }
    window.location.href = 'dashboard.html';
}

/**
 * Ajusta el enlace del logo del sidebar al inicio correcto según rol (evita enviar docentes a dashboard).
 */
function ajustarEnlaceHomeSidebar() {
    var rol = typeof obtenerRolActivoParaUi === 'function' ? obtenerRolActivoParaUi() : null;
    if (!rol && window.currentUser) {
        rol = window.currentUser.rol || window.currentUser.tipoUsuario;
    }
    if (!rol) {
        rol = localStorage.getItem('userTipo');
    }
    rol = rol ? String(rol).toUpperCase().trim() : '';
    var href = 'dashboard.html';
    if (rol === 'MAESTRO') {
        href = 'maestro.html';
    } else if (rol === 'ALUMNO') {
        href = 'alumno.html';
    }
    var container = document.getElementById('sidebar-container');
    var a = container ? container.querySelector('.sidebar-header a') : document.querySelector('.sidebar-header a');
    if (a) {
        a.setAttribute('href', href);
    }
}

/**
 * Aplicar permisos según el rol del usuario
 * 
 * ⚠️ IMPORTANTE: NO hacer logout por errores de JavaScript
 * El logout SOLO debe ocurrir si el backend responde 401
 */
function aplicarPermisosSegunRol() {
    if (typeof enforceSoloSinRolLandingPage === 'function' && enforceSoloSinRolLandingPage()) {
        return;
    }

    var pathLower = (window.location.pathname || '').toLowerCase();
    if (pathLower.indexOf('dashboard') !== -1) {
        if (typeof ensureRolActivoInStorage === 'function') {
            ensureRolActivoInStorage();
        }
        var rDash = typeof obtenerRolActivoParaUi === 'function' ? obtenerRolActivoParaUi() : null;
        if (rDash === 'MAESTRO') {
            window.location.replace('maestro.html');
            return;
        }
        if (rDash === 'ALUMNO') {
            window.location.replace('alumno.html');
            return;
        }
    }

    var rolesLista = obtenerRolesActualesLista();

    // ❌ Si no hay rol, no es un error fatal
    if (!rolesLista.length) {
        console.warn('⚠️ [ROLES] No hay rol disponible aún (usuario puede no estar autenticado)');
        console.warn('Esto es normal si se llama antes de que getCurrentUser() termine');
        return;
    }

    var rolUi = obtenerRolActivoParaUi();
    if (!rolUi || !esRolValido(rolUi)) {
        console.error('[ROLES] Rol activo no reconocido:', rolUi);
        console.warn('No se aplicarán permisos, pero NO cerrando sesión');
        return;
    }

    const permisos = obtenerPermisosActuales();
    if (!permisos) return;
    console.log('[ROLES] Aplicando permisos para rol activo:', rolUi, '(disponibles:', rolesLista.join(', ') + ')');

    // 1. Actualizar navbar con nombre del rol
    actualizarNavbar(permisos);

    // 2. Controlar visibilidad de secciones (solo si la página tiene secciones; dashboard ya no)
    controlarVisibilidadSecciones(permisos);

    // 2b. Mostrar/ocultar ítems del menú y tarjetas del panel por permiso de sección
    controlarVisibilidadMenuPorSecciones(permisos);

    // 3. Controlar interfaz de calificaciones según permisos
    controlarInterfazCalificaciones(permisos);

    // 4. Controlar menú de Títulos y Certificados
    controlarMenuTitulosCertificados(permisos);

    // 5. Deshabilitar/habilitar elementos interactivos
    controlarElementosInteractivos(permisos);

    // 6. Conmutador de perfil bajo el logo
    inicializarSelectorRolSidebar();

    // 7. Logo del menú → inicio del portal según rol (no dashboard para docente/alumno)
    ajustarEnlaceHomeSidebar();
}

/**
 * Actualizar navbar con información del rol
 */
function actualizarNavbar(permisos) {
    // Misma etiqueta que la lista de usuarios (chips) y el conmutador bajo el logo
    const navbarRole = document.getElementById('navbarRole');
    if (navbarRole) {
        var rAct = (typeof obtenerRolActivoParaUi === 'function') ? obtenerRolActivoParaUi() : null;
        if (rAct && typeof etiquetaRolListaUsuarios === 'function') {
            navbarRole.textContent = etiquetaRolListaUsuarios(rAct);
        } else {
            navbarRole.textContent = permisos.rolBadge;
        }
    }
    // Nombre completo (encima del correo)
    var nombre = localStorage.getItem('userNombreCompleto');
    const sidebarNombre = document.getElementById('sidebarUserNombre');
    if (sidebarNombre) {
        if (nombre && nombre.trim()) {
            sidebarNombre.textContent = typeof formatearNombreMostrar === 'function'
                ? formatearNombreMostrar(nombre.trim())
                : nombre.trim();
        } else {
            sidebarNombre.textContent = '—';
        }
    }
    /* El rol se muestra arriba en el conmutador; no duplicar en #navbarUserDisplay (footer). */
}

/** Clave para recordar la última sección visible (tras recarga se mantiene) */
const DASHBOARD_LAST_SECTION_KEY = 'dashboardLastSection';

/**
 * Controlar la visibilidad de secciones según rol.
 * Solo se muestra UNA sección: Programas por defecto o la última visitada (sessionStorage).
 * Nunca se muestran todas las secciones a la vez.
 */
function controlarVisibilidadSecciones(permisos) {
    const todasLasSecciones = document.querySelectorAll('.admin-section');
    if (todasLasSecciones.length === 0) return; // Dashboard sin secciones: navegación por páginas
    todasLasSecciones.forEach(seccion => {
        seccion.classList.add('d-none');
    });

    if (permisos.secciones.length === 0) return;

    // Decidir qué única sección mostrar: última guardada (si es válida para el rol) o la primera (Programas)
    let sectionIdToShow = null;
    try {
        const saved = sessionStorage.getItem(DASHBOARD_LAST_SECTION_KEY);
        if (saved && permisos.secciones.includes(saved)) {
            sectionIdToShow = saved;
        }
    } catch (e) { /* ignorar */ }
    if (!sectionIdToShow) {
        sectionIdToShow = permisos.secciones[0]; // Programas educativos
    }

    let section = document.getElementById(sectionIdToShow);
    if (!section && permisos.secciones.length > 0) {
        // Si la sección guardada ya no existe en el DOM (por cambios de UI),
        // hacer fallback seguro a la primera sección permitida (Programas, etc.).
        sectionIdToShow = permisos.secciones[0];
        section = document.getElementById(sectionIdToShow);
    }
    if (section) {
        section.classList.remove('d-none');
    }

    // Marcar en el menú la sección activa
    marcarSeccionActivaEnNavbar(sectionIdToShow);
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
            descElement.textContent = 'Los maestros asignan las calificaciones. Aquí puede modificar, asignar observaciones y confirmar. Una vez confirmada, se define el estatus final (Aprobado/Reprobado).';
        } else {
            descElement.textContent = 'Los maestros asignan las calificaciones. Aquí solo puede visualizar; la secretaría académica modifica y confirma.';
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
            footer.textContent = 'Nota: Modifique si es necesario, asigne observaciones y confirme. Al confirmar se define Aprobado/Reprobado y la calificación no podrá modificarse.';
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
 * Mostrar/ocultar ítems del menú y tarjetas del panel según permisos.secciones
 * (navegación por páginas: Programas, Usuarios, Calificaciones, Horarios, …)
 */
function controlarVisibilidadMenuPorSecciones(permisos) {
    const mapMenu = {
        'programasSection': 'menuProgramas',
        'personalSection': 'menuPersonal',
        'gruposSection': 'menuGrupos',
        'horariosSection': 'menuHorarios',
        'inscripcionesSection': 'menuInscripciones',
        'ciclosPeriodosSection': 'menuCiclosPeriodos',
        'cohortesSection': 'menuCohortes',
        'calificacionesSection': 'menuCalificaciones',
        'kardexSection': 'menuKardex',
        'evaluacionDocenteSection': 'menuEvaluacionDocente'
    };
    Object.keys(mapMenu).forEach(function (sectionId) {
        var id = mapMenu[sectionId];
        var el = document.getElementById(id);
        if (el) {
            if (permisos.secciones.indexOf(sectionId) !== -1) {
                el.classList.remove('d-none');
            } else {
                el.classList.add('d-none');
            }
        }
    });
    var cardPairs = [
        ['cardProgramas', 'programasSection'],
        ['cardPersonal', 'personalSection'],
        ['cardGrupos', 'gruposSection'],
        ['cardHorarios', 'horariosSection'],
        ['cardInscripciones', 'inscripcionesSection'],
        ['cardCalificaciones', 'calificacionesSection'],
        ['cardKardex', 'kardexSection'],
        ['cardCiclosPeriodos', 'ciclosPeriodosSection'],
        ['cardCohortes', 'cohortesSection'],
        ['cardEvaluacionDocente', 'evaluacionDocenteSection']
    ];
    cardPairs.forEach(function (pair) {
        var card = document.getElementById(pair[0]);
        if (card) {
            if (permisos.secciones.indexOf(pair[1]) !== -1) {
                card.classList.remove('d-none');
            } else {
                card.classList.add('d-none');
            }
        }
    });
    if (permisos.puedeVerCertificados) {
        var c = document.getElementById('cardCertificados');
        if (c) c.classList.remove('d-none');
    }
    if (permisos.puedeVerConstancias) {
        var c = document.getElementById('cardConstancias');
        if (c) c.classList.remove('d-none');
    }
    if (permisos.puedeVerConfiguracionSep) {
        var c = document.getElementById('cardConfigSep');
        if (c) c.classList.remove('d-none');
    }
    if (permisos.puedeVerTitulos) {
        var c = document.getElementById('cardTitulos');
        if (c) c.classList.remove('d-none');
    }
    // Mostrar/ocultar dropdowns completos según si tienen ítems visibles
    var tieneControlEscolar = permisos.secciones.some(function (s) {
        return ['gruposSection', 'horariosSection', 'inscripcionesSection', 'ciclosPeriodosSection', 'cohortesSection'].indexOf(s) !== -1;
    });
    var menuControlEscolar = document.getElementById('menuControlEscolar');
    if (menuControlEscolar) {
        if (tieneControlEscolar) menuControlEscolar.classList.remove('d-none');
        else menuControlEscolar.classList.add('d-none');
    }
    var tieneTramites = permisos.puedeVerCertificados || permisos.puedeVerConstancias ||
        permisos.puedeVerConfiguracionSep || permisos.puedeVerTitulos;
    var menuTramites = document.getElementById('menuTramites');
    if (menuTramites) {
        if (tieneTramites) menuTramites.classList.remove('d-none');
        else menuTramites.classList.add('d-none');
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
        if (permisos.puedeVerConfiguracionSep && (permisos.puedeVerCertificados || permisos.puedeVerConstancias || permisos.puedeVerTitulos)) {
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

var _configurarNavegacionAdminDone = false;
/**
 * Configurar navegación entre secciones (compatible con ambos roles). Solo una vez por página.
 */
function configurarNavegacionAdmin() {
    if (_configurarNavegacionAdminDone) return;
    _configurarNavegacionAdminDone = true;
    const adminNavLinks = document.querySelectorAll("#adminNavbar [data-admin-section]");
    adminNavLinks.forEach((link) => {
        var href = link.getAttribute("href") || "";
        // Si el enlace va a una página .html, dejar navegación normal (no prevenir default)
        if (href && href !== "#" && href.indexOf(".html") !== -1) {
            return;
        }
        link.addEventListener("click", function (e) {
            e.preventDefault();
            var sectionId = this.getAttribute("data-admin-section");
            var permisos = obtenerPermisosActuales();
            if (!permisos || !permisos.secciones.includes(sectionId)) {
                alert('No tienes permiso para acceder a esta sección.');
                return;
            }
            activarAdminSection(sectionId);
            var menu = document.getElementById("adminNavbar") || document.getElementById("navbarMenuPrincipal");
            if (menu) {
                menu.querySelectorAll(".nav-link, .nav-link-ide").forEach(function (nav) { nav.classList.remove("active"); });
            }
            if (this.classList.contains("dropdown-item") && this.closest(".dropdown")) {
                var parentLink = this.closest(".dropdown").querySelector(".nav-link");
                if (parentLink) parentLink.classList.add("active");
            } else {
                this.classList.add("active");
            }
        });
    });
}

/**
 * Activar una sección específica (solo esa visible) y guardarla para tras recarga
 */
function activarAdminSection(sectionId) {
    document.querySelectorAll(".admin-section").forEach((sec) => {
        sec.classList.add("d-none");
    });

    const section = document.getElementById(sectionId);
    if (section) {
        section.classList.remove("d-none");
    }

    try {
        sessionStorage.setItem(DASHBOARD_LAST_SECTION_KEY, sectionId);
    } catch (e) { /* ignorar */ }

    marcarSeccionActivaEnNavbar(sectionId);
}

/**
 * Marca en el navbar/sidebar el ítem correspondiente a la sección visible (quita active del resto)
 */
function marcarSeccionActivaEnNavbar(sectionId) {
    const navbar = document.getElementById('adminNavbar') || document.getElementById('navbarMenuPrincipal');
    if (!navbar) return;
    navbar.querySelectorAll('.nav-link, .nav-link-ide').forEach((nav) => nav.classList.remove('active'));
    const link = navbar.querySelector('[data-admin-section="' + sectionId + '"]');
    if (link) {
        if (link.classList.contains('dropdown-item') && link.closest('.dropdown')) {
            const parent = link.closest('.dropdown');
            const parentLink = parent && parent.querySelector('.nav-link');
            if (parentLink) parentLink.classList.add('active');
        } else {
            link.classList.add('active');
        }
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
 * Validar acceso a una página de módulo (MPA).
 * Si el rol actual no tiene permiso para esa sección o permiso, redirige a dashboard.
 * Uso: validarAccesoModulo({ seccion: 'programasSection' }) o validarAccesoModulo({ permiso: 'puedeVerTitulos' })
 */
function validarAccesoModulo(opciones) {
    if (!opciones || (!opciones.seccion && !opciones.permiso)) return;
    var path = (window.location.pathname || '').toLowerCase();
    var yaEnDashboard = path.indexOf('dashboard') !== -1;
    if (yaEnDashboard) return;
    var permisos = obtenerPermisosActuales();
    if (!permisos) {
        redirigirAPaginaInicioPortal();
        return;
    }
    if (opciones.seccion && permisos.secciones.indexOf(opciones.seccion) === -1) {
        redirigirAPaginaInicioPortal();
        return;
    }
    if (opciones.permiso && !permisos[opciones.permiso]) {
        redirigirAPaginaInicioPortal();
        return;
    }
}

/**
 * Función para redirigir según el rol (se llama desde app.js después de autenticar)
 * Retorna la página a la que debe ir el usuario
 */
function obtenerPaginaSegunRol(rol) {
    var r = rol ? String(rol).toUpperCase().trim() : '';
    if (r === 'MAESTRO') {
        return '/pages/maestro.html';
    }
    if (r === 'ALUMNO') {
        return '/pages/alumno.html';
    }
    if (r === 'SIN_ROL') {
        return '/pages/sin-rol-asignado.html';
    }
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

        if (typeof usuarioEsSoloSinRol === 'function' && usuarioEsSoloSinRol()) {
            if (typeof redirectToSinRolAsignadoPage === 'function') redirectToSinRolAsignadoPage();
            else window.location.href = 'sin-rol-asignado.html';
            return;
        }

        var rolCheck = (typeof obtenerRolActivoParaUi === 'function') ? obtenerRolActivoParaUi() : user.rol;
        if (rolCheck === 'MAESTRO') {
            window.location.replace('maestro.html');
            return;
        }
        if (rolCheck === 'ALUMNO') {
            window.location.replace('alumno.html');
            return;
        }

        // Verificar que el rol sea válido
        if (!rolCheck || !PERMISOS_POR_ROL[rolCheck]) {
            console.error('Rol no válido:', rolCheck);
            if (rolCheck === 'SIN_ROL' && typeof redirectToSinRolAsignadoPage === 'function') {
                redirectToSinRolAsignadoPage();
            } else {
                logout();
            }
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

// Inicializar cuando el DOM esté listo (solo una vez por carga)
document.addEventListener('DOMContentLoaded', () => {
    configurarNavegacionAdmin();
    // Aplicar permisos si ya hay usuario (dashboard y páginas de módulos lo establecen)
    if (window.currentUser || localStorage.getItem('userTipo')) {
        if (!window.currentUser && localStorage.getItem('userTipo')) {
            var rl = [];
            try {
                var jr = localStorage.getItem('userRoles');
                if (jr) rl = JSON.parse(jr);
            } catch (e) {}
            if (!rl || !rl.length) rl = [localStorage.getItem('userTipo')];
            var rolAct = (typeof obtenerRolActivoParaUi === 'function') ? obtenerRolActivoParaUi() : localStorage.getItem('userTipo');
            window.currentUser = {
                email: localStorage.getItem('userEmail') || '',
                tipoUsuario: localStorage.getItem('userTipo'),
                rol: rolAct || localStorage.getItem('userTipo'),
                roles: rl
            };
        }
        aplicarPermisosSegunRol();
    }
});

// Cuando el menú lateral se carga desde partial (páginas con sidebar único), aplicar permisos de nuevo
document.addEventListener('sidebarLoaded', () => {
    if (window.currentUser || localStorage.getItem('userTipo')) {
        aplicarPermisosSegunRol();
    }
});
