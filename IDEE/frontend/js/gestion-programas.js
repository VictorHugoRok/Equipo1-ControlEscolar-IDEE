// Gestion de programas educativos y catálogo Clave DGP / Plantel

let programasEducativos = [];
let programaEditando = null;
let programaSeleccionadoId = null;
let tablaProgramasInicializada = false;
let plantelesCatalog = [];
let plantelEditando = null;

function setTextoBotonDescargaExcelProgramas(modo) {
    const btn = document.getElementById('btnDescargarExcelProgramas');
    if (!btn) return;

    const textoEl = btn.querySelector('.btn-excel-text');
    const iconEl = btn.querySelector('i');

    if (modo === 'plantilla') {
        if (textoEl) textoEl.textContent = 'Descargar plantilla';
        btn.title = 'Descargar plantilla Excel para carga masiva';
        if (iconEl) iconEl.className = 'bi bi-file-earmark-arrow-down';
        return;
    }

    if (textoEl) textoEl.textContent = 'Exportar Excel';
    btn.title = 'Exportar Excel';
    if (iconEl) iconEl.className = 'bi bi-file-earmark-excel';
}

function setEstadoGenerandoExcelProgramas(generando) {
    const btn = document.getElementById('btnDescargarExcelProgramas');
    if (!btn) return;
    btn.disabled = !!generando;

    const iconEl = btn.querySelector('i');
    const textoEl = btn.querySelector('.btn-excel-text');

    if (generando) {
        if (iconEl) iconEl.className = 'spinner-border spinner-border-sm';
        if (textoEl) textoEl.textContent = 'Generando…';
    }
}

function getHeaders(includeContentType = true) {
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

function escapeHtml(value) {
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function formatTipoPrograma(tipo) {
    const labels = {
        LICENCIATURA: 'Licenciatura',
        MAESTRIA: 'Maestría',
        PROFESIONAL_ASOCIADO: 'Profesional Asociado',
        TECNICO_SUPERIOR: 'Técnico Superior',
        ESPECIALIDAD: 'Especialidad',
        DOCTORADO: 'Doctorado',
        EXTRACURRICULAR: 'Extracurricular'
    };
    return labels[tipo] || 'N/A';
}

function formatTipoPeriodo(tipo, cantidad) {
    const labels = {
        SEMANAL: 'Semanal',
        SEMESTRE: 'Semestre',
        CUATRIMESTRE: 'Cuatrimestre',
        TETRAMESTRE: 'Tetramestre',
        TRIMESTRE: 'Trimestre'
    };
    const label = labels[tipo] || '';
    if (!label) {
        return 'N/A';
    }
    if (!cantidad) {
        return label;
    }
    const plural = cantidad === 1 ? label : label + 's';
    return plural;
}

function getBadgeEstatusPrograma(estatus) {
    const badges = {
        ACTIVO: '<span class="badge bg-success-subtle text-success">Activo</span>',
        INACTIVO: '<span class="badge bg-secondary-subtle text-secondary">Inactivo</span>'
    };
    return badges[estatus] || '<span class="badge bg-secondary-subtle text-secondary">N/A</span>';
}

// ---------- Catálogo Clave DGP / Plantel ----------
async function cargarPlanteles() {
    try {
        const response = await fetch(`${API_URL}/planteles`, { method: 'GET', headers: getHeaders() });
        if (!response.ok) throw new Error('Error al cargar catálogo');
        plantelesCatalog = await response.json();
        renderizarSelectPlanteles();
        renderizarTablaPlanteles();
    } catch (error) {
        console.error('Error al cargar planteles:', error);
        plantelesCatalog = [];
        renderizarSelectPlanteles();
        const tbody = document.getElementById('plantelesTableBody');
        if (tbody) tbody.innerHTML = '<tr><td colspan="5" class="text-center text-danger py-2">No se pudo cargar el catálogo.</td></tr>';
    }
}

function renderizarSelectPlanteles() {
    const select = document.getElementById('programaNombrePlantel');
    if (!select) return;
    const valorActual = select.value;
    select.innerHTML = '<option value="">Seleccione plantel…</option>';
    (plantelesCatalog || []).forEach(p => {
        const opt = document.createElement('option');
        opt.value = p.claveDgp || '';
        opt.textContent = p.nombrePlantel || p.claveDgp || '';
        select.appendChild(opt);
    });
    if (valorActual) select.value = valorActual;
    actualizarClaveDgpEnFormulario();
}

function actualizarClaveDgpEnFormulario() {
    const select = document.getElementById('programaNombrePlantel');
    const claveDgpInput = document.getElementById('programaClaveDgp');
    if (!select || !claveDgpInput) return;
    claveDgpInput.value = (select.value || '').toUpperCase();
}

/** Texto del formulario programa: respeta mayúsculas/minúsculas capturadas. */
function normalizarTextoCapturaPrograma(valor) {
    if (valor === null || valor === undefined) return '';
    return String(valor);
}

function renderizarTablaPlanteles() {
    const tbody = document.getElementById('plantelesTableBody');
    if (!tbody) return;
    if (!Array.isArray(plantelesCatalog) || plantelesCatalog.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-3">No hay claves DGP registradas. Agregue una arriba.</td></tr>';
        return;
    }
    tbody.innerHTML = plantelesCatalog.map(p => `
        <tr data-plantel-id="${p.id}">
            <td><strong>${escapeHtml(p.idPlantel || '')}</strong></td>
            <td><strong>${escapeHtml(p.claveDgp || '')}</strong></td>
            <td>${escapeHtml(p.claveCct || '')}</td>
            <td>${escapeHtml(p.claveDgair || '')}</td>
            <td>${escapeHtml(p.nombrePlantel || '')}</td>
            <td class="text-end">
                <button type="button" class="btn btn-sm btn-outline-secondary me-1" data-action="edit-plantel">Editar</button>
                <button type="button" class="btn btn-sm btn-outline-danger" data-action="delete-plantel">Eliminar</button>
            </td>
        </tr>
    `).join('');
}

function limpiarFormularioPlantel() {
    plantelEditando = null;
    document.getElementById('plantelId').value = '';
    const idPlantelEl = document.getElementById('plantelIdPlantel');
    if (idPlantelEl) idPlantelEl.value = '';
    document.getElementById('plantelClaveDgp').value = '';
    if (document.getElementById('plantelClaveCct')) {
        document.getElementById('plantelClaveCct').value = '';
    }
    if (document.getElementById('plantelClaveDgair')) {
        document.getElementById('plantelClaveDgair').value = '';
    }
    document.getElementById('plantelNombre').value = '';
    const btnCancelar = document.getElementById('btnCancelarPlantel');
    if (btnCancelar) btnCancelar.classList.add('d-none');
    const btnGuardar = document.getElementById('btnGuardarPlantel');
    if (btnGuardar) btnGuardar.textContent = 'Guardar';
}

async function guardarPlantel() {
    const btnGuardar = document.getElementById('btnGuardarPlantel');
    if (btnGuardar) {
        btnGuardar.disabled = true;
        btnGuardar.textContent = 'Guardando…';
    }
    const idPlantelEl = document.getElementById('plantelIdPlantel');
    const idPlantel = idPlantelEl ? idPlantelEl.value.trim() : '';
    const claveDgp = document.getElementById('plantelClaveDgp').value.trim();
    const claveCctInput = document.getElementById('plantelClaveCct');
    const claveDgairInput = document.getElementById('plantelClaveDgair');
    const claveCct = claveCctInput ? claveCctInput.value.trim() : '';
    const claveDgair = claveDgairInput ? claveDgairInput.value.trim() : '';
    const nombrePlantel = document.getElementById('plantelNombre').value.trim();

    if (!claveDgp) {
        alert('Indique la clave DGP.');
        return;
    }
    if (!claveCct) {
        alert('Indique la clave CCT.');
        return;
    }
    if (!/^[A-Za-z0-9]{10}$/.test(claveCct)) {
        alert('La clave CCT debe tener exactamente 10 caracteres alfanuméricos sin espacios.');
        return;
    }
    if (!claveDgair) {
        alert('Indique la clave DGAIR.');
        return;
    }
    if (!/^[A-Za-z0-9]{1,20}$/.test(claveDgair)) {
        alert('La clave DGAIR debe tener hasta 20 caracteres alfanuméricos sin espacios.');
        return;
    }
    if (!nombrePlantel) {
        alert('Indique el nombre del plantel.');
        return;
    }

    const id = document.getElementById('plantelId').value;
    const url = id ? `${API_URL}/planteles/${id}` : `${API_URL}/planteles`;
    const method = id ? 'PUT' : 'POST';
    try {
        const response = await fetch(url, {
            method,
            headers: getHeaders(),
            body: JSON.stringify({ idPlantel: idPlantel || null, claveDgp, claveCct, claveDgair, nombrePlantel })
        });
        let data = null;
        try { data = await response.json(); } catch (_) {
            data = null;
        }
        if (!response.ok) {
            let msg = 'Error al guardar';
            if (data !== null && data !== undefined) {
                if (typeof data === 'string') {
                    // El backend puede devolver un string plano con el detalle (duplicidad, validación, etc.)
                    msg = data;
                } else if (typeof data === 'object') {
                    msg = data.message || data.error || msg;
                }
            }
            throw new Error(msg);
        }
        limpiarFormularioPlantel();
        await cargarPlanteles();
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

function editarPlantel(id) {
    const p = plantelesCatalog.find(x => x.id === id);
    if (!p) return;
    plantelEditando = p;
    document.getElementById('plantelId').value = p.id;
    const idPlantelEl = document.getElementById('plantelIdPlantel');
    if (idPlantelEl) idPlantelEl.value = p.idPlantel || '';
    document.getElementById('plantelClaveDgp').value = p.claveDgp || '';
    if (document.getElementById('plantelClaveCct')) {
        document.getElementById('plantelClaveCct').value = p.claveCct || '';
    }
    if (document.getElementById('plantelClaveDgair')) {
        document.getElementById('plantelClaveDgair').value = p.claveDgair || '';
    }
    document.getElementById('plantelNombre').value = p.nombrePlantel || '';
    document.getElementById('btnGuardarPlantel').textContent = 'Actualizar';
    document.getElementById('btnCancelarPlantel').classList.remove('d-none');
}

async function eliminarPlantel(id) {
    if (!confirm('¿Eliminar esta clave DGP del catálogo? Los programas que la usen seguirán mostrando la clave.')) return;
    try {
        const response = await fetch(`${API_URL}/planteles/${id}`, { method: 'DELETE', headers: getHeaders(false) });
        if (!response.ok) throw new Error('Error al eliminar');
        await cargarPlanteles();
        alert('Eliminado del catálogo.');
    } catch (error) {
        alert(error.message || 'Error al eliminar');
    }
}

function getApiBaseProgramas() {
    return (typeof API_URL !== 'undefined' && API_URL) ? API_URL : 'http://localhost:8080/api';
}

// ---------- Programas ----------
async function cargarProgramas() {
    const tbody = document.getElementById('programasTableBody');
    if (!tbody) return;

    tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted py-4"><span class="spinner-border spinner-border-sm"></span> Cargando programas...</td></tr>';

    try {
        let lista;
        if (typeof authFetch === 'function') {
            lista = await authFetch('/programas-educativos');
        } else {
            const response = await fetch(getApiBaseProgramas() + '/programas-educativos', {
                method: 'GET',
                headers: getHeaders()
            });
            if (!response.ok) {
                throw new Error('Error al cargar programas (código ' + response.status + ')');
            }
            lista = await response.json();
        }

        programasEducativos = Array.isArray(lista) ? lista : [];
        renderizarTablaProgramas(programasEducativos);
        renderizarSelectorProgramasAsignaturas();
        setTextoBotonDescargaExcelProgramas((programasEducativos || []).length === 0 ? 'plantilla' : 'exportar');
    } catch (error) {
        console.error('Error al cargar programas:', error);
        mostrarErrorTablaProgramas(error.message || 'Error al cargar la lista de programas');
        const sel = document.getElementById('asignaturasSelectorPrograma');
        if (sel && sel.tagName === 'SELECT') sel.innerHTML = '<option value="">Error al cargar programas</option>';
        // Si no pudimos determinar, dejamos el modo más seguro.
        setTextoBotonDescargaExcelProgramas('plantilla');
    }
}

function renderizarSelectorProgramasAsignaturas() {
    const select = document.getElementById('asignaturasSelectorPrograma');
    if (!select || select.tagName !== 'SELECT') return;

    const valorActual = select.value ? parseInt(select.value, 10) : null;
    select.innerHTML = '<option value="">-- Seleccione un programa --</option>';

    const lista = programasEducativos || [];
    if (!Array.isArray(lista) || lista.length === 0) return;

    lista.forEach(programa => {
        const opt = document.createElement('option');
        opt.value = programa.id;
        opt.textContent = (programa.clave || '') + ' - ' + (programa.nombre || '');
        select.appendChild(opt);
    });

    if (valorActual && lista.some(p => p.id === valorActual)) {
        select.value = valorActual;
    } else if (programaSeleccionadoId) {
        select.value = programaSeleccionadoId;
    }
}

function renderizarTablaProgramas(lista) {
    const tbody = document.getElementById('programasTableBody');
    if (!tbody) return;

    if (!Array.isArray(lista) || lista.length === 0) {
        if (typeof createEmptyTableMessage === 'function') {
            tbody.innerHTML = createEmptyTableMessage('No hay programas registrados');
        } else {
            tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted py-4">No hay programas registrados</td></tr>';
        }
        return;
    }

    tbody.innerHTML = lista.map(programa => {
        const periodo = programa.duracionPeriodos
            ? (programa.tipoPeriodo
                ? `${programa.duracionPeriodos} ${formatTipoPeriodo(programa.tipoPeriodo, programa.duracionPeriodos)}`
                : `${programa.duracionPeriodos}`)
            : (programa.tipoPeriodo ? formatTipoPeriodo(programa.tipoPeriodo) : 'N/A');

        return `
            <tr data-programa-id="${programa.id}" class="cursor-pointer">
                <td><strong>${escapeHtml(programa.clave || 'N/A')}</strong></td>
                <td>${escapeHtml(programa.claveDgp || 'N/A')}</td>
                <td>${escapeHtml(programa.nombre || 'N/A')}</td>
                <td>${formatTipoPrograma(programa.tipoPrograma)}</td>
                <td>${escapeHtml(periodo)}</td>
                <td>${escapeHtml(programa.rvoe || 'N/A')}</td>
                <td>${getBadgeEstatusPrograma(programa.estatus)}</td>
                <td class="text-nowrap">
                    <div class="btn-group btn-group-sm" role="group">
                        <button type="button" class="btn btn-outline-primary" data-action="view" title="Ver"><i class="bi bi-eye"></i></button>
                        <button type="button" class="btn btn-outline-secondary" data-action="edit" title="Editar"><i class="bi bi-pencil"></i></button>
                        <button type="button" class="btn btn-outline-danger" data-action="delete" title="Eliminar"><i class="bi bi-trash"></i></button>
                    </div>
                </td>
            </tr>
        `;
    }).join('');
}

function mostrarErrorTablaProgramas(mensaje) {
    const tbody = document.getElementById('programasTableBody');
    if (!tbody) return;

    tbody.innerHTML = `
        <tr>
            <td colspan="8" class="text-center text-danger py-4">
                ${escapeHtml(mensaje)}
            </td>
        </tr>
    `;
}

function formatModalidadPrograma(m) {
    const mapeo = { ESCOLARIZADO: 'Escolarizado', MIXTO: 'Mixto', EN_LINEA: 'En línea' };
    return mapeo[m] || (m || '—');
}

function formatTipoAsignaturaExcel(tipo) {
    const mapeo = {
        OBLIGATORIA: 'Obligatoria', OPTATIVA: 'Optativa', LIBRE: 'Libre',
        EXTRACURRICULAR: 'Extracurricular', SERVICIO_SOCIAL: 'Servicio Social',
        RESIDENCIA_PROFESIONAL: 'Residencia Profesional'
    };
    return mapeo[tipo] || (tipo || '—');
}

async function descargarExcelProgramasAsignaturas() {
    if (typeof XLSX === 'undefined') {
        alert('No se pudo cargar la librería de Excel. Recarga la página e intenta de nuevo.');
        return;
    }

    setEstadoGenerandoExcelProgramas(true);

    try {
        let programas = programasEducativos && programasEducativos.length > 0 ? programasEducativos : [];
        let asignaturas = [];

        const resAsig = await fetch(getApiBaseProgramas() + '/asignaturas', { method: 'GET', headers: getHeaders() });
        if (resAsig.ok) asignaturas = await resAsig.json();

        // Solo es "plantilla" cuando NO hay programas ni asignaturas registrados
        const esPlantilla = (Array.isArray(programas) ? programas.length : 0) === 0 && (Array.isArray(asignaturas) ? asignaturas.length : 0) === 0;
        setTextoBotonDescargaExcelProgramas(esPlantilla ? 'plantilla' : 'exportar');

        if (programas.length === 0 && !esPlantilla) {
            if (typeof authFetch === 'function') {
                programas = await authFetch('/programas-educativos') || [];
            } else {
                const res = await fetch(getApiBaseProgramas() + '/programas-educativos', { method: 'GET', headers: getHeaders() });
                if (res.ok) programas = await res.json() || [];
            }
        }

        const wb = XLSX.utils.book_new();

        // --- Hoja: Programas (solo identificadores de negocio: idPrograma, clave; NO IDs internos) ---
        const headersProgramas = [
            'idPrograma', 'Clave', 'Clave DGP', 'Nombre', 'Tipo', 'No. periodos', 'Tipo periodo',
            'Modalidad', 'Créditos totales', 'RVOE', 'Fecha RVOE', 'Estatus'
        ];
        const filasProgramas = [headersProgramas];
        programas.forEach(p => {
            const duracion = p.duracionPeriodos != null ? String(p.duracionPeriodos) : '—';
            const tipoPer = p.tipoPeriodo ? formatTipoPeriodo(p.tipoPeriodo, p.duracionPeriodos) : '—';
            const fechaRvoe = p.fechaRvoe ? (typeof p.fechaRvoe === 'string' ? p.fechaRvoe.substring(0, 10) : p.fechaRvoe) : '—';
            filasProgramas.push([
                p.idPrograma || '',
                p.clave || '—',
                p.claveDgp || '—',
                p.nombre || '—',
                formatTipoPrograma(p.tipoPrograma) || '—',
                duracion,
                tipoPer,
                formatModalidadPrograma(p.modalidad),
                p.creditosTotales != null ? String(p.creditosTotales) : '—',
                p.rvoe || '—',
                fechaRvoe,
                p.estatus === 'ACTIVO' ? 'Activo' : (p.estatus === 'INACTIVO' ? 'Inactivo' : (p.estatus || '—'))
            ]);
        });
        const wsProgramas = XLSX.utils.aoa_to_sheet(filasProgramas);
        wsProgramas['!cols'] = [
            { wch: 12 }, { wch: 12 }, { wch: 14 }, { wch: 38 }, { wch: 14 }, { wch: 10 },
            { wch: 12 }, { wch: 14 }, { wch: 10 }, { wch: 18 }, { wch: 12 }, { wch: 10 }
        ];
        wsProgramas['!freeze'] = { xSplit: 0, ySplit: 1, topLeftCell: 'A2' };
        XLSX.utils.book_append_sheet(wb, wsProgramas, 'Programas');

        // --- Hoja: Asignaturas (solo identificadores de negocio; NO IDs internos) ---
        // Vincular a programa mediante idPrograma o Clave Programa
        const headersAsignaturas = [
            'idPrograma', 'Clave Programa', 'idAsignatura', 'Clave', 'Nombre asignatura', 'Tipo', 'No. periodo',
            'Créditos', 'Horas aula', 'Horas práctica', 'Horas independientes', 'Estatus'
        ];
        const filasAsignaturas = [headersAsignaturas];
        asignaturas.forEach(a => {
            const prog = a.programa || {};
            const horasA = a.horasAula != null ? String(a.horasAula) : '—';
            const horasP = a.horasPractica != null ? String(a.horasPractica) : '—';
            const horasI = a.horasIndependientes != null ? String(a.horasIndependientes) : '—';
            const periodoTexto = a.periodo && typeof a.periodo === 'object'
                ? (a.periodo.nombre || a.periodo.nombreDisplay || (a.periodo.numero + '° Periodo'))
                : (a.periodoNumero != null ? String(a.periodoNumero) : (a.periodo != null ? String(a.periodo) : '—'));
            filasAsignaturas.push([
                prog.idPrograma || '',
                prog.clave || '—',
                a.idAsignatura || '',
                a.clave || '—',
                a.nombre || '—',
                formatTipoAsignaturaExcel(a.tipo),
                periodoTexto,
                a.creditos != null ? String(a.creditos) : '—',
                horasA,
                horasP,
                horasI,
                a.estatus === 'ACTIVA' ? 'Activa' : (a.estatus === 'INACTIVA' ? 'Inactiva' : (a.estatus || '—'))
            ]);
        });
        const wsAsignaturas = XLSX.utils.aoa_to_sheet(filasAsignaturas);
        wsAsignaturas['!cols'] = [
            { wch: 12 }, { wch: 14 }, { wch: 12 }, { wch: 10 }, { wch: 36 }, { wch: 14 },
            { wch: 8 }, { wch: 8 }, { wch: 10 }, { wch: 12 }, { wch: 16 }, { wch: 10 }
        ];
        wsAsignaturas['!freeze'] = { xSplit: 0, ySplit: 1, topLeftCell: 'A2' };
        XLSX.utils.book_append_sheet(wb, wsAsignaturas, 'Asignaturas');

        const nombreArchivo = 'Programas_y_Asignaturas_' + new Date().toISOString().slice(0, 10) + '.xlsx';
        XLSX.writeFile(wb, nombreArchivo);
    } catch (err) {
        console.error('Error al generar Excel:', err);
        alert('No se pudo generar el archivo Excel. Verifica la conexión e intenta de nuevo.');
    } finally {
        setEstadoGenerandoExcelProgramas(false);
        setTextoBotonDescargaExcelProgramas((programasEducativos || []).length === 0 ? 'plantilla' : 'exportar');
    }
}

async function ejecutarCargaMasivaProgramasAsignaturas() {
    const input = document.getElementById('archivoCargaMasiva');
    const resultadoEl = document.getElementById('cargaMasivaResultado');
    const btn = document.getElementById('btnEjecutarCargaMasiva');

    if (!input || !input.files || input.files.length === 0) {
        if (resultadoEl) {
            resultadoEl.className = 'alert alert-warning';
            resultadoEl.textContent = 'Selecciona un archivo Excel antes de procesar.';
            resultadoEl.classList.remove('d-none');
        }
        return;
    }

    const file = input.files[0];
    if (!file.name.endsWith('.xlsx') && !file.name.endsWith('.xls')) {
        if (resultadoEl) {
            resultadoEl.className = 'alert alert-warning';
            resultadoEl.textContent = 'El archivo debe ser Excel (.xlsx o .xls).';
            resultadoEl.classList.remove('d-none');
        }
        return;
    }

    if (btn) {
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Procesando...';
    }
    if (resultadoEl) resultadoEl.classList.add('d-none');

    try {
        const formData = new FormData();
        formData.append('archivo', file);

        const headers = {};
        const token = localStorage.getItem('token');
        if (token && token !== 'null' && token !== 'undefined') {
            headers['Authorization'] = 'Bearer ' + token;
        }

        const base = getApiBaseProgramas();
        const res = await fetch(base + '/programas-educativos/carga-masiva', {
            method: 'POST',
            headers: headers,
            body: formData
        });

        const data = await res.json().catch(function () { return {}; });

        if (resultadoEl) {
            resultadoEl.classList.remove('d-none');
            if (res.ok && data.exito !== false) {
                resultadoEl.className = 'alert alert-success';
                let msg = 'Carga completada: ';
                msg += (data.programasCreados || 0) + ' programas creados, ';
                msg += (data.programasActualizados || 0) + ' programas actualizados, ';
                msg += (data.asignaturasCreadas || 0) + ' asignaturas creadas, ';
                msg += (data.asignaturasActualizadas || 0) + ' asignaturas actualizadas.';
                if (data.errores && data.errores.length > 0) {
                    msg += '\n\nAdvertencias: ' + data.errores.join('; ');
                }
                resultadoEl.textContent = msg;
                resultadoEl.innerHTML = msg.replace(/\n/g, '<br>');
                if (typeof cargarProgramas === 'function') cargarProgramas();
                if (typeof cargarAsignaturas === 'function' && programaSeleccionadoId) cargarAsignaturas(programaSeleccionadoId);
            } else {
                resultadoEl.className = 'alert alert-danger';
                resultadoEl.textContent = data.error || data.errores?.join('; ') || 'Error al procesar el archivo.';
                if (data.errores && data.errores.length > 0) {
                    resultadoEl.innerHTML = '<strong>Errores:</strong><ul><li>' + data.errores.join('</li><li>') + '</li></ul>';
                }
            }
        }
    } catch (err) {
        console.error('Error carga masiva:', err);
        if (resultadoEl) {
            resultadoEl.className = 'alert alert-danger';
            resultadoEl.classList.remove('d-none');
            resultadoEl.textContent = 'Error de conexión: ' + (err.message || 'No se pudo procesar el archivo.');
        }
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = '<i class="bi bi-upload me-1"></i>Procesar archivo';
        }
    }
}

function seleccionarProgramaPorId(id) {
    const programa = programasEducativos.find(item => item.id === id);
    if (!programa) return;

    programaSeleccionadoId = id;
    if (typeof seleccionarPrograma === 'function') {
        seleccionarPrograma(programa.nombre || 'Programa');
    }
    if (typeof cargarAsignaturas === 'function') {
        cargarAsignaturas(id);
    }
    renderizarSelectorProgramasAsignaturas();
}

function prepararFormularioPrograma(programa) {
    const form = document.getElementById('programaForm');
    if (!form) return;

    document.getElementById('programaId').value = programa ? programa.id || '' : '';
    const idProgramaEl = document.getElementById('programaIdPrograma');
    if (idProgramaEl) idProgramaEl.value = programa ? normalizarTextoCapturaPrograma(programa.idPrograma || '') : '';
    document.getElementById('programaClave').value = programa ? normalizarTextoCapturaPrograma(programa.clave || '') : '';
    const selectPlantel = document.getElementById('programaNombrePlantel');
    const claveDgpVal = programa ? (programa.claveDgp || '') : '';
    if (selectPlantel) {
        selectPlantel.value = claveDgpVal;
        if (claveDgpVal && !Array.from(selectPlantel.options).some(o => o.value === claveDgpVal)) {
            const opt = document.createElement('option');
            opt.value = claveDgpVal;
            opt.textContent = claveDgpVal + ' (no en catálogo)';
            selectPlantel.appendChild(opt);
            selectPlantel.value = claveDgpVal;
        }
    }
    actualizarClaveDgpEnFormulario();
    document.getElementById('programaNombre').value = programa ? normalizarTextoCapturaPrograma(programa.nombre || '') : '';
    document.getElementById('programaTipo').value = programa ? (programa.tipoPrograma || '') : '';
    document.getElementById('programaDuracion').value = programa ? (programa.duracionPeriodos || '') : '';
    document.getElementById('programaTipoPeriodo').value = programa ? (programa.tipoPeriodo || '') : '';
    document.getElementById('programaModalidad').value = programa ? (programa.modalidad || '') : '';
    document.getElementById('programaCreditos').value = programa ? (programa.creditosTotales || '') : '';
    document.getElementById('programaPlanEstudio').value = programa ? normalizarTextoCapturaPrograma(programa.planEstudio || '') : '';
    document.getElementById('programaRvoe').value = programa ? normalizarTextoCapturaPrograma(programa.rvoe || '') : '';
    document.getElementById('programaFechaRvoe').value = programa ? (programa.fechaRvoe || '') : '';
    document.getElementById('programaEstatus').value = programa ? (programa.estatus || 'ACTIVO') : 'ACTIVO';

    const boton = document.getElementById('btnGuardarPrograma');
    if (boton) {
        boton.textContent = programa ? 'Actualizar programa' : 'Guardar programa';
    }
}

function limpiarFormularioPrograma() {
    programaEditando = null;
    prepararFormularioPrograma(null);
}

function editarPrograma(id) {
    const programa = programasEducativos.find(item => item.id === id);
    if (!programa) return;

    programaEditando = programa;
    prepararFormularioPrograma(programa);
    // Cambiar al tab de registro para mostrar el formulario
    const tabRegistrar = document.querySelector('[data-bs-target="#paneRegistrarPrograma"]');
    if (tabRegistrar) {
        const tab = new bootstrap.Tab(tabRegistrar);
        tab.show();
    }
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

function validarFormularioPrograma() {
    var faltantes = [];
    var campos = [
        { id: 'programaIdPrograma', nombre: 'ID Programa' },
        { id: 'programaClave', nombre: 'Clave interna' },
        { id: 'programaNombrePlantel', nombre: 'Nombre del plantel' },
        { id: 'programaClaveDgp', nombre: 'Clave DGP' },
        { id: 'programaNombre', nombre: 'Nombre del programa' },
        { id: 'programaTipo', nombre: 'Tipo de programa' },
        { id: 'programaDuracion', nombre: 'No. de periodos' },
        { id: 'programaTipoPeriodo', nombre: 'Tipo de periodo' },
        { id: 'programaModalidad', nombre: 'Modalidad' },
        { id: 'programaCreditos', nombre: 'Créditos totales' },
        { id: 'programaPlanEstudio', nombre: 'Plan de estudio' },
        { id: 'programaRvoe', nombre: 'RVOE' },
        { id: 'programaFechaRvoe', nombre: 'Fecha de RVOE' },
        { id: 'programaEstatus', nombre: 'Estatus' }
    ];
    for (var i = 0; i < campos.length; i++) {
        var el = document.getElementById(campos[i].id);
        if (!el) continue;
        var valor = el.value;
        if (el.type === 'number') {
            if (valor === '' || valor === null || isNaN(parseInt(valor, 10)) || parseInt(valor, 10) <= 0) {
                faltantes.push(campos[i].nombre);
            }
        } else if (el.tagName === 'SELECT') {
            if (!valor || valor.trim() === '') {
                faltantes.push(campos[i].nombre);
            }
        } else {
            if (!valor || valor.trim() === '') {
                faltantes.push(campos[i].nombre);
            }
        }
    }
    return faltantes;
}

async function guardarPrograma() {
    const form = document.getElementById('programaForm');
    const btnGuardar = document.getElementById('btnGuardarPrograma');
    if (!form) return;

    var faltantes = validarFormularioPrograma();
    if (faltantes.length > 0) {
        var mensaje = faltantes.length === 1
            ? 'Falta el siguiente dato obligatorio:\n\n• ' + faltantes[0]
            : 'Faltan los siguientes datos obligatorios:\n\n• ' + faltantes.join('\n• ');
        alert(mensaje);
        var nombreAId = { 'ID Programa': 'programaIdPrograma', 'Clave interna': 'programaClave', 'Nombre del plantel': 'programaNombrePlantel', 'Clave DGP': 'programaClaveDgp', 'Nombre del programa': 'programaNombre', 'Tipo de programa': 'programaTipo', 'No. de periodos': 'programaDuracion', 'Tipo de periodo': 'programaTipoPeriodo', 'Modalidad': 'programaModalidad', 'Créditos totales': 'programaCreditos', 'Plan de estudio': 'programaPlanEstudio', 'RVOE': 'programaRvoe', 'Fecha de RVOE': 'programaFechaRvoe', 'Estatus': 'programaEstatus' };
        var idCampo = nombreAId[faltantes[0]];
        if (idCampo) {
            var campoEl = document.getElementById(idCampo);
            if (campoEl) {
                campoEl.focus();
                campoEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
        }
        return;
    }

    if (btnGuardar) {
        btnGuardar.disabled = true;
        btnGuardar.textContent = 'Guardando…';
    }

    const duracionValue = document.getElementById('programaDuracion').value;
    const creditosValue = document.getElementById('programaCreditos').value;

    const idProgramaEl = document.getElementById('programaIdPrograma');
    const idProgramaVal = normalizarTextoCapturaPrograma(idProgramaEl ? idProgramaEl.value.trim() : '');
    const programaData = {
        idPrograma: idProgramaVal || null,
        clave: normalizarTextoCapturaPrograma(document.getElementById('programaClave').value.trim()),
        claveDgp: (function () {
            const el = document.getElementById('programaClaveDgp');
            const v = el && el.value ? el.value.trim() : '';
            return v ? normalizarTextoCapturaPrograma(v) : null;
        })(),
        nombre: normalizarTextoCapturaPrograma(document.getElementById('programaNombre').value.trim()),
        tipoPrograma: document.getElementById('programaTipo').value,
        duracionPeriodos: duracionValue ? parseInt(duracionValue, 10) : null,
        tipoPeriodo: (document.getElementById('programaTipoPeriodo').value || '').trim().toUpperCase() || null,
        modalidad: document.getElementById('programaModalidad').value || null,
        creditosTotales: creditosValue ? parseInt(creditosValue, 10) : null,
        planEstudio: normalizarTextoCapturaPrograma(document.getElementById('programaPlanEstudio').value.trim()) || null,
        rvoe: normalizarTextoCapturaPrograma(document.getElementById('programaRvoe').value.trim()) || null,
        fechaRvoe: document.getElementById('programaFechaRvoe').value || null,
        estatus: document.getElementById('programaEstatus').value || 'ACTIVO'
    };

    const programaId = document.getElementById('programaId').value;
    const base = getApiBaseProgramas();
    const url = programaId ? `${base}/programas-educativos/${programaId}` : `${base}/programas-educativos`;
    const method = programaId ? 'PUT' : 'POST';

    try {
        const response = await fetch(url, {
            method,
            headers: getHeaders(),
            body: JSON.stringify(programaData)
        });

        let data = null;
        let rawText = '';
        try {
            data = await response.json();
        } catch (parseError) {
            data = null;
            try { rawText = await response.text(); } catch (e) { rawText = ''; }
        }

        if (!response.ok) {
            const message = data && (data.error || data.message)
                ? (data.error || data.message)
                : (rawText ? String(rawText) : 'Error al guardar programa');
            throw new Error(message);
        }

        limpiarFormularioPrograma();
        await cargarProgramas();
        alert(programaId ? 'Programa actualizado exitosamente' : 'Programa creado exitosamente');
        // Volver al tab de programas y asignaturas
        const tabProgramas = document.querySelector('[data-bs-target="#paneProgramasAsignaturas"]');
        if (tabProgramas) {
            const tab = new bootstrap.Tab(tabProgramas);
            tab.show();
        }
    } catch (error) {
        console.error('Error al guardar programa:', error);
        alert(error.message || 'Error al guardar programa');
    } finally {
        if (btnGuardar) {
            btnGuardar.disabled = false;
            btnGuardar.textContent = programaEditando ? 'Actualizar programa' : 'Guardar programa';
        }
    }
}

async function eliminarPrograma(id) {
    try {
        const response = await fetch(`${getApiBaseProgramas()}/programas-educativos/${id}`, {
            method: 'DELETE',
            headers: getHeaders(false)
        });

        if (!response.ok) {
            let message = 'Error al eliminar programa';
            try {
                const data = await response.json();
                message = data.error || data.message || message;
            } catch (parseError) {
                message = 'Error al eliminar programa';
            }
            throw new Error(message);
        }

        if (programaSeleccionadoId === id) {
            programaSeleccionadoId = null;
            if (typeof seleccionarPrograma === 'function') {
                seleccionarPrograma('Seleccione un programa...');
            }
            if (typeof cargarAsignaturas === 'function') {
                cargarAsignaturas(null);
            }
        }

        await cargarProgramas();
        alert('Programa eliminado exitosamente');
    } catch (error) {
        console.error('Error al eliminar programa:', error);
        alert(error.message || 'Error al eliminar programa');
    }
}

function buscarPrograma() {
    const input = document.getElementById('buscarProgramaInput');
    if (!input) return;

    const termino = input.value.toLowerCase().trim();

    if (!termino) {
        renderizarTablaProgramas(programasEducativos);
        return;
    }

    const filtrados = programasEducativos.filter(programa => {
        return (
            (programa.clave && programa.clave.toLowerCase().includes(termino)) ||
            (programa.nombre && programa.nombre.toLowerCase().includes(termino)) ||
            (programa.claveDgp && programa.claveDgp.toLowerCase().includes(termino))
        );
    });

    renderizarTablaProgramas(filtrados);
}

function formatTipoAsignaturaModal(tipo) {
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

function getBadgeEstatusAsignaturaModal(estatus) {
    const badges = {
        ACTIVA: '<span class="badge bg-success-subtle text-success">Activa</span>',
        INACTIVA: '<span class="badge bg-secondary-subtle text-secondary">Inactiva</span>'
    };
    return badges[estatus] || '<span class="badge bg-secondary-subtle text-secondary">N/A</span>';
}

/** Nombre del plantel: API (nombrePlantel) o catálogo local por claveDgp. */
function resolverNombrePlantelParaPrograma(programa) {
    if (!programa) return '';
    const desdeApi = programa.nombrePlantel != null ? String(programa.nombrePlantel).trim() : '';
    if (desdeApi) return desdeApi;
    const cd = (programa.claveDgp != null ? String(programa.claveDgp) : '').trim();
    if (!cd) return '';
    const u = cd.toUpperCase();
    const cat = (plantelesCatalog || []).find(p => {
        const c = (p && p.claveDgp != null ? String(p.claveDgp) : '').trim().toUpperCase();
        return c === u;
    });
    if (cat && cat.nombrePlantel) return String(cat.nombrePlantel).trim();
    return '';
}

function setTextModalProgramaDetalle(id, text) {
    const el = document.getElementById(id);
    if (!el) return;
    el.textContent = (text == null || String(text).trim() === '') ? '—' : String(text);
}

function setHtmlModalProgramaDetalle(id, html) {
    const el = document.getElementById(id);
    if (!el) return;
    const s = (html == null) ? '' : String(html);
    el.innerHTML = s.trim() ? s : '—';
}

function badgeEstatusProgramaSolo(et) {
    const v = String(et || '').trim().toUpperCase();
    if (v === 'ACTIVO' || v === 'ACTIVA') {
        return '<span class="badge bg-success-subtle text-success">Activo</span>';
    }
    if (v === 'INACTIVO' || v === 'INACTIVA') {
        return '<span class="badge bg-danger-subtle text-danger">Inactivo</span>';
    }
    return '<span class="badge bg-secondary-subtle text-secondary">—</span>';
}

function mostrarDetalleProgramaEnModal(programa) {
    if (!programa) {
        setTextModalProgramaDetalle('modalProgramaDetalleIdPrograma', '—');
        setTextModalProgramaDetalle('modalProgramaDetalleClave', '—');
        setTextModalProgramaDetalle('modalProgramaDetalleNombre', '—');
        setHtmlModalProgramaDetalle('modalProgramaDetalleEstatus', '—');
        setTextModalProgramaDetalle('modalProgramaDetallePlantel', '—');
        setTextModalProgramaDetalle('modalProgramaDetalleClaveDgp', '—');
        setTextModalProgramaDetalle('modalProgramaDetalleTipo', '—');
        setTextModalProgramaDetalle('modalProgramaDetalleModalidad', '—');
        setTextModalProgramaDetalle('modalProgramaDetalleDuracion', '—');
        setTextModalProgramaDetalle('modalProgramaDetalleCreditos', '—');
        setTextModalProgramaDetalle('modalProgramaDetallePlanEstudio', '—');
        setTextModalProgramaDetalle('modalProgramaDetalleRvoe', '—');
        setTextModalProgramaDetalle('modalProgramaDetalleFechaRvoe', '—');
        return;
    }

    const duracion = programa.duracionPeriodos
        ? (programa.tipoPeriodo
            ? `${programa.duracionPeriodos} ${formatTipoPeriodo(programa.tipoPeriodo, programa.duracionPeriodos)}`
            : `${programa.duracionPeriodos}`)
        : (programa.tipoPeriodo ? formatTipoPeriodo(programa.tipoPeriodo) : '—');

    const fechaRvoe = programa.fechaRvoe
        ? (typeof programa.fechaRvoe === 'string' ? String(programa.fechaRvoe).substring(0, 10) : String(programa.fechaRvoe))
        : '—';

    setTextModalProgramaDetalle('modalProgramaDetalleIdPrograma', programa.idPrograma || '—');
    setTextModalProgramaDetalle('modalProgramaDetalleClave', programa.clave || '—');
    setTextModalProgramaDetalle('modalProgramaDetalleNombre', programa.nombre || '—');
    setHtmlModalProgramaDetalle('modalProgramaDetalleEstatus', badgeEstatusProgramaSolo(programa.estatus));
    setTextModalProgramaDetalle('modalProgramaDetallePlantel', resolverNombrePlantelParaPrograma(programa) || '—');
    setTextModalProgramaDetalle('modalProgramaDetalleClaveDgp', programa.claveDgp || '—');
    setTextModalProgramaDetalle('modalProgramaDetalleTipo', formatTipoPrograma(programa.tipoPrograma));
    setTextModalProgramaDetalle('modalProgramaDetalleModalidad', formatModalidadPrograma(programa.modalidad));
    setTextModalProgramaDetalle('modalProgramaDetalleDuracion', duracion);
    setTextModalProgramaDetalle('modalProgramaDetalleCreditos', programa.creditosTotales != null ? String(programa.creditosTotales) : '—');
    setTextModalProgramaDetalle('modalProgramaDetallePlanEstudio', programa.planEstudio || '—');
    setTextModalProgramaDetalle('modalProgramaDetalleRvoe', programa.rvoe || '—');
    setTextModalProgramaDetalle('modalProgramaDetalleFechaRvoe', fechaRvoe);
}

async function mostrarModalAsignaturasPrograma(programaId, programaNombre) {
    const modal = document.getElementById('modalAsignaturasPrograma');
    const titleSpan = document.getElementById('modalAsignaturasProgramaNombre');
    const tbody = document.getElementById('modalAsignaturasProgramaBody');
    if (!modal || !titleSpan || !tbody) return;

    titleSpan.textContent = programaNombre || '';
    // Mostrar detalle del programa (solo lectura) antes de la tabla
    const programa = (programasEducativos || []).find(p => p && p.id === programaId) || null;
    mostrarDetalleProgramaEnModal(programa);

    tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted py-4"><span class="spinner-border spinner-border-sm"></span> Cargando asignaturas…</td></tr>';

    const bsModal = typeof bootstrap !== 'undefined' ? new bootstrap.Modal(modal) : null;
    if (bsModal) bsModal.show();

    try {
        const url = (typeof API_URL !== 'undefined' && API_URL) ? API_URL : 'http://localhost:8080/api';
        const response = await fetch(`${url}/asignaturas?programaId=${programaId}`, {
            method: 'GET',
            headers: getHeaders()
        });
        if (!response.ok) throw new Error('Error al cargar asignaturas');
        const lista = await response.json();
        const listaAsignaturas = Array.isArray(lista) ? lista : [];

        if (listaAsignaturas.length === 0) {
            tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted py-4">No hay asignaturas registradas para este programa.</td></tr>';
            return;
        }

        tbody.innerHTML = listaAsignaturas.map(asignatura => {
            const horas = [asignatura.horasAula, asignatura.horasPractica, asignatura.horasIndependientes]
                .map(valor => (valor !== null && valor !== undefined && valor !== '') ? valor : '0')
                .join(' / ');
            const periodoTexto = asignatura.periodo && typeof asignatura.periodo === 'object'
                ? (asignatura.periodo.nombre || asignatura.periodo.nombreDisplay || asignatura.periodo.numero + '° Periodo')
                : (asignatura.periodoNumero ?? asignatura.periodo ?? 'N/A');
            return `
                <tr>
                    <td>${escapeHtml(asignatura.idAsignatura || '—')}</td>
                    <td><strong>${escapeHtml(asignatura.clave || 'N/A')}</strong></td>
                    <td>${escapeHtml(asignatura.nombre || 'N/A')}</td>
                    <td>${formatTipoAsignaturaModal(asignatura.tipo)}</td>
                    <td>${escapeHtml(periodoTexto)}</td>
                    <td>${escapeHtml(asignatura.creditos || 'N/A')}</td>
                    <td>${escapeHtml(horas)}</td>
                    <td>${getBadgeEstatusAsignaturaModal(asignatura.estatus)}</td>
                </tr>
            `;
        }).join('');
    } catch (error) {
        console.error('Error al cargar asignaturas:', error);
        tbody.innerHTML = '<tr><td colspan="8" class="text-center text-danger py-4">' + escapeHtml(error.message || 'Error al cargar las asignaturas') + '</td></tr>';
    }
}

function inicializarTablaProgramas() {
    const tbody = document.getElementById('programasTableBody');
    if (!tbody || tablaProgramasInicializada) return;

    tbody.addEventListener('click', (event) => {
        const button = event.target.closest('button[data-action]');
        const row = event.target.closest('tr[data-programa-id]');
        if (!row) return;

        const programaId = parseInt(row.dataset.programaId, 10);
        if (!programaId) return;

        if (button) {
            event.stopPropagation();
            const action = button.dataset.action;
            if (action === 'view') {
                const programa = (programasEducativos || []).find(p => p.id === programaId);
                const nombre = programa ? (programa.clave || '') + ' - ' + (programa.nombre || '') : '';
                mostrarModalAsignaturasPrograma(programaId, nombre);
            } else if (action === 'edit') {
                editarPrograma(programaId);
            } else if (action === 'delete') {
                if (confirm('Estas seguro de eliminar este programa? Esta accion no se puede deshacer.')) {
                    eliminarPrograma(programaId);
                }
            }
            return;
        }

        const programa = (programasEducativos || []).find(p => p.id === programaId);
        const nombre = programa ? (programa.clave || '') + ' - ' + (programa.nombre || '') : '';
        mostrarModalAsignaturasPrograma(programaId, nombre);
    });

    tablaProgramasInicializada = true;
}

document.addEventListener('DOMContentLoaded', function () {
    if (!document.getElementById('programasTableBody')) return;
    if (window._initProgramasDone) return;
    window._initProgramasDone = true;

    function initProgramas() {
        setTextoBotonDescargaExcelProgramas('plantilla');
        cargarPlanteles();
        inicializarTablaProgramas();
        cargarProgramas();

        var selectPlantel = document.getElementById('programaNombrePlantel');
        if (selectPlantel) selectPlantel.addEventListener('change', actualizarClaveDgpEnFormulario);

        // Ya no se fuerza a mayúsculas: se respeta lo capturado

        var selectAsignaturasPrograma = document.getElementById('asignaturasSelectorPrograma');
        if (selectAsignaturasPrograma) {
            selectAsignaturasPrograma.addEventListener('change', function () {
                const id = this.value ? parseInt(this.value, 10) : null;
                if (id) {
                    seleccionarProgramaPorId(id);
                } else {
                    programaSeleccionadoId = null;
                    if (typeof cargarAsignaturas === 'function') cargarAsignaturas(null);
                }
            });
        }

        var btnGuardar = document.getElementById('btnGuardarPrograma');
        if (btnGuardar) btnGuardar.addEventListener('click', guardarPrograma);

        var inputBuscar = document.getElementById('buscarProgramaInput');
        if (inputBuscar) inputBuscar.addEventListener('input', buscarPrograma);

        var btnGuardarPlantel = document.getElementById('btnGuardarPlantel');
        if (btnGuardarPlantel) btnGuardarPlantel.addEventListener('click', guardarPlantel);
        var btnCancelarPlantel = document.getElementById('btnCancelarPlantel');
        if (btnCancelarPlantel) btnCancelarPlantel.addEventListener('click', function () { limpiarFormularioPlantel(); });

        var plantelesTbody = document.getElementById('plantelesTableBody');
        if (plantelesTbody) {
            plantelesTbody.addEventListener('click', function (e) {
                var row = e.target.closest('tr[data-plantel-id]');
                if (!row) return;
                var id = parseInt(row.dataset.plantelId, 10);
                var btn = e.target.closest('button[data-action]');
                if (btn && id) {
                    if (btn.dataset.action === 'edit-plantel') editarPlantel(id);
                    else if (btn.dataset.action === 'delete-plantel') eliminarPlantel(id);
                }
            });
        }
    }

    var pathProg = (window.location.pathname || '').toLowerCase();
    var esPaginaProgramasEducativos = pathProg.indexOf('programas-educativos.html') !== -1;
    if (!document.getElementById('programasSection') || esPaginaProgramasEducativos) {
        initProgramas();
        return;
    }
    if (typeof dashboardSessionValidated !== 'undefined' && dashboardSessionValidated) {
        initProgramas();
    } else {
        window.addEventListener('dashboardSessionValidated', function onSessionValidated() {
            window.removeEventListener('dashboardSessionValidated', onSessionValidated);
            initProgramas();
        }, { once: true });
    }
});
