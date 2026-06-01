/**
 * Personal institucional unificado: misma ficha para administrativos y docentes,
 * varios roles por usuario (incl. MAESTRO). Lista vía GET /personal/staff.
 */

var staffList = [];
var staffEditandoPersonalId = null;
var staffEditandoLegacyMaestroId = null;
/** PATCH roles pendiente cuando se debe pedir datos de estudiante antes de enviar. */
var staffRolesPatchPendiente = { url: '', roles: [] };
var staffRolesCoordPatchPendiente = { url: '', roles: [] };
// Cache de blobs para fotos de perfil (evita refetch en repintados)
var staffFotoBlobUrlBySrc = {};
// Ordenamiento por nombre (lista)
var staffSortNombreDir = 'asc'; // 'asc' | 'desc'

// Paginación (cliente)
var staffPageSize = 20;
var staffPageIndex = 1; // 1-based
var staffUltimoFiltrado = []; // lista filtrada+ordenada (sin paginar)

// Mantener página/posición de lista al volver de edición/roles/guardado
var staffListaUiSnapshot = null; // { pageIndex:number, scrollY:number }

function staffCapturarSnapshotListaUi() {
    staffListaUiSnapshot = {
        pageIndex: staffPageIndex || 1,
        scrollY: (typeof window !== 'undefined' && window.scrollY != null) ? window.scrollY : 0
    };
}

function staffRestaurarSnapshotListaUi() {
    if (!staffListaUiSnapshot) return;
    var snap = staffListaUiSnapshot;
    staffListaUiSnapshot = null;
    var p = snap.pageIndex || 1;
    // Restaurar página primero (render)
    try { staffSetPagina(p); } catch (_) { /* ignore */ }
    // Restaurar scroll
    try {
        if (typeof window !== 'undefined' && window.scrollTo) {
            window.scrollTo({ top: snap.scrollY || 0, behavior: 'auto' });
        }
    } catch (_) { /* ignore */ }
}

// Selección múltiple en tabla (eliminación masiva)
var staffSelectedRowKeys = {}; // { key: true }

// Cambios pendientes en vista de expediente (no definitivos hasta guardar)
var staffPendienteEliminarDocsBasicos = {}; // { CURP_ARCHIVO: true, INE: true, CSF: true }
var expedienteAlumnoPendienteEliminarDocs = {}; // { ACTA_NACIMIENTO: true, ... }
// Expediente alumno (modal): no persistir cambios hasta "Guardar documentos"
var expedienteAlumnoPendienteTitulosCedulaBySlot = {}; // { 1: { file?: File, etiqueta: string, numero: string, docId?: number } }
var expedienteAlumnoPendienteEliminarDocIds = {}; // { docId: true }
var expedienteAlumnoDirty = false;
var expedienteAlumnoAllowHideOnce = false;
var expedienteTituloCedulaFilasVisibles = 1;
var staffAluTituloCedulaFilasVisibles = 1;
var EXPEDIENTE_MAX_TITULOS_CEDULA = 4;
var modalAlumnoPendienteEliminarDocs = {}; // { ACTA_NACIMIENTO: true, ... } para modal de asignación rol alumno
// Archivos seleccionados en el modal de estudiante para documentos básicos (se pasan al expediente general al continuar).
var modalAlumnoDocsBasicosSeleccionados = {}; // { CURP_ARCHIVO: File, INE: File, CSF: File }
/** true: expediente abierto solo desde el icono de carpeta (no mostrar "Volver a roles"). */
var staffExpedienteSoloDesdeCarpeta = false;

/**
 * Roles elegidos en el modal que aún no se han persistido (paso 2 expediente).
 * Mientras exista, {@link staffRolesActualesDesdeFormulario} los usa para mostrar secciones.
 */
var staffRolesModalPendientesEnFormulario = null;

/**
 * Asignación de roles diferida hasta guardar expediente: { roles, programaCoordinadoId? }.
 */
var staffRolesCommitPendiente = null;
/** Selección de programa pendiente tras guardar expediente (Coord. Acad.). */
var staffRolesCoordCommitDespuesDeExpediente = null; // { urlRoles, roles }

/** Máximo de cédulas profesionales por expediente (carga y guardado). */
var STAFF_MAX_CEDULAS = 8;

// Estudiante: cambios pendientes en documentos del alumno dentro del expediente institucional
var staffAlumnoPendienteEliminarDocs = {}; // { ACTA_NACIMIENTO: true, ... }

function expedienteAlumnoMarcarDirty() {
    expedienteAlumnoDirty = true;
}

function expedienteAlumnoResetPendientes() {
    expedienteAlumnoPendienteEliminarDocs = {};
    expedienteAlumnoPendienteTitulosCedulaBySlot = {};
    expedienteAlumnoPendienteEliminarDocIds = {};
    expedienteTituloCedulaFilasVisibles = 1;
    expedienteAlumnoDirty = false;
    expedienteAlumnoAllowHideOnce = false;
}

function expedienteAlumnoTieneCambiosPendientes() {
    if (!expedienteAlumnoDirty) return false;
    if (Object.keys(expedienteAlumnoPendienteEliminarDocs || {}).length) return true;
    if (Object.keys(expedienteAlumnoPendienteEliminarDocIds || {}).length) return true;
    if (Object.keys(expedienteAlumnoPendienteTitulosCedulaBySlot || {}).length) return true;
    var ids = ['expDocActa', 'expDocCertEstudios', 'expDocCurp', 'expDocIne', 'expDocConstanciaFiscal'];
    for (var i = 0; i < ids.length; i++) {
        var inp = document.getElementById(ids[i]);
        if (inp && inp.files && inp.files.length) return true;
    }
    return true;
}

function staffContarFilasCedula() {
    return document.querySelectorAll('#staffCedulasContainer .staff-cedula-row').length;
}

function staffActualizarEstadoBotonAddCedula() {
    var btn = document.getElementById('btnAddCedulaStaff');
    if (!btn) return;
    var n = staffContarFilasCedula();
    var maxed = n >= STAFF_MAX_CEDULAS;
    btn.disabled = maxed;
    btn.title = maxed ? ('Máximo ' + STAFF_MAX_CEDULAS + ' cédulas') : 'Añadir otra cédula';
}

function staffHeaders() {
    var h = { 'Content-Type': 'application/json' };
    var t = localStorage.getItem('token');
    if (t && t !== 'null') h['Authorization'] = 'Bearer ' + t;
    return h;
}

// Headers JSON para endpoints que consumen application/json
function staffHeadersJson() {
    return staffHeaders();
}

function staffHeadersNoJson() {
    var h = {};
    var t = localStorage.getItem('token');
    if (t && t !== 'null') h['Authorization'] = 'Bearer ' + t;
    return h;
}

function staffSafeUploadFilename(file, fallback) {
    var name = file && file.name ? String(file.name) : String(fallback || 'documento.pdf');
    try {
        name = name.normalize('NFD').replace(/[\u0300-\u036f]/g, '');
    } catch (_) {}
    name = name.replace(/[\\\/\r\n\t]+/g, '_').replace(/[^A-Za-z0-9._ -]/g, '_').replace(/\s+/g, ' ').trim();
    return name || String(fallback || 'documento.pdf');
}

function staffHeaderText(value) {
    var s = value != null ? String(value).trim() : '';
    return s ? encodeURIComponent(s) : '';
}

function staffFotoPlaceholderDataUri() {
    // SVG inline (no requiere assets). Fondo gris claro y silueta.
    var svg = [
        '<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 160 160">',
        '<rect width="160" height="160" rx="22" fill="#f1f3f5"/>',
        '<circle cx="80" cy="62" r="28" fill="#ced4da"/>',
        '<path d="M32 140c8-28 32-42 48-42s40 14 48 42" fill="#ced4da"/>',
        '</svg>'
    ].join('');
    return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
}

function staffFotoPerfilWrapCfg() {
    return { wrapId: 'staffFotoPerfilWrap', inputId: 'staffFotoPerfil' };
}

function staffInitFotoPerfilUi() {
    var cfg = staffFotoPerfilWrapCfg();
    var wrap = document.getElementById(cfg.wrapId);
    var inp = document.getElementById(cfg.inputId);
    if (!wrap || !inp) return;

    var existingSrc = wrap.getAttribute('data-existing-foto-src') || '';
    var renderedExisting = wrap.getAttribute('data-rendered-existing-foto-src') || '';

    // Si ya está mejorado, no duplicar (a menos que cambie la foto existente detectada)
    if (wrap.getAttribute('data-ui-ready') === '1' && renderedExisting === existingSrc) return;
    wrap.setAttribute('data-ui-ready', '1');
    wrap.setAttribute('data-rendered-existing-foto-src', existingSrc);

    // Reemplazar layout por input-group con X dentro del selector
    wrap.innerHTML = '';
    inp.classList.remove('d-none');

    // Si ya existe una foto guardada, mostrar indicador + botones Cambiar/Borrar
    if (existingSrc) {
        var existing = document.createElement('div');
        existing.className = 'd-flex align-items-center justify-content-between gap-2 border rounded px-2 py-2 bg-white mb-2';
        existing.innerHTML =
            '<div class="d-flex align-items-center gap-2" style="min-width:0;">' +
            '  <img class="staff-avatar-img staff-foto-open" data-foto-src="' + existingSrc + '" data-foto-loaded="0" alt="Foto" ' +
            '       src="' + staffFotoPlaceholderDataUri() + '" style="width:42px;height:42px;object-fit:cover;border-radius:10px;border:1px solid #e9ecef;" />' +
            '  <div class="small text-truncate" style="min-width:0;">' +
            '    <div class="text-muted">Foto de perfil</div>' +
            '    <strong>Registrada</strong>' +
            '  </div>' +
            '</div>' +
            '<div class="btn-group btn-group-sm" role="group">' +
            '  <button type="button" class="btn btn-outline-primary" data-action="cambiar" title="Cambiar foto"><i class="bi bi-upload"></i></button>' +
            '  <button type="button" class="btn btn-outline-danger" data-action="borrar" title="Borrar foto"><i class="bi bi-trash"></i></button>' +
            '</div>';
        wrap.appendChild(existing);
        var imgEl = existing.querySelector('img');
        staffCargarImgProtegidaEnEl(imgEl, existingSrc);

        // Por defecto, ocultar el selector para que no "vuelva a pedir" la foto si ya existe
        inp.classList.add('d-none');
        existing.querySelector('[data-action="cambiar"]').addEventListener('click', function () {
            inp.classList.remove('d-none');
            group.classList.remove('d-none');
            try { inp.scrollIntoView({ block: 'nearest' }); } catch (_) { }
        });
        existing.querySelector('[data-action="borrar"]').addEventListener('click', async function () {
            var pid = document.getElementById('staffPersonalId') && document.getElementById('staffPersonalId').value;
            if (!pid) return;
            var ok = false;
            if (typeof window.uiConfirm === 'function') {
                ok = await window.uiConfirm('¿Deseas borrar la foto de perfil? Esta acción no se puede deshacer.', {
                    title: 'Borrar foto',
                    subtitle: 'Se eliminará del expediente',
                    okText: 'Borrar',
                    cancelText: 'Cancelar'
                });
            }
            if (!ok) return;
            try {
                var r = await fetch(API_URL + '/personal/' + encodeURIComponent(String(pid)) + '/foto-perfil', {
                    method: 'DELETE',
                    headers: staffHeadersNoJson()
                });
                if (!r.ok) throw new Error('no-borra');
                // limpiar cache y refrescar UI
                delete staffFotoBlobUrlBySrc[existingSrc];
                var w = document.getElementById('staffFotoPerfilWrap');
                if (w) {
                    w.removeAttribute('data-existing-foto-src');
                    w.removeAttribute('data-ui-ready');
                }
                staffResetFotoPerfilUiSelection();
            } catch (_) {
                alert('No se pudo borrar la foto de perfil');
            }
        });
    }

    var group = document.createElement('div');
    group.className = 'input-group input-group-sm';
    wrap.appendChild(group);
    group.appendChild(inp);

    // Si ya hay foto registrada, no mostrar el selector hasta que se presione "Cambiar foto"
    if (existingSrc) {
        group.classList.add('d-none');
    }

    var btnClear = document.createElement('button');
    btnClear.type = 'button';
    btnClear.className = 'btn btn-outline-danger btn-sm';
    btnClear.title = 'Quitar selección';
    btnClear.innerHTML = '<i class="bi bi-x-lg"></i>';
    group.appendChild(btnClear);

    // Vista previa (thumbnail) cuando se selecciona una foto local (sin botón/enlace de vista previa)
    var preview = document.createElement('div');
    preview.className = 'mt-2 d-none';
    preview.innerHTML =
        '<div class="d-flex align-items-center gap-2">' +
        '  <img alt="Vista previa" style="width:42px;height:42px;object-fit:cover;border-radius:10px;border:1px solid #e9ecef;" />' +
        '  <span class="small text-muted">Vista previa</span>' +
        '</div>';
    wrap.appendChild(preview);
    var previewImg = preview.querySelector('img');
    var previewUrl = null;

    function sync() {
        var has = !!(inp.files && inp.files[0]);
        btnClear.disabled = !has;
        if (!has) {
            preview.classList.add('d-none');
            try {
                if (previewUrl && previewUrl.indexOf('blob:') === 0) URL.revokeObjectURL(previewUrl);
            } catch (_) { }
            previewUrl = null;
            if (previewImg) previewImg.removeAttribute('src');
            return;
        }
        try {
            if (previewUrl && previewUrl.indexOf('blob:') === 0) URL.revokeObjectURL(previewUrl);
        } catch (_) { }
        previewUrl = URL.createObjectURL(inp.files[0]);
        if (previewImg) previewImg.src = previewUrl;
        preview.classList.remove('d-none');
    }
    btnClear.addEventListener('click', function () {
        inp.value = '';
        sync();
    });
    inp.addEventListener('change', sync);
    sync();
}

function staffResetFotoPerfilUiSelection() {
    var inp = document.getElementById('staffFotoPerfil');
    var wrap = document.getElementById('staffFotoPerfilWrap');
    if (inp) inp.value = '';
    if (wrap) wrap.removeAttribute('data-ui-ready');
    staffInitFotoPerfilUi();
}

async function staffRefrescarFotoPerfilExistenteUi(personalId) {
    var wrap = document.getElementById('staffFotoPerfilWrap');
    if (!wrap) return;
    wrap.removeAttribute('data-existing-foto-src');
    if (!personalId) return;
    var src = API_URL + '/personal/' + encodeURIComponent(String(personalId)) + '/foto-perfil';
    try {
        var r = await fetch(src, { method: 'GET', headers: staffHeadersNoJson(), cache: 'no-store' });
        if (!r.ok) return;
        // No leer blob aquí: solo marcar que existe; la carga de imagen la hace staffInitFotoPerfilUi().
        wrap.setAttribute('data-existing-foto-src', src);
        // Forzar repintado si la UI ya estaba inicializada antes
        wrap.removeAttribute('data-ui-ready');
    } catch (_) { /* ignorar */ }
}

async function staffCargarImgProtegidaEnEl(imgEl, srcProtegida) {
    if (!imgEl || !srcProtegida) return;
    var src = String(srcProtegida);

    // Reusar si ya está en cache
    if (staffFotoBlobUrlBySrc[src]) {
        imgEl.src = staffFotoBlobUrlBySrc[src];
        imgEl.setAttribute('data-foto-loaded', '1');
        return;
    }
    // Evitar reintentos infinitos
    if (imgEl.getAttribute('data-foto-loaded') === '1') return;
    imgEl.setAttribute('data-foto-loaded', '1');

    try {
        var res = await fetch(src, {
            method: 'GET',
            headers: staffHeadersNoJson(),
            cache: 'no-store'
        });
        if (!res.ok) throw new Error('no-foto');
        var blob = await res.blob();
        if (!blob || blob.size === 0) throw new Error('no-foto');
        var url = URL.createObjectURL(blob);
        staffFotoBlobUrlBySrc[src] = url;
        imgEl.src = url;
    } catch (_) {
        imgEl.src = staffFotoPlaceholderDataUri();
    }
}

function staffCargarFotosTabla(tbodyEl) {
    if (!tbodyEl) return;
    var imgs = tbodyEl.querySelectorAll('img.staff-avatar-img[data-foto-src]');
    if (!imgs || !imgs.length) return;
    imgs.forEach(function (img) {
        var src = img.getAttribute('data-foto-src') || '';
        if (!src) return;
        staffCargarImgProtegidaEnEl(img, src);
    });
}

async function staffAbrirModalFotoPerfilDesdeSrc(srcProtegida) {
    var mel = document.getElementById('modalFotoPerfil');
    var img = document.getElementById('modalFotoPerfilImg');
    if (!mel || !img) return;

    // Limpiar/revocar blob anterior del modal
    try {
        var prev = img.getAttribute('data-blob-url');
        if (prev && prev.indexOf('blob:') === 0) URL.revokeObjectURL(prev);
    } catch (_) { }
    img.removeAttribute('data-blob-url');
    img.src = staffFotoPlaceholderDataUri();

    if (typeof bootstrap !== 'undefined' && bootstrap.Modal) {
        bootstrap.Modal.getOrCreateInstance(mel).show();
    }

    var src = String(srcProtegida || '');
    if (!src) return;
    try {
        var res = await fetch(src, { method: 'GET', headers: staffHeadersNoJson(), cache: 'no-store' });
        if (!res.ok) throw new Error('no-foto');
        var blob = await res.blob();
        if (!blob || blob.size === 0) throw new Error('no-foto');
        var url = URL.createObjectURL(blob);
        img.setAttribute('data-blob-url', url);
        img.src = url;
    } catch (_) {
        img.src = staffFotoPlaceholderDataUri();
    }
}

function puedeGestionarStaffUi() {
    if (typeof obtenerRolesActualesLista === 'function') {
        var r = obtenerRolesActualesLista();
        return r.indexOf('ADMIN') !== -1 || r.indexOf('SECRETARIA_ACADEMICA') !== -1;
    }
    var t = (localStorage.getItem('userTipo') || '').toUpperCase();
    return t === 'ADMIN' || t === 'SECRETARIA_ACADEMICA';
}

function obtenerRolesModalSeleccionados() {
    var sinRol = document.getElementById('modalRolSinRol');
    if (sinRol && sinRol.checked) {
        return ['SIN_ROL'];
    }
    var out = [];
    document.querySelectorAll('input.modal-staff-rol-chk:checked').forEach(function (el) {
        if (el.value && el.value !== 'SIN_ROL') out.push(el.value);
    });
    return out;
}

function aplicarRolesEnModal(roles) {
    var set = {};
    (roles || []).forEach(function (r) { if (r) set[String(r).toUpperCase()] = true; });
    var soloSinRol = set.SIN_ROL && Object.keys(set).length === 1;
    document.querySelectorAll('input.modal-staff-rol-chk').forEach(function (el) {
        var v = el.value;
        if (v === 'SIN_ROL') {
            el.checked = !!soloSinRol;
        } else {
            el.checked = !soloSinRol && !!set[v];
        }
    });
    staffActualizarBotonPaso1Roles();
}

function validarRfcStaffOpcional(raw) {
    var s = String(raw == null ? '' : raw).trim();
    if (!s) return null;
    if (s.length > 13 || s.length < 12) {
        return 'El RFC debe tener 12 o 13 caracteres, o dejarse vacío.';
    }
    return null;
}

function staffRolesActualesDesdeFormulario() {
    if (staffRolesModalPendientesEnFormulario && staffRolesModalPendientesEnFormulario.length) {
        return staffRolesModalPendientesEnFormulario.slice();
    }
    var pid = document.getElementById('staffPersonalId').value;
    if (!pid) {
        return ['SIN_ROL'];
    }
    var row = staffList.find(function (x) { return x.personalId === parseInt(pid, 10); });
    return (row && row.roles && row.roles.length) ? row.roles : ['SIN_ROL'];
}

function staffRolesOperativos(roles) {
    if (!roles || !roles.length) return false;
    return roles.some(function (r) { return r && r !== 'SIN_ROL'; });
}

function staffEsSoloSinRol(roles) {
    if (!roles || !roles.length) return true;
    return roles.length === 1 && roles[0] === 'SIN_ROL';
}

function staffActualizarBotonPaso1Roles() {
    var btn = document.getElementById('btnGuardarModalStaffRoles');
    if (!btn) return;
    var roles = obtenerRolesModalSeleccionados();
    btn.textContent = staffEsSoloSinRol(roles) ? 'Guardar rol' : 'Continuar al expediente';
}

function staffActualizarVolverRolesEnModalExpediente() {
    var v = document.getElementById('btnVolverModalStaffRolesPaso1');
    if (!v) return;
    if (staffExpedienteSoloDesdeCarpeta) v.classList.add('d-none');
    else v.classList.remove('d-none');
}

function staffTextoNoVacio(v) {
    var s = String(v == null ? '' : v).trim();
    return s ? s : '';
}

function staffLeerProgramasAlumnoNombresDesdeUi() {
    var cont = document.getElementById('staffAlumnoProgramasContainer');
    if (!cont) return [];
    var rows = cont.querySelectorAll('.staff-alumno-prog-row');
    var out = [];
    rows.forEach(function (row) {
        var sel = row.querySelector('.staff-alumno-prog-select');
        if (!sel) return;
        var txt = sel.options && sel.selectedIndex >= 0 ? (sel.options[sel.selectedIndex].text || '') : '';
        txt = staffTextoNoVacio(txt);
        if (!txt || txt === 'Seleccione…' || txt === 'Seleccione...' || txt === 'Selecciona…' || txt === 'Selecciona...') return;
        out.push(txt);
    });
    // únicos (case-insensitive)
    var seen = {};
    var uniq = [];
    out.forEach(function (s) {
        var k = s.toLowerCase();
        if (seen[k]) return;
        seen[k] = true;
        uniq.push(s);
    });
    return uniq;
}

function staffRenderResumenExpedienteModal() {
    var box = document.getElementById('modalStaffExpedienteResumen');
    if (!box) return;

    var nombre = staffTextoNoVacio([
        document.getElementById('staffNombre') && document.getElementById('staffNombre').value,
        document.getElementById('staffApellidoPaterno') && document.getElementById('staffApellidoPaterno').value,
        document.getElementById('staffApellidoMaterno') && document.getElementById('staffApellidoMaterno').value
    ].filter(Boolean).join(' '));

    var curp = staffTextoNoVacio(document.getElementById('staffCurp') && document.getElementById('staffCurp').value);
    var correo = staffTextoNoVacio(document.getElementById('staffCorreoInstitucional') && document.getElementById('staffCorreoInstitucional').value);
    var tel = staffTextoNoVacio(document.getElementById('staffTelefono') && document.getElementById('staffTelefono').value);
    var puesto = staffTextoNoVacio(document.getElementById('staffPuesto') && document.getElementById('staffPuesto').value);
    var depto = staffTextoNoVacio(document.getElementById('staffDepartamento') && document.getElementById('staffDepartamento').value);
    var activo = staffTextoNoVacio(document.getElementById('staffActivo') && document.getElementById('staffActivo').value);

    var roles = staffRolesActualesDesdeFormulario();
    var rolesOper = (roles || []).filter(function (r) { return r && r !== 'SIN_ROL'; });

    var mat = staffTextoNoVacio(document.getElementById('staffAlumnoMatricula') && document.getElementById('staffAlumnoMatricula').value);
    var programas = staffLeerProgramasAlumnoNombresDesdeUi();

    // Chips/pares: solo mostrar si hay valor
    var chips = [];
    function pushChip(label, val) {
        var v = staffTextoNoVacio(val);
        if (!v) return;
        chips.push('<span class="text-muted small">' + escapeStaff(label) + ':</span> <strong>' + escapeStaff(v) + '</strong>');
    }

    pushChip('CURP', curp);
    pushChip('Correo institucional', correo);
    pushChip('Teléfono', tel);
    pushChip('Puesto', puesto);
    pushChip('Departamento', depto);
    pushChip('Estatus', (activo === 'false' ? 'Inactivo' : (activo === 'true' ? 'Activo' : activo)));
    if (rolesOper.length) {
        chips.push('<span class="text-muted small">Roles:</span> ' + rolesOper.map(function (r) {
            return '<span class="badge bg-secondary-subtle text-secondary border border-secondary-subtle me-1">' + escapeStaff(staffEtiquetaRol(r)) + '</span>';
        }).join(''));
    }

    // Alumno: mostrar solo si hay contenido real
    var alumnoExtras = '';
    if (roles && roles.indexOf('ALUMNO') !== -1) {
        var extras = [];
        if (mat) extras.push('<div><span class="text-muted small">Matrícula:</span> <strong>' + escapeStaff(mat) + '</strong></div>');
        if (programas && programas.length) {
            extras.push('<div class="mt-1"><div class="text-muted small mb-1">Programas:</div>' +
                '<div class="d-flex flex-column gap-1">' +
                programas.map(function (s) { return '<div>' + escapeStaff(s) + '</div>'; }).join('') +
                '</div></div>');
        }
        if (extras.length) {
            alumnoExtras = '<div class="mt-2 pt-2 border-top">' + extras.join('') + '</div>';
        }
    }

    // Si no hay nada útil, ocultar
    var hayAlgo = !!(nombre || chips.length || alumnoExtras);
    if (!hayAlgo) {
        box.classList.add('d-none');
        box.innerHTML = '';
        return;
    }

    box.classList.remove('d-none');
    box.innerHTML =
        (nombre ? ('<div class="fw-semibold">' + escapeStaff(nombre) + '</div>') : '') +
        (chips.length ? ('<div class="mt-1">' + chips.join(' <span class="mx-2 text-muted">•</span> ') + '</div>') : '') +
        alumnoExtras;
}

function staffExtendedWrapperEl() {
    return document.getElementById('staffExtendedSectionsWrapper');
}

function staffSetModoConsultaExpediente(activado) {
    var form = document.getElementById('staffForm');
    if (!form) return;

    // En modo expediente: datos solo lectura; documentos sí editables
    var excepciones = new Set([
        'staffFotoPerfil',
        'staffDocCurp',
        'staffDocIne',
        'staffDocCsf'
    ]);

    var els = form.querySelectorAll('input, select, textarea, button');
    els.forEach(function (el) {
        if (!el || !el.id) return;
        if (excepciones.has(el.id)) return;
        // Botones de "Ver/Quitar" de documentos no usan id fijo: permitirlos siempre
        if (el.closest && el.closest('#staffSeccionDocsRegistro')) {
            // Dentro de documentación, dejar botones y file inputs; bloquear el resto (si hubiera)
            if (el.tagName === 'INPUT' && el.type === 'file') return;
            if (el.tagName === 'BUTTON') return;
        }
        if (activado) {
            // No deshabilitar botones de navegación del modal (están fuera del form)
            if (el.tagName === 'BUTTON') {
                el.disabled = true;
                return;
            }
            if (el.tagName === 'SELECT' || el.tagName === 'TEXTAREA') {
                el.disabled = true;
                return;
            }
            if (el.tagName === 'INPUT') {
                if (el.type === 'checkbox' || el.type === 'radio' || el.type === 'date') {
                    el.disabled = true;
                } else if (el.type === 'file') {
                    // Por seguridad: si hay file inputs fuera del bloque docs, no permitirlos
                    el.disabled = true;
                } else {
                    el.readOnly = true;
                }
                return;
            }
        } else {
            // Restablecer
            if (el.tagName === 'BUTTON') {
                el.disabled = false;
                return;
            }
            if (el.tagName === 'SELECT' || el.tagName === 'TEXTAREA') {
                el.disabled = false;
                return;
            }
            if (el.tagName === 'INPUT') {
                el.disabled = false;
                el.readOnly = false;
                return;
            }
        }
    });
}

function staffRestaurarSeccionesExpedienteEnFormulario() {
    staffSetModoConsultaExpediente(false);
    var wrap = staffExtendedWrapperEl();
    var hostForm = document.getElementById('staffExtendedSectionsHostInForm');
    if (!wrap || !hostForm) return;
    if (wrap.parentElement !== hostForm) {
        hostForm.appendChild(wrap);
    }
}

function staffMoverSeccionesExpedienteAlModal() {
    var wrap = staffExtendedWrapperEl();
    var hostModal = document.getElementById('modalStaffExpedienteHost');
    if (!wrap || !hostModal) return;
    hostModal.appendChild(wrap);
}

function modalStaffRolesResetPasosUi() {
    var p1 = document.getElementById('modalStaffRolesPaso1');
    var p2 = document.getElementById('modalStaffRolesPaso2');
    var intro = document.getElementById('modalStaffRolesPaso2Intro');
    var sinOp = document.getElementById('modalStaffRolesPaso2SinOperativo');
    var host = document.getElementById('modalStaffExpedienteHost');
    if (p1) p1.classList.remove('d-none');
    if (p2) p2.classList.add('d-none');
    if (intro) intro.classList.remove('d-none');
    if (sinOp) sinOp.classList.add('d-none');
    if (host) host.classList.remove('d-none');
    var tit = document.getElementById('modalStaffRolesLabel');
    if (tit) tit.textContent = 'Roles del usuario';
    var f1 = document.getElementById('modalStaffRolesFooterPaso1');
    var f2 = document.getElementById('modalStaffRolesFooterPaso2');
    if (f1) f1.classList.remove('d-none');
    if (f2) f2.classList.add('d-none');
    var btnG = document.getElementById('btnGuardarModalStaffExpediente');
    var btnC = document.getElementById('btnCerrarModalStaffRolesSoloSinOp');
    if (btnG) btnG.classList.remove('d-none');
    if (btnC) btnC.classList.add('d-none');
    staffActualizarVolverRolesEnModalExpediente();
}

function modalStaffRolesIrAPaso1() {
    staffExpedienteSoloDesdeCarpeta = false;
    staffRolesCommitPendiente = null;
    staffRolesModalPendientesEnFormulario = null;
    staffRolesPatchPendiente = { url: '', roles: [] };
    staffRolesCoordPatchPendiente = { url: '', roles: [] };
    staffRestaurarSeccionesExpedienteEnFormulario();
    modalStaffRolesResetPasosUi();
}

/**
 * @param {boolean} soloSinRolOperativo - solo SIN_ROL: mensaje breve sin formulario de expediente
 */
function modalStaffRolesMostrarPasoExpediente(soloSinRolOperativo) {
    var p1 = document.getElementById('modalStaffRolesPaso1');
    var p2 = document.getElementById('modalStaffRolesPaso2');
    var intro = document.getElementById('modalStaffRolesPaso2Intro');
    var sinOp = document.getElementById('modalStaffRolesPaso2SinOperativo');
    var host = document.getElementById('modalStaffExpedienteHost');
    var tit = document.getElementById('modalStaffRolesLabel');
    var f1 = document.getElementById('modalStaffRolesFooterPaso1');
    var f2 = document.getElementById('modalStaffRolesFooterPaso2');
    var btnG = document.getElementById('btnGuardarModalStaffExpediente');
    var btnC = document.getElementById('btnCerrarModalStaffRolesSoloSinOp');
    if (p1) p1.classList.add('d-none');
    if (p2) p2.classList.remove('d-none');
    if (tit) tit.textContent = 'Expediente institucional';
    if (f1) f1.classList.add('d-none');
    if (f2) f2.classList.remove('d-none');
    if (soloSinRolOperativo) {
        if (intro) intro.classList.add('d-none');
        if (host) host.classList.add('d-none');
        if (sinOp) sinOp.classList.remove('d-none');
        if (btnG) btnG.classList.add('d-none');
        if (btnC) btnC.classList.remove('d-none');
        var resumen = document.getElementById('modalStaffExpedienteResumen');
        if (resumen) { resumen.classList.add('d-none'); resumen.innerHTML = ''; }
        staffRestaurarSeccionesExpedienteEnFormulario();
    } else {
        if (intro) intro.classList.remove('d-none');
        if (host) host.classList.remove('d-none');
        if (sinOp) sinOp.classList.add('d-none');
        if (btnG) btnG.classList.remove('d-none');
        if (btnC) btnC.classList.add('d-none');
        staffMoverSeccionesExpedienteAlModal();
        // Tras asignar roles, el paso 2 de expediente es complemento: datos de registro en solo lectura, documentos editables
        if (!staffExpedienteSoloDesdeCarpeta) {
            staffSetModoConsultaExpediente(true);
        } else {
            staffSetModoConsultaExpediente(false);
        }
        staffRenderResumenExpedienteModal();
    }
    staffActualizarVolverRolesEnModalExpediente();
}

function staffMostrarFiscalLaboral(roles) {
    if (!roles || !roles.length) return false;
    var fiscal = ['ADMIN', 'SECRETARIA_ACADEMICA', 'SECRETARIA_ADMINISTRATIVA', 'COORDINADOR_ACADEMICO', 'MAESTRO'];
    return roles.some(function (r) { return fiscal.indexOf(r) !== -1; });
}

function staffMostrarAdminExpediente(roles) {
    if (!roles || !roles.length) return false;
    var admin = ['ADMIN', 'SECRETARIA_ACADEMICA', 'SECRETARIA_ADMINISTRATIVA', 'COORDINADOR_ACADEMICO'];
    return roles.some(function (r) { return admin.indexOf(r) !== -1; });
}

function staffMostrarAlumnoExpediente(roles) {
    if (!roles || !roles.length) return false;
    return roles.indexOf('ALUMNO') !== -1;
}

/**
 * Registro nuevo: solo básico + docs opcionales. Tras asignar roles (edición): bloques adicionales según perfil.
 */
function staffActualizarVisibilidadSeccionesFormulario(opts) {
    opts = opts || {};
    var pid = document.getElementById('staffPersonalId').value;
    var esNuevo = !pid;
    var legacy = document.getElementById('staffLegacyMaestroId') && document.getElementById('staffLegacyMaestroId').value;
    var roles = staffRolesActualesDesdeFormulario();
    var pwdWrap = document.getElementById('staffPasswordWrap');
    var docsReg = document.getElementById('staffSeccionDocsRegistro');
    var ext = document.getElementById('staffSeccionExtendida');
    var alu = document.getElementById('seccionAlumnoStaff');
    var fis = document.getElementById('staffBloqueFiscal');
    if (pwdWrap) pwdWrap.style.display = esNuevo ? '' : 'none';
    if (docsReg) {
        // Si el usuario tiene rol ALUMNO, los documentos se gestionan en "Documentos del estudiante"
        // (mismo expediente del alumno). Ocultar este bloque para evitar redundancia.
        if (staffMostrarAlumnoExpediente(roles)) {
            docsReg.style.display = 'none';
        } else {
        // Permitir ver/cargar documentación aunque el usuario esté SIN_ROL (expediente, edición de ficha, etc.)
        var forceDocs = !!opts.forceDocs;
        var docsSinRol = !esNuevo && !legacy && staffEsSoloSinRol(roles);
        docsReg.style.display = ((esNuevo && !legacy) || (!esNuevo && !legacy && (forceDocs || staffRolesOperativos(roles) || docsSinRol))) ? '' : 'none';
        }
    }
    var mostrarExt = (legacy && esNuevo) || (!esNuevo && !legacy && staffRolesOperativos(roles));
    if (ext) ext.style.display = (mostrarExt && staffMostrarAdminExpediente(roles)) ? '' : 'none';
    if (alu) alu.style.display = (mostrarExt && staffMostrarAlumnoExpediente(roles)) ? '' : 'none';
    if (fis) {
        if (legacy && esNuevo) fis.style.display = '';
        else fis.style.display = (mostrarExt && staffMostrarFiscalLaboral(roles)) ? '' : 'none';
    }
    var aluDocs = document.getElementById('staffAlumnoDocsBlock');
    if (aluDocs) {
        aluDocs.style.display = (mostrarExt && staffMostrarAlumnoExpediente(roles)) ? '' : 'none';
    }
    var cedBlk = document.getElementById('staffCedulasExpedienteBlock');
    if (cedBlk) {
        var rCed = staffRolesActualesDesdeFormulario();
        var mostrarCed = !legacy && staffRolesOperativos(rCed) && rCed.indexOf('MAESTRO') !== -1;
        cedBlk.style.display = mostrarCed ? '' : 'none';
    }
    staffActualizarVisibilidadDocencia();
}

/** Docencia: legado maestro o rol MAESTRO con ficha ya en modo extendido. */
function staffActualizarVisibilidadDocencia() {
    var doc = document.getElementById('seccionDocenciaStaff');
    if (!doc) return;
    var legacy = document.getElementById('staffLegacyMaestroId') && document.getElementById('staffLegacyMaestroId').value;
    var roles = staffRolesActualesDesdeFormulario();
    var mostrar = false;
    if (legacy) {
        mostrar = true;
    } else if (staffRolesOperativos(roles) && roles.indexOf('MAESTRO') !== -1) {
        mostrar = true;
    }
    doc.style.display = mostrar ? '' : 'none';
}

function normalizarTel10(v) {
    return String(v == null ? '' : v).replace(/\D/g, '').slice(0, 10);
}

function staffNormalizarFechaIsoYYYYMMDD(raw) {
    var s = String(raw == null ? '' : raw).trim();
    // En inputs type="date", el value debe ser YYYY-MM-DD, pero algunos navegadores permiten teclear raro.
    // Nos quedamos únicamente con dígitos y guiones y recortamos a longitud 10.
    s = s.replace(/[^\d-]/g, '').slice(0, 10);
    var m = s.match(/^(\d{4})-(\d{2})-(\d{2})$/);
    if (!m) return '';
    var y = parseInt(m[1], 10), mo = parseInt(m[2], 10), d = parseInt(m[3], 10);
    if (!(y >= 1900 && y <= 2100)) return '';
    if (!(mo >= 1 && mo <= 12)) return '';
    if (!(d >= 1 && d <= 31)) return '';
    return m[1] + '-' + m[2] + '-' + m[3];
}

/** Recorta espacios; conserva mayúsculas y minúsculas tal como se capturan. */
function staffTrimRegistro(s) {
    return String(s == null ? '' : s).trim();
}

function staffNormalizarNombreTituloLive(raw) {
    // Normaliza mayúsc/minúsc sin impedir escribir espacios (no trim ni colapsa espacios).
    var s = String(raw == null ? '' : raw);
    if (!s) return '';
    var out = '';
    var startWord = true;
    for (var i = 0; i < s.length; i++) {
        var ch = s.charAt(i);
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

function staffNormalizarNombreTituloFinal(raw) {
    // Para guardar/blur: recorta y colapsa espacios
    return staffNormalizarNombreTituloLive(raw).trim().replace(/\s+/g, ' ');
}

function staffNormalizarRfcUpper(raw) {
    var s = String(raw == null ? '' : raw).trim();
    if (!s) return '';
    return s.toUpperCase();
}

function staffNormalizarCurpUpperAlnum(raw) {
    // Solo A-Z y 0-9; sin espacios ni caracteres especiales
    var s = String(raw == null ? '' : raw).toUpperCase();
    s = s.replace(/[^A-Z0-9]/g, '');
    // CURP estándar: 18 caracteres
    if (s.length > 18) s = s.slice(0, 18);
    return s;
}

function staffTrimOpcional(s) {
    if (s == null || s === undefined) return null;
    var t = String(s).trim();
    return t === '' ? null : t;
}

function staffContadorTel(inpId, ctrId) {
    var inp = document.getElementById(inpId);
    var ctr = document.getElementById(ctrId);
    if (!inp || !ctr) return;
    ctr.textContent = (inp.value || '').length + '/10';
}

function staffObtenerLineasCedulaPayload() {
    var out = [];
    document.querySelectorAll('#staffCedulasContainer .staff-cedula-row').forEach(function (row) {
        var idAttr = row.getAttribute('data-cedula-id');
        var inpEt = row.querySelector('.staff-cedula-etiqueta');
        var inp = row.querySelector('.staff-cedula-numero');
        var et = inpEt ? staffTrimOpcional(inpEt.value) : null;
        var num = inp ? staffTrimRegistro(inp.value) : '';
        if (!num) return;
        var item = { numero: num };
        if (et) item.etiqueta = et;
        if (idAttr) item.id = parseInt(idAttr, 10);
        out.push(item);
    });
    return out;
}

function staffPrimeraCedulaNumero() {
    var lines = staffObtenerLineasCedulaPayload();
    return lines.length ? lines[0].numero : null;
}

function staffAppendCedulaRow(id, etiqueta, numero, filename) {
    var c = document.getElementById('staffCedulasContainer');
    if (!c) return;
    var wrap = document.createElement('div');
    wrap.className = 'staff-cedula-row border rounded p-2 bg-white';
    if (id) wrap.setAttribute('data-cedula-id', String(id));
    var row = document.createElement('div');
    row.className = 'row g-3 align-items-end';

    var colE = document.createElement('div');
    colE.className = 'col-12 col-md-4';
    var lblE = document.createElement('label');
    lblE.className = 'form-label small mb-1';
    lblE.textContent = 'Etiqueta del documento';
    colE.appendChild(lblE);
    var inpE = document.createElement('input');
    inpE.type = 'text';
    inpE.className = 'form-control form-control-sm staff-cedula-etiqueta';
    inpE.placeholder = 'Ej. Maestría en administración';
    inpE.value = etiqueta || '';
    colE.appendChild(inpE);

    var colN = document.createElement('div');
    colN.className = 'col-12 col-md-3';
    var lblN = document.createElement('label');
    lblN.className = 'form-label small mb-1';
    lblN.textContent = 'Número de cédula';
    colN.appendChild(lblN);
    var inpN = document.createElement('input');
    inpN.type = 'text';
    inpN.className = 'form-control form-control-sm staff-cedula-numero';
    inpN.value = numero || '';
    colN.appendChild(inpN);

    var colF = document.createElement('div');
    colF.className = 'col-12 col-md-4';
    var lblF = document.createElement('label');
    lblF.className = 'form-label small mb-1';
    lblF.textContent = 'Documento';
    colF.appendChild(lblF);
    var inpF = document.createElement('input');
    inpF.type = 'file';
    inpF.className = 'form-control form-control-sm staff-cedula-file-input';
    inpF.setAttribute('accept', '.pdf,.jpg,.jpeg,.png');
    // Contenedor para aplicar la misma UI que CURP/INE
    var fileWrap = document.createElement('div');
    fileWrap.className = 'staff-cedula-file-wrap';
    colF.appendChild(fileWrap);
    fileWrap.appendChild(inpF);

    var colR = document.createElement('div');
    colR.className = 'col-12 col-md-1 d-flex justify-content-md-end align-items-end';
    var btnRm = document.createElement('button');
    btnRm.type = 'button';
    btnRm.className = 'btn btn-sm btn-outline-danger staff-cedula-remove';
    btnRm.title = 'Eliminar fila';
    btnRm.setAttribute('aria-label', 'Eliminar fila');
    btnRm.innerHTML = '<i class="bi bi-trash"></i>';
    colR.appendChild(btnRm);

    row.appendChild(colE);
    row.appendChild(colN);
    row.appendChild(colF);
    row.appendChild(colR);
    wrap.appendChild(row);

    // La primera fila no debe poder eliminarse (solo limpiar).
    var esPrimeraFila = c.querySelectorAll('.staff-cedula-row').length === 0;
    if (esPrimeraFila) {
        btnRm.classList.add('d-none');
    }

    function setCedulaDocUi(metaFilename) {
        fileWrap.innerHTML = '';
        inpF.value = '';
        inpF.classList.remove('d-none');

        // Si hay archivo cargado en servidor, mostrar fila tipo CURP/INE
        if (metaFilename && id) {
            var rowDoc = document.createElement('div');
            rowDoc.className = 'd-flex align-items-center justify-content-between gap-2 border rounded px-2 py-2 bg-white';
            rowDoc.innerHTML =
                '<div class="small text-truncate" style="min-width:0;">' +
                '  <span class="text-muted">Documento:</span> ' +
                '  <strong>' + escapeStaff(metaFilename) + '</strong>' +
                '</div>' +
                '<div class="btn-group btn-group-sm" role="group">' +
                '  <button type="button" class="btn btn-outline-secondary" data-action="ver" title="Ver"><i class="bi bi-eye"></i></button>' +
                '  <button type="button" class="btn btn-outline-danger" data-action="quitar" title="Borrar"><i class="bi bi-trash"></i></button>' +
                '</div>';
            fileWrap.appendChild(rowDoc);
            inpF.classList.add('d-none');
            fileWrap.appendChild(inpF);

            rowDoc.querySelector('[data-action="ver"]').addEventListener('click', function () {
                var pid = document.getElementById('staffPersonalId').value;
                if (!pid) return;
                fetch(API_URL + '/personal/' + encodeURIComponent(pid) + '/cedulas-profesionales/' + encodeURIComponent(id) + '/archivo', { headers: staffHeadersNoJson() })
                    .then(function (r) {
                        if (!r.ok) throw new Error('No se pudo abrir');
                        return r.blob();
                    })
                    .then(function (b) {
                        var u = URL.createObjectURL(b);
                        window.open(u, '_blank');
                    })
                    .catch(function () { alert('No se pudo abrir el archivo'); });
            });

            rowDoc.querySelector('[data-action="quitar"]').addEventListener('click', async function () {
                var pid = document.getElementById('staffPersonalId').value;
                if (!pid) return;
                var ok = false;
                if (typeof window.uiConfirm === 'function') {
                    ok = await window.uiConfirm('¿Borrar el documento de esta cédula profesional?', {
                        title: 'Borrar documento',
                        subtitle: 'Esta acción no se puede deshacer',
                        okText: 'Borrar',
                        cancelText: 'Cancelar'
                    });
                }
                if (!ok) return;
                try {
                    var r = await fetch(API_URL + '/personal/' + encodeURIComponent(pid) + '/cedulas-profesionales/' + encodeURIComponent(id) + '/archivo', {
                        method: 'DELETE',
                        headers: staffHeadersNoJson()
                    });
                    var data = null;
                    try { data = await r.json(); } catch (_) { }
                    if (!r.ok) throw new Error((data && data.error) ? data.error : 'No se pudo borrar');
                    // Cambiar UI a "sin archivo"
                    setCedulaDocUi(null);
                } catch (e) {
                    alert(e.message || 'No se pudo borrar el documento');
                }
            });
            return;
        }

        // Sin archivo: input-group con botón X dentro (igual que docs básicos)
        var group = document.createElement('div');
        group.className = 'input-group input-group-sm';
        fileWrap.appendChild(group);
        group.appendChild(inpF);

        var btnClear = document.createElement('button');
        btnClear.type = 'button';
        btnClear.className = 'btn btn-outline-danger btn-sm';
        btnClear.title = 'Quitar selección';
        btnClear.innerHTML = '<i class="bi bi-x-lg"></i>';
        btnClear.disabled = true;
        group.appendChild(btnClear);

        function syncClearEnabled() {
            var has = !!(inpF.files && inpF.files[0]);
            btnClear.disabled = !has;
        }
        btnClear.addEventListener('click', function () {
            inpF.value = '';
            syncClearEnabled();
        });
        inpF.addEventListener('change', syncClearEnabled);
        syncClearEnabled();
    }

    setCedulaDocUi(filename || null);

    c.appendChild(wrap);
    staffActualizarEstadoBotonAddCedula();
    btnRm.addEventListener('click', function () {
        if (c.querySelectorAll('.staff-cedula-row').length <= 1) {
            inpE.value = '';
            inpN.value = '';
            inpF.value = '';
            staffActualizarEstadoBotonAddCedula();
            return;
        }
        wrap.remove();
        // Al remover, si queda una sola fila, ocultar su botón eliminar
        var rem = c.querySelector('.staff-cedula-row .staff-cedula-remove');
        if (rem && c.querySelectorAll('.staff-cedula-row').length === 1) {
            rem.classList.add('d-none');
        }
        staffActualizarEstadoBotonAddCedula();
    });
}

function staffRenderCedulasRows(cedulas) {
    var c = document.getElementById('staffCedulasContainer');
    if (!c) return;
    c.innerHTML = '';
    var list = cedulas && cedulas.length ? cedulas : [{ id: null, numero: '', filename: null }];
    if (list.length > STAFF_MAX_CEDULAS) {
        list = list.slice(0, STAFF_MAX_CEDULAS);
    }
    list.forEach(function (ced) {
        staffAppendCedulaRow(ced.id || null, ced.etiqueta || '', ced.numero || '', ced.filename || null);
    });
    // Asegurar: la primera fila nunca muestra botón eliminar
    var firstRemove = c.querySelector('.staff-cedula-row .staff-cedula-remove');
    if (firstRemove) firstRemove.classList.add('d-none');
    staffActualizarEstadoBotonAddCedula();
}

function staffNecesitaMultipartStaffPut() {
    var fCurp = document.getElementById('staffDocCurp');
    var fIne = document.getElementById('staffDocIne');
    var fCsf = document.getElementById('staffDocCsf');
    var fFoto = document.getElementById('staffFotoPerfil');
    if (fCurp && fCurp.files && fCurp.files[0]) return true;
    if (fIne && fIne.files && fIne.files[0]) return true;
    if (fCsf && fCsf.files && fCsf.files[0]) return true;
    if (fFoto && fFoto.files && fFoto.files[0]) return true;
    var rows = document.querySelectorAll('#staffCedulasContainer .staff-cedula-row');
    for (var i = 0; i < rows.length; i++) {
        if (rows[i].getAttribute('data-cedula-id')) continue;
        var fi = rows[i].querySelector('.staff-cedula-file-input');
        if (fi && fi.files && fi.files[0]) return true;
    }
    return false;
}

function construirPayloadStaff() {
    var apMat = (document.getElementById('staffApellidoMaterno').value || '').trim();
    if (!apMat) {
        alert('El apellido materno es obligatorio.');
        return null;
    }
    var pid = document.getElementById('staffPersonalId').value;
    var rfcRaw = staffNormalizarRfcUpper((document.getElementById('staffRfc') && document.getElementById('staffRfc').value || ''));
    var rfcEl = document.getElementById('staffRfc');
    if (rfcEl && rfcEl.value !== rfcRaw) rfcEl.value = rfcRaw;
    var errRfc = validarRfcStaffOpcional(rfcRaw);
    if (errRfc) {
        alert(errRfc);
        return null;
    }
    var tel = normalizarTel10(document.getElementById('staffTelefono').value);
    if (tel.length !== 10) {
        alert('El teléfono debe tener 10 dígitos.');
        return null;
    }
    var telERaw = (document.getElementById('staffContactoTelefono') && document.getElementById('staffContactoTelefono').value || '');
    var telE = normalizarTel10(telERaw);
    if (String(telERaw || '').trim() && telE.length !== 10) {
        alert('El teléfono de emergencia debe tener 10 dígitos.');
        return null;
    }
    var cnRaw = (document.getElementById('staffContactoNombre') && document.getElementById('staffContactoNombre').value || '');
    var cn = staffTrimOpcional(cnRaw);
    var genero = (document.getElementById('staffGenero') && document.getElementById('staffGenero').value || '').trim();
    if (!genero) {
        alert('Seleccione el género.');
        return null;
    }
    var fechaNacRaw = (document.getElementById('staffFechaNacimiento') && document.getElementById('staffFechaNacimiento').value || '').trim();
    var fechaNac = staffNormalizarFechaIsoYYYYMMDD(fechaNacRaw);
    if (!fechaNac) {
        alert('Indique la fecha de nacimiento con el calendario (formato válido).');
        return null;
    }
    var correoPers = staffTrimOpcional((document.getElementById('staffCorreoPersonal') && document.getElementById('staffCorreoPersonal').value || ''));
    var obs = (document.getElementById('staffObservaciones') || {}).value || '';
    var lineasCed = staffObtenerLineasCedulaPayload();
    if (lineasCed.length > STAFF_MAX_CEDULAS) {
        alert('Como máximo se permiten ' + STAFF_MAX_CEDULAS + ' cédulas profesionales en un expediente.');
        return null;
    }
    var payload = {
        curp: staffNormalizarCurpUpperAlnum(document.getElementById('staffCurp').value),
        nombre: staffNormalizarNombreTituloFinal(document.getElementById('staffNombre').value),
        apellidoPaterno: staffNormalizarNombreTituloFinal(document.getElementById('staffApellidoPaterno').value),
        apellidoMaterno: staffNormalizarNombreTituloFinal(apMat),
        etiqueta: (function () {
            var sel = document.getElementById('staffEtiquetaSelect');
            var ot = document.getElementById('staffEtiquetaOtro');
            if (!sel) return null;
            if (sel.value === 'otro') return staffTrimOpcional(ot && ot.value);
            return staffTrimOpcional(sel.value);
        })(),
        correoInstitucional: (document.getElementById('staffCorreoInstitucional').value || '').trim(),
        correoPersonal: correoPers,
        telefono: tel,
        codigoPostal: staffTrimOpcional(document.getElementById('staffCodigoPostal') && document.getElementById('staffCodigoPostal').value),
        sexo: genero,
        fechaNacimiento: fechaNac,
        gradoAcademico: (document.getElementById('staffGrado').value || '') || null,
        cedulaProfesional: staffPrimeraCedulaNumero(),
        cedulasProfesionales: lineasCed,
        puesto: null,
        departamento: staffTrimOpcional(document.getElementById('staffDepartamento').value),
        area: staffTrimOpcional(document.getElementById('staffArea').value),
        tipoMaestro: (document.getElementById('staffTipoMaestro').value || '') || null,
        rfc: rfcRaw ? staffTrimRegistro(rfcRaw) : null,
        regimenFiscal: (document.getElementById('staffRegimen').value || '') || null,
        activo: document.getElementById('staffActivo').value !== 'false',
        observaciones: staffTrimOpcional(obs),
        nombreContactoEmergencia: cn ? staffNormalizarNombreTituloFinal(cn) : null,
        telefonoContactoEmergencia: telE || null
    };
    var pw = (document.getElementById('staffPassword') && document.getElementById('staffPassword').value || '').trim();
    if (pw.length > 0 && pw.length < 6) {
        alert('La contraseña debe tener al menos 6 caracteres o déjela vacía para usar la predeterminada del sistema.');
        return null;
    }
    // En alta: si viene, se usa. En edición: si viene, se interpreta como restablecimiento.
    if (pw.length >= 6) {
        payload.password = pw;
    }
    return payload;
}

function staffBindNormalizacionesRegistro() {
    var curp = document.getElementById('staffCurp');
    if (curp && curp.getAttribute('data-curp-norm') !== '1') {
        curp.setAttribute('data-curp-norm', '1');
        curp.addEventListener('input', function () {
            var pos = curp.selectionStart;
            var v = staffNormalizarCurpUpperAlnum(curp.value);
            if (curp.value !== v) {
                curp.value = v;
                try { curp.setSelectionRange(pos, pos); } catch (_) {}
            }
        });
        curp.addEventListener('blur', function () {
            var v = staffNormalizarCurpUpperAlnum(curp.value);
            if (curp.value !== v) curp.value = v;
        });
    }

    var rfc = document.getElementById('staffRfc');
    if (rfc && rfc.getAttribute('data-norm') !== '1') {
        rfc.setAttribute('data-norm', '1');
        rfc.addEventListener('input', function () {
            var v = staffNormalizarRfcUpper(rfc.value);
            if (rfc.value !== v) rfc.value = v;
        });
        rfc.addEventListener('blur', function () {
            var v = staffNormalizarRfcUpper(rfc.value);
            if (rfc.value !== v) rfc.value = v;
        });
    }

    ['staffNombre', 'staffApellidoPaterno', 'staffApellidoMaterno', 'staffContactoNombre'].forEach(function (id) {
        var el = document.getElementById(id);
        if (!el || el.getAttribute('data-norm') === '1') return;
        el.setAttribute('data-norm', '1');
        el.addEventListener('input', function () {
            var pos = el.selectionStart;
            var v = staffNormalizarNombreTituloLive(el.value);
            if (el.value !== v) {
                el.value = v;
                try { el.setSelectionRange(pos, pos); } catch (_) {}
            }
        });
        el.addEventListener('blur', function () {
            var v = staffNormalizarNombreTituloFinal(el.value);
            if (el.value !== v) el.value = v;
        });
    });
}

async function cargarListaStaff(opts) {
    var tbody = document.getElementById('staffTableBody');
    if (!tbody) return;
    opts = opts || {};
    try {
        var res = await fetch(API_URL + '/personal/staff', { headers: staffHeadersNoJson() });
        if (res.status === 401) {
            if (typeof logout === 'function') logout();
            return;
        }
        if (!res.ok) throw new Error('Error al cargar personal');
        staffList = await res.json();
        staffPoblarFiltrosRolYPrograma(staffList);
        if (!opts.preservePage) staffPageIndex = 1;
        // Aplicar orden A→Z por defecto + paginar
        filtrarStaffTabla({ preservePage: !!opts.preservePage });
        if (opts.preserveScroll) {
            // esperar un tick para que el DOM pinte
            setTimeout(function () {
                try { staffRestaurarSnapshotListaUi(); } catch (_) {}
            }, 0);
        }
    } catch (e) {
        console.error(e);
        tbody.innerHTML = '<tr><td colspan="6" class="text-center text-danger py-4 align-middle">No se pudo cargar la lista</td></tr>';
    }
}

function staffTotalPaginas(total) {
    return Math.max(1, Math.ceil((total || 0) / staffPageSize));
}

function staffSetPagina(n) {
    var total = staffTotalPaginas(staffUltimoFiltrado.length);
    var p = parseInt(n, 10);
    if (isNaN(p) || p < 1) p = 1;
    if (p > total) p = total;
    staffPageIndex = p;
    staffRenderPagina();
}

function staffRenderPaginacion() {
    var ul = document.getElementById('staffPaginacion');
    var info = document.getElementById('staffPaginacionInfo');
    var total = staffUltimoFiltrado.length || 0;
    var totalPages = staffTotalPaginas(total);
    if (staffPageIndex > totalPages) staffPageIndex = totalPages;

    var from = total === 0 ? 0 : ((staffPageIndex - 1) * staffPageSize + 1);
    var to = Math.min(total, staffPageIndex * staffPageSize);
    if (info) {
        info.textContent = total === 0 ? 'Sin registros' : ('Mostrando ' + from + '–' + to + ' de ' + total);
    }
    if (!ul) return;
    if (totalPages <= 1) {
        ul.innerHTML = '';
        return;
    }
    function liBtn(label, page, disabled, active) {
        return '<li class="page-item' + (disabled ? ' disabled' : '') + (active ? ' active' : '') + '">' +
            '<button type="button" class="page-link" data-page="' + String(page) + '">' + label + '</button></li>';
    }
    var win = 2;
    var start = Math.max(1, staffPageIndex - win);
    var end = Math.min(totalPages, staffPageIndex + win);
    if (staffPageIndex <= 3) { start = 1; end = Math.min(totalPages, 5); }
    if (staffPageIndex >= totalPages - 2) { end = totalPages; start = Math.max(1, totalPages - 4); }

    var html = '';
    html += liBtn('«', 1, staffPageIndex === 1, false);
    html += liBtn('‹', staffPageIndex - 1, staffPageIndex === 1, false);
    if (start > 1) {
        html += liBtn('1', 1, false, staffPageIndex === 1);
        if (start > 2) html += '<li class="page-item disabled"><span class="page-link">…</span></li>';
    }
    for (var p = start; p <= end; p++) {
        html += liBtn(String(p), p, false, p === staffPageIndex);
    }
    if (end < totalPages) {
        if (end < totalPages - 1) html += '<li class="page-item disabled"><span class="page-link">…</span></li>';
        html += liBtn(String(totalPages), totalPages, false, staffPageIndex === totalPages);
    }
    html += liBtn('›', staffPageIndex + 1, staffPageIndex === totalPages, false);
    html += liBtn('»', totalPages, staffPageIndex === totalPages, false);
    ul.innerHTML = html;

    if (!ul.__bound) {
        ul.__bound = true;
        ul.addEventListener('click', function (ev) {
            var t = ev && ev.target ? ev.target : null;
            if (!t) return;
            var btn = t.matches && t.matches('button.page-link') ? t : (t.closest ? t.closest('button.page-link') : null);
            if (!btn) return;
            var pg = btn.getAttribute('data-page');
            if (!pg) return;
            staffSetPagina(pg);
        });
    }
}

function staffRenderPagina() {
    var total = staffUltimoFiltrado.length || 0;
    var totalPages = staffTotalPaginas(total);
    if (staffPageIndex > totalPages) staffPageIndex = totalPages;
    if (staffPageIndex < 1) staffPageIndex = 1;
    var start = (staffPageIndex - 1) * staffPageSize;
    var slice = staffUltimoFiltrado.slice(start, start + staffPageSize);
    renderStaffTable(slice);
    staffRenderPaginacion();
}

// Escape HTML para evitar XSS al inyectar strings en innerHTML.
function escapeHtml(s) {
    return String(s || '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

function escapeStaff(s) {
    return escapeHtml(s);
}

function staffEtiquetaRol(rol) {
    if (typeof etiquetaRolListaUsuarios === 'function') {
        return etiquetaRolListaUsuarios(rol);
    }
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

function staffNombreCompleto(row) {
    if (!row) return '';
    return [row.nombre, row.apellidoPaterno, row.apellidoMaterno].filter(Boolean).join(' ').trim();
}

function staffNormalizarTexto(s) {
    if (s == null) return '';
    try {
        return String(s).normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
    } catch (_) {
        return String(s).toLowerCase().trim();
    }
}

function staffActualizarIconoOrdenNombre() {
    var icon = document.getElementById('iconOrdenarStaffNombre');
    if (!icon) return;
    // Usar iconos bootstrap: asc=arrow-up, desc=arrow-down
    icon.className = 'bi ' + (staffSortNombreDir === 'asc' ? 'bi-arrow-up' : 'bi-arrow-down') + ' ms-1';
}

function staffPoblarFiltrosRolYPrograma(listaBase) {
    var selRol = document.getElementById('staffFiltroRol');
    var selProg = document.getElementById('staffFiltroPrograma');
    if (!selRol && !selProg) return;
    var lista = Array.isArray(listaBase) ? listaBase : [];

    if (selRol) {
        var prevRol = selRol.value || '';
        var roles = {};
        lista.forEach(function (r) {
            (r.roles || []).forEach(function (rol) {
                if (!rol) return;
                roles[String(rol)] = true;
            });
        });
        var keys = Object.keys(roles).sort(function (a, b) { return a.localeCompare(b, 'es'); });
        selRol.innerHTML = '<option value="">Rol</option>' + keys.map(function (k) {
            return '<option value="' + escapeStaff(k) + '">' + escapeStaff(staffEtiquetaRol(k)) + '</option>';
        }).join('');
        if (prevRol && keys.indexOf(prevRol) !== -1) selRol.value = prevRol;
    }

    if (selProg) {
        var prevProg = selProg.value || '';
        var progs = {};
        lista.forEach(function (r2) {
            if (r2.programaId != null && r2.programaNombre) {
                progs[String(r2.programaId)] = String(r2.programaNombre);
            }
        });
        var pkeys = Object.keys(progs).sort(function (a, b) {
            return String(progs[a]).localeCompare(String(progs[b]), 'es');
        });
        selProg.innerHTML = '<option value="">Programa académico</option>' + pkeys.map(function (id) {
            return '<option value="' + escapeStaff(id) + '">' + escapeStaff(progs[id]) + '</option>';
        }).join('');
        if (prevProg && pkeys.indexOf(prevProg) !== -1) selProg.value = prevProg;
    }
}

function renderStaffTable(lista) {
    var tbody = document.getElementById('staffTableBody');
    if (!tbody) return;
    if (!lista || !lista.length) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-4">No hay registros</td></tr>';
        staffActualizarBulkUi();
        return;
    }
    tbody.innerHTML = lista.map(function (row) {
        var nombre = [row.nombre, row.apellidoPaterno, row.apellidoMaterno].filter(Boolean).join(' ');
        var rowKey = staffRowKeyFromListItem(row);
        var checked = !!staffSelectedRowKeys[rowKey];
        var fotoSrc = null;
        if (row.personalId != null) {
            fotoSrc = API_URL + '/personal/' + encodeURIComponent(String(row.personalId)) + '/foto-perfil';
        } else if (row.alumnoId != null) {
            fotoSrc = API_URL + '/alumnos/' + encodeURIComponent(String(row.alumnoId)) + '/foto';
        }
        var fotoHtml = fotoSrc
            ? '<img class="staff-avatar-img staff-foto-open" data-foto-src="' + fotoSrc + '" data-foto-loaded="0" alt="Foto" src="' + staffFotoPlaceholderDataUri() + '" loading="lazy" referrerpolicy="no-referrer" />'
            : '<div class="staff-avatar-fallback" aria-hidden="true"></div>';
        var rolesHtml = (row.roles || []).map(function (r) {
            return '<span class="badge bg-secondary-subtle text-secondary me-1">' + escapeStaff(staffEtiquetaRol(r)) + '</span>';
        }).join('');
        var btnRoles = '';
        if (row.personalId != null && puedeGestionarStaffUi()) {
            btnRoles = ' <button type="button" class="btn btn-sm btn-outline-primary btn-staff-roles py-0 px-2" data-personal-id="' + row.personalId + '">Roles</button>';
        } else if (row.soloAlumnoSinPersonal && row.usuarioId && puedeGestionarStaffUi()) {
            btnRoles = ' <button type="button" class="btn btn-sm btn-outline-primary btn-staff-roles py-0 px-2" data-usuario-solo-alumno="' + row.usuarioId + '">Roles</button>';
        } else if (!puedeGestionarStaffUi()) {
            btnRoles = '';
        } else {
            btnRoles = ' <span class="text-muted small" title="Sin permisos o ficha no editable aquí">—</span>';
        }
        var badge = row.activo
            ? '<span class="badge bg-success-subtle text-success">Activo</span>'
            : '<span class="badge bg-secondary-subtle text-secondary">Inactivo</span>';
        var leg = row.soloMaestroLegacy ? ' <span class="badge bg-warning-subtle text-warning">Legado docente</span>' : '';
        var legAl = row.soloAlumnoSinPersonal ? ' <span class="badge bg-info-subtle text-info">Solo estudiante</span>' : '';
        var idAttr = row.personalId != null ? ' data-personal-id="' + row.personalId + '"' : '';
        var midAttr = row.maestroId != null ? ' data-maestro-id="' + row.maestroId + '"' : '';
        var aidAttr = row.alumnoId != null ? ' data-alumno-id="' + row.alumnoId + '"' : '';
        var uidAttr = row.usuarioId != null ? ' data-usuario-id="' + row.usuarioId + '"' : '';
        var soloAl = row.soloAlumnoSinPersonal ? '1' : '0';
        var accionesInner = '<button type="button" class="btn btn-outline-info btn-staff-expediente" title="Expediente"><i class="bi bi-folder2-open"></i></button>' +
            '<button type="button" class="btn btn-outline-secondary btn-staff-editar" title="Editar"><i class="bi bi-pencil"></i></button>' +
            '<button type="button" class="btn btn-outline-danger btn-staff-eliminar" title="Eliminar"><i class="bi bi-trash"></i></button>';
        var accionesTd = '<td class="staff-acciones-cell text-end"><div class="btn-group btn-group-sm" role="group">' + accionesInner + '</div></td>';
        return '<tr' + idAttr + midAttr + aidAttr + uidAttr + ' data-legacy="' + (row.soloMaestroLegacy ? '1' : '0') + '" data-solo-alumno="' + soloAl + '">' +
            '<td class="text-center"><input type="checkbox" class="form-check-input staff-row-select-chk" data-row-key="' + escapeStaff(rowKey) + '"' + (checked ? ' checked' : '') + ' aria-label="Seleccionar usuario" /></td>' +
            '<td><div class="d-flex align-items-center gap-2">' + fotoHtml + '<div><strong>' + escapeStaff(nombre) + '</strong>' + leg + legAl + '</div></div></td>' +
            '<td><small>' + escapeStaff(row.correoInstitucional || '') + '</small></td>' +
            '<td>' + rolesHtml + btnRoles + '</td>' +
            '<td>' + badge + '</td>' +
            accionesTd + '</tr>';
    }).join('');
    // Cargar fotos con Authorization (endpoints protegidos)
    staffCargarFotosTabla(tbody);
    staffActualizarBulkUi();
}

function limpiarFormularioStaff() {
    staffRestaurarSeccionesExpedienteEnFormulario();
    staffEditandoPersonalId = null;
    staffEditandoLegacyMaestroId = null;
    var f = document.getElementById('staffForm');
    if (f) f.reset();
    document.getElementById('staffPersonalId').value = '';
    document.getElementById('staffLegacyMaestroId').value = '';
    document.getElementById('staffActivo').value = 'true';
    var dc = document.getElementById('staffDocCurp');
    var di = document.getElementById('staffDocIne');
    if (dc) dc.value = '';
    if (di) di.value = '';
    staffResetFotoPerfilUiSelection();
    var sp = document.getElementById('staffPassword');
    if (sp) sp.value = '';
    staffContadorTel('staffTelefono', 'staffTelefonoCounter');
    staffContadorTel('staffContactoTelefono', 'staffContactoTelefonoCounter');
    var ot = document.getElementById('staffCampoEtiquetaOtro');
    if (ot) ot.classList.add('d-none');
    staffRenderCedulasRows([]);
    staffActualizarEstadoBotonAddCedula();
    // Limpia datos del estudiante (si existen en el DOM)
    var a1 = document.getElementById('staffAlumnoMatricula'); if (a1) a1.value = '';
    var contProg = document.getElementById('staffAlumnoProgramasContainer');
    if (contProg) contProg.innerHTML = '';
    document.getElementById('btnGuardarStaff').textContent = 'Guardar';
    staffActualizarVisibilidadSeccionesFormulario();

    // Documentos básicos (CURP/INE): restablecer UI a "sin archivo"
    staffSetDocBasicoUi('CURP_ARCHIVO', null);
    staffSetDocBasicoUi('INE', null);
    staffSetDocBasicoUi('CSF', null);
}

async function rellenarFormularioDesdePersonalId(id) {
    var res = await fetch(API_URL + '/personal/' + id, { headers: staffHeadersNoJson() });
    if (!res.ok) throw new Error('No se pudo cargar la ficha');
    var p = await res.json();
    document.getElementById('staffPersonalId').value = p.id || '';
    document.getElementById('staffCurp').value = p.curp || '';
    document.getElementById('staffNombre').value = p.nombre || '';
    document.getElementById('staffApellidoPaterno').value = p.apellidoPaterno || '';
    document.getElementById('staffApellidoMaterno').value = (p.apellidoMaterno || '').trim();
    document.getElementById('staffCorreoInstitucional').value = p.correoInstitucional || '';
    document.getElementById('staffCorreoPersonal').value = p.correoPersonal || '';
    document.getElementById('staffTelefono').value = normalizarTel10(p.telefono);
    var cp = document.getElementById('staffCodigoPostal');
    if (cp) cp.value = p.codigoPostal || '';
    var gen = document.getElementById('staffGenero');
    if (gen) gen.value = p.sexo || '';
    var fn = document.getElementById('staffFechaNacimiento');
    if (fn) fn.value = p.fechaNacimiento ? String(p.fechaNacimiento).substring(0, 10) : '';
    document.getElementById('staffGrado').value = p.gradoAcademico || '';
    staffRenderCedulasRows(p.cedulasProfesionales && p.cedulasProfesionales.length
        ? p.cedulasProfesionales.map(function (c) {
            return { id: c.id, etiqueta: c.etiqueta || '', numero: c.numero || '', filename: c.filename || null };
        })
        : (p.cedulaProfesional ? [{ id: null, numero: p.cedulaProfesional, filename: null }] : []));
    document.getElementById('staffDepartamento').value = p.departamento || '';
    document.getElementById('staffArea').value = p.area || '';
    document.getElementById('staffTipoMaestro').value = p.tipoMaestro || '';
    document.getElementById('staffRfc').value = p.rfc || '';
    document.getElementById('staffRegimen').value = p.regimenFiscal || '';
    document.getElementById('staffActivo').value = p.activo === false ? 'false' : 'true';
    document.getElementById('staffObservaciones').value = p.observaciones || '';
    document.getElementById('staffContactoNombre').value = p.nombreContactoEmergencia || '';
    document.getElementById('staffContactoTelefono').value = normalizarTel10(p.telefonoContactoEmergencia);
    staffEtiquetaDesdeServidor(p.etiqueta);
    staffContadorTel('staffTelefono', 'staffTelefonoCounter');
    staffContadorTel('staffContactoTelefono', 'staffContactoTelefonoCounter');
    staffActualizarVisibilidadSeccionesFormulario();
    await staffCargarAlumnoEnFormularioSiAplica();
    document.getElementById('btnGuardarStaff').textContent = 'Guardar';

    // Documentos básicos (CURP/INE): mostrar como "cargados" si existen
    await staffRefrescarDocumentosBasicosUi();
    // Foto de perfil: mostrar si existe (aunque venga del expediente alumno) + permitir cambiarla
    await staffRefrescarFotoPerfilExistenteUi(p.id || id);
    staffInitFotoPerfilUi();
}

function staffDocBasicoConfig(tipo) {
    if (tipo === 'CURP_ARCHIVO') return { wrapId: 'staffDocCurpWrap', inputId: 'staffDocCurp', label: 'CURP' };
    if (tipo === 'INE') return { wrapId: 'staffDocIneWrap', inputId: 'staffDocIne', label: 'INE' };
    if (tipo === 'CSF') return { wrapId: 'staffDocCsfWrap', inputId: 'staffDocCsf', label: 'CSF' };
    return null;
}

function staffAbrirDocumentoBasicoEnNuevaPestana(tipo) {
    var pid = document.getElementById('staffPersonalId') && document.getElementById('staffPersonalId').value;
    if (!pid) return;
    fetch(API_URL + '/personal/staff/' + encodeURIComponent(pid) + '/documentos/' + encodeURIComponent(tipo) + '/archivo', { headers: staffHeadersNoJson() })
        .then(function (r) {
            if (!r.ok) throw new Error('No se pudo abrir');
            return r.blob();
        })
        .then(function (b) {
            var u = URL.createObjectURL(b);
            window.open(u, '_blank');
        })
        .catch(function () { alert('No se pudo abrir el archivo'); });
}

async function staffEliminarDocumentoBasico(tipo) {
    var pid = document.getElementById('staffPersonalId') && document.getElementById('staffPersonalId').value;
    if (!pid) return;
    var ok = false;
    try {
        ok = (typeof window.uiConfirm === 'function')
            ? await window.uiConfirm('¿Eliminar este documento del expediente?', { subtitle: 'Esta acción no se puede deshacer.' })
            : confirm('¿Eliminar este documento del expediente?\n\nEsta acción no se puede deshacer.');
    } catch (_) {
        ok = false;
    }
    if (!ok) return;
    var r = await fetch(API_URL + '/personal/staff/' + encodeURIComponent(pid) + '/documentos/' + encodeURIComponent(String(tipo)), {
        method: 'DELETE',
        headers: staffHeadersNoJson()
    });
    if (!r.ok) {
        var err = null;
        try { err = await r.json(); } catch (_) { }
        throw new Error((err && (err.error || err.message)) || 'No se pudo eliminar el documento');
    }
    await staffRefrescarDocumentosBasicosUi();
}

function staffSetDocBasicoUi(tipo, meta) {
    var cfg = staffDocBasicoConfig(tipo);
    if (!cfg) return;
    var wrap = document.getElementById(cfg.wrapId);
    var inp = document.getElementById(cfg.inputId);
    if (!wrap || !inp) return;

    // Limpiar contenedor y reinsertar el input (para conservar listeners y evitar duplicados)
    wrap.innerHTML = '';

    if (meta && meta.filename) {
        var row = document.createElement('div');
        row.className = 'd-flex align-items-center justify-content-between gap-2 border rounded px-2 py-2 bg-white';
        row.innerHTML =
            '<div class="small text-truncate" style="min-width:0;">' +
              '<span class="text-muted">' + cfg.label + ':</span> ' +
              '<strong>' + escapeStaff(meta.filename) + '</strong>' +
            '</div>' +
            '<div class="btn-group btn-group-sm" role="group">' +
              '<button type="button" class="btn btn-outline-secondary" data-action="ver" title="Ver"><i class="bi bi-eye"></i></button>' +
              '<button type="button" class="btn btn-outline-danger" data-action="quitar" title="Quitar"><i class="bi bi-x-lg"></i></button>' +
            '</div>';
        wrap.appendChild(row);
        inp.value = '';
        inp.classList.add('d-none');
        wrap.appendChild(inp);
        row.querySelector('[data-action="ver"]').addEventListener('click', function () { staffAbrirDocumentoBasicoEnNuevaPestana(tipo); });
        row.querySelector('[data-action="quitar"]').addEventListener('click', function () { staffEliminarDocumentoBasico(tipo); });
        return;
    }

    inp.classList.remove('d-none');

    // UI: X dentro del selector (input-group). No mostrar "Seleccionado: ..."
    var group = document.createElement('div');
    group.className = 'input-group input-group-sm';
    wrap.appendChild(group);
    group.appendChild(inp);

    var btnClear = document.createElement('button');
    btnClear.type = 'button';
    btnClear.className = 'btn btn-outline-danger btn-sm';
    btnClear.title = 'Quitar selección';
    btnClear.innerHTML = '<i class="bi bi-x-lg"></i>';
    btnClear.disabled = true;
    group.appendChild(btnClear);

    function syncClearEnabled() {
        var has = !!(inp.files && inp.files[0]);
        btnClear.disabled = !has;
    }

    btnClear.addEventListener('click', function () {
        inp.value = '';
        syncClearEnabled();
    });
    inp.addEventListener('change', function () {
        syncClearEnabled();
    });
    syncClearEnabled();
}

async function staffRefrescarDocumentosBasicosUi() {
    var pid = document.getElementById('staffPersonalId') && document.getElementById('staffPersonalId').value;
    if (!pid) {
        staffSetDocBasicoUi('CURP_ARCHIVO', null);
        staffSetDocBasicoUi('INE', null);
        staffSetDocBasicoUi('CSF', null);
        return;
    }
    try {
        var res = await fetch(API_URL + '/personal/staff/' + encodeURIComponent(pid) + '/documentos', { headers: staffHeadersNoJson() });
        if (!res.ok) throw new Error('No se pudo cargar documentos');
        var list = await res.json();
        var curp = null;
        var ine = null;
        var csf = null;
        (list || []).forEach(function (x) {
            if (!x || !x.tipo) return;
            if (x.tipo === 'CURP_ARCHIVO') curp = x;
            if (x.tipo === 'INE') ine = x;
            if (x.tipo === 'CSF') csf = x;
        });
        staffSetDocBasicoUi('CURP_ARCHIVO', curp);
        staffSetDocBasicoUi('INE', ine);
        staffSetDocBasicoUi('CSF', csf);
    } catch (e) {
        staffSetDocBasicoUi('CURP_ARCHIVO', null);
        staffSetDocBasicoUi('INE', null);
        staffSetDocBasicoUi('CSF', null);
    }
}

async function staffCargarCatalogosAlumnoEnFormulario() {
    var cont = document.getElementById('staffAlumnoProgramasContainer');
    if (!cont) return;

    // Cargar programas una sola vez
    if (!window.__staffAlumnoProgramasCatalogo) {
        window.__staffAlumnoProgramasCatalogo = [];
        try {
            var r = await fetch(API_URL + '/programas-educativos', { headers: staffHeaders() });
            if (!r.ok) throw new Error('No se pudieron cargar los programas educativos.');
            var list = await r.json();
            window.__staffAlumnoProgramasCatalogo = Array.isArray(list) ? list : [];
        } catch (e) {
            console.error(e);
            window.__staffAlumnoProgramasCatalogo = [];
        }
    }

    // Botón agregar fila
    var btnAdd = document.getElementById('btnStaffAlumnoAgregarPrograma');
    if (btnAdd && !btnAdd.getAttribute('data-bound')) {
        btnAdd.setAttribute('data-bound', 'true');
        btnAdd.addEventListener('click', function () {
            staffAlumnoAgregarFilaPrograma(null);
        });
    }

    // Si aún no hay filas, crear una inicial
    if (!cont.querySelector('.staff-alumno-prog-row')) {
        staffAlumnoAgregarFilaPrograma(null);
    }
}

/** IDs de programa ya elegidos en otras filas (excluye la fila `excluirRow` si se pasa). */
function staffAlumnoMapProgramasOcupadosExcluyendoFila(excluirRow) {
    var map = {};
    var cont = document.getElementById('staffAlumnoProgramasContainer');
    if (!cont) return map;
    var rows = cont.querySelectorAll('.staff-alumno-prog-row');
    for (var i = 0; i < rows.length; i++) {
        var row = rows[i];
        if (excluirRow && row === excluirRow) continue;
        var sel = row.querySelector('.staff-alumno-prog-select');
        if (sel && sel.value) map[String(sel.value)] = true;
    }
    return map;
}

function staffAlumnoRefrescarSelectProgramaEnFila(row) {
    if (!row) return;
    var sel = row.querySelector('.staff-alumno-prog-select');
    if (!sel) return;
    var prev = sel.value || '';
    var list = window.__staffAlumnoProgramasCatalogo || [];
    var ocupados = staffAlumnoMapProgramasOcupadosExcluyendoFila(row);
    var html = '<option value="">Seleccione…</option>';
    if (!list.length) {
        sel.innerHTML = '<option value="">No se pudo cargar</option>';
        return;
    }
    for (var j = 0; j < list.length; j++) {
        var pr = list[j];
        var id = String(pr.id);
        if (ocupados[id] && id !== prev) continue;
        var label = (pr.clave ? String(pr.clave) + ' — ' : '') + (pr.nombre || '');
        html += '<option value="' + id + '">' + escapeHtml(label) + '</option>';
    }
    sel.innerHTML = html;
    var tiene = false;
    for (var k = 0; k < sel.options.length; k++) {
        if (sel.options[k].value === prev) { tiene = true; break; }
    }
    if (tiene) sel.value = prev;
    else sel.value = '';
}

function staffAlumnoRefrescarTodosSelectsProgramas() {
    var cont = document.getElementById('staffAlumnoProgramasContainer');
    if (!cont) return;
    var rows = cont.querySelectorAll('.staff-alumno-prog-row');
    for (var i = 0; i < rows.length; i++) {
        staffAlumnoRefrescarSelectProgramaEnFila(rows[i]);
    }
}

function staffAlumnoEstatusOptionsHtml() {
    return '' +
        '<option value="ACTIVA">Activa</option>' +
        '<option value="BAJA_TEMPORAL">Baja temporal</option>' +
        '<option value="BAJA_DEFINITIVA">Baja definitiva</option>' +
        '<option value="EGRESADO">Egresado</option>';
}

function staffAlumnoAgregarFilaPrograma(valorInicial) {
    var cont = document.getElementById('staffAlumnoProgramasContainer');
    if (!cont) return;
    var row = document.createElement('div');
    row.className = 'staff-alumno-prog-row border rounded p-2 mb-2 bg-white';
    row.innerHTML = '' +
        '<div class="row g-2 align-items-end">' +
          '<div class="col-md-5">' +
            '<label class="form-label small mb-1">Programa</label>' +
            '<select class="form-select form-select-sm staff-alumno-prog-select"></select>' +
          '</div>' +
          '<div class="col-md-4">' +
            '<label class="form-label small mb-1">Periodo de ingreso</label>' +
            '<select class="form-select form-select-sm staff-alumno-periodo-select">' +
              '<option value="">Seleccione…</option>' +
            '</select>' +
          '</div>' +
          '<div class="col-md-2">' +
            '<label class="form-label small mb-1">Estatus</label>' +
            '<select class="form-select form-select-sm staff-alumno-estatus-select">' + staffAlumnoEstatusOptionsHtml() + '</select>' +
          '</div>' +
          '<div class="col-md-1 text-end">' +
            '<button type="button" class="btn btn-sm btn-outline-danger staff-alumno-prog-remove" title="Quitar programa"><i class="bi bi-x-lg"></i></button>' +
          '</div>' +
        '</div>';
    cont.appendChild(row);

    staffAlumnoRefrescarSelectProgramaEnFila(row);

    var selP = row.querySelector('.staff-alumno-prog-select');
    var selPer = row.querySelector('.staff-alumno-periodo-select');
    var selEst = row.querySelector('.staff-alumno-estatus-select');
    var btnDel = row.querySelector('.staff-alumno-prog-remove');

    function cargarPeriodos(pid, preselectId) {
        selPer.innerHTML = '<option value="">Cargando…</option>';
        if (!pid) {
            selPer.innerHTML = '<option value="">Seleccione…</option>';
            return;
        }
        fetch(API_URL + '/periodos-academicos?programaId=' + encodeURIComponent(pid), { headers: staffHeaders() })
            .then(function (x) { return x.json(); })
            .then(function (periodos) {
                var opts = '<option value="">Seleccione…</option>';
                (periodos || []).forEach(function (pe) {
                    opts += '<option value="' + String(pe.id) + '">' + escapeHtml(pe.codigo || pe.nombre || String(pe.id)) + '</option>';
                });
                selPer.innerHTML = opts;
                if (preselectId != null) {
                    selPer.value = String(preselectId);
                }
            })
            .catch(function () {
                selPer.innerHTML = '<option value="">No se pudo cargar</option>';
            });
    }

    selP.addEventListener('change', function () {
        staffAlumnoRefrescarTodosSelectsProgramas();
        cargarPeriodos(selP.value, null);
    });

    btnDel.addEventListener('click', function () {
        var rows = cont.querySelectorAll('.staff-alumno-prog-row');
        if (rows.length <= 1) {
            // Mantener al menos una fila visible
            selP.value = '';
            selPer.innerHTML = '<option value="">Seleccione…</option>';
            selPer.value = '';
            if (selEst) selEst.value = 'ACTIVA';
            staffAlumnoRefrescarTodosSelectsProgramas();
            return;
        }
        row.remove();
        staffAlumnoRefrescarTodosSelectsProgramas();
    });

    // Aplicar valores iniciales si vienen
    if (valorInicial) {
        if (valorInicial.programaId != null) {
            selP.value = String(valorInicial.programaId);
            cargarPeriodos(selP.value, valorInicial.periodoAcademicoIngresoId != null ? valorInicial.periodoAcademicoIngresoId : null);
        }
        if (valorInicial.estatusMatricula) {
            selEst.value = String(valorInicial.estatusMatricula);
        } else {
            selEst.value = 'ACTIVA';
        }
    } else {
        if (selEst) selEst.value = 'ACTIVA';
    }

    staffAlumnoRefrescarTodosSelectsProgramas();
}

function staffAlumnoLeerProgramasDesdeUi() {
    var cont = document.getElementById('staffAlumnoProgramasContainer');
    if (!cont) return [];
    var rows = Array.prototype.slice.call(cont.querySelectorAll('.staff-alumno-prog-row'));
    return rows.map(function (row) {
        var selP = row.querySelector('.staff-alumno-prog-select');
        var selPer = row.querySelector('.staff-alumno-periodo-select');
        var selEst = row.querySelector('.staff-alumno-estatus-select');
        var pid = selP && selP.value ? parseInt(selP.value, 10) : null;
        var per = selPer && selPer.value ? parseInt(selPer.value, 10) : null;
        var est = selEst && selEst.value ? String(selEst.value).trim() : 'ACTIVA';
        return {
            programaId: (pid != null && !isNaN(pid)) ? pid : null,
            periodoAcademicoIngresoId: (per != null && !isNaN(per)) ? per : null,
            estatusMatricula: est || 'ACTIVA'
        };
    }).filter(function (x) { return x && x.programaId != null; });
}

async function staffCargarAlumnoEnFormularioSiAplica() {
    var roles = staffRolesActualesDesdeFormulario();
    if (!staffMostrarAlumnoExpediente(roles)) return;

    await staffCargarCatalogosAlumnoEnFormulario();

    var pid = document.getElementById('staffPersonalId').value;
    if (!pid) return;
    var row = staffList.find(function (x) { return x.personalId === parseInt(pid, 10); });
    var alumnoId = row && row.alumnoId ? row.alumnoId : null;
    // IMPORTANT: siempre limpiar UI de alumno antes de cargar (evita mezclar datos entre estudiantes)
    try {
        var hidA = document.getElementById('staffAlumnoIdHidden');
        if (hidA) hidA.value = alumnoId != null ? String(alumnoId) : '';
        var mat0 = document.getElementById('staffAlumnoMatricula');
        if (mat0) mat0.value = '';
        var cont0 = document.getElementById('staffAlumnoProgramasContainer');
        if (cont0) cont0.innerHTML = '';
        var docsBlock = document.getElementById('staffAlumnoDocsBlock');
        if (docsBlock) {
            // limpiar inputs file (si existieran selecciones previas)
            ['staffAluDocCurp', 'staffAluDocIne', 'staffAluDocConstanciaFiscal', 'staffAluDocActa', 'staffAluDocCertEstudios'].forEach(function (id) {
                var inp = document.getElementById(id);
                if (inp) inp.value = '';
            });
            var tc = document.getElementById('staffAluTitulosCedulaContainer');
            if (tc) tc.innerHTML = '';
        }
    } catch (_) {}
    if (!alumnoId) {
        staffAlumnoAgregarFilaPrograma(null);
        staffAlumnoRefrescarTodosSelectsProgramas();
        return;
    }

    try {
        var res = await fetch(API_URL + '/alumnos/' + alumnoId, { headers: staffHeadersNoJson() });
        if (!res.ok) return;
        var a = await res.json();
        var mat = document.getElementById('staffAlumnoMatricula');
        if (mat) mat.value = a.matricula || '';

        // Programas asignados (nuevo modelo)
        var cont = document.getElementById('staffAlumnoProgramasContainer');
        if (cont) cont.innerHTML = '';
        var asign = Array.isArray(a.programasAsignados) ? a.programasAsignados : [];
        if (asign.length) {
            asign.forEach(function (ap) {
                var pid2 = ap && ap.programa && ap.programa.id != null ? ap.programa.id : null;
                var per2 = ap && ap.periodoIngreso && ap.periodoIngreso.id != null ? ap.periodoIngreso.id : null;
                var est2 = ap && ap.estatusMatricula ? ap.estatusMatricula : 'ACTIVA';
                staffAlumnoAgregarFilaPrograma({ programaId: pid2, periodoAcademicoIngresoId: per2, estatusMatricula: est2 });
            });
        } else if (a.programa && a.programa.id) {
            // Legacy: 1 programa
            var perId = null;
            if (a.periodoAcademico && a.periodoAcademico.id) perId = a.periodoAcademico.id;
            if (a.periodoAcademicoId != null) perId = a.periodoAcademicoId;
            staffAlumnoAgregarFilaPrograma({
                programaId: a.programa.id,
                periodoAcademicoIngresoId: perId,
                estatusMatricula: (a.estatusMatricula || 'ACTIVA')
            });
        } else {
            staffAlumnoAgregarFilaPrograma(null);
        }
        staffAlumnoRefrescarTodosSelectsProgramas();
        // Documentos del estudiante (mismo expediente): reflejar estado actual al editar
        try { await staffAlumnoRefrescarDocsUi(alumnoId); } catch (_) {}
    } catch (e) {
        console.error(e);
    }
}

function staffEtiquetaDesdeServidor(etiqueta) {
    var sel = document.getElementById('staffEtiquetaSelect');
    var ot = document.getElementById('staffEtiquetaOtro');
    var wrap = document.getElementById('staffCampoEtiquetaOtro');
    var fijas = ['Dr.', 'Dra.', 'Mtro.', 'Mtra.', 'Lic.', 'CDEO', 'CDEE', 'CDEP', 'LOEO'];
    var et = (etiqueta || '').trim();
    if (!sel) return;
    if (et && fijas.indexOf(et) === -1) {
        sel.value = 'otro';
        if (ot) ot.value = et;
        if (wrap) wrap.classList.remove('d-none');
    } else {
        sel.value = et || '';
        if (ot) ot.value = '';
        if (wrap) wrap.classList.add('d-none');
    }
}

async function rellenarFormularioDesdeMaestroId(maestroId) {
    var res = await fetch(API_URL + '/maestros/' + maestroId, { headers: staffHeadersNoJson() });
    if (!res.ok) throw new Error('No se pudo cargar docente');
    var m = await res.json();
    document.getElementById('staffLegacyMaestroId').value = maestroId;
    document.getElementById('staffCurp').value = m.curp || '';
    document.getElementById('staffNombre').value = m.nombre || '';
    document.getElementById('staffApellidoPaterno').value = m.apellidoPaterno || '';
    document.getElementById('staffApellidoMaterno').value = m.apellidoMaterno || '';
    document.getElementById('staffCorreoInstitucional').value = m.correoInstitucional || '';
    document.getElementById('staffCorreoPersonal').value = m.correoPersonal || '';
    document.getElementById('staffTelefono').value = normalizarTel10(m.telefono);
    var cpL = document.getElementById('staffCodigoPostal');
    if (cpL) cpL.value = m.codigoPostal || '';
    document.getElementById('staffGrado').value = m.gradoAcademico || '';
    staffRenderCedulasRows(m.cedulaProfesional ? [{ id: null, numero: m.cedulaProfesional, filename: null }] : []);
    document.getElementById('staffArea').value = m.area || '';
    document.getElementById('staffTipoMaestro').value = m.tipoMaestro || '';
    document.getElementById('staffRfc').value = m.rfc || '';
    document.getElementById('staffRegimen').value = m.regimenFiscal || '';
    document.getElementById('staffActivo').value = m.activo === false ? 'false' : 'true';
    document.getElementById('staffObservaciones').value = m.observaciones || '';
    document.getElementById('staffContactoNombre').value = m.nombreContactoEmergencia || '';
    document.getElementById('staffContactoTelefono').value = normalizarTel10(m.telefonoContactoEmergencia);
    staffEtiquetaDesdeServidor(m.etiqueta);
    staffContadorTel('staffTelefono', 'staffTelefonoCounter');
    staffContadorTel('staffContactoTelefono', 'staffContactoTelefonoCounter');
    staffActualizarVisibilidadSeccionesFormulario();
    document.getElementById('btnGuardarStaff').textContent = 'Migrar a ficha unificada';
}

function abrirModalStaffRoles(personalId, rolesActuales, usuarioIdSoloAlumno) {
    staffExpedienteSoloDesdeCarpeta = false;
    staffRolesCommitPendiente = null;
    staffRolesModalPendientesEnFormulario = null;
    staffRolesPatchPendiente = { url: '', roles: [] };
    staffRolesCoordPatchPendiente = { url: '', roles: [] };
    staffRestaurarSeccionesExpedienteEnFormulario();
    modalStaffRolesResetPasosUi();
    var hid = document.getElementById('modalStaffRolesPersonalId');
    var hidU = document.getElementById('modalStaffRolesUsuarioSoloAlumnoId');
    if (hid) hid.value = personalId != null && personalId !== undefined ? String(personalId) : '';
    if (hidU) hidU.value = usuarioIdSoloAlumno != null && usuarioIdSoloAlumno !== undefined ? String(usuarioIdSoloAlumno) : '';
    aplicarRolesEnModal(rolesActuales);
    var m = document.getElementById('modalStaffRoles');
    if (m && typeof bootstrap !== 'undefined' && bootstrap.Modal) {
        bootstrap.Modal.getOrCreateInstance(m).show();
    }
}

async function staffPrepararYMostrarModalDatosAlumnoRol() {
    var selP = document.getElementById('modalAlumnoRolPrograma');
    var selPer = document.getElementById('modalAlumnoRolPeriodo');
    if (!selP || !selPer) return;
    document.getElementById('modalAlumnoRolMatricula').value = '';
    // Reset UI documentos (solo selección local al inicio)
    modalAlumnoPendienteEliminarDocs = {};
    modalAlumnoDocsBasicosSeleccionados = {};
    modalAlumnoRefrescarDocsUi(null);
    selPer.innerHTML = '<option value="">Seleccione…</option>';
    selP.innerHTML = '<option value="">Cargando…</option>';
    try {
        var r = await fetch(API_URL + '/programas-educativos', { headers: staffHeaders() });
        if (!r.ok) throw new Error('No se pudieron cargar los programas educativos.');
        var list = await r.json();
        selP.innerHTML = '<option value="">Seleccione…</option>';
        (list || []).forEach(function (pr) {
            var o = document.createElement('option');
            o.value = String(pr.id);
            o.textContent = (pr.clave ? String(pr.clave) + ' — ' : '') + (pr.nombre || '');
            selP.appendChild(o);
        });
    } catch (e) {
        alert(e.message || 'Error al cargar programas');
        return;
    }
    selP.onchange = function () {
        var pid = selP.value;
        selPer.innerHTML = '<option value="">Seleccione…</option>';
        if (!pid) return;
        fetch(API_URL + '/periodos-academicos?programaId=' + encodeURIComponent(pid), { headers: staffHeaders() })
            .then(function (x) { return x.json(); })
            .then(function (periodos) {
                (periodos || []).forEach(function (pe) {
                    var o = document.createElement('option');
                    o.value = String(pe.id);
                    o.textContent = pe.codigo || pe.nombre || String(pe.id);
                    selPer.appendChild(o);
                });
            })
            .catch(function () {});
    };
    var m = document.getElementById('modalDatosAlumnoRol');
    if (m && typeof bootstrap !== 'undefined' && bootstrap.Modal) {
        // Evitar backdrop doble: ocultar modal de roles antes de abrir éste
        var mRoles = document.getElementById('modalStaffRoles');
        if (mRoles) {
            var instRoles = bootstrap.Modal.getInstance(mRoles);
            if (instRoles) instRoles.hide();
        }
        bootstrap.Modal.getOrCreateInstance(m).show();
    }
}

function modalAlumnoDocConfig(tipo) {
    switch (tipo) {
        case 'ACTA_NACIMIENTO': return { wrapId: 'modalAluDocActaWrap', inputId: 'modalAluDocActa', label: 'Acta de nacimiento' };
        case 'CONSTANCIA_ESTUDIOS': return { wrapId: 'modalAluDocCertEstudiosWrap', inputId: 'modalAluDocCertEstudios', label: 'Certificado de estudios' };
        case 'TITULO_CEDULA': return { wrapId: 'modalAluDocTituloCedulaWrap', inputId: 'modalAluDocTituloCedula', label: 'Título / Cédula' };
        case 'CURP': return { wrapId: 'modalAluDocCurpWrap', inputId: 'modalAluDocCurp', label: 'CURP' };
        case 'INE': return { wrapId: 'modalAluDocIneWrap', inputId: 'modalAluDocIne', label: 'INE' };
        case 'CSF': return { wrapId: 'modalAluDocCsfWrap', inputId: 'modalAluDocCsf', label: 'Constancia de situación fiscal (CSF)' };
        default: return null;
    }
}

/** Tipo en API /alumnos/.../documentos (enum DocumentoAlumno); la UI usa «CSF» pero el backend es CONSTANCIA_SITUACION_FISCAL. */
function modalAlumnoTipoDocumentoAlumnoApi(tipoUi) {
    var u = String(tipoUi || '').toUpperCase();
    if (u === 'CSF') return 'CONSTANCIA_SITUACION_FISCAL';
    return u;
}

function modalAlumnoTipoPersonalBasicoDesdeTipoAlumno(tipoAlumno) {
    switch (String(tipoAlumno || '').toUpperCase()) {
        case 'CURP': return 'CURP_ARCHIVO';
        case 'INE': return 'INE';
        case 'CSF': return 'CSF';
        default: return null;
    }
}

function modalAlumnoSetInputFile(inputEl, fileOrNull) {
    if (!inputEl) return;
    try {
        var dt = new DataTransfer();
        if (fileOrNull) dt.items.add(fileOrNull);
        inputEl.files = dt.files;
    } catch (_) {
        // fallback: no se puede setear programáticamente en algunos navegadores; al menos limpiar
        if (!fileOrNull) inputEl.value = '';
    }
}

function modalAlumnoSetDocUi(tipo, meta) {
    var cfg = modalAlumnoDocConfig(tipo);
    if (!cfg) return;
    var wrap = document.getElementById(cfg.wrapId);
    var inp = document.getElementById(cfg.inputId);
    if (!wrap || !inp) return;
    wrap.innerHTML = '';

    var filename = meta && (meta.filename || meta.archivoUrl || meta.nombreArchivo) ? (meta.filename || meta.archivoUrl || meta.nombreArchivo) : null;
    var entregado = meta && meta.entregado === true;

    if (entregado && filename) {
        var src = meta && meta.__source ? String(meta.__source) : '';
        var personalTipo = meta && meta.__personalTipo ? String(meta.__personalTipo) : '';
        var row = document.createElement('div');
        row.className = 'd-flex align-items-center justify-content-between gap-2 border rounded px-2 py-2 bg-white';
        row.innerHTML =
            '<div class="small text-truncate" style="min-width:0;">' +
            '<span class="text-muted">' + escapeStaff(cfg.label) + ':</span> ' +
            '<strong>' + escapeStaff(filename) + '</strong>' +
            '</div>' +
            '<div class="btn-group btn-group-sm" role="group">' +
            '<button type="button" class="btn btn-outline-secondary" data-action="ver" title="Ver"><i class="bi bi-eye"></i></button>' +
            '<button type="button" class="btn btn-outline-danger" data-action="quitar" title="Quitar"><i class="bi bi-x-lg"></i></button>' +
            '</div>';
        wrap.appendChild(row);
        inp.value = '';
        inp.classList.add('d-none');
        wrap.appendChild(inp);
        row.querySelector('[data-action="ver"]').addEventListener('click', function () {
            if (src === 'personal') {
                var personalId = (document.getElementById('modalStaffRolesPersonalId') && document.getElementById('modalStaffRolesPersonalId').value || '').trim();
                if (!personalId) return;
                fetch(API_URL + '/personal/staff/' + encodeURIComponent(String(personalId)) + '/documentos/' + encodeURIComponent(personalTipo) + '/archivo', { headers: staffHeadersNoJson() })
                    .then(function (r) { if (!r.ok) throw new Error('No se pudo abrir'); return r.blob(); })
                    .then(function (b) { window.open(URL.createObjectURL(b), '_blank'); })
                    .catch(function () { alert('No se pudo abrir el archivo'); });
                return;
            }
            var aid = window.__modalAlumnoRolAlumnoId;
            if (!aid) return;
            var tipoApi = modalAlumnoTipoDocumentoAlumnoApi(tipo);
            var urlA = (tipoApi === 'TITULO_CEDULA' && meta && meta.id)
                ? (API_URL + '/alumnos/' + encodeURIComponent(String(aid)) + '/documentos/descarga?docId=' + encodeURIComponent(String(meta.id)))
                : (API_URL + '/alumnos/' + encodeURIComponent(String(aid)) + '/documentos/' + encodeURIComponent(tipoApi) + '/archivo');
            fetch(urlA, { headers: staffHeadersNoJson() })
                .then(function (r) { if (!r.ok) throw new Error('No se pudo abrir'); return r.blob(); })
                .then(function (b) { window.open(URL.createObjectURL(b), '_blank'); })
                .catch(function () { alert('No se pudo abrir el archivo'); });
        });
        row.querySelector('[data-action="quitar"]').addEventListener('click', async function () {
            try {
                if (src === 'personal') {
                    await staffEliminarDocumentoBasico(personalTipo);
                    return;
                }
                var aid = window.__modalAlumnoRolAlumnoId;
                if (!aid) return;
                var ok = (typeof window.uiConfirm === 'function')
                    ? await window.uiConfirm('¿Eliminar este documento del expediente del alumno?', { subtitle: 'Esta acción no se puede deshacer.' })
                    : confirm('¿Eliminar este documento del expediente del alumno?\n\nEsta acción no se puede deshacer.');
                if (!ok) return;
                var tipoApiDel = modalAlumnoTipoDocumentoAlumnoApi(tipo);
                var delUrl = (tipoApiDel === 'TITULO_CEDULA' && meta && meta.id)
                    ? (API_URL + '/alumnos/' + encodeURIComponent(String(aid)) + '/documentos/doc/' + encodeURIComponent(String(meta.id)))
                    : (API_URL + '/alumnos/' + encodeURIComponent(String(aid)) + '/documentos/' + encodeURIComponent(String(tipoApiDel)));
                await fetch(delUrl, {
                    method: 'DELETE',
                    headers: staffHeadersNoJson()
                });
                await modalAlumnoRefrescarDocsUi(aid);
            } catch (e) {
                alert((e && e.message) ? e.message : 'No se pudo eliminar el documento');
            }
        });
        return;
    }

    inp.classList.remove('d-none');
    // X dentro del selector (solo quita selección local)
    var group = document.createElement('div');
    group.className = 'input-group input-group-sm';
    wrap.appendChild(group);
    group.appendChild(inp);
    var btnClear = document.createElement('button');
    btnClear.type = 'button';
    btnClear.className = 'btn btn-outline-danger btn-sm';
    btnClear.title = 'Quitar selección';
    btnClear.innerHTML = '<i class="bi bi-x-lg"></i>';
    btnClear.disabled = true;
    group.appendChild(btnClear);
    function sync() {
        var has = !!(inp.files && inp.files[0]);
        btnClear.disabled = !has;
    }
    btnClear.addEventListener('click', function () { inp.value = ''; sync(); });
    inp.addEventListener('change', function () {
        sync();
        var alumnoTipo = String(tipo || '').toUpperCase();
        var personalTipo = modalAlumnoTipoPersonalBasicoDesdeTipoAlumno(alumnoTipo);
        if (personalTipo) {
            // Documento básico: se subirá en el expediente general
            if (inp.files && inp.files[0]) {
                modalAlumnoDocsBasicosSeleccionados[String(personalTipo)] = inp.files[0];
                delete staffPendienteEliminarDocsBasicos[String(personalTipo)];
            } else {
                delete modalAlumnoDocsBasicosSeleccionados[String(personalTipo)];
            }
        }
    });
    sync();
}

async function modalAlumnoRefrescarDocsUi(alumnoIdOrNull) {
    window.__modalAlumnoRolAlumnoId = alumnoIdOrNull || null;
    var mapA = {};
    if (alumnoIdOrNull) {
        try {
            var resA = await fetch(API_URL + '/alumnos/' + encodeURIComponent(String(alumnoIdOrNull)) + '/documentos', { headers: staffHeadersNoJson() });
            if (resA.ok) {
                var listA = await resA.json();
                (listA || []).forEach(function (x) {
                    if (!x || !x.tipo) return;
                    if (x.tipo === 'TITULO_CEDULA') return;
                    mapA[x.tipo] = x;
                });
                var titM = (listA || []).filter(function (x) { return x && x.tipo === 'TITULO_CEDULA'; })
                    .sort(function (a, b) { return (a.docSlot || 0) - (b.docSlot || 0); });
                if (titM.length) mapA.TITULO_CEDULA = titM[0];
            }
        } catch (_) {}
    }
    var mapP = {};
    var personalId = (document.getElementById('modalStaffRolesPersonalId') && document.getElementById('modalStaffRolesPersonalId').value || '').trim();
    if (personalId) {
        try {
            var resP = await fetch(API_URL + '/personal/staff/' + encodeURIComponent(String(personalId)) + '/documentos', { headers: staffHeadersNoJson() });
            if (resP.ok) {
                var listP = await resP.json();
                (listP || []).forEach(function (x) { if (x && x.tipo) mapP[String(x.tipo)] = x; });
            }
        } catch (_) {}
    }
    // CURP / INE / CSF: preferir documento de expediente personal; si no, el que subió el alumno (DocumentoAlumno; CSF = CONSTANCIA_SITUACION_FISCAL)
    ['CURP', 'INE', 'CSF'].forEach(function (t) {
        var tp = modalAlumnoTipoPersonalBasicoDesdeTipoAlumno(t);
        var pendienteP = tp && !!staffPendienteEliminarDocsBasicos[String(tp)];
        var metaP = (tp && mapP[tp] && mapP[tp].filename && !pendienteP) ? mapP[tp] : null;
        if (metaP) {
            modalAlumnoSetDocUi(t, { entregado: true, filename: metaP.filename, __source: 'personal', __personalTipo: tp });
            return;
        }
        var apiT = modalAlumnoTipoDocumentoAlumnoApi(t);
        var d = mapA[apiT];
        var fn = d && (d.filename || d.archivoUrl || d.nombreArchivo);
        var ent = d && (d.entregado === true || (fn && String(fn).trim() !== ''));
        if (ent && fn) {
            modalAlumnoSetDocUi(t, { entregado: true, filename: fn, __source: 'alumno' });
        } else {
            modalAlumnoSetDocUi(t, null);
        }
    });

    // Docs propios del expediente alumno (sin duplicar los básicos ya mostrados arriba)
    var alumnoTipos = ['ACTA_NACIMIENTO', 'CONSTANCIA_ESTUDIOS', 'TITULO_CEDULA'];
    alumnoTipos.forEach(function (t) { modalAlumnoSetDocUi(t, null); });
    if (!alumnoIdOrNull) return;
    alumnoTipos.forEach(function (t) { modalAlumnoSetDocUi(t, mapA[t] || null); });
}

function modalAlumnoArchivoInput(inpId) {
    var el = document.getElementById(inpId);
    if (!el || !el.files || !el.files[0]) return null;
    return el.files[0];
}

async function modalAlumnoGuardarDocs(alumnoId) {
    if (!alumnoId) return;
    // Subir archivos seleccionados y eliminaciones pendientes vía endpoint de staff (no depende de permisos de módulo alumnos)
    var personalId = (document.getElementById('modalStaffRolesPersonalId') && document.getElementById('modalStaffRolesPersonalId').value || '').trim();
    if (!personalId) {
        // fallback: si no hay personalId, no podemos subir por staff
        return;
    }
    var tiposPend = Object.keys(modalAlumnoPendienteEliminarDocs || {}).filter(function (t) { return !!modalAlumnoPendienteEliminarDocs[t]; });
    // Eliminaciones: usar endpoint de alumnos (permisos staff) por tipo
    for (var k = 0; k < tiposPend.length; k++) {
        try {
            await fetch(API_URL + '/alumnos/' + encodeURIComponent(String(alumnoId)) + '/documentos/' + encodeURIComponent(String(tiposPend[k])), {
                method: 'DELETE',
                headers: staffHeadersNoJson()
            });
        } catch (_) { /* no bloquear */ }
    }
    var docs = [
        { el: 'modalAluDocActa', tipo: 'ACTA_NACIMIENTO' },
        { el: 'modalAluDocCertEstudios', tipo: 'CONSTANCIA_ESTUDIOS' },
        { el: 'modalAluDocTituloCedula', tipo: 'TITULO_CEDULA', slot: 1 }
    ];
    var anyUpload = false;
    for (var i = 0; i < docs.length; i++) {
        var d = docs[i];
        var f = modalAlumnoArchivoInput(d.el);
        if (!f) continue;
        anyUpload = true;
        var q = 'tipo=' + encodeURIComponent(String(d.tipo));
        if (d.slot != null) q += '&slot=' + encodeURIComponent(String(d.slot));
        var resPut = await fetch(
            API_URL + '/personal/staff/' + encodeURIComponent(String(personalId)) + '/alumno/documentos/raw?' + q,
            {
                method: 'POST',
                headers: Object.assign({}, staffHeadersNoJson(), {
                    'Content-Type': (f && f.type) ? f.type : 'application/octet-stream',
                    'X-Filename': staffSafeUploadFilename(f, String(d.tipo).toLowerCase() + '.bin')
                }),
                body: f
            }
        );
        if (!resPut.ok) {
            var err = null;
            try { err = await resPut.json(); } catch (_) { }
            throw new Error((err && (err.error || err.message)) || 'No se pudieron subir los documentos del estudiante');
        }
    }
    if (!anyUpload && !tiposPend.length) return;
    modalAlumnoPendienteEliminarDocs = {};
}

async function staffAlumnoGuardarDocsDesdeExpediente(personalId, alumnoId) {
    if (!personalId || !alumnoId) return;
    var tiposPend = Object.keys(staffAlumnoPendienteEliminarDocs || {}).filter(function (t) { return !!staffAlumnoPendienteEliminarDocs[t]; });
    for (var k = 0; k < tiposPend.length; k++) {
        try {
            await fetch(API_URL + '/alumnos/' + encodeURIComponent(String(alumnoId)) + '/documentos/' + encodeURIComponent(String(tiposPend[k])), {
                method: 'DELETE',
                headers: staffHeadersNoJson()
            });
        } catch (_) { /* no bloquear */ }
    }
    var docs = [
        { el: 'staffAluDocCurp', tipo: 'CURP' },
        { el: 'staffAluDocIne', tipo: 'INE' },
        { el: 'staffAluDocConstanciaFiscal', tipo: 'CONSTANCIA_SITUACION_FISCAL' },
        { el: 'staffAluDocActa', tipo: 'ACTA_NACIMIENTO' },
        { el: 'staffAluDocCertEstudios', tipo: 'CONSTANCIA_ESTUDIOS' }
    ];
    var anyUpload = false;
    for (var i = 0; i < docs.length; i++) {
        var d = docs[i];
        var f = archivoExpedienteInput(d.el);
        if (!f) continue;
        anyUpload = true;
        var resPut = await fetch(
            API_URL + '/personal/staff/' + encodeURIComponent(String(personalId)) + '/alumno/documentos/raw?tipo=' + encodeURIComponent(String(d.tipo)),
            {
                method: 'POST',
                headers: Object.assign({}, staffHeadersNoJson(), {
                    'Content-Type': (f && f.type) ? f.type : 'application/octet-stream',
                    'X-Filename': staffSafeUploadFilename(f, String(d.tipo).toLowerCase() + '.bin')
                }),
                body: f
            }
        );
        if (!resPut.ok) {
            var err = null;
            try { err = await resPut.json(); } catch (_) { }
            throw new Error((err && (err.error || err.message)) || 'No se pudieron subir los documentos del estudiante');
        }
    }
    if (!anyUpload && !tiposPend.length) return;
    staffAlumnoPendienteEliminarDocs = {};
    // refrescar UI (si el bloque está presente)
    try { await staffAlumnoRefrescarDocsUi(alumnoId); } catch (_) {}
}

async function staffPrepararYMostrarModalDatosCoordRol() {
    var selP = document.getElementById('modalCoordRolPrograma');
    if (!selP) return;
    selP.innerHTML = '<option value="">Cargando…</option>';
    try {
        var r = await fetch(API_URL + '/programas-educativos', { headers: staffHeaders() });
        if (!r.ok) throw new Error('No se pudieron cargar los programas educativos.');
        var list = await r.json();
        selP.innerHTML = '<option value="">Seleccione…</option>';
        (list || []).forEach(function (pr) {
            var o = document.createElement('option');
            o.value = String(pr.id);
            o.textContent = (pr.clave ? String(pr.clave) + ' — ' : '') + (pr.nombre || '');
            selP.appendChild(o);
        });
    } catch (e) {
        alert(e.message || 'Error al cargar programas');
        return;
    }
    var m = document.getElementById('modalDatosCoordinadorRol');
    if (m && typeof bootstrap !== 'undefined' && bootstrap.Modal) {
        // Evitar backdrop doble: ocultar modal de roles antes de abrir éste
        var mRoles = document.getElementById('modalStaffRoles');
        if (mRoles) {
            var instRoles = bootstrap.Modal.getInstance(mRoles);
            if (instRoles) instRoles.hide();
        }
        bootstrap.Modal.getOrCreateInstance(m).show();
    }
}

/**
 * Paso 2 del modal «Roles»: carga la ficha y muestra expediente. Los roles se persisten solo al pulsar «Guardar expediente».
 * @param {{ postRellenar?: function(): void }} [opts]
 */
async function staffIrAPaso2ExpedienteSinPersistir(opts) {
    opts = opts || {};
    if (!staffRolesCommitPendiente || !staffRolesCommitPendiente.roles || !staffRolesCommitPendiente.roles.length) {
        alert('No hay roles pendientes de confirmar. Vuelva a abrir «Roles».');
        return;
    }
    var roles = staffRolesCommitPendiente.roles;
    staffRolesModalPendientesEnFormulario = roles.slice();

    var hidU = document.getElementById('modalStaffRolesUsuarioSoloAlumnoId');
    var uidSolo = hidU && hidU.value ? hidU.value.trim() : '';
    var pidFinal = (document.getElementById('modalStaffRolesPersonalId').value || '').trim();
    // Guardar el endpoint de roles para usarlo después (p. ej. coord. académico tras guardar expediente)
    var urlRoles = uidSolo
        ? (API_URL + '/personal/staff/by-usuario/' + encodeURIComponent(uidSolo) + '/roles')
        : (API_URL + '/personal/staff/' + encodeURIComponent(pidFinal) + '/roles');
    if (uidSolo && !pidFinal) {
        var rowU = staffList.find(function (r) { return String(r.usuarioId) === String(uidSolo); });
        if (rowU && rowU.personalId != null) {
            pidFinal = String(rowU.personalId);
            document.getElementById('modalStaffRolesPersonalId').value = pidFinal;
            urlRoles = API_URL + '/personal/staff/' + encodeURIComponent(pidFinal) + '/roles';
        }
    }
    if (!pidFinal) {
        if (uidSolo && staffRolesOperativos(roles)) {
            alert('Aún no hay ficha de personal vinculada. Recargue la lista e intente de nuevo.');
            return;
        }
        alert('No se encontró ficha de personal para continuar.');
        return;
    }
    try {
        await rellenarFormularioDesdePersonalId(parseInt(pidFinal, 10));
    } catch (err) {
        console.error(err);
        alert(err.message || 'No se pudo cargar la ficha para el expediente');
        return;
    }
    if (typeof opts.postRellenar === 'function') {
        try { opts.postRellenar(); } catch (e) { console.error(e); }
    }
    // Si incluye Estudiante y ya existe alumno, refrescar UI de documentos del alumno en el expediente
    try {
        var rowAct = staffList.find(function (x) { return String(x.personalId) === String(pidFinal); }) || null;
        var alumnoId = rowAct && rowAct.alumnoId != null ? rowAct.alumnoId : null;
        var hidA = document.getElementById('staffAlumnoIdHidden');
        if (hidA) hidA.value = alumnoId != null ? String(alumnoId) : '';
        if (alumnoId != null && roles && roles.indexOf('ALUMNO') !== -1) {
            await staffAlumnoRefrescarDocsUi(alumnoId);
        }
    } catch (_) {}
    if (!staffRolesOperativos(roles)) {
        modalStaffRolesMostrarPasoExpediente(true);
    } else {
        staffExpedienteSoloDesdeCarpeta = false;
        staffActualizarVisibilidadSeccionesFormulario();
        modalStaffRolesMostrarPasoExpediente(false);
    }
    var m = document.getElementById('modalStaffRoles');
    if (m && typeof bootstrap !== 'undefined' && bootstrap.Modal) {
        bootstrap.Modal.getOrCreateInstance(m).show();
    }
}

async function guardarModalStaffRoles() {
    if (!puedeGestionarStaffUi()) {
        alert('No tienes permisos para guardar roles.');
        return;
    }
    var hidU = document.getElementById('modalStaffRolesUsuarioSoloAlumnoId');
    var uidSolo = hidU && hidU.value ? hidU.value.trim() : '';
    var id = document.getElementById('modalStaffRolesPersonalId').value;
    var roles = obtenerRolesModalSeleccionados();
    if (!roles.length) {
        alert('Selecciona al menos un rol o «Sin rol asignado».');
        return;
    }
    var urlRoles = uidSolo
        ? (API_URL + '/personal/staff/by-usuario/' + encodeURIComponent(uidSolo) + '/roles')
        : (API_URL + '/personal/staff/' + encodeURIComponent(id) + '/roles');

    if (staffEsSoloSinRol(roles)) {
        var btn = document.getElementById('btnGuardarModalStaffRoles');
        if (btn) btn.disabled = true;
        try {
            var res = await fetch(urlRoles, {
                method: 'PATCH',
                headers: staffHeaders(),
                body: JSON.stringify({ roles: ['SIN_ROL'] })
            });
            var data = null;
            try { data = await res.json(); } catch (_) {}
            if (!res.ok) {
                alert((data && data.error) ? data.error : 'No se pudo guardar el rol');
                return;
            }
            staffCapturarSnapshotListaUi();
            await cargarListaStaff({ preservePage: true, preserveScroll: true });
            var mel = document.getElementById('modalStaffRoles');
            if (mel && typeof bootstrap !== 'undefined' && bootstrap.Modal) {
                var inst = bootstrap.Modal.getInstance(mel);
                if (inst) inst.hide();
            }
        } catch (e) {
            console.error(e);
            alert('Error de red al guardar el rol');
        } finally {
            if (btn) btn.disabled = false;
        }
        return;
    }

    var personalIdNum = id ? parseInt(id, 10) : NaN;
    var row = !isNaN(personalIdNum)
        ? staffList.find(function (x) { return x.personalId === personalIdNum; })
        : null;
    // Estudiante ya no usa un modal separado: se completa en el mismo expediente (paso 2).
    staffRolesCommitPendiente = { roles: roles.slice(), programaCoordinadoId: null };

    await staffIrAPaso2ExpedienteSinPersistir();
}

async function guardarStaff(opciones) {
    opciones = opciones || {};
    if (!puedeGestionarStaffUi()) {
        alert('No tienes permisos para guardar.');
        return;
    }
    var tel = normalizarTel10(document.getElementById('staffTelefono').value);
    var tr = document.getElementById('staffTelefono').value;
    if (String(tr || '').trim() && tel.length !== 10) {
        alert('El teléfono debe tener 10 dígitos.');
        return;
    }
    var te = normalizarTel10(document.getElementById('staffContactoTelefono').value);
    var ter = document.getElementById('staffContactoTelefono').value;
    if (String(ter || '').trim() && te.length !== 10) {
        alert('El teléfono de emergencia debe tener 10 dígitos.');
        return;
    }
    document.getElementById('staffTelefono').value = tel;
    document.getElementById('staffContactoTelefono').value = te;

    var body = construirPayloadStaff();
    if (!body) return;

    if (opciones.desdeModalExpedienteRoles && staffRolesCommitPendiente && staffRolesCommitPendiente.roles && staffRolesCommitPendiente.roles.length) {
        var pr = staffRolesCommitPendiente.roles;
        if (pr.indexOf('MAESTRO') !== -1) {
            var area = (document.getElementById('staffArea') && document.getElementById('staffArea').value || '').trim();
            var tipo = (document.getElementById('staffTipoMaestro') && document.getElementById('staffTipoMaestro').value || '').trim();
            var grado = (document.getElementById('staffGrado') && document.getElementById('staffGrado').value || '').trim();
            if (!area) {
                alert('Para el rol Docente, el área académica es obligatoria.');
                return;
            }
            if (!tipo) {
                alert('Para el rol Docente, el tipo de docente es obligatorio.');
                return;
            }
            if (!grado) {
                alert('Para el rol Docente, el grado académico es obligatorio.');
                return;
            }
        }
        var coordSeleccionado = pr.indexOf('COORDINADOR_ACADEMICO') !== -1;
        var rolesSinCoord = coordSeleccionado ? pr.filter(function (x) { return x !== 'COORDINADOR_ACADEMICO'; }) : pr.slice();
        body.roles = rolesSinCoord;
        // Coord. Acad. se asigna después de guardar el expediente (para pedir programa)
        if (coordSeleccionado) {
            // Se usará en el post-guardado para abrir modal de programa y asignar rol
            body.__coordPendiente = true;
        } else if (staffRolesCommitPendiente.programaCoordinadoId != null && staffRolesCommitPendiente.programaCoordinadoId !== '') {
            body.programaCoordinadoId = typeof staffRolesCommitPendiente.programaCoordinadoId === 'number'
                ? staffRolesCommitPendiente.programaCoordinadoId
                : parseInt(String(staffRolesCommitPendiente.programaCoordinadoId), 10);
        }
    }

    // Estudiante: enviar/actualizar sus datos académicos.
    // Nota: en edición, el usuario puede NO abrir el modal de roles; en ese caso `rolesAct`
    // puede no reflejar los roles efectivos y no debemos perder cambios de programas.
    var rolesAct = staffRolesActualesDesdeFormulario();
    var seccionAlumno = document.getElementById('seccionAlumnoStaff');
    var seccionAlumnoVisible = !!(seccionAlumno && seccionAlumno.style && seccionAlumno.style.display !== 'none' && !seccionAlumno.classList.contains('d-none'));
    var pidFormAlu = (document.getElementById('staffPersonalId') && document.getElementById('staffPersonalId').value || '').trim();
    var rowStaffAlu = pidFormAlu ? staffList.find(function (x) { return String(x.personalId) === String(pidFormAlu); }) : null;
    var alumnoSegunListaServidor = rowStaffAlu && rowStaffAlu.roles && rowStaffAlu.roles.indexOf('ALUMNO') !== -1;
    var tieneExpedienteAlumnoId = rowStaffAlu && rowStaffAlu.alumnoId != null;
    if (seccionAlumnoVisible || staffMostrarAlumnoExpediente(rolesAct) || alumnoSegunListaServidor || tieneExpedienteAlumnoId) {
        var matA = (document.getElementById('staffAlumnoMatricula') && document.getElementById('staffAlumnoMatricula').value || '').trim();
        var programas = staffAlumnoLeerProgramasDesdeUi();

        if (!matA) { alert('Para el rol Estudiante, la matrícula es obligatoria.'); return; }
        if (!programas || !programas.length) { alert('Para el rol Estudiante, agrega al menos un programa educativo.'); return; }
        for (var ip = 0; ip < programas.length; ip++) {
            if (!programas[ip].programaId) { alert('Selecciona programa en todas las filas.'); return; }
            if (!programas[ip].periodoAcademicoIngresoId) { alert('Selecciona periodo de ingreso en todas las filas.'); return; }
            if (!programas[ip].estatusMatricula) programas[ip].estatusMatricula = 'ACTIVA';
        }

        body.datosAlumno = {
            matricula: matA,
            programasAsignados: programas,
            fechaNacimiento: null
        };
    }

    var btn = document.getElementById('btnGuardarStaff');
    var btnModalExp = document.getElementById('btnGuardarModalStaffExpediente');
    if (btn) btn.disabled = true;
    if (opciones.desdeModalExpedienteRoles && btnModalExp) btnModalExp.disabled = true;

    // IDs actuales del formulario (alta/edición/migración)
    var legacyMid = (document.getElementById('staffLegacyMaestroId') && document.getElementById('staffLegacyMaestroId').value) || '';
    var pid = (document.getElementById('staffPersonalId') && document.getElementById('staffPersonalId').value) || '';

    try {
        var resData = null;
        if (legacyMid && !pid) {
            resData = await authFetch('/personal/staff/migrar-maestro/' + encodeURIComponent(String(legacyMid)), {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });
        } else if (pid) {
            if (body.password) delete body.password;
            if (staffNecesitaMultipartStaffPut()) {
                var fdPut = new FormData();
                fdPut.append('staff', new Blob([JSON.stringify(body)], { type: 'application/json' }));
                var fc = document.getElementById('staffDocCurp');
                var fi = document.getElementById('staffDocIne');
                var fs = document.getElementById('staffDocCsf');
                var ff = document.getElementById('staffFotoPerfil');
                if (fc && fc.files && fc.files[0]) fdPut.append('docCurp', fc.files[0]);
                if (fi && fi.files && fi.files[0]) fdPut.append('docIne', fi.files[0]);
                if (fs && fs.files && fs.files[0]) fdPut.append('docCsf', fs.files[0]);
                if (ff && ff.files && ff.files[0]) fdPut.append('fotoPerfil', ff.files[0]);
                var rowsSinId = Array.prototype.slice.call(document.querySelectorAll('#staffCedulasContainer .staff-cedula-row')).filter(function (row) {
                    return !row.getAttribute('data-cedula-id');
                });
                var nuevasLineas = (body.cedulasProfesionales || []).filter(function (x) { return !x.id; });
                for (var j = 0; j < nuevasLineas.length; j++) {
                    var fil = rowsSinId[j] && rowsSinId[j].querySelector('.staff-cedula-file-input');
                    var archivo = fil && fil.files && fil.files[0] ? fil.files[0] : new Blob([], { type: 'application/octet-stream' });
                    fdPut.append('cedulaProfesionalArchivo', archivo, fil && fil.files && fil.files[0] ? fil.files[0].name : 'sin-archivo.bin');
                }
                resData = await authFetch('/personal/staff/' + encodeURIComponent(String(pid)), { method: 'PUT', body: fdPut });
            } else {
                resData = await authFetch('/personal/staff/' + encodeURIComponent(String(pid)), {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(body)
                });
            }
        } else {
            var fCurp = document.getElementById('staffDocCurp');
            var fIne = document.getElementById('staffDocIne');
            var fCsf = document.getElementById('staffDocCsf');
            var fFoto = document.getElementById('staffFotoPerfil');
            var tieneArch = (fCurp && fCurp.files && fCurp.files[0])
                || (fIne && fIne.files && fIne.files[0])
                || (fCsf && fCsf.files && fCsf.files[0])
                || (fFoto && fFoto.files && fFoto.files[0]);
            if (tieneArch) {
                var fd = new FormData();
                fd.append('staff', new Blob([JSON.stringify(body)], { type: 'application/json' }));
                if (fCurp && fCurp.files && fCurp.files[0]) fd.append('docCurp', fCurp.files[0]);
                if (fIne && fIne.files && fIne.files[0]) fd.append('docIne', fIne.files[0]);
                if (fCsf && fCsf.files && fCsf.files[0]) fd.append('docCsf', fCsf.files[0]);
                if (fFoto && fFoto.files && fFoto.files[0]) fd.append('fotoPerfil', fFoto.files[0]);
                resData = await authFetch('/personal/staff', { method: 'POST', body: fd });
            } else {
                resData = await authFetch('/personal/staff', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(body)
                });
            }
        }
        var data = resData;

        // Aplicar eliminaciones pendientes de docs básicos (solo en expediente)
        if (opciones.desdeModalExpedienteRoles) {
            var personalIdDel = pid ? pid : (data && (data.personalId || data.id) ? String(data.personalId || data.id) : '');
            if (personalIdDel) {
                var tipos = Object.keys(staffPendienteEliminarDocsBasicos || {}).filter(function (t) { return !!staffPendienteEliminarDocsBasicos[t]; });
                for (var iDel = 0; iDel < tipos.length; iDel++) {
                    var tipoDel = tipos[iDel];
                    var cfg = staffDocBasicoConfig(tipoDel);
                    var inp = cfg ? document.getElementById(cfg.inputId) : null;
                    var hayNuevo = !!(inp && inp.files && inp.files[0]);
                    if (hayNuevo) {
                        delete staffPendienteEliminarDocsBasicos[tipoDel];
                        continue;
                    }
                    try {
                        await authFetch('/personal/staff/' + encodeURIComponent(String(personalIdDel)) + '/documentos/' + encodeURIComponent(tipoDel), { method: 'DELETE' });
                    } catch (_) { /* no bloquear guardado */ }
                    delete staffPendienteEliminarDocsBasicos[tipoDel];
                }
            }
        }

        // Estudiante: subir documentos del expediente del alumno también desde el formulario de edición/registro (no solo desde el modal de roles).
        // Esto mantiene el formulario "conectado" al expediente del estudiante.
        try {
            var personalIdDocs = pid ? String(pid) : (data && (data.personalId || data.id) ? String(data.personalId || data.id) : '');
            var alumnoIdForm = (document.getElementById('staffAlumnoIdHidden') && document.getElementById('staffAlumnoIdHidden').value || '').trim();
            if (personalIdDocs && alumnoIdForm) {
                await staffAlumnoGuardarDocsDesdeExpediente(personalIdDocs, alumnoIdForm);
            }
        } catch (eDocs) {
            console.error(eDocs);
            // No abortar guardado completo, pero sí avisar al usuario.
            if (typeof window.showSystemToast === 'function') {
                window.showSystemToast((eDocs && eDocs.message) ? eDocs.message : 'No se pudieron subir algunos documentos del estudiante', { type: 'warning', durationMs: 6500 });
            } else {
                alert((eDocs && eDocs.message) ? eDocs.message : 'No se pudieron subir algunos documentos del estudiante');
            }
        }

        if (opciones.desdeModalExpedienteRoles) {
            var rolesGuardados = body.roles && body.roles.length ? body.roles : null;
            var rolesSeleccionadosOriginal = (staffRolesCommitPendiente && staffRolesCommitPendiente.roles && staffRolesCommitPendiente.roles.length)
                ? staffRolesCommitPendiente.roles.slice()
                : null;
            var coordPendiente = !!body.__coordPendiente;
            if (body.__coordPendiente) delete body.__coordPendiente;
            staffRolesCommitPendiente = null;
            staffRolesModalPendientesEnFormulario = null;
            staffRolesPatchPendiente = { url: '', roles: [] };
            staffRolesCoordPatchPendiente = { url: '', roles: [] };
            alert('Expediente actualizado correctamente.');
            staffCapturarSnapshotListaUi();
            await cargarListaStaff({ preservePage: true, preserveScroll: true });
            if (rolesGuardados && rolesGuardados.indexOf('ALUMNO') !== -1) {
                try {
                    var hidUx = document.getElementById('modalStaffRolesUsuarioSoloAlumnoId');
                    var uidSoloX = hidUx && hidUx.value ? hidUx.value.trim() : '';
                    var pidFinalX = (document.getElementById('modalStaffRolesPersonalId').value || '').trim();
                    var rowA = null;
                    if (pidFinalX) rowA = staffList.find(function (r) { return String(r.personalId) === String(pidFinalX); }) || null;
                    if (!rowA && uidSoloX) rowA = staffList.find(function (r) { return String(r.usuarioId) === String(uidSoloX); }) || null;
                    var alumnoIdG = rowA && rowA.alumnoId != null ? rowA.alumnoId : null;
                    if (alumnoIdG != null) {
                        // Si existe bloque de documentos de alumno embebido, usarlo
                        var hidA = document.getElementById('expedienteAlumnoId');
                        if (hidA) hidA.value = String(alumnoIdG);
                        await staffAlumnoGuardarDocsDesdeExpediente(personalIdDel, alumnoIdG);
                    }
                } catch (e) {
                    console.error(e);
                    alert((e && e.message) ? e.message : 'No se pudieron subir algunos documentos del estudiante');
                }
            }
            staffRestaurarSeccionesExpedienteEnFormulario();
            modalStaffRolesResetPasosUi();
            var mel = document.getElementById('modalStaffRoles');
            if (mel && typeof bootstrap !== 'undefined' && bootstrap.Modal) {
                var inst = bootstrap.Modal.getInstance(mel);
                if (inst) inst.hide();
            }

            // Coord. Acad.: después de guardar expediente, pedir programa y recién asignar el rol
            if (coordPendiente) {
                try {
                    var pidFinalX = (document.getElementById('modalStaffRolesPersonalId').value || '').trim();
                    var hidU2 = document.getElementById('modalStaffRolesUsuarioSoloAlumnoId');
                    var uidSolo2 = hidU2 && hidU2.value ? hidU2.value.trim() : '';
                    var urlRoles = uidSolo2
                        ? (API_URL + '/personal/staff/by-usuario/' + encodeURIComponent(uidSolo2) + '/roles')
                        : (API_URL + '/personal/staff/' + encodeURIComponent(pidFinalX) + '/roles');
                    var rolesFinales = rolesSeleccionadosOriginal && rolesSeleccionadosOriginal.length
                        ? rolesSeleccionadosOriginal.slice()
                        : obtenerRolesModalSeleccionados();
                    if (rolesFinales.indexOf('COORDINADOR_ACADEMICO') === -1) rolesFinales.push('COORDINADOR_ACADEMICO');
                    staffRolesCoordCommitDespuesDeExpediente = { urlRoles: urlRoles, roles: rolesFinales.slice() };
                    await staffPrepararYMostrarModalDatosCoordRol();
                } catch (e) {
                    console.error(e);
                    alert('No se pudo abrir la selección de programa del coordinador académico');
                }
            }
        } else {
            if (typeof window.showSystemToast === 'function') {
                window.showSystemToast(pid || legacyMid ? 'Registro actualizado' : 'Usuario registrado correctamente', { type: 'success', durationMs: 4200 });
            } else {
                alert(pid || legacyMid ? 'Registro actualizado' : 'Registro creado');
            }
            limpiarFormularioStaff();
            staffCapturarSnapshotListaUi();
            await cargarListaStaff({ preservePage: true, preserveScroll: true });
            var tab = document.getElementById('tab-consulta-staff');
            if (tab && typeof bootstrap !== 'undefined' && bootstrap.Tab) {
                bootstrap.Tab.getOrCreateInstance(tab).show();
            }
        }
    } catch (e) {
        console.error(e);
        var msg = (e && e.message) ? e.message : 'Error al guardar';
        if (typeof window.showSystemToast === 'function') {
            window.showSystemToast(msg, { type: 'error', durationMs: 5200 });
            return;
        }
        alert(msg);
    } finally {
        if (btn) btn.disabled = false;
        if (btnModalExp) btnModalExp.disabled = false;
    }
}

async function eliminarStaffFila(personalId, legacyMaestroId, alumnoIdSolo) {
    var ok = false;
    if (typeof window.uiConfirm === 'function') {
        ok = await window.uiConfirm('¿Eliminar este registro? Se borrará también el acceso al sistema asociado.', {
            title: 'Eliminar usuario',
            subtitle: 'Esta acción no se puede deshacer',
            okText: 'Eliminar',
            cancelText: 'Cancelar'
        });
    }
    if (!ok) return;
    try {
        if (personalId) {
            await authFetch('/personal/staff/' + encodeURIComponent(String(personalId)), { method: 'DELETE' });
        } else if (legacyMaestroId) {
            await authFetch('/maestros/' + encodeURIComponent(String(legacyMaestroId)), { method: 'DELETE' });
        } else if (alumnoIdSolo) {
            await authFetch('/alumnos/' + encodeURIComponent(String(alumnoIdSolo)), { method: 'DELETE' });
        } else {
            return;
        }
        staffCapturarSnapshotListaUi();
        await cargarListaStaff({ preservePage: true, preserveScroll: true });
        if (typeof window.showSystemToast === 'function') {
            window.showSystemToast('Usuario eliminado correctamente', { type: 'success', durationMs: 4200 });
        }
    } catch (e) {
        if (typeof window.showSystemToast === 'function') {
            window.showSystemToast((e && e.message) ? e.message : 'Error al eliminar', { type: 'error', durationMs: 5200 });
            return;
        }
        alert((e && e.message) ? e.message : 'Error al eliminar');
    }
}

function filtrarStaffTabla(opts) {
    opts = opts || {};
    var t = (document.getElementById('buscarStaffInput').value || '').toLowerCase().trim();
    var fa = document.getElementById('staffFiltroActivo').value;
    var fr = (document.getElementById('staffFiltroRol') && document.getElementById('staffFiltroRol').value) || '';
    var fp = (document.getElementById('staffFiltroPrograma') && document.getElementById('staffFiltroPrograma').value) || '';
    var base = staffList.slice();
    var filtrado = base.filter(function (row) {
        var nombre = staffNormalizarTexto(staffNombreCompleto(row));
        var okTxt = !t || nombre.indexOf(staffNormalizarTexto(t)) !== -1
            || staffNormalizarTexto(row.correoInstitucional || '').indexOf(staffNormalizarTexto(t)) !== -1
            || staffNormalizarTexto(row.curp || '').indexOf(staffNormalizarTexto(t)) !== -1;
        var okAct = !fa || String(row.activo) === fa;
        var okRol = !fr || ((row.roles || []).some(function (r) { return String(r) === String(fr); }));
        var okProg = !fp || (row.programaId != null && String(row.programaId) === String(fp));
        return okTxt && okAct && okRol && okProg;
    });
    // Orden por nombre
    filtrado.sort(function (a, b) {
        var na = staffNormalizarTexto(staffNombreCompleto(a));
        var nb = staffNormalizarTexto(staffNombreCompleto(b));
        var c = na.localeCompare(nb, 'es');
        return staffSortNombreDir === 'desc' ? -c : c;
    });
    staffUltimoFiltrado = filtrado;
    if (!opts.preservePage) {
        staffPageIndex = 1;
    } else {
        // Mantener la página si sigue siendo válida
        var totalPages = staffTotalPaginas(staffUltimoFiltrado.length || 0);
        if (staffPageIndex > totalPages) staffPageIndex = totalPages;
        if (staffPageIndex < 1) staffPageIndex = 1;
    }
    staffRenderPagina();
}

function descargarExcelStaff() {
    if (typeof XLSX === 'undefined') {
        alert('No se cargó la librería Excel.');
        return;
    }
    var lista = staffList || [];
    var h = [
        'CURP', 'Nombres', 'Apellido Paterno', 'Apellido Materno',
        'Género', 'Fecha de nacimiento',
        'Teléfono',
        'Correo institucional', 'Correo personal (opcional)',
        'Contacto de emergencia (opcional)', 'Teléfono de emergencia (opcional)',
        'Contraseña (opcional)'
    ];
    var filas = [h];
    lista.forEach(function (row) {
        filas.push([
            row.curp || '',
            row.nombre || '',
            row.apellidoPaterno || '',
            row.apellidoMaterno || '',
            row.sexo || '',
            staffFormatFechaDDMMYYYY(row.fechaNacimiento),
            row.telefono || '',
            row.correoInstitucional || '',
            row.correoPersonal || '',
            row.nombreContactoEmergencia || '',
            row.telefonoContactoEmergencia || '',
            '' // nunca exportar contraseñas
        ]);
    });
    var wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(filas), 'Personal');
    XLSX.writeFile(wb, 'Personal_institucional_' + new Date().toISOString().slice(0, 10) + '.xlsx');
}

function staffNormalizarHeaderExcel(h) {
    return String(h || '')
        .trim()
        .toLowerCase()
        .replace(/\s+/g, ' ')
        .replace(/[()]/g, '')
        .replace(/á/g, 'a').replace(/é/g, 'e').replace(/í/g, 'i').replace(/ó/g, 'o').replace(/ú/g, 'u')
        .replace(/ñ/g, 'n');
}

function staffParseSiNo(v) {
    var t = String(v || '').trim().toLowerCase();
    if (!t) return null;
    if (['si', 'sí', '1', 'true', 'activo', 'activa'].includes(t)) return true;
    if (['no', '0', 'false', 'inactivo', 'inactiva'].includes(t)) return false;
    return null;
}

function staffParseFechaExcel(v) {
    if (!v) return '';
    // Si ya viene yyyy-mm-dd, usarlo
    var s = String(v).trim();
    if (/^\d{4}-\d{2}-\d{2}$/.test(s)) return s;
    // Si viene dd/mm/yyyy, convertir a yyyy-mm-dd
    var m = s.match(/^(\d{2})\/(\d{2})\/(\d{4})$/);
    if (m) {
        var dd = m[1];
        var mm = m[2];
        var yyyy = m[3];
        var dNum = parseInt(dd, 10);
        var mNum = parseInt(mm, 10);
        var yNum = parseInt(yyyy, 10);
        if (yNum >= 1900 && yNum <= 2100 && mNum >= 1 && mNum <= 12 && dNum >= 1 && dNum <= 31) {
            return yyyy + '-' + mm + '-' + dd;
        }
    }
    // XLSX a veces entrega Date o número serial
    try {
        if (v instanceof Date && !isNaN(v.getTime())) return v.toISOString().slice(0, 10);
    } catch (_) {}
    // XLSX a veces entrega número serial de Excel
    if (typeof v === 'number' && isFinite(v)) {
        try {
            var ms = Math.round((v - 25569) * 86400 * 1000);
            var d = new Date(ms);
            if (!isNaN(d.getTime())) return d.toISOString().slice(0, 10);
        } catch (_) {}
    }
    return s;
}

function staffFormatFechaDDMMYYYY(value) {
    if (!value) return '';
    // value puede venir como Date, ISO, o string ya formateado
    try {
        if (value instanceof Date && !isNaN(value.getTime())) {
            var dd = String(value.getDate()).padStart(2, '0');
            var mm = String(value.getMonth() + 1).padStart(2, '0');
            var yyyy = String(value.getFullYear());
            return dd + '/' + mm + '/' + yyyy;
        }
    } catch (_) {}
    var s = String(value).trim();
    var mIso = s.match(/^(\d{4})-(\d{2})-(\d{2})/);
    if (mIso) return mIso[3] + '/' + mIso[2] + '/' + mIso[1];
    if (/^\d{2}\/\d{2}\/\d{4}$/.test(s)) return s;
    return s;
}

async function importarExcelStaff(file) {
    if (typeof XLSX === 'undefined') {
        alert('No se cargó la librería Excel.');
        return;
    }
    if (!file) return;
    var data = await file.arrayBuffer();
    var wb = XLSX.read(data, { type: 'array' });
    var sheetName = wb.SheetNames && wb.SheetNames.length ? wb.SheetNames[0] : null;
    if (!sheetName) {
        alert('El archivo no tiene hojas.');
        return;
    }
    var ws = wb.Sheets[sheetName];
    var rows = XLSX.utils.sheet_to_json(ws, { header: 1, defval: '' });
    if (!rows || rows.length < 2) {
        alert('El Excel no tiene filas para importar.');
        return;
    }
    var header = rows[0];
    var idx = {};
    header.forEach(function (h, i) {
        idx[staffNormalizarHeaderExcel(h)] = i;
    });

    function col(name) {
        return idx[name] != null ? idx[name] : null;
    }

    var required = [
        'curp',
        'nombres',
        'apellido paterno',
        'apellido materno',
        'genero',
        'fecha de nacimiento',
        'telefono',
        'correo institucional'
    ];
    var faltantes = required.filter(function (k) { return col(k) == null; });
    if (faltantes.length) {
        alert('Faltan columnas requeridas en el Excel: ' + faltantes.join(', '));
        return;
    }

    var btn = document.getElementById('btnImportarExcelStaff');
    if (btn) btn.disabled = true;
    var btnModal = document.getElementById('btnEjecutarCargaMasivaStaff');
    var resultadoEl = document.getElementById('staffCargaMasivaResultado');
    if (btnModal) {
        btnModal.disabled = true;
        btnModal.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Procesando...';
    }
    if (resultadoEl) {
        resultadoEl.className = 'alert alert-info';
        resultadoEl.textContent = 'Procesando archivo...';
        resultadoEl.classList.remove('d-none');
    }
    var ok = 0;
    var err = 0;
    var errs = [];

    function safeCell(rowArr, idx) {
        try {
            return idx != null ? rowArr[idx] : '';
        } catch (_) {
            return '';
        }
    }

    function normalizeExcelText(v) {
        if (v == null) return '';
        var s = String(v);
        // Evitar "undefined"/"null"
        if (s === 'undefined' || s === 'null') return '';
        return s.trim();
    }

    function descargarErroresComoTxt(lista) {
        try {
            var contenido = (lista || []).join('\r\n');
            var blob = new Blob([contenido], { type: 'text/plain;charset=utf-8' });
            var a = document.createElement('a');
            a.href = URL.createObjectURL(blob);
            a.download = 'errores_importacion_usuarios_' + new Date().toISOString().slice(0, 10) + '.txt';
            document.body.appendChild(a);
            a.click();
            a.remove();
            setTimeout(function () { try { URL.revokeObjectURL(a.href); } catch (_) {} }, 1000);
        } catch (_) { /* ignorar */ }
    }

    for (var r = 1; r < rows.length; r++) {
        var row = rows[r];
        // Saltar filas vacías
        var curp = normalizeExcelText(safeCell(row, col('curp')));
        var correo = normalizeExcelText(safeCell(row, col('correo institucional')));
        if (!curp && !correo) continue;

        function colAny(names) {
            for (var i = 0; i < names.length; i++) {
                var c = col(names[i]);
                if (c != null) return c;
            }
            return null;
        }

        var payload = {
            curp: curp,
            nombre: normalizeExcelText(safeCell(row, col('nombres'))),
            apellidoPaterno: normalizeExcelText(safeCell(row, col('apellido paterno'))),
            apellidoMaterno: normalizeExcelText(safeCell(row, col('apellido materno'))),
            correoInstitucional: correo,
            telefono: normalizeExcelText(safeCell(row, col('telefono'))),
            sexo: normalizeExcelText(safeCell(row, col('genero'))).toUpperCase(),
            fechaNacimiento: staffParseFechaExcel(safeCell(row, col('fecha de nacimiento'))),
            correoPersonal: (function () {
                var ccp = colAny(['correo personal', 'correo personal opcional']);
                return ccp != null ? normalizeExcelText(safeCell(row, ccp)) : '';
            })(),
            nombreContactoEmergencia: (function () {
                var cce = colAny(['contacto de emergencia', 'contacto de emergencia opcional']);
                var v = cce != null ? normalizeExcelText(safeCell(row, cce)) : '';
                return v || null;
            })(),
            telefonoContactoEmergencia: (function () {
                var cte = colAny(['telefono de emergencia', 'teléfono de emergencia', 'telefono de emergencia opcional', 'teléfono de emergencia opcional']);
                var v = cte != null ? normalizeExcelText(safeCell(row, cte)) : '';
                v = normalizarTel10(v);
                return v || null;
            })()
        };
        // Opcional: contraseña (si viene vacía, el backend usará la contraseña por defecto del sistema)
        if (col('contrasena opcional') != null) {
            var pwd = String(row[col('contrasena opcional')] || '').trim();
            if (pwd) payload.password = pwd;
        } else if (col('contrasena') != null) {
            var pwd2 = String(row[col('contrasena')] || '').trim();
            if (pwd2) payload.password = pwd2;
        } else if (col('contraseña opcional') != null) {
            var pwd3 = String(row[col('contraseña opcional')] || '').trim();
            if (pwd3) payload.password = pwd3;
        } else if (col('contraseña') != null) {
            var pwd4 = String(row[col('contraseña')] || '').trim();
            if (pwd4) payload.password = pwd4;
        }

        try {
            // Usar authFetch para soportar refresh token durante cargas largas
            await authFetch('/personal/staff', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            ok++;
        } catch (e) {
            err++;
            var msg = (e && e.message) ? e.message : 'No se pudo crear';
            var hint = (curp ? (' CURP=' + curp) : '') + (correo ? (' Correo=' + correo) : '');
            errs.push('Fila ' + (r + 1) + ':' + hint + ' → ' + msg);
        }
    }

    // Importación: normalmente el usuario quiere volver a la misma página/posición en lista
    staffCapturarSnapshotListaUi();
    await cargarListaStaff({ preservePage: true, preserveScroll: true });
    if (typeof window.showSystemToast === 'function') {
        window.showSystemToast('Importación terminada: ' + ok + ' creados, ' + err + ' con error', { type: err ? 'warning' : 'success', durationMs: 5200 });
    } else {
        alert('Importación terminada: ' + ok + ' creados, ' + err + ' con error');
    }
    if (resultadoEl) {
        resultadoEl.className = err ? 'alert alert-warning' : 'alert alert-success';
        resultadoEl.textContent = 'Importación terminada: ' + ok + ' creados, ' + err + ' con error';
        resultadoEl.classList.remove('d-none');
    }
    if (errs.length) {
        console.warn('Errores importación Excel:', errs);
        // Mostrar un resumen visible y permitir descargar el detalle
        var resumen = errs.slice(0, 8).join('\n');
        alert('Algunas filas no se pudieron importar.\n\n' + resumen + (errs.length > 8 ? '\n\n(… y ' + (errs.length - 8) + ' más)' : '') + '\n\nSe descargará un archivo con el detalle.');
        descargarErroresComoTxt(errs);
    }
    if (btn) btn.disabled = false;
    if (btnModal) {
        btnModal.disabled = false;
        btnModal.innerHTML = '<i class="bi bi-upload me-1"></i>Procesar archivo';
    }
}

function archivoExpedienteInput(inpId) {
    var el = document.getElementById(inpId);
    return el && el.files && el.files[0] ? el.files[0] : null;
}

function staffBuscarRowPorTr(tr) {
    if (!tr) return null;
    var pid = tr.getAttribute('data-personal-id');
    if (pid) {
        var p = parseInt(pid, 10);
        return staffList.find(function (r) { return r.personalId === p; }) || null;
    }
    var uid = tr.getAttribute('data-usuario-id');
    if (uid) {
        return staffList.find(function (r) { return String(r.usuarioId) === String(uid); }) || null;
    }
    var mid = tr.getAttribute('data-maestro-id');
    if (mid) {
        var m = parseInt(mid, 10);
        return staffList.find(function (r) { return r.maestroId === m; }) || null;
    }
    return null;
}

function staffRowKeyFromListItem(row) {
    // Prioridad: personalId (ficha staff), maestroId (legado), alumnoId (solo estudiante)
    if (row && row.personalId != null) return 'P:' + String(row.personalId);
    if (row && row.maestroId != null) return 'M:' + String(row.maestroId);
    if (row && row.alumnoId != null) return 'A:' + String(row.alumnoId);
    // Fallback (no debería ocurrir): usar usuarioId si existe
    if (row && row.usuarioId != null) return 'U:' + String(row.usuarioId);
    return 'X:' + Math.random().toString(16).slice(2);
}

function staffParseRowKey(key) {
    var s = String(key || '');
    var parts = s.split(':');
    if (parts.length < 2) return null;
    var kind = parts[0];
    var id = parts.slice(1).join(':');
    if (!id) return null;
    var n = parseInt(id, 10);
    if (isNaN(n)) return null;
    if (kind === 'P') return { personalId: n, legacyMaestroId: null, alumnoIdSolo: null };
    if (kind === 'M') return { personalId: null, legacyMaestroId: n, alumnoIdSolo: null };
    if (kind === 'A') return { personalId: null, legacyMaestroId: null, alumnoIdSolo: n };
    return null;
}

function staffSelectedKeysArray() {
    return Object.keys(staffSelectedRowKeys || {}).filter(function (k) { return !!staffSelectedRowKeys[k]; });
}

function staffActualizarBulkUi() {
    var btn = document.getElementById('btnEliminarSeleccionadosStaff');
    var chkAll = document.getElementById('staffSelectAllChk');
    var keys = staffSelectedKeysArray();
    if (btn) {
        btn.disabled = keys.length === 0 || !puedeGestionarStaffUi();
        btn.title = keys.length ? ('Eliminar ' + keys.length + ' seleccionados') : 'Seleccione usuarios en la lista';
    }
    if (chkAll) {
        var visibles = document.querySelectorAll('#staffTableBody .staff-row-select-chk');
        var total = visibles.length;
        var checked = 0;
        for (var i = 0; i < visibles.length; i++) {
            if (visibles[i].checked) checked++;
        }
        chkAll.indeterminate = checked > 0 && checked < total;
        chkAll.checked = total > 0 && checked === total;
    }
}

async function staffEliminarSeleccionados() {
    if (!puedeGestionarStaffUi()) {
        alert('No tienes permisos para eliminar.');
        return;
    }
    var keys = staffSelectedKeysArray();
    if (!keys.length) return;

    var ok = false;
    if (typeof window.uiConfirm === 'function') {
        ok = await window.uiConfirm('¿Eliminar ' + keys.length + ' usuario(s) seleccionado(s)? Se borrará también el acceso al sistema asociado.', {
            title: 'Eliminar usuarios',
            subtitle: 'Esta acción no se puede deshacer',
            okText: 'Eliminar',
            cancelText: 'Cancelar'
        });
    }
    if (!ok) return;

    var btn = document.getElementById('btnEliminarSeleccionadosStaff');
    if (btn) btn.disabled = true;

    var okN = 0;
    var errN = 0;
    var errs = [];

    for (var i = 0; i < keys.length; i++) {
        var parsed = staffParseRowKey(keys[i]);
        if (!parsed) {
            errN++;
            errs.push('Key inválida: ' + keys[i]);
            continue;
        }
        try {
            if (parsed.personalId) {
                await authFetch('/personal/staff/' + encodeURIComponent(String(parsed.personalId)), { method: 'DELETE' });
            } else if (parsed.legacyMaestroId) {
                await authFetch('/maestros/' + encodeURIComponent(String(parsed.legacyMaestroId)), { method: 'DELETE' });
            } else if (parsed.alumnoIdSolo) {
                await authFetch('/alumnos/' + encodeURIComponent(String(parsed.alumnoIdSolo)), { method: 'DELETE' });
            } else {
                throw new Error('Identificador inválido');
            }
            okN++;
            delete staffSelectedRowKeys[keys[i]];
        } catch (e) {
            errN++;
            errs.push(keys[i] + ': ' + ((e && e.message) ? e.message : 'No se pudo eliminar'));
        }
    }

    // Eliminación masiva: mantener página/posición (no regresar a página 1)
    staffCapturarSnapshotListaUi();
    await cargarListaStaff({ preservePage: true, preserveScroll: true });
    staffActualizarBulkUi();
    if (typeof window.showSystemToast === 'function') {
        window.showSystemToast('Eliminación masiva: ' + okN + ' eliminados, ' + errN + ' con error', { type: errN ? 'warning' : 'success', durationMs: 5200 });
    } else {
        alert('Eliminación masiva: ' + okN + ' eliminados, ' + errN + ' con error');
    }
    if (errs.length) {
        console.warn('Errores eliminación masiva:', errs);
    }
    if (btn) btn.disabled = false;
}

function staffBindBulkSelectionUi() {
    var tbody = document.getElementById('staffTableBody');
    if (tbody && !tbody.__bulkBound) {
        tbody.__bulkBound = true;
        tbody.addEventListener('change', function (ev) {
            var t = ev && ev.target ? ev.target : null;
            if (!t) return;
            if (t.classList && t.classList.contains('staff-row-select-chk')) {
                var key = t.getAttribute('data-row-key') || '';
                if (t.checked) staffSelectedRowKeys[key] = true;
                else delete staffSelectedRowKeys[key];
                staffActualizarBulkUi();
            }
        });
    }

    var chkAll = document.getElementById('staffSelectAllChk');
    if (chkAll && !chkAll.__bulkBound) {
        chkAll.__bulkBound = true;
        chkAll.addEventListener('change', function () {
            var visibles = document.querySelectorAll('#staffTableBody .staff-row-select-chk');
            for (var i = 0; i < visibles.length; i++) {
                visibles[i].checked = chkAll.checked;
                var key = visibles[i].getAttribute('data-row-key') || '';
                if (chkAll.checked) staffSelectedRowKeys[key] = true;
                else delete staffSelectedRowKeys[key];
            }
            staffActualizarBulkUi();
        });
    }

    var btnDel = document.getElementById('btnEliminarSeleccionadosStaff');
    if (btnDel && !btnDel.__bulkBound) {
        btnDel.__bulkBound = true;
        btnDel.addEventListener('click', function (e) {
            e.preventDefault();
            e.stopPropagation();
            staffEliminarSeleccionados();
        });
    }

    staffActualizarBulkUi();
}

/**
 * Expediente: datos y documentos según el tipo de ficha (personal institucional, legado docente o solo estudiante).
 */
async function abrirExpedienteDesdeTablaRow(row) {
    if (!row) {
        alert('No se encontró el registro en la lista.');
        return;
    }
    staffExpedienteSoloDesdeCarpeta = true;
    staffRestaurarSeccionesExpedienteEnFormulario();
    var hid = document.getElementById('modalStaffRolesPersonalId');
    var hidU = document.getElementById('modalStaffRolesUsuarioSoloAlumnoId');
    if (hidU) hidU.value = '';

    try {
        if (row.personalId != null) {
            // Si el usuario es alumno, abrir directamente el expediente de estudiante (documentos completos)
            if (row.alumnoId != null && row.roles && Array.isArray(row.roles) && row.roles.indexOf('ALUMNO') !== -1) {
                staffExpedienteSoloDesdeCarpeta = false;
                await abrirModalExpedienteAlumno(row.alumnoId);
                return;
            }
            if (hid) hid.value = String(row.personalId);
            await rellenarFormularioDesdePersonalId(row.personalId);
            // Para usuarios SIN_ROL, el expediente debe mostrar al menos la sección de documentación.
            staffActualizarVisibilidadSeccionesFormulario({ forceDocs: true });
            modalStaffRolesMostrarPasoExpediente(false);
            var tit = document.getElementById('modalStaffRolesLabel');
            if (tit) tit.textContent = 'Expediente del usuario';
            var m = document.getElementById('modalStaffRoles');
            if (m && typeof bootstrap !== 'undefined' && bootstrap.Modal) {
                bootstrap.Modal.getOrCreateInstance(m).show();
            }
            return;
        }
        if (row.soloMaestroLegacy && row.maestroId != null) {
            if (hid) hid.value = '';
            await rellenarFormularioDesdeMaestroId(row.maestroId);
            staffActualizarVisibilidadSeccionesFormulario();
            modalStaffRolesMostrarPasoExpediente(false);
            var titL = document.getElementById('modalStaffRolesLabel');
            if (titL) titL.textContent = 'Expediente del usuario';
            var mL = document.getElementById('modalStaffRoles');
            if (mL && typeof bootstrap !== 'undefined' && bootstrap.Modal) {
                bootstrap.Modal.getOrCreateInstance(mL).show();
            }
            return;
        }
        if (row.alumnoId != null) {
            if (hid) hid.value = '';
            staffExpedienteSoloDesdeCarpeta = false;
            await abrirModalExpedienteAlumno(row.alumnoId);
            return;
        }
        staffExpedienteSoloDesdeCarpeta = false;
        alert('No hay expediente vinculado a este registro. Use «Editar» o «Roles» para completar la ficha.');
    } catch (e) {
        staffExpedienteSoloDesdeCarpeta = false;
        alert(e.message || 'Error al abrir expediente');
    }
}

async function abrirModalExpedienteAlumno(alumnoId) {
    try {
        expedienteAlumnoResetPendientes();
        var res = await fetch(API_URL + '/alumnos/' + alumnoId, { headers: staffHeadersNoJson() });
        if (!res.ok) throw new Error('No se pudo cargar el expediente');
        var a = await res.json();
        var hid = document.getElementById('expedienteAlumnoId');
        var mat = document.getElementById('expedienteAlumnoMatricula');
        var nom = document.getElementById('expedienteAlumnoNombre');
        var curp = document.getElementById('expedienteAlumnoCurp');
        var correo = document.getElementById('expedienteAlumnoCorreo');
        var progs = document.getElementById('expedienteAlumnoProgramas');
        if (hid) hid.value = String(alumnoId);
        if (mat) mat.textContent = a.matricula || '—';
        if (nom) nom.textContent = [a.nombre, a.apellidoPaterno, a.apellidoMaterno].filter(Boolean).join(' ') || '—';
        if (curp) curp.textContent = a.curp || '—';
        if (correo) correo.textContent = a.correoInstitucional || '—';
        if (progs) {
            var insc = Array.isArray(a.programasAsignados) ? a.programasAsignados : [];
            var nombres = insc
                .map(function (x) { return x && x.programa && x.programa.nombre ? String(x.programa.nombre).trim() : ''; })
                .filter(function (s) { return !!s; });
            // Legacy: si no trae programasAsignados, intentar programa legacy
            if (!nombres.length && a.programa && a.programa.nombre) {
                nombres = [String(a.programa.nombre).trim()];
            }
            // Únicos y en una sola línea compacta
            var uniq = [];
            var seen = {};
            nombres.forEach(function (s) {
                var k = s.toLowerCase();
                if (seen[k]) return;
                seen[k] = true;
                uniq.push(s);
            });
            if (!uniq.length) {
                progs.textContent = '—';
            } else if (uniq.length === 1) {
                progs.textContent = uniq[0];
            } else {
                // Mostrar en columna para múltiples programas
                progs.innerHTML = '<div class="d-flex flex-column gap-1 text-start">' +
                    uniq.map(function (s) { return '<div>' + escapeStaff(s) + '</div>'; }).join('') +
                    '</div>';
            }
        }
        var imgF = document.getElementById('expedienteAlumnoFotoImg');
        var phF = document.getElementById('expedienteAlumnoFotoPh');
        if (imgF && phF) {
            try {
                if (imgF.src && imgF.src.indexOf('blob:') === 0) URL.revokeObjectURL(imgF.src);
            } catch (e) { /* ignore */ }
            imgF.classList.add('d-none');
            phF.classList.remove('d-none');
            fetch(API_URL + '/alumnos/' + encodeURIComponent(String(alumnoId)) + '/foto', { headers: staffHeadersNoJson() })
                .then(function (rf) {
                    if (!rf.ok) throw new Error('no-foto');
                    return rf.blob();
                })
                .then(function (blob) {
                    imgF.src = URL.createObjectURL(blob);
                    imgF.classList.remove('d-none');
                    phF.classList.add('d-none');
                })
                .catch(function () { /* sin foto */ });
        }
        // No limpiar inputs aquí: se renderizan con estado "cargado" o "sin archivo" según backend.
        await expedienteAlumnoRefrescarDocsUi();
        var m = document.getElementById('modalExpedienteAlumno');
        if (m && typeof bootstrap !== 'undefined' && bootstrap.Modal) {
            bootstrap.Modal.getOrCreateInstance(m).show();
        }
    } catch (e) {
        alert(e.message || 'Error al abrir expediente');
    }
}

function expedienteAlumnoDocConfig(tipo) {
    switch (tipo) {
        case 'ACTA_NACIMIENTO': return { wrapId: 'expDocActaWrap', inputId: 'expDocActa', label: 'Acta de nacimiento' };
        case 'CONSTANCIA_ESTUDIOS': return { wrapId: 'expDocCertEstudiosWrap', inputId: 'expDocCertEstudios', label: 'Certificado de estudios' };
        case 'CURP': return { wrapId: 'expDocCurpWrap', inputId: 'expDocCurp', label: 'CURP' };
        case 'INE': return { wrapId: 'expDocIneWrap', inputId: 'expDocIne', label: 'INE' };
        case 'CONSTANCIA_SITUACION_FISCAL': return { wrapId: 'expDocConstanciaFiscalWrap', inputId: 'expDocConstanciaFiscal', label: 'Constancia de situación fiscal' };
        default: return null;
    }
}

function expedienteAlumnoAbrirDocumento(tipo, docId) {
    var aid = document.getElementById('expedienteAlumnoId') && document.getElementById('expedienteAlumnoId').value;
    if (!aid) return;
    var url = docId
        ? API_URL + '/alumnos/' + encodeURIComponent(aid) + '/documentos/descarga?docId=' + encodeURIComponent(String(docId))
        : API_URL + '/alumnos/' + encodeURIComponent(aid) + '/documentos/' + encodeURIComponent(tipo) + '/archivo';
    fetch(url, { headers: staffHeadersNoJson() })
        .then(function (r) {
            if (!r.ok) throw new Error('No se pudo abrir');
            return r.blob();
        })
        .then(function (b) {
            window.open(URL.createObjectURL(b), '_blank');
        })
        .catch(function () { alert('No se pudo abrir el archivo'); });
}

async function expedienteAlumnoEliminarDocumentoPorDocId(docId) {
    var aid = (document.getElementById('expedienteAlumnoId') && document.getElementById('expedienteAlumnoId').value) || '';
    aid = String(aid || '').trim();
    if (!aid || !docId) return;
    var ok = false;
    try {
        ok = (typeof window.uiConfirm === 'function')
            ? await window.uiConfirm('¿Eliminar este documento del expediente del alumno?', { subtitle: 'Esta acción no se puede deshacer.' })
            : confirm('¿Eliminar este documento del expediente del alumno?\n\nEsta acción no se puede deshacer.');
    } catch (_) {
        ok = false;
    }
    if (!ok) return;
    await fetch(API_URL + '/alumnos/' + encodeURIComponent(aid) + '/documentos/doc/' + encodeURIComponent(String(docId)), {
        method: 'DELETE',
        headers: staffHeadersNoJson()
    });
    await expedienteAlumnoRefrescarDocsUi();
}

async function expedienteAlumnoEliminarDocumento(tipo) {
    var aid = (document.getElementById('expedienteAlumnoId') && document.getElementById('expedienteAlumnoId').value) || '';
    aid = String(aid || '').trim();
    if (!aid) return;
    var ok = false;
    try {
        ok = (typeof window.uiConfirm === 'function')
            ? await window.uiConfirm('¿Eliminar este documento del expediente del alumno?', { subtitle: 'Esta acción no se puede deshacer.' })
            : confirm('¿Eliminar este documento del expediente del alumno?\n\nEsta acción no se puede deshacer.');
    } catch (_) {
        ok = false;
    }
    if (!ok) return;
    await fetch(API_URL + '/alumnos/' + encodeURIComponent(aid) + '/documentos/' + encodeURIComponent(String(tipo)), {
        method: 'DELETE',
        headers: staffHeadersNoJson()
    });
    delete expedienteAlumnoPendienteEliminarDocs[String(tipo)];
    await expedienteAlumnoRefrescarDocsUi();
}

function expedienteAlumnoSetDocUi(tipo, meta) {
    var cfg = expedienteAlumnoDocConfig(tipo);
    if (!cfg) return;
    var wrap = document.getElementById(cfg.wrapId);
    var inp = document.getElementById(cfg.inputId);
    if (!wrap || !inp) return;
    wrap.innerHTML = '';
    var filename = meta && (meta.filename || meta.archivoUrl || meta.nombreArchivo) ? (meta.filename || meta.archivoUrl || meta.nombreArchivo) : null;
    var entregado = meta && meta.entregado === true;

    if (entregado && filename) {
        var row = document.createElement('div');
        row.className = 'd-flex align-items-center justify-content-between gap-2 border rounded px-2 py-2 bg-white';
        row.innerHTML =
            '<div class="small text-truncate" style="min-width:0;">' +
              '<span class="text-muted">' + escapeStaff(cfg.label) + ':</span> ' +
              '<strong>' + escapeStaff(filename) + '</strong>' +
            '</div>' +
            '<div class="btn-group btn-group-sm" role="group">' +
              '<button type="button" class="btn btn-outline-secondary" data-action="ver" title="Ver"><i class="bi bi-eye"></i></button>' +
              '<button type="button" class="btn btn-outline-danger" data-action="quitar" title="Quitar"><i class="bi bi-x-lg"></i></button>' +
            '</div>';
        wrap.appendChild(row);
        inp.value = '';
        inp.classList.add('d-none');
        wrap.appendChild(inp);
        row.querySelector('[data-action="ver"]').addEventListener('click', function () { expedienteAlumnoAbrirDocumento(tipo); });
        row.querySelector('[data-action="quitar"]').addEventListener('click', function () { expedienteAlumnoEliminarDocumento(tipo); });
        return;
    }

    inp.classList.remove('d-none');

    // UI: X dentro del selector (input-group). No mostrar "Seleccionado: ..."
    var group = document.createElement('div');
    group.className = 'input-group input-group-sm';
    wrap.appendChild(group);
    group.appendChild(inp);

    var btnClear = document.createElement('button');
    btnClear.type = 'button';
    btnClear.className = 'btn btn-outline-danger btn-sm';
    btnClear.title = 'Quitar selección';
    btnClear.innerHTML = '<i class="bi bi-x-lg"></i>';
    btnClear.disabled = true;
    group.appendChild(btnClear);

    function syncClearEnabled() {
        var has = !!(inp.files && inp.files[0]);
        btnClear.disabled = !has;
    }

    btnClear.addEventListener('click', function () {
        inp.value = '';
        syncClearEnabled();
    });
    inp.addEventListener('change', function () {
        syncClearEnabled();
    });
    syncClearEnabled();
}

function expedienteAlumnoMetaTieneArchivo(m) {
    if (!m) return false;
    if (m.entregado === true || m.entregado === 'true') return true;
    var fn = m.filename || m.archivoUrl || m.nombreArchivo;
    return !!(fn && String(fn).trim() !== '');
}

/**
 * @param {{ containerId: string, btnAddId: string, bindKey: string, alumnoId: string|number,
 *   titulosList: Array, getFilasVisibles: function(): number, setFilasVisibles: function(number): void,
 *   refresh: function(): Promise|void }} ctx
 */
function renderTitulosCedulaBlockBase(ctx) {
    if (!ctx || !ctx.containerId || !ctx.alumnoId) return;
    var cont = document.getElementById(ctx.containerId);
    if (!cont) return;
    var alumnoId = ctx.alumnoId;
    cont.innerHTML = '';
    var porSlot = {};
    (ctx.titulosList || []).forEach(function (t) {
        var s = t.docSlot != null ? t.docSlot : 0;
        if (s >= 1 && s <= EXPEDIENTE_MAX_TITULOS_CEDULA) porSlot[s] = t;
    });
    var titN = Object.keys(porSlot).filter(function (k) { return expedienteAlumnoMetaTieneArchivo(porSlot[k]); }).length;
    ctx.setFilasVisibles(Math.min(EXPEDIENTE_MAX_TITULOS_CEDULA, Math.max(ctx.getFilasVisibles() || 1, titN || 1)));
    var nVis = Math.min(EXPEDIENTE_MAX_TITULOS_CEDULA, Math.max(1, ctx.getFilasVisibles()));
    for (var s = 1; s <= nVis; s++) {
        (function (slot) {
            var meta = porSlot[slot] || null;
            var docId = meta && meta.id != null ? meta.id : null;
            var ok = meta && expedienteAlumnoMetaTieneArchivo(meta);
            var wrap = document.createElement('div');
            wrap.className = 'border rounded p-2 bg-white';
            wrap.innerHTML =
                '<div class="small fw-semibold text-secondary mb-1">Registro ' + slot + ' de ' + EXPEDIENTE_MAX_TITULOS_CEDULA + '</div>' +
                '<div class="row g-2">' +
                '<div class="col-md-4"><label class="form-label form-label-sm mb-0">Etiqueta</label>' +
                '<input type="text" class="form-control form-control-sm exp-titulo-etiqueta" data-slot="' + slot + '" /></div>' +
                '<div class="col-md-4"><label class="form-label form-label-sm mb-0">Número de cédula</label>' +
                '<input type="text" class="form-control form-control-sm exp-titulo-numero" data-slot="' + slot + '" /></div>' +
                '<div class="col-md-4 exp-titulo-acc" data-slot="' + slot + '"></div></div>';
            cont.appendChild(wrap);
            var inpEt = wrap.querySelector('.exp-titulo-etiqueta');
            var inpNum = wrap.querySelector('.exp-titulo-numero');
            var acc = wrap.querySelector('.exp-titulo-acc');
            if (meta && meta.etiquetaDocumento) inpEt.value = meta.etiquetaDocumento;
            if (meta && meta.numeroCedula) inpNum.value = meta.numeroCedula;
            var esExpedienteModal = ctx && ctx.containerId === 'expedienteTitulosCedulaContainer';
            var pendSlot = (esExpedienteModal && expedienteAlumnoPendienteTitulosCedulaBySlot && expedienteAlumnoPendienteTitulosCedulaBySlot[slot])
                ? expedienteAlumnoPendienteTitulosCedulaBySlot[slot]
                : null;
            var estaPendienteEliminar = esExpedienteModal && docId && expedienteAlumnoPendienteEliminarDocIds && expedienteAlumnoPendienteEliminarDocIds[String(docId)];

            function guardarMetaPendiente() {
                if (!esExpedienteModal) return;
                var cur = expedienteAlumnoPendienteTitulosCedulaBySlot[slot] || {};
                expedienteAlumnoPendienteTitulosCedulaBySlot[slot] = Object.assign({}, cur, {
                    docId: docId || cur.docId,
                    etiqueta: (inpEt.value || '').trim(),
                    numero: (inpNum.value || '').trim()
                });
                expedienteAlumnoMarcarDirty();
            }
            inpEt.addEventListener('input', guardarMetaPendiente);
            inpNum.addEventListener('input', guardarMetaPendiente);

            if (pendSlot) {
                if (pendSlot.etiqueta != null) inpEt.value = String(pendSlot.etiqueta || '');
                if (pendSlot.numero != null) inpNum.value = String(pendSlot.numero || '');
            }

            if (ok && docId && !estaPendienteEliminar) {
                acc.innerHTML =
                    '<label class="form-label form-label-sm mb-0">PDF</label>' +
                    '<div class="btn-group btn-group-sm"><button type="button" class="btn btn-outline-secondary exp-titulo-ver" data-id="' + docId + '"><i class="bi bi-eye"></i></button>' +
                    '<button type="button" class="btn btn-outline-danger exp-titulo-quitar" data-id="' + docId + '"><i class="bi bi-x-lg"></i></button></div>' +
                    '<div class="small text-muted text-truncate mt-1" title="' + escapeStaff(meta.filename || '') + '">' + escapeStaff(meta.filename || '') + '</div>';
                acc.querySelector('.exp-titulo-ver').addEventListener('click', function () {
                    expedienteAlumnoAbrirDocumento('TITULO_CEDULA', docId);
                });
                acc.querySelector('.exp-titulo-quitar').addEventListener('click', function () {
                    if (esExpedienteModal) {
                        expedienteAlumnoPendienteEliminarDocIds[String(docId)] = true;
                        expedienteAlumnoMarcarDirty();
                        renderTitulosCedulaBlockBase(ctx);
                        return;
                    }
                    expedienteAlumnoEliminarDocumentoPorDocId(docId);
                });
            } else {
                acc.innerHTML =
                    '<label class="form-label form-label-sm mb-0">Documento (PDF)</label>' +
                    (estaPendienteEliminar ? '<div class="small text-warning mb-1"><i class="bi bi-exclamation-triangle me-1"></i>Se eliminará al guardar</div>' : '') +
                    '<input type="file" class="form-control form-control-sm exp-titulo-file" accept="application/pdf,.pdf" data-slot="' + slot + '" />';
                var finp = acc.querySelector('.exp-titulo-file');
                finp.addEventListener('change', function () {
                    if (!finp.files || !finp.files[0] || !alumnoId) return;
                    var f = finp.files[0];
                    if (esExpedienteModal) {
                        var cur = expedienteAlumnoPendienteTitulosCedulaBySlot[slot] || {};
                        expedienteAlumnoPendienteTitulosCedulaBySlot[slot] = Object.assign({}, cur, {
                            file: f,
                            etiqueta: (inpEt.value || '').trim(),
                            numero: (inpNum.value || '').trim(),
                            docId: docId || cur.docId
                        });
                        expedienteAlumnoMarcarDirty();
                        finp.value = '';
                        var fn = (f && f.name) ? String(f.name) : ('titulo_' + slot + '.pdf');
                        var note = wrap.querySelector('.exp-titulo-pendiente-note');
                        if (!note) {
                            note = document.createElement('div');
                            note.className = 'exp-titulo-pendiente-note small text-primary mt-1';
                            acc.appendChild(note);
                        }
                        note.textContent = 'Pendiente por guardar: ' + fn;
                        return;
                    }
                    finp.value = '';
                    fetch(
                        API_URL + '/alumnos/' + encodeURIComponent(String(alumnoId)) + '/documentos/raw?tipo=TITULO_CEDULA&slot=' + encodeURIComponent(String(slot)),
                        {
                            method: 'POST',
                            headers: Object.assign({}, staffHeadersNoJson(), {
                                'Content-Type': f.type || 'application/pdf',
                                'X-Filename': staffSafeUploadFilename(f, 'titulo_' + slot + '.pdf'),
                                'X-Etiqueta-Documento': staffHeaderText(inpEt.value),
                                'X-Numero-Cedula': staffHeaderText(inpNum.value)
                            }),
                            body: f
                        }
                    ).then(function (r) {
                        if (!r.ok) return r.json().then(function (j) { throw new Error((j && j.error) || 'Error'); });
                        return Promise.resolve(ctx.refresh());
                    }).catch(function (e) { alert(e.message || 'No se pudo subir'); });
                });
            }
        })(s);
    }
    var btnAdd = document.getElementById(ctx.btnAddId);
    if (btnAdd && !btnAdd[ctx.bindKey]) {
        btnAdd[ctx.bindKey] = true;
        btnAdd.addEventListener('click', function () {
            if (ctx.getFilasVisibles() >= EXPEDIENTE_MAX_TITULOS_CEDULA) {
                if (typeof window.showSystemToast === 'function') {
                    window.showSystemToast('Para agregar más documentos el estudiante debe solicitarlo a la secretaría académica.', { type: 'warning', durationMs: 5500 });
                } else {
                    alert('Para agregar más documentos el estudiante debe solicitarlo a la secretaría académica.');
                }
                return;
            }
            ctx.setFilasVisibles(ctx.getFilasVisibles() + 1);
            fetch(API_URL + '/alumnos/' + encodeURIComponent(String(alumnoId)) + '/documentos', { headers: staffHeadersNoJson() })
                .then(function (r) { return r.json(); })
                .then(function (list) {
                    var tit = (list || []).filter(function (x) { return x && x.tipo === 'TITULO_CEDULA'; });
                    renderTitulosCedulaBlockBase(Object.assign({}, ctx, { titulosList: tit }));
                })
                .catch(function () { /* ignore */ });
        });
    }
}

function renderExpedienteTitulosCedulaBlock(alumnoId, titulosList) {
    renderTitulosCedulaBlockBase({
        containerId: 'expedienteTitulosCedulaContainer',
        btnAddId: 'expedienteBtnAddTituloCedula',
        bindKey: '_expTitBind',
        alumnoId: alumnoId,
        titulosList: titulosList,
        getFilasVisibles: function () { return expedienteTituloCedulaFilasVisibles; },
        setFilasVisibles: function (n) { expedienteTituloCedulaFilasVisibles = n; },
        refresh: function () { return expedienteAlumnoRefrescarDocsUi(); }
    });
}

function renderStaffAluTitulosCedulaBlock(alumnoId, titulosList) {
    renderTitulosCedulaBlockBase({
        containerId: 'staffAluTitulosCedulaContainer',
        btnAddId: 'staffAluBtnAddTituloCedula',
        bindKey: '_staffAluTitBind',
        alumnoId: alumnoId,
        titulosList: titulosList,
        getFilasVisibles: function () { return staffAluTituloCedulaFilasVisibles; },
        setFilasVisibles: function (n) { staffAluTituloCedulaFilasVisibles = n; },
        refresh: function () { return staffAlumnoRefrescarDocsUi(alumnoId); }
    });
}

function staffAlumnoDocConfig(tipo) {
    switch (tipo) {
        case 'CURP': return { wrapId: 'staffAluDocCurpWrap', inputId: 'staffAluDocCurp', label: 'CURP' };
        case 'INE': return { wrapId: 'staffAluDocIneWrap', inputId: 'staffAluDocIne', label: 'INE' };
        case 'CONSTANCIA_SITUACION_FISCAL': return { wrapId: 'staffAluDocConstanciaFiscalWrap', inputId: 'staffAluDocConstanciaFiscal', label: 'Constancia de situación fiscal' };
        case 'ACTA_NACIMIENTO': return { wrapId: 'staffAluDocActaWrap', inputId: 'staffAluDocActa', label: 'Acta de nacimiento' };
        case 'CONSTANCIA_ESTUDIOS': return { wrapId: 'staffAluDocCertEstudiosWrap', inputId: 'staffAluDocCertEstudios', label: 'Certificado de estudios' };
        default: return null;
    }
}

function staffAlumnoAbrirDocumento(alumnoId, tipo, docId) {
    if (!alumnoId) return;
    var url = docId
        ? API_URL + '/alumnos/' + encodeURIComponent(String(alumnoId)) + '/documentos/descarga?docId=' + encodeURIComponent(String(docId))
        : API_URL + '/alumnos/' + encodeURIComponent(String(alumnoId)) + '/documentos/' + encodeURIComponent(tipo) + '/archivo';
    fetch(url, { headers: staffHeadersNoJson() })
        .then(function (r) { if (!r.ok) throw new Error('No se pudo abrir'); return r.blob(); })
        .then(function (b) { window.open(URL.createObjectURL(b), '_blank'); })
        .catch(function () { alert('No se pudo abrir el archivo'); });
}

async function staffAlumnoRefrescarDocsUi(alumnoId) {
    var tipos = ['CURP', 'INE', 'CONSTANCIA_SITUACION_FISCAL', 'ACTA_NACIMIENTO', 'CONSTANCIA_ESTUDIOS'];
    tipos.forEach(function (t) {
        var cfg = staffAlumnoDocConfig(t);
        if (!cfg) return;
        var wrap = document.getElementById(cfg.wrapId);
        var inp = document.getElementById(cfg.inputId);
        if (!wrap || !inp) return;
        wrap.innerHTML = '';
        inp.value = '';
        inp.classList.remove('d-none');
        wrap.appendChild(inp);
    });
    var st = document.getElementById('staffAluTitulosCedulaContainer');
    if (st) st.innerHTML = '';
    if (!alumnoId) return;
    try {
        var res = await fetch(API_URL + '/alumnos/' + encodeURIComponent(String(alumnoId)) + '/documentos', { headers: staffHeadersNoJson() });
        if (!res.ok) throw new Error('no-docs');
        var list = await res.json();
        var map = {};
        var titulos = [];
        (list || []).forEach(function (x) {
            if (!x || !x.tipo) return;
            if (x.tipo === 'TITULO_CEDULA') titulos.push(x);
            else map[x.tipo] = x;
        });
        titulos.sort(function (a, b) { return (a.docSlot || 0) - (b.docSlot || 0); });
        tipos.forEach(function (tipo) {
            var meta = map[tipo] || null;
            var cfg = staffAlumnoDocConfig(tipo);
            if (!cfg) return;
            var wrap = document.getElementById(cfg.wrapId);
            var inp = document.getElementById(cfg.inputId);
            if (!wrap || !inp) return;
            wrap.innerHTML = '';
            var filename = meta && (meta.filename || meta.archivoUrl || meta.nombreArchivo) ? (meta.filename || meta.archivoUrl || meta.nombreArchivo) : null;
            var entregado = meta && meta.entregado === true;
            if (entregado && filename) {
                var row = document.createElement('div');
                row.className = 'd-flex align-items-center justify-content-between gap-2 border rounded px-2 py-2 bg-white';
                row.innerHTML =
                    '<div class="small text-truncate" style="min-width:0;">' +
                      '<span class="text-muted">' + escapeStaff(cfg.label) + ':</span> ' +
                      '<strong>' + escapeStaff(filename) + '</strong>' +
                    '</div>' +
                    '<div class="btn-group btn-group-sm" role="group">' +
                      '<button type="button" class="btn btn-outline-secondary" data-action="ver" title="Ver"><i class="bi bi-eye"></i></button>' +
                      '<button type="button" class="btn btn-outline-danger" data-action="quitar" title="Quitar"><i class="bi bi-x-lg"></i></button>' +
                    '</div>';
                wrap.appendChild(row);
                inp.value = '';
                inp.classList.add('d-none');
                wrap.appendChild(inp);
                row.querySelector('[data-action="ver"]').addEventListener('click', function () { staffAlumnoAbrirDocumento(alumnoId, tipo); });
                row.querySelector('[data-action="quitar"]').addEventListener('click', async function () {
                    try {
                        var ok = (typeof window.uiConfirm === 'function')
                            ? await window.uiConfirm('¿Eliminar este documento del expediente del alumno?', { subtitle: 'Esta acción no se puede deshacer.' })
                            : confirm('¿Eliminar este documento del expediente del alumno?\n\nEsta acción no se puede deshacer.');
                        if (!ok) return;
                        await fetch(API_URL + '/alumnos/' + encodeURIComponent(String(alumnoId)) + '/documentos/' + encodeURIComponent(String(tipo)), {
                            method: 'DELETE',
                            headers: staffHeadersNoJson()
                        });
                        await staffAlumnoRefrescarDocsUi(alumnoId);
                    } catch (e) {
                        alert((e && e.message) ? e.message : 'No se pudo eliminar el documento');
                    }
                });
                return;
            }
            inp.classList.remove('d-none');
            var group = document.createElement('div');
            group.className = 'input-group input-group-sm';
            wrap.appendChild(group);
            group.appendChild(inp);
            var btnClear = document.createElement('button');
            btnClear.type = 'button';
            btnClear.className = 'btn btn-outline-danger btn-sm';
            btnClear.title = 'Quitar selección';
            btnClear.innerHTML = '<i class="bi bi-x-lg"></i>';
            btnClear.disabled = true;
            group.appendChild(btnClear);
            function sync() { btnClear.disabled = !(inp.files && inp.files[0]); }
            btnClear.addEventListener('click', function () { inp.value = ''; sync(); });
            inp.addEventListener('change', sync);
            sync();
        });
        renderStaffAluTitulosCedulaBlock(alumnoId, titulos);
    } catch (_) {}
}

async function expedienteAlumnoRefrescarDocsUi() {
    var aid = document.getElementById('expedienteAlumnoId') && document.getElementById('expedienteAlumnoId').value;
    var tiposExp = ['CURP', 'INE', 'CONSTANCIA_SITUACION_FISCAL', 'ACTA_NACIMIENTO', 'CONSTANCIA_ESTUDIOS'];
    if (!aid) {
        tiposExp.forEach(function (t) {
            expedienteAlumnoSetDocUi(t, null);
        });
        var ec = document.getElementById('expedienteTitulosCedulaContainer');
        if (ec) ec.innerHTML = '';
        return;
    }
    try {
        var res = await fetch(API_URL + '/alumnos/' + encodeURIComponent(aid) + '/documentos', { headers: staffHeadersNoJson() });
        if (!res.ok) throw new Error('No se pudo cargar documentos');
        var list = await res.json();
        var map = {};
        var titulos = [];
        (list || []).forEach(function (x) {
            if (!x || !x.tipo) return;
            if (x.tipo === 'TITULO_CEDULA') titulos.push(x);
            else map[x.tipo] = x;
        });
        titulos.sort(function (a, b) { return (a.docSlot || 0) - (b.docSlot || 0); });
        tiposExp.forEach(function (t) {
            expedienteAlumnoSetDocUi(t, map[t] || null);
        });
        renderExpedienteTitulosCedulaBlock(aid, titulos);
    } catch (e) {
        tiposExp.forEach(function (t) {
            expedienteAlumnoSetDocUi(t, null);
        });
        var ec2 = document.getElementById('expedienteTitulosCedulaContainer');
        if (ec2) ec2.innerHTML = '';
    }
}

async function guardarExpedienteAlumnoDocs() {
    var aid = document.getElementById('expedienteAlumnoId') && document.getElementById('expedienteAlumnoId').value;
    if (!aid) return;
    var btn = document.getElementById('btnGuardarExpedienteAlumno');
    if (btn) btn.disabled = true;
    try {
        var res = await fetch(API_URL + '/alumnos/' + aid, { headers: staffHeadersNoJson() });
        if (!res.ok) throw new Error('No se pudo cargar datos del alumno');
        var alumno = await res.json();
        var alumnoData = {
            matricula: alumno.matricula,
            nombre: alumno.nombre,
            apellidoPaterno: alumno.apellidoPaterno,
            apellidoMaterno: alumno.apellidoMaterno,
            curp: alumno.curp,
            sexo: alumno.sexo,
            fechaNacimiento: alumno.fechaNacimiento,
            correoInstitucional: alumno.correoInstitucional,
            correoPersonal: alumno.correoPersonal,
            telefono: alumno.telefono,
            estado: alumno.estado,
            codigoPostal: alumno.codigoPostal,
            turno: alumno.turno,
            estatusMatricula: alumno.estatusMatricula,
            nombreContactoEmergencia: alumno.nombreContactoEmergencia,
            telefonoContactoEmergencia: alumno.telefonoContactoEmergencia,
            observaciones: alumno.observaciones,
            periodoCursando: alumno.periodoCursando,
            fotoUrl: alumno.fotoUrl,
            periodoAcademicoId: alumno.periodoAcademicoId != null ? alumno.periodoAcademicoId : undefined
        };
        if (alumno.programa && alumno.programa.id) {
            alumnoData.programa = { id: alumno.programa.id };
        }
        if (alumno.cohorte && alumno.cohorte.id) {
            alumnoData.cohorte = { id: alumno.cohorte.id };
        }
        // Guardar datos base del alumno por JSON (evita multipart PUT que en algunos entornos termina como octet-stream)
        var headersJson = staffHeadersJson();
        var resPutJson = await fetch(API_URL + '/alumnos/' + encodeURIComponent(aid), {
            method: 'PUT',
            headers: headersJson,
            body: JSON.stringify(alumnoData)
        });
        var errBody = null;
        try { errBody = await resPutJson.json(); } catch (e2) {}
        if (!resPutJson.ok) {
            throw new Error((errBody && (errBody.error || errBody.message)) || 'No se pudo guardar');
        }

        // Subir documentos seleccionados como RAW
        var docs = [
            { el: 'expDocActa', tipo: 'ACTA_NACIMIENTO' },
            { el: 'expDocCertEstudios', tipo: 'CONSTANCIA_ESTUDIOS' },
            { el: 'expDocCurp', tipo: 'CURP' },
            { el: 'expDocIne', tipo: 'INE' },
            { el: 'expDocConstanciaFiscal', tipo: 'CONSTANCIA_SITUACION_FISCAL' }
        ];
        for (var di = 0; di < docs.length; di++) {
            var d = docs[di];
            var f = archivoExpedienteInput(d.el);
            if (!f) continue;
            expedienteAlumnoMarcarDirty();
            var up = await fetch(
                API_URL + '/alumnos/' + encodeURIComponent(aid) + '/documentos/raw?tipo=' + encodeURIComponent(String(d.tipo)),
                {
                    method: 'POST',
                    headers: Object.assign({}, staffHeadersNoJson(), {
                        'Content-Type': (f && f.type) ? f.type : 'application/octet-stream',
                        'X-Filename': staffSafeUploadFilename(f, String(d.tipo).toLowerCase() + '.pdf')
                    }),
                    body: f
                }
            );
            if (!up.ok) {
                var upErr = null;
                try { upErr = await up.json(); } catch (_) { }
                throw new Error((upErr && (upErr.error || upErr.message)) || ('No se pudo subir el documento: ' + d.tipo));
            }
        }

        // Eliminar docs marcados por docId (p.ej. Título/Cédula) - solo al guardar
        var delIds = Object.keys(expedienteAlumnoPendienteEliminarDocIds || {});
        for (var dx = 0; dx < delIds.length; dx++) {
            var docId = delIds[dx];
            if (!docId) continue;
            var del = await fetch(API_URL + '/alumnos/' + encodeURIComponent(String(aid)) + '/documentos/doc/' + encodeURIComponent(String(docId)), {
                method: 'DELETE',
                headers: staffHeadersNoJson()
            });
            if (!del.ok && del.status !== 404) {
                var delErr = null;
                try { delErr = await del.json(); } catch (_) { }
                throw new Error((delErr && (delErr.error || delErr.message)) || 'No se pudo eliminar un documento del expediente');
            }
        }

        // Subir/actualizar Título/Cédula pendiente (slot 1–4) - solo al guardar
        for (var s = 1; s <= EXPEDIENTE_MAX_TITULOS_CEDULA; s++) {
            var pend = expedienteAlumnoPendienteTitulosCedulaBySlot[s];
            if (!pend) continue;
            if (pend.docId && !pend.file) {
                var pch = await fetch(API_URL + '/alumnos/' + encodeURIComponent(String(aid)) + '/documentos/doc/' + encodeURIComponent(String(pend.docId)), {
                    method: 'PATCH',
                    headers: Object.assign({}, staffHeadersJson(), { 'Content-Type': 'application/json' }),
                    body: JSON.stringify({ etiquetaDocumento: pend.etiqueta || '', numeroCedula: pend.numero || '' })
                });
                if (!pch.ok) {
                    var pErr = null;
                    try { pErr = await pch.json(); } catch (_) { }
                    throw new Error((pErr && (pErr.error || pErr.message)) || 'No se pudieron actualizar metadatos de Título/Cédula');
                }
                continue;
            }
            if (!pend.file) continue;
            var f2 = pend.file;
            var up2 = await fetch(
                API_URL + '/alumnos/' + encodeURIComponent(String(aid)) + '/documentos/raw?tipo=TITULO_CEDULA&slot=' + encodeURIComponent(String(s)),
                {
                    method: 'POST',
                    headers: Object.assign({}, staffHeadersNoJson(), {
                        'Content-Type': (f2 && f2.type) ? f2.type : 'application/pdf',
                        'X-Filename': staffSafeUploadFilename(f2, 'titulo_' + s + '.pdf'),
                        'X-Etiqueta-Documento': staffHeaderText(pend.etiqueta),
                        'X-Numero-Cedula': staffHeaderText(pend.numero)
                    }),
                    body: f2
                }
            );
            if (!up2.ok) {
                var up2Err = null;
                try { up2Err = await up2.json(); } catch (_) { }
                throw new Error((up2Err && (up2Err.error || up2Err.message)) || 'No se pudo subir un Título/Cédula');
            }
        }

        await expedienteAlumnoRefrescarDocsUi();
        expedienteAlumnoResetPendientes();
        var mel = document.getElementById('modalExpedienteAlumno');
        if (mel && typeof bootstrap !== 'undefined' && bootstrap.Modal) {
            var inst = bootstrap.Modal.getInstance(mel);
            if (inst) {
                expedienteAlumnoAllowHideOnce = true;
                inst.hide();
            }
        }
        if (typeof window.showSystemToast === 'function') {
            window.showSystemToast('Documentos actualizados correctamente.', { type: 'success' });
        } else {
            alert('Documentos actualizados correctamente.');
        }
    } catch (e) {
        if (typeof window.showSystemToast === 'function') {
            window.showSystemToast(String(e && e.message ? e.message : 'Error al guardar'), { type: 'danger', durationMs: 6500 });
        } else {
            alert(e.message || 'Error al guardar');
        }
    } finally {
        if (btn) btn.disabled = false;
    }
}

function initStaffUnificadoPage() {
    cargarListaStaff();
    staffBindBulkSelectionUi();

    // Inicializar UI de documentos (modo "sin archivo") al cargar la página
    staffSetDocBasicoUi('CURP_ARCHIVO', null);
    staffSetDocBasicoUi('INE', null);
    staffSetDocBasicoUi('CSF', null);
    staffInitFotoPerfilUi();
    // Normalización al teclear (por si el navegador permite excedentes)
    (function () {
        var fn = document.getElementById('staffFechaNacimiento');
        if (!fn) return;
        if (fn.getAttribute('data-fecha-guard') === '1') return;
        fn.setAttribute('data-fecha-guard', '1');
        fn.addEventListener('input', function () {
            var norm = staffNormalizarFechaIsoYYYYMMDD(fn.value);
            if (norm && norm !== fn.value) fn.value = norm;
            // Si aún no es válido, no forzar (permitir que el usuario termine de teclear o use calendario)
            if (fn.value && fn.value.length > 10) fn.value = fn.value.slice(0, 10);
        });
        fn.addEventListener('blur', function () {
            if (!fn.value) return;
            var norm = staffNormalizarFechaIsoYYYYMMDD(fn.value);
            if (norm) fn.value = norm;
        });
    })();

    document.getElementById('btnGuardarStaff')?.addEventListener('click', guardarStaff);
    document.getElementById('btnNuevoStaff')?.addEventListener('click', function () {
        limpiarFormularioStaff();
        var t = document.getElementById('tab-registrar-staff');
        if (t && typeof bootstrap !== 'undefined' && bootstrap.Tab) bootstrap.Tab.getOrCreateInstance(t).show();
    });
    document.getElementById('btnLimpiarStaff')?.addEventListener('click', limpiarFormularioStaff);
    document.getElementById('btnDescargarExcelStaff')?.addEventListener('click', descargarExcelStaff);
    document.getElementById('btnImportarExcelStaff')?.addEventListener('click', function () {
        var modalEl = document.getElementById('modalCargaMasivaStaff');
        if (!modalEl || typeof bootstrap === 'undefined' || !bootstrap.Modal) {
            // fallback legacy
            var f = document.getElementById('staffImportExcelFile');
            if (f) f.click();
            return;
        }
        bootstrap.Modal.getOrCreateInstance(modalEl).show();
    });
    // Fallback legacy input (si no hay modal)
    document.getElementById('staffImportExcelFile')?.addEventListener('change', function () {
        var file = this.files && this.files[0] ? this.files[0] : null;
        this.value = '';
        if (!file) return;
        importarExcelStaff(file);
    });
    document.getElementById('btnEjecutarCargaMasivaStaff')?.addEventListener('click', function () {
        var inp = document.getElementById('staffCargaMasivaArchivo');
        var resultadoEl = document.getElementById('staffCargaMasivaResultado');
        if (!inp || !inp.files || !inp.files[0]) {
            if (resultadoEl) {
                resultadoEl.className = 'alert alert-warning';
                resultadoEl.textContent = 'Selecciona un archivo Excel antes de procesar.';
                resultadoEl.classList.remove('d-none');
            }
            return;
        }
        var file = inp.files[0];
        if (!file.name.endsWith('.xlsx') && !file.name.endsWith('.xls')) {
            if (resultadoEl) {
                resultadoEl.className = 'alert alert-warning';
                resultadoEl.textContent = 'El archivo debe ser Excel (.xlsx o .xls).';
                resultadoEl.classList.remove('d-none');
            }
            return;
        }
        importarExcelStaff(file);
    });
    // Reset UI modal al abrir/cerrar
    (function () {
        var modalEl = document.getElementById('modalCargaMasivaStaff');
        if (!modalEl) return;
        modalEl.addEventListener('show.bs.modal', function () {
            var inp = document.getElementById('staffCargaMasivaArchivo');
            var res = document.getElementById('staffCargaMasivaResultado');
            var btnP = document.getElementById('btnEjecutarCargaMasivaStaff');
            if (inp) inp.value = '';
            if (res) res.classList.add('d-none');
            if (btnP) {
                btnP.disabled = false;
                btnP.innerHTML = '<i class="bi bi-upload me-1"></i>Procesar archivo';
            }
        });
        modalEl.addEventListener('hidden.bs.modal', function () {
            var inp = document.getElementById('staffCargaMasivaArchivo');
            var res = document.getElementById('staffCargaMasivaResultado');
            if (inp) inp.value = '';
            if (res) res.classList.add('d-none');
        });
    })();

    // Abrir modal con foto de perfil desde la lista
    document.getElementById('staffTableBody')?.addEventListener('click', function (ev) {
        var t = ev && ev.target ? ev.target : null;
        if (!t) return;
        var el = t.closest ? t.closest('.staff-foto-open') : null;
        if (!el) return;
        var src = el.getAttribute('data-foto-src') || el.getAttribute('src') || '';
        if (!src) return;
        staffAbrirModalFotoPerfilDesdeSrc(src);
    });
    document.getElementById('buscarStaffInput')?.addEventListener('input', filtrarStaffTabla);
    document.getElementById('staffFiltroActivo')?.addEventListener('change', filtrarStaffTabla);
    document.getElementById('staffFiltroRol')?.addEventListener('change', filtrarStaffTabla);
    document.getElementById('staffFiltroPrograma')?.addEventListener('change', filtrarStaffTabla);
    document.getElementById('btnBuscarStaff')?.addEventListener('click', filtrarStaffTabla);
    document.getElementById('btnLimpiarFiltrosStaff')?.addEventListener('click', function () {
        var i = document.getElementById('buscarStaffInput');
        var f = document.getElementById('staffFiltroActivo');
        var r = document.getElementById('staffFiltroRol');
        var p = document.getElementById('staffFiltroPrograma');
        if (i) i.value = '';
        if (f) f.value = '';
        if (r) r.value = '';
        if (p) p.value = '';
        renderStaffTable(staffList);
    });

    document.getElementById('btnOrdenarStaffNombre')?.addEventListener('click', function (e) {
        e.preventDefault();
        staffSortNombreDir = staffSortNombreDir === 'asc' ? 'desc' : 'asc';
        staffActualizarIconoOrdenNombre();
        filtrarStaffTabla();
    });
    staffActualizarIconoOrdenNombre();

    staffBindNormalizacionesRegistro();

    document.getElementById('btnGuardarModalStaffRoles')?.addEventListener('click', guardarModalStaffRoles);
    // Estudiante ya no usa el modal #modalDatosAlumnoRol; se completa en el paso 2 del modal de roles.
    document.getElementById('btnVolverModalStaffRolesPaso1')?.addEventListener('click', function () {
        modalStaffRolesIrAPaso1();
    });
    document.getElementById('btnGuardarModalStaffExpediente')?.addEventListener('click', function () {
        guardarStaff({ desdeModalExpedienteRoles: true });
    });
    document.getElementById('btnAddCedulaStaff')?.addEventListener('click', function () {
        if (staffContarFilasCedula() >= STAFF_MAX_CEDULAS) {
            alert('Como máximo se permiten ' + STAFF_MAX_CEDULAS + ' cédulas profesionales en un expediente.');
            return;
        }
        staffAppendCedulaRow(null, '', null);
    });
    document.getElementById('modalStaffRoles')?.addEventListener('hidden.bs.modal', function () {
        staffExpedienteSoloDesdeCarpeta = false;
        staffRolesCommitPendiente = null;
        staffRolesModalPendientesEnFormulario = null;
        staffRolesPatchPendiente = { url: '', roles: [] };
        staffRolesCoordPatchPendiente = { url: '', roles: [] };
        staffRestaurarSeccionesExpedienteEnFormulario();
        modalStaffRolesResetPasosUi();
    });

    document.getElementById('btnConfirmarModalDatosCoordRol')?.addEventListener('click', async function () {
        var sel = document.getElementById('modalCoordRolPrograma');
        var pid = (sel && sel.value) ? String(sel.value).trim() : '';
        if (!pid) {
            alert('Seleccione el programa educativo.');
            return;
        }
        var post = staffRolesCoordCommitDespuesDeExpediente;
        if (!post || !post.urlRoles || !post.roles || !post.roles.length) {
            alert('Sesión de coordinación caducada. Vuelva a abrir «Roles».');
            return;
        }
        try {
            var res = await fetch(post.urlRoles, {
                method: 'PATCH',
                headers: staffHeaders(),
                body: JSON.stringify({ roles: post.roles, programaCoordinadoId: parseInt(pid, 10) })
            });
            var data = null;
            try { data = await res.json(); } catch (_) {}
            if (!res.ok) {
                alert((data && data.error) ? data.error : 'No se pudo asignar el programa del coordinador');
                return;
            }
            staffRolesCoordCommitDespuesDeExpediente = null;
            staffCapturarSnapshotListaUi();
            await cargarListaStaff({ preservePage: true, preserveScroll: true });
            var modCoord = document.getElementById('modalDatosCoordinadorRol');
            if (modCoord && typeof bootstrap !== 'undefined' && bootstrap.Modal) {
                var instC = bootstrap.Modal.getInstance(modCoord);
                if (instC) instC.hide();
            }
            if (typeof window.showSystemToast === 'function') {
                window.showSystemToast('Programa asignado al coordinador correctamente', { type: 'success', durationMs: 4200 });
            }
        } catch (e) {
            console.error(e);
            alert('Error de red al asignar el programa');
        }
    });

    (function bindModalRolesExclusividad() {
        var sin = document.getElementById('modalRolSinRol');
        if (!sin) return;
        var otros = document.querySelectorAll('input.modal-staff-rol-chk:not(#modalRolSinRol)');
        sin.addEventListener('change', function () {
            if (sin.checked) otros.forEach(function (el) { el.checked = false; });
            staffActualizarBotonPaso1Roles();
        });
        otros.forEach(function (el) {
            el.addEventListener('change', function () {
                if (el.checked && sin.checked) sin.checked = false;
                staffActualizarBotonPaso1Roles();
            });
        });
        staffActualizarBotonPaso1Roles();
    })();

    document.getElementById('staffEtiquetaSelect')?.addEventListener('change', function () {
        var w = document.getElementById('staffCampoEtiquetaOtro');
        if (!w) return;
        if (document.getElementById('staffEtiquetaSelect').value === 'otro') w.classList.remove('d-none');
        else w.classList.add('d-none');
    });

    ['staffTelefono', 'staffContactoTelefono'].forEach(function (id, idx) {
        var ctr = idx === 0 ? 'staffTelefonoCounter' : 'staffContactoTelefonoCounter';
        var inp = document.getElementById(id);
        if (!inp) return;
        inp.addEventListener('input', function () {
            var n = normalizarTel10(inp.value);
            if (inp.value !== n) inp.value = n;
            staffContadorTel(id, ctr);
        });
        staffContadorTel(id, ctr);
    });

    document.getElementById('staffTableBody')?.addEventListener('click', function (ev) {
        var br = ev.target.closest('.btn-staff-roles');
        if (br) {
            ev.preventDefault();
            ev.stopPropagation();
            var uidSolo = br.getAttribute('data-usuario-solo-alumno');
            var pidBtn = br.getAttribute('data-personal-id');
            if (uidSolo) {
                var rowU = staffList.find(function (x) { return String(x.usuarioId) === String(uidSolo); });
                if (rowU) abrirModalStaffRoles(null, rowU.roles, rowU.usuarioId);
            } else if (pidBtn) {
                var rowR = staffList.find(function (x) { return x.personalId === parseInt(pidBtn, 10); });
                if (rowR) abrirModalStaffRoles(rowR.personalId, rowR.roles, null);
            }
            return;
        }
        var ex = ev.target.closest('.btn-staff-expediente');
        if (ex) {
            ev.preventDefault();
            ev.stopPropagation();
            var trEx = ev.target.closest('tr[data-personal-id], tr[data-maestro-id], tr[data-alumno-id], tr[data-usuario-id]');
            var rowEx = staffBuscarRowPorTr(trEx);
            abrirExpedienteDesdeTablaRow(rowEx);
            return;
        }
        var ed = ev.target.closest('.btn-staff-editar');
        var el = ev.target.closest('.btn-staff-eliminar');
        var tr = ev.target.closest('tr[data-personal-id], tr[data-maestro-id], tr[data-alumno-id], tr[data-usuario-id]');
        if (!tr) return;
        var pid = tr.getAttribute('data-personal-id');
        var mid = tr.getAttribute('data-maestro-id');
        var legacy = tr.getAttribute('data-legacy') === '1';
        var soloAl = tr.getAttribute('data-solo-alumno') === '1';
        var aidRow = tr.getAttribute('data-alumno-id');
        if (ed) {
            if (soloAl && aidRow) {
                abrirModalExpedienteAlumno(parseInt(aidRow, 10));
                return;
            }
            limpiarFormularioStaff();
            (async function () {
                try {
                    if (pid) {
                        staffEditandoPersonalId = parseInt(pid, 10);
                        await rellenarFormularioDesdePersonalId(staffEditandoPersonalId);
                    } else if (legacy && mid) {
                        staffEditandoLegacyMaestroId = parseInt(mid, 10);
                        await rellenarFormularioDesdeMaestroId(staffEditandoLegacyMaestroId);
                    }
                    var t = document.getElementById('tab-registrar-staff');
                    if (t && typeof bootstrap !== 'undefined' && bootstrap.Tab) bootstrap.Tab.getOrCreateInstance(t).show();
                    document.getElementById('staffForm')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
                } catch (e) {
                    alert(e.message || 'Error al cargar');
                }
            })();
        }
        if (el) {
            if (soloAl && aidRow) {
                eliminarStaffFila(null, null, parseInt(aidRow, 10));
                return;
            }
            if (pid) eliminarStaffFila(parseInt(pid, 10), null, null);
            else if (mid) eliminarStaffFila(null, parseInt(mid, 10), null);
        }
    });

    document.getElementById('btnGuardarExpedienteAlumno')?.addEventListener('click', guardarExpedienteAlumnoDocs);

    if (!puedeGestionarStaffUi()) {
        document.querySelectorAll('#staffForm input, #staffForm select, #staffForm textarea, #staffForm button').forEach(function (el) {
            if (el.id === 'btnLimpiarStaff') return;
            el.disabled = true;
        });
    }

    // Modal expediente alumno: confirmar cierre si hay cambios sin guardar
    (function () {
        var m = document.getElementById('modalExpedienteAlumno');
        if (!m || m.__boundConfirmClose) return;
        m.__boundConfirmClose = true;
        m.addEventListener('hide.bs.modal', function (ev) {
            if (expedienteAlumnoAllowHideOnce) {
                expedienteAlumnoAllowHideOnce = false;
                return;
            }
            if (!expedienteAlumnoTieneCambiosPendientes()) return;
            try { if (ev) ev.preventDefault(); } catch (_) {}
            Promise.resolve().then(async function () {
                var ok = false;
                try {
                    ok = (typeof window.uiConfirm === 'function')
                        ? await window.uiConfirm('Tienes cambios sin guardar. ¿Deseas cerrar y descartarlos?', { title: 'Cambios sin guardar', subtitle: 'Si cierras, no se guardará nada hasta que pulses «Guardar documentos».' })
                        : confirm('Tienes cambios sin guardar.\n\n¿Cerrar y descartarlos?');
                } catch (_) { ok = false; }
                if (!ok) return;
                // Descartar: limpiar selecciones locales y recargar desde backend al siguiente open
                expedienteAlumnoResetPendientes();
                var ids = ['expDocActa', 'expDocCertEstudios', 'expDocCurp', 'expDocIne', 'expDocConstanciaFiscal'];
                ids.forEach(function (id) { var inp = document.getElementById(id); if (inp) inp.value = ''; });
                expedienteAlumnoAllowHideOnce = true;
                if (typeof bootstrap !== 'undefined' && bootstrap.Modal) {
                    bootstrap.Modal.getOrCreateInstance(m).hide();
                } else {
                    m.classList.remove('show');
                    m.style.display = 'none';
                }
            });
        });
    })();

    staffActualizarVisibilidadSeccionesFormulario();
}

document.addEventListener('DOMContentLoaded', function () {
    if (document.getElementById('staffTableBody')) initStaffUnificadoPage();
});
