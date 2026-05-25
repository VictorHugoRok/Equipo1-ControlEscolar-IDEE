/**
 * Gestión de Personal Administrativo
 * Controlado por Secretario Administrativo
 */

let personalData = [];
let personalEditando = null;

function normalizarNombreTituloPersonalLive(raw) {
    const s = String(raw == null ? '' : raw);
    if (!s) return '';
    let out = '';
    let startWord = true;
    for (let i = 0; i < s.length; i++) {
        const ch = s.charAt(i);
        if (ch === ' ') {
            out += ch;
            startWord = true;
            continue;
        }
        if (startWord) {
            out += ch.toUpperCase();
            startWord = false;
        } else {
            out += ch.toLowerCase();
        }
    }
    return out;
}

function normalizarNombreTituloPersonalFinal(raw) {
    return normalizarNombreTituloPersonalLive(raw).trim().replace(/\s+/g, ' ');
}

function normalizarCurpUpperAlnumPersonal(raw) {
    let s = String(raw == null ? '' : raw).toUpperCase();
    s = s.replace(/[^A-Z0-9]/g, '');
    if (s.length > 18) s = s.slice(0, 18);
    return s;
}

function normalizarTelefono10(raw) {
    const s = String(raw == null ? '' : raw);
    const digits = s.replace(/\D/g, '').slice(0, 10);
    return digits;
}

function actualizarContadorTelefono(inputId, counterId) {
    const inp = document.getElementById(inputId);
    const counter = document.getElementById(counterId);
    if (!inp || !counter) return;
    const n = (inp.value || '').length;
    counter.textContent = n + '/10';
}

function setupPhoneInput10(inputId, counterId) {
    const inp = document.getElementById(inputId);
    if (!inp) return;

    function sync() {
        const norm = normalizarTelefono10(inp.value);
        if (inp.value !== norm) inp.value = norm;
        if (counterId) actualizarContadorTelefono(inputId, counterId);
    }

    inp.addEventListener('input', sync);
    inp.addEventListener('paste', function () { setTimeout(sync, 0); });
    sync();
}

function obtenerEtiquetaSeleccionadaPersonal() {
    const sel = document.getElementById('personalEtiquetaSelect');
    const otro = document.getElementById('personalEtiquetaOtro');
    if (!sel) return null;
    if (sel.value === 'otro') {
        return (otro ? (otro.value || '').trim() : '') || null;
    }
    return (sel.value || '').trim() || null;
}

function aplicarEtiquetaPersonalEnFormulario(etiqueta) {
    const sel = document.getElementById('personalEtiquetaSelect');
    const otro = document.getElementById('personalEtiquetaOtro');
    const wrap = document.getElementById('personalCampoEtiquetaOtro');
    if (!sel || !otro || !wrap) return;

    const etiquetasFijas = ['Dr.', 'Dra.', 'Mtro.', 'Mtra.', 'Lic.', 'CDEO', 'CDEE', 'CDEP', 'LOEO'];
    const et = (etiqueta || '').trim();
    if (et && !etiquetasFijas.includes(et)) {
        sel.value = 'otro';
        otro.value = et;
        wrap.classList.remove('d-none');
        return;
    }
    sel.value = et || '';
    otro.value = '';
    wrap.classList.add('d-none');
}

function tipoUsuarioActualPersonal() {
    try {
        if (window.currentUser && window.currentUser.tipoUsuario) return String(window.currentUser.tipoUsuario);
    } catch (e) {}
    return localStorage.getItem('userTipo') || '';
}

function puedeDarAltaPersonalAdministrativo() {
    const t = String(tipoUsuarioActualPersonal() || '').toUpperCase();
    return t === 'ADMIN' || t === 'SECRETARIA_ACADEMICA';
}

function labelRolParaPuesto(value) {
    const v = String(value == null ? '' : value).trim();
    if (!v) return '';
    if (typeof etiquetaRolListaUsuarios === 'function') {
        const corta = etiquetaRolListaUsuarios(v);
        if (corta) return corta;
    }
    try {
        if (typeof PERMISOS_POR_ROL !== 'undefined' && PERMISOS_POR_ROL && PERMISOS_POR_ROL[v] && PERMISOS_POR_ROL[v].rolDisplay) {
            return String(PERMISOS_POR_ROL[v].rolDisplay);
        }
    } catch (e) {}
    return v;
}

function poblarSelectPuestoConRoles() {
    const sel = document.getElementById('personalPuesto');
    if (!sel) return;

    let roles = [];
    try {
        if (typeof ROLES !== 'undefined' && ROLES) {
            roles = Object.keys(ROLES).map(k => ROLES[k]).filter(Boolean);
        }
    } catch (e) {}
    if (!roles.length) {
        roles = ['ADMIN', 'SECRETARIA_ACADEMICA', 'SECRETARIA_ADMINISTRATIVA', 'COORDINADOR_ACADEMICO', 'MAESTRO'];
    }
    roles = roles
        .map(r => String(r).toUpperCase())
        .filter(r => r && r !== 'ALUMNO' && r !== 'MAESTRO');

    const actual = String(sel.value || '').trim().toUpperCase();
    sel.innerHTML = '<option value="">Selecciona un rol...</option>' + roles.map(function (r) {
        const lbl = labelRolParaPuesto(r);
        const selected = actual && actual === r ? ' selected' : '';
        return '<option value="' + escapeHtmlPersonal(r) + '"' + selected + '>' + escapeHtmlPersonal(lbl) + '</option>';
    }).join('');
}

function getHeadersPersonal(includeContentType = true) {
    const headers = {};
    if (includeContentType) {
        headers['Content-Type'] = 'application/json';
    }

    const token = localStorage.getItem('token');
    if (token && token !== 'null' && token !== 'undefined') {
        headers['Authorization'] = `Bearer ${token}`;
    }

    return headers;
}

function escapeHtmlPersonal(value) {
    if (typeof escapeHtml === 'function') {
        return escapeHtml(value);
    }

    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

// ==================== CARGAR DATOS ====================

/**
 * Cargar todo el personal
 */
async function cargarPersonal() {
    try {
        const response = await fetch(`${API_URL}/personal`, {
            method: 'GET',
            headers: getHeadersPersonal()
        });

        if (!response.ok) {
            throw new Error('Error al cargar personal');
        }

        personalData = await response.json();
        renderizarTablaPersonal(personalData);
    } catch (error) {
        console.error('Error al cargar personal:', error);
        mostrarErrorTablaPersonal('Error al cargar la lista de personal');
    }
}

// ==================== RENDERIZAR TABLA ====================

/**
 * Renderizar tabla de personal
 */
function renderizarTablaPersonal(lista) {
    const tbody = document.getElementById('personalTableBody');
    if (!tbody) return;

    if (!Array.isArray(lista) || lista.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="6" class="text-center text-muted py-4">
                    <i class="bi bi-inbox"></i> No hay personal registrado
                </td>
            </tr>
        `;
        return;
    }

    var nombreCompleto = (persona) => {
        var et = (persona.etiqueta || '').trim();
        var n = persona.nombre || persona.nombres || '';
        var p = persona.apellidoPaterno || '';
        var m = persona.apellidoMaterno || '';
        var base = [n, p, m].filter(Boolean).join(' ').trim();
        if (!base) return '—';
        return (et ? (et + ' ') : '') + base;
    };
    tbody.innerHTML = lista.map(persona => {
        var nombre = nombreCompleto(persona);
        var nombreSeguro = escapeHtmlPersonal(nombre.replace(/'/g, "\\'"));
        return `<tr>
            <td><strong>${escapeHtmlPersonal(nombre)}</strong></td>
            <td>${escapeHtmlPersonal(labelRolParaPuesto(persona.puesto) || 'N/A')}</td>
            <td><small>${escapeHtmlPersonal(persona.correoInstitucional || 'N/A')}</small></td>
            <td>${escapeHtmlPersonal(persona.telefono || 'N/A')}</td>
            <td>${getBadgeActivoPersonal(persona.activo)}</td>
            <td>
                <button class="btn btn-sm btn-outline-secondary me-1" onclick="editarPersonal(${persona.id}); event.stopPropagation();">Editar</button>
                <button class="btn btn-sm btn-outline-danger" onclick="confirmarEliminarPersonal(${persona.id}, '${nombreSeguro}'); event.stopPropagation();">Eliminar</button>
            </td>
        </tr>`;
    }).join('');
}

function mostrarErrorTablaPersonal(mensaje) {
    const tbody = document.getElementById('personalTableBody');
    if (!tbody) return;

    tbody.innerHTML = `
        <tr>
            <td colspan="6" class="text-center text-danger py-4">
                <i class="bi bi-exclamation-triangle"></i> ${mensaje}
            </td>
        </tr>
    `;
}

function getBadgeActivoPersonal(activo) {
    return activo
        ? '<span class="badge bg-success-subtle text-success">Activo</span>'
        : '<span class="badge bg-secondary-subtle text-secondary">Inactivo</span>';
}

// ==================== GUARDAR PERSONAL ====================

/**
 * Guardar o actualizar personal
 */
async function guardarPersonal(evento) {
    evento.preventDefault();

    if (!puedeDarAltaPersonalAdministrativo()) {
        alert('No tienes permisos para dar de alta personal administrativo.');
        return;
    }

    const formulario = evento.target;
    const personalId = document.getElementById('personalId')?.value;

    var apellidoM = document.getElementById('personalApellidoMaterno')?.value?.trim() || '';
    var telRaw = document.getElementById('personalTelefono')?.value || '';
    var telefono = normalizarTelefono10(telRaw);
    if (String(telRaw || '').trim() && telefono.length !== 10) {
        alert('El teléfono debe tener exactamente 10 dígitos numéricos.');
        return;
    }

    const datos = {
        curp: (function () {
            const el = document.getElementById('personalClave');
            const v = el ? normalizarCurpUpperAlnumPersonal(el.value) : '';
            if (el && el.value !== v) el.value = v;
            return v || null;
        })(),
        nombre: normalizarNombreTituloPersonalFinal(document.getElementById('personalNombres').value),
        apellidoPaterno: normalizarNombreTituloPersonalFinal(document.getElementById('personalApellidos').value),
        apellidoMaterno: normalizarNombreTituloPersonalFinal(apellidoM || ' ') || ' ',
        etiqueta: obtenerEtiquetaSeleccionadaPersonal(),
        puesto: String(document.getElementById('personalPuesto').value || '').trim(),
        correoInstitucional: document.getElementById('personalCorreo').value.trim(),
        telefono: telefono || null,
        activo: document.getElementById('personalActivo')?.checked !== false
    };

    // Validar campos requeridos
    if (!datos.nombre || !datos.apellidoPaterno || !datos.puesto || !datos.correoInstitucional) {
        alert('Por favor completa todos los campos requeridos');
        return;
    }

    try {
        const metodo = personalId ? 'PUT' : 'POST';
        const url = personalId ? `${API_URL}/personal/${personalId}` : `${API_URL}/personal`;

        const response = await fetch(url, {
            method: metodo,
            headers: getHeadersPersonal(),
            body: JSON.stringify(datos)
        });

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.error || `Error al guardar personal: ${response.status}`);
        }

        const resultado = await response.json();

        alert(personalId ? 'Personal actualizado correctamente' : 'Personal registrado correctamente');
        
        // Limpiar formulario
        formulario.reset();
        document.getElementById('personalId').value = '';
        
        // Recargar tabla
        await cargarPersonal();
    } catch (error) {
        console.error('Error al guardar personal:', error);
        alert('Error al guardar personal: ' + error.message);
    }
}

// ==================== EDITAR PERSONAL ====================

/**
 * Editar personal existente
 */
async function editarPersonal(id) {
    try {
        const response = await fetch(`${API_URL}/personal/${id}`, {
            method: 'GET',
            headers: getHeadersPersonal()
        });

        if (!response.ok) {
            throw new Error('Error al cargar datos del personal');
        }

        const persona = await response.json();

        poblarSelectPuestoConRoles();

        // Llenar formulario
        document.getElementById('personalId').value = persona.id || '';
        document.getElementById('personalNombres').value = persona.nombre || '';
        // Combinamos apellidoPaterno y apellidoMaterno en un solo campo
        const apellidos = (persona.apellidoPaterno || '') + ' ' + (persona.apellidoMaterno || '');
        document.getElementById('personalApellidos').value = (persona.apellidoPaterno || '').trim();
        var apellidoM = document.getElementById('personalApellidoMaterno');
        if (apellidoM) apellidoM.value = (persona.apellidoMaterno || '').trim();
        document.getElementById('personalPuesto').value = (persona.puesto || '').toString().trim();
        document.getElementById('personalCorreo').value = persona.correoInstitucional || '';
        document.getElementById('personalTelefono').value = normalizarTelefono10(persona.telefono || '');
        document.getElementById('personalClave').value = persona.curp || '';
        document.getElementById('personalActivo').checked = persona.activo !== false;
        aplicarEtiquetaPersonalEnFormulario(persona.etiqueta || '');
        actualizarContadorTelefono('personalTelefono', 'personalTelefonoCounter');

        var tabBtn = document.getElementById('tab-registrar-personal');
        if (tabBtn && typeof bootstrap !== 'undefined' && bootstrap.Tab) {
            bootstrap.Tab.getOrCreateInstance(tabBtn).show();
        }
        document.getElementById('personalForm')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    } catch (error) {
        console.error('Error al editar personal:', error);
        alert('Error al cargar datos del personal');
    }
}

document.addEventListener('DOMContentLoaded', function () {
    poblarSelectPuestoConRoles();
    setupPhoneInput10('personalTelefono', 'personalTelefonoCounter');

    aplicarEtiquetaPersonalEnFormulario('');

    ['personalNombres', 'personalApellidos', 'personalApellidoMaterno'].forEach(function (id) {
        var el = document.getElementById(id);
        if (!el || el.getAttribute('data-norm') === '1') return;
        el.setAttribute('data-norm', '1');
        el.addEventListener('input', function () {
            var pos = el.selectionStart;
            var v = normalizarNombreTituloPersonalLive(el.value);
            if (el.value !== v) {
                el.value = v;
                try { el.setSelectionRange(pos, pos); } catch (_) {}
            }
        });
        el.addEventListener('blur', function () {
            var v = normalizarNombreTituloPersonalFinal(el.value);
            if (el.value !== v) el.value = v;
        });
    });

    var curp = document.getElementById('personalClave');
    if (curp && curp.getAttribute('data-curp-norm') !== '1') {
        curp.setAttribute('data-curp-norm', '1');
        curp.addEventListener('input', function () {
            var pos = curp.selectionStart;
            var v = normalizarCurpUpperAlnumPersonal(curp.value);
            if (curp.value !== v) {
                curp.value = v;
                try { curp.setSelectionRange(pos, pos); } catch (_) {}
            }
        });
        curp.addEventListener('blur', function () {
            var v = normalizarCurpUpperAlnumPersonal(curp.value);
            if (curp.value !== v) curp.value = v;
        });
    }

    if (!puedeDarAltaPersonalAdministrativo()) {
        const form = document.getElementById('personalForm');
        if (form) {
            form.querySelectorAll('input,select,textarea,button').forEach(function (el) {
                if (!el) return;
                if (el.id === 'personalBusqueda' || el.id === 'personalFiltroActivo') return;
                if (el.type === 'submit' || el.tagName === 'BUTTON' || el.tagName === 'INPUT' || el.tagName === 'SELECT' || el.tagName === 'TEXTAREA') {
                    // Deshabilitar captura/guardado; el listado sí se sigue consultando según permisos de backend.
                    if (el.type !== 'button' || (el.textContent || '').toLowerCase().indexOf('limpiar') === -1) {
                        el.disabled = true;
                    }
                }
            });
        }
    }
});

/**
 * Limpiar formulario de personal
 */
function limpiarFormularioPersonal() {
    const formulario = document.getElementById('personalForm');
    if (formulario) {
        formulario.reset();
    }
    document.getElementById('personalId').value = '';
    aplicarEtiquetaPersonalEnFormulario('');
    actualizarContadorTelefono('personalTelefono', 'personalTelefonoCounter');
}

// ==================== ELIMINAR PERSONAL ====================

/**
 * Confirmar y eliminar personal
 */
async function confirmarEliminarPersonal(id, nombre) {
    var ok = false;
    if (typeof window.uiConfirm === 'function') {
        ok = await window.uiConfirm(`¿Está seguro de eliminar a ${nombre}?`, {
            title: 'Eliminar usuario',
            subtitle: 'Esta acción no se puede deshacer',
            okText: 'Eliminar',
            cancelText: 'Cancelar'
        });
    }
    if (!ok) return;
    await eliminarPersonal(id);
}

/**
 * Eliminar personal
 */
async function eliminarPersonal(id) {
    try {
        const response = await fetch(`${API_URL}/personal/${id}`, {
            method: 'DELETE',
            headers: getHeadersPersonal()
        });

        if (!response.ok) {
            throw new Error(`Error al eliminar personal: ${response.status}`);
        }

        alert('Personal eliminado correctamente');
        
        // Recargar tabla
        await cargarPersonal();
    } catch (error) {
        console.error('Error al eliminar personal:', error);
        alert('Error al eliminar personal: ' + error.message);
    }
}

// ==================== BÚSQUEDA Y FILTROS ====================

/**
 * Buscar personal por filtros
 */
function buscarPersonal() {
    const busqueda = (document.getElementById('personalBusqueda')?.value || '').toLowerCase().trim();
    const activo = document.getElementById('personalFiltroActivo')?.value || '';

    const filtrados = personalData.filter(persona => {
        var nombre = (persona.nombre || '') + ' ' + (persona.apellidoPaterno || '') + ' ' + (persona.apellidoMaterno || '');
        const coincideNombre = !busqueda || nombre.toLowerCase().includes(busqueda);
        const coincideActivo = !activo || String(persona.activo) === activo;
        return coincideNombre && coincideActivo;
    });

    renderizarTablaPersonal(filtrados);
}

/**
 * Limpiar filtros de búsqueda
 */
function limpiarBusquedaPersonal() {
    var b = document.getElementById('personalBusqueda');
    var a = document.getElementById('personalFiltroActivo');
    if (b) b.value = '';
    if (a) a.value = '';
    renderizarTablaPersonal(personalData);
}

// ==================== EXPORTAR DATOS ====================

/**
 * Exportar lista de personal a Excel (demo)
 */
function exportarPersonalExcel() {
    alert('Funcionalidad de exportación a Excel en desarrollo.\nSe generará un archivo .xlsx con la lista de personal.');
}
