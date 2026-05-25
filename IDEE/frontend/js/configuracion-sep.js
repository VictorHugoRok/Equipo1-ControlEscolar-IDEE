/**
 * configuracion-sep.js
 * Gestión de configuración institucional y responsables de firma para títulos electrónicos SEP
 */

console.log('📄 configuracion-sep.js cargado');

let configuracionActual = null;
let plantelesCatalog = [];
let plantelEditando = null;
/** Evitar bucles: una sola carga por vez y no repetir en menos de 2 s */
var _configSepCargaEnCurso = false;
var _configSepUltimaCargaTs = 0;
var _configSepMinIntervaloMs = 2000;

function normalizarNombreTituloResponsableLive(raw) {
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

function normalizarNombreTituloResponsableFinal(raw) {
    return normalizarNombreTituloResponsableLive(raw).trim().replace(/\s+/g, ' ');
}

function setupNombreApellidoResponsableInputs() {
    const ids = ['responsableNombre', 'responsablePrimerApellido', 'responsableSegundoApellido'];
    ids.forEach(function (id) {
        const inp = document.getElementById(id);
        if (!inp) return;

        function syncLive() {
            const norm = normalizarNombreTituloResponsableLive(inp.value);
            if (inp.value !== norm) inp.value = norm;
        }
        function syncFinal() {
            const norm = normalizarNombreTituloResponsableFinal(inp.value);
            if (inp.value !== norm) inp.value = norm;
        }

        inp.addEventListener('input', syncLive);
        inp.addEventListener('paste', function () { setTimeout(syncLive, 0); });
        inp.addEventListener('blur', syncFinal);
        syncFinal();
    });
}

/**
 * Función helper para crear headers con token si está disponible
 * Compatible tanto con seguridad habilitada como deshabilitada
 */
function getHeaders(includeContentType = true) {
    const headers = {};

    if (includeContentType) {
        headers['Content-Type'] = 'application/json';
    }

    // Si existe token en localStorage, incluirlo
    const token = localStorage.getItem('token');
    if (token && token !== 'null' && token !== 'undefined') {
        headers['Authorization'] = `Bearer ${token}`;
    }

    return headers;
}

// ==================== INICIALIZACIÓN ====================

document.addEventListener('DOMContentLoaded', function() {
    console.log('🔍 DOMContentLoaded en configuracion-sep.js');
    if (window._configSepInitDone) return;
    window._configSepInitDone = true;

    // Inicializar si estamos en la página de configuración SEP (dashboard con sección o MPA con tabs)
    const configuracionSection = document.getElementById('configuracionSepSection');
    const configSepTabs = document.getElementById('configSepTabs');
    const root = configuracionSection || configSepTabs;
    if (!root) return;

    console.log('✅ Inicializando configuración SEP...');
    try {
        inicializarEventosConfiguracion();
    } catch (error) {
        console.error('❌ Error al inicializar eventos:', error);
    }

    function cargarConfigSepUnaVez() {
        if (_configSepCargaEnCurso) return;
        if (Date.now() - _configSepUltimaCargaTs < _configSepMinIntervaloMs) return;
        _configSepCargaEnCurso = true;
        _configSepUltimaCargaTs = Date.now();
        Promise.all([
            cargarConfiguracionInstitucional().catch(function(e) { console.warn('Config SEP carga institución:', e); }),
            cargarResponsablesFirma().catch(function(e) { console.warn('Config SEP carga responsables:', e); }),
            cargarPlantelesConfig().catch(function(e) { console.warn('Config SEP carga planteles:', e); })
        ]).finally(function() {
            _configSepCargaEnCurso = false;
        });
    }

    if (configuracionSection) {
        const observer = new MutationObserver(function(mutations) {
            mutations.forEach(function(mutation) {
                if (mutation.attributeName === 'class') {
                    const target = mutation.target;
                    if (!target.classList.contains('d-none')) {
                        cargarConfigSepUnaVez();
                    }
                }
            });
        });
        observer.observe(configuracionSection, { attributes: true });
    }

    // Si la sección está visible o estamos en MPA (sin d-none), cargar datos una sola vez
    if (!configuracionSection || !configuracionSection.classList.contains('d-none')) {
        cargarConfigSepUnaVez();
    }
});

function inicializarEventosConfiguracion() {
    console.log('🎯 Ejecutando inicializarEventosConfiguracion()');

    // Institución (datos para folios/XML)
    const btnGuardarInstitucion = document.getElementById('btnGuardarInstitucion');
    if (btnGuardarInstitucion) btnGuardarInstitucion.addEventListener('click', guardarConfiguracionInstitucional);
    const btnLimpiarInstitucion = document.getElementById('btnLimpiarInstitucion');
    if (btnLimpiarInstitucion) btnLimpiarInstitucion.addEventListener('click', function () { limpiarFormularioInstitucion(); });

    // Catálogo de entidades federativas (México): seleccionar nombre y asignar ID automáticamente
    inicializarCatalogoEntidadesFederativas();
    inicializarCatalogoEntidadesFederativasPlantel();

    // Formulario de Responsables
    const btnGuardarResponsable = document.getElementById('btnGuardarResponsable');
    if (btnGuardarResponsable) {
        btnGuardarResponsable.addEventListener('click', guardarResponsable);
    }
    setupNombreApellidoResponsableInputs();

    // Modal - Reset form solo cuando se abre desde el botón "Nuevo" (no al editar)
    const modalResponsable = document.getElementById('modalResponsable');
    if (modalResponsable) {
        modalResponsable.addEventListener('show.bs.modal', function(event) {
            const button = event.relatedTarget;
            if (button && !button.dataset.responsableId) {
                limpiarFormularioResponsable();
            }
            // Reaplicar normalización al abrir el modal, también al editar.
            setupNombreApellidoResponsableInputs();
        });
    }

    // Mostrar ID del cargo y sincronizar texto de cargo al seleccionar en catálogo SEP
    const selCargo = document.getElementById('responsableIdCargo');
    if (selCargo) {
        selCargo.addEventListener('change', function() {
            actualizarIdCargoDisplay();
            sincronizarCargoDesdeSelect();
        });
    }

    // Formulario de Certificados
    const formCertificados = document.getElementById('formCertificados');
    console.log('formCertificados:', formCertificados);
    if (formCertificados) {
        formCertificados.addEventListener('submit', guardarCertificados);
        console.log('✓ Event listener agregado a formCertificados');

        // Refuerzo: algunos navegadores/atributos inline pueden evitar el submit.
        const btnSubirCerts = formCertificados.querySelector('button[type="submit"]');
        if (btnSubirCerts) {
            btnSubirCerts.addEventListener('click', guardarCertificados);
        }
    } else {
        console.warn('⚠️ formCertificados NO encontrado');
    }

    // Al mostrar la pestaña FIEL, cargar estado
    const tabCertificados = document.querySelector('[data-bs-target="#certificadosSep"]');
    if (tabCertificados) {
        tabCertificados.addEventListener('shown.bs.tab', function() {
            cargarEstadoCertificados();
        });
    }

    // Si al cargar la página la pestaña Certificados ya está activa, cargar estado
    const tabCertificadosPane = document.getElementById('certificadosSep');
    if (tabCertificadosPane && tabCertificadosPane.classList.contains('show') && tabCertificadosPane.classList.contains('active')) {
        cargarEstadoCertificados();
    }

    // Eventos del formulario de plantel
    const btnGuardarPlantel = document.getElementById('btnGuardarPlantel');
    if (btnGuardarPlantel) btnGuardarPlantel.addEventListener('click', guardarPlantelConfig);
    const btnCancelarPlantel = document.getElementById('btnCancelarPlantel');
    if (btnCancelarPlantel) btnCancelarPlantel.addEventListener('click', limpiarFormularioPlantelConfig);

    const plantelesTbody = document.getElementById('plantelesTableBody');
    if (plantelesTbody) {
        plantelesTbody.addEventListener('click', function(e) {
            const row = e.target.closest('tr[data-plantel-id]');
            if (!row) return;
            const id = parseInt(row.dataset.plantelId, 10);
            const btn = e.target.closest('button[data-action]');
            if (btn && id) {
                if (btn.dataset.action === 'edit-plantel') editarPlantelConfig(id);
                else if (btn.dataset.action === 'delete-plantel') eliminarPlantelConfig(id);
            }
        });
    }
}

// ==================== CONFIGURACIÓN INSTITUCIONAL ====================

async function cargarConfiguracionInstitucional() {
    try {
        const response = await fetch(`${API_BASE_URL}/configuracion-institucional`, {
            headers: getHeaders()
        });

        if (response.ok) {
            configuracionActual = await response.json();
            mostrarConfiguracionEnFormulario(configuracionActual);
        } else if (response.status === 404) {
            console.log('No hay configuración institucional activa');
        } else {
            throw new Error('Error al cargar configuración');
        }
    } catch (error) {
        console.error('Error:', error);
        mostrarAlerta('alertaInstitucion', 'danger', 'Error al cargar la configuración institucional');
    }
}

function getCatalogoEntidadesFederativasMx() {
    // IDs según catálogo SEP (01..32)
    return [
        { id: '01', nombre: 'AGUASCALIENTES' },
        { id: '02', nombre: 'BAJA CALIFORNIA' },
        { id: '03', nombre: 'BAJA CALIFORNIA SUR' },
        { id: '04', nombre: 'CAMPECHE' },
        { id: '05', nombre: 'COAHUILA DE ZARAGOZA' },
        { id: '06', nombre: 'COLIMA' },
        { id: '07', nombre: 'CHIAPAS' },
        { id: '08', nombre: 'CHIHUAHUA' },
        { id: '09', nombre: 'CIUDAD DE MÉXICO' },
        { id: '10', nombre: 'DURANGO' },
        { id: '11', nombre: 'GUANAJUATO' },
        { id: '12', nombre: 'GUERRERO' },
        { id: '13', nombre: 'HIDALGO' },
        { id: '14', nombre: 'JALISCO' },
        { id: '15', nombre: 'MÉXICO' },
        { id: '16', nombre: 'MICHOACÁN DE OCAMPO' },
        { id: '17', nombre: 'MORELOS' },
        { id: '18', nombre: 'NAYARIT' },
        { id: '19', nombre: 'NUEVO LEÓN' },
        { id: '20', nombre: 'OAXACA' },
        { id: '21', nombre: 'PUEBLA' },
        { id: '22', nombre: 'QUERÉTARO' },
        { id: '23', nombre: 'QUINTANA ROO' },
        { id: '24', nombre: 'SAN LUIS POTOSÍ' },
        { id: '25', nombre: 'SINALOA' },
        { id: '26', nombre: 'SONORA' },
        { id: '27', nombre: 'TABASCO' },
        { id: '28', nombre: 'TAMAULIPAS' },
        { id: '29', nombre: 'TLAXCALA' },
        { id: '30', nombre: 'VERACRUZ DE IGNACIO DE LA LLAVE' },
        { id: '31', nombre: 'YUCATÁN' },
        { id: '32', nombre: 'ZACATECAS' }
    ];
}

function inicializarCatalogoEntidadesFederativas() {
    const sel = document.getElementById('entidadFederativaSelect');
    const inputId = document.getElementById('idEntidadFederativa');
    const inputNombre = document.getElementById('entidadFederativa');
    if (!sel || !inputId || !inputNombre) return;

    // Solo aplica si el campo es un select (evita romper si se reutiliza en otras pantallas)
    if ((sel.tagName || '').toUpperCase() !== 'SELECT') return;

    // Evitar repoblar si ya tiene opciones (además del placeholder)
    if (sel.options && sel.options.length > 1) return;

    const catalogo = getCatalogoEntidadesFederativasMx();
    catalogo.forEach(function(e) {
        const opt = document.createElement('option');
        opt.value = e.id;
        opt.textContent = e.id + ' - ' + e.nombre;
        opt.setAttribute('data-entidad-id', e.id);
        opt.setAttribute('data-entidad-nombre', e.nombre);
        sel.appendChild(opt);
    });

    sel.addEventListener('change', function() {
        sincronizarIdEntidadDesdeEntidad();
    });

    // Si ya venía con valor (por autofill o carga previa), sincronizar
    sincronizarIdEntidadDesdeEntidad();
}

function sincronizarIdEntidadDesdeEntidad() {
    const sel = document.getElementById('entidadFederativaSelect');
    const inputId = document.getElementById('idEntidadFederativa');
    const inputNombre = document.getElementById('entidadFederativa');
    if (!sel || !inputId || !inputNombre) return;
    if ((sel.tagName || '').toUpperCase() !== 'SELECT') return;

    const id = (sel.value || '').trim();
    if (!id) {
        inputId.value = '';
        inputNombre.value = '';
        return;
    }
    const catalogo = getCatalogoEntidadesFederativasMx();
    const match = catalogo.find(function(e) { return e.id === id; });
    inputId.value = match ? match.id : '';
    inputNombre.value = match ? match.nombre : '';
}

function inicializarCatalogoEntidadesFederativasPlantel() {
    const sel = document.getElementById('plantelEntidadFederativaSelect');
    const inputId = document.getElementById('plantelIdEntidadFederativa');
    const inputNombre = document.getElementById('plantelEntidadFederativa');
    if (!sel || !inputId || !inputNombre) return;
    if ((sel.tagName || '').toUpperCase() !== 'SELECT') return;
    if (sel.options && sel.options.length > 1) return;
    const catalogo = getCatalogoEntidadesFederativasMx();
    catalogo.forEach(function (e) {
        const opt = document.createElement('option');
        opt.value = e.id;
        opt.textContent = e.id + ' - ' + e.nombre;
        opt.setAttribute('data-entidad-id', e.id);
        opt.setAttribute('data-entidad-nombre', e.nombre);
        sel.appendChild(opt);
    });
    sel.addEventListener('change', function () { sincronizarIdEntidadDesdeEntidadPlantel(); });
    sincronizarIdEntidadDesdeEntidadPlantel();
}

function sincronizarIdEntidadDesdeEntidadPlantel() {
    const sel = document.getElementById('plantelEntidadFederativaSelect');
    const inputId = document.getElementById('plantelIdEntidadFederativa');
    const inputNombre = document.getElementById('plantelEntidadFederativa');
    if (!sel || !inputId || !inputNombre) return;
    if ((sel.tagName || '').toUpperCase() !== 'SELECT') return;
    const id = (sel.value || '').trim();
    if (!id) {
        inputId.value = '';
        inputNombre.value = '';
        return;
    }
    const catalogo = getCatalogoEntidadesFederativasMx();
    const match = catalogo.find(function (e) { return e.id === id; });
    inputId.value = match ? match.id : '';
    inputNombre.value = match ? match.nombre : '';
}

function mostrarConfiguracionEnFormulario(config) {
    const ids = ['institucionId', 'cveInstitucion', 'nombreInstitucion', 'idEntidadFederativa', 'entidadFederativa', 'idCampus', 'campus'];
    const vals = [config.id || '', config.cveInstitucion || '', config.nombreInstitucion || '', config.idEntidadFederativa || '', config.entidadFederativa || '', config.idCampus || '', config.campus || ''];
    for (let i = 0; i < ids.length; i++) {
        const el = document.getElementById(ids[i]);
        if (el) el.value = vals[i];
    }

    // Plantel base (deriva cveInstitucion/nombreInstitucion/idCampus/campus)
    const selPlantel = document.getElementById('institucionPlantelBase');
    if (selPlantel) {
        // Si el config trae cveInstitucion, intentar seleccionar el plantel cuyo idPlantel coincida
        const targetCve = (config.cveInstitucion || '').trim();
        if (targetCve && selPlantel.options && selPlantel.options.length > 0) {
            const opt = Array.prototype.slice.call(selPlantel.options).find(function (o) {
                return String(o.value || '') === String(targetCve);
            });
            if (opt) selPlantel.value = String(targetCve);
        }
        // Si no hay selección, escoger el primer plantel con idPlantel
        if (!selPlantel.value) {
            const first = (plantelesCatalog || []).find(function (p) { return p && p.idPlantel && String(p.idPlantel).trim(); });
            if (first) selPlantel.value = String(first.idPlantel).trim();
        }
        aplicarPlantelBaseAConfiguracion();
    }

    // Entidad federativa ahora se deriva del plantel base (no se captura aquí).
}

function aplicarPlantelBaseAConfiguracion() {
    const selPlantel = document.getElementById('institucionPlantelBase');
    if (!selPlantel) return;
    const idPlantel = (selPlantel.value || '').trim();
    if (!idPlantel) return;
    const p = (plantelesCatalog || []).find(function (x) {
        return x && String(x.idPlantel || '').trim() === idPlantel;
    });
    if (!p) return;

    // Mapas solicitados:
    // - cveInstitucion (títulos) = ID Plantel (certificados)
    // - nombreInstitucion = Nombre del plantel
    // - idCampus = Clave DGP
    // - campus = Nombre del plantel (no duplicar otro campo)
    const cve = document.getElementById('cveInstitucion');
    const nom = document.getElementById('nombreInstitucion');
    const idCamp = document.getElementById('idCampus');
    const camp = document.getElementById('campus');
    const idEnt = document.getElementById('idEntidadFederativa');
    const ent = document.getElementById('entidadFederativa');
    if (cve) cve.value = String(p.idPlantel || '').trim();
    if (nom) nom.value = String(p.nombrePlantel || '').trim();
    if (idCamp) idCamp.value = String(p.claveDgp || '').trim();
    // Permitir capturar un nombre de campus distinto al nombre del plantel.
    // Si está vacío, sugerimos el nombre del plantel como valor inicial.
    if (camp && !String(camp.value || '').trim()) camp.value = String(p.nombrePlantel || '').trim();
    if (idEnt) idEnt.value = String(p.idEntidadFederativa || '').trim();
    if (ent) ent.value = String(p.entidadFederativa || '').trim();
}

async function guardarConfiguracionInstitucional(event) {
    if (event && typeof event.preventDefault === 'function') event.preventDefault();

    const id = document.getElementById('institucionId').value;
    aplicarPlantelBaseAConfiguracion();

    const selPlantel = document.getElementById('institucionPlantelBase');
    if (selPlantel && !String(selPlantel.value || '').trim()) {
        mostrarAlerta('alertaInstitucion', 'warning', 'Seleccione el plantel base para documentos.');
        return;
    }
    const datos = {
        cveInstitucion: document.getElementById('cveInstitucion').value.trim(),
        nombreInstitucion: document.getElementById('nombreInstitucion').value.trim(),
        idEntidadFederativa: document.getElementById('idEntidadFederativa').value.trim(),
        entidadFederativa: document.getElementById('entidadFederativa').value.trim(),
        idCampus: document.getElementById('idCampus').value.trim() || null,
        campus: document.getElementById('campus').value.trim() || null,
        activo: true
    };

    if (!datos.entidadFederativa || !datos.idEntidadFederativa) {
        mostrarAlerta('alertaInstitucion', 'warning', 'El plantel base no tiene entidad federativa configurada. Complete el plantel registrado.');
        return;
    }

    // Si existe configuración, incluir los datos de certificados
    if (configuracionActual) {
        datos.certificadoPath = configuracionActual.certificadoPath;
        datos.llavePrivadaPath = configuracionActual.llavePrivadaPath;
        datos.passwordLlavePrivada = configuracionActual.passwordLlavePrivada;
    }

    try {
        const url = id ? `${API_BASE_URL}/configuracion-institucional/${id}` : `${API_BASE_URL}/configuracion-institucional`;
        const method = id ? 'PUT' : 'POST';

        const response = await fetch(url, {
            method: method,
            headers: getHeaders(),
            body: JSON.stringify(datos)
        });

        if (response.ok) {
            const resultado = await response.json();
            configuracionActual = resultado;
            mostrarAlerta('alertaInstitucion', 'success', 'Configuración guardada exitosamente');
            mostrarConfiguracionEnFormulario(resultado);
        } else {
            throw new Error('Error al guardar configuración');
        }
    } catch (error) {
        console.error('Error:', error);
        mostrarAlerta('alertaInstitucion', 'danger', 'Error al guardar la configuración: ' + error.message);
    }
}

function limpiarFormularioInstitucion() {
    const form = document.getElementById('formInstitucion');
    if (form) form.reset();
    const idEl = document.getElementById('institucionId');
    if (idEl) idEl.value = '';
    ocultarAlerta('alertaInstitucion');
}

// ==================== RESPONSABLES DE FIRMA ====================

function actualizarIdCargoDisplay() {
    const sel = document.getElementById('responsableIdCargo');
    const display = document.getElementById('responsableIdCargoDisplay');
    if (!sel || !display) return;
    const val = sel.value.trim();
    if (!val) {
        display.textContent = '—';
        return;
    }
    const id = val.includes('|') ? val.split('|')[0] : val;
    display.textContent = id;
}

/**
 * Sincroniza el campo cargo (texto) con el texto de la opción seleccionada en el catálogo.
 * idCargo = solo el número; cargo = texto de la opción (Director, SUBDIRECTOR, etc.)
 */
function sincronizarCargoDesdeSelect() {
    const sel = document.getElementById('responsableIdCargo');
    const inputCargo = document.getElementById('responsableCargo');
    if (!sel || !inputCargo) return;
    const val = sel.value.trim();
    if (!val) {
        inputCargo.value = '';
        return;
    }
    const text = val.includes('|') ? val.split('|').slice(1).join('|') : val;
    inputCargo.value = toTitleCaseEs(text);
}

function toTitleCaseEs(s) {
    s = String(s || '').trim();
    if (!s) return '';
    // Normalizar a minúsculas y capitalizar cada palabra separada por espacio
    const lower = s.toLocaleLowerCase('es-MX');
    return lower.split(/\s+/g).map(function (w) {
        if (!w) return w;
        return w.charAt(0).toLocaleUpperCase('es-MX') + w.slice(1);
    }).join(' ');
}

async function cargarResponsablesFirma() {
    try {
        const response = await fetch(`${API_BASE_URL}/responsables-firma`, {
            headers: getHeaders()
        });

        if (response.ok) {
            const responsables = await response.json();
            mostrarResponsablesEnTabla(responsables);
        } else {
            throw new Error('Error al cargar responsables');
        }
    } catch (error) {
        console.error('Error:', error);
        const tbody = document.getElementById('tablaResponsables');
        if (tbody) tbody.innerHTML = '<tr><td colspan="7" class="text-center text-danger">Error al cargar responsables de firma</td></tr>';
    }
}

function mostrarResponsablesEnTabla(responsables) {
    const tbody = document.getElementById('tablaResponsables');
    if (!tbody) return;

    if (responsables.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="7" class="text-center text-muted">
                    No hay responsables de firma registrados
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = responsables.map(resp => {
        const nombreCompleto = `${resp.nombre} ${resp.primerApellido} ${resp.segundoApellido || ''}`.trim();
        const estadoBadge = resp.activo
            ? '<span class="badge bg-success">Activo</span>'
            : '<span class="badge bg-secondary">Inactivo</span>';

        return `
            <tr>
                <td>${resp.ordenFirma}</td>
                <td>${nombreCompleto}</td>
                <td>${resp.curp}</td>
                <td>${resp.cargo}</td>
                <td>${resp.abrTitulo || '-'}</td>
                <td>${estadoBadge}</td>
                <td class="text-end text-nowrap">
                    <div class="btn-group btn-group-sm" role="group">
                        <button type="button" class="btn btn-outline-secondary" onclick="editarResponsable(${resp.id})" title="Editar"><i class="bi bi-pencil"></i></button>
                        <button type="button" class="btn btn-outline-danger" onclick="eliminarResponsable(${resp.id})" title="Eliminar"><i class="bi bi-trash"></i></button>
                    </div>
                </td>
            </tr>
        `;
    }).join('');
}

async function guardarResponsable() {
    const id = document.getElementById('responsableId').value;
    const selCargoVal = document.getElementById('responsableIdCargo').value.trim();
    // idCargo = solo el número; cargo = texto de la opción (Director, SUBDIRECTOR, etc.)
    let idCargoVal = '';
    let cargoVal = '';
    if (selCargoVal && selCargoVal.includes('|')) {
        const parts = selCargoVal.split('|');
        idCargoVal = (parts[0] || '').replace(/[^0-9]/g, '') || parts[0] || '';
        cargoVal = parts.slice(1).join('|').trim();
    } else if (selCargoVal) {
        idCargoVal = selCargoVal.replace(/[^0-9]/g, '') || selCargoVal;
    }

    const datos = {
        nombre: document.getElementById('responsableNombre').value.trim(),
        primerApellido: document.getElementById('responsablePrimerApellido').value.trim(),
        segundoApellido: document.getElementById('responsableSegundoApellido').value.trim(),
        curp: document.getElementById('responsableCurp').value.trim().toUpperCase(),
        idCargo: idCargoVal,
        cargo: cargoVal,
        abrTitulo: document.getElementById('responsableAbrTitulo').value.trim() || null,
        ordenFirma: parseInt(document.getElementById('responsableOrdenFirma').value),
        activo: document.getElementById('responsableActivo').checked
    };

    if (!datos.segundoApellido) {
        mostrarAlerta('alertaResponsable', 'danger', 'El apellido materno es requerido');
        return;
    }

    // Validar CURP
    if (datos.curp.length !== 18) {
        mostrarAlerta('alertaResponsable', 'danger', 'El CURP debe tener 18 caracteres');
        return;
    }
    if (!selCargoVal || !idCargoVal || !cargoVal) {
        mostrarAlerta('alertaResponsable', 'danger', 'Seleccione un cargo');
        return;
    }

    try {
        const url = id ? `${API_BASE_URL}/responsables-firma/${id}` : `${API_BASE_URL}/responsables-firma`;
        const method = id ? 'PUT' : 'POST';

        const response = await fetch(url, {
            method: method,
            headers: getHeaders(),
            body: JSON.stringify(datos)
        });

        if (response.ok) {
            // Cerrar modal
            const modal = bootstrap.Modal.getInstance(document.getElementById('modalResponsable'));
            modal.hide();

            // Recargar tabla
            await cargarResponsablesFirma();

            mostrarAlerta('alertaResponsable', 'success', 'Responsable guardado exitosamente');
            setTimeout(() => ocultarAlerta('alertaResponsable'), 3000);
        } else {
            throw new Error('Error al guardar responsable');
        }
    } catch (error) {
        console.error('Error:', error);
        mostrarAlerta('alertaResponsable', 'danger', 'Error al guardar el responsable: ' + error.message);
    }
}

async function editarResponsable(id) {
    try {
        const response = await fetch(`${API_BASE_URL}/responsables-firma/${id}`, {
            headers: getHeaders()
        });

        if (!response.ok) {
            throw new Error('No se pudo cargar el responsable');
        }

        const responsable = await response.json();

        document.getElementById('responsableId').value = responsable.id;
        document.getElementById('responsableNombre').value = responsable.nombre || '';
        document.getElementById('responsablePrimerApellido').value = responsable.primerApellido || '';
        document.getElementById('responsableSegundoApellido').value = responsable.segundoApellido || '';
        document.getElementById('responsableCurp').value = responsable.curp || '';
        document.getElementById('responsableAbrTitulo').value = responsable.abrTitulo || '';
        document.getElementById('responsableOrdenFirma').value = responsable.ordenFirma != null ? responsable.ordenFirma : 1;
        document.getElementById('responsableActivo').checked = responsable.activo !== false;

        const selCargo = document.getElementById('responsableIdCargo');
        const idC = (responsable.idCargo || '').replace(/[^0-9]/g, '') || responsable.idCargo || '';
        const cargoTxt = (responsable.cargo || '').trim();
        let valorSelect = '';
        if (selCargo) {
            for (let i = 0; i < selCargo.options.length; i++) {
                const opt = selCargo.options[i];
                if (!opt.value) continue;
                const parts = opt.value.split('|');
                const optId = (parts[0] || '').replace(/[^0-9]/g, '') || parts[0];
                const optCargo = parts.slice(1).join('|').trim();
                if (optId === idC || (optCargo && cargoTxt && optCargo.toUpperCase() === cargoTxt.toUpperCase())) {
                    valorSelect = opt.value;
                    break;
                }
            }
            selCargo.value = valorSelect || '';
        }
        document.getElementById('responsableCargo').value = cargoTxt;
        actualizarIdCargoDisplay();

        document.getElementById('modalResponsableTitle').innerHTML =
            '<i class="bi bi-pencil"></i> Editar Responsable de Firma';

        const modal = new bootstrap.Modal(document.getElementById('modalResponsable'));
        modal.show();
    } catch (error) {
        console.error('Error:', error);
        alert('Error al cargar los datos del responsable: ' + (error.message || ''));
    }
}

async function eliminarResponsable(id) {
    if (!confirm('¿Está seguro de desactivar este responsable de firma?')) {
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/responsables-firma/${id}`, {
            method: 'DELETE',
            headers: getHeaders()
        });

        if (response.ok) {
            await cargarResponsablesFirma();
            alert('Responsable desactivado exitosamente');
        } else {
            throw new Error('Error al eliminar responsable');
        }
    } catch (error) {
        console.error('Error:', error);
        alert('Error al eliminar el responsable: ' + error.message);
    }
}

function limpiarFormularioResponsable() {
    document.getElementById('formResponsable').reset();
    document.getElementById('responsableId').value = '';
    document.getElementById('responsableActivo').checked = true;
    actualizarIdCargoDisplay();
    document.getElementById('modalResponsableTitle').innerHTML =
        '<i class="bi bi-person-plus"></i> Nuevo Responsable de Firma';
    ocultarAlerta('alertaResponsable');
}

// ==================== CERTIFICADOS SAT ====================

/**
 * Carga el estado de los certificados desde el backend y actualiza el panel de estado.
 */
async function cargarEstadoCertificados() {
    const panel = document.getElementById('certificadosStatusPanel');
    const content = document.getElementById('certificadosStatusContent');
    if (!panel || !content) return;

    content.innerHTML = '<span class="spinner-border spinner-border-sm" role="status"></span><span>Verificando estado...</span>';

    try {
        const response = await fetch(`${API_BASE_URL}/configuracion-institucional/certificados/diagnostico`, {
            headers: getHeaders()
        });

        if (!response.ok) {
            content.innerHTML = '<span class="text-danger"><i class="bi bi-exclamation-triangle me-1"></i>No se pudo verificar el estado</span>';
            return;
        }

        const data = await response.json();

        if (data.error) {
            content.innerHTML = `<span class="text-warning"><i class="bi bi-info-circle me-1"></i>${data.error}</span>`;
            actualizarTextoBotonSubir(false);
            return;
        }

        if (!data.tieneCertificados) {
            content.innerHTML = `
                <div class="status-row w-100">
                    <span class="badge bg-secondary"><i class="bi bi-x-circle me-1"></i>Sin certificados cargados</span>
                    <span class="text-muted small">Sube los archivos .cer y .key para firmar documentos</span>
                </div>
            `;
            actualizarTextoBotonSubir(false);
            return;
        }

        const parValido = data.parValido === true;
        const cerNombre = data.cerFilename || 'certificado.cer';
        const keyNombre = data.keyFilename || 'llave.key';

        content.innerHTML = `
            <div class="status-row w-100 flex-wrap">
                <span class="badge bg-success"><i class="bi bi-check-circle me-1"></i>Certificados cargados</span>
                <span class="badge ${parValido ? 'bg-success' : 'bg-warning text-dark'}">
                    <i class="bi bi-${parValido ? 'shield-check' : 'exclamation-triangle'} me-1"></i>
                    ${parValido ? 'Par válido' : 'Par no válido'}
                </span>
                <span class="text-muted small">${cerNombre} · ${keyNombre}</span>
                <div class="btn-group btn-group-sm btn-group-certificados ms-auto">
                    <button type="button" class="btn btn-outline-primary" onclick="document.getElementById('formCertificados').scrollIntoView({behavior:'smooth'})">
                        <i class="bi bi-arrow-repeat me-1"></i>Actualizar
                    </button>
                    <button type="button" class="btn btn-outline-danger" onclick="eliminarCertificadosSistema()">
                        <i class="bi bi-trash me-1"></i>Eliminar
                    </button>
                </div>
            </div>
        `;
        actualizarTextoBotonSubir(true);

    } catch (error) {
        console.error('Error al cargar estado de certificados:', error);
        content.innerHTML = '<span class="text-danger"><i class="bi bi-exclamation-triangle me-1"></i>Error al verificar</span>';
    }
}

function actualizarTextoBotonSubir(tieneCertificados) {
    const btn = document.querySelector('#formCertificados button[type="submit"]');
    if (btn) {
        btn.innerHTML = tieneCertificados
            ? '<i class="bi bi-arrow-repeat me-1"></i>Actualizar certificados'
            : '<i class="bi bi-upload me-1"></i>Subir certificados';
    }
}

/**
 * Elimina los certificados del sistema (solo los archivos .cer y .key, no afecta la configuración).
 */
async function eliminarCertificadosSistema() {
    if (!confirm('¿Eliminar los certificados cargados? Los títulos y certificados electrónicos no podrán firmarse hasta que suba nuevos archivos.')) {
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/configuracion-institucional/certificados`, {
            method: 'DELETE',
            headers: getHeaders()
        });

        if (response.ok) {
            await cargarConfiguracionInstitucional();
            await cargarEstadoCertificados();
            document.getElementById('certificadoStatus').innerHTML = '';
            document.getElementById('llaveStatus').innerHTML = '';
            document.getElementById('formCertificados').reset();
            document.getElementById('passwordLlave').value = '';
            mostrarAlerta('alertaCertificados', 'success', 'Certificados eliminados correctamente');
        } else {
            const err = await response.json();
            throw new Error(err.error || 'Error al eliminar');
        }
    } catch (error) {
        console.error('Error:', error);
        mostrarAlerta('alertaCertificados', 'danger', 'Error al eliminar certificados: ' + error.message);
    }
}

async function guardarCertificados(event) {
    if (event && typeof event.preventDefault === 'function') event.preventDefault();
    console.log('guardarCertificados llamado');

    if (!configuracionActual || !configuracionActual.id) {
        mostrarAlerta('alertaCertificados', 'warning',
            'Debe guardar primero la configuración institucional en la pestaña "Institución"');
        return;
    }

    // Obtener archivos del formulario
    const certificadoFile = document.getElementById('certificadoFile').files[0];
    const llavePrivadaFile = document.getElementById('llavePrivadaFile').files[0];
    const password = document.getElementById('passwordLlave').value.trim();

    // Validar que se hayan seleccionado los archivos
    if (!certificadoFile) {
        mostrarAlerta('alertaCertificados', 'warning', 'Debe seleccionar el archivo .cer del certificado');
        return;
    }

    if (!llavePrivadaFile) {
        mostrarAlerta('alertaCertificados', 'warning', 'Debe seleccionar el archivo .key de la llave privada');
        return;
    }

    if (!password) {
        mostrarAlerta('alertaCertificados', 'warning', 'Debe ingresar la contraseña de la llave privada');
        return;
    }

    // Validar extensiones de archivos
    if (!certificadoFile.name.toLowerCase().endsWith('.cer')) {
        mostrarAlerta('alertaCertificados', 'danger', 'El certificado debe ser un archivo .cer');
        return;
    }

    if (!llavePrivadaFile.name.toLowerCase().endsWith('.key')) {
        mostrarAlerta('alertaCertificados', 'danger', 'La llave privada debe ser un archivo .key');
        return;
    }

    // Crear FormData para enviar archivos
    const formData = new FormData();
    formData.append('cer', certificadoFile);
    formData.append('key', llavePrivadaFile);
    formData.append('password', password);

    const form = (event && event.target && event.target.closest)
        ? (event.target.closest('form') || document.getElementById('formCertificados'))
        : document.getElementById('formCertificados');

    // Mostrar estado de carga
    const submitButton = (form && form.querySelector)
        ? form.querySelector('button[type="submit"]')
        : document.querySelector('#formCertificados button[type="submit"]');
    const originalText = submitButton.innerHTML;
    submitButton.innerHTML = '<i class="bi bi-hourglass-split"></i> Subiendo archivos...';
    submitButton.disabled = true;

    try {
        // Para FormData, solo incluir Authorization si hay token
        const headers = getHeaders(false); // false = no incluir Content-Type
        const fetchOptions = {
            method: 'POST',
            body: formData
        };

        // Solo agregar headers si hay token (getHeaders incluirá Authorization automáticamente)
        if (Object.keys(headers).length > 0) {
            fetchOptions.headers = headers;
        }

        const response = await fetch(`${API_BASE_URL}/configuracion-institucional/certificados`, fetchOptions);

        if (response.ok) {
            const resultado = await response.json();

            // Mostrar información de los archivos subidos
            document.getElementById('certificadoStatus').innerHTML = `
                <div class="alert alert-success alert-sm">
                    <i class="bi bi-check-circle"></i> ${resultado.certificadoFilename}
                    (${(resultado.certificadoSize / 1024).toFixed(2)} KB)
                </div>
            `;

            document.getElementById('llaveStatus').innerHTML = `
                <div class="alert alert-success alert-sm">
                    <i class="bi bi-check-circle"></i> ${resultado.llavePrivadaFilename}
                    (${(resultado.llavePrivadaSize / 1024).toFixed(2)} KB)
                </div>
            `;

            // Recargar configuración
            await cargarConfiguracionInstitucional();

            // Actualizar panel de estado
            await cargarEstadoCertificados();

            mostrarAlerta('alertaCertificados', 'success', resultado.mensaje || 'Certificados guardados exitosamente');

            // Limpiar formulario
            document.getElementById('formCertificados').reset();
            document.getElementById('passwordLlave').value = '';
        } else {
            const error = await response.json();
            throw new Error(error.error || 'Error al guardar certificados');
        }
    } catch (error) {
        console.error('Error:', error);
        mostrarAlerta('alertaCertificados', 'danger', 'Error al guardar certificados: ' + error.message);
    } finally {
        // Restaurar botón
        submitButton.innerHTML = originalText;
        submitButton.disabled = false;
    }
}

// ==================== PLANTEL REGISTRADO ====================

function escapeHtmlPlantel(value) {
    return String(value || '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

async function cargarPlantelesConfig() {
    const tbody = document.getElementById('plantelesTableBody');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="7" class="text-center text-muted py-3"><span class="spinner-border spinner-border-sm"></span> Cargando…</td></tr>';
    try {
        const response = await fetch(`${API_BASE_URL}/planteles`, { method: 'GET', headers: getHeaders() });
        if (response.status === 401 || response.status === 403) {
            // Token presente pero inválido/expirado (pasa después de un reset total).
            try {
                localStorage.removeItem('token');
                localStorage.removeItem('refreshToken');
            } catch (_) {}
            tbody.innerHTML = '<tr><td colspan="7" class="text-center text-danger py-2">Sesión expirada. Vuelve a iniciar sesión.</td></tr>';
            setTimeout(function () { window.location.href = '../index.html'; }, 800);
            return;
        }
        if (!response.ok) {
            const t = await response.text().catch(() => '');
            throw new Error(t || ('Error al cargar planteles (' + response.status + ')'));
        }
        plantelesCatalog = await response.json().catch(() => []);
        renderizarTablaPlantelesConfig();
        // La configuración institucional se deriva del registro de plantel (ya no hay tabla/form de institución).
        await asegurarConfiguracionInstitucionalDesdePlantel();
    } catch (error) {
        console.error('Error al cargar planteles:', error);
        plantelesCatalog = [];
        tbody.innerHTML = '<tr><td colspan="7" class="text-center text-danger py-2">No se pudo cargar el catálogo.</td></tr>';
        await asegurarConfiguracionInstitucionalDesdePlantel();
    }
}

async function asegurarConfiguracionInstitucionalDesdePlantel() {
    try {
        if (!Array.isArray(plantelesCatalog) || plantelesCatalog.length === 0) {
            return;
        }

        // Elegir plantel base: si existe configActual y coincide por cveInstitucion/idPlantel, mantener; si no, tomar el primero válido.
        const target = (configuracionActual && configuracionActual.cveInstitucion) ? String(configuracionActual.cveInstitucion).trim() : '';
        let base = null;
        if (target) {
            base = (plantelesCatalog || []).find(function (p) { return p && String(p.idPlantel || '').trim() === target; }) || null;
        }
        if (!base) {
            base = (plantelesCatalog || []).find(function (p) { return p && p.idPlantel && String(p.idPlantel).trim(); }) || null;
        }
        if (!base) return;

        // Requiere entidad federativa para documentos
        if (!base.idEntidadFederativa || !base.entidadFederativa) {
            return;
        }

        const datos = {
            cveInstitucion: String(base.idPlantel || '').trim(),
            nombreInstitucion: String(base.nombrePlantel || '').trim(),
            nombreCorto: (base.nombreCorto != null && String(base.nombreCorto).trim())
                ? String(base.nombreCorto).trim().toUpperCase()
                : null,
            idEntidadFederativa: String(base.idEntidadFederativa || '').trim(),
            entidadFederativa: String(base.entidadFederativa || '').trim(),
            idCampus: (base.claveDgp != null && String(base.claveDgp).trim()) ? String(base.claveDgp).trim() : null,
            campus: (base.campus != null && String(base.campus).trim()) ? String(base.campus).trim() : null,
            activo: true
        };

        // Preservar rutas/credenciales FIEL si ya existía config
        if (configuracionActual) {
            datos.certificadoPath = configuracionActual.certificadoPath;
            datos.llavePrivadaPath = configuracionActual.llavePrivadaPath;
            datos.passwordLlavePrivada = configuracionActual.passwordLlavePrivada;
        }

        const id = configuracionActual && configuracionActual.id ? configuracionActual.id : null;
        const url = id ? `${API_BASE_URL}/configuracion-institucional/${id}` : `${API_BASE_URL}/configuracion-institucional`;
        const method = id ? 'PUT' : 'POST';

        const response = await fetch(url, {
            method: method,
            headers: getHeaders(),
            body: JSON.stringify(datos)
        });
        if (response.ok) {
            configuracionActual = await response.json();
        }
    } catch (e) {
        // Silencioso: la pantalla puede operar el registro de plantel aunque no se pueda guardar config aquí.
        console.warn('No se pudo asegurar configuración institucional desde plantel:', e);
    }
}

function poblarSelectPlantelBaseInstitucion() {
    const sel = document.getElementById('institucionPlantelBase');
    if (!sel) return;
    const current = (sel.value || '').trim();
    sel.innerHTML = '<option value="">Seleccione un plantel…</option>';
    (plantelesCatalog || [])
        .filter(function (p) { return p && p.idPlantel && String(p.idPlantel).trim(); })
        .slice()
        .sort(function (a, b) {
            const na = (a.nombrePlantel || a.idPlantel || '').toString();
            const nb = (b.nombrePlantel || b.idPlantel || '').toString();
            return na.localeCompare(nb, 'es');
        })
        .forEach(function (p) {
            const opt = document.createElement('option');
            opt.value = String(p.idPlantel).trim();
            opt.textContent = (p.idPlantel ? String(p.idPlantel).trim() + ' - ' : '') + (p.nombrePlantel || p.claveDgp || 'Plantel');
            sel.appendChild(opt);
        });
    if (current) sel.value = current;
    sel.onchange = aplicarPlantelBaseAConfiguracion;

    // Si hay configuración cargada y no hay selección, intentar sincronizar.
    if (!sel.value && configuracionActual && configuracionActual.cveInstitucion) {
        sel.value = String(configuracionActual.cveInstitucion).trim();
    }
    // Si aún no hay, tomar el primero
    if (!sel.value) {
        const first = (plantelesCatalog || []).find(function (p) { return p && p.idPlantel && String(p.idPlantel).trim(); });
        if (first) sel.value = String(first.idPlantel).trim();
    }
    aplicarPlantelBaseAConfiguracion();
}

function renderizarTablaPlantelesConfig() {
    const tbody = document.getElementById('plantelesTableBody');
    if (!tbody) return;
    if (!Array.isArray(plantelesCatalog) || plantelesCatalog.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-3">No hay planteles registrados. Agregue uno arriba.</td></tr>';
        return;
    }
    tbody.innerHTML = plantelesCatalog.map(p => `
        <tr data-plantel-id="${p.id}">
            <td>${escapeHtmlPlantel(p.claveDgair || p.idPlantel)}</td>
            <td>${escapeHtmlPlantel(p.claveDgp)}</td>
            <td>${escapeHtmlPlantel(p.claveCct)}</td>
            <td>${escapeHtmlPlantel(p.idEntidadFederativa ? (String(p.idEntidadFederativa).padStart(2,'0') + ' - ' + (p.entidadFederativa || '')) : (p.entidadFederativa || ''))}</td>
            <td><span class="fw-semibold">${escapeHtmlPlantel(p.nombrePlantel)}</span></td>
            <td class="text-end text-nowrap">
                <div class="btn-group btn-group-sm" role="group">
                    <button type="button" class="btn btn-outline-secondary" data-action="edit-plantel" title="Editar"><i class="bi bi-pencil"></i></button>
                    <button type="button" class="btn btn-outline-danger" data-action="delete-plantel" title="Eliminar"><i class="bi bi-trash"></i></button>
                </div>
            </td>
        </tr>
    `).join('');
}

function limpiarFormularioPlantelConfig() {
    plantelEditando = null;
    const plantelId = document.getElementById('plantelId');
    if (plantelId) plantelId.value = '';
    const plantelClaveDgp = document.getElementById('plantelClaveDgp');
    if (plantelClaveDgp) plantelClaveDgp.value = '';
    const plantelClaveCct = document.getElementById('plantelClaveCct');
    if (plantelClaveCct) plantelClaveCct.value = '';
    const plantelClaveDgair = document.getElementById('plantelClaveDgair');
    if (plantelClaveDgair) plantelClaveDgair.value = '';
    const plantelCampus = document.getElementById('plantelCampus');
    if (plantelCampus) plantelCampus.value = '';
    const plantelNombreCorto = document.getElementById('plantelNombreCorto');
    if (plantelNombreCorto) plantelNombreCorto.value = '';
    const plantelNombre = document.getElementById('plantelNombre');
    if (plantelNombre) plantelNombre.value = '';
    const selEnt = document.getElementById('plantelEntidadFederativaSelect');
    if (selEnt) selEnt.value = '';
    const hidIdEnt = document.getElementById('plantelIdEntidadFederativa');
    if (hidIdEnt) hidIdEnt.value = '';
    const hidEnt = document.getElementById('plantelEntidadFederativa');
    if (hidEnt) hidEnt.value = '';
    const btnCancelar = document.getElementById('btnCancelarPlantel');
    if (btnCancelar) btnCancelar.classList.add('d-none');
    const btnGuardar = document.getElementById('btnGuardarPlantel');
    if (btnGuardar) btnGuardar.textContent = 'Guardar';
}

async function guardarPlantelConfig() {
    const btnGuardar = document.getElementById('btnGuardarPlantel');
    if (btnGuardar) {
        btnGuardar.disabled = true;
        btnGuardar.textContent = 'Guardando…';
    }
    const claveDgp = document.getElementById('plantelClaveDgp') ? document.getElementById('plantelClaveDgp').value.trim() : '';
    sincronizarIdEntidadDesdeEntidadPlantel();
    const idEntFed = document.getElementById('plantelIdEntidadFederativa') ? document.getElementById('plantelIdEntidadFederativa').value.trim() : '';
    const entFed = document.getElementById('plantelEntidadFederativa') ? document.getElementById('plantelEntidadFederativa').value.trim() : '';
    const claveCctInput = document.getElementById('plantelClaveCct');
    const claveDgairInput = document.getElementById('plantelClaveDgair');
    const claveCct = claveCctInput ? claveCctInput.value.trim() : '';
    const claveDgair = claveDgairInput ? claveDgairInput.value.trim() : '';
    // Clave institución para XML ahora se toma de Clave DGAIR
    const idPlantel = claveDgair;
    const campus = document.getElementById('plantelCampus') ? document.getElementById('plantelCampus').value.trim() : '';
    const nombreCorto = document.getElementById('plantelNombreCorto') ? document.getElementById('plantelNombreCorto').value.trim() : '';
    const nombrePlantel = document.getElementById('plantelNombre') ? document.getElementById('plantelNombre').value.trim() : '';

    if (!claveDgp) {
        alert('Indique la clave DGP.');
        if (btnGuardar) { btnGuardar.disabled = false; btnGuardar.textContent = plantelEditando ? 'Actualizar' : 'Guardar'; }
        return;
    }
    if (!idEntFed || !entFed) {
        alert('Seleccione la entidad federativa del plantel.');
        if (btnGuardar) { btnGuardar.disabled = false; btnGuardar.textContent = plantelEditando ? 'Actualizar' : 'Guardar'; }
        return;
    }
    if (!claveCct) {
        alert('Indique la clave CCT.');
        if (btnGuardar) { btnGuardar.disabled = false; btnGuardar.textContent = plantelEditando ? 'Actualizar' : 'Guardar'; }
        return;
    }
    if (!/^[A-Za-z0-9]{10}$/.test(claveCct)) {
        alert('La clave CCT debe tener exactamente 10 caracteres alfanuméricos sin espacios.');
        if (btnGuardar) { btnGuardar.disabled = false; btnGuardar.textContent = plantelEditando ? 'Actualizar' : 'Guardar'; }
        return;
    }
    if (!claveDgair) {
        alert('Indique la clave DGAIR.');
        if (btnGuardar) { btnGuardar.disabled = false; btnGuardar.textContent = plantelEditando ? 'Actualizar' : 'Guardar'; }
        return;
    }
    if (!/^[A-Za-z0-9]{1,20}$/.test(claveDgair)) {
        alert('La clave DGAIR debe tener hasta 20 caracteres alfanuméricos sin espacios.');
        if (btnGuardar) { btnGuardar.disabled = false; btnGuardar.textContent = plantelEditando ? 'Actualizar' : 'Guardar'; }
        return;
    }
    if (!nombrePlantel) {
        alert('Indique el nombre del plantel.');
        if (btnGuardar) { btnGuardar.disabled = false; btnGuardar.textContent = plantelEditando ? 'Actualizar' : 'Guardar'; }
        return;
    }
    if (!nombreCorto) {
        alert('Indique el nombre corto.');
        if (btnGuardar) { btnGuardar.disabled = false; btnGuardar.textContent = plantelEditando ? 'Actualizar' : 'Guardar'; }
        return;
    }
    if (!campus) {
        alert('Indique el campus.');
        if (btnGuardar) { btnGuardar.disabled = false; btnGuardar.textContent = plantelEditando ? 'Actualizar' : 'Guardar'; }
        return;
    }

    const id = document.getElementById('plantelId') ? document.getElementById('plantelId').value : '';
    const url = id ? `${API_BASE_URL}/planteles/${id}` : `${API_BASE_URL}/planteles`;
    const method = id ? 'PUT' : 'POST';
    try {
        const response = await fetch(url, {
            method,
            headers: getHeaders(),
            body: JSON.stringify({ idPlantel: idPlantel || null, claveDgp, idEntidadFederativa: idEntFed, entidadFederativa: entFed, claveCct, claveDgair, nombreCorto, campus, nombrePlantel })
        });
        if (response.status === 401 || response.status === 403) {
            try {
                localStorage.removeItem('token');
                localStorage.removeItem('refreshToken');
            } catch (_) {}
            throw new Error('Sesión expirada. Vuelve a iniciar sesión.');
        }
        let data = null;
        try { data = await response.json(); } catch (_) { data = null; }
        if (!response.ok) {
            let msg = 'Error al guardar';
            if (data !== null && data !== undefined) {
                if (typeof data === 'string') msg = data;
                else if (typeof data === 'object') msg = data.message || data.error || msg;
            }
            throw new Error(msg);
        }
        limpiarFormularioPlantelConfig();
        await cargarPlantelesConfig();
        // Mantener config institucional alineada al registro de plantel
        await asegurarConfiguracionInstitucionalDesdePlantel();
        alert(id ? 'Plantel actualizado.' : 'Plantel registrado.');
    } catch (error) {
        alert(error.message || 'Error al guardar');
    } finally {
        if (btnGuardar) {
            btnGuardar.disabled = false;
            btnGuardar.textContent = plantelEditando ? 'Actualizar' : 'Guardar';
        }
    }
}

function editarPlantelConfig(id) {
    const p = plantelesCatalog.find(x => x.id === id);
    if (!p) return;
    plantelEditando = p;
    const plantelId = document.getElementById('plantelId');
    if (plantelId) plantelId.value = p.id;
    const plantelClaveDgp = document.getElementById('plantelClaveDgp');
    if (plantelClaveDgp) plantelClaveDgp.value = p.claveDgp || '';
    const plantelClaveCct = document.getElementById('plantelClaveCct');
    if (plantelClaveCct) plantelClaveCct.value = p.claveCct || '';
    const plantelClaveDgair = document.getElementById('plantelClaveDgair');
    if (plantelClaveDgair) plantelClaveDgair.value = p.claveDgair || '';
    const plantelCampus = document.getElementById('plantelCampus');
    if (plantelCampus) plantelCampus.value = p.campus || '';
    const plantelNombreCorto = document.getElementById('plantelNombreCorto');
    if (plantelNombreCorto) plantelNombreCorto.value = p.nombreCorto || '';
    const plantelNombre = document.getElementById('plantelNombre');
    if (plantelNombre) plantelNombre.value = p.nombrePlantel || '';
    const selEnt = document.getElementById('plantelEntidadFederativaSelect');
    if (selEnt) selEnt.value = p.idEntidadFederativa ? String(p.idEntidadFederativa).toString().padStart(2,'0') : '';
    const hidIdEnt = document.getElementById('plantelIdEntidadFederativa');
    if (hidIdEnt) hidIdEnt.value = p.idEntidadFederativa || '';
    const hidEnt = document.getElementById('plantelEntidadFederativa');
    if (hidEnt) hidEnt.value = p.entidadFederativa || '';
    const btnGuardar = document.getElementById('btnGuardarPlantel');
    if (btnGuardar) btnGuardar.textContent = 'Actualizar';
    const btnCancelar = document.getElementById('btnCancelarPlantel');
    if (btnCancelar) btnCancelar.classList.remove('d-none');
}

async function eliminarPlantelConfig(id) {
    if (!confirm('¿Eliminar este plantel del catálogo? Los programas que lo usen seguirán mostrando la clave.')) return;
    try {
        const response = await fetch(`${API_BASE_URL}/planteles/${id}`, { method: 'DELETE', headers: getHeaders(false) });
        if (!response.ok) throw new Error('Error al eliminar');
        await cargarPlantelesConfig();
        alert('Eliminado del catálogo.');
    } catch (error) {
        alert(error.message || 'Error al eliminar');
    }
}

// ==================== UTILIDADES ====================

function mostrarAlerta(elementId, tipo, mensaje) {
    const alerta = document.getElementById(elementId);
    alerta.className = `alert alert-${tipo}`;
    alerta.textContent = mensaje;
    alerta.classList.remove('d-none');

    // Auto-ocultar después de 5 segundos
    setTimeout(() => ocultarAlerta(elementId), 5000);
}

function ocultarAlerta(elementId) {
    const alerta = document.getElementById(elementId);
    alerta.classList.add('d-none');
}
