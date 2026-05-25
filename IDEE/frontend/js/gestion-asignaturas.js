// Gestion de asignaturas por programa educativo

let asignaturas = [];
let asignaturaEditando = null;
let asignaturaBlockCounter = 0;
let periodosDisponibles = [];

async function cargarPeriodosPrograma(programaId) {
    if (!programaId) {
        periodosDisponibles = [];
        return;
    }
    try {
        const response = await fetch(`${API_URL}/periodos?programaId=${programaId}`, {
            method: 'GET',
            headers: getHeadersAsignaturas()
        });
        if (!response.ok) {
            const errMsg = await extraerMensajeError(response);
            throw new Error(errMsg || 'Error al cargar periodos');
        }
        const data = await response.json();
        periodosDisponibles = Array.isArray(data) ? data : [];
    } catch (err) {
        console.error('Error cargando periodos:', err);
        periodosDisponibles = [];
        throw err;
    }
}

function extraerMensajeError(response) {
    const contentType = response.headers.get('content-type') || '';
    if (contentType.includes('application/json')) {
        return response.json().then(d => d.error || d.message || null).catch(() => null);
    }
    return response.text().then(t => t || null).catch(() => null);
}

function tipoPeriodoLabelDesdeProgramaSeleccionado() {
    try {
        const sel = document.getElementById('asignaturasSelectorPrograma');
        const pid = sel && sel.value ? String(sel.value) : (typeof programaSeleccionadoId !== 'undefined' ? String(programaSeleccionadoId || '') : '');
        const prog = (typeof programasEducativos !== 'undefined' && Array.isArray(programasEducativos))
            ? programasEducativos.find(p => String(p.id) === String(pid))
            : null;
        const t = prog && prog.tipoPeriodo ? String(prog.tipoPeriodo).toUpperCase() : '';
        if (t === 'SEMESTRE') return 'Semestre';
        if (t === 'CUATRIMESTRE') return 'Cuatrimestre';
        if (t === 'TETRAMESTRE') return 'Tetramestre';
        if (t === 'TRIMESTRE') return 'Trimestre';
    } catch (e) {}
    return 'Periodo';
}

function poblarSelectPeriodos(selectEl) {
    if (!selectEl) return;
    const valorActual = selectEl.value;
    const tipoLabel = tipoPeriodoLabelDesdeProgramaSeleccionado();
    selectEl.innerHTML = '<option value="">Selecciona periodo...</option>';
    (periodosDisponibles || []).forEach(p => {
        const opt = document.createElement('option');
        opt.value = p.id;
        const num = (p && p.numero != null) ? String(p.numero) : '';
        opt.textContent = num ? (num + '° ' + tipoLabel) : (p.nombre || p.nombreDisplay || 'Periodo');
        selectEl.appendChild(opt);
    });
    if (valorActual) selectEl.value = valorActual;
}

function poblarSelectPeriodosEnTodosBloques() {
    const container = document.getElementById('asignaturasBlocksContainer');
    if (!container) return;
    container.querySelectorAll('select[data-field="periodoId"]').forEach(poblarSelectPeriodos);
}

function normalizarTextoCapturaAsignatura(valor) {
    if (valor === null || valor === undefined) return '';
    return String(valor);
}

function crearHtmlBloqueAsignatura(index) {
    const mostrarQuitar = index > 0;
    return `
    <div class="asignatura-block border rounded-3 p-3 mb-3 bg-light bg-opacity-50" data-block-index="${index}">
      <div class="d-flex justify-content-between align-items-center mb-2">
        <span class="badge bg-secondary">Asignatura ${index + 1}</span>
        ${mostrarQuitar ? `<button type="button" class="btn btn-sm btn-outline-danger quitar-bloque-asignatura" title="Quitar este formulario">
          <i class="bi bi-trash"></i> Quitar
        </button>` : '<span></span>'}
      </div>
      <div class="row g-2">
        <div class="col-md-2">
          <label class="form-label small">ID Asignatura</label>
          <input type="text" class="form-control form-control-sm" data-field="idAsignatura" placeholder="ASIG-001" required />
        </div>
        <div class="col-md-2">
          <label class="form-label small">Clave</label>
          <input type="text" class="form-control form-control-sm" data-field="clave" placeholder="MAT" required />
        </div>
        <div class="col-md-4">
          <label class="form-label small">Nombre</label>
          <input type="text" class="form-control form-control-sm" data-field="nombre" placeholder="Cálculo Diferencial" required />
        </div>
        <div class="col-md-2">
          <label class="form-label small">Tipo</label>
          <select class="form-select form-select-sm" data-field="tipo" required>
            <option value="">Selecciona...</option>
            <option value="OBLIGATORIA">Obligatoria</option>
            <option value="OPTATIVA">Optativa</option>
            <option value="LIBRE">Libre</option>
            <option value="EXTRACURRICULAR">Extracurricular</option>
            <option value="SERVICIO_SOCIAL">Servicio Social</option>
            <option value="RESIDENCIA_PROFESIONAL">Residencia Profesional</option>
          </select>
        </div>
        <div class="col-md-2">
          <label class="form-label small">No. periodo</label>
          <select class="form-select form-select-sm" data-field="periodoId" required>
            <option value="">Selecciona periodo...</option>
          </select>
        </div>
        <div class="col-md-2">
          <label class="form-label small">Créditos</label>
          <input type="number" class="form-control form-control-sm" data-field="creditos" placeholder="8" min="1" required />
        </div>
        <div class="col-md-2">
          <label class="form-label small" title="Horas semanales de clase con el profesor">Horas aula</label>
          <input type="number" class="form-control form-control-sm" data-field="horasAula" placeholder="3" min="0" required />
        </div>
        <div class="col-md-2">
          <label class="form-label small" title="Horas semanales de laboratorio, taller o práctica">Horas práctica</label>
          <input type="number" class="form-control form-control-sm" data-field="horasPractica" placeholder="2" min="0" required />
        </div>
        <div class="col-md-2">
          <label class="form-label small" title="Horas semanales de estudio independiente">Horas independientes</label>
          <input type="number" class="form-control form-control-sm" data-field="horasIndependientes" placeholder="3" min="0" required />
        </div>
        <div class="col-md-2">
          <label class="form-label small">Estatus</label>
          <select class="form-select form-select-sm" data-field="estatus" required>
            <option value="ACTIVA">Activa</option>
            <option value="INACTIVA">Inactiva</option>
          </select>
        </div>
      </div>
      <div class="mt-3">
        <button type="button" class="btn btn-outline-primary btn-sm btn-agregar-asignatura-block" title="Agrega varios formularios para registrar múltiples asignaturas a la vez">
          <i class="bi bi-plus-circle me-1"></i>Agregar más asignaturas
        </button>
      </div>
    </div>
    `;
}

function validarBloqueAsignatura(bloque) {
    const campos = [
        { field: 'idAsignatura', label: 'ID Asignatura' },
        { field: 'clave', label: 'Clave' },
        { field: 'nombre', label: 'Nombre' },
        { field: 'tipo', label: 'Tipo' },
        { field: 'periodoId', label: 'No. periodo' },
        { field: 'creditos', label: 'Créditos' },
        { field: 'horasAula', label: 'Horas aula' },
        { field: 'horasPractica', label: 'Horas práctica' },
        { field: 'horasIndependientes', label: 'Horas independientes' },
        { field: 'estatus', label: 'Estatus' }
    ];

    const getEl = (field) => bloque.querySelector(`[data-field="${field}"]`);
    const getVal = (field) => {
        const el = getEl(field);
        return el ? String(el.value || '').trim() : '';
    };
    const anyValue = campos.some(c => getVal(c.field) !== '');
    if (!anyValue) return { ok: true, empty: true };

    for (const c of campos) {
        const el = getEl(c.field);
        const val = getVal(c.field);
        if (!val) return { ok: false, label: c.label, el };

        // Reglas numéricas básicas
        if (['creditos'].includes(c.field)) {
            const n = parseInt(val, 10);
            if (isNaN(n) || n <= 0) return { ok: false, label: c.label, el };
        }
        if (['horasAula', 'horasPractica', 'horasIndependientes'].includes(c.field)) {
            const n = parseInt(val, 10);
            if (isNaN(n) || n < 0) return { ok: false, label: c.label, el };
        }
    }
    return { ok: true, empty: false };
}

function agregarBloqueAsignatura() {
    const container = document.getElementById('asignaturasBlocksContainer');
    if (!container) return;

    const index = container.querySelectorAll('.asignatura-block').length;
    const div = document.createElement('div');
    div.innerHTML = crearHtmlBloqueAsignatura(asignaturaBlockCounter).trim();
    const block = div.firstChild;
    asignaturaBlockCounter++;

    const btnQuitar = block.querySelector('.quitar-bloque-asignatura');
    if (btnQuitar) {
        btnQuitar.addEventListener('click', function () {
            const bloques = container.querySelectorAll('.asignatura-block');
            if (bloques.length <= 1) return;
            block.remove();
            actualizarNumerosYContador();
        });
    }

    const btnAgregar = block.querySelector('.btn-agregar-asignatura-block');
    if (btnAgregar) {
        btnAgregar.addEventListener('click', agregarBloqueAsignatura);
        if (typeof bootstrap !== 'undefined' && bootstrap.Tooltip) {
            new bootstrap.Tooltip(btnAgregar, { delay: { show: 2000, hide: 0 } });
        }
    }

    const selectPeriodo = block.querySelector('select[data-field="periodoId"]');
    poblarSelectPeriodos(selectPeriodo);

    container.appendChild(block);
    // Ya no se fuerza a mayúsculas: se respeta lo capturado
    actualizarNumerosYContador();
}

function actualizarNumerosYContador() {
    const container = document.getElementById('asignaturasBlocksContainer');
    const contador = document.getElementById('asignaturasContador');
    if (!container) return;

    const bloques = container.querySelectorAll('.asignatura-block');
    bloques.forEach((bloque, i) => {
        const badge = bloque.querySelector('.badge');
        if (badge) badge.textContent = 'Asignatura ' + (i + 1);
        bloque.dataset.blockIndex = i;
        const btnAgregar = bloque.querySelector('.btn-agregar-asignatura-block');
        if (btnAgregar) btnAgregar.classList.add('d-none');
    });

    if (!asignaturaEditando && bloques.length > 0) {
        const ultimoBloque = bloques[bloques.length - 1];
        const btnAgregar = ultimoBloque.querySelector('.btn-agregar-asignatura-block');
        if (btnAgregar) btnAgregar.classList.remove('d-none');
    }

    if (contador) {
        const n = bloques.length;
        contador.textContent = n === 1 ? '1 formulario' : n + ' formularios';
    }
}

function inicializarBloquesAsignatura() {
    const container = document.getElementById('asignaturasBlocksContainer');
    if (!container) return;

    container.innerHTML = '';
    asignaturaBlockCounter = 0;
    agregarBloqueAsignatura();
}

function getHeadersAsignaturas(includeContentType = true) {
    if (typeof getHeaders === 'function') {
        return getHeaders(includeContentType);
    }

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

function escapeHtmlAsignaturas(value) {
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

function formatTipoAsignatura(tipo) {
    const labels = {
        OBLIGATORIA: 'Obligatoria',
        OPTATIVA: 'Optativa',
        LIBRE: 'Libre',
        EXTRACURRICULAR: 'Extracurricular',
        SERVICIO_SOCIAL: 'Servicio Social',
        RESIDENCIA_PROFESIONAL: 'Residencia Profesional'
    };
    return labels[tipo] || 'N/A';
}

function getBadgeEstatusAsignatura(estatus) {
    const badges = {
        ACTIVA: '<span class="badge bg-success-subtle text-success">Activa</span>',
        INACTIVA: '<span class="badge bg-secondary-subtle text-secondary">Inactiva</span>'
    };
    return badges[estatus] || '<span class="badge bg-secondary-subtle text-secondary">N/A</span>';
}

function renderizarTablaAsignaturas(lista, mensajeVacio) {
    const tbody = document.getElementById('asignaturasTableBody');
    if (!tbody) return;

    if (!Array.isArray(lista) || lista.length === 0) {
        if (typeof createEmptyTableMessage === 'function') {
            tbody.innerHTML = createEmptyTableMessage(mensajeVacio || 'No hay asignaturas registradas');
        } else {
            tbody.innerHTML = `<tr><td colspan="9" class="text-center text-muted py-4">${escapeHtmlAsignaturas(mensajeVacio || 'No hay asignaturas registradas')}</td></tr>`;
        }
        return;
    }

    tbody.innerHTML = lista.map(asignatura => {
        const horas = [asignatura.horasAula, asignatura.horasPractica, asignatura.horasIndependientes]
            .map(valor => (valor !== null && valor !== undefined && valor !== '') ? valor : '0')
            .join(' / ');

        const periodoTexto = asignatura.periodo && typeof asignatura.periodo === 'object'
            ? (asignatura.periodo.nombre || asignatura.periodo.nombreDisplay || asignatura.periodo.numero + '° Periodo')
            : (asignatura.periodoNumero ?? asignatura.periodo ?? 'N/A');

        return `
            <tr data-asignatura-id="${asignatura.id}">
                <td>${escapeHtmlAsignaturas(asignatura.idAsignatura || '—')}</td>
                <td><strong>${escapeHtmlAsignaturas(asignatura.clave || 'N/A')}</strong></td>
                <td>${escapeHtmlAsignaturas(asignatura.nombre || 'N/A')}</td>
                <td>${formatTipoAsignatura(asignatura.tipo)}</td>
                <td>${escapeHtmlAsignaturas(periodoTexto)}</td>
                <td>${escapeHtmlAsignaturas(asignatura.creditos || 'N/A')}</td>
                <td>${escapeHtmlAsignaturas(horas)}</td>
                <td>${getBadgeEstatusAsignatura(asignatura.estatus)}</td>
                <td class="text-nowrap">
                    <div class="btn-group btn-group-sm" role="group">
                        <button type="button" class="btn btn-outline-secondary" data-action="edit" title="Editar"><i class="bi bi-pencil"></i></button>
                        <button type="button" class="btn btn-outline-danger" data-action="delete" title="Eliminar"><i class="bi bi-trash"></i></button>
                    </div>
                </td>
            </tr>
        `;
    }).join('');
}

async function cargarAsignaturas(programaId) {
    const tbody = document.getElementById('asignaturasTableBody');
    if (!tbody) return;

    if (!programaId) {
        asignaturas = [];
        periodosDisponibles = [];
        renderizarTablaAsignaturas([], 'Selecciona un programa para ver sus asignaturas');
        limpiarFormularioAsignatura();
        poblarSelectPeriodosEnTodosBloques();
        return;
    }

    try {
        await cargarPeriodosPrograma(programaId);

        const response = await fetch(`${API_URL}/asignaturas?programaId=${programaId}`, {
            method: 'GET',
            headers: getHeadersAsignaturas()
        });

        if (!response.ok) {
            throw new Error('Error al cargar asignaturas');
        }

        asignaturas = await response.json();
        renderizarTablaAsignaturas(asignaturas || []);
        limpiarFormularioAsignatura();
        poblarSelectPeriodosEnTodosBloques();
    } catch (error) {
        console.error('Error al cargar asignaturas:', error);
        const msg = error && error.message ? error.message : 'Error al cargar las asignaturas';
        renderizarTablaAsignaturas([], msg);
    }
}

function llenarBloqueConDatos(bloque, asignatura) {
    if (!bloque || !asignatura) return;
    const set = (field, val) => {
        const el = bloque.querySelector(`[data-field="${field}"]`);
        if (el) el.value = val != null ? val : '';
    };
    set('idAsignatura', asignatura.idAsignatura);
    set('clave', asignatura.clave);
    set('nombre', asignatura.nombre);
    set('tipo', asignatura.tipo);
    const periodoId = asignatura.periodo && typeof asignatura.periodo === 'object' ? asignatura.periodo.id : asignatura.periodo;
    set('periodoId', periodoId);
    set('creditos', asignatura.creditos);
    set('horasAula', asignatura.horasAula);
    set('horasPractica', asignatura.horasPractica);
    set('horasIndependientes', asignatura.horasIndependientes);
    set('estatus', asignatura.estatus || 'ACTIVA');
}

function prepararFormularioAsignatura(asignatura) {
    const container = document.getElementById('asignaturasBlocksContainer');
    const btnGuardar = document.getElementById('btnGuardarAsignatura');
    const btnCancelar = document.getElementById('btnCancelarEdicionAsignatura');
    if (!container) return;

    document.getElementById('asignaturaId').value = asignatura ? asignatura.id || '' : '';

    container.innerHTML = '';
    asignaturaBlockCounter = 0;

    if (asignatura) {
        const div = document.createElement('div');
        div.innerHTML = crearHtmlBloqueAsignatura(0).trim();
        const block = div.firstChild;
        const btnQuitar = block.querySelector('.quitar-bloque-asignatura');
        if (btnQuitar) btnQuitar.remove();
        poblarSelectPeriodos(block.querySelector('select[data-field="periodoId"]'));
        llenarBloqueConDatos(block, asignatura);
        container.appendChild(block);
        if (btnGuardar) btnGuardar.textContent = 'Actualizar asignatura';
        if (btnCancelar) btnCancelar.classList.remove('d-none');
    } else {
        agregarBloqueAsignatura();
        if (btnGuardar) btnGuardar.textContent = 'Guardar asignaturas';
        if (btnCancelar) btnCancelar.classList.add('d-none');
    }
    poblarSelectPeriodosEnTodosBloques();
    actualizarNumerosYContador();
}

function limpiarFormularioAsignatura() {
    asignaturaEditando = null;
    prepararFormularioAsignatura(null);
}

function editarAsignatura(id) {
    const asignatura = asignaturas.find(item => item.id === id);
    if (!asignatura) return;

    asignaturaEditando = asignatura;
    prepararFormularioAsignatura(asignatura);
    var formAsignatura = document.getElementById('asignaturaForm');
    if (formAsignatura) {
        formAsignatura.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
}

function obtenerDatosDesdeBloque(bloque) {
    const get = (field) => {
        const el = bloque.querySelector(`[data-field="${field}"]`);
        return el ? el.value.trim() : '';
    };
    const getNum = (field) => {
        const v = get(field);
        return v ? parseInt(v, 10) : null;
    };
    const periodoId = getNum('periodoId');
    const idAsig = normalizarTextoCapturaAsignatura(get('idAsignatura'));
    return {
        idAsignatura: idAsig || null,
        clave: normalizarTextoCapturaAsignatura(get('clave')),
        nombre: normalizarTextoCapturaAsignatura(get('nombre')),
        tipo: normalizarTextoCapturaAsignatura(get('tipo')) || null,
        periodoId: periodoId,
        periodo: periodoId ? { id: periodoId } : null,
        creditos: getNum('creditos'),
        horasAula: getNum('horasAula'),
        horasPractica: getNum('horasPractica'),
        horasIndependientes: getNum('horasIndependientes'),
        estatus: normalizarTextoCapturaAsignatura(get('estatus') || 'ACTIVA') || 'ACTIVA'
    };
}

async function guardarAsignatura() {
    const container = document.getElementById('asignaturasBlocksContainer');
    const btnGuardar = document.getElementById('btnGuardarAsignatura');
    const asignaturaId = document.getElementById('asignaturaId') ? document.getElementById('asignaturaId').value : '';

    if (!programaSeleccionadoId) {
        alert('Selecciona un programa para registrar las asignaturas.');
        return;
    }

    const bloques = container ? container.querySelectorAll('.asignatura-block') : [];
    if (bloques.length === 0) {
        alert('No hay formularios para guardar.');
        return;
    }

    if (asignaturaEditando && asignaturaId) {
        const v = validarBloqueAsignatura(bloques[0]);
        if (!v.ok) {
            alert('Completa el campo obligatorio: ' + v.label);
            if (v.el) {
                v.el.focus();
                v.el.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
            return;
        }
        const data = obtenerDatosDesdeBloque(bloques[0]);
        if (btnGuardar) {
            btnGuardar.disabled = true;
            btnGuardar.textContent = 'Guardando…';
        }
        try {
            const asignaturaData = { ...data, programa: { id: programaSeleccionadoId } };
            const response = await fetch(`${API_URL}/asignaturas/${asignaturaId}`, {
                method: 'PUT',
                headers: getHeadersAsignaturas(),
                body: JSON.stringify(asignaturaData)
            });
            if (!response.ok) {
                const errMsg = await extraerMensajeError(response);
                throw new Error(errMsg || 'Error al actualizar');
            }
            await cargarAsignaturas(programaSeleccionadoId);
            alert('Asignatura actualizada exitosamente');
        } catch (error) {
            console.error('Error:', error);
            alert(error.message || 'Error al actualizar asignatura');
        } finally {
            if (btnGuardar) {
                btnGuardar.disabled = false;
                btnGuardar.textContent = 'Actualizar asignatura';
            }
        }
        return;
    }

    const listaDatos = [];
    for (const bloque of bloques) {
        const v = validarBloqueAsignatura(bloque);
        if (v.empty) continue;
        if (!v.ok) {
            alert('Completa el campo obligatorio: ' + v.label);
            if (v.el) {
                v.el.focus();
                v.el.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
            return;
        }
        listaDatos.push(obtenerDatosDesdeBloque(bloque));
    }

    if (listaDatos.length === 0) {
        alert('Completa al menos un formulario antes de guardar.');
        return;
    }

    if (btnGuardar) {
        btnGuardar.disabled = true;
        btnGuardar.textContent = 'Guardando…';
    }

    let exito = 0;
    let errorMsgs = [];

    try {
        for (const data of listaDatos) {
            const asignaturaData = { ...data, programa: { id: programaSeleccionadoId } };
            const response = await fetch(`${API_URL}/asignaturas`, {
                method: 'POST',
                headers: getHeadersAsignaturas(),
                body: JSON.stringify(asignaturaData)
            });
            if (!response.ok) {
                const errMsg = await extraerMensajeError(response);
                errorMsgs.push(errMsg || 'Error');
            } else {
                exito++;
            }
        }

        await cargarAsignaturas(programaSeleccionadoId);

        if (errorMsgs.length > 0) {
            alert(`Se guardaron ${exito} asignatura(s). Errores: ${errorMsgs.join('; ')}`);
        } else {
            alert(exito === 1 ? 'Asignatura creada exitosamente' : `${exito} asignaturas creadas exitosamente`);
        }
        limpiarFormularioAsignatura();
    } catch (error) {
        console.error('Error al guardar asignaturas:', error);
        alert(error.message || 'Error al guardar asignaturas');
    } finally {
        if (btnGuardar) {
            btnGuardar.disabled = false;
            btnGuardar.textContent = 'Guardar asignaturas';
        }
    }
}

async function eliminarAsignatura(id) {
    try {
        const response = await fetch(`${API_URL}/asignaturas/${id}`, {
            method: 'DELETE',
            headers: getHeadersAsignaturas(false)
        });

        if (!response.ok) {
            let message = 'Error al eliminar asignatura';
            try {
                const data = await response.json();
                message = data.error || data.message || message;
            } catch (parseError) {
                message = 'Error al eliminar asignatura';
            }
            throw new Error(message);
        }

        await cargarAsignaturas(programaSeleccionadoId);
        alert('Asignatura eliminada exitosamente');
    } catch (error) {
        console.error('Error al eliminar asignatura:', error);
        alert(error.message || 'Error al eliminar asignatura');
    }
}

function inicializarTablaAsignaturas() {
    const tbody = document.getElementById('asignaturasTableBody');
    if (!tbody) return;

    tbody.addEventListener('click', (event) => {
        const button = event.target.closest('button[data-action]');
        const row = event.target.closest('tr[data-asignatura-id]');
        if (!row) return;

        const asignaturaId = parseInt(row.dataset.asignaturaId, 10);
        if (!asignaturaId) return;

        if (button) {
            event.stopPropagation();
            const action = button.dataset.action;
            if (action === 'edit') {
                editarAsignatura(asignaturaId);
            } else if (action === 'delete') {
                if (confirm('Estas seguro de eliminar esta asignatura? Esta accion no se puede deshacer.')) {
                    eliminarAsignatura(asignaturaId);
                }
            }
        }
    });
}

document.addEventListener('DOMContentLoaded', function () {
    if (!document.getElementById('asignaturasTableBody')) return;
    if (window._initAsignaturasDone) return;
    window._initAsignaturasDone = true;

    function initAsignaturas() {
        inicializarTablaAsignaturas();

        var btnGuardar = document.getElementById('btnGuardarAsignatura');
        if (btnGuardar) btnGuardar.addEventListener('click', guardarAsignatura);

        var btnCancelar = document.getElementById('btnCancelarEdicionAsignatura');
        if (btnCancelar) btnCancelar.addEventListener('click', limpiarFormularioAsignatura);

        inicializarBloquesAsignatura();

        if (!programaSeleccionadoId) {
            renderizarTablaAsignaturas([], 'Selecciona un programa para ver sus asignaturas');
        }
    }

    var pathAsig = (window.location.pathname || '').toLowerCase();
    var esPaginaProgramasEducativosAsig = pathAsig.indexOf('programas-educativos.html') !== -1;
    if (!document.getElementById('programasSection') || esPaginaProgramasEducativosAsig) {
        initAsignaturas();
        return;
    }
    if (typeof dashboardSessionValidated !== 'undefined' && dashboardSessionValidated) {
        initAsignaturas();
    } else {
        window.addEventListener('dashboardSessionValidated', function onSessionValidated() {
            window.removeEventListener('dashboardSessionValidated', onSessionValidated);
            initAsignaturas();
        }, { once: true });
    }
});
