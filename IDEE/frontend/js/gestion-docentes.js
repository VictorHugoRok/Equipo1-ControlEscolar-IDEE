// Gestion de docentes (maestros)

let docentes = [];
let docenteEditando = null;
/** Evita doble envío si hay varios listeners o doble clic muy rápido. */
let guardarDocenteEnCurso = false;

function normalizarNombreTituloDocenteLive(raw) {
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

function normalizarNombreTituloDocenteFinal(raw) {
    return normalizarNombreTituloDocenteLive(raw).trim().replace(/\s+/g, ' ');
}

function normalizarRfcUpperDocente(raw) {
    const s = String(raw == null ? '' : raw).trim();
    return s ? s.toUpperCase() : '';
}

function normalizarCurpUpperAlnumDocente(raw) {
    let s = String(raw == null ? '' : raw).toUpperCase();
    s = s.replace(/[^A-Z0-9]/g, '');
    if (s.length > 18) s = s.slice(0, 18);
    return s;
}

function normalizarTelefono10Docente(raw) {
    const s = String(raw == null ? '' : raw);
    return s.replace(/\D/g, '').slice(0, 10);
}

function actualizarContadorTelefonoDocente(inputId, counterId) {
    const inp = document.getElementById(inputId);
    const counter = document.getElementById(counterId);
    if (!inp || !counter) return;
    const n = (inp.value || '').length;
    counter.textContent = n + '/10';
}

function setupPhoneInput10Docente(inputId, counterId) {
    const inp = document.getElementById(inputId);
    if (!inp) return;

    function sync() {
        const norm = normalizarTelefono10Docente(inp.value);
        if (inp.value !== norm) inp.value = norm;
        if (counterId) actualizarContadorTelefonoDocente(inputId, counterId);
    }

    inp.addEventListener('input', sync);
    inp.addEventListener('paste', function () { setTimeout(sync, 0); });
    sync();
}

function getHeadersDocentes(includeContentType = true) {
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

function escapeHtmlDocente(value) {
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

function formatGradoAcademico(grado) {
    const labels = {
        LICENCIATURA: 'Licenciatura',
        ESPECIALIDAD: 'Especialidad',
        MAESTRIA: 'Maestria',
        DOCTORADO: 'Doctorado'
    };
    return labels[grado] || 'N/A';
}

function formatTipoMaestroExcel(tipo) {
    const labels = {
        TIEMPO_COMPLETO: 'Tiempo completo',
        MEDIO_TIEMPO: 'Medio tiempo',
        POR_HORAS: 'Por horas'
    };
    return labels[tipo] || (tipo || '—');
}

async function descargarExcelDocentes() {
    if (typeof XLSX === 'undefined') {
        alert('No se pudo cargar la librería de Excel. Recarga la página e intenta de nuevo.');
        return;
    }

    const btn = document.getElementById('btnDescargarExcelDocentes');
    if (btn) {
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Generando...';
    }

    try {
        let lista = docentes && docentes.length > 0 ? docentes : [];
        if (lista.length === 0) {
            const res = await fetch(`${API_URL}/maestros`, { method: 'GET', headers: getHeadersDocentes(false) });
            if (res.ok) lista = await res.json();
        }

        const headers = [
            'CURP', 'Nombre', 'Apellido paterno', 'Apellido materno', 'Etiqueta',
            'Correo institucional', 'Correo personal', 'Teléfono', 'Código postal',
            'Grado académico', 'Cédula profesional', 'Área',
            'RFC', 'Régimen fiscal',
            'Tipo de maestro', 'Fecha de alta', 'Estatus',
            'Contacto emergencia', 'Tel. emergencia',
            'Observaciones'
        ];
        const filas = [headers];

        lista.forEach(d => {
            const fechaAlta = d.fechaAlta ? (typeof d.fechaAlta === 'string' ? d.fechaAlta.substring(0, 10) : d.fechaAlta) : '—';
            const obs = (d.observaciones || '').toString();
            const obsCorta = obs.length > 200 ? obs.substring(0, 200) + '…' : obs || '—';
            filas.push([
                d.curp || '—',
                d.nombre || '—',
                d.apellidoPaterno || '—',
                d.apellidoMaterno || '—',
                d.etiqueta || '—',
                d.correoInstitucional || '—',
                d.correoPersonal || '—',
                d.telefono || '—',
                d.codigoPostal || '—',
                formatGradoAcademico(d.gradoAcademico) || '—',
                d.cedulaProfesional || '—',
                d.area || '—',
                d.rfc || '—',
                d.regimenFiscal || '—',
                formatTipoMaestroExcel(d.tipoMaestro),
                fechaAlta,
                d.activo ? 'Activo' : 'Inactivo',
                d.nombreContactoEmergencia || '—',
                d.telefonoContactoEmergencia || '—',
                obsCorta
            ]);
        });

        const ws = XLSX.utils.aoa_to_sheet(filas);
        ws['!cols'] = [
            { wch: 20 }, { wch: 18 }, { wch: 16 }, { wch: 16 }, { wch: 8 },
            { wch: 28 }, { wch: 28 }, { wch: 14 }, { wch: 10 },
            { wch: 14 }, { wch: 12 }, { wch: 18 },
            { wch: 14 }, { wch: 40 },
            { wch: 16 }, { wch: 12 }, { wch: 10 },
            { wch: 22 }, { wch: 14 },
            { wch: 35 }
        ];
        ws['!freeze'] = { xSplit: 0, ySplit: 1, topLeftCell: 'A2' };

        const wb = XLSX.utils.book_new();
        XLSX.utils.book_append_sheet(wb, ws, 'Docentes');

        const nombreArchivo = 'Docentes_' + new Date().toISOString().slice(0, 10) + '.xlsx';
        XLSX.writeFile(wb, nombreArchivo);
    } catch (err) {
        console.error('Error al generar Excel:', err);
        alert('No se pudo generar el archivo Excel. Verifica la conexión e intenta de nuevo.');
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = '<i class="bi bi-file-earmark-excel"></i><span class="btn-excel-text">Exportar docentes</span>';
        }
    }
}

function getBadgeActivo(activo) {
    return activo
        ? '<span class="badge bg-success-subtle text-success">Activo</span>'
        : '<span class="badge bg-secondary-subtle text-secondary">Inactivo</span>';
}

async function cargarDocentes() {
    const tbody = document.getElementById('docentesTableBody');
    if (!tbody) return;

    try {
        const response = await fetch(`${API_URL}/maestros`, {
            method: 'GET',
            headers: getHeadersDocentes(false)
        });

        if (response.status === 401) {
            if (typeof logout === 'function') logout();
            else window.location.href = '../index.html';
            return;
        }

        if (!response.ok) {
            let msg = 'Error al cargar docentes';
            try {
                const d = await response.json();
                msg = d.error || d.message || msg;
            } catch (_) {
                try { msg = await response.text() || msg; } catch (_) { }
            }
            throw new Error(msg);
        }

        docentes = await response.json();
        renderizarTablaDocentes(docentes);
    } catch (error) {
        console.error('Error al cargar docentes:', error);
        const mensaje = error.message || 'Error al cargar la lista de docentes. Verifica la conexión y que el backend esté en ejecución.';
        mostrarErrorTablaDocentes(mensaje);
    }
}

function renderizarTablaDocentes(lista) {
    const tbody = document.getElementById('docentesTableBody');
    if (!tbody) return;

    if (!Array.isArray(lista) || lista.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="7" class="text-center py-5">
                    <div class="text-muted">
                        <i class="bi bi-people display-4 d-block mb-2" style="opacity: 0.5;"></i>
                        <p class="mb-0 fs-5">No hay docentes registrados</p>
                        <p class="small mb-0 mt-1">Usa la pestaña «Registrar docentes» para dar de alta al primer maestro.</p>
                    </div>
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = lista.map(docente => {
        const nombreCompleto = [docente.nombre, docente.apellidoPaterno, docente.apellidoMaterno]
            .filter(Boolean)
            .join(' ');

        return `
            <tr data-docente-id="${docente.id}">
                <td><strong>${escapeHtmlDocente(docente.curp || 'N/A')}</strong></td>
                <td>${escapeHtmlDocente(nombreCompleto || 'N/A')}</td>
                <td>${escapeHtmlDocente(docente.etiqueta || 'N/A')}</td>
                <td>${formatGradoAcademico(docente.gradoAcademico)}</td>
                <td>${escapeHtmlDocente(docente.area || 'N/A')}</td>
                <td>${getBadgeActivo(docente.activo)}</td>
                <td>
                    <button class="btn btn-sm btn-outline-secondary me-1" data-action="edit">Editar</button>
                    <button class="btn btn-sm btn-outline-danger" data-action="delete">Eliminar</button>
                </td>
            </tr>
        `;
    }).join('');
}

function mostrarErrorTablaDocentes(mensaje) {
    const tbody = document.getElementById('docentesTableBody');
    if (!tbody) return;

    tbody.innerHTML = `
        <tr>
            <td colspan="7" class="text-center py-5">
                <div class="text-danger">
                    <i class="bi bi-exclamation-triangle display-6 d-block mb-2"></i>
                    <p class="mb-1 fw-semibold">No se pudo cargar la lista</p>
                    <p class="small mb-0 text-muted">${escapeHtmlDocente(mensaje)}</p>
                    <p class="small mt-2 mb-0">Verifica que el backend esté en ejecución y que hayas iniciado sesión.</p>
                </div>
            </td>
        </tr>
    `;
}

function prepararFormularioDocente(docente) {
    const form = document.getElementById('maestroForm');
    if (!form) return;

    document.getElementById('maestroId').value = docente ? docente.id || '' : '';
    document.getElementById('maestroCurp').value = docente ? (docente.curp || '') : '';
    document.getElementById('maestroNombre').value = docente ? (docente.nombre || '') : '';
    document.getElementById('maestroApellidoPaterno').value = docente ? (docente.apellidoPaterno || '') : '';
    document.getElementById('maestroApellidoMaterno').value = docente ? (docente.apellidoMaterno || '') : '';
    document.getElementById('maestroGrado').value = docente ? (docente.gradoAcademico || '') : '';
    document.getElementById('maestroCedula').value = docente ? (docente.cedulaProfesional || '') : '';
    document.getElementById('maestroArea').value = docente ? (docente.area || '') : '';
    document.getElementById('maestroCorreoInstitucional').value = docente ? (docente.correoInstitucional || '') : '';
    document.getElementById('maestroCorreoPersonal').value = docente ? (docente.correoPersonal || '') : '';
    document.getElementById('maestroTelefono').value = docente ? normalizarTelefono10Docente(docente.telefono || '') : '';
    actualizarContadorTelefonoDocente('maestroTelefono', 'maestroTelefonoCounter');
    document.getElementById('maestroCodigoPostal').value = docente ? (docente.codigoPostal || '') : '';
    document.getElementById('maestroContactoNombre').value = docente ? (docente.nombreContactoEmergencia || '') : '';
    document.getElementById('maestroContactoTelefono').value = docente ? normalizarTelefono10Docente(docente.telefonoContactoEmergencia || '') : '';
    actualizarContadorTelefonoDocente('maestroContactoTelefono', 'maestroContactoTelefonoCounter');
    document.getElementById('maestroRfc').value = docente ? (docente.rfc || '') : '';
    document.getElementById('maestroRegimen').value = docente ? (docente.regimenFiscal || '') : '';
    document.getElementById('maestroTipo').value = docente ? (docente.tipoMaestro || '') : '';
    const fechaAltaRaw = docente && docente.fechaAlta ? String(docente.fechaAlta) : '';
    document.getElementById('maestroFechaAlta').value = docente ? (fechaAltaRaw ? fechaAltaRaw.substring(0, 10) : '') : '';
    document.getElementById('maestroActivo').value = docente ? String(docente.activo) : 'true';
    document.getElementById('maestroObservaciones').value = docente ? (docente.observaciones || '') : '';

    const etiquetaSelect = document.getElementById('etiquetaSelect');
    const etiquetaOtro = document.getElementById('etiquetaOtro');
    const campoOtro = document.getElementById('campoEtiquetaOtro');
    if (etiquetaSelect && etiquetaOtro && campoOtro) {
        const etiquetasFijas = ['Dr.', 'Dra.', 'Mtro.', 'Mtra.', 'Lic.', 'CDEO', 'CDEE', 'CDEP', 'LOEO'];
        if (docente && docente.etiqueta && !etiquetasFijas.includes(docente.etiqueta)) {
            etiquetaSelect.value = 'otro';
            etiquetaOtro.value = docente.etiqueta;
            campoOtro.classList.remove('d-none');
        } else {
            etiquetaSelect.value = docente ? (docente.etiqueta || '') : '';
            etiquetaOtro.value = '';
            campoOtro.classList.add('d-none');
        }
    }

    const archivos = document.getElementById('maestroAntecedentes');
    if (archivos) {
        archivos.value = '';
    }

    const boton = document.getElementById('btnGuardarMaestro');
    if (boton) {
        boton.textContent = docente ? 'Actualizar docente' : 'Guardar docente';
    }
}

function limpiarFormularioDocente() {
    docenteEditando = null;
    prepararFormularioDocente(null);
}

function editarDocente(id) {
    const docente = docentes.find(item => item.id === id);
    if (!docente) return;

    docenteEditando = docente;
    prepararFormularioDocente(docente);
    var tabBtn = document.getElementById('tab-registrar-docente');
    if (tabBtn && typeof bootstrap !== 'undefined' && bootstrap.Tab) {
        bootstrap.Tab.getOrCreateInstance(tabBtn).show();
    }
    document.getElementById('maestroForm')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function obtenerEtiquetaSeleccionada() {
    const etiquetaSelect = document.getElementById('etiquetaSelect');
    const etiquetaOtro = document.getElementById('etiquetaOtro');
    if (!etiquetaSelect) return null;

    if (etiquetaSelect.value === 'otro') {
        return etiquetaOtro ? etiquetaOtro.value.trim() || null : null;
    }
    return etiquetaSelect.value || null;
}

async function guardarDocente() {
    const form = document.getElementById('maestroForm');
    const btnGuardar = document.getElementById('btnGuardarMaestro');
    if (!form) return;

    if (guardarDocenteEnCurso) {
        return;
    }

    if (!form.checkValidity()) {
        form.reportValidity();
        return;
    }

    const telRaw = document.getElementById('maestroTelefono').value || '';
    const tel = normalizarTelefono10Docente(telRaw);
    if (String(telRaw || '').trim() && tel.length !== 10) {
        alert('El teléfono debe tener exactamente 10 dígitos numéricos.');
        document.getElementById('maestroTelefono')?.focus();
        return;
    }
    document.getElementById('maestroTelefono').value = tel;

    const telEmergRaw = document.getElementById('maestroContactoTelefono').value || '';
    const telEmerg = normalizarTelefono10Docente(telEmergRaw);
    if (String(telEmergRaw || '').trim() && telEmerg.length !== 10) {
        alert('El teléfono de emergencia debe tener exactamente 10 dígitos numéricos.');
        document.getElementById('maestroContactoTelefono')?.focus();
        return;
    }
    document.getElementById('maestroContactoTelefono').value = telEmerg;

    const rfcTrim = (document.getElementById('maestroRfc') && document.getElementById('maestroRfc').value || '').trim();
    const rfcUpper = normalizarRfcUpperDocente(rfcTrim);
    const rfcEl = document.getElementById('maestroRfc');
    if (rfcEl && rfcEl.value !== rfcUpper) rfcEl.value = rfcUpper;
    if (rfcTrim && (rfcTrim.length > 13 || rfcTrim.length < 12)) {
        alert('El RFC debe tener 12 o 13 caracteres, o dejarse vacío.');
        document.getElementById('maestroRfc')?.focus();
        return;
    }

    guardarDocenteEnCurso = true;
    if (btnGuardar) {
        btnGuardar.disabled = true;
        btnGuardar.textContent = 'Guardando…';
    }

    const docenteData = {
        curp: normalizarCurpUpperAlnumDocente(document.getElementById('maestroCurp').value),
        nombre: normalizarNombreTituloDocenteFinal(document.getElementById('maestroNombre').value),
        apellidoPaterno: normalizarNombreTituloDocenteFinal(document.getElementById('maestroApellidoPaterno').value),
        apellidoMaterno: normalizarNombreTituloDocenteFinal(document.getElementById('maestroApellidoMaterno').value),
        etiqueta: obtenerEtiquetaSeleccionada(),
        gradoAcademico: document.getElementById('maestroGrado').value || null,
        cedulaProfesional: document.getElementById('maestroCedula').value.trim() || null,
        area: document.getElementById('maestroArea').value || null,
        correoInstitucional: document.getElementById('maestroCorreoInstitucional').value.trim(),
        correoPersonal: document.getElementById('maestroCorreoPersonal').value.trim() || null,
        telefono: tel || null,
        codigoPostal: document.getElementById('maestroCodigoPostal').value.trim() || null,
        nombreContactoEmergencia: normalizarNombreTituloDocenteFinal(document.getElementById('maestroContactoNombre').value) || null,
        telefonoContactoEmergencia: telEmerg || null,
        rfc: normalizarRfcUpperDocente(document.getElementById('maestroRfc').value) || null,
        regimenFiscal: document.getElementById('maestroRegimen').value || null,
        tipoMaestro: document.getElementById('maestroTipo').value || null,
        fechaAlta: document.getElementById('maestroFechaAlta').value || null,
        activo: document.getElementById('maestroActivo').value === 'true',
        observaciones: document.getElementById('maestroObservaciones').value.trim() || null
    };

    const docenteId = document.getElementById('maestroId').value;
    const url = docenteId ? `${API_URL}/maestros/${docenteId}` : `${API_URL}/maestros`;
    const archivos = document.getElementById('maestroAntecedentes');
    const tieneArchivos = archivos && archivos.files && archivos.files.length > 0;

    try {
        let response;
        if (tieneArchivos) {
            const formData = new FormData();
            formData.append('maestro', new File([JSON.stringify(docenteData)], 'maestro.json', { type: 'application/json' }));
            Array.from(archivos.files).forEach(file => formData.append('antecedentes', file));
            response = await fetch(url, {
                method: docenteId ? 'PUT' : 'POST',
                headers: getHeadersDocentes(false),
                body: formData
            });
        } else {
            response = await fetch(url, {
                method: docenteId ? 'PUT' : 'POST',
                headers: getHeadersDocentes(),
                body: JSON.stringify(docenteData)
            });
        }

        let data = null;
        const contentType = response.headers.get('content-type');
        if (contentType && contentType.includes('application/json')) {
            try { data = await response.json(); } catch (_) { }
        } else if (!response.ok) {
            try { data = { error: await response.text() }; } catch (_) { }
        }

        if (!response.ok) {
            const msg = (data && typeof data === 'object' && (data.error || data.message)) ? (data.error || data.message) : (typeof data === 'string' ? data : 'Error al guardar docente');
            throw new Error(msg);
        }

        limpiarFormularioDocente();
        await cargarDocentes();
        alert(docenteId ? 'Docente actualizado exitosamente' : 'Docente creado exitosamente');
    } catch (error) {
        console.error('Error al guardar docente:', error);
        alert(error.message || 'Error al guardar docente. Verifica la conexión y tu sesión.');
    } finally {
        guardarDocenteEnCurso = false;
        if (btnGuardar) {
            btnGuardar.disabled = false;
            btnGuardar.textContent = docenteEditando ? 'Actualizar docente' : 'Guardar docente';
        }
    }
}

function bindNormalizacionesDocentes() {
    const curp = document.getElementById('maestroCurp');
    if (curp && !curp.dataset.curpNorm) {
        curp.dataset.curpNorm = '1';
        curp.addEventListener('input', () => {
            const pos = curp.selectionStart;
            const v = normalizarCurpUpperAlnumDocente(curp.value);
            if (curp.value !== v) {
                curp.value = v;
                try { curp.setSelectionRange(pos, pos); } catch (_) {}
            }
        });
        curp.addEventListener('blur', () => {
            const v = normalizarCurpUpperAlnumDocente(curp.value);
            if (curp.value !== v) curp.value = v;
        });
    }

    const rfc = document.getElementById('maestroRfc');
    if (rfc && !rfc.dataset.norm) {
        rfc.dataset.norm = '1';
        rfc.addEventListener('input', () => {
            const v = normalizarRfcUpperDocente(rfc.value);
            if (rfc.value !== v) rfc.value = v;
        });
        rfc.addEventListener('blur', () => {
            const v = normalizarRfcUpperDocente(rfc.value);
            if (rfc.value !== v) rfc.value = v;
        });
    }
    ['maestroNombre', 'maestroApellidoPaterno', 'maestroApellidoMaterno', 'maestroContactoNombre'].forEach(id => {
        const el = document.getElementById(id);
        if (!el || el.dataset.norm) return;
        el.dataset.norm = '1';
        el.addEventListener('input', () => {
            const pos = el.selectionStart;
            const v = normalizarNombreTituloDocenteLive(el.value);
            if (el.value !== v) {
                el.value = v;
                try { el.setSelectionRange(pos, pos); } catch (_) {}
            }
        });
        el.addEventListener('blur', () => {
            const v = normalizarNombreTituloDocenteFinal(el.value);
            if (el.value !== v) el.value = v;
        });
    });
}

async function eliminarDocente(id) {
    try {
        const response = await fetch(`${API_URL}/maestros/${id}`, {
            method: 'DELETE',
            headers: getHeadersDocentes()
        });

        if (!response.ok) {
            let message = 'Error al eliminar docente';
            try {
                const data = await response.json();
                message = data.error || data.message || message;
            } catch (parseError) {
                message = 'Error al eliminar docente';
            }
            throw new Error(message);
        }

        await cargarDocentes();
        alert('Docente eliminado exitosamente');
    } catch (error) {
        console.error('Error al eliminar docente:', error);
        alert(error.message || 'Error al eliminar docente');
    }
}

function buscarDocente() {
    const input = document.getElementById('buscarDocenteInput');
    const filtroActivoEl = document.getElementById('docenteFiltroActivo');
    if (!input) return;

    const termino = input.value.toLowerCase().trim();
    const filtroActivo = filtroActivoEl ? filtroActivoEl.value : '';

    let base = Array.isArray(docentes) ? docentes.slice() : [];
    if (filtroActivo !== '') {
        const wantActivo = filtroActivo === 'true';
        base = base.filter(d => !!d.activo === wantActivo);
    }

    if (!termino) {
        renderizarTablaDocentes(base);
        return;
    }

    const filtrados = base.filter(docente => {
        const nombreCompleto = [docente.nombre, docente.apellidoPaterno, docente.apellidoMaterno]
            .filter(Boolean)
            .join(' ')
            .toLowerCase();

        return (
            (docente.curp && docente.curp.toLowerCase().includes(termino)) ||
            (nombreCompleto && nombreCompleto.includes(termino)) ||
            (docente.correoInstitucional && docente.correoInstitucional.toLowerCase().includes(termino)) ||
            (docente.correoPersonal && docente.correoPersonal.toLowerCase().includes(termino))
        );
    });

    renderizarTablaDocentes(filtrados);
}

function inicializarTablaDocentes() {
    const tbody = document.getElementById('docentesTableBody');
    if (!tbody) return;

    tbody.addEventListener('click', (event) => {
        const button = event.target.closest('button[data-action]');
        const row = event.target.closest('tr[data-docente-id]');
        if (!row) return;

        const docenteId = parseInt(row.dataset.docenteId, 10);
        if (!docenteId) return;

        if (button) {
            event.stopPropagation();
            const action = button.dataset.action;
            if (action === 'edit') {
                editarDocente(docenteId);
            } else if (action === 'delete') {
                if (confirm('Estas seguro de eliminar este docente? Esta accion no se puede deshacer.')) {
                    eliminarDocente(docenteId);
                }
            }
        }
    });
}

document.addEventListener('DOMContentLoaded', function () {
    var tbody = document.getElementById('docentesTableBody');
    if (!tbody) return;
    if (window._initDocentesDone) return;
    window._initDocentesDone = true;

    function initDocentes() {
        inicializarTablaDocentes();
        cargarDocentes();

        setupPhoneInput10Docente('maestroTelefono', 'maestroTelefonoCounter');
        setupPhoneInput10Docente('maestroContactoTelefono', 'maestroContactoTelefonoCounter');
        bindNormalizacionesDocentes();

        var btnGuardar = document.getElementById('btnGuardarMaestro');
        if (btnGuardar) btnGuardar.addEventListener('click', guardarDocente);

        var inputBuscar = document.getElementById('buscarDocenteInput');
        if (inputBuscar) inputBuscar.addEventListener('input', buscarDocente);

        var btnBuscarDoc = document.getElementById('btnBuscarDocentes');
        if (btnBuscarDoc) btnBuscarDoc.addEventListener('click', buscarDocente);

        var filtroActivo = document.getElementById('docenteFiltroActivo');
        if (filtroActivo) filtroActivo.addEventListener('change', buscarDocente);

        var btnLimpDoc = document.getElementById('btnLimpiarFiltrosDocentes');
        if (btnLimpDoc) {
            btnLimpDoc.addEventListener('click', function () {
                if (inputBuscar) inputBuscar.value = '';
                if (filtroActivo) filtroActivo.value = '';
                buscarDocente();
            });
        }

        var btnExcel = document.getElementById('btnDescargarExcelDocentes');
        if (btnExcel) btnExcel.addEventListener('click', descargarExcelDocentes);
    }

    // Página MPA (docentes.html): no hay docentesSection; cargar datos ya (sesión validada en la página)
    if (!document.getElementById('docentesSection')) {
        initDocentes();
        return;
    }
    // Dashboard (sección docentes): esperar evento de sesión validada
    if (typeof dashboardSessionValidated !== 'undefined' && dashboardSessionValidated) {
        initDocentes();
    } else {
        window.addEventListener('dashboardSessionValidated', function onSessionValidated() {
            window.removeEventListener('dashboardSessionValidated', onSessionValidated);
            initDocentes();
        }, { once: true });
    }
});
